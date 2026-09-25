package com.example.patientservice.deadletter;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dead-letters")
public class DeadLetterController {

	private final DeadLetterService deadLetters;

	public DeadLetterController(DeadLetterService deadLetters) {
		this.deadLetters = deadLetters;
	}

	@GetMapping
	public List<DeadLetter> list(@RequestParam(defaultValue = "50") int max) {
		return deadLetters.peek(max);
	}

}
