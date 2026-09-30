# V3 Mobile teacher verification

Date: 2026-09-12. Milestone 2, Prompt 7 is component-complete for page decisions
and objective MC/TF answer acceptance. Next: Prompt 8, scoring/finalization
integration. **Production verification remains disabled; Mobile is not fully connected.**

## Implemented behavior

`POST /api/v3/mobile/verification-batches` accepts the objective subset of the
planned contract. One batch contains one assignment and 1..50 unique result items.
Each item contains `expectedRevision`, page decisions and/or objective answers.
The [1.2.0 contract supplement](contracts/mobile-v3/1.2.0/README.md) includes
[OpenAPI 3.1](contracts/mobile-v3/1.2.0/openapi.json), nine examples and a validator.
Wire `contractVersion` stays `3.0`; earlier packs remain unchanged. Manual/rubric
evaluations are explicitly unsupported in this slice.

The complete batch is validated and authorized before its immutable operation and
sync identities are registered. A malformed or initially unauthorized batch writes
no result item. After registration, each result runs in its own transaction and
rechecks current teacher/assignment/result/page access. One learner's invalid
verification or failed transaction cannot roll back another learner's success.

The handler rejects unknown fields, duplicate JSON keys, missing nullable fields,
numeric strings, fractional revisions and non-UTC client timestamp strings. Actual
and declared JSON body size are bounded to 2 MiB. A result allows up to 200 page
decisions and 200 answers, with at least one. UUID duplicates are rejected before
registration; page/question uniqueness is checked within each item.

Page decisions append to `scan_verifications`: accepted, rejected or rescan requested.
Non-acceptance requires a reason code. Full comments up to 4000 characters and the
client's UTC decision instant are retained. The server supplies verifier identity
and authoritative `decided_at`; client time is separate, untrusted evidence.

Objective answers resolve stored detection, region, question and scan-page IDs
server-side. The page must already be accepted or accepted in the same transaction.
Detected marks use the stored question option; blanks retain null options. Multiple
or uncertain marks cannot be converted to a selected answer. A bad answer rolls
back that item's page decisions and every answer/audit/revision write.

Accepted answers are inserted into `student_answers` and linked to immutable
objective audit records. `answer_verifications` is not misused for objective work;
it remains the written-evaluation/correction history table. Existing answers and
initial page decisions are not overwritten by new operations. Correction/reopen
and actual rescan/replacement capture remain later contracts.

## Verification versus scoring

Each successful result item increments `mobile_revision` once and leaves the
result `pending_verification`. Appropriate page decisions update scan status;
all expected pages must be accepted before the session is marked accepted.

Individual accepted answers receive `verified_by_user_id`, server `verified_at`,
`evaluation_status=finalized` and `finalized_at`, as required by the existing scorer.
This denotes completion of the individual answer review, not official result
finalization. `is_correct` stays null, points stay zero and no result totals,
evaluated-item count or official score version are computed here. A partial answer
set can be stored; it is insufficient to finalize an incomplete assessment.

The actual MariaDB test reads these records through `V3ScoringRepository` and
checks its required verification fields. It does not run the complete scoring
workflow. Existing scoring/finalization routes still need Mobile revision and
lifecycle integration in Prompt 8; the new revision counter is not yet a completed
whole-system concurrency contract.

## Per-result outcomes and retries

HTTP 200 with envelope `success=true` means the batch was processed. Mobile must
inspect `data.syncStatus` (`success`, `partial_success`, `failed`) and every item's
status, disposition, revision, ID mappings and nullable `{code,message,retryable}`
error. Item success and its saved response commit with the answer/page mutations.

- A new immutable batch reserves its sync UUID and fixed item set before processing.
- Successful retries return `replayed` with original revision/mappings, without
  rechecking an obsolete expected revision or duplicating decisions. Live access
  is still required, even for historical replay after finalization.
- Array ordering does not change identity. Changed content, expected revision or
  item set under the same operation UUID is a top-level 409 conflict.
- Stale revisions and invalid decisions produce permanent item failures. These
  replay their stored failure; fixing content requires a new operation/sync UUID.
- Missing dependencies and database failures are retryable. Identical retries leave
  successful items untouched and retry transient failed items. A dependency that
  advances the result revision may require reconciliation and a new operation.
- Unknown registration commits can leave resumable pending items. Unknown item
  commits are resolved against saved receipts before recording failure. If outcome
  recording itself fails, a top-level 500 may occur after other items have committed.
  Retry the same immutable request; do not generate replacement entity UUIDs.

Items are returned in canonical result-UUID order. Answer-containing items return
student-answer mappings; page-only items return scan-verification mappings. Mixed
items omit page-audit ID mappings to preserve the existing 200-mapping response
limit. Those page audit records and UUIDs remain stored. No new reconciliation GET
or recovery scheduler was added; retrying the batch resumes pending work.

## Draft schema and release boundary

[V3_017_mobile_teacher_verification_DRAFT.sql](migrations/v3/V3_017_mobile_teacher_verification_DRAFT.sql)
adds immutable batch registration, per-item receipts and objective decision audit
tables. It adds client-time/operation linkage to page audits and widens their
comment column to match the proposed 4000-character contract.

| Baseline | Tables | Foreign keys | Checks | Unique constraints |
|---|---:|---:|---:|---:|
| Configured V3_014 | 67 | 163 | 87 | 99 |
| Prior isolated V3_016 chain | 69 | 167 | 93 | 102 |
| New isolated V3_017 chain | 72 | 174 | 95 | 104 |

No school database connection or migration was performed. All three draft Mobile
migrations require their separate review/deployment step and matching baseline
configuration updates. Runtime defaults remain V3_014. V3 security explicitly
denies scan-page, detection and verification POSTs, and scan upload availability
remains false. Private storage, deployment upload limits and phone acceptance
remain release work.

## Validation

**126 passing tests, zero failures/errors/skips**, across this session's focused
suites. [Machine-readable evidence](V3_MOBILE_TEACHER_VERIFICATION_VALIDATION.json)
records report timestamps and migration/contract hashes. After the full focused
run, the two affected persistence suites were rerun for the final per-result
authorization change and competing-revision race.

| Suite | Tests |
|---|---:|
| Teacher verification persistence, isolated H2 | 29 |
| Teacher verification JSON handler | 16 |
| Actual MariaDB persistence | 16: six new verification tests plus ten scan/detection regressions |
| Actual V3 security gates | 3 |
| Detection H2 persistence regression | 42 |
| Detection handler regression | 15 |
| Existing Java contract fixture checks | 5 |

Cases include ownership rejection before registration, MC/TF and blank acceptance,
unresolved-mark rollback, client/server audit time separation, long comments,
rescan/rejection, stale revisions, immutable retries, finalization-time replay,
concurrent identical/different operations, and lost registration/item commit
acknowledgements. The MariaDB two-learner test preserves one committed result while
another transaction fails, then finishes only the remaining learner on retry.

Schema validation used MariaDB 10.4.32 in a fresh loopback instance at port 33317,
with synthetic records and its own data directory under `target`. The shared
temporary-schema helper now includes draft V3_017 after the prior validated chain.
The canonical export already includes V3_013. The temporary instance is stopped
after validation. No application datasource or school server was used.

```powershell
$validationPath = (Get-Content target/scan-recovery-validation-path.txt -Raw).Trim()
$testDatabase = (Get-Content (Join-Path $validationPath 'database.txt') -Raw).Trim()
mvn -q '-Dtest=V3TeacherVerificationPersistenceTest,V3TeacherVerificationControllerTest,V3ScanUploadMariaDbTest,V3ScanPageSecurityGateTest,V3DetectionPersistenceTest,V3DetectionUploadControllerTest,V3MobileContractFixtureTest' "-Dv3.scan.mariadb.database=$testDatabase" test
node docs/contracts/mobile-v3/1.2.0/validate.cjs
```

The command requires starting/preparing the isolated instance first using the
existing `tmp` validation scripts. All three contract validators passed: 1.0.0
(58 fixtures), 1.1.0 (nine), and 1.2.0 (nine). These checks are not full OpenAPI
toolchain certification. Transaction failures were injected; actual power loss,
physical-phone scanning and production HTTP were not tested. TF uses synthetic
records; approved original capture remains the one-page 10-MC A4 template.

Follow-up: [Prompt 8 scoring/finalization integration](V3_MOBILE_FINALIZATION_HANDOFF.md)
is objective-component complete and release gated. Next is Prompt 9 authoritative
readback/reconciliation. Written evidence/manual/rubric
scoring, correction/reopen, complete readback/reconciliation/analytics and physical
scanner acceptance remain pending. SF1, V1/V2, Mobile code/SQLite/APKs, printed
geometry/QR contracts and the school database were untouched.
