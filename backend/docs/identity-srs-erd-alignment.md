# Identity SRS and ERD Alignment

## Scope

This document records how the implemented Identity Service maps to the authoritative:

- `Requirements/RRA_RoomIQ_Backend_Requirements_Specification.docx`
- `Requirements/RRA_RoomIQ_Final_Requirements_Specification.docx`
- `Requirements/RRA_RoomIQ_ERD_v2.mmd`

The DOCX specifications and Mermaid ERD remain the authoritative requirements artifacts. This note records implementation traceability and does not replace them.

## Alignment Status

S2-17 and S2-18 are complete for the implemented identity scope. No change to the authoritative SRS or ERD was required.

## Identity Ownership Mapping

| Requirement/ERD concept | Implementation | Status |
|---|---|---|
| `APP_USER` | `AppUser`, `UserManagementService`, `UserController`, `V1__identity_schema.sql` | Synchronized |
| `ROLE` | `Role`, role management APIs, identity migration | Synchronized |
| `PERMISSION` | `Permission`, permission management APIs, identity migration | Synchronized |
| `ROLE_PERMISSION` | `RolePermission`, grant/list/revoke APIs, unique pair constraint | Synchronized |
| `USER_ROLE` | `UserRole`, scoped assignment APIs, scope authorization | Synchronized |
| `USER_PRIVILEGE` | `UserPrivilege`, `CG_BOOKING`, validity windows, grant/revoke APIs | Synchronized |
| `USER_SESSION` | `UserSession`, hashed refresh tokens, rotation/revocation, JWT session validation | Synchronized |
| Authentication and authorization | Spring Security, JWT, BCrypt, database-backed permission/scope evaluation | Synchronized |
| Identity audit behavior | Transactional outbox plus versioned RabbitMQ event envelope | Synchronized with boundary note below |

## ERD Boundary Note

The ERD defines `AUDIT_EVENT` as an Audit Service-owned entity and requires the Audit Service to own immutable audit persistence. Identity therefore does not add an `AUDIT_EVENT` table or cross-service foreign keys.

Identity owns the technical `identity_audit_outbox` table introduced by `V2__identity_audit_outbox.sql`. The outbox is infrastructure for atomically publishing identity events to the Audit Service. It is not a replacement for `AUDIT_EVENT`, is not part of the identity business-domain ERD, and does not change the approved Mermaid ERD.

The outbox event envelope contains `schemaVersion`, event ID/type, timestamp, source service, actor/resource/scope identifiers where available, outcome, correlation ID, and safe metadata. Passwords, hashes, tokens, email addresses, and profile values are excluded. The Audit Service remains responsible for durable audit storage, search, retention, and administrative APIs in its later implementation stage.

## API and Documentation Traceability

- Identity REST APIs are versioned under `/api/v1` and documented through Springdoc and the Postman collection.
- Stable error codes and correlation-ID behavior are documented in `identity-service/Documentation.md`, `identity-service/AUTHORIZATION.md`, and the shared `common-web` README.
- S2-14 audit/error behavior, S2-15 automated tests, S2-16 Postman contract/sample data, and S2-17 alignment status are recorded in `backend/Documentation.md` and the stage contract JSON.
- The importable collection is validated with `node api-contracts/validate-identity-postman-collection.mjs`.

## Configuration and Verification
S4-09 extends Identity's existing room-management authorization beyond the S4-07 photo use case. Room writes now receive an Identity-derived actor UUID, with building-scoped `ROOM_MANAGE` and global system-admin-only unscoped catalog decisions; no Identity table or ERD change was required.

Identity configuration remains externalized. The implementation uses environment variables for database, JWT, and RabbitMQ settings; no production credentials are documented or committed.

The final clean verification passed:

- Identity/common-web: 12 Surefire suites, 37 tests, zero failures/errors.
- Full backend reactor: 20 Surefire suites, 45 tests, zero failures/errors.

The S2-18 verification gate confirmed the identity suite and full Stage 1 reactor remain green together. Stage 3 S3-01 through S3-11 verification is complete and accepted. Identity retains Organization references as UUID scalars only; it has no Organization JPA entities, tables, or foreign keys. The dedicated Identity/common-web suite passed 35 tests. Identity now exposes a protected internal `ROOM_MANAGE` decision for Room Service photo mutations; existing building-scope policy is reused, with no Identity persistence or ERD change. See [organization-srs-erd-alignment.md](organization-srs-erd-alignment.md) for the Organization verification matrix. Room Service S4-01 through S4-07 are complete and verified, including the Cloudinary photo boundary and authenticated building-scoped photo operations.

## S3-08 Organization references

`AppUser.departmentId`, `AppUser.officeBuildingId`, and `UserRole.scopeOfficeBuildingId` are UUID scalar fields. Identity's Flyway schema creates no Organization tables and declares no foreign keys to Organization-owned data. Identity does not import or map Organization JPA entities. `IdentityRepositoryTests.organizationReferencesRemainIdentifierOnlyAcrossServiceBoundary` proves arbitrary/unbacked IDs persist, validates the UUID metamodel types, checks imported foreign-key columns, and confirms Organization tables/entities are absent from the Identity persistence context. Organization hierarchy foreign keys remain owned by Organization Service. No ERD change was needed.

## Change Governance
Room Service S4-09/S4-10 are complete without Identity persistence changes. Identity returns the authenticated actor UUID from its room-management authorization decision and enforces building-scoped `ROOM_MANAGE` or global system-admin for unscoped catalog writes. The Room API OpenAPI contract and Postman collection cover 31 operations. Stage 4 S4-01 through S4-12 is accepted. S5-02 and S5-03 use the protected Scheduling authorization decision for reads, management, and Holiday creator attribution; S5-04 applies the same policy to recurrence rules and derives their creator from the authenticated actor. S5-05 uses that policy's `READ` action for its read-only validation POST, and S5-06 uses the same read policy for availability search; active users may read/validate/search while management routes remain admin-only. Scheduling forwards an Identity-issued user bearer token; Identity resolves actor and permissions and does not trust caller-provided identity or scope. All Scheduling `/api/v1/**` requests, including `OPTIONS`, are protected; the service has no Gateway route in Stage 5. Identity authorization/OpenAPI and full backend Java tests pass. No Identity persistence or ERD change was required; Stage 5 was subsequently accepted by the user.

Any future change affecting identity ownership, authorization, scheduling policy, public data exposure, cross-service persistence, or the approved ERD must be documented and reviewed before implementation. The ERD must not be edited silently.
