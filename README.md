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
- Dead-letter queue: the "Dead letters" section in the UI, or `GET http://localhost:8080/api/dead-letters` (peek only; messages stay in the queue)

## Experiments

- **Consumer down:** stop notification-service and record a move. The listener abandons the message, it is redelivered immediately, and after 10 deliveries (`MaxDeliveryCount` in `infra/servicebus/Config.json`) it goes to the dead-letter queue.
- **Consumer paused:** start patient-service with `--listener.enabled=false`, record some moves, then restart it with the listener on and watch it catch up.
- **Publish fails after save:** stop the emulator and record a move. The move is saved in H2, no event is sent, and the API returns 500 ([ADR-0001](docs/adr/0001-save-then-publish-without-outbox.md)).
