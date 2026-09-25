import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { DeadLetter, PatientApi, errorMessage } from '../patient-api';

@Component({
  selector: 'app-dead-letters',
  imports: [DatePipe],
  templateUrl: './dead-letters.html',
})
export class DeadLetters implements OnInit {
  private readonly api = inject(PatientApi);

  protected readonly deadLetters = signal<DeadLetter[]>([]);
  protected readonly message = signal('');

  ngOnInit() {
    this.load();
  }

  protected load() {
    this.message.set('');
    this.api.listDeadLetters().subscribe({
      next: deadLetters => this.deadLetters.set(deadLetters),
      error: err => this.message.set(errorMessage(err)),
    });
  }
}
