package com.example.patientservice.listening;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Stops receiving while a circuit breaker is open, so messages wait in the subscription instead of
 * using up their Deliveries during an outage. While stopped it probes every {@code probeInterval};
 * once the service is reachable the breaker goes half-open and receiving starts again, letting the
 * next real calls decide whether the breaker closes or opens (and pauses) again.
 */
class ReceivingPause implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(ReceivingPause.class);

	private final CircuitBreaker breaker;

	private final BooleanSupplier reachable;

	private final Runnable stopReceiving;

	private final Runnable startReceiving;

	private final Duration probeInterval;

	/** Stops and starts receiving and runs the probes, one thing at a time. */
	private final ScheduledExecutorService executor = Executors
		.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "receiving-pause"));

	/** Only touched on the executor thread. */
	private ScheduledFuture<?> probeTask;

	ReceivingPause(CircuitBreaker breaker, BooleanSupplier reachable, Runnable stopReceiving,
			Runnable startReceiving, Duration probeInterval) {
		this.breaker = breaker;
		this.reachable = reachable;
		this.stopReceiving = stopReceiving;
		this.startReceiving = startReceiving;
		this.probeInterval = probeInterval;
		breaker.getEventPublisher().onStateTransition(event -> {
			if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
				executor.execute(this::pause);
			}
		});
	}

	private void pause() {
		stopReceiving.run();
		log.warn("{} is unavailable, stopped receiving; probing every {}", breaker.getName(), probeInterval);
		if (probeTask != null) {
			probeTask.cancel(false);
		}
		probeTask = executor.scheduleWithFixedDelay(this::probe, probeInterval.toMillis(), probeInterval.toMillis(),
				TimeUnit.MILLISECONDS);
	}

	private void probe() {
		// Never throw: a scheduled task that throws is silently never run again
		try {
			if (!reachable.getAsBoolean()) {
				return;
			}
			// Half-open before starting, so the first messages are let through instead of turned away
			breaker.transitionToHalfOpenState();
			startReceiving.run();
		}
		catch (RuntimeException ex) {
			log.error("Could not start receiving again, will retry on the next probe", ex);
			return;
		}
		probeTask.cancel(false);
		probeTask = null;
		log.info("{} answers again, receiving (circuit breaker half-open)", breaker.getName());
	}

	@Override
	public void close() {
		executor.shutdownNow();
	}

}
