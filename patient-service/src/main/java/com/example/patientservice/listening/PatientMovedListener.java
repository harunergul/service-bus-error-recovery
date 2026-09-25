package com.example.patientservice.listening;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.example.patientservice.move.PatientMoved;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
 * Around the retries sits a circuit breaker (Resilience4j "notification-service"; switched off with
 * {@code listener.notification-circuit-breaker.enabled=false}). When enough Deliveries fail it opens
 * and the processor stops receiving, so the messages wait in the subscription instead of using up
 * their Deliveries during an outage. While stopped, notification-service is probed with a HEAD
 * request every {@code listener.notification-circuit-breaker.probe-interval}; once it answers, the
 * breaker goes half-open and the processor starts again, letting the next real calls decide
 * whether it closes or opens again.
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

	private final CircuitBreaker notificationBreaker;

	private final boolean notificationBreakerEnabled;

	private final Duration probeInterval;

	/** Stops and restarts the processor and runs the probes, one thing at a time. */
	private final ScheduledExecutorService breakerExecutor = Executors
		.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "notification-breaker"));

	/** Only touched on the breakerExecutor thread. */
	private ScheduledFuture<?> probeTask;

	private final JsonMapper jsonMapper;

	public PatientMovedListener(@Value("${servicebus.connection-string}") String connectionString,
			@Value("${servicebus.topic}") String topic, @Value("${servicebus.subscription}") String subscription,
			@Value("${listener.max-concurrent-sessions}") int maxConcurrentSessions,
			@Value("${listener.notification-retry.enabled}") boolean notificationRetryEnabled,
			@Value("${listener.notification-circuit-breaker.enabled}") boolean notificationBreakerEnabled,
			@Value("${listener.notification-circuit-breaker.probe-interval}") Duration probeInterval,
			NotificationServiceClient notificationService, RetryRegistry retryRegistry,
			CircuitBreakerRegistry circuitBreakerRegistry, JsonMapper jsonMapper) {
		this.notificationService = notificationService;
		this.notificationRetryEnabled = notificationRetryEnabled;
		this.notificationRetry = retryRegistry.retry("notification-service");
		this.notificationRetry.getEventPublisher()
			.onRetry(event -> log.warn("Call to notification-service failed (attempt {}), retrying in {}: {}",
					event.getNumberOfRetryAttempts(), event.getWaitInterval(), event.getLastThrowable().getMessage()));
		this.notificationBreakerEnabled = notificationBreakerEnabled;
		this.probeInterval = probeInterval;
		this.notificationBreaker = circuitBreakerRegistry.circuitBreaker("notification-service");
		this.notificationBreaker.getEventPublisher().onStateTransition(event -> {
			log.info("notification-service circuit breaker {}", event.getStateTransition());
			if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
				breakerExecutor.execute(this::pause);
			}
		});
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
		log.info("Listening on subscription {} (notification-service retry {}, circuit breaker {})",
				processor.getSubscriptionName(), notificationRetryEnabled ? "on" : "off",
				notificationBreakerEnabled ? "on" : "off");
	}

	@PreDestroy
	void stop() {
		breakerExecutor.shutdownNow();
		processor.close();
	}

	private void onMessage(ServiceBusReceivedMessageContext context) {
		ServiceBusReceivedMessage message = context.getMessage();
		try {
			PatientMoved event = jsonMapper.readValue(message.getBody().toString(), PatientMoved.class);
			Runnable call = () -> notificationService.patientMoved(event);
			if (notificationRetryEnabled) {
				call = Retry.decorateRunnable(notificationRetry, call);
			}
			if (notificationBreakerEnabled) {
				// Outside the retry, so one failed Delivery counts once, however many Call Attempts it made
				call = CircuitBreaker.decorateRunnable(notificationBreaker, call);
			}
			call.run();
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

	/** The breaker opened: stop taking messages we could not deliver, and start probing. */
	private void pause() {
		processor.stop();
		log.warn("notification-service is unavailable, stopped receiving; probing every {}", probeInterval);
		if (probeTask != null) {
			probeTask.cancel(false);
		}
		probeTask = breakerExecutor.scheduleWithFixedDelay(this::probe, probeInterval.toMillis(),
				probeInterval.toMillis(), TimeUnit.MILLISECONDS);
	}

	private void probe() {
		try {
			notificationService.probe();
		}
		catch (FeignException.FeignClientException ex) {
			// A 4xx still means it answers
		}
		catch (Exception ex) {
			log.info("notification-service still unavailable: {}", ex.getMessage());
			return;
		}
		probeTask.cancel(false);
		probeTask = null;
		notificationBreaker.transitionToHalfOpenState();
		processor.start();
		log.info("notification-service answers again, receiving (circuit breaker half-open)");
	}

	private void onError(ServiceBusErrorContext context) {
		log.error("Service Bus error from {}: {}", context.getErrorSource(), context.getException().getMessage());
	}

}
