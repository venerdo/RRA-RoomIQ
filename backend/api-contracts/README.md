# API Contract Files

This folder stores backend API contract definitions and sample request payloads for Postman import and manual verification.

## Files

- `rra-roomiq-stage1-contract.json` — shared foundation contract, accepted Stage 2 through Stage 4 progress, and Stage 5 S5-01 persistence plus S5-02 calendar/window API/authorization/verification metadata, including the exact 38-operation Organization and 31-operation Room API inventories
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

These files are synchronized with the backend through S5-02. Stage 4 S4-01 through S4-12 is accepted. The contract records all 38 Organization and 31 Room operations, Scheduling's five owned persistence tables, and nine calendar/window API operations. S5-01 and S5-02 focused and full-reactor tests passed; no ERD change was needed. S5-03 is next. See [identity-srs-erd-alignment.md](../docs/identity-srs-erd-alignment.md), [organization-srs-erd-alignment.md](../docs/organization-srs-erd-alignment.md), and [scheduling-service/README.md](../scheduling-service/README.md) for ownership/verification mappings.
