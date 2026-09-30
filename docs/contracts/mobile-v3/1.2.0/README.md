# Mobile V3 teacher verification supplement 1.2.0

[OpenAPI 3.1](openapi.json) describes the implemented, objective-only
`POST /api/v3/mobile/verification-batches` handler. Wire `contractVersion` remains
`3.0`. This supplements the frozen 1.0.0 proposal and 1.1.0 detection supplement;
it does not replace their other routes. Manual/rubric evaluation remains pending.

**Production security denies this route.** The draft V3_015/V3_016/V3_017 migrations
have not been deployed to the school database. This document does not authorize
Mobile production wiring, SQLite migration or scanner acceptance.

## Request and response rules

- Send one immutable operation/sync UUID pair for one assignment and 1..50 unique
  results. Each item contains its current `expectedRevision`, 0..200 page decisions
  and 0..200 answers, with at least one decision or answer. JSON is bounded to 2 MiB.
- All fields are required, including explicit nullable comments/reasons. Client
  timestamps must be UTC text ending in `Z`. Unknown fields, duplicate JSON keys,
  numeric strings and fractional revisions are rejected. Duplicate result, answer,
  verification or per-item page/question identities are rejected before registration.
- Pages support `accepted`, `rejected`, or `rescan_requested`; non-acceptance requires
  a nonblank reason code. Comments support up to 4000 characters. This is initial
  verification: a decided page cannot be changed through a fresh initial decision.
  Correction/reopen and actual replacement capture upload remain later contracts.
- Answers accept only `evaluation: {kind: "objective", detectionUuid}`. Backend
  resolves the stored MC/TF detection through the page/region/question. Detected
  marks yield their stored option; blanks yield no option. Uncertain/multiple marks
  require a rescan or rejection, never a teacher-selected replacement here.
- Accept the page in the same item or a prior successful operation before accepting
  its answers. A bad answer rolls back that item's page decisions as well.
- The verifier and authoritative audit time come from the backend. Client time is
  retained separately as untrusted evidence. Do not send correctness, points,
  verifier IDs, selected-option overrides or official finalization timestamps.
- HTTP 200 and envelope `success=true` mean processed, not all items accepted.
  Inspect `data.syncStatus`, then each item's `status`, `disposition` and `error`.
  A result is its own transaction; one failed result does not roll back another.
- Successful items return student-answer ID mappings when answers are present;
  page-only items return scan-verification mappings. This stays within the existing
  200-mapping response limit. Mixed items do not additionally return page-audit IDs;
  their UUIDs and records remain durably stored. Correlate by UUID, not list position.
- Results/items and nested decisions are canonicalized for retry identity. Same
  content in a different array order replays. Changed content, revision or item set
  under the same operation UUID conflicts. A new operation needs a new sync UUID.
- Exact retries replay committed successful items without advancing revision or
  reapplying decisions, even after later finalization. Permanent failures replay
  their recorded failure. Transient failures (`error.retryable=true`) are retried.
  If a dependency arriving changes the result revision, reconcile and submit a
  new operation; retry permission does not bypass `expectedRevision`.
- Each successful item advances `mobile_revision` once and leaves the result
  `pending_verification`. Individual accepted answers have the finalized evaluation
  fields required by the existing scorer. This does not compute points or finalize
  the overall result. Broader scoring/correction revision integration is still pending.

The whole batch is authorized before registration. Subsequent item transactions
recheck the active teacher, assignment, result and page ownership. Lost registration
acknowledgements can leave a resumable pending batch; a top-level 500 may occur after
some result items have committed. Retry the original operation and inspect receipts.

## Validation

```text
node docs/contracts/mobile-v3/1.2.0/validate.cjs
```

Nine fixtures cover objective/page/partial requests, acknowledgements and unsupported
manual/rubric requests. IDs are illustrative, not device-test data. The validator
checks schema/examples/reference integrity and the explicitly narrowed objective
schema; it does not execute SQL/HTTP or certify a full OpenAPI toolchain.

See the [implementation and validation handoff](../../../V3_MOBILE_TEACHER_VERIFICATION_HANDOFF.md).
