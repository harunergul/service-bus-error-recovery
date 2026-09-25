package com.example.patientservice.listening;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.example.patientservice.move.PatientMoved;
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
 * Auto-complete is off: a message is completed only after the Feign call succeeds. On failure
 * it is abandoned, so Service Bus redelivers it and dead-letters it after MaxDeliveryCount.
 */
@Component
@ConditionalOnProperty(name = "listener.enabled", havingValue = "true", matchIfMissing = true)
public class PatientMovedListener {

	private static final Logger log = LoggerFactory.getLogger(PatientMovedListener.class);

	private final ServiceBusProcessorClient processor;

	private final NotificationServiceClient notificationService;

	private final JsonMapper jsonMapper;

	public PatientMovedListener(@Value("${servicebus.connection-string}") String connectionString,
			@Value("${servicebus.topic}") String topic, @Value("${servicebus.subscription}") String subscription,
			NotificationServiceClient notificationService, JsonMapper jsonMapper) {
		this.notificationService = notificationService;
		this.jsonMapper = jsonMapper;
		this.processor = new ServiceBusClientBuilder().connectionString(connectionString)
			.processor()
			.topicName(topic)
			.subscriptionName(subscription)
			.disableAutoComplete()
			.processMessage(this::onMessage)
			.processError(this::onError)
			.buildProcessorClient();
	}

	@EventListener(ApplicationReadyEvent.class)
	void start() {
		processor.start();
		log.info("Listening on subscription {}", processor.getSubscriptionName());
	}

	@PreDestroy
	void stop() {
		processor.close();
	}

	private void onMessage(ServiceBusReceivedMessageContext context) {
		ServiceBusReceivedMessage message = context.getMessage();
		try {
			PatientMoved event = jsonMapper.readValue(message.getBody().toString(), PatientMoved.class);
			notificationService.patientMoved(event);
			context.complete();
			log.info("Delivered {} (delivery {})", event, message.getDeliveryCount() + 1);
		}
		catch (Exception ex) {
			log.warn("Failed to deliver message {} (delivery {}), abandoning: {}", message.getMessageId(),
					message.getDeliveryCount() + 1, ex.getMessage());
			context.abandon();
		}
	}

	private void onError(ServiceBusErrorContext context) {
		log.error("Service Bus error from {}: {}", context.getErrorSource(), context.getException().getMessage());
	}

}
