# V3 Mobile scan ingestion: implementation and release gate

Date: 2026-09-12. Scope: Milestone 2, Prompt 4, following
[original-image storage](V3_MOBILE_MILESTONE_2_STORAGE_HANDOFF.md).

The backend now contains the multipart scan-page handler, owned-context resolution
and transactional ingestion code. **The route remains unavailable through V3
security. Mobile is not fully connected and must not start production upload.**

Update after Prompt 5: durable retries and recovery are now implemented and tested
on H2 and isolated MariaDB. See the [current recovery handoff](V3_MOBILE_UPLOAD_RECOVERY_HANDOFF.md).
The results and pending-work descriptions below are the historical Prompt 4
snapshot; the receipt/recovery gaps are superseded by that handoff. Deployment
and complete Mobile connectivity remain pending.

## Completed in this slice

- `POST /api/v3/mobile/scan-pages` handler accepts exactly one `metadata`
  application/json part and one `image` part. Metadata is limited to 64 KiB by
  declared and actual bytes, works without a filename, and rejects unknown fields,
  duplicate JSON keys, trailing JSON and scalar coercion. The DTO fields stay unchanged.
- The service requires an active teacher and resolves assignment, membership,
  student, sheet, page and template from stored relationships. It checks the
  teacher, assessment, student and section school scope; active account/class/
  assignment/enrollment; ready sheet/page; and permitted assessment status.
- The first implementation accepts the currently supported one-page, 10-question
  MC A4 template `OMR-A4-10-MC-CTX-V2`, first capture only. It checks scanner version,
  test version/item snapshots, question mappings, page number and the SHA-256 of
  the stored QR payload. This is not image QR decoding or proof of learner identity
  from the QR. Printed geometry and payload remain unchanged.
- The backend allocates the attempt number under transaction locks. A new result
  starts as `draft`, with zero points/evaluated items and a maximum-score snapshot
  computed from stored question points. No official score or teacher verification
  is inferred from ingestion.
- The transaction inserts/reuses the owned result and scan session, links the
  scan to its result, creates the sync group/item, and inserts the captured page
  and `original_page` attachment with private storage metadata and original hash.
- Original bytes use the preceding storage component: JPEG validation, 15 MiB and
  40-million-pixel limits, hash verification, no overwrite, and read integrity
  verification before the transaction returns. The success DTO is constructed
  only after the transaction manager reports a successful commit.
- Existing result/scan/sync identities cannot be reassigned to another capture
  context. Finalized/superseded results, occupied page captures and unsupported
  rescans are rejected. Result-to-scan selection is an initial capture link,
  explicitly marked as awaiting teacher verification.

Capture policy currently rejects planned/archived deliveries, captures before
`open_at`, and timestamps more than five minutes into the future. Without
`allow_late_capture`, it also rejects closed deliveries and captures after
`close_at`. An open delivery can receive an offline capture made within its time
window. Client capture time is a claimed timestamp, not independent proof of
when the paper was scanned. Scanner versions accept one to three numeric components.

## Verified behavior and limits

The focused suite passed **93 tests, zero failures/errors/skips**:

| Suite | Tests | Evidence |
|---|---:|---|
| Scan ingestion persistence | 32 | Real SQL transactions in isolated in-memory H2 and real temporary JPEG files |
| Multipart handler | 14 | Standalone MVC with mocked ingestion service |
| V3 security release gate | 1 | Actual V3 filter chain, mocked token authentication; teacher denied, anonymous unauthorized |
| Original-image storage | 36 | Real temporary files |
| Existing Mobile service | 5 | Existing mocked service regression |
| Existing Mobile fixtures | 5 | Existing Java fixture checks |

Command:

```text
mvn -q "-Dtest=V3ScanPageIngestionPersistenceTest,V3ScanPageUploadControllerTest,V3ScanPageSecurityGateTest,V3OriginalScanImageStorageTest,V3MobileServiceTest,V3MobileContractFixtureTest" test
```

The persistence suite covers stored ID resolution, attempt allocation, private
original bytes, rollback on validation/storage failure, cross-school/teacher/
learner rejection, stale manifests, hash mismatches, duplicate identities and
locked results. An injected commit failure confirms no success response is
returned and demonstrates that an orphan file can remain for later recovery.

H2 is a **test-only** dependency. The SQL fixture is a projection of columns and
constraints used by ingestion, derived from the migration chain; it is not the
complete V3_014 schema. These tests do **not** validate the MySQL driver, full
production constraints, crash durability, concurrent upload races or a running
phone-to-backend request. No existing database was connected to or changed.

The 58-fixture, 44-schema pack validator also passed, including all seven unchanged
source fixture copies. Pack 1.0.0 remains the historical Milestone 1 snapshot; its
old availability-reason text is an example, not the newest source status. The
runtime source reason now identifies the remaining receipt/recovery/MySQL gates.
`scanPageUploadAvailable` is still false. No wire fields changed.

## Historical next slice at Prompt 4 completion: Prompt 5

1. Add durable per-page receipt storage and a recovery ledger through an explicit
   reviewed database delta. V3_014's result-level `sync_items` is insufficient for
   the full page/operation contract. No migration has been added or applied here.
2. Bind immutable request metadata and original content to each page receipt;
   exact retry must return the original page identity/status, changed reuse must
   conflict. The present implementation deliberately returns
   `409 SCAN_PAGE_RECEIPT_PENDING` for an existing page, including identical retries.
3. Coordinate staging, publication, database commit and durable recovery for
   interrupted/uncertain commits. The current component may leave an unreferenced
   published image after commit failure. Do not delete published evidence blindly.
4. Validate batch identity, per-page outcomes and concurrent retry behavior against
   an isolated MySQL-compatible database using the applied schema chain. Current
   sync group hashes bind teacher/delivery/learner/result/session/sheet, never the
   first page's bytes. Parent `syncs` stays `in_progress`; its `sync_items` stays
   `pending` until the durable acknowledgement slice implements completion.
5. Before release, configure and test servlet/proxy multipart limits for the
   intended 15 MiB image plus metadata/envelope. Existing servlet settings were
   not expanded in this slice. Only then update the explicit security denial and
   capability flag together after the release checks pass.

The security rule intentionally denies this POST even for a valid teacher bearer
token. Handler-level 201 results are test evidence, not a currently usable mobile
API. There is no production enable switch in this slice.

Written-answer attachments, detections, teacher verification/manual scoring,
finalization integration, reopen/correction, readback, analytics and physical
scanner acceptance remain subsequent work. SF1, V1/V2 behavior, Mobile source,
SQLite, APKs, and the frozen contract pack were not changed.

Changed production source: new `V3ScanPageUploadController`,
`V3ScanPageIngestionService`, and `V3ScanPageRepository`; explicit POST denial in
`V3SystemSecurityConfig`; accurate availability text in `V3MobileService`.
Other additions: H2 test dependency, isolated SQL fixture, three test classes and
this handoff. Existing unrelated worktree changes were preserved.
