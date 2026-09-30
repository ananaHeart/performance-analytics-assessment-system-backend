# V3 Mobile Milestone 2: original-image storage

Date: 2026-09-12. Completed scope: Prompt 3, the first bounded implementation
slice of Milestone 2. **Milestone 2 and complete Mobile connectivity are not complete.**

Current follow-up: [Prompt 5 upload retries and recovery](V3_MOBILE_UPLOAD_RECOVERY_HANDOFF.md)
is component-complete, including isolated MariaDB validation of a draft ledger
migration. Storage descriptions/results below retain the original Prompt 3 scope.

The versioned [Milestone 1 contract pack](contracts/mobile-v3/1.0.0/README.md)
remains the handoff baseline. Its fixtures and wire DTOs were not changed.

## Implemented

`V3OriginalScanImageStorage` is an internal Spring service active only in the
`v3` profile. It has no HTTP route or database access.

- Accepts an original JPEG multipart file and the expected lowercase SHA-256.
- Streams at most 15 MiB, checking actual bytes even if the reported size is false.
- Checks JPEG start/end markers, reads dimensions before decoding, and rejects
  dimensions over 40 million pixels. Full decoding must finish without JPEG
  decoder warnings; malformed/truncated input is rejected.
- Computes SHA-256 from original bytes and rejects a mismatching `imageHash`.
- Preserves exact received bytes; does not resize, recompress, normalize, or
  alter image/QR geometry. Client filenames never become storage keys.
- Allocates a backend v4 attachment UUID and stages a private `.part` file.
  The descriptor contains attachment UUID, `storageProvider=local`, storage key,
  `mimeType=image/jpeg`, actual byte size, SHA-256, width, and height.
- Publishes complete evidence using a same-volume hard link with no overwrite.
  Existing destination files cause failure and remain intact. Concurrent calls
  on one staging handle publish only one file.
- Reads published bytes with a backend descriptor and verifies size/hash again.
  Storage-key validation rejects device paths, URLs, and arbitrary filenames.
- Cleans staging on rejection, interrupted reads, or handle closure. Closing a
  handle never deletes its published original. There is no public overwrite or
  published-file deletion method.

## Storage setup and lifecycle

Configuration: `app.v3.scan-evidence.storage-directory`, with environment alias
`V3_SCAN_EVIDENCE_STORAGE_DIRECTORY`. Default: `output/v3-scan-evidence`.
The default evidence folder is ignored by Git. It is not mapped to a static
resource handler by the current backend.

Configure a private persistent directory with service-account filesystem access
for deployment; do not expose it as a public web directory. The local provider
requires a filesystem supporting same-volume hard links. Publication passed on
the current Windows test filesystem. Unsupported filesystems fail closed;
there is no copying or overwriting fallback. Network/object storage is not
implemented. Immutability here means application-level no-overwrite plus read
integrity checking; it is not OS-level write protection against administrators.

The future ingestion service can use this lifecycle:

```java
try (var staged = storage.stage(imagePart, metadata.imageHash())) {
    var descriptor = staged.image();
    // Future ingestion: persist recovery intent, resolve/lock owned identities,
    // coordinate attachment/scan rows and receipts with publication and commit.
    staged.publish();
}
```

This example is storage usage only, not a complete transaction implementation.
The storage descriptor is available before publication so recovery tracking can
record the UUID/key first. Re-staging a request creates a new identity: request
deduplication must be performed by the future ingestion/receipt service. Repeated
publication of one in-memory handle is not durable request idempotency.

Staged file data is flushed before publication. A process crash can leave staging
or an unreferenced published file. Cleanup failure is logged and does not remove
published evidence. Durable recovery, orphan reconciliation, transaction
compensation, and power-loss validation remain required before upload release.
No database acceptance should be acknowledged based solely on `publish()`.

Storage validation uses `422 VALIDATION_FAILED`, `422 IMAGE_HASH_MISMATCH`, and
`413 PAYLOAD_TOO_LARGE`. Storage I/O/integrity failures are safe `500` errors;
missing evidence returns `404 SCAN_EVIDENCE_NOT_FOUND`. These are internal service
errors until the upload/readback routes are implemented. The component enforces
its file/pixel limits; servlet multipart limits, one-image enforcement, and the
64 KiB metadata limit still belong to the next HTTP integration slice.

## Verification

Executed against real temporary files, without application startup or database
connections:

```text
mvn -q "-Dtest=V3OriginalScanImageStorageTest,V3MobileServiceTest,V3MobileContractFixtureTest" test
```

Result: **46 tests passed; zero failures/errors/skips**:

| Suite | Tests |
|---|---:|
| Original scan image storage | 36 |
| Existing Mobile service | 5 |
| Existing Mobile contract fixtures | 5 |

Storage cases include exact-byte preservation, hash/MIME rejection, disguised PNG,
truncation with a restored end marker, oversized dimensions, false size reports,
interrupted streams, staging cleanup, pre-existing destinations, concurrent
publication, invalid storage keys, and detection of modified published bytes.
The oversized dimension test also covers signed-int pixel-count overflow.

`node docs/contracts/mobile-v3/1.0.0/validate.cjs` also passed: 58 fixtures,
44 schemas, seven unchanged source copies and seven source DTO root-field checks.
The default evidence-folder Git ignore rule was verified. No live upload,
database-persistence, physical-phone, or scanner acceptance test was run.

## Next bounded slices

| Prompt | Scope | Status |
|---|---|---|
| 3 | Original-image validation and storage | COMPLETE for the component above |
| 4 | Scan ingestion handler, ownership, UUID/central-ID resolution and persistence | COMPONENT COMPLETE; HTTP release blocked; see [ingestion handoff](V3_MOBILE_SCAN_INGESTION_HANDOFF.md) |
| 5 | Durable retries, concurrency, receipts and failure recovery | COMPONENT COMPLETE; H2/MariaDB tested; draft ledger deployment pending; see [recovery handoff](V3_MOBILE_UPLOAD_RECOVERY_HANDOFF.md) |
| 6 | MC/TF detections | COMPONENT COMPLETE; H2/MariaDB tested, release blocked; see [detection handoff](V3_MOBILE_DETECTION_HANDOFF.md) |
| 7 | Teacher verification | OBJECTIVE COMPONENT COMPLETE; per-result retries and audit tested; see [verification handoff](V3_MOBILE_TEACHER_VERIFICATION_HANDOFF.md) |
| 8 | Existing backend scoring/finalization integration | OBJECTIVE COMPONENT COMPLETE; release gated. See [Prompt 8 handoff](V3_MOBILE_FINALIZATION_HANDOFF.md) |
| 9 | Authoritative result readback and reconciliation | COMPONENT COMPLETE; release gated. See [Prompt 9 handoff](V3_MOBILE_READBACK_HANDOFF.md) |

Current consolidated status and next milestone: [milestone tracker](V3_MOBILE_MILESTONE_TRACKER.md).

The original Prompt 4 scope was to implement the planned `POST /api/v3/mobile/scan-pages` contract with
owned sheet/page/assignment/result resolution, original attachment metadata and
scan persistence. Preserve the current DTO fields, QR payload, and immutable
sheet geometry. Any required database delta must be identified explicitly;
do not silently claim V3_014 already supports a durable receipt ledger. Keep
capability release gated until persistence, retry and recovery validation passes.

`scanPageUploadAvailable` remains false. Mobile is not fully connected. Written
answer uploads/scoring, correction/reopen, complete analytics and physical dynamic
scanner acceptance remain separate pending work. SF1, V1/V2, Mobile SQLite,
Mobile source and APKs were not changed by this slice. No migration was added or
applied; V3_014 remains the latest source baseline inspected, not a new live-DB
verification.
