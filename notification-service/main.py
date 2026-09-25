import logging
from datetime import datetime
from http import HTTPStatus

from fastapi import FastAPI, Response
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("notification-service")

app = FastAPI(title="notification-service")


class PatientMoved(BaseModel):
    moveId: int
    patientId: int
    room: str
    movedAt: datetime


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
