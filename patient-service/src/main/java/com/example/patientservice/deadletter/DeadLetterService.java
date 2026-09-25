package com.example.patientservice.deadletter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
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

	private static final int PAGE_SIZE = 100;

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

	/** The newest {@code max} dead letters, newest first. */
	public List<DeadLetter> peek(int max) {
		// Service Bus only peeks oldest-first, so read the whole queue before picking the newest.
		// Always start from an explicit sequence number; a plain peek continues where the last one stopped.
		List<DeadLetter> all = new ArrayList<>();
		long from = 0;
		while (true) {
			List<ServiceBusReceivedMessage> page = receiver.peekMessages(PAGE_SIZE, from).stream().toList();
			if (page.isEmpty()) {
				break;
			}
			page.stream().map(DeadLetter::from).forEach(all::add);
			from = page.get(page.size() - 1).getSequenceNumber() + 1;
		}
		return all.stream().sorted(Comparator.comparing(DeadLetter::enqueuedTime).reversed()).limit(max).toList();
	}

	@PreDestroy
	void close() {
		receiver.close();
	}

}
