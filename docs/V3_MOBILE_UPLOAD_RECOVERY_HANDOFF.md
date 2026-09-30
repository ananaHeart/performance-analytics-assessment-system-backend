# V3 Mobile upload retries and recovery

Date: 2026-09-12. Completed scope: Milestone 2, Prompt 5, following
[scan ingestion](V3_MOBILE_SCAN_INGESTION_HANDOFF.md). Next: Prompt 6, MC/TF detections.

**Retry and recovery components are implemented and persistence-tested. Production
scan upload remains denied. Mobile is not fully connected.** The new ledger is a
draft migration, applied only to an isolated validation database during this slice.

Follow-up: [Prompt 6 detection upload](V3_MOBILE_DETECTION_HANDOFF.md) is now
component-complete. The results below retain the Prompt 5 snapshot; the shared
temporary-schema helper now also applies draft V3_016. Prompt 7 teacher
verification is next. Production release remains blocked.

## Retry behavior

The existing multipart request and response DTO fields remain unchanged.

| Situation | Handler/service behavior |
|---|---|
| First successful upload | HTTP 201, `status=created`, committed backend page identity |
| Identical successful retry | HTTP 200, `status=replayed`, original backend page identity, image hash and receipt `pageStatus=captured` |
| Same page UUID with changed metadata | HTTP 409 `SCAN_IDENTITY_CONFLICT`; no replacement |
| Same sync UUID assigned to another upload group | HTTP 409 `SYNC_IDENTITY_CONFLICT` |
| Actual image bytes do not match the submitted hash | HTTP 422 `IMAGE_HASH_MISMATCH` |
| Interrupted persistence | No success acknowledged; a registered intent remains available for retry/recovery |
| Published evidence is missing | Exact device retry can restore the same attachment identity; recovery alone requires retained staging bytes |
| Published evidence exists but fails integrity checks | Fail closed; never overwrite it |

These are implementation/test results, not currently accessible production HTTP
behavior: the actual V3 security chain still denies this POST. The handler accepts
one page per request. There is no new batch-upload or reconciliation GET endpoint.

The durable fingerprint binds all metadata fields, the teacher/school and original
image hash. Canonical timestamps and field encoding avoid JSON-order differences.
The uploaded bytes are independently validated even on replay. Client filenames
are not identities. `acknowledgedAt` describes the current acknowledgement; it may
change on replay. A receipt's captured status describes the completed upload, not
the result's current scoring/finalization status.

An exact committed retry survives later finalization or sheet retirement while
still requiring current teacher access to the stored relationships. New/pending
uploads must pass the current capture policy. Recovery checks the live teacher
role and ownership; it does not reuse an expired bearer token.

## Durable sequence and concurrency

1. Validate metadata, owned context and incoming JPEG/hash.
2. In a separate transaction, reserve the sync group and persist the page intent,
   canonical request and stable attachment descriptor. Retain its private staging
   bytes before the commit attempt, including uncertain commit acknowledgements.
3. In the completion transaction, publish/verify the registered original, persist
   capture rows, complete the upload sync item and mark the receipt committed.
4. Build the success response only after the transaction manager reports commit.

All ingestion/recovery transactions acquire the same database teacher lock before
ledger/context locks. Two service instances cannot create two receipts for an
identical request. Conflicting UUID reuse is rejected. Reserving `syncUuid` with
the first intent prevents another result from taking the pending batch before
the first page commits.

Publication followed by database rollback leaves a registered pending intent and
the same immutable original. A fresh service instance can finish it. A commit
that succeeded but lost its acknowledgement is subsequently replayed. Missing
retained bytes require the device to retry the same metadata and original bytes.
Existing pages without matching receipts are not automatically adopted.

The scheduled worker is disabled by default:

```properties
app.v3.mobile.scan-recovery-enabled=${V3_SCAN_RECOVERY_ENABLED:false}
app.v3.mobile.scan-recovery-delay-ms=${V3_SCAN_RECOVERY_DELAY_MS:60000}
```

When enabled after deployment validation, it processes at most 20 intents per
run. Failed attempts record a safe error code and timestamp; the pending query
uses the database clock for a 60-second retry cooldown. Internal recovery returns
per-page `recovered` or `retry_required` outcomes. This is not yet a Mobile sync
readback contract. A completed `syncs`/`sync_items` entry means capture upload
success only, not verification, scoring or result finalization.

## Database delta and release gates

[V3_015_mobile_scan_upload_receipts_DRAFT.sql](migrations/v3/V3_015_mobile_scan_upload_receipts_DRAFT.sql)
adds `mobile_scan_uploads`, with unique page/attachment identities, pending/committed
state checks, validated JSON and image limits. It contains no database selection,
destructive SQL or automatic startup application.

| Baseline | Tables | Foreign keys | Checks | Unique constraints |
|---|---:|---:|---:|---:|
| Current configured V3_014 baseline | 67 | 163 | 87 | 99 |
| Isolated V3_014 plus draft V3_015 validation | 68 | 165 | 91 | 101 |

The school database was not accessed or migrated. V3_014 remains the configured
baseline. An approved deployment must review/apply the delta and update expected
baseline counts together before enabling recovery. Production POST denial and
`scanPageUploadAvailable=false` remain in place. Servlet/proxy multipart limits,
persistent private storage and end-to-end deployment acceptance remain release
work; no production enable switch was added for the POST.

## Validation

The latest results across the focused suites contain **116 passing tests, zero
failures, errors or skips**. Affected persistence suites were rerun after the final
pending-batch reservation change. The machine-readable
[validation evidence](V3_MOBILE_UPLOAD_RECOVERY_VALIDATION.json) records each report
timestamp and migration hash.

| Suite | Tests | Scope |
|---|---:|---|
| Scan ingestion persistence | 50 | Isolated H2 transactions and real temporary files |
| MariaDB upload recovery | 4 | Actual MariaDB 10.4.32, reconstructed schema chain, synthetic records and real files |
| Multipart handler | 15 | Standalone MVC with mocked ingestion |
| V3 security release gate | 1 | Actual filter chain; teacher denied and anonymous unauthorized |
| Original-image storage | 36 | Real temporary files |
| Existing Mobile service | 5 | Mocked regression checks |
| Existing Java contract fixtures | 5 | Fixture checks |

Persistence tests cover simultaneous identical/conflicting retries, metadata and
actual-byte mismatches, pending batch reservation, missing/corrupt evidence,
role revocation, lost intent/receipt commit acknowledgements, rollback after
publication, and recovery cooldown. MariaDB also verifies JSON/state constraints.
Failures are injected at transaction/storage boundaries with fresh service
instances; these are not actual process-kill, power-loss or physical-phone tests.

The temporary MariaDB instance uses only loopback port 33317 and a new data
directory under `target`; it does not use application datasource settings. Its
reconstructed schema uses the canonical export plus V3_004, V3_006, V3_007,
V3_009, V3_014 and draft V3_015. The current canonical export already contains
V3_013 school-scoped sections, so V3_013 must not be reapplied to that export.
This validates a fresh reconstruction, not migration of existing school data.
Helper scripts are `tmp/start-scan-recovery-validation.ps1` and
`tmp/prepare-scan-recovery-schema.ps1`.

To reproduce after starting/preparing that isolated instance:

```powershell
$validationPath = (Get-Content target/scan-recovery-validation-path.txt -Raw).Trim()
$testDatabase = (Get-Content (Join-Path $validationPath 'database.txt') -Raw).Trim()
mvn -q '-Dtest=V3ScanPageIngestionPersistenceTest,V3ScanUploadMariaDbTest,V3ScanPageUploadControllerTest,V3ScanPageSecurityGateTest,V3OriginalScanImageStorageTest,V3MobileServiceTest,V3MobileContractFixtureTest' "-Dv3.scan.mariadb.database=$testDatabase" test
node docs/contracts/mobile-v3/1.0.0/validate.cjs
```

The frozen pack validator also passed: 58 fixtures, 44 schemas, seven unchanged
source copies and seven source DTO root-field checks. Pack 1.0.0 remains a
historical snapshot; its availability explanation is not the latest runtime
explanation. No wire fields or Mobile SQLite schema changed.

## Remaining scope

The accepted capture scope remains one page, first capture, 10 MC questions, A4
template `OMR-A4-10-MC-CTX-V2`. Multi-page/rescan support, detections, teacher
verification, written-answer evidence, scoring, finalization/correction integration,
authoritative readback/analytics and physical scanner acceptance remain pending.

A crash before intent registration can leave an unpublished private staging file.
It has no acknowledged receipt and cannot be recovered without a device retry.
This slice does not add an automatic garbage collector or prove filesystem
durability under power loss. Do not blindly delete published evidence. The local
provider still requires persistent storage supporting same-volume hard links.

Next bounded session: **Prompt 6, MC/TF detection upload and persistence**, with
owned region/question/option resolution, idempotent detection identities and
focused validation. Then proceed to teacher verification, scoring/finalization
integration and authoritative readback. SF1, V1/V2, Mobile source, SQLite and APKs
were not changed by this slice.
