package com.example.patientservice.pending;

import java.time.OffsetDateTime;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;

public record PendingMessage(String messageId, long sequenceNumber, long deliveryCount, String state,
		OffsetDateTime enqueuedTime, String body) {

	static PendingMessage from(ServiceBusReceivedMessage message) {
		return new PendingMessage(message.getMessageId(), message.getSequenceNumber(), message.getDeliveryCount(),
				message.getState().toString(), message.getEnqueuedTime(), message.getBody().toString());
	}

}
