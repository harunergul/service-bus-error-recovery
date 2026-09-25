import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Notice, Patient, PatientApi, errorNotice } from '../patient-api';

@Component({
  selector: 'app-patient-record',
  imports: [FormsModule],
  templateUrl: './patient-record.html',
})
export class PatientRecord implements OnInit {
  private readonly api = inject(PatientApi);

  protected readonly patients = signal<Patient[]>([]);
  protected readonly notice = signal<Notice | null>(null);

  protected name = '';
  protected nationalIdentity = '';

  ngOnInit() {
    this.loadPatients();
  }

  protected register() {
    this.api.registerPatient(this.name, this.nationalIdentity).subscribe({
      next: patient => {
        this.notice.set({ kind: 'success', text: `Registered ${patient.name} (id ${patient.id})` });
        this.name = '';
        this.nationalIdentity = '';
        this.loadPatients();
      },
      error: err => this.notice.set(errorNotice(err)),
    });
  }

  private loadPatients() {
    this.api.listPatients().subscribe({
      next: patients => this.patients.set(patients),
      error: err => this.notice.set(errorNotice(err)),
    });
  }
}
