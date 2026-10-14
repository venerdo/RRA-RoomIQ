# Notification Service

Notification Service owns notification persistence and event consumption. It does not read or write another service's database and exposes no notification business API in S7-01.

## S7-01 event consumer

The service consumes persistent messages from the durable `roomiq.events.v1` direct exchange and its `roomiq.notifications.events.v1` queue. It binds only the documented S7-01 routing keys. A durable dead-letter exchange and queue retain malformed, unsupported, mismatched-source/routing-key, and retry-exhausted messages. The main listener uses automatic acknowledgement and finite exponential retries (three attempts by default); the transactional processor returns only after PostgreSQL commits, so the broker acknowledges only committed work.

The JSON event envelope contains `eventId`, `sourceService`, `eventType`, `eventVersion`, `aggregateType`, `aggregateId`, `aggregateVersion`, `occurredAt`, `payload`, and optional `correlationId`/`causationId`. Version 1 accepts the SRS event families recorded in the API contract. Event type/source pairing, aggregate type, routing key, required event facts, UUIDs, timestamps, correlation fields, and recipient identifiers are validated. Payloads must not provide email addresses or display-name overrides; recipients are user UUIDs from service publishers, with contact/display data resolved by later service-owned workflows. `sourceService` is a body claim, not authentication. Production must use separate broker credentials and publisher routing-key ACLs so only each owning service can publish its event families; this broker authorization policy is not configured or integration-tested in this repository yet.

Every valid event and its resulting in-app rows are persisted in one transaction. `notification_event_receipt.event_id` is unique and inserted with PostgreSQL `ON CONFLICT DO NOTHING` before effects are saved. A matching replay is a no-op; reusing an event ID with conflicting envelope metadata is rejected. An event may yield zero or multiple recipients, and an effect persistence failure rolls back the receipt and all rows together.

### Local configuration

Configure the service database and broker outside source control:

```dotenv
NOTIFICATION_DB_URL=jdbc:postgresql://localhost:5432/roomiq_notification
NOTIFICATION_DB_USERNAME=roomiq
NOTIFICATION_DB_PASSWORD=replace-with-local-secret
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=replace-with-service-account
RABBITMQ_PASSWORD=replace-with-local-secret
NOTIFICATION_EVENT_CONSUMER_ENABLED=true
NOTIFICATION_EVENT_MAX_ATTEMPTS=3
NOTIFICATION_EVENT_RETRY_INITIAL_INTERVAL=1s
NOTIFICATION_EVENT_RETRY_MULTIPLIER=2
NOTIFICATION_EVENT_RETRY_MAX_INTERVAL=5s
NOTIFICATION_REMINDER_DELIVERY_ENABLED=true
NOTIFICATION_REMINDER_FIXED_DELAY_MS=1000
# Optional service-specific broker account; shared RABBITMQ_USERNAME/PASSWORD
# are development defaults only.
NOTIFICATION_RABBITMQ_USERNAME=replace-with-notification-account
NOTIFICATION_RABBITMQ_PASSWORD=replace-with-local-secret
```

Provide RabbitMQ credentials with publish/consume access limited to the required exchanges and queues in deployed environments. The local `guest` default is for development only. Do not add credentials to tracked configuration files.

## Verification

From `backend/`:

```bash
mvn -pl notification-service -am test
```

The S7-01 PostgreSQL and RabbitMQ integration tests use Testcontainers and require a running Docker daemon. Focused Notification verification passes 24 tests (codec, PostgreSQL transaction/deduplication/concurrency/rollback, RabbitMQ retry/dead-letter/acknowledgement, reminder scheduling/terminal handling, and application context). Identity passes 53 tests and common-web 6 tests. The final 11-module backend reactor passed 61 suites/354 tests, including the completed reminder behavior. All runs had zero failures, errors, or skips.

## Event producer status

Booking and Room publish notification business events after source commits through their local transactional outboxes. Booking emits request/decision/cancellation/extension/completion events, emits `MeetingReleased` only from the protected explicit release operation, and writes two `Reminder` events for each confirmed occurrence: 15 and 5 minutes before its authoritative start instant. Each reminder carries the validated IANA building timezone and trusted organizer recipient UUID and is written to the Booking outbox in the same transaction as the meeting and occurrence rows.

Notification stores reminder effects as `SCHEDULED` rows. A scheduled worker makes due rows `PENDING` only after taking the same reservation-scoped PostgreSQL advisory lock used by reminder/terminal event processing and checking durable cancellation, completion, release, and automatic-release receipts. The reminder uniqueness index prevents equivalent schedule events from creating duplicate in-app reminders. A terminal event cancels scheduled reminders and unread reminders already made pending; late reminder events are ignored when a terminal receipt has committed. The schema change is limited to the user's approved Notification ERD addition. Reminder recipients beyond the organizer, channel preferences, email delivery, and templates remain later Stage 7 work. Room status/maintenance events use the user-approved `ROOM_EVENT_OUTBOX` and protected Booking recipient lookup scoped by Identity. Deployed RabbitMQ source credentials and per-routing-key ACLs remain to be configured and verified. No additional ERD change is authorized. The Notification consumer never treats missing events as evidence that no business action occurred.
