import { Routes } from '@angular/router';
import { PatientRecord } from './patient-record/patient-record';
import { PatientMove } from './patient-move/patient-move';
import { PendingMessages } from './pending-messages/pending-messages';
import { DeliveredMessages } from './delivered/delivered';
import { DeadLetters } from './dead-letters/dead-letters';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'patients' },
  { path: 'patients', component: PatientRecord, title: 'Patient record' },
  { path: 'moves', component: PatientMove, title: 'Patient move' },
  { path: 'pending', component: PendingMessages, title: 'Pending messages' },
  { path: 'delivered', component: DeliveredMessages, title: 'Delivered' },
  { path: 'dead-letters', component: DeadLetters, title: 'Dead letters' },
  { path: '**', redirectTo: 'patients' },
];
