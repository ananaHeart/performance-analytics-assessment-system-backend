# V3 Mobile Milestone 1 handoff

Date: 2026-09-12. Scope: documentation and fixtures only.

Start with [contract pack 1.0.0](contracts/mobile-v3/1.0.0/README.md).

This pack completes the Milestone 1 design deliverables: versioned examples,
existing versus proposed route inventory, session-expiry/re-login policy,
download alignment, UUID/central-ID mapping, and a staged end-to-end contract.
It does not implement or enable upload, verification, correction, reconciliation,
analytics, secure Mobile token storage, or a production SQLite migration.

Decisions for the first release:

- Use existing expiring bearer sessions and re-login; no refresh endpoint.
- Secure token persistence is a future Mobile implementation requirement.
- Download includes `classAssignmentSchedules` and independent `classStatus`.
- Preserve current wire `contractVersion: "3.0"`; pack version `1.0.0` is not
  the wire version, SQLite version, migration level, or scanner version.
- Preserve original scan-page DTO fields and legacy fixtures unchanged.
- New route names and payloads in the pack are implementation targets, not
  implemented endpoints. Their release gates are explicit in the route index.
- The backend resolves UUIDs within authenticated ownership; local SQLite IDs,
  local image paths, and locally calculated totals are never authoritative.
- Reuse the existing numeric-ID finalization endpoint after UUID reconciliation.
- Objective uncertainty requires rescan/rejection, not teacher answer replacement.
- Written-response evaluation and rubric criteria need separate write/read slices.

Known blockers are recorded in the pack, including missing per-operation durable
receipts, missing offline rubric criteria, crop-only response constraints, and
correction/version history. No migration is created or applied by this handoff.

Next implementation scope: original-image storage, then the existing planned
`POST /api/v3/mobile/scan-pages` route. Keep new capabilities unavailable until
persistence, ownership, retry, and failure-recovery checks pass. SF1 is excluded.
