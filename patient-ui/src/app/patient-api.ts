import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export interface Patient {
  id: number;
  name: string;
  nationalIdentity: string;
}

export interface PatientMove {
  id: number;
  patientId: number;
  room: string;
  movedAt: string;
}

export interface DeadLetter {
  messageId: string;
  sequenceNumber: number;
  deliveryCount: number;
  reason: string;
  description: string;
  enqueuedTime: string;
  body: string;
}

@Injectable({ providedIn: 'root' })
export class PatientApi {
  private readonly http = inject(HttpClient);

  listPatients() {
    return this.http.get<Patient[]>('/api/patients');
  }

  registerPatient(name: string, nationalIdentity: string) {
    return this.http.post<Patient>('/api/patients', { name, nationalIdentity });
  }

  recordMove(patientId: number, room: string) {
    return this.http.post<PatientMove>('/api/patient-moves', { patientId, room });
  }

  listDeadLetters() {
    return this.http.get<DeadLetter[]>('/api/dead-letters');
  }
}

export interface Notice {
  kind: 'success' | 'error';
  text: string;
}

export function errorNotice(err: HttpErrorResponse): Notice {
  return { kind: 'error', text: `Error ${err.status}: ${err.error?.message ?? err.message}` };
}
