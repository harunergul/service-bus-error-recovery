import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Notice, Patient, PatientApi, errorNotice } from '../patient-api';
import { PatientPicker } from '../patient-picker/patient-picker';

@Component({
  selector: 'app-patient-move',
  imports: [FormsModule, RouterLink, PatientPicker],
  templateUrl: './patient-move.html',
})
export class PatientMove implements OnInit {
  private readonly api = inject(PatientApi);

  protected readonly patients = signal<Patient[]>([]);
  protected readonly notice = signal<Notice | null>(null);

  protected patientId: number | null = null;
  protected room = '';

  ngOnInit() {
    this.api.listPatients().subscribe({
      next: patients => this.patients.set(patients),
      error: err => this.notice.set(errorNotice(err)),
    });
  }

  protected recordMove() {
    if (this.patientId === null) return;
    this.api.recordMove(this.patientId, this.room).subscribe({
      next: move => {
        this.notice.set({
          kind: 'success',
          text: `Recorded move ${move.id}: patient ${move.patientId} to room ${move.room}`,
        });
        this.room = '';
      },
      error: err => this.notice.set(errorNotice(err)),
    });
  }
}
