import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';

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
  sessionId: string;
  sequenceNumber: number;
  deliveryCount: number;
  reason: string;
  description: string;
  enqueuedTime: string;
  deadLetteredTime: string | null;
  body: string;
}

export interface PendingMessage {
  messageId: string;
  sessionId: string;
  sequenceNumber: number;
  deliveryCount: number;
  state: string;
  enqueuedTime: string;
  body: string;
}

export interface Cleared {
  cleared: number;
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

  listPendingMessages() {
    return this.http.get<PendingMessage[]>('/api/pending-messages');
  }

  listDeadLetters() {
    return this.http.get<DeadLetter[]>('/api/dead-letters');
  }

  clearPendingMessages() {
    return this.http.delete<Cleared>('/api/pending-messages');
  }

  clearDeadLetters() {
    return this.http.delete<Cleared>('/api/dead-letters');
  }
}

export interface Notice {
  kind: 'success' | 'error';
  text: string;
}

export function errorNotice(err: HttpErrorResponse): Notice {
  return { kind: 'error', text: `Error ${err.status}: ${err.error?.message ?? err.message}` };
}

const SUCCESS_NOTICE_MS = 4000;

/**
 * Holds the notice shown on a page. Success notices hide themselves after 4 seconds; errors
 * stay until replaced so they can be read. Call from a field initializer (needs injection context).
 */
export function pageNotice() {
  const notice = signal<Notice | null>(null);
  let timer: ReturnType<typeof setTimeout> | undefined;
  inject(DestroyRef).onDestroy(() => clearTimeout(timer));

  return Object.assign(notice.asReadonly(), {
    set(value: Notice | null) {
      clearTimeout(timer);
      notice.set(value);
      if (value?.kind === 'success') {
        timer = setTimeout(() => notice.set(null), SUCCESS_NOTICE_MS);
      }
    },
  });
}

/** A duration in whole seconds, e.g. "45s", "1m 3s", "1h 2m 5s". */
export function formatDuration(from: string, to: string) {
  const seconds = Math.max(0, Math.round((Date.parse(to) - Date.parse(from)) / 1000));
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  return h > 0 ? `${h}h ${m}m ${s}s` : m > 0 ? `${m}m ${s}s` : `${s}s`;
}

/** Message bodies are JSON; indent them for reading, or show them as-is if they aren't. */
export function prettyBody(body: string) {
  try {
    return JSON.stringify(JSON.parse(body), null, 2);
  } catch {
    return body;
  }
}
