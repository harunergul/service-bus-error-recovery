import { Component, computed, input, model, signal } from '@angular/core';
import { Patient } from '../patient-api';

const MAX_MATCHES = 8;

/** Case- and accent-insensitive, so "ayse" finds "Ayşe" and "i" finds "İ" or "ı". */
function fold(text: string) {
  return text.normalize('NFD').replace(/\p{M}/gu, '').replace(/ı/g, 'i').toLowerCase();
}

/**
 * Type-ahead patient search by name or National Identity. Scales to many patients where a
 * plain <select> doesn't: only the best few matches are shown.
 */
@Component({
  selector: 'app-patient-picker',
  templateUrl: './patient-picker.html',
  styleUrl: './patient-picker.css',
})
export class PatientPicker {
  readonly patients = input.required<Patient[]>();
  readonly selectedId = model<number | null>(null);

  protected readonly query = signal('');
  protected readonly open = signal(false);
  protected readonly active = signal(0);

  protected readonly selected = computed(() => this.patients().find(p => p.id === this.selectedId()) ?? null);

  private readonly found = computed(() => {
    const q = fold(this.query().trim());
    const all = this.patients();
    return q ? all.filter(p => fold(p.name).includes(q) || p.nationalIdentity.includes(q)) : all;
  });

  protected readonly matches = computed(() => this.found().slice(0, MAX_MATCHES));
  protected readonly hiddenCount = computed(() => this.found().length - this.matches().length);

  protected onInput(value: string) {
    this.query.set(value);
    this.selectedId.set(null);
    this.active.set(0);
    this.open.set(true);
  }

  protected onKeydown(event: KeyboardEvent) {
    const count = this.matches().length;
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        if (!this.open()) this.open.set(true);
        else if (count) this.active.update(i => (i + 1) % count);
        break;
      case 'ArrowUp':
        event.preventDefault();
        if (count) this.active.update(i => (i - 1 + count) % count);
        break;
      case 'Enter':
        if (this.open() && count) {
          event.preventDefault();
          this.choose(this.matches()[this.active()]);
        }
        break;
      case 'Escape':
        this.open.set(false);
        break;
    }
  }

  protected choose(patient: Patient) {
    this.selectedId.set(patient.id);
    this.query.set('');
    this.open.set(false);
  }

  protected clear() {
    this.selectedId.set(null);
    this.query.set('');
  }

  protected display() {
    const patient = this.selected();
    return patient ? `${patient.name} (${patient.nationalIdentity})` : this.query();
  }
}
