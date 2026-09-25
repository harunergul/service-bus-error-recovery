package com.example.patientservice.move;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient-moves")
public class PatientMoveController {

	private final PatientMoveService service;

	public PatientMoveController(PatientMoveService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PatientMove record(@Valid @RequestBody RecordMoveRequest request) {
		return service.record(request.patientId(), request.room());
	}

	public record RecordMoveRequest(@NotNull Long patientId, @NotBlank String room) {
	}

}
