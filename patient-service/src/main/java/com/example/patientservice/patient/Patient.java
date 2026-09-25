package com.example.patientservice.patient;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Patient {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String nationalIdentity;

	protected Patient() {
	}

	public Patient(String name, String nationalIdentity) {
		this.name = name;
		this.nationalIdentity = nationalIdentity;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getNationalIdentity() {
		return nationalIdentity;
	}

}
