# V3 Adviser and Demo Requirements Report

**Date:** August 27, 2026
**Status:** Planning and contract review only
**Applies to:** Central Spring Boot/MySQL-TiDB design and Mobile SQLite/OMR design

## 1. Scope and Safety Boundary

This report records issues observed during the presentation demo and additional requirements raised by the thesis adviser, professor, and development team.

This document does **not** approve or apply a database migration. It does not modify the current Java implementation, React frontend, Mobile SQLite database, OMR detector, local MySQL database, or TiDB deployment. All proposed entities and fields below remain design candidates until the ERD, Data Dictionary, and shared API/mobile contract are reviewed and approved.

## 2. Executive Decisions

1. Re-importing an SF1 file must be incremental. A repeated file is not automatically rejected because it may contain newly added learners.
2. A learner may have only one active class/section enrollment in the same academic year, while the class may have multiple teacher-subject `class_assignments`.
3. The current validated `OMR-A4-10-MC-CTX-V2` contract remains an exact 10-item Multiple Choice A-D template. The approved V3 target starts at five total questions and supports A4, US Letter, and US Legal through separate immutable template geometry and multi-page manifests. This target does not modify the validated template and is not yet implemented.
4. Assessments need opening and closing schedules. Structural assessment editing must be restricted after distribution or result capture.
5. One reusable assessment may be delivered to multiple class assignments through a normalized assignment/delivery relationship.
6. Mobile may present result-level synchronization, but transport must retain UUID-based retry and idempotency records. A synchronization request may contain one result.
7. Multiple Choice and True/False may be machine-scored. Identification, Enumeration, and Essay require hybrid capture and mandatory teacher evaluation.
8. Multiple Choice and True/False scanner results are read-only for teachers. Teachers may accept the captured outcome or request a rescan, but they may not select or replace a learner answer. Identification, Enumeration, and Essay remain teacher-evaluated with an append-only audit trail.
9. Existing `term_periods` rows represent First through Fourth Quarter. Separate `term_one`, `term_two`, `term_three`, and `term_four` tables must not be created.
10. Student performance information should be presented through one aggregated learner profile, not duplicated into one oversized database table.
11. Backend UTC/server time is authoritative. A public external Time API is not recommended as a system dependency.

## 3. Current Source Findings

### 3.1 SF1 Import

The current V2 import already checks learners by LRN, creates missing learners, updates an existing learner's basic details, and avoids inserting the same learner twice into the same target class.

Current gaps:

- No persistent import-job or row-level import history exists.
- Existing learners are counted as updated even when no value changed.
- Existing learners do not receive a clear `existing_unchanged` warning in the response.
- Duplicate LRNs inside one uploaded file are skipped without a detailed result record.
- Membership validation checks only the selected class. It does not prevent the same learner from having active memberships in two classes under the same academic year.

### 3.2 Assessment and OMR

The current assessment service allows `multiple_choice` and `true_false`. The current fixed OMR printer and mobile map allow exactly 10 Multiple Choice questions with options A-D.

The current `tests` structure has `test_date` but no opening or closing timestamp. Each test is directly owned by one `class_assignment_id`, so the same assessment content cannot currently be assigned to three or more classes without duplicating the test.

### 3.3 Teacher Verification

The central design correctly separates raw `omr_detections` from final `student_answers`. The final V3 policy must make every Multiple Choice and True/False outcome read-only in the mobile review screen. A teacher may inspect the result, accept the scan, or request a rescan, but cannot change `detected`, `blank`, `multiple_marks`, or `uncertain` into another option.

### 3.4 Terms and Analytics

`tests.term_period_id` already separates assessments by quarter without dividing `class_assignments` by term. Current Principal analytics supports grade, section, teacher, subject, and class filters, but academic-year, term-period, and individual-assessment filters are still required for the requested school-year report.

### 3.5 Registration Verification

Email verification is implemented as the available registration contact-verification method. SMS is exposed as unavailable and still requires a provider, secure credentials, resend controls, attempt limits, and delivery auditing.

## 4. SF1 and Enrollment Requirements

### 4.1 Incremental Re-import

SF1 confirmation must evaluate every valid LRN independently and return one of these outcomes:

- `created`: learner and enrollment were created.
- `updated`: approved learner fields changed.
- `existing_unchanged`: learner already exists and no values changed.
- `already_enrolled`: learner is already in the selected class.
- `enrollment_conflict`: learner is actively enrolled in another class for the same academic year.
- `invalid`: row failed validation.

The file hash may produce a warning that the same file was previously processed, but it must not block re-import by itself. LRN and active academic-year enrollment are the authoritative duplicate checks.

### 4.2 Enrollment Integrity

A learner may have only one **active** class membership for an academic year. A transfer must be explicit, principal-authorized, transactional, and audited. The previous membership is retained as transferred or exited before the new membership becomes active.

This restriction does not prevent multiple teacher-subject assignments. The learner belongs to one `class`, while multiple `class_assignments` may reference that class.

## 5. Assessment Delivery and Scheduling

### 5.1 Dynamic Answer Sheet Generation

The existing `OMR-A4-10-MC-CTX-V2` name and geometry remain unchanged as the physically validated baseline. The approved V3 target generates one or more pages for assessments with at least five total questions. A4, US Letter, and US Legal use independent immutable geometry and must never be produced by stretching another paper size.

Every generated page requires its own four markers, QR/page identity, exact template version, and question-to-region manifest. The backend must not generate a sheet unless every objective bubble and written-response region has a compatible activated page template.

The full schema/API impact and remaining physical-validation gates are recorded in `V3_DYNAMIC_ANSWER_SHEET_DESIGN_CONTRACT.md`.

### 5.2 Assessment Schedule

Each assessment delivery should have timezone-aware `opens_at` and `closes_at` values. Recommended behavior:

- Before `opens_at`: unavailable to mobile download/capture.
- Between `opens_at` and `closes_at`: available.
- After `closes_at`: new capture is closed; existing data remains viewable.
- Principal or authorized teacher override requires a reason and audit event.

Editing rules must be separated from availability:

- Draft assessments are structurally editable.
- Once downloaded or used for a learner result, questions, options, mappings, and answer keys are locked.
- Harmless metadata and schedule changes may remain editable under authorization.
- Content correction after distribution requires a new version, not a silent overwrite.

### 5.3 Teacher Class Timetable and Schedule-Aware Due Dates

An authenticated teacher may maintain the real meeting timetable only for an
active `class_assignment` that belongs to the teacher and school. The editable
schedule includes the meeting day, local start and end time, effective date
range, and `Asia/Manila` display timezone. Every change requires an audit event.

This permission does not allow the teacher to replace `class_id`, `subject_id`,
`user_id`, assignment role, or assignment status. Those fields remain under
Principal-controlled class assignment management.

Assessment schedule validation must use the selected class-assignment context:

- `open_at` must be earlier than `close_at`;
- both values must belong to the selected academic year and term period;
- the delivery must remain inside the effective class-assignment period;
- overlapping timetable entries for the same teacher must be rejected;
- a due date outside a normal meeting slot should produce a confirmation
  warning, not an automatic rejection, because homework may be due outside
  classroom hours;
- the backend remains authoritative and stores timestamps in UTC while the UI
  displays the teacher's local `Asia/Manila` date and time.

The recommended normalized entity is `class_assignment_schedules`, because one
class assignment may meet on multiple days or time slots. This is a pending
schema/API/runtime slice and must not be simulated only in the frontend.

### 5.4 One Test Across Multiple Classes

Assessment content should be separated from class delivery. A proposed `test_assignments` entity connects one test to each authorized `class_assignment` and stores delivery-specific schedule and status.

For three classes, the system creates three delivery rows while reusing the same assessment content. Results and mobile downloads identify the specific `test_assignment_id` so learners and analytics cannot be mixed across classes.

## 6. Question Types and Hybrid Scanning

### 6.1 Supported Question-Type Direction

| Question type | Capture method | Scoring authority |
|---|---|---|
| Multiple Choice | Bubble detection | Backend answer key after read-only scan acceptance |
| True/False | Two-option bubble detection, A=True and B=False | Backend answer key after read-only scan acceptance |
| Identification | Scanned response crop with optional OCR suggestion | Teacher-confirmed accepted answer |
| Enumeration | Scanned response crop with optional OCR term suggestions | Teacher confirmation against required terms and scoring rule |
| Essay | Scanned response image | Teacher rubric and manual score |

OpenCV is suitable for marker detection, alignment, perspective correction, image cleanup, and answer-region cropping. OpenCV alone does not understand handwriting. OCR can produce a text suggestion, but handwritten identification and enumeration may be inaccurate. Essay responses must not be automatically graded as correct or incorrect.

The recommended reference entity is `question_types`, not separate tables for each term or a free-text `part_types` field. A test part may reference one `question_type_id` when all questions in that part use the same capture and scoring behavior.

## 7. Objective Scan Acceptance and Manual Response Evaluation

### 7.1 Multiple Choice and True/False

The teacher may view every scanner state, but cannot edit any objective answer:

- `detected`: retain the scanner-selected option; backend scoring uses it after scan acceptance;
- `blank`: retain a blank answer and award zero points;
- `multiple_marks`: retain the multiple-mark result as invalid and award zero points;
- `uncertain`: do not create an official selected option; require a rescan before final scoring.

The mobile and backend APIs must not expose an objective-answer replacement field to the teacher. Accepting a scan confirms only that the displayed sheet and learner/test context are correct; it does not authorize changing an option.

If the scanner is genuinely wrong, the remedy is a new `scan_session`. The original image, detections, confidence values, and scan status remain immutable. The new scan is linked through rescan/supersession history, and the backend selects the authoritative scan without modifying the old evidence.

### 7.2 Identification, Enumeration, and Essay

Teacher interaction is allowed because these responses require transcription, OCR confirmation, accepted-answer evaluation, rubric scoring, or manual scoring. Every transcription, OCR correction, score, reopen, and finalization must record the teacher, timestamp, reason where applicable, before/after value, and retained image evidence.

After finalization, non-objective answers are locked. A later change requires explicit authorization, a reopen reason, and a new append-only `answer_verifications` row.

## 8. Synchronization Contract Direction

The mobile user experience may offer synchronization per learner result instead of forcing the teacher to upload every checked learner at once. The backend should still retain `syncs` and `sync_items` because they provide retry tracking, partial-failure reporting, and auditability.

A one-result upload is valid. Retrying it must reuse the same `sync_uuid`, `result_uuid`, `scan_uuid`, and `answer_uuid` values. Repeated upload attempts must upsert the same central records instead of creating duplicate analytics rows.

Uploads for different `test_assignment_id` values must not be mixed in one request. When the same test is delivered to three classes, mobile performs separate synchronized requests per delivery/class context.

## 9. SMS Verification

SMS registration verification remains a proposed enhancement. It requires an approved provider and must include:

- normalized Philippine contact numbers;
- hashed OTP values;
- short expiration;
- one-time use;
- resend cooldown;
- maximum attempts and rate limits;
- provider delivery status;
- environment-based credentials;
- masked contact details in responses and logs.

A generalized `verification_challenges` design is preferred over maintaining unrelated email-only and SMS-only OTP logic. Registration contact verification remains separate from optional future login two-factor authentication.

## 10. Term Automation and School-Year Reports

`term_periods` should continue to contain four rows per academic year. Proposed additions are `starts_at`, `ends_at`, and optional manual-override metadata. Only one term should be active for an academic year at a time.

The backend may activate or complete terms based on server time, while Principal override actions remain protected and audited. Class assignments remain academic-year based; tests and test assignments reference the applicable term.

Principal reporting should support these filters:

- `academicYearId`
- `termPeriodId`
- `gradeLevelId`
- `classId`
- `classAssignmentId`
- `teacherUserId`
- `subjectId`
- `testId`
- `studentId`, where applicable

Most reports should be calculated from verified results and mappings. New report tables are unnecessary unless a frozen historical snapshot or export audit is explicitly required.

## 11. Student Performance Profile

The requested learner view is a Student 360 aggregation, not a single replacement table. It should combine:

- learner identity and current enrollment status;
- active, transferred, dropped, completed, or graduated status history;
- assessment and test-part scores;
- competency and skill mastery;
- performance trend by term and academic year;
- intervention recommendations, actions, and progress;
- calculation evidence and rule/threshold used.

The system may classify a learner as `Mastered`, `Developing`, `Needs Support`, or `At Risk` using approved, explainable rules. It must not automatically declare that a learner should fail when the system contains only assessment evidence and does not contain the complete official grading basis.

## 12. Time Authority

The central backend and database must store timestamps in UTC and return timezone-aware values. User interfaces may display them in `Asia/Manila`.

A public Time API should not be required because it adds network availability and trust dependencies. Server time synchronized through the hosting environment or NTP is the central authority. Mobile stores the downloaded schedule for offline use, but exact deadline enforcement while completely offline cannot be guaranteed against device-clock changes. The backend performs the final schedule validation when data is synchronized.

## 13. Proposed Data-Model Changes

All entries in this section are **proposed only**.

### New Entity Candidates

| Entity | Purpose |
|---|---|
| `sf1_imports` | Import operation, source file metadata, file hash, owner, and timestamps |
| `sf1_import_items` | Per-row created/updated/warning/conflict/invalid outcome |
| `test_assignments` | Reusable test delivery to one or more class assignments |
| `omr_templates` | Versioned fixed-template metadata and supported capabilities |
| `question_types` | Capture, scoring, OMR, OCR, and manual-review capabilities |
| `question_options` | Normalized options for objective questions |
| `accepted_answers` | Accepted text answers and matching rules for identification/enumeration |
| `answer_attachments` | References to scanned answer crops or response images |
| `scan_verifications` | Append-only scan acceptance, rejection, and rescan/supersession decisions without editing objective answers |
| `answer_verifications` | Append-only transcription, OCR review, rubric/manual scoring, and correction history for non-objective answers only |
| `rubrics` | Essay/manual-scoring rubric header |
| `rubric_criteria` | Individual rubric criteria and maximum points |
| `verification_challenges` | Shared email/SMS OTP lifecycle and delivery audit |
| `student_intervention_cases` | Learner intervention case and lifecycle |
| `student_intervention_updates` | Teacher actions, notes, outcomes, and follow-up history |
| `performance_rule_sets` | Approved, versioned mastery and performance thresholds |

### Proposed Field Inventory for New Entities

The following fields are **candidate Data Dictionary fields only**. Types, nullability, indexes, and status values must be approved in the final ERD and Data Dictionary before any migration is written.

#### `sf1_imports`

| Field | Proposed type | Key or rule |
|---|---|---|
| `sf1_import_id` | `BIGINT UNSIGNED` | Primary key |
| `import_uuid` | `CHAR(36)` | Unique client/server idempotency identifier |
| `school_id` | `VARCHAR(20)` | FK to `school_profiles.school_id` |
| `academic_year_id` | `INT UNSIGNED` | FK to `academic_years.academic_year_id` |
| `target_class_id` | `BIGINT UNSIGNED NULL` | FK to `classes.class_id`; null during preview if unresolved |
| `uploaded_by_user_id` | `BIGINT UNSIGNED` | FK to `users.user_id` |
| `source_file_name` | `VARCHAR(255)` | Original file name for audit display |
| `source_file_hash` | `CHAR(64)` | SHA-256 duplicate-file warning key; not a hard uniqueness constraint |
| `detected_school_year` | `VARCHAR(20) NULL` | Parsed source value snapshot |
| `detected_grade_level` | `VARCHAR(30) NULL` | Parsed source value snapshot |
| `detected_section_name` | `VARCHAR(100) NULL` | Parsed source value snapshot |
| `import_status` | `ENUM` | `previewed`, `processing`, `completed`, `partial_success`, `failed`, `cancelled` |
| `total_row_count` | `INT UNSIGNED` | Number of learner rows read |
| `created_student_count` | `INT UNSIGNED` | Newly created learners |
| `updated_student_count` | `INT UNSIGNED` | Existing learners with approved profile updates |
| `unchanged_student_count` | `INT UNSIGNED` | Existing learners requiring no change |
| `conflict_row_count` | `INT UNSIGNED` | Enrollment or ownership conflicts |
| `invalid_row_count` | `INT UNSIGNED` | Invalid rows |
| `started_at` | `TIMESTAMP` | UTC processing start |
| `completed_at` | `TIMESTAMP NULL` | UTC completion time |
| `created_at` | `TIMESTAMP` | Record creation time |

#### `sf1_import_items`

| Field | Proposed type | Key or rule |
|---|---|---|
| `sf1_import_item_id` | `BIGINT UNSIGNED` | Primary key |
| `sf1_import_id` | `BIGINT UNSIGNED` | FK to `sf1_imports.sf1_import_id` |
| `row_number` | `INT UNSIGNED` | Unique with `sf1_import_id` |
| `student_lrn_snapshot` | `VARCHAR(20)` | LRN read from the source row |
| `source_row_hash` | `CHAR(64)` | Detects whether a repeated row changed |
| `student_id` | `BIGINT UNSIGNED NULL` | FK to `students.student_id` when resolved |
| `class_list_id` | `BIGINT UNSIGNED NULL` | FK to `class_lists.class_list_id` when enrolled |
| `outcome_status` | `ENUM` | `created`, `updated`, `existing_unchanged`, `already_enrolled`, `enrollment_conflict`, `invalid` |
| `warning_code` | `VARCHAR(50) NULL` | Stable machine-readable warning/error code |
| `outcome_message` | `VARCHAR(255) NULL` | Human-readable import result |
| `processed_at` | `TIMESTAMP` | UTC row-processing time |

#### `test_assignments`

| Field | Proposed type | Key or rule |
|---|---|---|
| `test_assignment_id` | `BIGINT UNSIGNED` | Primary key |
| `assignment_uuid` | `CHAR(36)` | Unique public/idempotency identifier |
| `test_id` | `BIGINT UNSIGNED` | FK to `tests.test_id` |
| `class_assignment_id` | `BIGINT UNSIGNED` | FK to `class_assignments.class_assignment_id`; unique with `test_id` |
| `assigned_by_user_id` | `BIGINT UNSIGNED` | FK to `users.user_id` |
| `open_at` | `TIMESTAMP NULL` | UTC availability start |
| `close_at` | `TIMESTAMP NULL` | UTC availability end; must be after `open_at` |
| `assignment_status` | `ENUM` | `planned`, `open`, `closed`, `archived` |
| `allow_late_capture` | `BOOLEAN` | Whether late paper-result capture is permitted with audit |
| `assigned_at` | `TIMESTAMP` | UTC assignment time |
| `updated_at` | `TIMESTAMP` | Last update time |

#### `omr_templates`

| Field | Proposed type | Key or rule |
|---|---|---|
| `omr_template_id` | `BIGINT UNSIGNED` | Primary key |
| `template_code` | `VARCHAR(80)` | Unique immutable code, such as `OMR-A4-10-MC-CTX-V2` |
| `template_name` | `VARCHAR(120)` | Teacher-facing name |
| `question_type_id` | `SMALLINT UNSIGNED` | FK to `question_types.question_type_id` |
| `page_size` | `VARCHAR(20)` | Approved paper size, initially `A4` |
| `page_orientation` | `ENUM` | `portrait` or `landscape` |
| `minimum_item_count` | `SMALLINT UNSIGNED` | Minimum supported item count |
| `maximum_item_count` | `SMALLINT UNSIGNED` | Maximum supported item count |
| `option_count` | `TINYINT UNSIGNED` | Number of bubbles per item |
| `geometry_definition` | `JSON` | Versioned marker, bubble, and QR coordinates |
| `qr_payload_version` | `SMALLINT UNSIGNED` | QR contract version |
| `minimum_scanner_version` | `VARCHAR(50)` | Oldest compatible mobile scanner version |
| `template_status` | `ENUM` | `draft`, `active`, `retired` |
| `created_by_user_id` | `BIGINT UNSIGNED` | FK to `users.user_id` |
| `activated_at` | `TIMESTAMP NULL` | UTC activation time |
| `created_at` | `TIMESTAMP` | Record creation time |
| `updated_at` | `TIMESTAMP` | Last update time |

#### `question_types`

| Field | Proposed type | Key or rule |
|---|---|---|
| `question_type_id` | `SMALLINT UNSIGNED` | Primary key |
| `question_type_code` | `VARCHAR(40)` | Unique stable code: `multiple_choice`, `true_false`, `identification`, `enumeration`, `essay` |
| `question_type_name` | `VARCHAR(80)` | Display name |
| `capture_mode` | `ENUM` | `omr`, `ocr`, `manual`, `hybrid` |
| `scoring_mode` | `ENUM` | `automatic`, `manual`, `hybrid` |
| `supports_omr` | `BOOLEAN` | Machine-readable capability flag |
| `supports_ocr` | `BOOLEAN` | OCR-suggestion capability flag |
| `supports_multiple_response` | `BOOLEAN` | Whether one question expects multiple response entries |
| `requires_attachment` | `BOOLEAN` | Whether scan/image evidence is mandatory |
| `requires_teacher_verification` | `BOOLEAN` | Must be true for scanner/OCR-assisted capture |
| `allows_teacher_answer_edit` | `BOOLEAN` | Must be false for Multiple Choice and True/False; may be true for non-objective review before finalization |
| `is_active` | `BOOLEAN` | Reference-data availability |
| `created_at` | `TIMESTAMP` | Record creation time |
| `updated_at` | `TIMESTAMP` | Last update time |

#### `question_options`

| Field | Proposed type | Key or rule |
|---|---|---|
| `question_option_id` | `BIGINT UNSIGNED` | Primary key |
| `question_id` | `BIGINT UNSIGNED` | FK to `questions.question_id` |
| `option_key` | `VARCHAR(10)` | Option identifier such as A, B, C, or D; unique per question |
| `option_text` | `TEXT` | Displayed option content |
| `option_order` | `SMALLINT UNSIGNED` | Unique ordering within a question |
| `is_active` | `BOOLEAN` | Supports controlled retirement without deleting history |
| `created_at` | `TIMESTAMP` | Record creation time |
| `updated_at` | `TIMESTAMP` | Last update time |

#### `accepted_answers`

| Field | Proposed type | Key or rule |
|---|---|---|
| `accepted_answer_id` | `BIGINT UNSIGNED` | Primary key |
| `question_id` | `BIGINT UNSIGNED` | FK to `questions.question_id` |
| `answer_order` | `SMALLINT UNSIGNED NULL` | Expected order for enumeration; null when order is irrelevant |
| `accepted_text` | `VARCHAR(500)` | Teacher-approved answer text or variant |
| `normalized_text` | `VARCHAR(500)` | Normalized comparison value |
| `matching_mode` | `ENUM` | `exact` or `normalized`; OCR remains a suggestion, not final truth |
| `is_case_sensitive` | `BOOLEAN` | Text-comparison rule |
| `points` | `DECIMAL(5,2)` | Points awarded for this expected answer component |
| `is_primary` | `BOOLEAN` | Marks the preferred displayed answer |
| `is_active` | `BOOLEAN` | Reference lifecycle flag |
| `created_at` | `TIMESTAMP` | Record creation time |
| `updated_at` | `TIMESTAMP` | Last update time |

#### `answer_attachments`

| Field | Proposed type | Key or rule |
|---|---|---|
| `answer_attachment_id` | `BIGINT UNSIGNED` | Primary key |
| `attachment_uuid` | `CHAR(36)` | Unique offline/idempotency identifier |
| `student_answer_id` | `BIGINT UNSIGNED NULL` | FK to `student_answers.student_answer_id`; null for a full-sheet scan attachment |
| `scan_session_id` | `BIGINT UNSIGNED NULL` | FK to `scan_sessions.scan_session_id` |
| `attachment_type` | `ENUM` | `full_sheet`, `answer_crop`, `teacher_evidence` |
| `storage_provider` | `VARCHAR(40)` | Approved private storage provider |
| `storage_key` | `VARCHAR(500)` | Private object key; never a public permanent URL |
| `mime_type` | `VARCHAR(100)` | Validated media type |
| `file_size_bytes` | `BIGINT UNSIGNED` | Validated file size |
| `content_hash` | `CHAR(64)` | SHA-256 duplicate/integrity hash |
| `crop_coordinates` | `JSON NULL` | Source-image crop coordinates when applicable |
| `captured_at` | `TIMESTAMP NULL` | Device capture time retained for audit |
| `created_at` | `TIMESTAMP` | Central record creation time |

At least one of `student_answer_id` or `scan_session_id` must be present. Full-sheet evidence links to the scan session, while item crops may link to both the scan session and final student answer.

#### `scan_verifications`

| Field | Proposed type | Key or rule |
|---|---|---|
| `scan_verification_id` | `BIGINT UNSIGNED` | Primary key |
| `verification_uuid` | `CHAR(36)` | Unique offline/idempotency identifier |
| `scan_session_id` | `BIGINT UNSIGNED` | FK to `scan_sessions.scan_session_id` |
| `verified_by_user_id` | `BIGINT UNSIGNED` | FK to `users.user_id` |
| `verification_action` | `ENUM` | `accepted`, `rescan_requested`, `rejected`, `superseded` |
| `reason_code` | `VARCHAR(50) NULL` | Required for rescan, reject, or supersede actions |
| `reason_detail` | `VARCHAR(500) NULL` | Optional safe teacher explanation |
| `decided_at` | `TIMESTAMP` | UTC teacher decision time |
| `created_at` | `TIMESTAMP` | Append-only central creation time |

This table records a decision about the scan as a whole. It never stores a replacement answer option. A rescan creates another `scan_sessions` row and retains the original scan and detections.

#### `answer_verifications`

| Field | Proposed type | Key or rule |
|---|---|---|
| `answer_verification_id` | `BIGINT UNSIGNED` | Primary key |
| `verification_uuid` | `CHAR(36)` | Unique offline/idempotency identifier |
| `student_answer_id` | `BIGINT UNSIGNED` | FK to `student_answers.student_answer_id` |
| `verified_by_user_id` | `BIGINT UNSIGNED` | FK to `users.user_id` |
| `verification_action` | `ENUM` | `transcribed`, `ocr_confirmed`, `ocr_corrected`, `manual_scored`, `reopened`, `finalized` |
| `previous_answer_status` | `VARCHAR(30) NULL` | Immutable before-state snapshot |
| `previous_answer_value` | `TEXT NULL` | Immutable before-state value |
| `new_answer_status` | `VARCHAR(30)` | Immutable after-state snapshot |
| `new_answer_value` | `TEXT NULL` | Immutable after-state value |
| `previous_points` | `DECIMAL(8,2) NULL` | Before-state score snapshot |
| `new_points` | `DECIMAL(8,2)` | After-state score snapshot |
| `reason_code` | `VARCHAR(50) NULL` | Required for correction or reopen actions |
| `reason_detail` | `VARCHAR(500) NULL` | Teacher explanation; required when the reason code needs detail |
| `evidence_attachment_id` | `BIGINT UNSIGNED NULL` | FK to `answer_attachments.answer_attachment_id` |
| `verified_at` | `TIMESTAMP` | UTC decision time |

This table is append-only and applies only to Identification, Enumeration, Essay, and other approved non-objective responses. It cannot be used to replace Multiple Choice or True/False options. Updating or deleting verification-history rows is not permitted in the normal application workflow.

#### `rubrics`

| Field | Proposed type | Key or rule |
|---|---|---|
| `rubric_id` | `BIGINT UNSIGNED` | Primary key |
| `rubric_uuid` | `CHAR(36)` | Unique public identifier |
| `school_id` | `VARCHAR(20)` | FK to `school_profiles.school_id` |
| `created_by_user_id` | `BIGINT UNSIGNED` | FK to `users.user_id` |
| `rubric_name` | `VARCHAR(120)` | Teacher-facing rubric name |
| `description` | `TEXT NULL` | Rubric purpose/instructions |
| `total_points` | `DECIMAL(8,2)` | Must equal the approved sum of criterion maximum points |
| `rubric_status` | `ENUM` | `draft`, `active`, `archived` |
| `created_at` | `TIMESTAMP` | Record creation time |
| `updated_at` | `TIMESTAMP` | Last update time |

#### `rubric_criteria`

| Field | Proposed type | Key or rule |
|---|---|---|
| `rubric_criterion_id` | `BIGINT UNSIGNED` | Primary key |
| `rubric_id` | `BIGINT UNSIGNED` | FK to `rubrics.rubric_id` |
| `criterion_order` | `SMALLINT UNSIGNED` | Unique with `rubric_id` |
| `criterion_name` | `VARCHAR(120)` | Short criterion label |
| `criterion_description` | `TEXT` | Scoring expectation |
| `maximum_points` | `DECIMAL(8,2)` | Maximum criterion score |
| `level_definition` | `JSON NULL` | Approved point-level descriptors |
| `is_required` | `BOOLEAN` | Whether the criterion must be scored |
| `created_at` | `TIMESTAMP` | Record creation time |
| `updated_at` | `TIMESTAMP` | Last update time |

Criterion-level learner scoring may require a separate normalized score table. That decision remains unresolved and must be added to the final ERD if detailed rubric breakdowns are required.

#### `verification_challenges`

| Field | Proposed type | Key or rule |
|---|---|---|
| `verification_challenge_id` | `BIGINT UNSIGNED` | Primary key |
| `challenge_uuid` | `CHAR(36)` | Unique public/idempotency identifier |
| `user_id` | `BIGINT UNSIGNED` | FK to `users.user_id` |
| `verification_purpose` | `ENUM` | `teacher_registration`, `email_verification`, `contact_verification`, `sensitive_action` |
| `delivery_channel` | `ENUM` | `email` or `sms` |
| `destination_masked` | `VARCHAR(160)` | Masked audit snapshot; not the OTP recipient authority |
| `code_hash` | `VARCHAR(255)` | One-way OTP hash; never stores plain OTP |
| `challenge_status` | `ENUM` | `pending`, `verified`, `expired`, `locked`, `cancelled` |
| `delivery_status` | `ENUM` | `queued`, `sent`, `failed` |
| `delivery_provider` | `VARCHAR(50) NULL` | Email/SMS provider name |
| `provider_message_id` | `VARCHAR(120) NULL` | Provider delivery reference |
| `attempt_count` | `SMALLINT UNSIGNED` | Failed verification attempts |
| `maximum_attempt_count` | `SMALLINT UNSIGNED` | Lock threshold snapshot |
| `resend_count` | `SMALLINT UNSIGNED` | Resend counter |
| `last_sent_at` | `TIMESTAMP NULL` | Most recent delivery time |
| `next_resend_at` | `TIMESTAMP NULL` | Rate-limit/cooldown boundary |
| `expires_at` | `TIMESTAMP` | OTP expiration time |
| `verified_at` | `TIMESTAMP NULL` | Successful verification time |
| `created_at` | `TIMESTAMP` | Record creation time |
| `updated_at` | `TIMESTAMP` | Last state update time |

#### `student_intervention_cases`

| Field | Proposed type | Key or rule |
|---|---|---|
| `student_intervention_case_id` | `BIGINT UNSIGNED` | Primary key |
| `case_uuid` | `CHAR(36)` | Unique public/idempotency identifier |
| `school_id` | `VARCHAR(20)` | FK to `school_profiles.school_id` |
| `student_id` | `BIGINT UNSIGNED` | FK to `students.student_id` |
| `academic_year_id` | `INT UNSIGNED` | FK to `academic_years.academic_year_id` |
| `term_period_id` | `INT UNSIGNED NULL` | FK to `term_periods.term_period_id` |
| `class_list_id` | `BIGINT UNSIGNED` | FK to `class_lists.class_list_id` |
| `skill_id` | `BIGINT UNSIGNED NULL` | FK to `skills.skill_id` |
| `source_test_result_id` | `BIGINT UNSIGNED NULL` | FK to `test_results.test_result_id` |
| `performance_rule_set_id` | `BIGINT UNSIGNED NULL` | FK to `performance_rule_sets.performance_rule_set_id` |
| `opened_by_user_id` | `BIGINT UNSIGNED` | FK to `users.user_id` |
| `assigned_to_user_id` | `BIGINT UNSIGNED NULL` | Responsible teacher/user |
| `case_status` | `ENUM` | `open`, `in_progress`, `monitoring`, `resolved`, `closed` |
| `priority_level` | `ENUM` | `normal`, `review`, `priority` |
| `recommendation_snapshot` | `VARCHAR(500)` | Recommendation shown when the case opened |
| `opened_at` | `TIMESTAMP` | UTC case start |
| `due_at` | `TIMESTAMP NULL` | Optional target follow-up time |
| `resolved_at` | `TIMESTAMP NULL` | UTC resolution time |
| `created_at` | `TIMESTAMP` | Record creation time |
| `updated_at` | `TIMESTAMP` | Last state update time |

#### `student_intervention_updates`

| Field | Proposed type | Key or rule |
|---|---|---|
| `student_intervention_update_id` | `BIGINT UNSIGNED` | Primary key |
| `student_intervention_case_id` | `BIGINT UNSIGNED` | FK to `student_intervention_cases.student_intervention_case_id` |
| `updated_by_user_id` | `BIGINT UNSIGNED` | FK to `users.user_id` |
| `update_type` | `ENUM` | `note`, `action`, `status_change`, `follow_up`, `outcome` |
| `previous_status` | `VARCHAR(30) NULL` | Before-state snapshot when status changed |
| `new_status` | `VARCHAR(30) NULL` | After-state snapshot when status changed |
| `action_taken` | `VARCHAR(255) NULL` | Short teacher action description |
| `update_notes` | `TEXT NULL` | Teacher notes |
| `outcome_status` | `ENUM NULL` | `pending`, `improved`, `no_change`, `regressed` |
| `follow_up_at` | `TIMESTAMP NULL` | Next scheduled follow-up |
| `created_at` | `TIMESTAMP` | Append-only UTC update time |

#### `performance_rule_sets`

| Field | Proposed type | Key or rule |
|---|---|---|
| `performance_rule_set_id` | `BIGINT UNSIGNED` | Primary key |
| `rule_set_uuid` | `CHAR(36)` | Unique public identifier |
| `school_id` | `VARCHAR(20) NULL` | FK to `school_profiles.school_id`; null only for approved system defaults |
| `rule_set_name` | `VARCHAR(120)` | Rule-set display name |
| `rule_version` | `VARCHAR(30)` | Unique with `school_id` and `rule_set_name` |
| `metric_scope` | `ENUM` | `student_score`, `skill_mastery`, `class_mastery`, `intervention` |
| `rule_definition` | `JSON` | Validated ordered thresholds, statuses, and recommendation references |
| `rule_status` | `ENUM` | `draft`, `active`, `retired` |
| `approved_by_user_id` | `BIGINT UNSIGNED NULL` | FK to `users.user_id` |
| `effective_from_at` | `TIMESTAMP NULL` | UTC activation boundary |
| `effective_until_at` | `TIMESTAMP NULL` | UTC retirement boundary |
| `created_at` | `TIMESTAMP` | Record creation time |
| `updated_at` | `TIMESTAMP` | Last update time |

The final design must decide whether `rule_definition` remains a validated JSON snapshot or is normalized into a child `performance_rules` table. No unexplained threshold may be hard-coded in analytics code.

### Existing Entity Candidates for Extension

| Existing entity | Proposed direction |
|---|---|
| `users` | Reference-based suffix and month/year teaching-start precision |
| `class_lists` | Academic-year context, enrollment lifecycle, one-active-class protection, transfer metadata |
| `class_assignments` | Auditable completion/archive metadata without deleting assignments |
| `term_periods` | Start/end timestamps and controlled override metadata |
| `tests` | School-owned reusable assessment content and version metadata |
| `test_parts` | Reference an approved question type when parts are homogeneous |
| `questions` | Remove dependence on mandatory A-D columns for non-objective questions |
| `answer_keys` | Support objective and text-answer key strategies without forcing one option letter |
| `scan_sessions` | Bind scans to the approved template, assessment delivery, learner membership, and rescan history |
| `omr_detections` | Add stable raw-detection identity while preserving immutable scanner evidence |
| `student_answers` | Support selected option, teacher-confirmed text, attachment reference, and evaluation status |
| `test_results` | Reference the specific assessment delivery and retain authoritative score snapshots |
| `intervention_results` | Retain the rule, mastery evidence, recommendation snapshot, and teacher acknowledgement |
| `syncs` | Bind synchronization activity to an assessment delivery and retain retry metadata |
| `sync_items` | Retain item-attempt metadata without creating duplicate results |
| `students` | Add profile-update and global status-change audit fields; class dropping remains in `class_lists` |

### Proposed New Fields for Existing Entities

All fields below are **V11/V3 candidates only**. They do not exist in the current V2 runtime unless explicitly stated otherwise, and no migration has been applied.

#### `users`

| New field | Proposed type | Purpose or rule |
|---|---|---|
| `suffix_id` | `TINYINT UNSIGNED NULL` | FK to `suffixes.suffix_id`; replaces free-text suffix after migration |
| `teaching_start_month` | `TINYINT UNSIGNED NULL` | Month value from 1 through 12 |
| `teaching_start_year` | `SMALLINT UNSIGNED NULL` | Four-digit year; cannot precede a valid working age |

`email_verified_at` and `contact_verified_at` already exist in V2 and must not be added again. The existing `suffix` and `teaching_start_date` columns become migration/deprecation decisions, not duplicate permanent fields.

#### `class_lists`

| New field | Proposed type | Purpose or rule |
|---|---|---|
| `membership_uuid` | `CHAR(36)` | Unique stable membership identifier for central/mobile alignment |
| `academic_year_id` | `INT UNSIGNED` | FK to `academic_years`; validated against the selected `class_id` |
| `enrollment_status` | `ENUM` | `enrolled`, `transferred`, `dropped`, `completed` |
| `active_academic_year_id` | Generated `INT UNSIGNED NULL` | Equals `academic_year_id` only while enrolled; supports unique active membership per learner/year |
| `enrollment_source` | `ENUM` | `sf1`, `manual`, `transfer` |
| `source_sf1_import_id` | `BIGINT UNSIGNED NULL` | FK to `sf1_imports.sf1_import_id` |
| `previous_class_list_id` | `BIGINT UNSIGNED NULL` | Self-FK preserving transfer history |
| `enrolled_at` | `TIMESTAMP` | UTC enrollment start |
| `ended_at` | `TIMESTAMP NULL` | UTC transfer/drop/completion time |
| `status_reason` | `VARCHAR(255) NULL` | Required for transfer or drop |
| `status_changed_by_user_id` | `BIGINT UNSIGNED NULL` | FK to `users.user_id` |
| `created_at` | `TIMESTAMP` | Membership creation time |
| `updated_at` | `TIMESTAMP` | Last membership update time |

Proposed uniqueness: `UNIQUE(student_id, active_academic_year_id)`. Because inactive rows produce null for the generated value, historical memberships remain available while only one enrolled class is allowed per learner and academic year.

#### `class_assignments`

| New field | Proposed type | Purpose or rule |
|---|---|---|
| `ended_at` | `TIMESTAMP NULL` | Completion/archive effective time |
| `status_changed_by_user_id` | `BIGINT UNSIGNED NULL` | FK to the principal/user who changed status |
| `status_reason` | `VARCHAR(255) NULL` | Required for early completion or archive |

Class assignments remain academic-year scoped through `class_id`; they are not duplicated per term.

#### `term_periods`

| New field | Proposed type | Purpose or rule |
|---|---|---|
| `start_at` | `TIMESTAMP` | UTC term opening boundary |
| `end_at` | `TIMESTAMP` | UTC term closing boundary; must be after `start_at` |
| `activation_mode` | `ENUM` | `automatic` or `manual` |
| `activated_at` | `TIMESTAMP NULL` | Actual activation time |
| `completed_at` | `TIMESTAMP NULL` | Actual completion time |
| `overridden_by_user_id` | `BIGINT UNSIGNED NULL` | FK to authorized principal/user |
| `override_reason` | `VARCHAR(255) NULL` | Mandatory when dates or lifecycle are manually overridden |
| `overridden_at` | `TIMESTAMP NULL` | UTC override time |
| `created_at` | `TIMESTAMP` | Record creation time |
| `updated_at` | `TIMESTAMP` | Last update time |

Terms within one academic year must not overlap. Server UTC time is authoritative for automatic lifecycle changes.

#### `tests`

| New field | Proposed type | Purpose or rule |
|---|---|---|
| `test_uuid` | `CHAR(36)` | Unique public identifier shared with offline clients |
| `school_id` | `VARCHAR(20)` | FK to `school_profiles.school_id`; explicit reusable-content ownership |
| `created_by_user_id` | `BIGINT UNSIGNED` | FK to the teacher who owns the assessment content |
| `version_number` | `INT UNSIGNED` | Immutable content version, starting at 1 |
| `source_test_id` | `BIGINT UNSIGNED NULL` | Self-FK to the assessment version that was copied/revised |
| `published_at` | `TIMESTAMP NULL` | First activation/publication time |
| `content_locked_at` | `TIMESTAMP NULL` | Time the published version became immutable |
| `completed_at` | `TIMESTAMP NULL` | Assessment lifecycle completion time |
| `archived_at` | `TIMESTAMP NULL` | Archive time |

If `test_assignments` is approved, availability fields belong to each delivery, not to reusable test content. The current `class_assignment_id` then becomes a relationship-migration decision rather than remaining the sole owner of `tests`.

#### `test_parts`

| New field | Proposed type | Purpose or rule |
|---|---|---|
| `question_type_id` | `SMALLINT UNSIGNED` | FK to `question_types.question_type_id`; replaces free-text `part_type` after migration |
| `part_instructions` | `TEXT NULL` | Instructions specific to the test part |
| `created_at` | `TIMESTAMP` | Record creation time |
| `updated_at` | `TIMESTAMP` | Last update time |

No separate `term_one`, `term_two`, `term_three`, or `term_four` field is added. The parent test continues to reference `term_period_id`.

#### `questions`

| New field | Proposed type | Purpose or rule |
|---|---|---|
| `question_uuid` | `CHAR(36)` | Unique public/offline identifier |
| `question_type_id` | `SMALLINT UNSIGNED` | FK to `question_types.question_type_id`; supports validation even if mixed parts are later allowed |
| `maximum_points` | `DECIMAL(8,2)` | Item-level maximum score for variable-point and rubric questions |
| `rubric_id` | `BIGINT UNSIGNED NULL` | FK to `rubrics.rubric_id` for essay/manual scoring |
| `response_instructions` | `TEXT NULL` | Item-specific response guidance |
| `answer_order_required` | `BOOLEAN` | Whether enumeration answers must follow the defined order |
| `maximum_response_length` | `INT UNSIGNED NULL` | Optional validated text-response limit |
| `created_at` | `TIMESTAMP` | Record creation time |
| `updated_at` | `TIMESTAMP` | Last update time |

The existing `option_a` through `option_e` columns are not new fields. They are candidates for migration into `question_options` and must become nullable or be retired for non-objective questions.

#### `answer_keys`

| New field | Proposed type | Purpose or rule |
|---|---|---|
| `answer_key_type` | `ENUM` | `option`, `accepted_text`, `rubric`, `manual` |
| `correct_question_option_id` | `BIGINT UNSIGNED NULL` | FK to `question_options.question_option_id` for objective questions |
| `scoring_method` | `ENUM` | `exact`, `normalized`, `rubric`, `manual` |
| `rubric_id` | `BIGINT UNSIGNED NULL` | FK to `rubrics.rubric_id` when the key uses a rubric |
| `answer_explanation` | `TEXT NULL` | Teacher-facing key explanation |
| `created_at` | `TIMESTAMP` | Record creation time |
| `updated_at` | `TIMESTAMP` | Last update time |

The current `correct_option` must become nullable or be retired after option migration. Text variants remain normalized in `accepted_answers`, not duplicated in this table.

#### `scan_sessions`

| New field | Proposed type | Purpose or rule |
|---|---|---|
| `omr_template_id` | `BIGINT UNSIGNED` | FK to the exact approved `omr_templates` record |
| `test_assignment_id` | `BIGINT UNSIGNED` | FK to the assessment delivery being scanned |
| `class_list_id` | `BIGINT UNSIGNED` | FK to the selected learner membership |
| `supersedes_scan_session_id` | `BIGINT UNSIGNED NULL` | Self-FK for explicit rescan lineage |
| `failure_code` | `VARCHAR(50) NULL` | Machine-readable scan failure code |
| `failure_detail` | `VARCHAR(500) NULL` | Safe diagnostic detail |

`scan_uuid`, `template_version`, `image_hash`, scan timestamps, and scan status already exist. The string template version remains an immutable snapshot even when `omr_template_id` is added.

#### `omr_detections`

| New field | Proposed type | Purpose or rule |
|---|---|---|
| `detection_uuid` | `CHAR(36)` | Unique stable raw-detection identifier |
| `created_at` | `TIMESTAMP` | Central insertion time, separate from device detection time |

No final answer or teacher-edited value is added to this table. `detected_option`, confidence, status, and raw measurements remain immutable scanner evidence. Objective scan acceptance/rescan decisions belong to `scan_verifications`; non-objective evaluation history belongs to `answer_verifications`.

#### `student_answers`

| New field | Proposed type | Purpose or rule |
|---|---|---|
| `selected_question_option_id` | `BIGINT UNSIGNED NULL` | FK to `question_options.question_option_id` |
| `response_text` | `TEXT NULL` | Final teacher-confirmed identification/enumeration/essay response |
| `evaluation_status` | `ENUM` | `pending_verification`, `needs_manual_scoring`, `scored`, `finalized` |
| `teacher_feedback` | `TEXT NULL` | Optional learner-facing or report feedback |
| `finalized_at` | `TIMESTAMP NULL` | First finalization time |
| `reopened_at` | `TIMESTAMP NULL` | Most recent authorized reopen time |
| `score_version` | `INT UNSIGNED` | Increments after an audited score correction |
| `created_at` | `TIMESTAMP` | Central record creation time |

For objective questions, `selected_question_option_id` is populated only by the backend from the accepted immutable detection; teacher-facing APIs must reject replacement values. For non-objective questions, `is_correct` and `verified_by_user_id` require nullability changes until scoring/evaluation is complete. OCR suggestions are evidence only and must not overwrite `response_text` automatically.

#### `test_results`

| New field | Proposed type | Purpose or rule |
|---|---|---|
| `test_assignment_id` | `BIGINT UNSIGNED` | FK to the exact class delivery; prevents multi-class ambiguity |
| `result_status` | `ENUM` | `draft`, `pending_verification`, `finalized`, `superseded` |
| `submitted_at` | `TIMESTAMP NULL` | Student/paper result submission or capture time |
| `verification_completed_at` | `TIMESTAMP NULL` | Completion of mandatory teacher verification |
| `scored_at` | `TIMESTAMP NULL` | Backend authoritative-scoring time |
| `finalized_at` | `TIMESTAMP NULL` | Result lock time |
| `percentage_snapshot` | `DECIMAL(7,4) NULL` | Auditable percentage produced from score/max score |
| `performance_status` | `VARCHAR(50) NULL` | Status produced by the approved rule version |
| `performance_rule_set_id` | `BIGINT UNSIGNED NULL` | FK to `performance_rule_sets.performance_rule_set_id` |
| `score_version` | `INT UNSIGNED` | Increments after verified corrections and rescoring |

The attempt uniqueness changes from `(test_id, class_list_id, attempt_number)` to `(test_assignment_id, class_list_id, attempt_number)` when multi-class delivery is approved.

#### `intervention_results`

| New field | Proposed type | Purpose or rule |
|---|---|---|
| `performance_rule_set_id` | `BIGINT UNSIGNED` | FK to the exact approved rules used |
| `mastery_rate_snapshot` | `DECIMAL(7,4)` | Mastery evidence that triggered the recommendation |
| `recommendation_status` | `ENUM` | `maintain`, `review`, `reteach`, `priority_intervention` |
| `recommendation_snapshot` | `VARCHAR(500)` | Teacher-facing recommendation at generation time |
| `generated_at` | `TIMESTAMP` | UTC recommendation-generation time |
| `acknowledged_by_user_id` | `BIGINT UNSIGNED NULL` | FK to teacher/user who reviewed it |
| `acknowledged_at` | `TIMESTAMP NULL` | UTC acknowledgement time |

#### `syncs`

| New field | Proposed type | Purpose or rule |
|---|---|---|
| `test_assignment_id` | `BIGINT UNSIGNED NULL` | FK to the exact assessment delivery; required for result upload |
| `retry_count` | `SMALLINT UNSIGNED` | Number of repeated requests using the same `sync_uuid` |
| `last_retry_at` | `TIMESTAMP NULL` | Most recent retry time |
| `request_item_count` | `INT UNSIGNED` | Number of results contained in the request |
| `idempotency_version` | `SMALLINT UNSIGNED` | Upload/idempotency contract version |

`sync_uuid`, `partial_success`, payload hash, and actual server timestamps already exist. A one-result upload may still use this envelope with `request_item_count = 1`.

#### `sync_items`

| New field | Proposed type | Purpose or rule |
|---|---|---|
| `attempt_count` | `SMALLINT UNSIGNED` | Processing attempts for this result inside the retained sync record |
| `last_attempt_at` | `TIMESTAMP NULL` | Most recent processing time, successful or failed |
| `processed_at` | `TIMESTAMP NULL` | Final item-processing completion time |

The existing unique key `(sync_id, result_uuid)` and central `test_result_id` link remain authoritative. Repeated uploads must upsert by UUID rather than insert another learner result.

#### `students`

| New field | Proposed type | Purpose or rule |
|---|---|---|
| `status_reason` | `VARCHAR(255) NULL` | Reason for a school-wide learner master-status change |
| `status_effective_at` | `TIMESTAMP NULL` | Effective global status-change time |
| `status_changed_by_user_id` | `BIGINT UNSIGNED NULL` | FK to authorized user |
| `updated_at` | `TIMESTAMP` | Last profile/master-record update time |

`dropped` is not added to the global `students.status` solely for leaving one class. Transfer/drop/completion for a specific academic year belongs to `class_lists.enrollment_status`, preserving the learner master record and historical results.

### Existing-Column and Constraint Changes, Not New Fields

| Existing table/column | Proposed V11/V3 change |
|---|---|
| `users.suffix` | Migrate to `suffix_id`, then deprecate free text |
| `users.teaching_start_date` | Migrate to month/year precision or store the first day only as a documented implementation detail, not both permanently |
| `class_lists` unique rules | Enforce one active learner membership per academic year while retaining inactive history |
| `tests.class_assignment_id` | Replace sole delivery ownership with reusable test ownership plus `test_assignments` after approval |
| `test_parts.part_type` | Replace free text with `question_type_id` |
| `questions.option_a` through `option_e` | Migrate to `question_options`; do not require option columns for text/essay items |
| `answer_keys.correct_option` | Make nullable or retire after normalized option/text/rubric key migration |
| `student_answers.selected_option` | Migrate to `selected_question_option_id`; retain a migration snapshot only if required |
| `student_answers.is_correct` | Allow null for manual/rubric answers until evaluated |
| `student_answers.verified_by_user_id` | Allow null before mandatory teacher verification is completed |
| `test_results` attempt uniqueness | Scope by `test_assignment_id`, learner membership, and attempt number |
| `omr_detections` | Make raw scanner fields application-immutable; do not overwrite them during correction |
| `sync_items` uniqueness | Retain `UNIQUE(sync_id, result_uuid)` and UUID-based result upsert behavior |

### Supporting Central and Mobile-Only Table Fields

These tables must be classified before they are placed in an ERD. `schema_versions` and `download_snapshots` are Mobile SQLite infrastructure and do not belong in the central MySQL/TiDB ERD.

#### `email_verification_otps` - Current Central V2 Table

| Field | Current type | Key or purpose |
|---|---|---|
| `email_verification_otp_id` | `BIGINT UNSIGNED` | Primary key |
| `user_id` | `BIGINT UNSIGNED` | FK to `users.user_id` |
| `otp_hash` | `VARCHAR(100)` | One-way OTP hash; never plain text |
| `attempt_count` | `SMALLINT UNSIGNED` | Failed-attempt counter |
| `max_attempts` | `SMALLINT UNSIGNED` | Lock threshold, currently defaults to 5 |
| `expires_at` | `DATETIME` | OTP expiration |
| `resend_available_at` | `DATETIME` | Resend cooldown boundary |
| `used_at` | `DATETIME NULL` | One-time-use marker |
| `created_at` | `TIMESTAMP` | Creation time |

V3 disposition: replace/generalize this table through `verification_challenges` when email and SMS share one verification lifecycle. Do not permanently retain both tables for the same purpose.

#### `notifications` - Current Central V2 Table

| Field | Current type | Key or purpose |
|---|---|---|
| `notification_id` | `BIGINT UNSIGNED` | Primary key |
| `notification_uuid` | `CHAR(36)` | Unique public identifier |
| `recipient_user_id` | `BIGINT UNSIGNED` | FK to `users.user_id` |
| `notification_type` | `VARCHAR(50)` | Machine-readable event type |
| `title` | `VARCHAR(120)` | Short display title |
| `message` | `VARCHAR(500)` | User-facing message |
| `reference_type` | `VARCHAR(50) NULL` | Related entity type |
| `reference_id` | `VARCHAR(100) NULL` | Related entity identifier |
| `event_key` | `VARCHAR(150)` | Idempotency key; unique per recipient |
| `read_at` | `TIMESTAMP NULL` | Read timestamp |
| `created_at` | `TIMESTAMP` | Creation timestamp |

The existing uniqueness rules are `UNIQUE(notification_uuid)` and `UNIQUE(recipient_user_id, event_key)`.

#### `schema_versions` - Current Mobile-Only Table

| Field | Current SQLite type | Key or purpose |
|---|---|---|
| `schema_version_id` | `INTEGER` | Primary key; current implementation keeps one row with ID 1 |
| `version_number` | `INTEGER` | Current local schema version |
| `applied_at` | `TEXT` | ISO-8601 migration/application timestamp |

This is a Mobile SQLite maintenance table. It must be documented in the Mobile SQLite Data Dictionary, not the central database ERD.

#### `download_snapshots` - Current Mobile-Only Table

| Field | Current SQLite type | Key or purpose |
|---|---|---|
| `download_snapshot_id` | `INTEGER` | Autoincrement primary key |
| `contract_version` | `TEXT` | Backend/mobile download-contract version |
| `generated_at` | `TEXT` | Backend snapshot-generation timestamp |
| `downloaded_at` | `TEXT` | Mobile receipt/storage timestamp |
| `user_id` | `INTEGER` | FK to local `users.user_id` |

Recommended V3 additions, subject to Mobile contract approval:

| New field | Proposed SQLite type | Purpose |
|---|---|---|
| `snapshot_uuid` | `TEXT` | Unique snapshot identity |
| `payload_hash` | `TEXT` | Integrity and duplicate-download detection |
| `snapshot_status` | `TEXT` | `downloading`, `complete`, `failed`, `superseded` |
| `expires_at` | `TEXT NULL` | Optional server-provided refresh boundary |
| `last_error` | `TEXT NULL` | Safe local download failure detail |

#### `scan_teacher_verification` - Rename Before Approval

This singular table name does not exist in the current central schema or Mobile SQLite. Use the plural proposed name `scan_verifications` and the field inventory defined above.

`scan_verifications` records read-only scan acceptance, rejection, rescan request, and supersession. It supplements, not replaces, `scan_sessions`, `omr_detections`, `student_answers`, or `answer_verifications`. It cannot store or authorize a replacement Multiple Choice or True/False answer.

## 14. Backend and Mobile Responsibilities

### Backend

- Enforce school, teacher, class, assessment-delivery, and learner ownership.
- Enforce one active class enrollment per learner per academic year.
- Process SF1 imports transactionally and return per-row outcomes.
- Own assessment schedules, question-type rules, authoritative scoring, and report formulas.
- Preserve raw objective detections, enforce read-only objective answers, and retain append-only scan decisions and non-objective evaluation history.
- Upsert UUID-identified synchronization records.
- Enforce term automation and authorized override actions using server time.

### Mobile

- Store downloaded tests, deliveries, schedules, templates, and roster context offline.
- Select only a scanner template supported by both backend and mobile assets.
- Display every objective scanner result as read-only and allow only accept/rescan/reject actions.
- Preserve raw scan measurements and scan-decision reasons.
- Store text-answer crops and manual/OCR review state offline.
- Upload one or more result items using stable UUIDs and retry metadata.
- Separate synchronization requests by assessment delivery/class context.

## 15. Decisions Required Before V3 Migration

1. Approve and physically validate the exact A4, US Letter, and US Legal page-template geometry, region capacities, and version names for the dynamic 5-or-more-item contract.
2. Confirm whether result-level mobile sync means one-result requests while retaining the central sync envelope.
3. Approve `test_assignments` as the multi-class assessment-delivery model.
4. Approve teacher-owned `class_assignment_schedules`, timetable-overlap rules,
   and schedule-aware assessment warnings without allowing teachers to change
   Principal-owned class or subject assignment fields.
5. Select the first implementation scope for text responses:
   - scanned image plus manual teacher entry/scoring; or
   - OCR suggestion plus mandatory teacher correction.
6. Approve the enrollment-transfer lifecycle and one-active-class database constraint.
7. Approve the question-type, answer-key, attachment, rubric, and verification-history entities.
8. Approve SMS provider and operating cost before enabling SMS verification.
9. Update the final ERD, Data Dictionary, backend API contract, and Mobile SQLite contract together before migration.
