import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Patient, PatientApi, errorMessage } from '../patient-api';

@Component({
  selector: 'app-patient-record',
  imports: [FormsModule],
  templateUrl: './patient-record.html',
})
export class PatientRecord implements OnInit {
  private readonly api = inject(PatientApi);

  protected readonly patients = signal<Patient[]>([]);
  protected readonly message = signal('');

  protected name = '';
  protected nationalIdentity = '';

  ngOnInit() {
    this.loadPatients();
  }

  protected register() {
    this.api.registerPatient(this.name, this.nationalIdentity).subscribe({
      next: patient => {
        this.message.set(`Registered ${patient.name} (id ${patient.id})`);
        this.name = '';
        this.nationalIdentity = '';
        this.loadPatients();
      },
      error: err => this.message.set(errorMessage(err)),
    });
  }

  private loadPatients() {
    this.api.listPatients().subscribe({
      next: patients => this.patients.set(patients),
      error: err => this.message.set(errorMessage(err)),
    });
  }
}
