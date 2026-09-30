# Mobile V3 1.7.0 — written teacher verification

Prompt 12 introduces explicit **request contractVersion 3.1** on
`POST /api/v3/mobile/verification-batches`. Contract 3.0 remains objective-only.
This pack does not globally bump login/download/attachment/readback wire versions.
The existing verification response shape is reused.

Each written item requires `testVersionNumber` and `evaluationReferenceHash` from
the evaluation-reference GET, in addition to the latest result `expectedRevision`.
The backend locks current scoring bounds and rejects stale references per result.
Reference snapshots and teacher evaluation JSON are retained with the audit.

Manual and rubric evaluations use distinct schemas. Rubrics apply to assigned
essay questions; direct manual essay scores require the no-rubric manual setup.
Each answer accepts at most 20 evidence attachments; each rubric needs all of its
criteria, including criteria flagged optional in the current reference. Maximum
body is 2 MiB. No objective answers or correction/reopen requests in a 3.1 batch.

[OpenAPI](openapi.json) and four illustrative fixtures are supplied. Run
`node docs/contracts/mobile-v3/1.7.0/validate.cjs --runtime-samples` after the opt-in
MariaDB suite to validate four actual synthetic runtime samples too.

Read the [backend handoff](../../../V3_MOBILE_WRITTEN_SCORING_HANDOFF.md) for SQL,
retry/reference and release limitations. HTTP remains denied; production Mobile
wiring, reviewed V3_020 deployment and written scanner/finalization acceptance are pending.
