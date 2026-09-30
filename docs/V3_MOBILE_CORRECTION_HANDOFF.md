# Milestone 4 — Prompt 15: correction upload and re-finalization

Backend component for correcting an already reopened result. Production release
remains gated. Superseding/rescan remains a separate unfinished part of the
existing Milestone 4; production integration and physical acceptance are Milestone 5.

## Mobile flow

1. Read the owned finalized result and reopen it using pack 1.9.0, recording its
   operation UUID. Readback remains pending/stale until correction succeeds.
2. Download the current evaluation reference. Have the teacher review every
   current answer, including unchanged answers, against the retained capture.
3. Send the explicit 3.2 complete-result request to
   `POST /api/v3/mobile/results/{resultUuid}/corrections`. Include expected revision,
   previous score version, reference hash, test version, reopen operation, required
   reason, fresh operation/sync/verification UUIDs and existing answer identities.
4. Backend validates all decisions and finalizes atomically. A successful new
   request returns the next official score version; a failed result remains reopened
   with its prior official history intact. No additional finalize request is needed.
5. After timeout or lost acknowledgement, retry identical content/identities. A
   committed replay returns its original score with `scoreChanged=false`.
6. GET current result, analytics and sync receipt. Apply current revision/version
   monotonically. A historical replay must not replace a later local score.

See [pack 1.10.0](contracts/mobile-v3/1.10.0/README.md) and
[OpenAPI](contracts/mobile-v3/1.10.0/openapi.json) for exact DTOs and bounds.

## Persistence and scoring

Draft [V3_022](migrations/v3/V3_022_mobile_result_corrections_DRAFT.sql) adds
`mobile_result_corrections` and `mobile_answer_corrections`. One correction consumes
one reopen operation. Unique operation, sync, result/version and verification
identities prevent duplicate commits. JSON, version and audit-link checks protect
the receipt/history structure. Application code appends history; this does not
claim database administrators cannot change rows.

The transaction locks the live teacher/assignment, result, original capture and
current evaluation reference. It validates original detection identity and active
options for MC/TF; the server determines correctness/points from current keys.
Written decisions reuse manual/rubric validation, including all criteria and
per-criterion bounds. The selected evidence must be retained, hash-valid and
related to the same page/region/answer. Old attached evidence stays linked;
submitted evidence selects the current primary pointer without deleting history.

Before/after answer snapshots retain prior values and rubric/evidence references.
New written verification and rubric rows use the new score version; old rubric
rows are retained. The previous official response remains in the reopen history.
Current official receipt, result header and answers advance together. Even equal
scores or a comment-only complete review produce a new version.

The existing scorer handles totals, performance rules and official response. Its
internal correction entry requires a proof constructed after full validation and
bound to that same active transaction. The ordinary first-finalization route
continues to reject a merely reopened result. After successful correction it can
replay the new official receipt.

No public history GET is added in this slice. Historical snapshots are backend
audit data; public result/analytics GETs expose current authoritative state.
Analytics here remain official total/max/percentage. Existing unavailable advanced
analytics modules are not enabled by this change.

## Release and acceptance boundaries

- Configured baseline stays V3_014: 67 tables, 163 FKs, 87 checks, 99 uniques.
- Disposable draft chain through V3_022: 78 tables, 195 FKs, 123 checks, 114 uniques.
- Correction HTTP route is explicitly denied; `app.v3.mobile.correction-enabled`
  defaults to false. Existing finalization/readback and upload gates remain.
- New correction and affected sync readback require draft V3_022. Do not enable
  them against V3_014 or deploy schema/config changes independently.
- No school database migration, production URL deployment, Mobile source/schema,
  secure session storage, APK, printed template or physical scanner change here.
- Same selected capture only. Reassignment to another teacher does not transfer
  ownership of the original capture through this route. New scans/superseding
  need the remaining Milestone 4 contract and implementation.
- Reference hash behavior remains pack 1.5.0 behavior; it is not a new hash of
  objective answer keys. Current keys/reference rows are locked during scoring.
- Written fixtures use synthetic region/question metadata and do not validate
  printed written layouts. Dynamic QR/physical acceptance remains pending.
- SF1 remains excluded. V1/V2 are preserved.

Validation evidence is recorded in
[V3_MOBILE_CORRECTION_VALIDATION.json](V3_MOBILE_CORRECTION_VALIDATION.json).
