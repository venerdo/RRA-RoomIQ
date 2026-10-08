# RRA RoomIQ Scheduling Service

## Current scope

Stage 4 S4-01 through S4-12 is accepted. Stage 5 is in progress: S5-01 persistence and S5-02 calendar/working-window APIs are complete and verified; S5-03 holiday/closure APIs are next. Availability calculation, recurrence evaluation, and Booking occupancy integration are not implemented yet.

## Ownership boundary

Scheduling owns `working_calendar`, `working_day_window`, `holiday`, `closure_period`, and `recurrence_rule`. Foreign keys are limited to Scheduling-owned relationships: working-day windows require a local calendar, and holidays may reference a local calendar. Building and creator/user IDs remain UUID scalars.

Room Service continues to own room status/rules and `maintenance_period`. Booking Service will own confirmed reservation/occupancy persistence and its authoritative conflict-prevention constraint in S6. Scheduling must not persist duplicate occupancy or use a cross-service database foreign key. Availability must consult Booking through an API/event contract; a read result is not the final double-booking guard. Booking's occupied interval includes the five-minute release buffer.

## S5-01 persistence

Flyway V1 creates the five Scheduling tables from the approved ERD. Flyway V2 adds a partial unique index allowing at most one calendar per non-null `office_building_id`, matching the ERD's optional one-to-one building/calendar relationship while allowing multiple global (`NULL` building) calendars. PostgreSQL checks validate weekday numbers, increasing working-window times, nonempty closure ranges, recurrence end dates, and positive occurrence counts. Calendar names are unique. `closure_period.period` remains PostgreSQL `TSTZRANGE` and is mapped through the service-local `PostgreSqlTstzRangeType`.

## S5-02 calendar and window APIs

Calendar routes are under `/api/v1/working-calendars`: GET/POST collection, GET/PUT/DELETE by calendar ID. Calendar lists support bounded paging (default 0/20, maximum size 100), name search, building/active filters, and allow-listed name sorting. Working-day-window routes are nested under `/api/v1/working-calendars/{calendarId}/working-day-windows` and support GET/POST collection plus PUT/DELETE by window ID. All request/response types are Scheduling-owned DTOs using shared `ApiResponse<T>` and `ApiError` envelopes.

Calendar timezones must be recognized IANA `ZoneId` values. Window weekdays are 1-7 and `closeTime` must be later than `openTime`, as required by the ERD. Calendar deletion conflicts while windows or holidays reference it; no cascading delete is performed. Identity authorization is delegated for every `/api/v1` request: active users may read, while mutations require system-admin authority. Missing credentials, denial, and Identity unavailability fail closed. OpenAPI documents bearer security, request/response `X-Correlation-ID`, DTOs, and stable errors.

## Runtime configuration

`src/main/resources/application.yml` reads `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, runs Flyway at startup, and sets Hibernate `ddl-auto=validate`. The service port is `SCHEDULING_SERVICE_PORT`, default `8084`; `IDENTITY_SERVICE_URL` configures the authorization endpoint and defaults to `http://localhost:8081`. Shared example values are in `backend/.env.example`. The PostgreSQL role running Flyway must have the permissions needed to create the Scheduling tables; the range type itself requires no extension.

## Verification

Migration, PostgreSQL constraint, foreign-key ownership, repository, and range round-trip tests use Testcontainers and require Docker:

```bash
mvn -f backend/pom.xml -pl scheduling-service -am test
mvn -f backend/pom.xml -pl identity-service -am test
mvn -f backend/pom.xml test
```

S5-01 PostgreSQL persistence and S5-02 API/OpenAPI/authorization tests passed in the backend reactor. Identity authorization/OpenAPI tests and the full backend Java test gate passed with no reported failures or errors. The approved requirements and ERD were unchanged.