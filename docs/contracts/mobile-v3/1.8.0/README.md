# Mobile V3 1.8.0 — written and mixed finalization

Prompt 13 extends the existing bodyless
`POST /api/v3/scoring/results/{testResultId}/finalize` to validate accepted written
evaluations before computing official scores. No new endpoint, request DTO or
migration is introduced. Finalization/readback wire schemas remain unchanged;
written verification requests still use contractVersion 3.1 from pack 1.7.0.

Before first finalization, the backend checks all required answers, accepted teacher
audits and receipts, current public scoring-reference binding, exact rubric rows,
evidence links, attachment lineage and retained bytes. Changed references block
finalization with `WRITTEN_REFERENCE_STALE`; conflicting saved facts block it with
`WRITTEN_AUDIT_MISMATCH`. Do not overwrite accepted evaluations to resolve these
conflicts: audited correction/reopen is the next milestone.

The backend preserves teacher-awarded written points and computes official totals
using the existing scorer. Successful retry returns the saved official score with
`scoreChanged=false`; subsequent rubric edits do not silently rescore that result.
Readback exposes official total/max/percentage and sync mappings. Advanced analytics
remain explicitly unavailable. A sync success is an acknowledgement of its upload
stage, not an implicit finalization.

[OpenAPI](openapi.json) contains the existing finalization and readback routes.
Four fixtures were captured from synthetic isolated MariaDB tests, including a
mixed result of 23.50/28 (83.93%). Their IDs and timestamps are illustrative.
Run `node docs/contracts/mobile-v3/1.8.0/validate.cjs --runtime-samples` after the
opt-in MariaDB suite to check the four fresh runtime responses too.

Required draft chain ends at V3_020. Configured production baseline remains V3_014;
HTTP upload gates and finalization/readback flags remain closed. Written physical
capture, Mobile production wiring and reviewed migration deployment are pending.
See the [backend handoff](../../../V3_MOBILE_WRITTEN_FINALIZATION_HANDOFF.md).
