package com.example.patientservice.listening;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ReceivingPauseTest {

	private final CircuitBreaker breaker = CircuitBreaker.ofDefaults("notification-service");

	private final AtomicBoolean reachable = new AtomicBoolean(false);

	private final AtomicInteger probes = new AtomicInteger();

	private final List<String> receiving = new CopyOnWriteArrayList<>();

	private Runnable startReceiving = () -> receiving.add("start");

	private final ReceivingPause pause = new ReceivingPause(breaker, () -> {
		probes.incrementAndGet();
		return reachable.get();
	}, () -> receiving.add("stop"), () -> startReceiving.run(), Duration.ofMillis(10));

	@AfterEach
	void close() {
		pause.close();
	}

	@Test
	void stopsReceivingWhenTheBreakerOpens() {
		breaker.transitionToOpenState();

		await().until(() -> receiving.equals(List.of("stop")));
	}

	@Test
	void keepsReceivingStoppedWhileTheServiceIsUnreachable() {
		breaker.transitionToOpenState();

		await().until(() -> probes.get() >= 3);
		assertThat(receiving).containsExactly("stop");
		assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
	}

	@Test
	void startsReceivingHalfOpenOnceTheServiceIsReachable() {
		breaker.transitionToOpenState();
		await().until(() -> probes.get() >= 1);

		reachable.set(true);

		await().until(() -> receiving.equals(List.of("stop", "start")));
		assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
	}

	@Test
	void stopsProbingOnceReceivingAgain() throws InterruptedException {
		reachable.set(true);
		breaker.transitionToOpenState();
		await().until(() -> receiving.equals(List.of("stop", "start")));

		int probesWhenStarted = probes.get();
		Thread.sleep(100);

		assertThat(probes.get()).isEqualTo(probesWhenStarted);
	}

	@Test
	void stopsAgainWhenTheHalfOpenCallsFail() {
		reachable.set(true);
		breaker.transitionToOpenState();
		await().until(() -> receiving.equals(List.of("stop", "start")));

		breaker.transitionToOpenState();

		await().until(() -> receiving.equals(List.of("stop", "start", "stop", "start")));
	}

	@Test
	void keepsProbingWhenStartingFails() {
		AtomicInteger startAttempts = new AtomicInteger();
		startReceiving = () -> {
			if (startAttempts.incrementAndGet() == 1) {
				throw new IllegalStateException("processor could not start");
			}
			receiving.add("start");
		};
		reachable.set(true);

		breaker.transitionToOpenState();

		await().until(() -> receiving.equals(List.of("stop", "start")));
		assertThat(startAttempts.get()).isEqualTo(2);
	}

}
