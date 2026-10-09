# Backend Architecture Baseline

## Target architecture

The backend is designed around independent service ownership and a shared API entry pattern.

## Core principles

- Microservices boundaries are based on business ownership.
- Service-to-service communication happens through HTTP or asynchronous events.
- Scheduling owns calendar rules and availability calculations; confirmed reservation occupancy and its authoritative overlap barrier belong to Booking Service.
- The identity service is authoritative for auth, roles, permissions, scopes, and privilege evaluation.
- Database ownership is maintained per service.

## Current phase

Stage 2 identity requirements S2-01 through S2-18 and Stage 3 S3-01 through S3-11 are complete and accepted. Stage 4 S4-01 through S4-12 is complete and accepted. Room Service owns the nine ERD room/resource tables, Room APIs, Identity-delegated mutation authorization, PostgreSQL integrity, and the shared response/error/OpenAPI contract. Stage 5 S5-01 through S5-07 are implemented and verified; Stage 5 awaits explicit user acceptance. Scheduling owns calendar/policy/availability calculations and constraints; bounded recurrence evaluation returns local dates, S5-05 validates candidate local times, and S5-06 returns continuous candidate intervals using Room owner APIs and a protected Booking snapshot contract. Candidate-producing searches fail closed until Booking implements its S6 occupancy provider. S5-02's service-hosted calendar routes require bearer authentication and Identity authorization; no Gateway route exists yet (Gateway integration is Stage 11). Room retains maintenance, and Booking owns confirmed reservation occupancy and double-booking prevention in S6.

## Next stage

Stage 3 and Stage 4 are accepted. Stage 5 S5-01 through S5-07 are implemented and verified; explicit Stage 5 acceptance remains pending.
