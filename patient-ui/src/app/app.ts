import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { OutageState } from './notification-outage/outage-state';

type Theme = 'light' | 'dark';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly theme = signal<Theme>(App.savedTheme());
  protected readonly outage = inject(OutageState);

  constructor() {
    this.apply();
  }

  protected toggleTheme() {
    this.theme.update(theme => (theme === 'light' ? 'dark' : 'light'));
    this.apply();
    try {
      localStorage.setItem('theme', this.theme());
    } catch {
      // Storage unavailable (e.g. private mode): the choice just isn't remembered.
    }
  }

  protected endOutage() {
    this.outage.end().subscribe({ error: () => {} });
  }

  private apply() {
    document.documentElement.dataset['theme'] = this.theme();
  }

  private static savedTheme(): Theme {
    try {
      return localStorage.getItem('theme') === 'dark' ? 'dark' : 'light';
    } catch {
      return 'light';
    }
  }
}
