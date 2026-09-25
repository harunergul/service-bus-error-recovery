package com.example.patientservice.deadletter;

import java.time.OffsetDateTime;
import java.util.List;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.models.SubQueue;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view of the subscription's dead-letter queue. Peeking does not lock or remove
 * messages, so they stay in the queue.
 */
@RestController
@RequestMapping("/api/dead-letters")
public class DeadLetterController {

	private final ServiceBusReceiverClient receiver;

	public DeadLetterController(@Value("${servicebus.connection-string}") String connectionString,
			@Value("${servicebus.topic}") String topic, @Value("${servicebus.subscription}") String subscription) {
		this.receiver = new ServiceBusClientBuilder().connectionString(connectionString)
			.receiver()
			.topicName(topic)
			.subscriptionName(subscription)
			.subQueue(SubQueue.DEAD_LETTER_QUEUE)
			.buildClient();
	}

	@GetMapping
	public List<DeadLetter> list(@RequestParam(defaultValue = "50") int max) {
		// Always peek from the first sequence number; a plain peek continues where the last one stopped.
		return receiver.peekMessages(max, 0).stream().map(DeadLetter::from).toList();
	}

	@PreDestroy
	void close() {
		receiver.close();
	}

	public record DeadLetter(String messageId, long sequenceNumber, long deliveryCount, String reason,
			String description, OffsetDateTime enqueuedTime, String body) {

		static DeadLetter from(ServiceBusReceivedMessage message) {
			return new DeadLetter(message.getMessageId(), message.getSequenceNumber(), message.getDeliveryCount(),
					message.getDeadLetterReason(), message.getDeadLetterErrorDescription(), message.getEnqueuedTime(),
					message.getBody().toString());
		}

	}

}
