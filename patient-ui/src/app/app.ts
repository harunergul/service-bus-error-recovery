import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { DeadLetter, Patient, PatientApi } from './patient-api';

@Component({
  selector: 'app-root',
  imports: [FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  private readonly api = inject(PatientApi);

  protected readonly patients = signal<Patient[]>([]);
  protected readonly deadLetters = signal<DeadLetter[]>([]);
  protected readonly message = signal('');

  protected name = '';
  protected nationalIdentity = '';
  protected patientId: number | null = null;
  protected room = '';

  ngOnInit() {
    this.loadPatients();
    this.loadDeadLetters();
  }

  protected register() {
    this.api.registerPatient(this.name, this.nationalIdentity).subscribe({
      next: patient => {
        this.message.set(`Registered ${patient.name} (id ${patient.id})`);
        this.name = '';
        this.nationalIdentity = '';
        this.loadPatients();
      },
      error: err => this.showError(err),
    });
  }

  protected recordMove() {
    if (this.patientId === null) return;
    this.api.recordMove(this.patientId, this.room).subscribe({
      next: move => {
        this.message.set(`Recorded move ${move.id}: patient ${move.patientId} to room ${move.room}`);
        this.room = '';
      },
      error: err => this.showError(err),
    });
  }

  protected loadDeadLetters() {
    this.api.listDeadLetters().subscribe({
      next: deadLetters => this.deadLetters.set(deadLetters),
      error: err => this.showError(err),
    });
  }

  private loadPatients() {
    this.api.listPatients().subscribe({
      next: patients => this.patients.set(patients),
      error: err => this.showError(err),
    });
  }

  private showError(err: HttpErrorResponse) {
    this.message.set(`Error ${err.status}: ${err.error?.message ?? err.message}`);
  }
}
