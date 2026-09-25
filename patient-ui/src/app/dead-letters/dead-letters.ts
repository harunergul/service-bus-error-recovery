import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { DeadLetter, PatientApi, errorNotice, pageNotice } from '../patient-api';

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

  protected prettyBody(body: string) {
    try {
      return JSON.stringify(JSON.parse(body), null, 2);
    } catch {
      return body;
    }
  }
}
