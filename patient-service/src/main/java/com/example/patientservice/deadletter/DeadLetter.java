package com.example.patientservice.deadletter;

import java.time.OffsetDateTime;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;

public record DeadLetter(String messageId, String sessionId, long sequenceNumber, long deliveryCount, String reason, String description,
		OffsetDateTime enqueuedTime, String body) {

	static DeadLetter from(ServiceBusReceivedMessage message) {
		return new DeadLetter(message.getMessageId(), message.getSessionId(), message.getSequenceNumber(), message.getDeliveryCount(),
				message.getDeadLetterReason(), message.getDeadLetterErrorDescription(), message.getEnqueuedTime(),
				message.getBody().toString());
	}

}
