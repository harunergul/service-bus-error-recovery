package com.example.patientservice.pending;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pending-messages")
public class PendingMessageController {

	private final PendingMessageService pendingMessages;

	public PendingMessageController(PendingMessageService pendingMessages) {
		this.pendingMessages = pendingMessages;
	}

	@GetMapping
	public List<PendingMessage> list(@RequestParam(defaultValue = "50") int max) {
		return pendingMessages.peek(max);
	}

	/** Deletes the pending messages, for local testing. */
	@DeleteMapping
	public Cleared clear() {
		return new Cleared(pendingMessages.clear());
	}

	public record Cleared(int cleared) {
	}

}
