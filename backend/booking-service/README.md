# Booking Service

## Current status

Booking Service is currently a Spring Boot scaffold, not an implemented booking or meeting workflow. Its current module contains the application bootstrap, Actuator configuration, shared `common-web` dependency, and application-context smoke test. It has no Booking-owned migration, domain model, repository, API, authorization integration, or runtime database configuration.

The user accepted Stage 5 and requested the Stage 6 requirement checklist. The proposed checklist is maintained in [Backend Documentation](../Documentation.md#stage-6-requirement-checklist-proposed-pending-user-approval). Stage 6 implementation has not started; checklist approval is pending. For recurring bookings, the user selected all-or-nothing acceptance: validate every occurrence, report occurrence-specific conflicts, and persist no occurrence if any occurrence fails. The Booking APIs described as future acceptance expectations in that checklist are not current endpoints and are not exposed through the API Gateway.

## Ownership and constraints

- Booking will own requests, approval decisions, reservations, meetings, participants, cancellations, extensions, share links, idempotency, and the authoritative occupancy snapshot.
- Booking must use Identity, Organization, Room, and Scheduling owner APIs for authoritative cross-service data and decisions. It must not read or write another service's database.
- Confirmed reservation occupancy and concurrent same-room overlap prevention belong to Booking. The stored half-open interval includes the configured release buffer.
- Booking's future internal occupancy provider is a dependency of Scheduling availability. Until implemented, candidate-producing availability searches fail closed.
- All Booking operations require server-side authorization except the narrowly scoped public share-token read, if approved and implemented. Stage 11 owns Gateway routing.
- The approved ERD must not be changed without explicit user approval. Any demonstrated need for an ERD change must be proposed for approval before editing it.

## Current module configuration

The application listens on `${BOOKING_SERVICE_PORT:8085}` and exposes Actuator `health` and `info`. See [pom.xml](pom.xml), [application.yml](src/main/resources/application.yml), and [BookingServiceApplication.java](src/main/java/rw/rra/roomiq/booking/BookingServiceApplication.java).
