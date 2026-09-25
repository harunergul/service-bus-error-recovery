package com.example.patientservice.deadletter;

import java.time.OffsetDateTime;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.example.patientservice.listening.PatientMovedListener;

/**
 * @param deadLetteredTime when the last Delivery failed, stamped by the listener; null for messages dead-lettered
 * before the listener started stamping it
 */
public record DeadLetter(String messageId, String sessionId, long sequenceNumber, long deliveryCount, String reason, String description,
		OffsetDateTime enqueuedTime, OffsetDateTime deadLetteredTime, String body) {

	static DeadLetter from(ServiceBusReceivedMessage message) {
		Object lastFailedAt = message.getApplicationProperties().get(PatientMovedListener.LAST_FAILED_AT);
		return new DeadLetter(message.getMessageId(), message.getSessionId(), message.getSequenceNumber(), message.getDeliveryCount(),
				message.getDeadLetterReason(), message.getDeadLetterErrorDescription(), message.getEnqueuedTime(),
				lastFailedAt == null ? null : OffsetDateTime.parse(lastFailedAt.toString()), message.getBody().toString());
	}

}
