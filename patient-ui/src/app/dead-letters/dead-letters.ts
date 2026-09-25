import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { DeadLetter, PatientApi, autoRefresh, errorNotice, formatDuration, pageNotice, prettyBody } from '../patient-api';
import { LiveState, LiveStatus } from '../live-status/live-status';

@Component({
  selector: 'app-dead-letters',
  imports: [DatePipe, LiveStatus],
  templateUrl: './dead-letters.html',
})
export class DeadLetters implements OnInit {
  private readonly api = inject(PatientApi);

  protected readonly deadLetters = signal<DeadLetter[]>([]);
  protected readonly notice = pageNotice();
  protected readonly loading = signal(false);
  protected readonly clearing = signal(false);
  protected readonly live = signal<LiveState>('connecting');

  private polling = false;

  constructor() {
    autoRefresh(() => this.poll());
  }

  ngOnInit() {
    this.load();
  }

  protected clear() {
    if (!confirm('Delete all dead letters? This cannot be undone.')) return;
    this.clearing.set(true);
    this.api.clearDeadLetters().subscribe({
      next: ({ cleared }) => {
        this.clearing.set(false);
        this.load();
        this.notice.set({ kind: 'success', text: `Cleared ${cleared} dead letters` });
      },
      error: err => {
        this.clearing.set(false);
        this.notice.set(errorNotice(err));
      },
    });
  }

  protected load() {
    this.notice.set(null);
    this.loading.set(true);
    this.api.listDeadLetters().subscribe({
      next: deadLetters => {
        this.deadLetters.set(deadLetters);
        this.live.set('live');
        this.loading.set(false);
      },
      error: err => {
        this.notice.set(errorNotice(err));
        this.live.set('offline');
        this.loading.set(false);
      },
    });
  }

  /**
   * The automatic reload: no "Refreshing…" and the notice is left alone; a failure only shows
   * as Offline. Skipped while another request is still running.
   */
  private poll() {
    if (this.polling || this.loading() || this.clearing()) return;
    this.polling = true;
    this.api.listDeadLetters().subscribe({
      next: deadLetters => {
        this.deadLetters.set(deadLetters);
        this.live.set('live');
        this.polling = false;
      },
      error: () => {
        this.live.set('offline');
        this.polling = false;
      },
    });
  }

  /** Time from enqueue to dead-lettering, e.g. "1m 3s". */
  protected took(deadLetter: DeadLetter) {
    return deadLetter.deadLetteredTime ? formatDuration(deadLetter.enqueuedTime, deadLetter.deadLetteredTime) : '—';
  }

  protected readonly prettyBody = prettyBody;
}
