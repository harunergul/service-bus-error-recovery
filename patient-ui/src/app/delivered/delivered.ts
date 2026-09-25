import { HttpClient } from '@angular/common/http';
import { Component, ElementRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { errorNotice, formatDuration, pageNotice, prettyBody } from '../patient-api';

interface Delivered {
  id: number;
  moveId: number;
  patientId: number;
  room: string;
  movedAt: string;
  receivedAt: string;
  duplicate: boolean;
  body: string;
}

/** PatientMoved events notification-service accepted, as it recorded them. */
@Component({
  selector: 'app-delivered',
  imports: [DatePipe],
  templateUrl: './delivered.html',
  styleUrl: './delivered.css',
})
export class DeliveredMessages implements OnInit {
  private readonly http = inject(HttpClient);

  protected readonly delivered = signal<Delivered[]>([]);
  protected readonly notice = pageNotice();
  protected readonly loading = signal(false);
  protected readonly prettyBody = prettyBody;

  /** The row whose message is open in the dialog. */
  protected readonly viewing = signal<Delivered | null>(null);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('messageDialog');

  ngOnInit() {
    this.load();
  }

  protected load() {
    this.notice.set(null);
    this.loading.set(true);
    this.http.get<Delivered[]>('/notification/delivered').subscribe({
      next: delivered => {
        this.delivered.set(delivered);
        this.loading.set(false);
      },
      error: err => {
        this.notice.set(errorNotice(err));
        this.loading.set(false);
      },
    });
  }

  /** Time from recording the move to notification-service accepting it, e.g. "1m 3s". */
  protected took(delivered: Delivered) {
    return formatDuration(delivered.movedAt, delivered.receivedAt);
  }

  protected view(delivered: Delivered) {
    this.viewing.set(delivered);
    this.dialog().nativeElement.showModal();
  }

  protected close() {
    this.dialog().nativeElement.close();
  }

  /** Closes when the click lands on the backdrop, outside the dialog's content. */
  protected closeOnBackdrop(event: MouseEvent) {
    if (event.target === this.dialog().nativeElement) {
      this.close();
    }
  }
}
