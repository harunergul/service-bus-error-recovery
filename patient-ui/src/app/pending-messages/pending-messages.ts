import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { PatientApi, PendingMessage, autoRefresh, errorNotice, pageNotice, prettyBody } from '../patient-api';
import { LiveState, LiveStatus } from '../live-status/live-status';

@Component({
  selector: 'app-pending-messages',
  imports: [DatePipe, LiveStatus],
  templateUrl: './pending-messages.html',
})
export class PendingMessages implements OnInit {
  private readonly api = inject(PatientApi);

  protected readonly messages = signal<PendingMessage[]>([]);
  protected readonly notice = pageNotice();
  protected readonly loading = signal(false);
  protected readonly clearing = signal(false);
  protected readonly live = signal<LiveState>('connecting');
  protected readonly prettyBody = prettyBody;

  private polling = false;

  constructor() {
    autoRefresh(() => this.poll());
  }

  ngOnInit() {
    this.load();
  }

  protected clear() {
    if (!confirm('Delete all pending messages? This cannot be undone.')) return;
    this.clearing.set(true);
    this.api.clearPendingMessages().subscribe({
      next: ({ cleared }) => {
        this.clearing.set(false);
        this.load();
        this.notice.set({ kind: 'success', text: `Cleared ${cleared} pending messages` });
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
    this.api.listPendingMessages().subscribe({
      next: messages => {
        this.messages.set(messages);
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
    this.api.listPendingMessages().subscribe({
      next: messages => {
        this.messages.set(messages);
        this.live.set('live');
        this.polling = false;
      },
      error: () => {
        this.live.set('offline');
        this.polling = false;
      },
    });
  }
}
