package com.example.patientservice.patient;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

	private final PatientRepository patients;

	public PatientController(PatientRepository patients) {
		this.patients = patients;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Patient register(@Valid @RequestBody RegisterPatientRequest request) {
		if (patients.existsByNationalIdentity(request.nationalIdentity())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "A patient with this national identity already exists");
		}
		return patients.save(new Patient(request.name(), request.nationalIdentity()));
	}

	@GetMapping
	public List<Patient> list() {
		return patients.findAll();
	}

	public record RegisterPatientRequest(@NotBlank String name, @NotBlank String nationalIdentity) {
	}

}
