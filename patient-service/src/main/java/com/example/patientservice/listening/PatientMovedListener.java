package com.example.patientservice.listening;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.example.patientservice.move.PatientMoved;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Consumes PatientMoved from the topic subscription and forwards it to notification-service.
 * Auto-complete is off: a message is completed only after the Feign call succeeds. A failed
 * call is first retried in place (Resilience4j retry "notification-service", configured in
 * application.properties; switched off with {@code listener.notification-retry.enabled=false}).
 * If it still fails the message is abandoned, so Service Bus
 * redelivers it and dead-letters it after MaxDeliveryCount.
 * <p>
 * The subscription requires sessions (session id = patient id). Each session is locked to one
 * receiver and delivered in order, so a Patient's moves are handled one after another while
 * different Patients are handled in parallel, up to {@code listener.max-concurrent-sessions}.
 */
@Component
@ConditionalOnProperty(name = "listener.enabled", havingValue = "true", matchIfMissing = true)
public class PatientMovedListener {

	private static final Logger log = LoggerFactory.getLogger(PatientMovedListener.class);

	private final ServiceBusProcessorClient processor;

	private final NotificationServiceClient notificationService;

	private final Retry notificationRetry;

	private final boolean notificationRetryEnabled;

	private final JsonMapper jsonMapper;

	public PatientMovedListener(@Value("${servicebus.connection-string}") String connectionString,
			@Value("${servicebus.topic}") String topic, @Value("${servicebus.subscription}") String subscription,
			@Value("${listener.max-concurrent-sessions}") int maxConcurrentSessions,
			@Value("${listener.notification-retry.enabled}") boolean notificationRetryEnabled,
			NotificationServiceClient notificationService, RetryRegistry retryRegistry, JsonMapper jsonMapper) {
		this.notificationService = notificationService;
		this.notificationRetryEnabled = notificationRetryEnabled;
		this.notificationRetry = retryRegistry.retry("notification-service");
		this.notificationRetry.getEventPublisher()
			.onRetry(event -> log.warn("Call to notification-service failed (attempt {}), retrying in {}: {}",
					event.getNumberOfRetryAttempts(), event.getWaitInterval(), event.getLastThrowable().getMessage()));
		this.jsonMapper = jsonMapper;
		this.processor = new ServiceBusClientBuilder().connectionString(connectionString)
			.sessionProcessor()
			.topicName(topic)
			.subscriptionName(subscription)
			.maxConcurrentSessions(maxConcurrentSessions)
			.disableAutoComplete()
			.processMessage(this::onMessage)
			.processError(this::onError)
			.buildProcessorClient();
	}

	@EventListener(ApplicationReadyEvent.class)
	void start() {
		processor.start();
		log.info("Listening on subscription {} (notification-service retry {})", processor.getSubscriptionName(),
				notificationRetryEnabled ? "on" : "off");
	}

	@PreDestroy
	void stop() {
		processor.close();
	}

	private void onMessage(ServiceBusReceivedMessageContext context) {
		ServiceBusReceivedMessage message = context.getMessage();
		try {
			PatientMoved event = jsonMapper.readValue(message.getBody().toString(), PatientMoved.class);
			if (notificationRetryEnabled) {
				notificationRetry.executeRunnable(() -> notificationService.patientMoved(event));
			}
			else {
				notificationService.patientMoved(event);
			}
			context.complete();
			log.info("Delivered {} (session {}, delivery {})", event, message.getSessionId(),
					message.getDeliveryCount() + 1);
		}
		catch (Exception ex) {
			log.warn("Failed to deliver message {} (session {}, delivery {}), abandoning: {}", message.getMessageId(),
					message.getSessionId(), message.getDeliveryCount() + 1, ex.getMessage());
			context.abandon();
		}
	}

	private void onError(ServiceBusErrorContext context) {
		log.error("Service Bus error from {}: {}", context.getErrorSource(), context.getException().getMessage());
	}

}
