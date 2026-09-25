package com.example.patientservice.move;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "patient_location")
public class PatientMove {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long patientId;

	@Column(nullable = false)
	private String room;

	@Column(nullable = false)
	private Instant movedAt;

	protected PatientMove() {
	}

	public PatientMove(Long patientId, String room, Instant movedAt) {
		this.patientId = patientId;
		this.room = room;
		this.movedAt = movedAt;
	}

	public Long getId() {
		return id;
	}

	public Long getPatientId() {
		return patientId;
	}

	public String getRoom() {
		return room;
	}

	public Instant getMovedAt() {
		return movedAt;
	}

}
