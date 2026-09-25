package com.example.patientservice.move;

import java.time.Instant;

/**
 * Event published for every recorded Patient Move. Carries references only, never the
 * patient's personal details (see CONTEXT.md).
 */
public record PatientMoved(Long moveId, Long patientId, String room, Instant movedAt) {

	public static PatientMoved from(PatientMove move) {
		return new PatientMoved(move.getId(), move.getPatientId(), move.getRoom(), move.getMovedAt());
	}

}
