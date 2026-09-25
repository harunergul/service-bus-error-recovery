import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Patient, PatientApi, errorMessage } from '../patient-api';

@Component({
  selector: 'app-patient-move',
  imports: [FormsModule],
  templateUrl: './patient-move.html',
})
export class PatientMove implements OnInit {
  private readonly api = inject(PatientApi);

  protected readonly patients = signal<Patient[]>([]);
  protected readonly message = signal('');

  protected patientId: number | null = null;
  protected room = '';

  ngOnInit() {
    this.api.listPatients().subscribe({
      next: patients => this.patients.set(patients),
      error: err => this.message.set(errorMessage(err)),
    });
  }

  protected recordMove() {
    if (this.patientId === null) return;
    this.api.recordMove(this.patientId, this.room).subscribe({
      next: move => {
        this.message.set(`Recorded move ${move.id}: patient ${move.patientId} to room ${move.room}`);
        this.room = '';
      },
      error: err => this.message.set(errorMessage(err)),
    });
  }
}
