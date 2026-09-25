import logging
from datetime import datetime

from fastapi import FastAPI
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("notification-service")

app = FastAPI(title="notification-service")


class PatientMoved(BaseModel):
    moveId: int
    patientId: int
    room: str
    movedAt: datetime


@app.post("/patient-moves", status_code=204)
def patient_moved(event: PatientMoved) -> None:
    log.info("Patient %s moved to room %s at %s (move %s)", event.patientId, event.room, event.movedAt, event.moveId)
