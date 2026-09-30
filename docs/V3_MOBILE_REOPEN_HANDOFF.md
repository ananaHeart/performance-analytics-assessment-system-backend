# Mobile V3 — Prompt 14 audited result reopen

Completed 2026-09-12. **Reopen backend component complete in isolated tests; release
gated. Milestone 4 remains in progress.** Correction uploads and re-finalization are
the next session, not part of this implementation.

## Implemented behavior

`POST /api/v3/mobile/results/{resultUuid}/reopen` implements the previously proposed
3.0 `ReopenRequest` / `LifecycleAck` contract. [Pack 1.9.0](contracts/mobile-v3/1.9.0/README.md)
contains OpenAPI, six synthetic request/response fixtures and an executable validator.

The teacher sends distinct sync/operation UUIDs, expected result revision, expected
score version, a nonblank reasonCode and nullable comment. A reason is required;
the server supplies actor identity and acknowledgement time. Body limit is 32 KiB;
reasonCode is at most 50 characters, comment at most 4000. Strict JSON rejects
unknown/missing fields, duplicate keys, scalar coercion, fractional integer versions
and trailing documents. All included named wire schemas remain unchanged from 1.0.0;
runtime validation additionally rejects whitespace-only reasons.

New reopen intent requires a currently owned, finalized Mobile result with a valid
official finalization receipt, matching revision/score version, active school teacher
and correct learner/class/school ownership. The assessment must be active/completed;
an archived test assignment rejects new reopen intent. These lifecycle restrictions
follow the existing scorer. No original evidence or rubric is recomputed to reopen
a historical official score; later rubric edits do not prevent requesting review.

The transaction locks the current teacher and owned result, validates the official
receipt through the existing finalization bridge, then commits all of the following:

- Result status becomes `pending_verification`; mobile revision advances exactly once.
- Score version remains the previous official version until future re-finalization.
- The previous official JSON is copied into the reopen history; the original
  finalization receipt, stored totals and timestamps are preserved.
- Existing answers, rubric rows, teacher audits, selected page and evidence are preserved.
- An append-only reopen record stores operation, actor, reason/comment, versions,
  request hash, request JSON, official snapshot, response and server time.
- A linked `V3_MOBILE_RESULT_REOPENED` audit event and successful result-level
  sync/item acknowledgement commit in the same transaction.

This is a result-level state transition. It does not rewrite answer evaluations,
increment score version, select a new scan, produce corrected totals or supersede
another result. The prior score is retained internally for history; no new public
score-history endpoint is introduced in Prompt 14.

## Retry and conflict handling

Retry the identical request after timeout or `REOPEN_PERSISTENCE_UNAVAILABLE` (503).
An uncertain commit can already have succeeded. Request identity includes the actor,
school, result UUID and every typed request field. Reusing an operation with changed
content returns `REOPEN_IDENTITY_CONFLICT`; reusing a sync from another operation
returns `SYNC_IDENTITY_CONFLICT`. Concurrent identical retries yield one created
acknowledgement and replay; concurrent different operations can have only one winner.

Successful replay rechecks current teacher ownership and returns the stored
acknowledgement with `disposition=replayed`, preserving its original revision, score
version and time. This acknowledgement is historical, even after later lifecycle
changes. A reassigned result is no longer accessible to the former teacher. The new
teacher cannot replay another actor's operation under their own identity.

Stale expected versions return `REVISION_CONFLICT`. Non-finalized/superseded results
return `RESULT_NOT_FINALIZED`; missing official receipts return
`OFFICIAL_SCORE_UNAVAILABLE`. Invalid official or reopen receipts return
`FINALIZATION_STATE_CONFLICT` or `REOPEN_STATE_CONFLICT`. Failed transactions leave
no partial status, sync or audit commit. The revision ceiling is enforced.

## Readback and Mobile reconciliation

| Existing GET | Reopened behavior |
|---|---|
| `/api/v3/mobile/results/{resultUuid}` | `pending_verification`, advanced revision, unchanged scoreVersion, `officialScore=null`, pending reason `RESULT_REOPENED` |
| `/api/v3/mobile/results/{resultUuid}/analytics` | `status=stale`, `reasonCode=RESULT_REOPENED`, `metrics=null` |
| `/api/v3/mobile/syncs/{syncUuid}` | One successful result acknowledgement with empty `pageOutcomes`; reopening is not a page upload |

Mobile must reconcile current result/readback after any acknowledgement and invalidate
its displayed current score when reopened. It must not display cached historical
metrics as the current official score. Existing V3 report SQL already only exposes
score metrics when `result_status='finalized'`; no report formula was changed.

The existing first-finalization route remains blocked after reopen with
`FINALIZATION_STATE_CONFLICT`. A pending result is not permission to overwrite old
accepted answers through first-verification uploads. Correction upload and audited
re-finalization require the next Milestone 4 implementation. Upload-stage retry
receipts remain historical and do not undo the reopened state.

## Schema and release

New **draft V3_021** adds `mobile_result_reopens` with unique operation, sync,
result/score-version and audit identities, four FKs, and JSON/revision/reason checks.
The draft was applied only to synthetic disposable MariaDB on port 33317.

- Configured baseline remains **V3_014: 67 tables / 163 FKs / 87 checks / 99 uniques**.
- Isolated draft chain through **V3_021: 76 tables / 186 FKs / 114 checks / 109 uniques**.
- New reopen and affected result/sync readback require the reviewed draft chain
  through V3_021. Earlier pack snapshots retain their historical prerequisites.
- HTTP reopen is explicitly denied in V3 security; `app.v3.mobile.reopen-enabled`
  defaults false. First reopen also uses the existing finalization bridge flag.
- Existing upload gates, readback/reference defaults and Mobile capability remain
  closed. No configured production schema baseline was changed.

No school database, Mobile code/SQLite/APK, printed template, QR payload, SF1 or V1/V2
code was changed in this session. No physical phone/scanner or production deployment
acceptance is claimed. The disposable server was shut down after validation.

## Validation

[Validation evidence](V3_MOBILE_REOPEN_VALIDATION.json) records **156 passing focused
tests, including 23 new Prompt 14 cases** (15 persistence, 5 controller, 2 service
validation/gate and 1 HTTP security gate). It also records source hashes, contract
checks and isolated schema counts. Coverage includes:

- Objective and mixed written reopen; exact preservation of official/answer/rubric data.
- Required reason/version validation and strict HTTP parsing; release and ownership gates.
- Same/different concurrent operations, stale versions, reused IDs, archived lifecycle,
  current owner reassignment, superseded results and revision exhaustion.
- Pre-commit rollback, lost acknowledgement, malformed receipts and database constraints.
- Stale result/analytics and successful historical sync readback; first-finalization
  remains blocked until correction/re-finalization is implemented.

All ten versioned contract pack validators pass. Pack 1.9.0 checks 14 unchanged named
schemas and six actual synthetic runtime samples. Synthetic written metadata tests
remain backend component validation, not physical printed written capture validation.
The final run used a fresh synthetic schema after a temporary-server interruption
and storage-engine error in the reused test database. The managed validation script
`tmp/run-reopen-validation.ps1` keeps the server in console mode, prepares a fresh
schema, runs the focused suite and shuts down the isolated instance afterward.

## Next bounded session

**Milestone 4 — Prompt 15: audited correction upload and re-finalization.** Use the
reopen history as the starting point; preserve accepted history while saving changed
evaluations and producing a new official score version. Superseding/rescan workflow
and production acceptance remain separately tracked pending work.
