# V3 Mobile Prompt 8: backend scoring and first finalization

Date: 2026-09-12. **Objective scoring/finalization component implemented and
validated in isolation. Production Mobile connectivity is still incomplete.**

## Implemented behavior

The existing `POST /api/v3/scoring/results/{testResultId}/finalize` route now
connects accepted Mobile objective answers to `V3ScoringService`. Its path,
bodyless request and `V3ScoredResultResponse` DTO remain unchanged. The focused
[1.3.0 OpenAPI and fixtures](contracts/mobile-v3/1.3.0/README.md) supplement the
frozen 1.0.0 contract pack. No client total or expected revision is introduced.

The scorer acquires a live active-teacher lock before its result lock, matching
the existing Mobile write lock order. It rechecks assignment ownership and school
scope. A scan-backed first finalization additionally requires:

- Exactly one selected, accepted, complete first-capture session and one accepted
  page, linked to the current assessment/sheet version and matching question count.
- The assigned teacher's accepted page audit, plus a committed original-image
  receipt. Attachment identity, page, provider, storage key, MIME, size, hash,
  retention and source lineage must agree. Stored bytes are reread and their
  SHA-256 and size checked before scoring.
- Complete MC/TF answers with objective verification audits, tied to detections
  on that selected page and the correct question/region. Blank answers remain
  blank; selected options must agree with the accepted detection. Uncertain or
  multiple marks cannot bypass review by setting teacher fields.
- Valid backend answer keys. Each objective key must identify an active option
  of its own question. Existing scorer coverage, score bounds and performance-rule
  validation also apply. Mobile-written/rubric answers are outside this bridge.

The existing backend scorer computes correctness, earned points, result totals,
percentage, performance band and per-part totals. It commits those values, the
finalized result status, audit event, one Mobile revision increment, and a durable
official response receipt in the **same database transaction**.

For the synthetic ten-question assessment, each question is worth two points:
nine correct answers and one blank produce **18/20, 90%, 10 evaluated items**.
The seeded rule returns Maintain. TF scoring is tested with synthetic stored
records only; the approved capture/print boundary remains one-page 10-MC A4.

## Retries, concurrency and correction boundary

Retry the same numeric result ID after an uncertain response. Once committed,
the receipt returns the original official score, scoring time, version and part
totals, with `scoreChanged=false`. Envelope timestamps may differ. Changes to
answer keys or performance rules do not rescore an already receipted Mobile
result. Duplicate concurrent finalization commits only once. A rollback leaves
answer correctness, totals, revision, audit and receipt unchanged.

First finalization advances the result's `mobile_revision` once (normally
upload=1, detections=2, verification=3, finalization=4). This existing response DTO
does not return that revision. The bodyless request also does not carry an
expected revision: locks serialize backend writes, but cannot detect a stale
caller screen. Current-state readback/reconciliation remains Prompt 9.

A final teacher-answer transaction racing finalization either commits before
scoring, or finalization rejects incomplete coverage and succeeds on retry after
the review. No verified answer is lost. Earlier verification receipts retain their
historical revision, so Mobile must not treat receipt replay as current-state GET.

An already-scored result without a first-finalization receipt, a changed official
snapshot/revision, or an unaudited reopened result is rejected. No correction,
reopen or superseding endpoint is implemented. The receipt is a first-finalization
score snapshot, not a complete answer-history/reopen design. Saved successful
receipts replay without rereading evidence or current keys; later evidence
recovery remains the upload workflow's responsibility.

## Release and migration boundary

`app.v3.mobile.finalization-enabled` / `V3_MOBILE_FINALIZATION_ENABLED` defaults to
**false**. Scan-linked results and results with OMR answers receive HTTP 503
`MOBILE_FINALIZATION_UNAVAILABLE` while disabled. This includes preexisting paper
results without Mobile receipts; the existing scoring route cannot bypass the
new scan readiness gates. Non-scan V3 scoring continues with the added live account
lock. V1/V2 are unaffected.

The disabled path only queries baseline tables, with a focused compatibility test
that has no Mobile draft tables or revision column. Enabling the bridge requires
separately reviewed deployment of V3_015 through V3_018 and release acceptance.
Enabling it alone does not unlock the denied scan, detection or verification POSTs;
`scanPageUploadAvailable` remains false.

[Draft V3_018](migrations/v3/V3_018_mobile_result_finalization_DRAFT.sql) adds one
first-finalization receipt per result, with FK, JSON, revision and score-version
constraints. No startup migration or school database modification was performed.

| Schema | Tables | Foreign keys | Checks | Unique constraints |
|---|---:|---:|---:|---:|
| Configured V3_014 | 67 | 163 | 87 | 99 |
| Previous isolated V3_017 chain | 72 | 174 | 95 | 104 |
| New isolated V3_018 chain | 73 | 175 | 98 | 104 |

## Error handling

| HTTP/code | Mobile handling |
|---|---|
| 503 `MOBILE_FINALIZATION_UNAVAILABLE` | Release contract unavailable; do not mark connected. |
| 409 `SCAN_VERIFICATION_INCOMPLETE` | Resolve selected-page acceptance, missing audit or sheet-version mismatch. |
| 409 `SCAN_EVIDENCE_INCOMPLETE` | Reconcile original attachment and committed upload receipt. |
| 404 `SCAN_EVIDENCE_NOT_FOUND` | Retry the original upload with identical bytes to restore missing evidence, then finalize again. |
| 500 `SCAN_EVIDENCE_INTEGRITY_FAILED` | Stored evidence differs; backend recovery is required. It is not overwritten by finalization. |
| 409 `OBJECTIVE_VERIFICATION_INCOMPLETE` | Complete valid objective teacher verification. |
| 409 `ANSWER_KEY_MISSING` / `ANSWER_KEY_INVALID` | Correct backend key configuration before first finalization. |
| 409 `AUDITED_REOPEN_REQUIRED` / `FINALIZATION_STATE_CONFLICT` | Stop automatic rescoring; audited correction/reconciliation is required. |
| 409 `RESULT_REVISION_EXHAUSTED` | Revision cannot advance safely; backend intervention required. |
| 401 / 403 / result 404 | Reauthenticate or resolve current ownership; do not create another result to bypass rejection. |
| Uncertain connection/persistence outcome | Retry the same result ID; a prior commit may have succeeded. |

Other existing scorer validation codes remain possible. This single-result route
does not add a batch sync operation or per-page outcome. Upload and verification
retain their own existing receipts.

## Validation

[Machine-readable evidence](V3_MOBILE_FINALIZATION_VALIDATION.json) records focused
suite results, timestamps, contract/migration hashes and isolation details.

The focused suites cover 96 tests: 33 actual MariaDB persistence tests (17 new
finalization cases plus 16 prior scan/detection/verification regressions), nine
existing scoring tests, one baseline compatibility test, 29 verification
persistence tests, 16 verification handler tests, three V3 security-gate tests,
and five existing Java fixture tests. The final affected scoring/persistence
suites were rerun after the concurrency and prior-scored-result guards.

MariaDB 10.4.32 ran in its own directory under `target`, bound to loopback port
33317, using synthetic records. The canonical export and reviewed test chain were
loaded only there. The shared temporary-schema helper now includes draft V3_018.
No application datasource, school server or port 3306 was used. The temporary
instance is stopped after validation. Failures were injected; actual power loss,
production HTTP and physical-phone acceptance were not tested.

```powershell
# First start and prepare the isolated database with the existing tmp helpers.
$validationPath = (Get-Content target/scan-recovery-validation-path.txt -Raw).Trim()
$testDatabase = (Get-Content (Join-Path $validationPath 'database.txt') -Raw).Trim()
mvn -q '-Dtest=V3ScoringServiceTest,V3MobileFinalizationBaselineTest,V3ScanUploadMariaDbTest,V3TeacherVerificationPersistenceTest,V3TeacherVerificationControllerTest,V3ScanPageSecurityGateTest,V3MobileContractFixtureTest' "-Dv3.scan.mariadb.database=$testDatabase" test
node docs/contracts/mobile-v3/1.3.0/validate.cjs
```

## Next bounded slice

Follow-up: [Prompt 9 readback and reconciliation](V3_MOBILE_READBACK_HANDOFF.md)
is component-complete for backend score totals and release gated. The
[milestone tracker](V3_MOBILE_MILESTONE_TRACKER.md) now points to Milestone 3.
Written evidence,
manual/rubric upload, correction/reopen/superseding, secure persistent Mobile
sessions, production wiring and physical scanner acceptance remain pending.
No SF1, Mobile source/SQLite/APK, V1/V2, printed geometry or QR contract was changed.
