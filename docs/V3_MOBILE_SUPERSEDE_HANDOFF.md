# Milestone 4 — Prompt 16: supersession and rescan

This completes the remaining Milestone 4 backend component within the existing
supported capture scope. It does not complete Milestone 5 deployment, Mobile
production integration or physical scanner acceptance.

## Implemented behavior

- Teacher-requested page rescan through the existing 3.0 scan-pages upload.
  Same result/session/manifest identities; fresh page/sync UUIDs and exact next
  capture number. Up to five captures, with one current page. Accepted answers and
  previously official results require a separate replacement result.
- Atomic page replacement, immutable original evidence and prior decisions retained,
  contiguous predecessor links, new revision, durable receipt/recovery and retries.
  Finalization uses accepted current evidence and checks its audit lineage.
- `POST /api/v3/mobile/results/{resultUuid}/supersede` links a verified finalized
  newer result of the same student/delivery to its finalized or audited reopened
  official predecessor. Server-assigned attempts strictly increase along the chain.
- Source revision advances; existing scores/versions remain. Both official snapshots,
  reason/request, result/session lineage, audit and successful sync commit together.
  Unique source/replacement/operation/sync identities prevent competing duplicate links.
- `GET /api/v3/mobile/results/{resultUuid}/supersession` returns the immediate durable
  replacement link. Historical retries retain that original link, even after a later
  successor. Both results are authorized on reads and retries.
- Source result/analytics becomes superseded/stale with no current official score;
  replacement readback stays authoritative. Sync acknowledges one result-level link.

See [pack 1.11.0](contracts/mobile-v3/1.11.0/README.md) for the exact Mobile sequence,
failure/retry behavior and unchanged versioned DTOs, or use its
[OpenAPI](contracts/mobile-v3/1.11.0/openapi.json).

## Reporting and history

The existing report repository already chooses the latest non-superseded attempt
per student/delivery. Its policy is preserved and persistence-tested before/after
linkage: one row, never the sum of old/new attempts. A pending newer attempt can
already be selected by that existing policy; there is no new hidden staging state.
This distinction matters when Mobile displays an older per-result score while a
replacement is being prepared. Use current reports, result states and supersession
links rather than adding together historical results.

Original photos, detections, accepted answers, written/rubric history and previous
official scores are not deleted or repurposed. A supersession does not recalculate
either score. For corrected evaluations on the same capture, use Prompt 15 instead.
The new table records the replacement reason; it does not redefine institutional
retake grading policy. Links progress to newer attempts, reject reverse/self links
and preserve historical chain receipts.

## Migration and release

Draft [V3_023](migrations/v3/V3_023_mobile_result_supersession_DRAFT.sql) adds one
table, mobile_result_supersessions, with five FKs, eight checks and four unique
constraints. The tested disposable chain totals are 79 tables / 200 FKs / 131 checks /
118 uniques. Source and replacement official JSON snapshots are stored internally;
the new public GET exposes the link, not internal storage paths or raw evidence.

Configured baseline remains V3_014: 67 tables / 163 FKs / 87 checks / 99 uniques.
New supersession writes/reads are explicitly denied by HTTP security and
`app.v3.mobile.supersede-enabled` defaults false. Existing scan/detection/verification/
attachment/reopen/correction writes and finalization/readback gates remain.
Affected sync readback requires the new draft table; schema and application release
must be coordinated. No school database migration was performed.

Capture eligibility remains the existing one-page ten-MC A4 sheet. Written backend
components can use their already supported persisted fixtures; this slice does not
validate a new written printed template. It changes no QR/layout geometry, Mobile
source, SQLite, APK or secure-session handling. SF1 stays excluded; V1/V2 are preserved.

Validation: **222 focused tests passed**, including 156 disposable MariaDB tests
and 25 new Prompt 16 cases across persistence, controllers and gates. All 12
contract packs passed; pack 1.11.0 validates 16 unchanged schemas, 11 fixtures and
11 actual runtime samples. The disposable server was stopped and port 33317
verified closed.

Validation evidence: [V3_MOBILE_SUPERSEDE_VALIDATION.json](V3_MOBILE_SUPERSEDE_VALIDATION.json).
Next: Milestone 5 release preparation and coordinated Backend/Mobile acceptance.
