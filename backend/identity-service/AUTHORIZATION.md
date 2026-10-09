# Identity Authorization

## Enforcement model

Authorization is centralized in `IdentityAuthorizationService` and applied at the controller boundary with method security. Every request uses the validated JWT subject and reloads the current account/permission state from the identity database. A stale token therefore cannot preserve a permission after it is revoked or an account is disabled. Missing permission, inactive account, unknown scope, and cross-building access fail closed with HTTP 403 and the shared `ApiError` response.

The evaluator supports both database-backed role/permission assignments and trusted Spring authorities supplied by infrastructure. The service does not trust role, scope, status, or approval values supplied in request bodies.

## Organization Service delegation

`POST /api/v1/internal/authorization/organization` is authenticated by the same bearer-JWT/session filter as other protected identity APIs. It accepts only an action (`READ` or `MANAGE`) and evaluates the caller from the JWT subject and current database state. Active accounts may read; `MANAGE` requires the unscoped `IDENTITY_SYSTEM_ADMIN` permission. Organization Service calls this endpoint for each `/api/v1` request and treats Identity unavailability as denial, never as permission to proceed.

`POST /api/v1/internal/authorization/scheduling` accepts only `READ` or `MANAGE` and evaluates the authenticated account from current Identity state. Active accounts may read calendars, working windows, holidays, closure periods, recurrence rules, constraints, and availability; `MANAGE` requires `IDENTITY_SYSTEM_ADMIN`. The response includes the authenticated `actorUserId` for trusted creator attribution. Scheduling forwards the Identity-issued user bearer token on every `/api/v1` request, including `OPTIONS`, and fails closed on missing authentication, inactive accounts, denial, or Identity unavailability. The token supplies the user identity; this endpoint is not a separate machine-identity credential. No user, role, scope, or permission values are accepted from Scheduling request bodies. Scheduling has no API Gateway route in Stage 5; production deployment must separately restrict direct service and OpenAPI network access.

`POST /api/v1/internal/authorization/room-management` accepts an optional office-building ID. A non-null ID evaluates `ROOM_MANAGE` against the authenticated active user and that building's current scope; a null ID is reserved for global room/facility catalog mutations and requires `IDENTITY_SYSTEM_ADMIN`. Room Service forwards the original bearer token for every mutation. On success Identity returns `200` with the authenticated `actorUserId`; denial is `403`, missing/invalid authentication is `401`, and dependency failures are treated as denial by Room. Room never trusts client-supplied actor, role, or scope values.

`POST /api/v1/internal/authorization/booking` is bearer-protected and evaluates Booking permissions against the current Identity account, role assignments, building scope, and time-bounded `CG_BOOKING` privilege. It supports `AUTHENTICATE`, `REQUEST_CREATE`, `REQUEST_SUBMIT`, `REQUEST_LIST`, `REQUEST_READ`, `DIRECT_CREATE`, `APPROVE`, `CANCEL`, `EXTENSION_REQUEST`, and `EXTENSION_DECIDE`. Identity derives `actorUserId` from the authenticated principal; the request schema deliberately has no actor, role, status, permission, or scope fields. Request creation is restricted to the active caller's own current department/building and requires `BOOKING_REQUEST_CREATE`; direct-booking permission does not grant request creation. Draft submission additionally requires the authenticated caller to equal the persisted requester. Own-list/read decisions are limited to the caller's current building and request capability; building-wide list/read decisions require `BOOKING_APPROVE` in that building. Reviewer reads do not authorize draft submission. VIP eligibility is evaluated against the current active `CG_BOOKING` grant and server time. For an authorized `APPROVE` decision, the response also includes the persisted requester's current active `resourceOwnerUserId` and trusted display name (`displayName`, falling back to `fullName`); this data is returned only for that action so Booking can create the initial meeting organizer record without trusting client-provided profile data. Booking forwards the caller token and fails closed when Identity is unavailable, returns an incomplete response, or denies the current policy. The endpoint is internal integration, not a public Booking API.

## Permission code catalog

Create permission rows through the Super Admin API after bootstrap. These stable codes are consumed by centralized policy checks:

- `IDENTITY_SYSTEM_ADMIN`: global identity administration and all building scopes.
- `IDENTITY_USER_READ`: read users in an assigned office-building scope.
- `IDENTITY_USER_MANAGE`: create/update/status/delete users in an assigned office-building scope.
- `IDENTITY_USER_ROLE_READ`: read role assignments in an assigned scope.
- `IDENTITY_USER_ROLE_ASSIGN`: assign/revoke Secretary roles in an assigned scope.
- `IDENTITY_PRIVILEGE_READ`: read privileges in an assigned scope.
- `IDENTITY_PRIVILEGE_GRANT`: grant/revoke privileges in an assigned scope.
- `IDENTITY_ROLE_READ`, `IDENTITY_ROLE_MANAGE`, `IDENTITY_PERMISSION_READ`, `IDENTITY_PERMISSION_MANAGE`, `IDENTITY_ROLE_PERMISSION_MANAGE`: global role and permission catalog operations; catalog mutations require `IDENTITY_SYSTEM_ADMIN`.
- `BOOKING_REQUEST_CREATE`: create booking requests for the authenticated user's own department and office.
- `BOOKING_DIRECT_CREATE`: create direct bookings in assigned scope.
- `BOOKING_APPROVE`: approve/reject requests in assigned scope, never the actor's own request.
- `BOOKING_CANCEL`: cancel bookings in assigned scope.
- `BOOKING_CANCEL_OWN`: cancel the authenticated user's own eligible booking.
- `BOOKING_EXTENSION_REQUEST`: request an extension for the authenticated user's own booking.
- `BOOKING_EXTENSION_DECIDE`: decide extension requests in assigned scope, never the actor's own request.
- `ROOM_MANAGE`: manage rooms and room configuration in assigned scope.

`CG_BOOKING` remains a separate time-bounded privilege, not a role or permission. It permits eligible booking workflows for VIP rooms only when the user's account, scope, room rules, and all scheduling constraints also allow it.

## Role policy

- **Super Admin:** assign the unscoped `IDENTITY_SYSTEM_ADMIN` permission to the controlled `SUPER_ADMIN` role. This grants system-wide administration.
- **Admin:** assign identity-management and operational permissions to `ADMIN`; each `USER_ROLE` grant must carry the authorized `scope_office_building_id`. Scoped Admins can manage only Secretaries or unassigned `PENDING` users in that building. They cannot create/modify role or permission definitions, assign `ADMIN`/`SUPER_ADMIN`, manage privileged accounts, or move users outside scopes they manage.
- **Secretary:** assign booking request, own-cancellation, and extension-request permissions to `SECRETARY`, scoped to the user's office. A Secretary may read their own identity and may not administer users, roles, permissions, or privileges.
- **CG:** grant `CG_BOOKING` as a separate privilege. Scoped Admins can grant/revoke it only for eligible users in their building scope; Super Admins can do so globally. Privilege grant attribution always comes from the authenticated actor, not the request body.

## First Super Admin bootstrap

There is deliberately no unauthenticated HTTP bootstrap endpoint. An authorized database operator performs this one-time setup before exposing the service:

1. Generate the initial password hash using the application's BCrypt encoder at work factor 12. Never place a plaintext password in SQL, shell history, source control, or logs.
2. Insert the first `APP_USER` with the approved Super Admin email, the generated hash, status `ACTIVE`, and an application-generated UUID.
3. Insert the `SUPER_ADMIN` role (`is_system=true`) and the `IDENTITY_SYSTEM_ADMIN` permission.
4. Insert the `ROLE_PERMISSION` assignment connecting that role and permission.
5. Insert the `USER_ROLE` assignment for the new user with `scope_office_building_id=NULL` and `granted_by_user_id=NULL`.
6. Verify login, a protected identity operation, and audit/security monitoring before removing operator access.

All later role, permission, and user-role changes use authenticated APIs and the centralized policy evaluator. The initial administrator identity and password hash must be provisioned through the organization's approved secret-handling process.

## Policy test coverage

`IdentityAuthorizationTests` exercises scoped Admin permissions, cross-building denial, Secretary self-read, role escalation denial, scoped `CG_BOOKING`, booking approval/direct-booking decisions, permission revocation, and Super Admin global access against H2.

## Error and audit contract

Identity domain failures use stable machine-readable `ApiError.code` values rather than status-derived codes. Current codes include `AUTHENTICATION_FAILED`, `INVALID_REFRESH_TOKEN`, `USER_NOT_FOUND`, `DUPLICATE_USER_IDENTIFIER`, `ROLE_CODE_CONFLICT`, `PERMISSION_CODE_CONFLICT`, `ROLE_PERMISSION_CONFLICT`, `USER_ROLE_CONFLICT`, `USER_PRIVILEGE_NOT_FOUND`, `PRIVILEGE_GRANT_CONFLICT`, `PRIVILEGE_ALREADY_INACTIVE`, and `INVALID_PRIVILEGE_VALIDITY_WINDOW`. Security filters use `AUTHENTICATION_REQUIRED` and `ACCESS_DENIED`; validation failures use `VALIDATION_ERROR` or `CONSTRAINT_VIOLATION`.

Every response error includes a correlation ID. A valid caller ID is restricted to 1-128 ASCII letters, digits, `.`, `_`, and `-`; invalid/missing IDs are replaced consistently across the response header, error envelope, and MDC.

Identity writes audit envelopes to its transactional outbox and publishes them to the durable `roomiq.audit.identity.v1` queue using the `audit.identity` routing key on `roomiq.events.v1`. The relay requires a positive correlated publisher confirm and no mandatory return before marking a row published. Events carry a stable event ID, which consumers must use for deduplication. The outbox relay retries transient, NACKed, timed-out, or unroutable publishes with capped exponential backoff. Event metadata contains identifiers and workflow-safe attributes only; passwords, hashes, tokens, email addresses, and profile values are excluded. Audit persistence/search remains owned by the Audit Service.
