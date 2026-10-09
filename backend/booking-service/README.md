# Booking Service

## Current status

S6-01 through S6-03 are implemented. Protected APIs are `POST/GET /api/v1/booking-requests`, `GET /api/v1/booking-requests/{id}`, and `POST /api/v1/booking-requests/{id}/submit`. Creation validates request data and current Organization, Room, and Scheduling owner data before storing a `DRAFT`; only its active requester can submit it, which revalidates current policy and transitions it to `PENDING_APPROVAL`. Identity derives actor identity and evaluates current permissions, privilege, and building/department scope. Client-supplied actor, request type, approval, and status are never persisted. Booking forwards the caller bearer token to Identity, Organization, Room, and Scheduling and fails closed on missing, denied, inconsistent, or unavailable authoritative results. A global interceptor protects every `/api/v1/**` handler, including `OPTIONS`. Approval/rejection, cancellation, expiration, reservations, and the other Booking workflows remain future Stage 6 requirements; requests are not auto-confirmed.

The user accepted Stage 5, approved the Stage 6 checklist, and selected all-or-nothing acceptance for recurring bookings: validate every occurrence, report occurrence-specific conflicts, and persist no occurrence if any occurrence fails. The checklist is maintained in [Backend Documentation](../Documentation.md#stage-6-requirement-checklist-approved). Identity evaluates current account status, permissions, scope, and privilege through protected `POST /api/v1/internal/authorization/booking`; see the Identity [authorization policy](../identity-service/AUTHORIZATION.md). The API exposes only the request workflow paths listed above; no Booking route is configured in the API Gateway. Approval, direct-booking, reservation, meeting, cancellation, extension, share-link, and occupancy routes remain unimplemented until their requirements are approved and completed.

## Ownership and constraints

- Booking will own requests, approval decisions, reservations, meetings, participants, cancellations, extensions, share links, idempotency, and the authoritative occupancy snapshot.
- Booking must use Identity, Organization, Room, and Scheduling owner APIs for authoritative cross-service data and decisions. It must not read or write another service's database.
- Confirmed reservation occupancy and concurrent same-room overlap prevention belong to Booking. The stored half-open interval includes the configured release buffer.
- Booking's future internal occupancy provider is a dependency of Scheduling availability. Until implemented, candidate-producing availability searches fail closed.
- All Booking business operations require server-side authorization except the narrowly scoped public share-token read, if approved and implemented. Stage 11 owns Gateway routing.
- The approved ERD must not be changed without explicit user approval. Any demonstrated need for an ERD change must be proposed for approval before editing it.

## Current module configuration

The application listens on `${BOOKING_SERVICE_PORT:8085}` and exposes Actuator `health` and `info`. PostgreSQL settings are `${BOOKING_DB_URL:jdbc:postgresql://localhost:5432/roomiq_booking}`, `${BOOKING_DB_USERNAME:roomiq}`, and `${BOOKING_DB_PASSWORD:roomiq}`. Integration base URLs are `${IDENTITY_SERVICE_URL:http://localhost:8081}`, `${ORGANIZATION_SERVICE_URL:http://localhost:8082}`, `${ROOM_SERVICE_URL:http://localhost:8083}`, and `${SCHEDULING_SERVICE_URL:http://localhost:8084}`. Hibernate validates the Flyway-managed schema and does not create/update tables. See [pom.xml](pom.xml), [application.yml](src/main/resources/application.yml), [V1__booking_schema.sql](src/main/resources/db/migration/V1__booking_schema.sql), and [BookingServiceApplication.java](src/main/java/rw/rra/roomiq/booking/BookingServiceApplication.java).
