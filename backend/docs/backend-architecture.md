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

Stages 3, 4, and 5 are accepted, and the Stage 6 checklist is approved in [Backend Documentation](../Documentation.md#stage-6-requirement-checklist-approved). S6-01 through S6-04 are complete; S6-05 is in progress. Booking exposes protected request create/list/get, draft-submit, and decision APIs; approval/rejection decisions revalidate current Identity authorization and Organization, Room, and Scheduling owner data, plus Booking-owned occupancy. Approval atomically persists the decision, confirmed reservation, private meeting, and requester organizer participant; rejection persists no reservation or meeting. The user approved a Booking-owned `reservation_occurrence` table: one series-level reservation has one child per evaluated local date, each with a buffered half-open range protected by a PostgreSQL GiST exclusion constraint. The direct-booking operation and Scheduling's validated local occurrence intervals are being implemented against this model. Owner-service calls propagate the caller bearer token and fail closed; Booking does not read cross-service databases and has no Gateway route. Cancellation, extensions, share links, occupancy-provider, and remaining Stage 6 workflows remain future requirements; lifecycle operations must retain occurrence history and release or adjust occurrence occupancy consistently. The user selected all-or-nothing recurring-series conflict handling, with no partial persistence when an occurrence fails.
