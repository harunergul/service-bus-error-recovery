import { HttpClient } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { errorNotice, pageNotice } from '../patient-api';

interface Outage {
  remainingSeconds: number;
  totalSeconds: number;
}

/**
 * Starts and ends a simulated outage of notification-service. The outage lives in
 * notification-service, so after a page refresh the countdown picks up where it was.
 */
@Component({
  selector: 'app-notification-outage',
  imports: [FormsModule],
  templateUrl: './notification-outage.html',
  styleUrl: './notification-outage.css',
})
export class NotificationOutage implements OnInit {
  private readonly http = inject(HttpClient);

  protected readonly notice = pageNotice();
  protected seconds = 60;

  /** When the outage ends (browser clock), or 0 when there is none. */
  private readonly endsAt = signal(0);
  protected readonly totalSeconds = signal(0);
  private readonly now = signal(Date.now());

  protected readonly remainingSeconds = computed(() => Math.max(0, Math.ceil((this.endsAt() - this.now()) / 1000)));
  protected readonly down = computed(() => this.remainingSeconds() > 0);
  protected readonly progress = computed(() =>
    this.totalSeconds() > 0 ? (this.remainingSeconds() / this.totalSeconds()) * 100 : 0,
  );
  protected readonly countdown = computed(() => {
    const s = this.remainingSeconds();
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  });

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), 250);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  ngOnInit() {
    this.http.get<Outage>('/notification/outage').subscribe({
      next: outage => this.show(outage),
      error: err => this.notice.set(errorNotice(err)),
    });
  }

  protected start() {
    this.http.put<Outage>('/notification/outage', { seconds: this.seconds }).subscribe({
      next: outage => this.show(outage),
      error: err => this.notice.set(errorNotice(err)),
    });
  }

  protected end() {
    this.http.delete<Outage>('/notification/outage').subscribe({
      next: outage => this.show(outage),
      error: err => this.notice.set(errorNotice(err)),
    });
  }

  private show(outage: Outage) {
    this.now.set(Date.now());
    this.endsAt.set(outage.remainingSeconds > 0 ? Date.now() + outage.remainingSeconds * 1000 : 0);
    this.totalSeconds.set(outage.totalSeconds);
  }
}
