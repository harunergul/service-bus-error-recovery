# service-bus-recovery

A learning project for Azure Service Bus reliability. The domain language is in [CONTEXT.md](CONTEXT.md) and the decisions are in [docs/adr](docs/adr).

```
patient-ui ──REST──▶ patient-service ──PatientMoved──▶ topic patient-moved
 (Angular)           (Spring Boot, H2)                        │
                           ▲                    subscription notification-service
                           └──── listener ◀───────────────────┘
                                    │ Feign
                                    ▼
                           notification-service (FastAPI, logs it)
```

## Run it

```sh
# 1. Service Bus emulator (+ its SQL Edge). Ready when the logs say "Emulator Service is Successfully Up".
docker compose -f infra/docker-compose.yml up -d

# 2. notification-service on :8000
cd notification-service && python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn main:app --port 8000 --reload

# 3. patient-service on :8080 (Java 21)
cd patient-service && ./mvnw spring-boot:run

# 4. patient-ui on :4200 (proxies /api to :8080)
cd patient-ui && npm install && npm start
```

- H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/patient-db`, user `sa`, no password)
- notification-service API docs: http://localhost:8000/docs
- Pending messages (in the subscription, not yet delivered): the "Pending messages" page in the UI (http://localhost:4200/pending), or `GET http://localhost:8080/api/pending-messages` (peek only)
- Delivered (accepted by notification-service, kept in SQLite at `notification-service/data/`): the "Delivered" page in the UI (http://localhost:4200/delivered), or `GET http://localhost:8000/delivered` (`DELETE` clears it). Each accepted call is a row, so a move delivered twice is listed twice and marked duplicate. The page stays up to date without refreshing: notification-service pushes the whole list over the WebSocket `ws://localhost:8000/delivered/live` when a browser connects and again after every change
- Dead-letter queue: the "Dead letters" page in the UI (http://localhost:4200/dead-letters), or `GET http://localhost:8080/api/dead-letters` (peek only; messages stay in the queue). Each of these pages has a Clear button for local testing (`DELETE` on the same URL); clearing Pending messages skips any Patient whose session the listener holds at that moment
- Pending messages and Dead letters refresh themselves every 3 seconds while the page is open and the browser tab is visible. Service Bus cannot tell us when they change, so they are peeked again; the Refresh button reloads at once

## Experiments

- **Consumer down:** stop notification-service and record a move. Each Delivery first retries the Feign call in place (5 attempts, backoff 500ms, 1s, 2s, 4s; Resilience4j `notification-service` in `application.properties`; turn it off with `--listener.notification-retry.enabled=false`), then the listener abandons the message, it is redelivered immediately, and after 10 deliveries (`MaxDeliveryCount` in `infra/servicebus/Config.json`) it goes to the dead-letter queue.
- **Consumer down for long (circuit breaker):** stop notification-service and record several moves. After 3 failed Deliveries the circuit breaker (Resilience4j `notification-service`) opens and the listener stops receiving, so the messages wait in the subscription (see "Pending messages") instead of being dead-lettered. Every 10s it sends `HEAD /patient-moves`; once notification-service is back (it answers 405), the listener starts again and catches up. Turn it off with `--listener.notification-circuit-breaker.enabled=false` to get the behaviour above. The subscription's 1h `DefaultMessageTimeToLive` limits how long an outage can last before messages expire.
- **Simulated outage:** on the "Patient move" page, start a notification-service outage for N seconds. Until it ends (or you press "End now") notification-service answers 503 to every PatientMoved and to the HEAD probe, so the circuit breaker opens and stays open for the whole outage, unlike room `503`, where the probe finds the service up. The outage is kept in notification-service (`PUT`/`GET`/`DELETE /outage`), so the countdown survives a page refresh; restarting notification-service ends it.
- **Failure by room number:** notification-service answers with the room as the HTTP status when the room is a status code (e.g. room `404` → 404, `503` → 503); any other room gets 200 (1xx too). A 4xx is not retried in place: the message is abandoned on every Delivery and dead-lettered after 10, holding up that Patient's later moves meanwhile. A 5xx is retried in place and counts toward the circuit breaker, so a few moves to room `503` pause receiving for every Patient until the probe finds notification-service answering again; the same message then fails again, and the cycle repeats until it is dead-lettered.
- **Per-patient ordering (sessions):** the subscription requires sessions and the session id is the patient id, so each Patient's moves are delivered in order while different Patients are handled in parallel (`listener.max-concurrent-sessions`). A failing message holds up only its own Patient's later moves until it is delivered or dead-lettered. Changing `RequiresSession` needs an emulator restart, which recreates the subscription.
- **Consumer paused:** start patient-service with `--listener.enabled=false`, record some moves, then restart it with the listener on and watch it catch up.
- **Publish fails after save:** stop the emulator and record a move. The move is saved in H2, no event is sent, and the API returns 500 ([ADR-0001](docs/adr/0001-save-then-publish-without-outbox.md)).
