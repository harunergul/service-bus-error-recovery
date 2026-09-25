package com.example.patientservice.pending;

import java.util.List;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Read-only access to the messages still waiting in the subscription, not yet delivered and not
 * dead-lettered. Peeking also returns messages currently locked by the listener, and does not
 * lock, remove or count as a Delivery.
 */
@Service
public class PendingMessageService {

	private final ServiceBusReceiverClient receiver;

	public PendingMessageService(@Value("${servicebus.connection-string}") String connectionString,
			@Value("${servicebus.topic}") String topic, @Value("${servicebus.subscription}") String subscription) {
		this.receiver = new ServiceBusClientBuilder().connectionString(connectionString)
			.receiver()
			.topicName(topic)
			.subscriptionName(subscription)
			.buildClient();
	}

	public List<PendingMessage> peek(int max) {
		// Always peek from the first sequence number; a plain peek continues where the last one stopped.
		return receiver.peekMessages(max, 0).stream().map(PendingMessage::from).toList();
	}

	@PreDestroy
	void close() {
		receiver.close();
	}

}
