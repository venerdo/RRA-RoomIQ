# RRA RoomIQ Backend Documentation

## Implementation roadmap

The authoritative end-to-end roadmap, requirement checklists, and stage-gate rules are maintained in [Backend Documentation](../Documentation.md#end-to-end-implementation-roadmap). Stages 3 through 5 are accepted; Stage 6 was accepted by the user on 2026-10-12 after S6-01 through S6-14 completed verification. The Stage 7 checklist has been approved; requirement implementation has not started. Booking provides protected request, decision, direct-booking, reservation lifecycle, meeting, participant, cancellation, extension, share-link management, and internal occupancy APIs with caller-token-authorized Identity, Organization, Room, and Scheduling checks. The active Room rule applies to all direct-booking initiators; required approval yields only `PENDING_APPROVAL`, and recurring acceptance validates every bounded Scheduling RRULE occurrence before atomically storing the series. All-or-nothing conflicts report affected dates and roll back. Identity-derived visibility, organizer and participant attribution, protected cancellation/extension, buffered occupancy, and Booking's PostgreSQL overlap barriers remain enforced. The sole unauthenticated Booking data route returns the explicit public meeting-card allowlist. S6-12 adds replay-safe request/direct-booking, extension, cancellation, and share-link operations; S6-13 adds the approved transactional Booking outbox with at-least-once delivery. Consumers deduplicate by `eventId`, process distinct equal-version recurring occurrence events, and ignore only older aggregate versions. S6-14 verifies PostgreSQL-backed Booking behavior and overlap concurrency, Identity/common-web, and the full 11-module reactor: 138 Booking tests, 53 Identity tests, 6 common-web tests, and 311 full-reactor tests passed with zero failures, errors, or skips. The only S6-12 ERD addition is the user-approved nullable share-link idempotency key and creator/key uniqueness; the user-approved S6-13 ERD addition is `BOOKING_EVENT_OUTBOX`. See the linked Stage 6 checklist and API contract for implementation details.

## Stage 1 status

Stage 1 is complete and verified. Stage 2 is complete through S2-18 verification. Stages 3, 4, and 5 are accepted. Scheduling owns calendar rules, bounded date-based recurrence evaluation, constraint validation, and candidate availability; Room retains maintenance/status, while Booking owns reservation occupancy and its transactional conflict barrier. Candidate-producing availability uses Booking's protected S6-13 v1 occupancy provider and fails closed on invalid or unavailable authoritative data. All Scheduling business routes require an Identity-issued bearer token and server-side authorization; they are not routed through the API Gateway. Production deployment must enforce private network ingress. Stage 6 was accepted by the user on 2026-10-12 after S6-01 through S6-14 verification. The proposed Stage 7 checklist is awaiting approval; no Notification Service business APIs or persistence have been implemented. Booking's request, decision, direct-booking, reservation lifecycle, meeting, participant, cancellation, extension, share-link management, occupancy, and outbox operations are protected as applicable; no Booking API is routed through the Gateway. Request, approval, and direct-booking operations revalidate current owner-service policies and occupancy; required approval creates only a pending request, and recurring acceptance validates every occurrence and writes all-or-nothing. Meeting access uses current Identity scope; only the separate public-card route is anonymous. Cancellation preserves history and releases occupancy transactionally; extensions preserve decision history and update buffered occupancy under existing exclusion barriers. S6-12 enforces same-key retry serialization for request/direct-booking, extension, and share-link creation, validates cancellation replay actor and payload, and returns explicit conflicts for changed payloads. Share-link token replay uses the retained versioned HMAC key ring while storing only token hashes. S6-13 persists versioned UPSERT/REMOVE occupancy events in the user-approved Flyway V7 outbox transactionally; the relay uses `FOR UPDATE SKIP LOCKED`, publisher confirms, mandatory-return checks, and durable capped retry metadata. Delivery is at-least-once, so consumers deduplicate by event ID. S6-14 verified Booking 138/138, Identity 53/53, common-web 6/6, and the full 11-module reactor 311/311, with zero failures, errors, or skips; PostgreSQL overlap/exclusion/deadlock races were exercised. The approved S6-12 ERD change is nullable `MEETING_SHARE_LINK.idempotency_key VARCHAR(128)` plus creator/key uniqueness; S6-13 adds only the user-approved `BOOKING_EVENT_OUTBOX`.

### SRS/ERD traceability

Identity implementation alignment with the authoritative Backend SRS, Final SRS, and ERD is recorded in [identity-srs-erd-alignment.md](identity-srs-erd-alignment.md). Organization schema alignment is recorded in [organization-srs-erd-alignment.md](organization-srs-erd-alignment.md). The organization change adds the explicitly approved `FLOOR.created_at` field; the identity audit outbox remains service-local infrastructure that publishes to the Audit Service.

### Current architecture baseline

- API Gateway
- Identity/Auth Service
- Organization/Location Service
- Room Management Service
- Scheduling Service
- Booking/Meeting Service
- Notification Service
- Audit Service
- Analytics Service

The `common-web` module is consumed by every service and provides the shared response, error, validation, OpenAPI, correlation-ID, exception-handling, and logging conventions.

Organization Service owns its S3-01 schema, S3-02 persistence/domain model, S3-03 hierarchy integrity, S3-04 APIs, S3-05 validation/lifecycle policy, S3-06 authorization, and S3-07 API contract/OpenAPI completeness. S3-08 verified that Identity stores Organization references as identifiers only; S3-09 verified isolated Organization test coverage; S3-10 synchronized docs, contracts, and configuration notes; S3-11 clean service/Identity/shared/full-reactor verification passed. Stage 3 is accepted. Room Service S4-01 through S4-12 are implemented, documented, verified, and accepted. Scheduling S5-01 through S5-07 are implemented and verified; all `/api/v1/**` business routes require bearer/Identity authorization and are not routed through the Gateway pending Stage 11. The service accepts no caller-supplied identity or scope. Recurrence uses the existing ERD table with one-year/365-occurrence bounds, S5-05 validates candidate occurrences, and S5-06 provides owner-API-based continuous availability candidates with fail-closed Booking occupancy integration. Booking confirmation and occupancy ownership remain in S6.

S3-05 enforces nonblank/length and database uniqueness constraints, timezone path format plus service-validated IANA IDs with the `Africa/Kigali` default, active parent/child rules, recursive active-descendant protection, department subtree scope safety, and explicit nullable PUT semantics. Flyway V3/V4 add database checks; no ERD change was required. Organization/common-web passed 7 suites/20 tests and identity/common-web passed 12/37, with zero failures/errors/skips.

### Shared architectural rules

- Each service owns its own persistence boundary.
- No cross-service database access or JPA entity sharing.
- Public APIs are versioned under `/api/v1`.
- Business logic remains in the service layer.
- Authentication and authorization remain server-side authoritative.
- All schema changes must be reflected in migration scripts.

### Infrastructure baseline

The backend includes a shared local infrastructure plan for:

- PostgreSQL
- RabbitMQ
- Redis

The `backend/infrastructure/docker-compose.yml` file is the local development starting point.

### Environment handling

Use the project-level `.env.example` template as the source for local environment values. Sensitive values must not be committed to the repository.

### Verification

Verified with:

```bash
cd "/home/victoire/Desktop/My_Projects/RRA_Projects/RRA_RoomIQ"
mvn -q -f backend/pom.xml clean test
```

This verifies the complete backend reactor, including shared standards and identity integration tests.

### Implementation changelog

### 2026-09-30
- Completed approved S3-01 organization schema migration and synchronized the ERD, roadmap, and stage contract.
- Completed S3-02 organization persistence/domain model and focused H2 round-trip coverage; no additional ERD change was required. Focused organization tests (3 suites/4 tests), identity/common-web (12/37), and full reactor (22/48) passed; build succeeded.
- Completed S3-03 hierarchy foreign-key and cycle/scope integrity; focused organization tests (3 suites/5 tests), identity/common-web (12/37), and full reactor (22/49) passed; build succeeded.
- Completed S3-04 CRUD/status/search/filter/page/sort and hierarchy APIs; focused Organization tests (4 suites/6 tests), identity/common-web (12/37), and full reactor (23/50) passed; build succeeded.
- Completed S2-14 through S2-17: stable identity errors/audit, automated identity coverage, executable Postman contract/sample data, and SRS/ERD/documentation alignment.
- Completed S2-17 SRS/ERD/documentation synchronization and added the identity alignment note.
- Completed S2-18 identity and full-reactor verification with zero failures/errors.
- Defined the Stage 3 Organization and Locations requirement checklist and implementation constraints.
- Synchronized the backend roadmap, identity documentation, API contract, collection validator, and shared-module notes.
- Verified the clean backend reactor: 20 suites, 45 tests, zero failures/errors.

### 2026-09-28
- Completed and verified Stage 1 backend foundation setup.
- Added shared backend parent Maven project and runnable service modules.
- Added the common-web shared standards module.
- Added service application entry points, configuration, Actuator, and smoke tests.
- Created environment placeholder configuration and infrastructure baseline.
- Synced backend documentation with the actual implementation state.
- Verified the complete backend reactor passes its Maven test lifecycle.

#### 2026-09-27
- Initialized the Spring Boot identity service foundation.
- Fixed datasource and environment configuration.
- Established Flyway, JPA, security, actuator, and pagination baseline.
- Confirmed service startup and test execution.
