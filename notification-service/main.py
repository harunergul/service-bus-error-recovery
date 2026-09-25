import logging
import sqlite3
import time
from datetime import datetime, timezone
from http import HTTPStatus
from pathlib import Path

from fastapi import FastAPI, Request, Response
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


class PatientMoved(BaseModel):
    moveId: int
    patientId: int
    room: str
    movedAt: datetime


class OutageRequest(BaseModel):
    seconds: int = Field(gt=0)


class Delivered(BaseModel):
    id: int
    moveId: int
    patientId: int
    room: str
    movedAt: datetime
    receivedAt: datetime
    duplicate: bool
    """True when this move was already delivered before: Service Bus delivers at least once."""


class Outage(BaseModel):
    remainingSeconds: float
    totalSeconds: int


# Simulated outage: until this monotonic time every /patient-moves request (the POST and the HEAD
# probe) answers 503, as if the service were down. In memory only, so a restart ends it.
outage_until = 0.0
outage_seconds = 0


def outage_remaining() -> float:
    return max(0.0, outage_until - time.monotonic())


@app.middleware("http")
async def simulate_outage(request: Request, call_next):
    # The /outage endpoints stay up, so an outage can always be checked and ended
    if request.url.path == "/patient-moves" and outage_remaining() > 0:
        log.info("%s %s during simulated outage, answering 503", request.method, request.url.path)
        return Response(status_code=HTTPStatus.SERVICE_UNAVAILABLE)
    return await call_next(request)


@app.get("/outage")
def get_outage() -> Outage:
    remaining = outage_remaining()
    return Outage(remainingSeconds=remaining, totalSeconds=outage_seconds if remaining > 0 else 0)


@app.put("/outage")
def start_outage(request: OutageRequest) -> Outage:
    global outage_until, outage_seconds
    outage_until = time.monotonic() + request.seconds
    outage_seconds = request.seconds
    log.warning("Simulated outage started for %ss", request.seconds)
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
def patient_moved(event: PatientMoved) -> Response:
    status = status_for(event.room)
    log.info("Patient %s moved to room %s at %s (move %s), answering %s",
             event.patientId, event.room, event.movedAt, event.moveId, int(status))
    if status.is_success:
        with db() as connection:
            connection.execute(
                "INSERT INTO delivered (move_id, patient_id, room, moved_at, received_at) VALUES (?, ?, ?, ?, ?)",
                (event.moveId, event.patientId, event.room, event.movedAt.isoformat(),
                 datetime.now(timezone.utc).isoformat()))
    return Response(status_code=status)


@app.get("/delivered")
def list_delivered(limit: int = 200) -> list[Delivered]:
    """Newest first. Every accepted call is a row, so a move delivered twice shows up twice."""
    with db() as connection:
        rows = connection.execute("""
            SELECT d.*, EXISTS (SELECT 1 FROM delivered e WHERE e.move_id = d.move_id AND e.id < d.id) AS duplicate
            FROM delivered d ORDER BY d.id DESC LIMIT ?""", (limit,)).fetchall()
    return [Delivered(id=r["id"], moveId=r["move_id"], patientId=r["patient_id"], room=r["room"],
                      movedAt=r["moved_at"], receivedAt=r["received_at"], duplicate=r["duplicate"])
            for r in rows]
