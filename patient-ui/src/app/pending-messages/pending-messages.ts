import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { PatientApi, PendingMessage, errorNotice, pageNotice, prettyBody } from '../patient-api';

@Component({
  selector: 'app-pending-messages',
  imports: [DatePipe],
  templateUrl: './pending-messages.html',
})
export class PendingMessages implements OnInit {
  private readonly api = inject(PatientApi);

  protected readonly messages = signal<PendingMessage[]>([]);
  protected readonly notice = pageNotice();
  protected readonly loading = signal(false);
  protected readonly prettyBody = prettyBody;

  ngOnInit() {
    this.load();
  }

  protected load() {
    this.notice.set(null);
    this.loading.set(true);
    this.api.listPendingMessages().subscribe({
      next: messages => {
        this.messages.set(messages);
        this.loading.set(false);
      },
      error: err => {
        this.notice.set(errorNotice(err));
        this.loading.set(false);
      },
    });
  }
}
