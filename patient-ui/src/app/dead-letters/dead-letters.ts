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

  ngOnInit() {
    this.load();
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
