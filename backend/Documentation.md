# RRA RoomIQ Backend Documentation

## End-to-end implementation roadmap

This table is the backend stage-gate roadmap from initial inspection through production readiness. A stage advances only after its listed requirements are implemented, tested, documented, synchronized with the SRS/ERD/API contract, and explicitly accepted by the user. Each individual requirement is completed and verified before work moves to the next one.

| Stage | Scope and expected outcome | Status |
|---|---|---|
| Stage 0: Inspect and agree | Inspect the requirements, SRS, ERD, repository, risks, and constraints; analyze gaps; propose an ordered plan and acceptance checks; obtain approval before implementation. | Complete |
| Stage 1: Backend foundation | Parent Maven reactor; runnable service modules; shared `common-web` response/error/validation/OpenAPI/logging/correlation conventions; environment and local infrastructure; service smoke tests; synchronized docs and Postman contract. | Complete |
| Stage 2: Identity and authorization | Implement identity schema, entities, repositories, user APIs, roles, permissions, privileges, credentials, login/refresh/logout, sessions, scoped authorization, security configuration, audit behavior, OpenAPI, tests, and identity contract. | Complete: S2-01 through S2-18 verified |
| Stage 3: Organization and locations | Dynamic CRUD for country, province, district, office building, floor, and department; hierarchical integrity, uniqueness, active state, APIs, validation, authorization, migrations, and tests. | Accepted: S3-01 through S3-11 verified |
| Stage 4: Room and resource management | Dynamic room/type/facility/photo/rule/status management; scope-aware uniqueness, capacity checks, soft deletion, maintenance/status history, Cloudinary adapter boundary, APIs, and tests. | Accepted by user: S4-01 through S4-12 verified |
| Stage 5: Scheduling and availability | Working calendars, day windows, holidays, closures, recurrence, availability queries, timezone handling, and scheduling constraints integrated with Booking-owned occupancy. | In progress: S5-01 and S5-02 complete; S5-03 next |
| S5-01 | Scheduling Service owns Flyway migrations, JPA entities, repositories, and PostgreSQL integrity for `WORKING_CALENDAR`, `WORKING_DAY_WINDOW`, `HOLIDAY`, `CLOSURE_PERIOD`, and `RECURRENCE_RULE` as represented in the approved ERD. Cross-service building/user IDs remain scalar identifiers. `MAINTENANCE_PERIOD` stays Room-owned; confirmed `RESERVATION`/occupancy stays Booking-owned in S6. | Complete: Flyway V1-V2, five Scheduling entities/repositories, PostgreSQL `tstzrange` mapping, one-calendar-per-building constraint, schema/FK/constraint/round-trip tests; Scheduling and full-reactor tests passed. No ERD change was needed |
| Stage 6: Booking and meeting workflows | Booking request state machine, policy validation, approval decisions, reservations, meetings, participants, cancellation, extension, idempotency, public share-link lifecycle, and database-backed overlap prevention. | Planned |
| Stage 7: Notifications | In-app/email preferences, templates, delivery attempts, retry/dead-letter behavior, and event-driven integration that does not compromise core booking consistency. | Planned |
| Stage 8: Audit service | Append-only security/admin/booking audit events, correlation propagation, authorized filtering/search, retention rules, and service integration without cross-service database access. | Planned |
| Stage 9: Analytics | Authorized reporting/KPI APIs and read models, with asynchronous refresh for long-running aggregation and no direct ownership violations. | Planned |
| Stage 10: AI integration | Versioned model metadata, recommendations, predictions, feedback, and optimization adapters; AI remains advisory and cannot bypass deterministic policy. | Planned |
| Stage 11: Gateway and cross-service integration | Routing, gateway policy, rate limiting, service discovery/configuration, versioned events/APIs, resilience, observability, and end-to-end workflow verification. | Planned |
| Stage 12: Production readiness and release | Security and privacy review, migration/recovery rehearsal, load and resilience tests, deployment/CI configuration, operational runbooks, monitoring/alerts, cost controls, and final acceptance gate. | Planned |

### Stage 2 requirement checklist

| ID | Requirement and acceptance expectation | Status |
|---|---|---|
| S2-01 | Identity Flyway schema matches the identity-owned ERD entities and integrity rules. | Complete |
| S2-02 | JPA entities, enums, relationships, audit defaults, optimistic locking, and soft deletion match the migration and ERD. | Complete |
| S2-03 | User, role, permission, privilege, session, and assignment repositories pass H2 query tests. | Complete |
| S2-04 | Validated create/update/status/list DTOs and safe response DTOs use the shared error convention. | Complete |
| S2-05 | User create/read/list/update/status/soft-delete APIs return shared response/error envelopes and pass HTTP tests. | Complete |
| S2-06 | Dynamic role/permission definitions and role-permission/user-role assignment operations persist and reject duplicates. | Complete |
| S2-07 | Privilege grants, evaluation, validity windows, active state, revoke history, and `CG_BOOKING` behavior are verified. | Complete |
| S2-08 | Passwords use adaptive hashing; validated credential rules are enforced; only active accounts with credentials are enabled for authentication; plaintext passwords/hashes are not returned. | Complete |
| S2-09 | Login verifies credentials/status; HS256 access-token issuance, rotating opaque refresh tokens, logout, and active-session JWT validation pass integration tests. | Complete |
| S2-10 | Refresh tokens are persisted only as hashes; sessions preserve issued/expiry/revocation timestamps and IP/user-agent; rotation, logout, and expired-session revocation are verified. | Complete |
| S2-11 | Role, permission, privilege, and office/department scope decisions enforce the SRS authorization matrix. | Complete |
| S2-12 | Explicit Spring Security configuration replaces development defaults and defines public/protected route behavior. | Complete |
| S2-13 | OpenAPI documents identity routes, DTOs, validation/errors, and authentication requirements. | Complete |
| S2-14 | Identity-specific audit events and security outcomes are recorded without sensitive data leakage; domain failures use stable codes and correlation IDs. | Complete: typed errors, validated correlation IDs, transactional audit outbox, durable RabbitMQ relay and focused tests |
| S2-15 | Unit, repository, API, security, migration, and behavior tests cover the complete identity requirements. | Complete: identity/common-web passed 12 suites/37 tests; clean backend reactor passed 20 suites/45 tests |
| S2-16 | Postman contract/examples cover implemented identity APIs and safe test data. | Complete: importable Postman collection, variables, and identity request bodies are synchronized |
| S2-17 | Identity documentation, SRS/ERD alignment notes, API contract, and implementation status stay synchronized. | Complete: alignment note added; SRS/ERD unchanged and all documentation surfaces synchronized |
| S2-18 | Final identity and full-reactor verification passes with no Stage 1 regression, followed by the Stage 2 acceptance gate. | Complete: identity/common-web 12 suites/37 tests and full reactor 20 suites/45 tests passed with zero failures/errors |

### Stage gate rules

- Work proceeds one requirement at a time; no later requirement is started until the current requirement passes its focused test, the identity suite, and the full Stage 1/reactor regression required by the gate.
- If a requirement or implementation reveals a possible ERD change, present the proposed change and wait for user approval before editing the ERD.
- Keep persistence ownership service-local; cross-service references remain identifiers plus API/event integration, never shared entities or cross-service database writes.
- Keep statuses evidence-based: a scaffold, shared default, or partial workflow is not marked complete as a domain requirement.
- After implementation and verification, synchronize this roadmap, service documentation, SRS/ERD traceability, and the API/Postman contract before asking for the next stage/requirement approval.

### Stage 3 requirement checklist: Organization and locations

| ID | Requirement and acceptance expectation | Status |
|---|---|---|
| S3-01 | Organization schema and Flyway migrations cover `COUNTRY`, `PROVINCE`, `DISTRICT`, `OFFICE_BUILDING`, `FLOOR`, and `DEPARTMENT` with UUID identifiers, timestamps, active/status fields, and service-local foreign keys. | Complete: PostgreSQL Flyway V1 and H2 migration/constraint tests; approved ERD adds `FLOOR.created_at` |
| S3-02 | Organization JPA entities, enums, repositories, DTOs, mappers, and package boundaries match the approved ERD and migration schema; entities are not shared with other services. | Complete: focused suite 3/4 tests, identity/common-web 12/37, full reactor 22/48; all pass with zero failures/errors |
| S3-03 | Hierarchical integrity prevents invalid parent references and enforces Country → Province → District → Office/Building → Floor relationships; department parent relationships remain valid and cycle-safe. | Complete: service-local foreign keys, V2 direct-self-parent check, and serializable cycle/scope-checked Department reparenting; focused hierarchy tests pass |
| S3-04 | Dynamic CRUD APIs support create, read, update, activate/deactivate, hierarchy retrieval, search, filtering, pagination, and sorting without hard-coded operational locations. | Complete: versioned CRUD/status APIs for all six resources, country hierarchy endpoint, allow-listed sort, filters, and bounded pagination; focused API tests pass |
| S3-05 | Database and service validation enforce required names/codes, parent ownership, uniqueness scopes, timezone format/default, active-state rules, and safe update behavior. | Complete: Flyway V3/V4 checks nonblank fields and timezone path format; service validates IANA IDs; ISO alpha-2, parent activity, recursive active descendants, scope moves, and nullable PUT rules are tested |
| S3-06 | Authorization enforces Super Admin system-wide organization control and denies unauthorized or out-of-scope organization mutations; all protected endpoints use server-side permission checks. | Complete: Organization delegates every `/api/v1` request to Identity; active users may read, only global system admins may mutate, and authorization fails closed |
| S3-07 | API contracts use `/api/v1`, DTO boundaries, shared `ApiResponse`/`ApiError`, stable business error codes, correlation IDs, Bean Validation, and OpenAPI documentation. | Complete: 38 organization operations are documented by the live OpenAPI contract and stage JSON; exact routes, bearer security, DTO boundary, shared error schema, correlation headers, and stable errors are asserted by the API contract test |
| S3-08 | Cross-service references remain identifier-only; identity users continue storing organization IDs without cross-service database foreign keys or shared JPA entities. | Complete: Identity persists department/building and role-scope IDs as UUID scalars; migration/JPA metadata tests prove no Organization tables, entities, or foreign keys are owned by Identity |
| S3-09 | Repository, migration, service, controller, authorization, hierarchy, uniqueness, lifecycle, pagination, and error behavior tests pass using isolated test configuration. | Complete: Organization's 21 tests cover migrations/repositories, service/domain rules, API/controller envelopes, authorization, hierarchy, uniqueness, lifecycle, pagination, and errors; the test profile asserts H2 PostgreSQL mode, Hibernate validation, and Flyway V1-V4. Identity and full-reactor Java test runs passed with no failures/errors |
| S3-10 | Organization documentation, SRS/ERD alignment notes, API contract, environment/configuration notes, changelog, and service README are synchronized before the stage gate. | Complete: roadmap, organization/identity alignment notes, service/API READMEs, stage contract, and changelog agree; DB, Identity URL, and Organization port settings are documented in `.env.example`/service notes; SRS/ERD unchanged |
| S3-11 | Stage 3 clean service test, identity regression, shared Stage 1 regression, and full reactor verification pass with no unauthorized ERD or architecture changes. | Complete: clean Organization reactor 21 tests; Identity/common-web 35 tests; full clean reactor 25 suites/69 tests; zero failures/errors/skips. Approved SRS/ERD and service architecture unchanged |

### Stage 3 implementation constraints

- The Organization Service owns organization/location persistence; no other service may write its database.
- Existing identity organization fields remain UUID references resolved through APIs/events, never cross-service foreign keys.
- Operational locations, departments, statuses, timezones, and hierarchy data must be database/API driven rather than hard-coded.
- Any required ERD, public API, service-boundary, authorization, or business-rule change must be proposed and approved before implementation.
- S3-01 through S3-11 are implemented and verified; Stage 3 is accepted.

### Stage 4 requirement checklist: Room and resources

| ID | Requirement and acceptance expectation | Status |
|---|---|---|
| S4-01 | Room Service Flyway migrations create the ERD-owned room/resource tables: `ROOM`, `ROOM_TYPE`, `FACILITY_TYPE`, `ROOM_FACILITY`, `ROOM_PHOTO`, `ROOM_RULE`, `ROOM_RULE_ALLOWED_DEPARTMENT`, `ROOM_STATUS_HISTORY`, and `MAINTENANCE_PERIOD`, with required constraints, indexes, and service-local foreign keys. References to Organization-owned and other service data remain identifiers, not cross-service foreign keys. | Complete: PostgreSQL Flyway V1; 5 Testcontainers migration/integrity tests pass |
| S4-02 | Room JPA entities, enums, repositories, DTOs, and mappers match the approved ERD and migrations; persistence entities remain private to Room Service. | Complete: all nine entities/repositories/DTOs are Room-owned; PostgreSQL `ddl-auto=validate` and Testcontainers round-trip/mapping tests pass; cross-service references remain UUIDs |
| S4-03 | Versioned `/api/v1` APIs provide dynamic room-type and facility-type management and room create/read/update/list operations, including server-side filtering, pagination, and allow-listed sorting. | Complete: `/api/v1/room-types`, `/api/v1/facility-types`, and `/api/v1/rooms` are implemented and verified in the focused Room suite |
| S4-04 | Room validation enforces positive capacity, supported class/status values, required fields, scope-aware room name/code uniqueness within a building, and consistency between a room's building and floor using the owning Organization API. | Complete: DTO/API validation, case-insensitive building-scoped checks and PostgreSQL indexes, and fail-closed active building/floor validation through Organization APIs; 12 focused Room/client tests pass |
| S4-05 | Facility assignments support structured facility types, quantity/state, and last-serviced timestamps; duplicate or invalid room/facility assignments are rejected according to the approved ERD and API contract. | Complete: nested list/create/update APIs; validation and active catalog checks; duplicate room/type assignments rejected; PostgreSQL Testcontainers/API tests pass |
| S4-06 | Room rules support room-specific and building-default policy records, including duration/advance/cancellation bounds, recurring/external guest/approval/outside-hours flags, release buffer, active/effective state, and allowed-department references. Policy values and department identifiers are validated without direct Organization database access. | Complete: room-specific/building-default list/create plus get/update APIs; bounded policy validation, active/effective state, allowed-department validation through Organization API, V3 rule-scope integrity; focused and regression gates pass |
| S4-07 | Room photos persist provider asset identifiers, secure URLs, ordering, primary/public-approval metadata, and timestamps. A Cloudinary adapter boundary owns upload/delete operations; server-side authorization and file type/size/count validation are tested, with provider credentials externalized and no photo binary stored in Room Service. | Complete: multipart photo lifecycle, scoped `ROOM_MANAGE` Identity delegation, Cloudinary adapter, JPEG/PNG/WEBP signature and size/count checks; focused and cross-stage gates pass |
| S4-08 | Room status changes validate lifecycle rules, retain actor/reason/time in status history, and preserve records through soft deletion/deactivation. Maintenance periods are room-scoped and overlapping periods for the same room are prevented using a PostgreSQL constraint or an approved equivalent transactional strategy. | Complete: status/history are transactional; same-status changes and transitions out of `DECOMMISSIONED` conflict; soft-deleted rooms are hidden from normal reads while history remains available; room-scoped maintenance APIs rely on PostgreSQL `btree_gist` exclusion. S4-08 focused gate passed 19 tests; final integrated Room suite 24 tests, Identity/common-web 41 tests, full reactor 28 suites/103 tests; zero failures/errors/skips |
| S4-09 | Protected room mutations enforce server-side `ROOM_MANAGE` authorization within the caller's building scope; callers cannot supply trusted identity, scope, or actor values. Cross-service authorization and Organization lookups fail closed and use identifier/API boundaries. | Complete: Identity authorizes every Room write; source/destination scopes are checked on room moves; global catalog writes require system-admin; Identity returns authenticated actor UUIDs for status and maintenance attribution. Denial and outage fail closed before persistence/Organization lookup. S4-09 focused Room/client gate passed 26 tests; final integrated Room suite 24 tests, Identity/common-web 41 tests, full reactor 28 suites/103 tests; zero failures/errors/skips |
| S4-10 | Room API responses use service-owned DTOs and shared `ApiResponse<T>`/`ApiError`, stable error codes, Bean Validation, correlation IDs, and OpenAPI bearer-security documentation under `/api/v1`. | Complete: all 31 Room operations are documented with bearer security, shared success/error schemas, correlation headers, stable domain-error descriptions, and request DTOs; focused Room suite 24 tests, Identity/common-web 41 tests, full reactor 28 suites/103 tests; zero failures/errors/skips |
| S4-11 | Isolated migration, repository, service, API, authorization, uniqueness/capacity, soft-delete, rule, facility, photo-adapter, status-history, maintenance-overlap, and error-contract tests cover the implemented requirements. PostgreSQL-specific integrity behavior is verified against PostgreSQL or an equivalent integration test environment where H2 cannot faithfully exercise it. | Complete: Room/Testcontainers passed 27 tests; focused Room/client/adapter suites passed 39 tests; Identity/common-web passed 41 tests; full reactor passed 28 suites/107 tests with zero failures/errors/skips |
| S4-12 | Room Service documentation, SRS/ERD alignment, environment/configuration notes, API contract/Postman examples, changelog, focused Room test suite, Identity regression, and full-reactor verification are synchronized and pass before Stage 4 acceptance. | Complete: Room docs, Room Postman collection/validator, `ROOM_SERVICE_PORT`, and contract status synchronized; focused Room/client/adapter 39 tests, Identity/common-web 41 tests, and full reactor 28 suites/107 tests pass with zero failures/errors/skips. Stage 4 accepted by user |

### Stage 4 implementation constraints

- Room Service owns room/resource persistence; no other service writes its tables, and Room Service does not map Organization or Identity entities.
- Validate Organization and identity data through their owning service APIs. Cross-service relationships remain IDs only.
- Keep room types, facilities, status, rules, and operational resource data database/API driven; do not seed hard-coded production catalogs unless explicitly approved.
- Keep Cloudinary behind an adapter. Do not expose provider credentials, trust client-supplied asset URLs, or couple room persistence directly to provider SDK details.
- Work one S4 requirement at a time. A requirement is not complete until its focused tests and required Identity/shared/full-reactor regressions pass and its documentation/contract evidence is synchronized.
- Any ERD, public API, service-boundary, authorization, or business-rule change must be proposed and explicitly approved before implementation.
- S4-09 derives status-history and maintenance actor IDs from Identity; Room callers cannot submit trusted actor, role, or scope values. Building-scoped mutations require `ROOM_MANAGE` in every affected building; unscoped catalog writes require global system-admin authority.
- S4-10 documents all 31 Room operations with bearer security, shared response/error schemas, correlation IDs, request DTOs, and stable error codes. S4-11 test verification is complete; S4-12 remains the Stage 4 documentation and acceptance gate.
- S4-12 documentation and verification are complete. Room runtime/configuration notes, all 31 Room Postman requests and their validator, API stage contract, and changelog are synchronized. Requirements and ERD were unchanged. Stage 4 was explicitly accepted by the user before Stage 5 began.

### Stage 5 requirement checklist: Scheduling and availability

| ID | Requirement and acceptance expectation | Status |
|---|---|---|
| S5-01 | Scheduling Service owns Flyway migrations, JPA entities, repositories, and PostgreSQL integrity for `WORKING_CALENDAR`, `WORKING_DAY_WINDOW`, `HOLIDAY`, `CLOSURE_PERIOD`, and `RECURRENCE_RULE` as represented in the approved ERD. Cross-service building/user IDs remain scalar identifiers. `MAINTENANCE_PERIOD` stays Room-owned; confirmed `RESERVATION`/occupancy stays Booking-owned in S6. | Complete: Flyway V1, five Scheduling entities/repositories, PostgreSQL `tstzrange` mapping, schema/FK/constraint/round-trip tests; focused Scheduling 5 tests, Identity/common-web 41 tests, full reactor 28 suites/111 tests, zero failures/errors/skips |
| S5-02 | Versioned `/api/v1` calendar APIs manage calendars and working-day windows using service-owned DTOs, validation, shared response/error envelopes, correlation IDs, OpenAPI, and server-side authorization. Calendar timezone values are valid IANA zone IDs; window weekdays and times obey the ERD constraints. | Complete: nine calendar/window operations; Identity-delegated read/manage authorization; bounded paging, IANA timezone and weekday/time validation; PostgreSQL/API/OpenAPI tests and full backend reactor passed |
| S5-03 | Versioned APIs manage holidays and closure periods with calendar/building/nationwide scope, active/blocking behavior, date/range validation, persistence integrity, authorization, and shared errors. Scheduling stores no Room maintenance or Booking reservation rows. | Planned |
| S5-04 | Recurrence rules are persisted and validated against the approved RRULE-based ERD model, including timezone and bounded date/count limits. Rule evaluation must be deterministic and must not generate unbounded occurrences. | Planned |
| S5-05 | Scheduling exposes a versioned scheduling-constraint validation API for Booking Service to validate working windows, holidays, closures, timezone, and recurrence before confirming or changing a reservation. Dependency and validation failures fail closed; Scheduling does not mutate occupancy. | Planned |
| S5-06 | Availability queries calculate candidate windows from calendar rules and current Room status/rules/maintenance through owning-service APIs, then account for Booking-owned occupancy through an explicit API/event contract. Scheduling does not read Booking or Room databases directly. Missing/unavailable authoritative inputs cannot be reported as free availability. | Planned |
| S5-07 | Focused PostgreSQL migration/repository/domain/API/authorization/timezone/holiday/closure/recurrence/availability tests pass, followed by Identity/common-web and full-reactor regressions; docs/contracts/configuration are synchronized before S5 acceptance. Booking's concurrent reservation exclusion/locking tests remain an S6 acceptance requirement. | Planned |

### Stage 5 implementation constraints

- Scheduling owns working calendars, day windows, holidays, closures, recurrence rules, scheduling validation, and availability calculation.
- Room continues to own maintenance periods and room status/rules. Booking owns confirmed reservations and the authoritative overlap constraint.
- Booking's occupied interval includes the configured five-minute release buffer; a read-only availability result is never the final double-booking barrier.
- Cross-service references are UUIDs and cross-service checks use APIs/events only. No cross-service database foreign keys or duplicated occupancy persistence.
- Keep operational calendar data dynamic; do not hard-code or silently seed working windows or holidays.
- Any required ERD, public API, authorization, service-boundary, or business-rule change must be proposed and explicitly approved before editing the requirements or ERD.
- Work one S5 requirement at a time and pass its focused test, Identity regression, and full-reactor gate before proceeding.

S5-02 exposes `GET/POST /api/v1/working-calendars`, `GET/PUT/DELETE /api/v1/working-calendars/{calendarId}`, and `GET/POST /api/v1/working-calendars/{calendarId}/working-day-windows` plus `PUT/DELETE` for a window ID. All operations require a bearer token; active users may read and Identity system administrators may mutate. S5-02 is implemented and verified; S5-03 is next and has not started.

## Stage 1: Backend foundation

This repository contains the Stage 1 multi-service backend foundation for the RRA RoomIQ platform. Shared HTTP, validation, logging, OpenAPI, and service bootstrap conventions are implemented in `common-web` and consumed by every service module.

## Current implementation

- Shared standards: common-web
- Service baseline: identity-service plus eight runnable service modules
- Runtime: Java 25, Spring Boot 4.1.1, Spring Framework 7
- Persistence: PostgreSQL + JPA + Flyway
- Security: Spring Security
- Monitoring: Spring Boot Actuator
- Test baseline: Maven test execution using the service's application context smoke test

## Shared backend conventions

- `common-web` provides `ApiResponse<T>`, `ApiError`, and `ValidationError`.
- `GlobalExceptionHandler` standardizes validation and unexpected-error responses.
- `CorrelationIdFilter` accepts or creates `X-Correlation-ID` and propagates it through MDC and response headers.
- Shared OpenAPI metadata is auto-configured for every service.
- Shared `logback-spring.xml` emits correlation IDs in the console pattern.
- Every service has a Spring Boot entry point, Actuator health endpoints, runtime configuration, Maven packaging, and a context smoke test.
- Domain request DTOs must remain inside their owning service and use Jakarta Bean Validation at controller boundaries.

## Target service structure

The backend is intended to evolve into the following service layout under backend/:

- api-gateway
- common-web
- identity-service
- organization-service
- room-service
- scheduling-service
- booking-service
- notification-service
- audit-service
- analytics-service
- infrastructure
- docs

## Design principles

- Each service owns its own domain and data boundary.
- No cross-service DB access or entity sharing.
- APIs are versioned under /api/v1.
- DTOs are used at API boundaries.
- Spring Security controls authorization.
- Business rules belong in the service layer.
- PostgreSQL is authoritative for persistent business data.
- RabbitMQ and Redis support asynchronous workflows and operational coordination.

## Environment conventions

- Local environment configuration is externalized through .env files.
- Do not commit real secrets.
- Use placeholder values in example files.

## Validation status

The identity-service foundation currently validates with Maven.

### Verified command

```bash
cd backend/identity-service
./mvnw clean test
```

This is the authority for the Stage 1 baseline until the next major implementation step is approved.

## Stage 2 progress

- `S2-01` Identity schema migration: complete.
- `S2-02` Identity domain entities: complete.
- `S2-03` Identity repositories: complete.
- `S2-04` User request/response DTOs: complete.
- `S2-05` User management APIs: complete.
- `S2-06` Role and permission management: complete.
- `S2-07` Privilege management: complete.
- `S2-08` Password security: complete.
- `S2-09` Authentication: complete.
- `S2-10` Session management: complete.
- `S2-11` Authorization policies: complete.
- `S2-12` Explicit Spring Security route configuration: complete.
- `S2-13` Identity OpenAPI endpoint and security documentation: complete.
- `S2-14` Identity error and audit behavior: complete.
- `S2-15` Automated identity tests: complete.
- `S2-16` Postman contract and sample data: complete.
- `S2-17` SRS/ERD/documentation synchronization: complete.
- The first identity Flyway migration is applied and verified in the identity-service H2 test context.
- Identity entities, relationships, audit defaults, optimistic locking, and soft-delete filtering are verified in the identity-service H2 test context.
- Repository query behavior is verified against the Flyway schema in H2.
- User DTO validation and shared `ApiError` formatting are verified with focused tests.
- User CRUD, status, listing, and soft-delete endpoints are verified with H2 MockMvc tests; responses use shared envelopes and optional passwords are BCrypt encoded.
- User management APIs and login/refresh/logout flows are complete; centralized method-security policies now enforce role, permission, privilege, and building scope, including the scoped Admin, Secretary, and Super Admin matrix.
- Role/permission definitions and role-permission/user-role assignment lifecycle are verified in H2; assignment duplicates are rejected.
- Privilege grants are evaluated by active account state and validity windows; overlap, adjacency, revoke, and regrant behavior is covered by H2 tests.
- Password credentials use BCrypt strength 12 and the validated 12–72 printable ASCII composition policy; only ACTIVE users with a stored hash are enabled by the identity-backed `UserDetailsService`.
- Login, token issuance, refresh rotation, logout, and active-session JWT validation are covered by H2 MockMvc tests.
- Session tests verify hashed refresh persistence, configured expiry, IP/user-agent capture, old-session revocation on rotation, logout revocation, and durable expiry revocation.
- Identity API business errors now use stable codes; authentication/authorization errors preserve the same validated correlation ID in the response and logs.
- Identity user, role, permission, role-assignment, privilege, login, logout, refresh, authentication-failure, and authorization-denial events are written transactionally to an identity outbox. The relay publishes persistent v1 JSON messages and marks them delivered only after a positive broker publisher confirm and no mandatory return; failed/unroutable sends are retried. Consumers must deduplicate by event ID.
- Audit payloads exclude credentials, password hashes, access/refresh tokens, and user email/profile values. Unexpected-error logs include the exception type, not exception messages or stack traces.
- Automated identity coverage includes unit, repository, migration, controller, authentication, security, authorization, audit, and error-contract tests; the clean identity/common-web suite passed 12 suites and 37 tests.
- The Postman v2.1 identity collection covers all 23 identity operations plus health/OpenAPI support, chains tokens and resource IDs, uses reserved `example.test` samples, and is checked by `node api-contracts/validate-identity-postman-collection.mjs`.
- SRS/ERD traceability is documented in [identity-srs-erd-alignment.md](docs/identity-srs-erd-alignment.md); the approved SRS and Mermaid ERD were reviewed and remain unchanged.
- Credential expiry, password recovery, login throttling, and refresh-family reuse policy remain later requirements.

## Implementation changelog

### 2026-10-05
- Completed S4-01 Room Service schema ownership with Flyway V1 for all nine ERD room/resource tables, PostgreSQL constraints and indexes, and same-service foreign keys. Organization/user references remain identifiers only. PostgreSQL `btree_gist` exclusion prevents overlapping maintenance periods for the same room while allowing adjacent periods. Focused Room Testcontainers suite passed 5 tests; Identity passed 35 tests, common-web 6 tests, and the full backend reactor test run passed. No ERD changes were required.
- Completed S4-02 Room JPA entities, enums, repositories, DTOs, and mapper for all nine migrated tables. Hibernate schema validation and PostgreSQL Testcontainers persistence/DTO mapping coverage pass; Organization and Identity references remain UUID scalars. Focused Room suite passed 6 tests; Identity passed 35 tests, common-web 6 tests, and the full reactor passed. No ERD changes were required.
- Completed S4-03 room API implementation and verification: `GET/POST/PUT` room-type, facility-type, and room endpoints were added under `/api/v1`, with server-side filtering, pagination, and allow-listed sorting. The focused Room suite passed 7 tests and the backend reactor remained green. No ERD changes were required.

### 2026-10-07
- Completed S4-08 Room status/history, soft deletion, and room-scoped maintenance APIs. Status updates and history are transactional; invalid no-op/reactivation transitions are rejected; deleted rooms are hidden from ordinary reads while their history remains queryable. Maintenance overlap remains protected by the existing PostgreSQL exclusion constraint. Focused Room suite passed 19 tests at the S4-08 gate; Identity/common-web passed 41 tests and the full reactor passed 28 suites/97 tests. No ERD or migration change was needed.
- Completed S4-09 server-side Room mutation authorization. Identity evaluates active user and `ROOM_MANAGE` building scope for room, facility, rule, photo, status, soft-delete, and maintenance writes; both old and new building scopes are checked for room moves. Room/facility catalog writes require global system-admin authority. Identity returns the authenticated actor UUID for status and maintenance records; client actor fields were removed. Authorization failures and Identity outages fail closed before writes and Organization lookups. Focused Room/client tests passed 26 tests, Identity/common-web 41 tests, and full reactor passed 28 suites/101 tests with zero failures/errors/skips. S4-10 API/error/OpenAPI completeness is next.
- Completed S4-10 Room API contract coverage. The Room OpenAPI customizer documents all 31 `/api/v1` operations with JWT bearer security, request correlation headers, success `ApiResponse`, `ApiError` schemas for validation/auth/not-found/conflict/dependency errors, and stable Room error codes. Tests verify route inventory, schemas, security, correlation, Bean Validation, and runtime error envelopes. Focused Room suite passed 24 tests, Identity/common-web passed 41 tests, and full reactor passed 28 suites/103 tests with zero failures/errors/skips. No ERD or requirements change was needed; S4-11 followed as the next verification gate.
- Completed S4-11 Room verification: PostgreSQL Testcontainers cover migration/schema integrity, repository mappings, API/service behavior, authorization, uniqueness/capacity, soft deletion, rules, facilities, photos, status history, maintenance overlap, and shared error/correlation responses. Cloudinary adapter failures and malformed responses are tested; uploaded assets are compensated when provider metadata is invalid or Room persistence fails. The tests exposed and fixed missing cleanup for invalid provider metadata. Room/Testcontainers passed 27 tests, focused Room/client/adapter suites passed 39 tests, Identity/common-web passed 41 tests, and the full reactor passed 28 suites/107 tests with zero failures/errors/skips. No ERD, requirements, or migration change was needed; S4-12 followed as the documentation/configuration synchronization gate.
- Completed S4-12 Room documentation and acceptance-readiness synchronization. Added a Room Postman collection and route/auth/correlation validator covering all 31 documented operations, documented `ROOM_SERVICE_PORT=8083` and all Room integration/photo settings, synchronized service/docs/contract status, and reran the Room, Identity/common-web, and full-reactor gates: 39 focused tests, 41 Identity/common-web tests, and 28 suites/107 reactor tests, all with zero failures/errors/skips. No ERD or requirements changes were needed. Stage 4 was explicitly accepted by the user before Stage 5 work began.
- Completed S5-01 Scheduling-owned persistence for Working Calendar, Working Day Window, Holiday, Closure Period, and Recurrence Rule. Flyway V1, JPA entities/repositories, PostgreSQL `tstzrange` mapping, check constraints, and FK-boundary tests match the approved ERD; Building and creator IDs remain scalars. Scheduling/Testcontainers passed 5 tests, Identity/common-web passed 41 tests, and the full reactor passed 28 suites/111 tests with zero failures/errors/skips. No ERD change was needed; S5-02 calendar and working-window APIs are next.
- Completed S5-01 persistence follow-up with Flyway V2's partial unique index enforcing at most one calendar per non-null office-building ID, as represented by the ERD relationship. PostgreSQL coverage confirms duplicate building calendars fail while multiple null-scoped calendars remain valid; the ERD was unchanged.
- Completed S5-02 calendar and working-window APIs. Added nine versioned operations with service-owned DTOs, bounded calendar paging, IANA zone validation, ERD weekday/time checks, non-cascading calendar deletion, shared envelopes/correlation IDs, bearer OpenAPI, and fail-closed Identity authorization (active users read; system admins manage). The focused Scheduling and full backend Java test gates passed; S5-03 is next.
- Completed S4-04 room validation: required request fields, positive capacity, supported room class/status, case-insensitive name/code uniqueness per building (including update/move conflicts), and active building/floor ownership are enforced. Room Service validates Organization references via the owning service's authenticated GET APIs, forwards the caller's bearer token, and fails closed on missing/denied/unavailable references. Added Flyway V2 functional unique indexes without modifying V1 or the approved ERD. Focused Room and Organization-client tests passed 12/12; Identity/common-web passed 35 tests; clean full backend reactor passed 26 suites/80 tests with zero failures/errors/skips.
- Completed S4-05 Room facility assignments: added `GET/POST/PUT /api/v1/rooms/{roomId}/facilities`, structured type/quantity/state/last-serviced request and response handling, positive `SMALLINT` quantity validation, active facility-type and room existence checks, and duplicate assignment rejection on create/update. The existing ERD unique constraint remains authoritative; no ERD change was needed. Focused Room/client tests passed 13 tests; Identity/common-web passed 35 tests; clean full backend reactor passed 26 suites/81 tests with zero failures/errors/skips.
- Completed S4-06 Room rules: added room-specific and building-default list/create APIs plus rule get/update, all duration/advance/cancellation/flag/release-buffer/active/effective fields, default release buffer, and allowed-department replacement. Department IDs are checked through the authenticated Organization API for active status and compatible building scope; no direct Organization database access was added. Added Flyway V3 `ck_room_rule_exactly_one_scope` to enforce the existing ERD's room-specific versus building-default ownership invariant. Focused Room/client tests passed 18 tests; Identity/common-web passed 35 tests; clean full backend reactor passed 26 suites/86 tests with zero failures/errors/skips. The approved ERD and requirements were not modified.
- Completed S4-07 Room photos: added list/upload/update-metadata/delete APIs, a Cloudinary storage adapter, Identity-delegated `ROOM_MANAGE` authorization per building, and server-side file signature/MIME/size/count validation. Photo bytes are transient request data only; Room Service stores the Cloudinary public ID, HTTPS URL, ordering, primary/public flags, and creation time. Cloudinary credentials and upload limits are externalized. Focused Room/adapter/auth tests passed 26 tests; Identity/common-web passed 35 tests; clean full backend reactor passed 28 suites/94 tests with zero failures/errors/skips. Existing ERD fields were sufficient; no ERD change was made.

### 2026-10-01
- Completed S3-05 organization validation and lifecycle rules: Flyway V3 nonblank checks, IANA timezone validation, ISO alpha-2 validation/normalization, parent activity enforcement, active-descendant deactivation protection, safe nullable PUT behavior, department subtree scope protection, and uniqueness/error coverage. No ERD change was required. Organization/common-web passed 7 suites/20 tests; identity/common-web passed 12/37; the full reactor passed 23 suites/58 tests with zero failures/errors/skips, and `mvn package` succeeded.

### 2026-10-03
- Completed S3-11 final verification gate: clean Organization service/reactor passed 21 tests; dedicated Identity/common-web reactor passed 35 tests; clean full backend reactor passed 25 suites and 69 tests with zero failures/errors/skips. No unauthorized ERD or architecture changes were found. Stage 3 now awaits explicit user acceptance; Stage 4 has not started.
- Added `ORGANIZATION_SERVICE_PORT=8082` to `.env.example` so the documented Organization port override is available in local configuration.
- Completed S3-10 Organization documentation and contract synchronization: aligned the roadmap, stage contract, organization service README, SRS/ERD alignment note, architecture/docs summaries, API contract README, environment/configuration notes, and changelog. Documented `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `IDENTITY_SERVICE_URL`, and the `ORGANIZATION_SERVICE_PORT` default. The approved requirements and ERD were not modified. Contract/document consistency checks and the final Identity/full-reactor test gates passed.
- Completed S3-09 Organization verification coverage: added MVC validation checks for negative page, oversized page size, and invalid sort direction; asserted isolated H2 PostgreSQL-mode test URL, Hibernate `ddl-auto=validate`, and Flyway V1-V4 in the Organization context test. All 21 Organization tests and the Identity/full backend parent-reactor test run passed with no failures/errors. No ERD change was needed.
- Completed S3-08 cross-service identifier-only persistence boundary: verified Identity stores `department_id`, `office_building_id`, and `scope_office_building_id` as UUID values without Organization JPA entities, tables, or foreign keys; added an H2/Flyway regression test that persists unbacked IDs and inspects imported FK metadata. The focused `IdentityRepositoryTests` class passed all 3 tests, and the backend parent-reactor test suite passed with no failures/errors. Organization-local hierarchy foreign keys and the approved ERD are unchanged.

### 2026-10-02
- Completed S3-07 Organization API contract/OpenAPI completeness: documented all 38 `/api/v1` operations, DTOs, shared success/error envelopes, bearer security, correlation headers, validation rules, and stable error codes in the stage contract; expanded the OpenAPI test to compare the exact route set and documented errors. Focused `OrganizationCrudApiTests` passed 6 tests; the backend parent-reactor Java test run passed with no failures/errors. No ERD change was needed.
- Closed S3-05 validation gaps with Flyway V4 timezone path-format checks and DTO acceptance of whitespace-only optional country ISO codes as clear-to-null. Exact IANA timezone membership remains enforced by DTO/service validation; no ERD change was needed. Organization/common-web passed 7 suites/20 tests and identity/common-web passed 12 suites/37 tests, all with zero failures/errors/skips.
- Completed S3-06 organization authorization through Identity Service. Every Organization `/api/v1` request forwards its bearer token to an Identity-owned authorization endpoint; active users may read, and mutations require the current unscoped `IDENTITY_SYSTEM_ADMIN` permission. Missing credentials return 401, policy denials return 403, and Identity outages fail closed with 503. Identity/common-web passed 13 suites/40 tests, Organization/common-web passed 8 suites/24 tests, and the full reactor passed 25 suites/65 tests, all with zero failures/errors/skips. No ERD change was needed.

### 2026-09-30
- Added stable identity domain error codes and consistent correlation-ID validation/propagation.
- Added Flyway V2 identity audit outbox and durable RabbitMQ relay with retry/backoff.
- Recorded identity authentication, authorization, user, role, permission, assignment, and privilege events without sensitive payload data.
- Added focused error, correlation, audit-payload, and outbox-delivery tests; synchronized the API contract.
- Completed S2-15 automated identity coverage and S2-16 Postman collection/sample-data synchronization.
- Completed S2-17 SRS/ERD alignment documentation and synchronized all backend README, identity, API-contract, and changelog surfaces.
- Final clean backend reactor verification passed 20 suites and 45 tests with zero failures/errors.
- Defined the Stage 3 Organization and Locations checklist and constraints; S3-01 organization schema was subsequently approved, implemented, and verified.
- Added organization Flyway V1 for the six location/department tables and synchronized the approved Floor timestamp with the ERD.
- Verified the focused organization migration tests, identity/common-web regression (12 suites/37 tests), and full reactor (21 suites/47 tests); all passed with zero failures/errors.
- Completed S3-02 Organization JPA entities, DepartmentStatus, repositories, validated create/response DTOs, mapper, and Hibernate schema validation.
- Verified the focused organization suite (3 suites/4 tests), identity/common-web (12 suites/37 tests), and full reactor (22 suites/48 tests); all passed with zero failures/errors/skips and the full build succeeded.
- Completed S3-03 organization hierarchy integrity with a V2 direct-self-parent check, transactional cycle/scope validation for department reparenting, and FK/cycle tests.
- Verified S3-03 focused organization tests (3 suites/5 tests), identity/common-web (12/37), and full reactor (22/49), all with zero failures/errors/skips; reactor package build succeeded.
- Completed S3-04 `/api/v1` CRUD, status, search/filter/page/sort, and country-rooted hierarchy APIs for all organization resources. Authorization remains S3-06 scope.
- Verified focused Organization APIs (4 suites/6 tests), identity/common-web (12/37), and full reactor (23/50), all with zero failures/errors/skips; full reactor package build succeeded.
- Verified the focused organization persistence suite: 3 suites/4 tests passed with zero failures/errors.

### 2026-09-28
- Added common-web shared backend standards module.
- Initialized every service module as a runnable Spring Boot Maven module.
- Added service-specific ports, configuration, Actuator, logging, and context smoke tests.
- Replaced identity-service-local response, error, exception, and OpenAPI classes with shared implementations.
- Added structured validation errors and correlation ID propagation.

### 2026-09-27
- Initialized identity-service foundation
- Fixed PostgreSQL datasource configuration via environment placeholders
- Added runtime and test configuration for Spring Boot
- Created the multi-service backend directory scaffold for Stage 1
- Added documentation baseline and infrastructure placeholders
- Prepared local infrastructure defaults for PostgreSQL, RabbitMQ, and Redis
