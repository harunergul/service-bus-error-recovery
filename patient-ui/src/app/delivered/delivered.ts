import { HttpClient } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Cleared, errorNotice, formatDuration, pageNotice, prettyBody } from '../patient-api';
import { LiveState, LiveStatus } from '../live-status/live-status';

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

/** How long to wait before reconnecting, e.g. while notification-service restarts. */
const RECONNECT_MS = 2000;

/**
 * PatientMoved events notification-service accepted, as it recorded them. notification-service
 * sends the whole list over a WebSocket when connected and again after every change.
 */
@Component({
  selector: 'app-delivered',
  imports: [DatePipe, LiveStatus],
  templateUrl: './delivered.html',
  styleUrl: './delivered.css',
})
export class DeliveredMessages {
  private readonly http = inject(HttpClient);

  protected readonly delivered = signal<Delivered[]>([]);
  protected readonly notice = pageNotice();
  protected readonly clearing = signal(false);
  protected readonly live = signal<LiveState>('connecting');
  protected readonly prettyBody = prettyBody;

  /** The row whose message is open in the dialog. */
  protected readonly viewing = signal<Delivered | null>(null);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('messageDialog');

  constructor() {
    let socket: WebSocket | undefined;
    let reconnect: ReturnType<typeof setTimeout> | undefined;
    const connect = () => {
      const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
      socket = new WebSocket(`${scheme}://${location.host}/notification/delivered/live`);
      socket.onmessage = event => {
        this.delivered.set(JSON.parse(event.data));
        this.live.set('live');
      };
      socket.onclose = () => {
        this.live.set('offline');
        reconnect = setTimeout(connect, RECONNECT_MS);
      };
    };
    connect();
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(reconnect);
      if (socket) {
        socket.onclose = null;
        socket.close();
      }
    });
  }

  protected clear() {
    if (!confirm('Delete all delivered messages? This cannot be undone.')) return;
    this.clearing.set(true);
    this.http.delete<Cleared>('/notification/delivered').subscribe({
      // The emptied list arrives over the WebSocket
      next: ({ cleared }) => {
        this.clearing.set(false);
        this.notice.set({ kind: 'success', text: `Cleared ${cleared} delivered messages` });
      },
      error: err => {
        this.clearing.set(false);
        this.notice.set(errorNotice(err));
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
