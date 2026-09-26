import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { errorNotice, pageNotice } from '../patient-api';
import { OUTAGE_FAILURES, OutageFailure, OutageState } from './outage-state';

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
export class NotificationOutage {
  protected readonly outage = inject(OutageState);
  protected readonly notice = pageNotice();
  protected readonly failures = OUTAGE_FAILURES;
  protected seconds = 60;
  protected failure: OutageFailure = 503;

  protected start() {
    this.outage.start(this.seconds, this.failure).subscribe({ error: err => this.notice.set(errorNotice(err)) });
  }

  protected end() {
    this.outage.end().subscribe({ error: err => this.notice.set(errorNotice(err)) });
  }
}
