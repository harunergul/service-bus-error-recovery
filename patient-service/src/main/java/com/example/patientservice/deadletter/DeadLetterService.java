package com.example.patientservice.deadletter;

import java.util.List;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.models.SubQueue;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Read-only access to the subscription's dead-letter queue. Peeking does not lock or remove
 * messages, so they stay in the queue.
 */
@Service
public class DeadLetterService {

	private final ServiceBusReceiverClient receiver;

	public DeadLetterService(@Value("${servicebus.connection-string}") String connectionString,
			@Value("${servicebus.topic}") String topic, @Value("${servicebus.subscription}") String subscription) {
		this.receiver = new ServiceBusClientBuilder().connectionString(connectionString)
			.receiver()
			.topicName(topic)
			.subscriptionName(subscription)
			.subQueue(SubQueue.DEAD_LETTER_QUEUE)
			.buildClient();
	}

	public List<DeadLetter> peek(int max) {
		// Always peek from the first sequence number; a plain peek continues where the last one stopped.
		return receiver.peekMessages(max, 0).stream().map(DeadLetter::from).toList();
	}

	@PreDestroy
	void close() {
		receiver.close();
	}

}
