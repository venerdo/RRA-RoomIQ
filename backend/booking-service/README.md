# Booking Service

## Current status

S6-01 persistence is complete. The S6-02 authorization foundation is implemented, but S6-02 is not yet fully accepted: Booking has no business operations, so workflow-dependent Organization, Room, and Scheduling owner-API validation has not been connected or verified. Booking Service has a PostgreSQL Flyway migration, JPA mappings and repositories for the eight approved Booking-owned ERD entities, schema validation, a caller-token Identity authorization client, and an interceptor that protects every `/api/v1/**` handler (including `OPTIONS`). The authenticated actor is derived from Identity and stored on the request; workflow services must use Identity and the relevant owner-service clients for authorization and authoritative checks before reads or writes.

The user accepted Stage 5, approved the Stage 6 checklist, and selected all-or-nothing acceptance for recurring bookings: validate every occurrence, report occurrence-specific conflicts, and persist no occurrence if any occurrence fails. The checklist is maintained in [Backend Documentation](../Documentation.md#stage-6-requirement-checklist-approved). Identity evaluates current account status, permissions, scope, and privilege through protected `POST /api/v1/internal/authorization/booking`; see the Identity [authorization policy](../identity-service/AUTHORIZATION.md). Caller-supplied actor IDs, roles, statuses, permissions, and scopes are never trusted. Organization, Room, and Scheduling checks remain pending integration with future workflows. Booking has no business routes yet, and none are exposed through the API Gateway.

## Ownership and constraints

- Booking will own requests, approval decisions, reservations, meetings, participants, cancellations, extensions, share links, idempotency, and the authoritative occupancy snapshot.
- Booking must use Identity, Organization, Room, and Scheduling owner APIs for authoritative cross-service data and decisions. It must not read or write another service's database.
- Confirmed reservation occupancy and concurrent same-room overlap prevention belong to Booking. The stored half-open interval includes the configured release buffer.
- Booking's future internal occupancy provider is a dependency of Scheduling availability. Until implemented, candidate-producing availability searches fail closed.
- All Booking operations require server-side authorization except the narrowly scoped public share-token read, if approved and implemented. Stage 11 owns Gateway routing.
- The approved ERD must not be changed without explicit user approval. Any demonstrated need for an ERD change must be proposed for approval before editing it.

## Current module configuration

The application listens on `${BOOKING_SERVICE_PORT:8085}` and exposes Actuator `health` and `info`. PostgreSQL settings are `${BOOKING_DB_URL:jdbc:postgresql://localhost:5432/roomiq_booking}`, `${BOOKING_DB_USERNAME:roomiq}`, and `${BOOKING_DB_PASSWORD:roomiq}`; Identity integration uses `${IDENTITY_SERVICE_URL:http://localhost:8081}`. Hibernate validates the Flyway-managed schema and does not create/update tables. See [pom.xml](pom.xml), [application.yml](src/main/resources/application.yml), [V1__booking_schema.sql](src/main/resources/db/migration/V1__booking_schema.sql), and [BookingServiceApplication.java](src/main/java/rw/rra/roomiq/booking/BookingServiceApplication.java).
