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

Stage 2 identity requirements S2-01 through S2-18 and Stage 3 S3-01 through S3-11 are complete and accepted. Stage 4 S4-01 through S4-12 is complete and accepted. Room Service owns the nine ERD room/resource tables, Room APIs, Identity-delegated mutation authorization, PostgreSQL integrity, and the shared response/error/OpenAPI contract. Stage 5 is in progress: S5-01 persistence through S5-04 recurrence APIs are complete and verified; S5-05 scheduling-constraint validation is next. Scheduling owns calendar/policy/availability data and constraints; its bounded recurrence evaluator returns local dates only and writes no reservation occupancy. Room retains maintenance, and Booking owns confirmed reservation occupancy and double-booking prevention in S6.

## Next stage

Stage 3 and Stage 4 are accepted. Stage 5 S5-01 through S5-07 are the current requirement checklist; S5-01 through S5-04 are complete and verified, and S5-05 is next.
