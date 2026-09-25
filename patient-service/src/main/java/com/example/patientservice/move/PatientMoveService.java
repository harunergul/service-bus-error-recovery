package com.example.patientservice.move;

import java.time.Instant;

import com.example.patientservice.patient.PatientRepository;
import com.example.patientservice.publishing.PatientMovedPublisher;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PatientMoveService {

	private final PatientRepository patients;

	private final PatientMoveRepository moves;

	private final PatientMovedPublisher publisher;

	public PatientMoveService(PatientRepository patients, PatientMoveRepository moves, PatientMovedPublisher publisher) {
		this.patients = patients;
		this.moves = moves;
		this.publisher = publisher;
	}

	// Deliberately not @Transactional: save, then publish. If publishing fails the move
	// stays saved without an event (known gap, see docs/adr/0001).
	public PatientMove record(Long patientId, String room) {
		if (!patients.existsById(patientId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient " + patientId + " not found");
		}
		PatientMove move = moves.save(new PatientMove(patientId, room, Instant.now()));
		publisher.publish(PatientMoved.from(move));
		return move;
	}

}
