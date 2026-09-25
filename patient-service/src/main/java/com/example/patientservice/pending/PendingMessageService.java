package com.example.patientservice.pending;

import java.time.Duration;
import java.util.List;

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSessionReceiverClient;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Access to the messages still waiting in the subscription, not yet delivered and not
 * dead-lettered. Peeking also returns messages currently locked by the listener, and does not
 * lock, remove or count as a Delivery. {@link #clear()} deletes them, for local testing.
 */
@Service
public class PendingMessageService {

	private static final Logger log = LoggerFactory.getLogger(PendingMessageService.class);

	/** How long to wait for another session with messages before deciding there are none left. */
	private static final Duration NO_MORE_SESSIONS = Duration.ofSeconds(3);

	private final ServiceBusReceiverClient receiver;

	private final ServiceBusClientBuilder.ServiceBusSessionReceiverClientBuilder sessionReceiverBuilder;

	public PendingMessageService(@Value("${servicebus.connection-string}") String connectionString,
			@Value("${servicebus.topic}") String topic, @Value("${servicebus.subscription}") String subscription) {
		this.receiver = new ServiceBusClientBuilder().connectionString(connectionString)
			.receiver()
			.topicName(topic)
			.subscriptionName(subscription)
			.buildClient();
		this.sessionReceiverBuilder = new ServiceBusClientBuilder().connectionString(connectionString)
			.retryOptions(new AmqpRetryOptions().setTryTimeout(NO_MORE_SESSIONS).setMaxRetries(0))
			.sessionReceiver()
			.topicName(topic)
			.subscriptionName(subscription)
			.receiveMode(ServiceBusReceiveMode.RECEIVE_AND_DELETE);
	}

	public List<PendingMessage> peek(int max) {
		// Always peek from the first sequence number; a plain peek continues where the last one stopped.
		return receiver.peekMessages(max, 0).stream().map(PendingMessage::from).toList();
	}

	/**
	 * Deletes the pending messages and returns how many. The subscription requires sessions, so it
	 * takes each Patient's session in turn and drains it. A session the listener holds at the time
	 * cannot be taken, so its messages stay.
	 */
	public int clear() {
		int cleared = 0;
		try (ServiceBusSessionReceiverClient sessions = sessionReceiverBuilder.buildClient()) {
			while (true) {
				ServiceBusReceiverClient session;
				try {
					session = sessions.acceptNextSession();
				}
				catch (RuntimeException noMoreSessions) {
					break;
				}
				try (session) {
					int received;
					do {
						received = (int) session.receiveMessages(100, Duration.ofSeconds(1)).stream().count();
						cleared += received;
					}
					while (received > 0);
				}
			}
		}
		log.warn("Cleared {} pending messages", cleared);
		return cleared;
	}

	@PreDestroy
	void close() {
		receiver.close();
	}

}
