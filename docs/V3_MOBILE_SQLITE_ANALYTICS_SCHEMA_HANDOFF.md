# V3 Mobile SQLite Analytics Schema Handoff

Date: September 7, 2026  
Status: Mobile design and schema-alignment handoff only  
Scope: Update Mobile SQLite planning so V3 scan/sync data can later produce backend analytics.  

## Documentation Continuity

This document is a continuation of the previous V3 database, mobile contract, dynamic answer-sheet, and analytics planning documents. It does not replace the Data Dictionary, ERD, or migration scripts by itself.

Its purpose is to give Mobile AI a current, practical SQLite update guide for analytics readiness, while the attached current `performance_assessment_v3_db` SQL export remains the actual schema baseline.

## Important Rule

Do not treat this document as permission to switch production Mobile SQLite, replace the scanner, or upload real V3 scan data yet.

This handoff explains what Mobile SQLite must be able to store and preserve so that analytics can work after the backend V3 sync/scoring endpoints are completed.

## Authoritative Reference Order

Use the SQL export that the owner will attach from the current local database as the primary reference:

```text
performance_assessment_v3_db
```

If the attached phpMyAdmin/XAMPP SQL export conflicts with any older repo document, follow the attached SQL export and report the contradiction.

Secondary backend references:

```text
docs/performance_assessment_v3_schema.sql
docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql
docs/migrations/v3/V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql
docs/V3_MOBILE_API_CONTRACT.md
docs/V3_REPORTS_API.md
docs/V3_AUTHORITATIVE_SCORING_API.md
docs/V3_ASSESSMENT_CREATION_API.md
docs/V3_SCHOOL_SETUP_SF1_API.md
```

Reason: `performance_assessment_v3_schema.sql` is useful, but some newer dynamic answer-sheet/mobile entities are represented in the V3 migration files and in the current live database. The exported SQL file should settle the final current table/column list.

## Current Backend Truth

The active V3 backend profile is:

```text
Profile: v3
Base URL: http://localhost:8082
Database: performance_assessment_v3_db
```

Recent readiness validation reported:

```text
66 central V3 tables
158 foreign keys
80 check constraints
96 unique constraints
5 active question types
3 paper sizes
1 active approved OMR template
4 active performance rule sets
```

Only the fixed A4 10-item multiple-choice template is currently considered physically validated for production-style scanning:

```text
OMR-A4-10-MC-CTX-V2
```

Dynamic A4, US Letter, and US Legal mixed-type answer sheets are design/schema-ready, but they must still pass physical phone-and-print validation before being treated as production templates.

## Why Mobile SQLite Must Change

Analytics cannot be tested correctly if Mobile only uploads raw scan events or old V1-style result rows.

Official analytics in V3 must come from this chain:

```text
Mobile scan/capture evidence
-> Teacher verification/scoring
-> student_answers
-> Backend authoritative scoring
-> test_results
-> part_skill_mappings / skills / competency_tags
-> V3 reports and analytics
```

Raw `omr_detections` are not official analytics data. They are evidence only.

The backend must receive enough mobile data to reconstruct:

- who took the assessment,
- which class enrollment the learner belonged to,
- which assigned assessment was answered,
- which sheet/page/region was scanned,
- which objective marks were detected,
- which written responses were captured,
- which answers were verified or scored,
- which retries are duplicates,
- and which finalized result should count in analytics.

## Core Identity Rules

Mobile must keep these identifiers separate. Do not merge or substitute them.

| Identifier | Meaning | Mobile Rule |
|---|---|---|
| `school_id` | School owner/scope | Comes from backend download/auth context. |
| `user_id` | Teacher/principal account | Backend derives from Bearer token; mobile stores only for cache/display. |
| `student_id` | Learner identity | Same student may move classes over time. |
| `class_id` | Grade-level + section + academic-year cohort | Not the same as teacher assignment. |
| `class_assignment_id` | Teacher + subject + class assignment | This is what a teacher owns. |
| `class_list_id` | Student enrollment/membership in a class | This is the learner's assessment membership. |
| `test_id` | Assessment content | Questions, parts, instructions, status. |
| `test_assignment_id` | Scheduled/delivered assessment to a class assignment | Use this for mobile capture/sync context. |
| `test_part_id` | Assessment section/part | One part has one question type. |
| `question_id` | Individual item | Used by answers, detections, scoring. |
| `answer_sheet_version_id` | Generated sheet package | Immutable printed-sheet version. |
| `answer_sheet_page_id` | One physical page identity | Used for QR/page matching. |
| `answer_sheet_region_id` | One question/response area on a page | Used for detection/crop mapping. |

For offline retry and idempotency, Mobile should also store UUIDs for all locally created upload records.

## Required Mobile SQLite Groups

Mobile SQLite should not blindly copy every central table as writable. Classify the local schema into four groups.

## 1. Mobile-Only Operational Tables

These tables exist only on the device.

### `schema_versions`

Purpose: Track Mobile SQLite migration version.

Recommended fields:

```text
schema_version_id
version_code
version_name
applied_at
notes
```

### `download_snapshots`

Purpose: Track each successful backend download so Mobile can replace cached reference/test data atomically.

Recommended fields:

```text
download_snapshot_id
snapshot_uuid
teacher_user_id
school_id
download_type
backend_base_url
backend_profile
payload_hash
downloaded_at
applied_at
status
error_message
```

Rules:

- Apply a downloaded snapshot inside one SQLite transaction.
- Do not partially replace assessment/class data.
- If validation fails, keep the previous usable snapshot.

### Local retry columns

Most local upload-related tables should include:

```text
local_id
central_id nullable
uuid
is_synced
sync_status
sync_attempt_count
last_sync_error
last_synced_at
created_at
updated_at
```

Use local integer IDs for SQLite relations, but preserve backend numeric IDs and UUIDs exactly.

## 2. Shared Read-Only Download Cache

These tables should be downloaded from backend and treated as read-only on Mobile, except for local cache metadata.

### School and teacher context

Cache:

```text
school_profiles
users / teacher profile summary
roles
statuses
```

Mobile should not create school, teacher, role, or account-status records offline.

### Academic and class context

Cache:

```text
academic_years
term_periods
grade_levels
sections
subjects
classes
class_assignments
class_assignment_schedules
class_lists
students
```

Rules:

- `classes` represent grade-section-year cohorts.
- `class_assignments` represent teacher-subject ownership.
- `class_lists` represent learner membership.
- A student can appear in historical class-list records, but only one active enrolled class for the same active academic year should be used for current assessment capture.

### Assessment content

Cache:

```text
tests
test_assignments
test_parts
questions
question_options
question_types
part_skill_mappings
skills
competency_tags
root_tags
```

Rules:

- Mobile must use `test_assignment_id` for an assessment assigned to a teacher's class.
- A `test` is content; a `test_assignment` is delivery/schedule.
- A `test_part` has one question type.
- `part_skill_mappings` maps item ranges to skills for analytics.
- Mobile should not edit competency/skill mappings.

### Answer-sheet manifest cache

Cache once backend provides these in the download/manifest contract:

```text
paper_sizes
omr_templates
omr_template_regions
answer_sheet_versions
answer_sheet_pages
answer_sheet_regions
```

Rules:

- These define immutable scanner geometry.
- Mobile must not stretch one paper layout to another paper size.
- A4, US Letter, and US Legal need separate geometry.
- Every page has its own QR/page identity.
- Every answer region must map back to a question.

## 3. Local Capture, Evidence, and Verification Tables

These are created or updated on Mobile before upload.

### `scan_sessions`

Purpose: One learner's scan attempt for one assigned assessment.

Important fields to preserve:

```text
scan_uuid
answer_sheet_version_id
omr_template_id
test_assignment_id
class_list_id
device_identifier
template_version
scanner_version
image_hash
expected_page_count
received_page_count
scan_status
failure_code
failure_detail
scanned_at
verified_at
```

Recommended mobile status values:

```text
captured
processing
needs_verification
accepted
rescan_requested
rejected
superseded
failed
```

### `scan_pages`

Purpose: One captured physical page within a scan session.

Important fields:

```text
scan_page_uuid
scan_uuid / scan_session_id
answer_sheet_page_id
omr_template_id
page_number
capture_number
scanner_version
qr_payload
qr_payload_hash
image_hash
captured_rotation_degrees
page_status
failure_code
failure_detail
supersedes_scan_page_uuid
captured_at
```

Rules:

- Multi-page assessments need one `scan_pages` row per physical page.
- A rescan should create a new page record and point to the superseded page.
- Do not overwrite original evidence.

### `omr_detections`

Purpose: Raw objective mark detection evidence for MC/TF regions.

Important fields:

```text
detection_uuid
scan_uuid / scan_session_id
scan_page_uuid
answer_sheet_region_id
question_id
detected_option
confidence_score
detection_status
raw_mark
detected_at
```

Status values:

```text
detected
blank
multiple_marks
uncertain
```

Rules:

- `detected` means scanner found a clear answer.
- `blank`, `multiple_marks`, and `uncertain` are not official answers.
- Do not convert raw detections directly into analytics.

### `answer_attachments`

Purpose: Preserve full-sheet images and written-response crops.

Important fields:

```text
attachment_uuid
student_answer_uuid nullable
scan_uuid / scan_session_id
scan_page_uuid
answer_sheet_region_id
attachment_type
storage_provider
storage_key
mime_type
file_size_bytes
content_hash
retention_policy_code
crop_coordinates
captured_at
```

Attachment types should support:

```text
full_sheet
answer_crop
enhanced_answer_crop
teacher_evidence
```

Rules:

- Full sheet image is page/session evidence.
- Written response crops are attached to the related answer region/question.
- Preserve original and enhanced evidence if the scanner produces both.

### `student_answers`

Purpose: Local draft/final answer records that will become official only after backend validation/scoring.

Important fields:

```text
answer_uuid
result_uuid
question_id
selected_question_option_id
response_text
capture_source
answer_status
evaluation_status
is_correct nullable
points_earned nullable
teacher_feedback
verified_by_user_id nullable
verified_at
finalized_at
reopened_at
score_version
```

Answer statuses:

```text
answered
blank
multiple
uncertain
invalid
pending_manual
```

Evaluation statuses:

```text
pending_verification
needs_manual_scoring
scored
finalized
```

### `answer_verifications`

Purpose: Append-only teacher verification/scoring history.

Important fields:

```text
verification_uuid
answer_uuid
verified_by_user_id
verification_action
previous_answer_status
previous_answer_value
new_answer_status
new_answer_value
previous_points
new_points
reason_code
reason_detail
evidence_attachment_uuid
verified_at
```

Rules:

- Do not silently replace answer evidence.
- Store why a teacher verification/scoring action happened.
- For MC/TF, detected answers should be protected. Teacher should not replace a clear detected objective answer.
- For uncertain/blank/multiple MC/TF, teacher should request rescan or mark verification outcome according to approved policy, not invent a student answer.
- For identification, enumeration, and essay, teacher scoring is required.

### `answer_rubric_scores`

Purpose: Per-criterion scoring for essay or rubric-based answers.

Important fields:

```text
answer_rubric_score_uuid / local id
answer_uuid
rubric_criterion_id
answer_verification_uuid
scored_by_user_id
score_version
points_awarded
maximum_points_snapshot
criterion_feedback
created_at
```

## 4. Local Result and Sync Queue Tables

### `test_results`

Purpose: One learner attempt for one assigned assessment.

Important fields:

```text
result_uuid
test_assignment_id
class_list_id
attempt_number
total_score nullable
max_score nullable
items_evaluated
result_status
submitted_at
verification_completed_at
scored_at
finalized_at
percentage_snapshot
performance_status
performance_rule_set_id
score_version
checked_at
```

Rules:

- Mobile may hold local draft scores for UX, but official score must come from backend.
- Backend must recompute and validate `total_score <= max_score`.
- Analytics should count one latest non-superseded finalized result per learner/test assignment.

### `syncs`

Purpose: Upload/download batch identity and retry state.

Important fields:

```text
sync_uuid
user_id
test_assignment_id
device_identifier
direction
sync_status
payload_hash
retry_count
last_retry_at
request_item_count
idempotency_version
started_at
completed_at
error_message
```

Sync statuses:

```text
pending
in_progress
partial_success
success
failed
```

### `sync_items`

Purpose: Per-result upload outcome inside a sync.

Important fields:

```text
sync_uuid / sync_id
result_uuid
test_result_id nullable
sync_action
sync_status
attempt_count
last_attempt_at
processed_at
error_code
error_message
synced_at
```

Rules:

- `sync_action` should currently be `upsert`.
- Mobile must preserve the same `sync_uuid`, `result_uuid`, `scan_uuid`, `scan_page_uuid`, `answer_uuid`, `attachment_uuid`, and `verification_uuid` across retries.
- Retrying the same upload must not create duplicate backend rows.
- Six students uploaded four times must remain six logical results, not twenty-four analytics records.

## Question-Type Handling

V3 supports five question types:

```text
multiple_choice
true_false
identification
enumeration
essay
```

### Multiple Choice

Mobile responsibility:

- Detect objective bubble mark.
- Preserve raw detection status and confidence.
- Preserve full-sheet image/page evidence.
- Do not expose the answer key.

Teacher verification rule:

- Clear detected answers are protected.
- Blank, multiple, and uncertain answers require verification/rescan policy.

Backend responsibility:

- Map selected option to `question_options`.
- Score using `answer_keys`.
- Finalize `student_answers` and `test_results`.

### True/False

Display rule:

```text
T/F may be shown on paper and UI.
Backend storage remains A = True, B = False.
```

Mobile should store the detected option key, not a free-text True/False value.

### Identification

Mobile responsibility:

- Capture written-response crop.
- Optionally allow transcription if the approved UI supports it.
- Do not claim handwriting recognition unless explicitly validated.

Backend responsibility:

- Score using accepted answers or teacher scoring depending on question setup.

### Enumeration

Mobile responsibility:

- Capture written-response crop.
- Preserve expected response count if included in manifest.

Backend responsibility:

- Score by accepted answers, teacher verification, or future validated matching policy.

### Essay

Mobile responsibility:

- Capture written-response crop.
- Support teacher scoring controls if part of mobile verification flow.

Backend responsibility:

- Score manually or by rubric.
- Store per-criterion scores in `answer_rubric_scores` when a rubric is used.

## Data Mobile Must Not Download

The current V3 mobile download contract must not include:

```text
answer_keys
correct option flags
accepted written answers
rubric solution keys
official analytics
final class reports
```

Reason: students or devices could inspect local SQLite. Mobile should only receive what is needed for display, scanning, and capture.

If teacher-side mobile scoring later needs rubrics or accepted answers, backend must provide a separate authenticated teacher-only contract with clear offline security rules.

## Analytics Dependency Map

For analytics to appear in web reports, backend needs these central records:

```text
test_assignments
class_lists
tests
test_parts
questions
question_options
part_skill_mappings
skills
student_answers
test_results
syncs
sync_items
```

Optional but important for auditability:

```text
scan_sessions
scan_pages
omr_detections
answer_attachments
scan_verifications
answer_verifications
answer_rubric_scores
test_result_scans
```

Official reports should use:

```text
finalized test_results
finalized/scored student_answers
part_skill_mappings
skills
competency_tags
performance_rule_sets
```

Official reports should not use:

```text
raw omr_detections only
frontend-computed totals
mobile-only temporary scores
sync attempt count
draft results
superseded scans/results
```

## Minimum Mobile Schema Needed To Test Analytics Soon

If the immediate goal is to test analytics quickly, prioritize this subset first:

### Download cache

```text
download_snapshots
teacher/school summary
classes
class_assignments
class_lists
students
test_assignments
tests
test_parts
questions
question_options
question_types
part_skill_mappings
skills
competency_tags
root_tags
```

### Local answer/result capture

```text
test_results
student_answers
answer_verifications
answer_rubric_scores
answer_attachments
```

### OMR/evidence

```text
scan_sessions
scan_pages
omr_detections
answer_sheet_versions
answer_sheet_pages
answer_sheet_regions
```

### Sync queue

```text
syncs
sync_items
```

This is enough to validate the full analytics path once backend upload/scoring accepts the data.

## Backend Endpoint Reality

Currently implemented V3 mobile endpoints include:

```text
GET /api/v3/mobile/reference-data
GET /api/v3/mobile/download
GET /api/v3/mobile/test-assignments/{assignmentUuid}/answer-sheets/{answerSheetUuid}/manifest
```

All require:

```http
Authorization: Bearer <v3-session-token>
```

The scan upload endpoint remains contract/future until implemented:

```text
POST /api/v3/mobile/scan-pages
```

Do not make production Mobile depend on `POST /api/v3/mobile/scan-pages` until backend confirms it is implemented and tested.

## Expected Future Upload Shape

Mobile should prepare local data so it can produce this kind of upload later:

```json
{
  "syncUuid": "uuid",
  "syncAction": "upsert",
  "deviceIdentifier": "android-installation-id",
  "testAssignmentId": 123,
  "results": [
    {
      "resultUuid": "uuid",
      "classListId": 456,
      "attemptNumber": 1,
      "scanSession": {
        "scanUuid": "uuid",
        "answerSheetVersionId": 789,
        "templateCode": "OMR-A4-10-MC-CTX-V2",
        "scannerVersion": "mobile-scanner-version",
        "scanStatus": "needs_verification",
        "pages": [
          {
            "scanPageUuid": "uuid",
            "answerSheetPageId": 111,
            "pageNumber": 1,
            "qrPayloadHash": "64_hex_sha256",
            "imageHash": "64_hex_sha256",
            "capturedAt": "2026-09-07T00:00:00Z"
          }
        ]
      },
      "answers": [
        {
          "answerUuid": "uuid",
          "questionId": 222,
          "answerSheetRegionId": 333,
          "captureSource": "omr",
          "selectedOptionKey": "A",
          "answerStatus": "answered",
          "evaluationStatus": "pending_verification"
        }
      ]
    }
  ]
}
```

Exact DTO names and multipart/file handling must follow the final implemented backend upload endpoint once available.

## Time And Schedule Rules

Mobile should store timestamps as UTC ISO-8601 strings:

```text
2026-09-07T00:00:00Z
```

Mobile may display local Philippine time, but stored upload timestamps should remain timezone-aware.

Assessment availability should be based on backend schedule fields:

```text
open_at
close_at
allow_late_capture
assignment_status
```

Mobile should not decide official late/valid status alone. Backend remains authoritative.

## Idempotency And Duplicate Prevention

Mobile must never generate new UUIDs when retrying the same logical upload.

Preserve:

```text
sync_uuid
result_uuid
scan_uuid
scan_page_uuid
detection_uuid
answer_uuid
attachment_uuid
verification_uuid
```

Expected backend behavior:

- Same UUID + same immutable payload hash: replay/upsert safely.
- Same UUID + different immutable payload hash: reject as idempotency-key reuse.
- Duplicate network retry: no duplicate analytics.
- One learner result should count once even if sync is retried many times.

## Acceptance Checklist For Mobile SQLite Update

Mobile AI should verify:

- Fresh install creates the new SQLite schema successfully.
- Upgrade from old Mobile SQLite does not crash.
- Download snapshot saves inside one transaction.
- Teacher/class/student/test IDs remain distinct.
- Offline capture works without internet after download.
- MC and TF detections preserve raw evidence.
- Written-response crops are saved for identification, enumeration, and essay.
- Local teacher verification/scoring can be stored without losing original evidence.
- Retry queue preserves UUIDs across app restart.
- Duplicate sync retries do not create duplicate local rows.
- Backend upload is not called until the V3 upload endpoint is implemented.
- Analytics testing uses backend-finalized results, not local-only draft values.

## Message For Mobile AI

Mobile AI, please update the Mobile SQLite design against the attached current `performance_assessment_v3_db` SQL export.

Treat the attached SQL export as the current central database baseline. Use backend docs only as secondary references, especially:

```text
docs/V3_MOBILE_API_CONTRACT.md
docs/V3_REPORTS_API.md
docs/V3_AUTHORITATIVE_SCORING_API.md
docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql
docs/migrations/v3/V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql
```

Main goal: prepare Mobile SQLite so scanned/verified answers can later sync into V3 `student_answers` and `test_results`, because analytics depends on backend-finalized answers and scores.

Do not expose answer keys in the mobile download cache. Do not compute official analytics locally. Do not treat raw OMR detections as official results. Preserve scan evidence, UUIDs, hashes, page identities, answer-sheet regions, and teacher verification/scoring history.

If the current SQL export differs from this handoff, stop and report the exact table/column difference before implementing the SQLite migration.
