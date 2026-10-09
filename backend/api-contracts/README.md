# API Contract Files

This folder stores backend API contract definitions and sample request payloads for Postman import and manual verification.

## Files

- `rra-roomiq-stage1-contract.json` — shared foundation contract, accepted Stage 2 through Stage 5 progress, and approved Stage 6 checklist status; S6-01 persistence is complete, S6-02 is partial pending workflow-level owner-service checks, and Booking business endpoint contracts are not yet implemented or published
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

These files are synchronized with the backend through the S6-02 authorization foundation; Stage 5 verification is complete and the user accepted Stage 5. Stages 3 and 4 are also accepted. The contract records all 38 Organization and 31 Room operations, Scheduling's five owned persistence tables, nine calendar/window operations, ten holiday/closure operations, six recurrence-rule operations, the read-only scheduling-constraint validation operation, and the availability search operation. Recurrence endpoints validate date-based RRULEs and IANA timezones, require UNTIL and/or COUNT, cap the inclusive horizon at one year and occurrence output at 365, and do not create reservations. S5-05 validates each candidate occurrence against local working windows, applicable blocking holidays, and overlapping blocking closure periods. S5-06 combines Scheduling's rules with Room owner APIs and a protected versioned Booking occupancy snapshot contract; unavailable, incomplete, malformed, or stale occupancy fails closed. The Booking provider is not implemented yet, so candidate-producing searches return an availability error rather than claim rooms free. Continuous occurrence-level intervals are candidates only, not confirmation or a whole-series recurrence guarantee. All Scheduling business routes require a forwarded Identity bearer token and server-side Identity authorization; the actor and permissions come from Identity, not request-supplied IDs or scope. Scheduling routes are not routed through the API Gateway in Stage 5; authorized direct access/service integrations require a private trusted network, which must be enforced by deployment configuration. OpenAPI documentation describes contracts but does not publish routes. Follow-up S5-06/S5-07 verification passed 50 Scheduling tests, 44 Identity/common-web tests, and 159 full-reactor tests, with zero failures, errors, or skips. S6-01's PostgreSQL-focused suite passed 6 tests, Identity passed 38, common-web passed 6, and its full 11-module reactor passed 164, all with zero failures, errors, or skips. The latest S6-02 verification passed 16 Booking module tests (including 9 authorization tests), 42 Identity tests, 6 common-web tests, and all 178 tests across the 11-module reactor, with zero failures, errors, or skips; the full reactor passed after the final Booking runtime-configuration change. No ERD or requirements change was needed. S6-02 remains partial: Identity authorization, caller-token propagation, protected Booking route guard, and fail-closed tests are implemented, but no Booking business workflows exist to exercise required Organization/Room/Scheduling owner APIs. No Booking business routes or S6-13 occupancy provider are implemented or exposed. The protected Identity Booking decision operation is documented in the contract metadata. The user selected all-or-nothing recurring-series acceptance, with whole-series validation and no partial persistence on conflict. See [identity-srs-erd-alignment.md](../docs/identity-srs-erd-alignment.md), [organization-srs-erd-alignment.md](../docs/organization-srs-erd-alignment.md), and [scheduling-service/README.md](../scheduling-service/README.md) for ownership/verification mappings.
