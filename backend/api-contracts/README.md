# API Contract Files

This folder stores backend API contract definitions and sample request payloads for Postman import and manual verification.

## Files

- `rra-roomiq-stage1-contract.json` — shared foundation/API contract, accepted Stage 2 through Stage 5 progress, approved Stage 6 checklist status, and protected Booking request API operations
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

These files are synchronized through S6-03. The user accepted Stage 5 and Stages 3 and 4. The contract records 38 Organization and 31 Room operations, Scheduling's owned persistence and API surfaces, bounded RRULE evaluation, constraint validation, and fail-closed availability occupancy integration. The Booking contract now documents protected request create/list/get and draft-submit operations. Create persists a validated `DRAFT`; only its requester may submit, after Identity, Organization, Room, and Scheduling checks are repeated using the caller token, and the state then becomes `PENDING_APPROVAL`. Caller-controlled actor/type/status fields are not authoritative, auto-confirm is not performed, and Booking is not routed through the API Gateway. Approval/rejection, cancellation, reservations, meetings, extensions, share links, and occupancy remain later Stage 6 work. The S6-focused Booking suite passed 30 tests; Identity passed 45 tests and common-web 6. Full-reactor verification is pending. No ERD or requirements change was made. The user selected all-or-nothing recurring-series acceptance; whole-series validation must not leave partial bookings. See [identity-srs-erd-alignment.md](../docs/identity-srs-erd-alignment.md), [organization-srs-erd-alignment.md](../docs/organization-srs-erd-alignment.md), [scheduling-service/README.md](../scheduling-service/README.md), and [booking-service/README.md](../booking-service/README.md) for ownership and operation details.
