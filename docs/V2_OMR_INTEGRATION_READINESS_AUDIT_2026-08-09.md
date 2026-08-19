# V2 OMR Integration Readiness Audit

**Date:** August 9, 2026  
**Scope:** Isolated Python/OpenCV prototype versus the proposed V2 API and central database contract

## Decision

The OMR prototype remains isolated. Production SQLite, V1 synchronization, Spring Boot runtime code, local V1 MySQL, and TiDB are unchanged. Integration is blocked until the identity, question mapping, True/False encoding, ambiguous-detection behavior, template registry, and upload metadata match the approved V2 contract.

## Prototype Contract Blockers

1. QR must use authoritative `classListId` (`cl`), not only `studentId`.
2. Scanner maps and detections must carry authoritative numeric `questionId`; `itemNumber` is display and diagnostic data only.
3. True/False prints `T/F` but normalizes to `A` for True and `B` for False.
4. `detectedOption` must be null for `blank`, `multiple_marks`, and `uncertain`.
5. Upload-ready output must include `scanUuid`, `scanStatus`, `scannedAt`, `verifiedAt`, `imageHash` when available, `verificationStatus`, and offset-bearing `detectedAt`.
6. QR contract version is `v: 2`; `testId` and `classListId` are numeric central identifiers.
7. A `templateVersion` is immutable and identifies one exact paper geometry, item count, question type, printed option set, and revision.
8. Current physical validation does not yet prove the QR-enabled V2 Multiple Choice and True/False layouts under real capture conditions.

## V8 Word Dictionary Versus SQL Reference

The mobile review found stale definitions in the V8 Word dictionary. The current SQL reference at `docs/performance_assessment_v2_schema.sql` already contains the intended definitions:

| Concern | Current SQL reference |
| --- | --- |
| Batch status | `syncs.sync_status` includes `partial_success`. |
| Retry identity | `sync_items.result_uuid` is required. |
| Central result ID | `sync_items.test_result_id` is nullable until insert or match succeeds. |
| Retry uniqueness | `UNIQUE (sync_id, result_uuid)`. |
| Item error | `sync_items.error_code` and `error_message` exist. |
| Scan status | `captured`, `processing`, `needs_verification`, `verified`, `failed`. |
| Detection verification | `omr_detections.verification_status` is non-null with default `pending`. |

The Word Data Dictionary must be updated to match these definitions before it is treated as the schema source of truth.

## Approved Recapture Database Decision

The approved SQL reference uses `test_result_scans` instead of a direct `test_results.scan_session_id`. It retains selected, superseded, and rejected captures, associates the decision with the responsible teacher, and uses a generated nullable key with a unique constraint to permit at most one selected scan per result. Existing V1 and TiDB databases remain unchanged; only the separate local V2 validation database is eligible for this migration.

## Approved Work Order

1. Align the versioned Word Data Dictionary and ERD with `test_result_scans`.
2. Validate the recapture migration and constraints in the separate local V2 database.
3. Freeze exact QR and template registry definitions.
4. Update only the isolated prototype output contract.
5. Physically validate filled QR-enabled MC and True/False sheets across representative devices and lighting.
6. Approve teacher-verification request and response shapes.
7. Only then design and approve mobile SQLite migration and V2 synchronization integration.
