# Mobile V3 1.9.0 — audited result reopen

Prompt 14 implements `POST /api/v3/mobile/results/{resultUuid}/reopen` using the
existing proposed `ReopenRequest` and `LifecycleAck` wire shapes from pack 1.0.0.
Request contractVersion remains 3.0. This does not change written verification 3.1.

Mobile sends a fresh `syncUuid` and `operationUuid`, the latest `expectedRevision`
and `expectedScoreVersion`, a nonblank `reasonCode` (up to 50 characters) and `comment`
(required field, nullable, up to 4000 characters). Reason codes are caller-supplied
labels; no reason enumeration is introduced. Maximum request body is 32 KiB.

On first success, the result becomes `pending_verification` and revision advances
once. Score version stays unchanged until future correction/re-finalization. The
backend retains the previous official snapshot, existing finalization receipt,
answers, rubric scores and evidence, and stores the actor/reason/time in an audit.

Retry the identical request after a lost response or persistence 503. Reusing an
operation with different content conflicts. A successful retry is a historical
acknowledgement with `disposition=replayed`, even if the result later changes.
Always retrieve current result state after acknowledgement. Reopen sync readback
contains one result success and an empty `pageOutcomes` array because reopening is
a result operation, not a page upload.

Reopened result readback has `officialScore=null`, `resultStatus=pending_verification`
and pending reason `RESULT_REOPENED`. Analytics are `stale` with no metrics. Mobile
must invalidate its displayed current score using this state; retained historical
scores must not be presented as the current official score.

Correction uploads, rescans, superseding and re-finalization are **not enabled by
this pack**. Calling first-finalization again while reopened remains blocked. They
require subsequent Milestone 4 work; no overwrite of accepted evaluations is allowed.

[OpenAPI](openapi.json) and six synthetic runtime fixtures are provided. Run
`node docs/contracts/mobile-v3/1.9.0/validate.cjs --runtime-samples` after the opt-in
MariaDB suite. Included named schemas are unchanged from 1.0.0; nonblank reason
validation additionally rejects whitespace-only strings at runtime.

Draft V3_021 stores reopen history and receipts. Reopen and affected readback require
reviewed deployment of the draft chain through V3_021. Configured baseline remains
V3_014; the HTTP gate and `app.v3.mobile.reopen-enabled` flag remain closed. First
reopen also uses the existing finalization bridge flag. No production Mobile or
school database migration was performed. See the [handoff](../../../V3_MOBILE_REOPEN_HANDOFF.md).
