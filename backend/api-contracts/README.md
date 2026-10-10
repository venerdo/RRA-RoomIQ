# API Contract Files

This folder stores backend API contract definitions and sample request payloads for Postman import and manual verification.

## Files

- `rra-roomiq-stage1-contract.json` — shared foundation/API contract, accepted Stage 2 through Stage 5 progress, approved Stage 6 checklist status, and protected Booking request/decision/direct-booking API operations
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

These files are synchronized through S6-07. The user accepted Stage 5 and Stages 3 and 4 and approved the Stage 6 checklist. The contract records 38 Organization and 31 Room operations, Scheduling's owned persistence and API surfaces, bounded RRULE evaluation, constraint validation, and fail-closed availability occupancy integration. Booking exposes protected request create/list/get, draft-submit, approval/rejection decision, direct-booking, check-in, and completion operations; all require bearer authentication and current Identity authorization. Owner-service calls propagate the caller token. Approval is revalidated against current policy and occupancy, then atomically persists one decision, a confirmed reservation including the effective release buffer, a PRIVATE meeting, and the requester as accepted organizer participant. Rejection creates no reservation or meeting. Direct booking applies the active Room rule to Admin, Super Admin, and eligible Secretary initiators alike: required approval creates only a `PENDING_APPROVAL` request, and the requester cannot approve it. When approval is not required, reservation occurrences and meeting creation are atomic.

Recurring Booking acceptance uses the stored Scheduling RRULE and timezone; Scheduling's existing one-year/365-occurrence bound remains authoritative. Booking verifies returned calendar/rule/timezone metadata, checks each occurrence against Scheduling, effective Room policy, Room's existing protected maintenance API, and Booking occupancy, then atomically stores the existing series reservation, one occurrence row per date, and the meeting data. A request-level room rule is not a substitute for per-date checks: if any effective rule requires approval, the full series is held for the existing approval workflow, and the maximum effective release buffer is applied to all occurrence intervals. Scheduling and maintenance conflicts return occurrence-scoped stable errors before writes; Booking occupancy conflicts use the shared `ROOM_OCCUPANCY_CONFLICT` error with affected dates in its message and roll back the transaction. PostgreSQL exclusion conflicts/deadlocks normalize to the same response. No new Booking route or ERD change was made, and no route was added to the API Gateway.

Identity returns requester display data only on an authorized APPROVE decision and validates lifecycle actors against current Identity permissions and organizational scope. Stale, repeated, and conflicting decisions fail with stable shared error envelopes. S6-07 adds the protected routes `POST /api/v1/reservations/{reservationId}/check-in` and `/complete`; check-in is `CONFIRMED → IN_PROGRESS` and completion is `IN_PROGRESS → COMPLETED`. Actor identity and `checkedInAt`/`completedAt` are server-derived. The user approved nullable `reservation.completed_at TIMESTAMP WITH TIME ZONE`, required iff the status is `COMPLETED`; Flyway V3 and the ERD document the constraint. The existing half-open buffered reservation/occurrence intervals and PostgreSQL GiST exclusion constraints remain the occupancy barriers. No lifecycle route is configured at the Gateway, and there is no automatic overdue/missed-check-in transition. Verification passed: focused Booking lifecycle selection 37/37, Identity 48/48, common-web 6/6, and full 11-project reactor 244/244, all with zero failures/errors/skips. The S6-07 lifecycle tests cover state conflicts, denied authorization, concurrency, terminal-state handling, adjacent intervals, and database overlap barriers. The user selected all-or-nothing recurring-series acceptance; whole-series validation must not leave partial bookings. See [identity-srs-erd-alignment.md](../docs/identity-srs-erd-alignment.md), [organization-srs-erd-alignment.md](../docs/organization-srs-erd-alignment.md), [scheduling-service/README.md](../scheduling-service/README.md), and [booking-service/README.md](../booking-service/README.md) for ownership and operation details.
