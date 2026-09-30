# Message For Mobile AI: V3 Central Baseline Update

The isolated local V3 central database has now been built and populated from the
current V2 data. This is a backend/database milestone only. Do not replace or
migrate production V1 Mobile SQLite yet.

## Finalized And Locally Validated

- Central database: `performance_assessment_v3_db`
- Central tables: 66
- Dynamic answer-sheet schema through `V3_009` is applied to local central V3.
- Exact structural baseline: 157 foreign keys, 80 checks, 96 unique constraints.
- V2 source remains unchanged with 41 tables.
- 8 users, 13 students, 27 memberships, 7 tests, 9 results, 9 scans, and 2 sync
  envelopes were migrated and reconciled.
- Existing `result_uuid`, `answer_uuid`, `scan_uuid`, and `sync_uuid` values were
  preserved.
- Repeated sync processing is modeled as `upsert`; `sync_items` enforces
  `UNIQUE(sync_id, result_uuid)`.
- A sync envelope belongs to at most one `test_assignment_id`; each learner result
  still receives an individual item-level success or failure response.
- Shared normalized naming is `part_skill_mappings`, not `mappings` or
  `question_mappings`.
- Tests are reusable content. Mobile downloads and results must identify the exact
  `test_assignment_id`, not only `test_id`.
- Four active system `performance_rule_sets` now store the versioned student-score,
  skill-mastery, class-mastery, and intervention rules used by the central backend.

## Shared Status Values

- `test_assignments.assignment_status`: `planned`, `open`, `closed`, `archived`
- `scan_sessions.scan_status`: `captured`, `processing`, `needs_verification`,
  `accepted`, `rescan_requested`, `rejected`, `superseded`, `failed`
- `omr_detections.detection_status`: `detected`, `blank`, `multiple_marks`, `uncertain`
- `student_answers.answer_status`: `answered`, `blank`, `multiple`, `uncertain`,
  `invalid`, `pending_manual`
- `student_answers.evaluation_status`: `pending_verification`,
  `needs_manual_scoring`, `scored`, `finalized`
- `test_results.result_status`: `draft`, `pending_verification`, `finalized`, `superseded`
- `syncs.sync_status`: `pending`, `in_progress`, `partial_success`, `success`, `failed`
- `sync_items.sync_status`: `pending`, `success`, `failed`, `skipped`
- `sync_items.sync_action`: `upsert`

## Question And Verification Rules

- Supported reference types: `multiple_choice`, `true_false`, `identification`,
  `enumeration`, and `essay`.
- Multiple Choice and True/False use automatic objective scoring and set
  `allows_teacher_answer_edit = false`.
- For blank, multiple-mark, or uncertain objective scans, the teacher may accept,
  request a rescan, or reject the sheet. The teacher must not replace the detected
  MC/TF answer value.
- Identification, Enumeration, and Essay use OCR/manual or hybrid workflows and
  append-only `answer_verifications` where teacher transcription/scoring is allowed.
- Raw scan evidence in `scan_sessions` and `omr_detections` remains immutable.

## Current Physically Validated OMR Baseline

- Authoritative template: `OMR-A4-10-MC-CTX-V2`
- Question type: Multiple Choice
- Item count: exactly 10
- Options: A-D
- QR payload version: 2
- Minimum scanner version: `1.0`
- The exact marker and bubble geometry is stored in `omr_templates.geometry_definition`.

This remains the only physically validated scanner layout. Do not change or reuse
its template code for another paper size, item count, page layout, or question
type.

## Approved Dynamic Answer-Sheet Schema Baseline

The approved V3 central schema now represents:

- `A4`, `US_LETTER`, and `US_LEGAL`, each with separate immutable geometry;
- at least 5 total questions, with no fixed maximum or fixed 10-item part size;
- ordered dynamic `test_parts` using any subset/order of the five question types;
- multiple pages with page-specific markers, QR identity, and question regions;
- read-only MC/TF detections; and
- retained written-response crops plus teacher evaluation/rubric scoring for
  Identification, Enumeration, and Essay.

The six page/region entities are now present in local central V3. The functional
generator, download/upload APIs, Mobile SQLite mapping, and new physical template
geometries are not implemented. The authoritative design-impact assessment is
`V3_DYNAMIC_ANSWER_SHEET_DESIGN_CONTRACT.md`.

## Mobile Naming Direction

For the future V3 SQLite migration, use the same names for shared concepts where
practical:

- `syncs`
- `sync_items`
- `part_skill_mappings`
- `test_assignments`
- `question_types`
- `question_options`
- `omr_templates`
- `scan_sessions`
- `omr_detections`
- `scan_verifications`
- `student_answers`
- `test_results`

Mobile-only support tables may remain mobile-only, including `schema_versions` and
`download_snapshots`. Offline retry fields such as `is_synced`,
`sync_attempt_count`, `last_sync_error`, and `last_synced_at` remain Mobile SQLite
responsibility unless the final API contract requires a central equivalent.

## Analytics Rule Ownership

- `performance_rule_sets` is central-only authoritative data; Mobile must not
  calculate an official performance status, mastery status, or intervention.
- The current system baseline uses Maintain at 80% or higher, Review at 60% or
  higher, Reteach at 40% or higher, and Priority Intervention below 40%.
- Mobile may retain the returned `performance_rule_set_id`, rule UUID/version, and
  backend-computed status snapshots for offline display and audit only.
- Official analytics must use finalized verified answers and backend-computed
  points. Raw OMR detections and local synchronization events are not analytics.

## Implemented Mobile-Facing Backend Slice

- V3 authentication, registration, bearer sessions, and teacher-only RBAC
- `GET /api/v3/mobile/reference-data`
- `GET /api/v3/mobile/download`
- `GET /api/v3/mobile/test-assignments/{assignmentUuid}/answer-sheets/{answerSheetUuid}/manifest`
- Shared download, manifest, scan-page metadata, and retry-response DTOs
- Machine-readable JSON contract fixtures under
  `src/test/resources/contracts/v3/mobile`
- Token-derived teacher/school ownership filtering and answer-key exclusion

The exact runtime contract is documented in `V3_MOBILE_API_CONTRACT.md`.

## Not Yet Implemented

- V3 assessment authoring and generated answer-sheet runtime
- V3 scan-page upload persistence and idempotency transaction
- V3 scan verification, scoring, analytics, and export APIs
- Mobile SQLite V3 migration
- Mobile authorization-header integration for V3 sync
- TOTP authenticator runtime
- SMS OTP provider
- OCR/manual scoring runtime
- Dynamic 5-or-more-item, multi-page answer-sheet templates for A4, US Letter,
  and US Legal

Continue using the working V1 mobile flow until the V3 APIs and DTOs are implemented,
tested, and approved. Mobile AI may design the migration against this baseline, but
must not modify production SQLite or switch endpoints yet.

## Backend Migration Started

The isolated Spring Boot V3 runtime foundation is now implemented and locally
validated. This does not expose business workflows yet.

- Profile: `v3`
- Default local port: `8082`
- Database: `performance_assessment_v3_db`
- Read-only endpoint: `GET /api/v3/system/readiness`
- Startup is rejected when the database name, 66-table baseline, five active
  question types, active OMR template, or four active performance rule sets do not
  match the approved baseline.
- All legacy V1/V2 endpoints are denied while the `v3` profile is active.
- Live readiness: HTTP 200, 16 of 16 checks passed.
- Full Maven result at this milestone: 133 tests, 0 failures, 0 errors, 0 skipped.

Mobile must not call the readiness endpoint as a replacement for download/sync.
V3 authentication/RBAC and the read-only Mobile reference/download/manifest
slice are now implemented. The scan-page upload DTO and retry fixtures are
published, but the POST endpoint deliberately remains unavailable until evidence
persistence and idempotency are implemented transactionally.

## Applied Local Dynamic-Sheet Schema Package

The approved additive 60-to-66-table schema delta and exact region seed for the
already validated `OMR-A4-10-MC-CTX-V2` are applied only to local central V3. The
historical filenames retain their `DRAFT` suffix, but the controlled execution is
recorded in `V3_LOCAL_DYNAMIC_MIGRATION_EXECUTION_2026-08-30.md`. Mobile must
continue treating A4 dynamic, US Letter, US Legal, mixed-type, written-response,
and multi-page scanner maps as unavailable. Do not migrate production SQLite or
switch endpoints until V3 APIs, shared DTO fixtures, and each physical map pass
their own acceptance tests.
