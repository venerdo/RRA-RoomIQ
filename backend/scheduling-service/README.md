# RRA RoomIQ Scheduling Service

## Current scope

Stage 4 S4-01 through S4-12 is accepted. S5-01 through S5-07 are implemented and verified; Stage 5 is awaiting explicit user acceptance. Availability candidates use Room owner APIs and a versioned Booking occupancy contract, but candidate-producing searches fail closed until Booking implements its S6 provider.

## Ownership boundary

Scheduling owns `working_calendar`, `working_day_window`, `holiday`, `closure_period`, and `recurrence_rule`. Foreign keys are limited to Scheduling-owned relationships: working-day windows require a local calendar, and holidays may reference a local calendar. Building and creator/user IDs remain UUID scalars.

Room Service continues to own room status/rules and `maintenance_period`. Booking Service will own confirmed reservation/occupancy persistence and its authoritative conflict-prevention constraint in S6. Scheduling must not persist duplicate occupancy or use a cross-service database foreign key. Availability consults Room over owner APIs and Booking through the protected `v1` occupancy snapshot API; a read result is not the final double-booking guard. Booking's occupied interval includes the five-minute release buffer.

## S5-01 persistence

Flyway V1 creates the five Scheduling tables from the approved ERD. Flyway V2 adds a partial unique index allowing at most one calendar per non-null `office_building_id`, matching the ERD's optional one-to-one building/calendar relationship while allowing multiple global (`NULL` building) calendars. PostgreSQL checks validate weekday numbers, increasing working-window times, nonempty closure ranges, recurrence end dates, and positive occurrence counts. Calendar names are unique. `closure_period.period` remains PostgreSQL `TSTZRANGE` and is mapped through the service-local `PostgreSqlTstzRangeType`.

## S5-02 calendar and window APIs

Calendar routes are under `/api/v1/working-calendars`: GET/POST collection, GET/PUT/DELETE by calendar ID. Calendar lists support bounded paging (default 0/20, maximum size 100), name search, building/active filters, and allow-listed name sorting. Working-day-window routes are nested under `/api/v1/working-calendars/{calendarId}/working-day-windows` and support GET/POST collection plus PUT/DELETE by window ID. All request/response types are Scheduling-owned DTOs using shared `ApiResponse<T>` and `ApiError` envelopes.

Calendar timezones must be recognized IANA `ZoneId` values. Window weekdays are 1-7 and `closeTime` must be later than `openTime`, as required by the ERD. Calendar deletion conflicts while windows or holidays reference it; no cascading delete is performed. Identity authorization is delegated for every `/api/v1` request: active users may read, while mutations require system-admin authority. Missing credentials, denial, and Identity unavailability fail closed. S5-02 route-level tests confirm unauthenticated list/create requests are rejected before Identity is called, authenticated reads delegate `READ`, and mutations delegate `MANAGE`. The API Gateway currently has no Scheduling route; gateway routing remains a Stage 11 concern. OpenAPI documents bearer security, request/response `X-Correlation-ID`, DTOs, and stable errors.

## S5-03 holiday and closure APIs

Holiday CRUD is under `/api/v1/holidays`; list supports bounded paging, calendar/building/nationwide scope filters, active/blocking filters, and date bounds. `workingCalendarId` and `officeBuildingId` are scalar IDs; both may be supplied for a calendar/building intersection, and the combination is rejected if it contradicts a building-scoped calendar. Both null means nationwide. The authenticated actor ID returned by Identity is stored as `created_by_user_id`; callers cannot provide trusted attribution. Holiday updates control `active` and `blocksBooking`. A partial-null-aware V3 unique index rejects duplicate holiday dates for the exact calendar/building/nationwide scope.

Closure CRUD is under `/api/v1/closure-periods`; list supports bounded paging and building/nationwide plus `blocksBooking` filters. A null `officeBuildingId` means nationwide. Inputs require finite `startsAt` and `endsAt` Instants with end strictly after start; persistence uses the half-open PostgreSQL `tstzrange` `[start,end)`. The API does not create Room maintenance or Booking reservation rows. All ten operations are bearer-protected by Identity: active users may read and only system admins may manage. Identity outages fail closed. OpenAPI documents the shared envelopes, correlation header, and stable error responses.

## S5-04 recurrence rules

Recurrence rules use the existing `recurrence_rule` table and approved ERD fields; no migration or ERD change was required. Protected CRUD endpoints are rooted at `/api/v1/recurrence-rules`. Creation and updates validate and normalize date-based RRULE values; GET `/{recurrenceRuleId}/occurrences` deterministically returns the rule's local occurrence dates and its timezone. Creator identity is derived from the authenticated Identity response, never from a request field. Active users may read and only system administrators may manage, consistent with the existing Scheduling authorization policy.

Supported RRULE components are `FREQ` (`DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`), `INTERVAL`, `BYDAY` (weekday tokens, without ordinal selectors), `BYMONTHDAY`, `BYMONTH`, `WKST`, `UNTIL` (iCalendar DATE, `YYYYMMDD`), and `COUNT`. Unknown, repeated, malformed, and unsupported components are rejected. `startsOn` is a local date and must match the selected pattern. At least one of `UNTIL` or `COUNT` is required; if both are present, occurrences stop at whichever bound is reached first. `COUNT` cannot exceed 365, and `UNTIL` must be no later than the inclusive date `startsOn + 1 year`; count-only rules must be able to reach their requested count within that horizon. A rule that would exceed either maximum is rejected, not truncated.

Expansion operates on `LocalDate` values, avoiding elapsed-time/DST shifts; the stored IANA timezone identifies the local calendar context. Rwanda calendars should use `Africa/Kigali`. Occurrence evaluation returns dates only: it does not create booking requests, confirmed reservations, or occupancy. Each future booking occurrence still requires the applicable Booking and Scheduling validation before confirmation.

## S5-05 scheduling-constraint validation

Booking can call the read-only `POST /api/v1/scheduling-constraints/validate` endpoint before confirming or changing a reservation. The request supplies a persisted `workingCalendarId`, `officeBuildingId`, `startsAt`/`endsAt` Instants, the expected IANA `timezone`, and an optional persisted `recurrenceRuleId`. The timezone must exactly match the selected active calendar; if a recurrence is supplied, its stored timezone must match as well. Instants are converted to local date/time using that zone. Start and end must fall on the same local date, with end later than start. Recurring rules reuse the submitted local times on every bounded occurrence date (maximum 365 from S5-04). A nonexistent recurring wall-clock time during a timezone transition is rejected; ambiguous times resolve consistently using the zone's first valid offset.

For each date, validation requires the full interval to fit inside at least one working window marked as a working day, rejects applicable active `blocksBooking` holidays (calendar, building, intersection, or nationwide), and rejects overlaps with blocking building-scoped or nationwide half-open closure ranges. Inactive calendars and calendars scoped to another building return a failed validation result. Policy conflicts are returned as HTTP 200 with `data.valid=false`, the checked occurrence count, and stable occurrence-scoped violation codes. Booking must proceed only when `data.valid=true`. Malformed intervals/timezones/recurrence combinations and missing referenced calendars/rules return errors; Identity authorization outages return 503. The validation POST uses Identity `READ` authorization because it is read-only. The API does not query other services or mutate Booking/reservation/occupancy data. No ERD or migration change was needed.

## S5-06 availability search

`POST /api/v1/availability/search` is bearer-protected and delegates read authorization to Identity. The request includes `workingCalendarId`, `officeBuildingId`, absolute `startsAt`/`endsAt` Instants, and optional `minimumCapacity`, `minimumDurationMinutes`, `facilityTypeIds`, `departmentId`, or persisted `recurrenceRuleId`. The search range is limited to 31 days, the facility filter to 25 IDs, and output to 5,000 windows; limit violations return explicit errors and never partial results.

Scheduling creates continuous candidate windows from its own calendar/day windows, applicable blocking holidays, blocking closure ranges, and optional bounded recurrence dates. It queries Room APIs for available rooms, current room and building rules, working facilities, and maintenance periods. The caller's bearer token is forwarded to Room. Invalid or unavailable owner data fails closed. Room-specific rules take precedence over building defaults, and effective-time boundaries split the candidate interval when policy changes.

If there are candidate windows, the service calls protected internal `POST /api/v1/internal/availability/occupancy` with `contractVersion: "v1"`, requested room IDs, and the search range. The response must include every requested room, finite half-open occupied intervals that already include Booking's release buffer, and a complete snapshot no more than 30 seconds old. Scheduling subtracts these intervals; missing, partial, malformed, stale, timed-out, or unavailable occupancy returns an error, never a free-availability fallback. The caller token is forwarded. This internal API is not gateway-exposed and its Booking provider does not exist until S6. Empty pre-occupancy results can return without querying Booking and explicitly set `bookingOccupancyIncluded=false`.

The response carries the calendar timezone, search and occupancy snapshot timestamps, occupancy-included and confirmation-required flags, plus room-specific continuous windows with explicit UTC Instants and local occurrence dates. For recurrence, each result is an individual RRULE occurrence; it does not guarantee a common interval across the entire series. Booking must validate the full recurrence and remains the authoritative conflict barrier. A future versioned Booking outbox event is notification/invalidation only; Scheduling does not consume or cache event data for availability.

## Runtime configuration

`src/main/resources/application.yml` reads `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, runs Flyway at startup, and sets Hibernate `ddl-auto=validate`. The service port is `SCHEDULING_SERVICE_PORT`, default `8084`; `IDENTITY_SERVICE_URL` configures the authorization endpoint and defaults to `http://localhost:8081`. `ROOM_SERVICE_URL` and `BOOKING_SERVICE_URL` configure owner-service API clients. Shared example values are in `backend/.env.example`. The PostgreSQL role running Flyway must have the permissions needed to create the Scheduling tables; the range type itself requires no extension.

## Verification

Migration, PostgreSQL constraint, foreign-key ownership, repository, and range round-trip tests use Testcontainers and require Docker:

```bash
mvn -f backend/pom.xml -pl scheduling-service -am test
mvn -f backend/pom.xml -pl identity-service -am test
mvn -f backend/pom.xml test
```

The S5-07 PostgreSQL-backed Scheduling suite passed 48 tests, including the protected S5-02 route tests and migration, repository, domain, API, timezone, holiday, closure, recurrence, and availability coverage. Identity/common-web passed 37 tests, and the full 11-module backend reactor passed 156 tests, all with zero failures, errors, or skips. API operations require caller bearer authentication and Identity authorization; no Scheduling route is configured on the Gateway pending Stage 11. Candidate-producing searches currently fail closed until S6 implements the Booking occupancy provider. No ERD or approved requirements change was required.