package com.example.patientservice.listening;

import com.example.patientservice.move.PatientMoved;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Hands a PatientMoved to notification-service. A failed call is retried in place (Resilience4j
 * retry "notification-service"; switched off with {@code listener.notification-retry.enabled=false}).
 * Around the retries sits a circuit breaker (Resilience4j "notification-service"; switched off with
 * {@code listener.notification-circuit-breaker.enabled=false}), which opens when enough Deliveries
 * fail. Both are configured in application.properties and only count connection errors and 5xx.
 */
@Component
class NotificationSender {

	private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

	private final NotificationServiceClient client;

	private final Retry retry;

	private final boolean retryEnabled;

	private final CircuitBreaker breaker;

	private final boolean breakerEnabled;

	NotificationSender(@Value("${listener.notification-retry.enabled}") boolean retryEnabled,
			@Value("${listener.notification-circuit-breaker.enabled}") boolean breakerEnabled,
			NotificationServiceClient client, RetryRegistry retryRegistry,
			CircuitBreakerRegistry circuitBreakerRegistry) {
		this.client = client;
		this.retryEnabled = retryEnabled;
		this.retry = retryRegistry.retry("notification-service");
		this.retry.getEventPublisher()
			.onRetry(event -> log.warn("Call to notification-service failed (attempt {}), retrying in {}: {}",
					event.getNumberOfRetryAttempts(), event.getWaitInterval(), event.getLastThrowable().getMessage()));
		this.breakerEnabled = breakerEnabled;
		this.breaker = circuitBreakerRegistry.circuitBreaker("notification-service");
		this.breaker.getEventPublisher()
			.onStateTransition(event -> log.info("notification-service circuit breaker {}", event.getStateTransition()));
		log.info("notification-service retry {}, circuit breaker {}", retryEnabled ? "on" : "off",
				breakerEnabled ? "on" : "off");
	}

	/** Throws if notification-service did not accept the event, including when the breaker is open. */
	void send(PatientMoved event) {
		Runnable call = () -> client.patientMoved(event);
		if (retryEnabled) {
			call = Retry.decorateRunnable(retry, call);
		}
		if (breakerEnabled) {
			// Outside the retry, so one failed Delivery counts once, however many Call Attempts it made
			call = CircuitBreaker.decorateRunnable(breaker, call);
		}
		call.run();
	}

	/**
	 * Whether notification-service answers at all, checked with a HEAD request instead of a
	 * PatientMoved. A 4xx still means it answers; a 5xx or a connection error means it is down.
	 */
	boolean isReachable() {
		try {
			client.probe();
			return true;
		}
		catch (FeignException.FeignClientException ex) {
			return true;
		}
		catch (Exception ex) {
			log.info("notification-service still unavailable: {}", ex.getMessage());
			return false;
		}
	}

	CircuitBreaker breaker() {
		return breaker;
	}

}
