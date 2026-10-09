# API Contract Files

This folder stores backend API contract definitions and sample request payloads for Postman import and manual verification.

## Files

- `rra-roomiq-stage1-contract.json` — shared foundation/API contract, accepted Stage 2 through Stage 5 progress, approved Stage 6 checklist status, and protected Booking request/decision API operations
- `rra-roomiq-identity-postman-collection.json` — importable Postman collection with safe sample payloads, variables, and the authenticated identity flows for users, roles, permissions, privileges, and auth endpoints
- `rra-roomiq-room-postman-collection.json` — importable Room collection with all 31 documented Room operations, bearer-token inheritance, correlation IDs, safe variables, and representative requests
- `validate-room-postman-collection.mjs` — route inventory, variable, bearer, correlation, multipart upload, and secret-marker validator for the Room collection
- `../scheduling-service/README.md` — Scheduling persistence ownership, PostgreSQL migration, runtime configuration, and verification notes

## Import into Postman

1. Open Postman.
2. Click Import.
3. Choose either JSON file in this folder. Use the Postman collection for interactive API execution and the stage contract for reference metadata.
4. Use the included variables and safe sample payloads to validate the current backend foundation.

## Automated collection validation

From the `backend/` directory, run:

```bash
node api-contracts/validate-identity-postman-collection.mjs
node api-contracts/validate-room-postman-collection.mjs
```

The Identity validator checks Postman v2.1 metadata, implemented Identity/support endpoints, required variables, bearer/correlation headers, token/resource capture scripts, reserved sample domains, and forbidden secret markers. The Room validator checks all 31 documented Room method/path pairs, required variables, bearer/correlation configuration, multipart photo upload shape, and secret markers.

## Current contract status

These files are synchronized through S6-04. The user accepted Stage 5 and Stages 3 and 4. The contract records 38 Organization and 31 Room operations, Scheduling's owned persistence and API surfaces, bounded RRULE evaluation, constraint validation, and fail-closed availability occupancy integration. Booking exposes protected request create/list/get, draft-submit, and approval/rejection decision operations; all require bearer authentication, current Identity authorization, and caller-token propagation to authoritative owner APIs. Approval is revalidated against current policy and occupancy, then atomically persists one decision, a confirmed reservation including the effective release buffer, a PRIVATE meeting, and the requester as accepted organizer participant. Rejection creates no reservation or meeting. Identity returns requester display data only on an authorized APPROVE decision. Stale, repeated, and conflicting decisions fail with stable shared error envelopes; no Booking route is configured on the API Gateway. Booking/common-web passed 49 tests, Identity/common-web passed 51 tests, and the full 11-module reactor passed 214 tests, all with zero failures/errors/skips. No ERD, migration, or requirements change was made. The user selected all-or-nothing recurring-series acceptance; whole-series validation must not leave partial bookings. See [identity-srs-erd-alignment.md](../docs/identity-srs-erd-alignment.md), [organization-srs-erd-alignment.md](../docs/organization-srs-erd-alignment.md), [scheduling-service/README.md](../scheduling-service/README.md), and [booking-service/README.md](../booking-service/README.md) for ownership and operation details.
