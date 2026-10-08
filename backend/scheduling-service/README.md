# RRA RoomIQ Scheduling Service

## Current scope

Stage 4 S4-01 through S4-12 is accepted. Stage 5 is in progress: S5-01 persistence through S5-04 recurrence rules are complete and verified; S5-05 scheduling-constraint validation is next. Availability calculation and Booking occupancy integration are not implemented yet.

## Ownership boundary

Scheduling owns `working_calendar`, `working_day_window`, `holiday`, `closure_period`, and `recurrence_rule`. Foreign keys are limited to Scheduling-owned relationships: working-day windows require a local calendar, and holidays may reference a local calendar. Building and creator/user IDs remain UUID scalars.

Room Service continues to own room status/rules and `maintenance_period`. Booking Service will own confirmed reservation/occupancy persistence and its authoritative conflict-prevention constraint in S6. Scheduling must not persist duplicate occupancy or use a cross-service database foreign key. Availability must consult Booking through an API/event contract; a read result is not the final double-booking guard. Booking's occupied interval includes the five-minute release buffer.

## S5-01 persistence

Flyway V1 creates the five Scheduling tables from the approved ERD. Flyway V2 adds a partial unique index allowing at most one calendar per non-null `office_building_id`, matching the ERD's optional one-to-one building/calendar relationship while allowing multiple global (`NULL` building) calendars. PostgreSQL checks validate weekday numbers, increasing working-window times, nonempty closure ranges, recurrence end dates, and positive occurrence counts. Calendar names are unique. `closure_period.period` remains PostgreSQL `TSTZRANGE` and is mapped through the service-local `PostgreSqlTstzRangeType`.

## S5-02 calendar and window APIs

Calendar routes are under `/api/v1/working-calendars`: GET/POST collection, GET/PUT/DELETE by calendar ID. Calendar lists support bounded paging (default 0/20, maximum size 100), name search, building/active filters, and allow-listed name sorting. Working-day-window routes are nested under `/api/v1/working-calendars/{calendarId}/working-day-windows` and support GET/POST collection plus PUT/DELETE by window ID. All request/response types are Scheduling-owned DTOs using shared `ApiResponse<T>` and `ApiError` envelopes.

Calendar timezones must be recognized IANA `ZoneId` values. Window weekdays are 1-7 and `closeTime` must be later than `openTime`, as required by the ERD. Calendar deletion conflicts while windows or holidays reference it; no cascading delete is performed. Identity authorization is delegated for every `/api/v1` request: active users may read, while mutations require system-admin authority. Missing credentials, denial, and Identity unavailability fail closed. OpenAPI documents bearer security, request/response `X-Correlation-ID`, DTOs, and stable errors.

## S5-03 holiday and closure APIs

Holiday CRUD is under `/api/v1/holidays`; list supports bounded paging, calendar/building/nationwide scope filters, active/blocking filters, and date bounds. `workingCalendarId` and `officeBuildingId` are scalar IDs; both may be supplied for a calendar/building intersection, and the combination is rejected if it contradicts a building-scoped calendar. Both null means nationwide. The authenticated actor ID returned by Identity is stored as `created_by_user_id`; callers cannot provide trusted attribution. Holiday updates control `active` and `blocksBooking`. A partial-null-aware V3 unique index rejects duplicate holiday dates for the exact calendar/building/nationwide scope.

Closure CRUD is under `/api/v1/closure-periods`; list supports bounded paging and building/nationwide plus `blocksBooking` filters. A null `officeBuildingId` means nationwide. Inputs require finite `startsAt` and `endsAt` Instants with end strictly after start; persistence uses the half-open PostgreSQL `tstzrange` `[start,end)`. The API does not create Room maintenance or Booking reservation rows. All ten operations are bearer-protected by Identity: active users may read and only system admins may manage. Identity outages fail closed. OpenAPI documents the shared envelopes, correlation header, and stable error responses.

## S5-04 recurrence rules

Recurrence rules use the existing `recurrence_rule` table and approved ERD fields; no migration or ERD change was required. Protected CRUD endpoints are rooted at `/api/v1/recurrence-rules`. Creation and updates validate and normalize date-based RRULE values; GET `/{recurrenceRuleId}/occurrences` deterministically returns the rule's local occurrence dates and its timezone. Creator identity is derived from the authenticated Identity response, never from a request field. Active users may read and only system administrators may manage, consistent with the existing Scheduling authorization policy.

Supported RRULE components are `FREQ` (`DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`), `INTERVAL`, `BYDAY` (weekday tokens, without ordinal selectors), `BYMONTHDAY`, `BYMONTH`, `WKST`, `UNTIL` (iCalendar DATE, `YYYYMMDD`), and `COUNT`. Unknown, repeated, malformed, and unsupported components are rejected. `startsOn` is a local date and must match the selected pattern. At least one of `UNTIL` or `COUNT` is required; if both are present, occurrences stop at whichever bound is reached first. `COUNT` cannot exceed 365, and `UNTIL` must be no later than the inclusive date `startsOn + 1 year`; count-only rules must be able to reach their requested count within that horizon. A rule that would exceed either maximum is rejected, not truncated.

Expansion operates on `LocalDate` values, avoiding elapsed-time/DST shifts; the stored IANA timezone identifies the local calendar context. Rwanda calendars should use `Africa/Kigali`. Occurrence evaluation returns dates only: it does not create booking requests, confirmed reservations, or occupancy. Each future booking occurrence still requires the applicable Booking and Scheduling validation before confirmation.

## Runtime configuration

`src/main/resources/application.yml` reads `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, runs Flyway at startup, and sets Hibernate `ddl-auto=validate`. The service port is `SCHEDULING_SERVICE_PORT`, default `8084`; `IDENTITY_SERVICE_URL` configures the authorization endpoint and defaults to `http://localhost:8081`. Shared example values are in `backend/.env.example`. The PostgreSQL role running Flyway must have the permissions needed to create the Scheduling tables; the range type itself requires no extension.

## Verification

Migration, PostgreSQL constraint, foreign-key ownership, repository, and range round-trip tests use Testcontainers and require Docker:

```bash
mvn -f backend/pom.xml -pl scheduling-service -am test
mvn -f backend/pom.xml -pl identity-service -am test
mvn -f backend/pom.xml test
```

S5-01 persistence through S5-04 recurrence APIs passed PostgreSQL-backed Scheduling tests and the full backend Java test gate with no reported failures or errors. S5-04 focused tests cover 365 accepted occurrences, over-limit count and date horizon rejection, required bounds, invalid dates and timezones, pattern/date consistency, both UNTIL and COUNT, DST-adjacent local dates, persistence, CRUD, authorization, and OpenAPI. Identity authorization/OpenAPI coverage passed. The approved requirements and ERD were unchanged.