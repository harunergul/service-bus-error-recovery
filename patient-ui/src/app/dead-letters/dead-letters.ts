import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { DeadLetter, PatientApi, errorNotice, formatDuration, pageNotice, prettyBody } from '../patient-api';

@Component({
  selector: 'app-dead-letters',
  imports: [DatePipe],
  templateUrl: './dead-letters.html',
})
export class DeadLetters implements OnInit {
  private readonly api = inject(PatientApi);

  protected readonly deadLetters = signal<DeadLetter[]>([]);
  protected readonly notice = pageNotice();
  protected readonly loading = signal(false);
  protected readonly clearing = signal(false);

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
        this.loading.set(false);
      },
      error: err => {
        this.notice.set(errorNotice(err));
        this.loading.set(false);
      },
    });
  }

  /** Time from enqueue to dead-lettering, e.g. "1m 3s". */
  protected took(deadLetter: DeadLetter) {
    return deadLetter.deadLetteredTime ? formatDuration(deadLetter.enqueuedTime, deadLetter.deadLetteredTime) : '—';
  }

  protected readonly prettyBody = prettyBody;
}
