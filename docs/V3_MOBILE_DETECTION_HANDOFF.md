# V3 Mobile MC/TF detection upload and persistence

Date: 2026-09-12. Milestone 2, Prompt 6 is component-complete. Next bounded session:
Prompt 7, teacher verification. **Production Mobile upload is still disabled and
complete Mobile connectivity is not established.**

Follow-up: [Prompt 7 teacher verification](V3_MOBILE_TEACHER_VERIFICATION_HANDOFF.md)
is now component-complete for page decisions and objective answers. The results
below retain the Prompt 6 snapshot. The shared schema helper now also includes
draft V3_017; Prompt 8 scoring/finalization integration is next.

## Implemented

`POST /api/v3/mobile/scan-pages/{scanPageUuid}/detections` now has a JSON handler,
validation, transactional persistence and durable operation receipts. It accepts
the previously proposed detection fields unchanged: `contractVersion`, `syncUuid`,
`operationUuid`, and 1..200 `detections`. Each detection has `detectionUuid`,
`regionUuid`, `questionUuid`, `detectionStatus`, explicit nullable `detectedOption`
and numeric `confidence`. The actual V3 security chain explicitly denies this POST.

The [1.1.0 focused contract supplement](contracts/mobile-v3/1.1.0/README.md) includes
[OpenAPI 3.1](contracts/mobile-v3/1.1.0/openapi.json), nine fixtures and a read-only
validator. Pack version changes to 1.1.0; wire `contractVersion` stays `3.0`. Frozen
pack 1.0.0 remains unchanged and historical. The supplement is not authorization
to migrate Mobile SQLite or start production wiring.

Backend resolves the path page through its committed original-upload receipt,
owned scan/result, teacher assignment, learner and school. New observations require
an active owner, a selected/unverified capture, a draft or pending-verification
result, and matching assessment/sheet versions. It resolves each region/question
pair against that page and checks objective type, question part and sheet ownership.

The central database has no `answer_sheet_region_options` table. Permitted keys
come from immutable `answer_sheet_regions.geometry_snapshot.option_keys` and
active `question_options`; these must agree. MC uses stored A/B/C/D keys; TF uses
A=True/B=False. `T`/`F` labels are rejected. Blank, uncertain and multiple-mark
observations retain null options and never become automatically verified answers.

Observations are persisted in existing `omr_detections`, including resolved scan,
page, region and question IDs. `raw_mark` stores a labelled `mobile_summary_v3`
JSON observation with exact reported confidence. The existing DECIMAL(5,4)
`confidence_score` column gets a HALF_UP rounded projection. The DTO supplies no
per-bubble darkness measurements; none are invented. Server time stamps detection
receipt; no client detection timestamp field was added.

No scores, correctness flags, comments, manual points, answer rows or teacher
decisions are accepted/created here. Detection persistence leaves total score,
evaluated-item count, result status and page verification status unchanged.

## Atomicity and retry contract

The transaction locks the active teacher, receipt and owned page/result before
writing. A single invalid observation rolls back the entire request: detections,
sync group/item, revision increment and receipt all commit together. The handler
acknowledges only after transaction commit returns successfully.

| Case | Implemented outcome behind the release gate |
|---|---|
| New valid batch | 201, `disposition=created`, detection ID mappings and incremented revision |
| Same operation, page, sync UUID and observations | 200, `disposition=replayed`, original mappings/revision/acknowledgement time |
| Array reordered or equivalent decimal spelling | Same replay |
| Changed immutable operation content | 409 `DETECTION_IDENTITY_CONFLICT` |
| Existing detection UUID or occupied page region under a new operation | 409 `DETECTION_IDENTITY_CONFLICT`; no replacement |
| Sync UUID reused by another stage/operation | 409 `SYNC_IDENTITY_CONFLICT` |
| Invalid region/question/option or locked result/capture | 409; entire batch rejected |
| Owner or committed page unavailable | 404 `SCAN_CONTEXT_NOT_FOUND` |
| Invalid JSON, unknown fields, duplicate keys/observations or invalid values | 422 `VALIDATION_FAILED` |
| JSON body exceeds declared or actual 128 KiB limit | 413 `PAYLOAD_TOO_LARGE` |
| Commit failure/uncertain acknowledgement | Safe failure; identical retry commits or replays |

Canonical fingerprints bind owner, school, path page and every request field.
Detection UUID sorting and normalized decimals make order/format-only retries
stable. Response mapping order is canonical; correlate by UUID. A new operation
may add disjoint regions, but cannot rewrite an occupied one. No request implicitly
claims all regions are complete. Exact committed replay survives later lifecycle
changes while retaining live teacher/ownership checks.

Detection uploads contain no filesystem publication step: a single database
transaction suffices. Lost acknowledgements use the saved response JSON. The
existing original-image recovery worker remains disabled by default and unchanged.

## Draft schema delta

[V3_016_mobile_detection_receipts_DRAFT.sql](migrations/v3/V3_016_mobile_detection_receipts_DRAFT.sql)
adds `mobile_detection_uploads` with unique operation/sync identities and JSON
receipt validation, plus `test_results.mobile_revision` defaulting to 1. Each new
detection batch advances the revision once. The saved receipt returns that original
revision on replay even if later state advances.

This begins the proposed Mobile revision mechanism. Existing scoring routes do
not yet increment it; integrating teacher verification, attachments, finalization
and correction remains subsequent work. It must not be advertised as a complete
whole-system optimistic concurrency contract or as official `scoreVersion`.

| Schema | Tables | Foreign keys | Checks | Unique constraints |
|---|---:|---:|---:|---:|
| Configured V3_014 baseline | 67 | 163 | 87 | 99 |
| Prior isolated V3_015 validation | 68 | 165 | 91 | 101 |
| Fresh isolated V3_015 + V3_016 validation | 69 | 167 | 93 | 102 |

The school database was not accessed or modified. No migration ran automatically;
profile baseline defaults remain V3_014. Draft V3_015/V3_016 deployment and matching
baseline-count updates need their separate review/deployment step before release.
Both mobile upload POSTs remain denied; `scanPageUploadAvailable=false` remains.

## Validation evidence

Latest successful reports across this session's focused suites: **139 tests,
zero failures/errors/skips**. See [machine-readable evidence](V3_MOBILE_DETECTION_VALIDATION.json).

| Suite | Tests | Evidence |
|---|---:|---|
| Detection persistence | 42 | H2 transactions: ownership, pairing/options, ambiguity, rollback, concurrent replay, locked state and exact confidence |
| Detection handler | 15 | Strict JSON parsing, existing fixture shapes, response codes, actual body bound |
| MariaDB scan/detection persistence | 10 | Six new detection cases plus four existing scan retry/recovery cases, actual schema chain and synthetic records |
| Existing scan ingestion | 50 | H2 regression |
| Existing scan handler | 15 | MVC regression |
| Actual V3 security gate | 2 | Both POSTs deny valid teachers and reject anonymous callers |
| Existing Java fixture checks | 5 | Contract regression |

The full focused run was followed by rerunning the two detection suites after
adding exact-confidence and unknown-content-length checks. Contract validators
also passed: 1.0.0 has 58 fixtures/44 schemas; 1.1.0 has nine fixtures/four schemas
with unchanged parent wire shapes. The latter validates schema/examples and
references, not full OpenAPI toolchain conformance.

The actual engine was MariaDB 10.4.32 on a fresh isolated loopback instance at
port 33317 with a new `target` data directory. The schema used the canonical export
plus V3_004, V3_006, V3_007, V3_009, V3_014, V3_015 and V3_016. The canonical export
already includes V3_013. The temporary instance is shut down after validation;
no application datasource, school data or port 3306 connection was used.

Reproduction after starting/preparing that isolated instance with the existing
`tmp/start-scan-recovery-validation.ps1` and `tmp/prepare-scan-recovery-schema.ps1`:

```powershell
$validationPath = (Get-Content target/scan-recovery-validation-path.txt -Raw).Trim()
$testDatabase = (Get-Content (Join-Path $validationPath 'database.txt') -Raw).Trim()
mvn -q '-Dtest=V3DetectionPersistenceTest,V3DetectionUploadControllerTest,V3ScanUploadMariaDbTest,V3ScanPageSecurityGateTest,V3ScanPageIngestionPersistenceTest,V3ScanPageUploadControllerTest,V3MobileContractFixtureTest' "-Dv3.scan.mariadb.database=$testDatabase" test
node docs/contracts/mobile-v3/1.1.0/validate.cjs
node docs/contracts/mobile-v3/1.0.0/validate.cjs
```

## Remaining work and acceptance boundary

TF persistence is validated against synthetic stored relationships, including an
explicit TF arrangement in the MariaDB fixture. It is not an end-to-end TF sheet
test. Original scan ingestion still accepts only the approved one-page, 10-MC A4
first-capture template. No real manifest geometry, QR payload or printed template
was changed. Physical scanner reliability and TF template acceptance remain pending.

Commit-failure tests inject transaction exceptions, including after an actual
commit. They are not physical power-loss/process-kill tests. Production HTTP,
phone reachability and deployment multipart limits were not acceptance-tested.

Next: Prompt 7 teacher verification, consuming these immutable detections with
audited decisions and revision checks. Written evidence/manual scoring, scoring
and finalization integration, correction/reopen, authoritative readback and
analytics remain pending. SF1, V1/V2, Mobile code/SQLite/APKs and the school database
were untouched in this slice.
