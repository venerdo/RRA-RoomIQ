# Backend Architecture Baseline

## Target architecture

The backend is designed around independent service ownership and a shared API entry pattern.

## Core principles

- Microservices boundaries are based on business ownership.
- Service-to-service communication happens through HTTP or asynchronous events.
- Scheduling business routes are guarded by service-side bearer validation and Identity authorization. Callers propagate an Identity-issued user token; the actor and permissions are resolved by Identity, not supplied by a caller. Scheduling has no API Gateway route in Stage 5; production deployments must keep the service endpoint and its OpenAPI surfaces on a private/trusted network.
- Scheduling owns calendar rules and availability calculations; confirmed reservation occupancy and its authoritative overlap barrier belong to Booking Service.
- The identity service is authoritative for auth, roles, permissions, scopes, and privilege evaluation.
- Database ownership is maintained per service.

## Current phase

Stage 2 identity requirements S2-01 through S2-18 and Stage 3 S3-01 through S3-11 are complete and accepted. Stage 4 S4-01 through S4-12 and Stage 5 S5-01 through S5-07 are complete and accepted. Room Service owns the nine ERD room/resource tables, Room APIs, Identity-delegated mutation authorization, PostgreSQL integrity, and the shared response/error/OpenAPI contract. Scheduling owns calendar/policy/availability calculations and constraints; bounded recurrence evaluation returns local dates, S5-05 validates candidate local times, and S5-06 returns continuous candidate intervals using Room owner APIs and a protected Booking snapshot contract. Candidate-producing searches fail closed until Booking implements its S6 occupancy provider. All Scheduling `/api/v1/**` requests, including `OPTIONS`, require bearer authentication and Identity authorization; Identity is authoritative for actor/permissions and unauthenticated, inactive, denied, or unavailable cases fail closed. The service routes are not configured on the API Gateway in Stage 5; Gateway publication is Stage 11. Direct authorized service access is not equivalent to public routing and production network/ingress policy must keep the service and its OpenAPI surfaces private. Room retains maintenance, and Booking owns confirmed reservation occupancy and double-booking prevention in S6.

## Next stage

Stages 3, 4, and 5 are accepted. The Stage 6 checklist is proposed in [Backend Documentation](../Documentation.md#stage-6-requirement-checklist-proposed-pending-user-approval); no Booking workflow API has been implemented or exposed. Checklist approval is pending. The user selected all-or-nothing recurring-series conflict handling, with no partial persistence when an occurrence fails.
