import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

/** How notification-service fails during an outage: an HTTP status, or no answer at all. */
export type OutageFailure = 500 | 502 | 503 | 504 | 'timeout';

export const OUTAGE_FAILURES: { value: OutageFailure; label: string }[] = [
  { value: 503, label: '503 Service Unavailable' },
  { value: 500, label: '500 Internal Server Error' },
  { value: 502, label: '502 Bad Gateway' },
  { value: 504, label: '504 Gateway Timeout' },
  { value: 'timeout', label: 'Timeout (no answer)' },
];

interface Outage {
  remainingSeconds: number;
  totalSeconds: number;
  failure: OutageFailure | null;
}

/** How often to ask notification-service again, to notice outages started or ended elsewhere. */
const RESYNC_MS = 5000;

/**
 * The simulated notification-service outage, shared by the header banner and the card on the
 * Patient move page. The outage lives in notification-service; this counts down locally and
 * asks again every few seconds.
 */
@Injectable({ providedIn: 'root' })
export class OutageState {
  private readonly http = inject(HttpClient);

  /** When the outage ends (browser clock), or 0 when there is none. */
  private readonly endsAt = signal(0);
  private readonly now = signal(Date.now());

  readonly totalSeconds = signal(0);
  readonly failure = signal<OutageFailure | null>(null);
  /** False when notification-service did not answer the last check, e.g. because it is stopped. */
  readonly reachable = signal(true);

  readonly remainingSeconds = computed(() => Math.max(0, Math.ceil((this.endsAt() - this.now()) / 1000)));
  readonly down = computed(() => this.remainingSeconds() > 0);
  readonly progress = computed(() =>
    this.totalSeconds() > 0 ? (this.remainingSeconds() / this.totalSeconds()) * 100 : 0,
  );
  /** The chosen failure as offered in the select, e.g. "502 Bad Gateway". */
  readonly failureLabel = computed(() => OUTAGE_FAILURES.find(f => f.value === this.failure())?.label ?? '');
  /** What notification-service does meanwhile, for the header banner. */
  readonly behaviour = computed(() =>
    this.failure() === 'timeout'
      ? 'Not answering any PatientMoved or probe'
      : `Answering ${this.failure()} to every PatientMoved and probe`,
  );
  readonly countdown = computed(() => {
    const s = this.remainingSeconds();
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  });

  constructor() {
    // Lives as long as the app (root service), so the timers are never cleared
    setInterval(() => this.now.set(Date.now()), 250);
    setInterval(() => this.refresh().subscribe({ error: () => {} }), RESYNC_MS);
    this.refresh().subscribe({ error: () => {} });
  }

  refresh(): Observable<Outage> {
    return this.track(this.http.get<Outage>('/notification/outage'));
  }

  start(seconds: number, failure: OutageFailure): Observable<Outage> {
    return this.track(this.http.put<Outage>('/notification/outage', { seconds, failure }));
  }

  end(): Observable<Outage> {
    return this.track(this.http.delete<Outage>('/notification/outage'));
  }

  private track(request: Observable<Outage>) {
    return request.pipe(
      tap({
        next: outage => {
          this.reachable.set(true);
          this.now.set(Date.now());
          this.endsAt.set(outage.remainingSeconds > 0 ? Date.now() + outage.remainingSeconds * 1000 : 0);
          this.totalSeconds.set(outage.totalSeconds);
          this.failure.set(outage.failure);
        },
        error: (err: HttpErrorResponse) => {
          // A 4xx (e.g. an invalid duration) is an answer; anything else means it is not answering
          this.reachable.set(err.status >= 400 && err.status < 500);
        },
      }),
    );
  }
}
