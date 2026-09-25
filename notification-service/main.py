import logging
import time
from datetime import datetime
from http import HTTPStatus

from fastapi import FastAPI, Request, Response
from pydantic import BaseModel, Field

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("notification-service")

app = FastAPI(title="notification-service")


class PatientMoved(BaseModel):
    moveId: int
    patientId: int
    room: str
    movedAt: datetime


class OutageRequest(BaseModel):
    seconds: int = Field(gt=0)


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
    return Response(status_code=status)
