import asyncio
import json
import logging
import sqlite3
import time
from datetime import datetime, timezone
from http import HTTPStatus
from pathlib import Path
from typing import Literal

from fastapi import FastAPI, Request, Response, WebSocket, WebSocketDisconnect
from pydantic import BaseModel, Field

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("notification-service")

app = FastAPI(title="notification-service")

# Accepted PatientMoved events, kept in SQLite so they survive restarts (uvicorn --reload restarts on every edit)
DB_PATH = Path(__file__).parent / "data" / "notifications.db"
DB_PATH.parent.mkdir(exist_ok=True)


def db() -> sqlite3.Connection:
    connection = sqlite3.connect(DB_PATH)
    connection.row_factory = sqlite3.Row
    return connection


with db() as connection:
    connection.execute("""
        CREATE TABLE IF NOT EXISTS delivered (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            move_id INTEGER NOT NULL,
            patient_id INTEGER NOT NULL,
            room TEXT NOT NULL,
            moved_at TEXT NOT NULL,
            received_at TEXT NOT NULL
        )""")
    # Added later: the request body exactly as received; null on rows saved before it existed
    if "body" not in {row["name"] for row in connection.execute("PRAGMA table_info(delivered)")}:
        connection.execute("ALTER TABLE delivered ADD COLUMN body TEXT")


class PatientMoved(BaseModel):
    moveId: int
    patientId: int
    room: str
    movedAt: datetime


# How notification-service fails during a simulated outage: an HTTP status, or "timeout" to not answer at all
OutageFailure = Literal[500, 502, 503, 504, "timeout"]


class OutageRequest(BaseModel):
    seconds: int = Field(gt=0)
    failure: OutageFailure = 503


class Delivered(BaseModel):
    id: int
    moveId: int
    patientId: int
    room: str
    movedAt: datetime
    receivedAt: datetime
    duplicate: bool
    """True when this move was already delivered before: Service Bus delivers at least once."""
    body: str


class Cleared(BaseModel):
    cleared: int


class Outage(BaseModel):
    remainingSeconds: float
    totalSeconds: int
    failure: OutageFailure | None
    """How it fails while the outage lasts; null when there is none."""


# Simulated outage: until this monotonic time every /patient-moves request (the POST and the HEAD
# probe) fails as outage_failure says, as if the service were down. In memory only, so a restart ends it.
outage_until = 0.0
outage_seconds = 0
outage_failure: OutageFailure = 503


def outage_remaining() -> float:
    return max(0.0, outage_until - time.monotonic())


@app.middleware("http")
async def simulate_outage(request: Request, call_next):
    # The /outage endpoints stay up, so an outage can always be checked and ended
    if request.url.path == "/patient-moves" and outage_remaining() > 0:
        if outage_failure == "timeout":
            # Holds the request unanswered, so the caller gives up on its own read timeout. When the
            # outage ends the request is dropped, never handled: the caller has long stopped waiting.
            log.info("%s %s during simulated outage, not answering", request.method, request.url.path)
            while outage_remaining() > 0:
                await asyncio.sleep(0.5)
            return Response(status_code=HTTPStatus.SERVICE_UNAVAILABLE)
        log.info("%s %s during simulated outage, answering %s", request.method, request.url.path, outage_failure)
        return Response(status_code=outage_failure)
    return await call_next(request)


@app.get("/outage")
def get_outage() -> Outage:
    remaining = outage_remaining()
    if remaining == 0:
        return Outage(remainingSeconds=0, totalSeconds=0, failure=None)
    return Outage(remainingSeconds=remaining, totalSeconds=outage_seconds, failure=outage_failure)


@app.put("/outage")
def start_outage(request: OutageRequest) -> Outage:
    global outage_until, outage_seconds, outage_failure
    outage_until = time.monotonic() + request.seconds
    outage_seconds = request.seconds
    outage_failure = request.failure
    log.warning("Simulated outage started for %ss, failing with %s", request.seconds, request.failure)
    return get_outage()


@app.delete("/outage")
def end_outage() -> Outage:
    global outage_until
    outage_until = 0.0
    log.warning("Simulated outage ended")
    return get_outage()


def status_for(room: str) -> int:
    """A room named after an HTTP status code (e.g. "404", "503") answers with that code, to
    simulate failures from the UI. Informational 1xx codes are skipped: they cannot end a response."""
    try:
        status = HTTPStatus(int(room))
    except ValueError:
        return HTTPStatus.OK
    return HTTPStatus.OK if status.is_informational else status


@app.post("/patient-moves")
async def patient_moved(event: PatientMoved, request: Request) -> Response:
    status = status_for(event.room)
    log.info("Patient %s moved to room %s at %s (move %s), answering %s",
             event.patientId, event.room, event.movedAt, event.moveId, int(status))
    if status.is_success:
        with db() as connection:
            connection.execute(
                "INSERT INTO delivered (move_id, patient_id, room, moved_at, received_at, body) VALUES (?, ?, ?, ?, ?, ?)",
                (event.moveId, event.patientId, event.room, event.movedAt.isoformat(),
                 datetime.now(timezone.utc).isoformat(), (await request.body()).decode()))
        await broadcast_delivered()
    return Response(status_code=status)


@app.get("/delivered")
def list_delivered(limit: int = 200) -> list[Delivered]:
    """Newest first. Every accepted call is a row, so a move delivered twice shows up twice."""
    with db() as connection:
        rows = connection.execute("""
            SELECT d.*, EXISTS (SELECT 1 FROM delivered e WHERE e.move_id = d.move_id AND e.id < d.id) AS duplicate
            FROM delivered d ORDER BY d.id DESC LIMIT ?""", (limit,)).fetchall()
    return [Delivered(id=r["id"], moveId=r["move_id"], patientId=r["patient_id"], room=r["room"],
                      movedAt=r["moved_at"], receivedAt=r["received_at"], duplicate=r["duplicate"],
                      body=r["body"] or json.dumps({"moveId": r["move_id"], "patientId": r["patient_id"],
                                                    "room": r["room"], "movedAt": r["moved_at"]}))
            for r in rows]


@app.delete("/delivered")
async def clear_delivered() -> Cleared:
    """Deletes every delivered row, for local testing."""
    with db() as connection:
        cleared = connection.execute("DELETE FROM delivered").rowcount
    log.warning("Cleared %s delivered", cleared)
    await broadcast_delivered()
    return Cleared(cleared=cleared)


# Browsers watching the Delivered list. Each gets the whole list when it connects and again after
# every change, so it never has to ask. In memory only: a restart drops them and they reconnect.
delivered_watchers: set[WebSocket] = set()


def delivered_json() -> str:
    return json.dumps([delivered.model_dump(mode="json") for delivered in list_delivered()])


async def broadcast_delivered() -> None:
    if not delivered_watchers:
        return
    payload = delivered_json()
    for watcher in list(delivered_watchers):
        try:
            await watcher.send_text(payload)
        except Exception:
            # Went away without a close handshake; its own endpoint below stops too
            delivered_watchers.discard(watcher)


@app.websocket("/delivered/live")
async def watch_delivered(websocket: WebSocket) -> None:
    await websocket.accept()
    delivered_watchers.add(websocket)
    try:
        await websocket.send_text(delivered_json())
        while True:
            # The browser sends nothing; this only waits for it to disconnect
            await websocket.receive_text()
    except WebSocketDisconnect:
        pass
    finally:
        delivered_watchers.discard(websocket)
