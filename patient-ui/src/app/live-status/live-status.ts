import { Component, computed, input } from '@angular/core';

export type LiveState = 'connecting' | 'live' | 'offline';

/** Shows whether a page's list is kept up to date on its own, next to the page's buttons. */
@Component({
  selector: 'app-live-status',
  template: `<span class="live-status" [class]="state()" [title]="detail()">{{ label() }}</span>`,
  styleUrl: './live-status.css',
})
export class LiveStatus {
  readonly state = input.required<LiveState>();
  /** Shown on hover, e.g. how the list is kept up to date. */
  readonly detail = input('');

  protected readonly label = computed(() => {
    switch (this.state()) {
      case 'connecting':
        return 'Connecting…';
      case 'live':
        return 'Live';
      case 'offline':
        return 'Offline, retrying…';
    }
  });
}
