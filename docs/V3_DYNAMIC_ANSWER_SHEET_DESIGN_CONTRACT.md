# V3 Dynamic Answer Sheet Design Contract

**Date:** August 30, 2026  
**Status:** Approved design; local central schema applied, functional integration pending  
**Applies to:** Central Spring Boot/MySQL-TiDB, React print flow, and Mobile SQLite/OpenCV

## 1. Safety Boundary

This document records the approved target behavior and required schema/API
impact. The corresponding central schema delta has now been applied only to the
local V3 database. This document does not authorize Java functional APIs,
frontend changes, Mobile SQLite migration, detector-map replacement, TiDB
deployment, or activation of unvalidated templates.

The currently validated `OMR-A4-10-MC-CTX-V2` template remains unchanged and is
still the only physically validated scanner layout. The dynamic V3 contract below
must use new template codes and versions. No new layout may reuse the validated
template code.

The exact applied-local DDL, validated-template region seed, and smoke-test
package are listed in `V3_DYNAMIC_ANSWER_SHEET_SCHEMA_DELTA_HANDOFF.md`. Having
the schema does not make dynamic generation or scanning runtime-available.

## 2. Finalized Product Requirements

1. Generated and scannable answer sheets support `A4`, `US_LETTER`, and
   `US_LEGAL`.
2. Each paper size has its own immutable geometry. A layout must never be stretched
   or scaled to another paper size.
3. Answer-sheet generation requires at least five questions across the complete
   assessment.
4. There is no fixed maximum item number, no fixed 10-item part size, and no fixed
   question-type order.
5. One assessment may contain multiple ordered `test_parts`. Each part selects one
   `question_type_id`; parts of different types may appear in any order.
6. Supported types are `multiple_choice`, `true_false`, `identification`,
   `enumeration`, and `essay`.
7. Multiple Choice and True/False use OpenCV marker alignment and bubble detection.
   A teacher may review the evidence and accept, reject, or request a rescan, but
   cannot replace the scanner-detected objective answer.
8. Identification, Enumeration, and Essay preserve the scanned response region.
   A teacher then evaluates, transcribes when needed, and scores the response.
9. OpenCV performs page alignment, detection, and response-region extraction. It
   does not authoritatively interpret handwriting or grade essays.
10. Mixed assessments may produce multiple pages. Every page has four markers, a
    page-specific identity, an immutable template version, and an explicit
    question-to-region map.

## 3. Current Baseline Versus V3 Target

| Capability | Current validated baseline | Approved V3 target |
|---|---|---|
| Paper size | A4 only | A4, US Letter, US Legal |
| Questions | Exactly 10 MC items | At least 5 total; dynamic pagination |
| Question types | MC scanner map | All five types |
| Page count | One page | One or more pages |
| Geometry | One JSON template | Immutable page template plus generated page/region manifest |
| Objective review | Read-only detection | Same rule retained |
| Written responses | Schema foundation only | Cropped evidence plus teacher evaluation/scoring |
| Runtime/API | Existing V2 fixed prototype | Not yet implemented in V3 |

The V3 target does not invalidate the existing physical test evidence. It extends
the design through new immutable template versions and generated manifests.

## 4. Paper Size Contract

Recommended central reference entity: `paper_sizes`.

| Field | Rule |
|---|---|
| `paper_size_id` | Primary key |
| `paper_size_code` | Unique stable code: `A4`, `US_LETTER`, `US_LEGAL` |
| `paper_size_name` | Display name |
| `width_points` | Exact PDF width |
| `height_points` | Exact PDF height |
| `is_active` | Availability for new template generation |
| `created_at` | Central creation time |

Initial portrait dimensions:

| Code | Width | Height |
|---|---:|---:|
| `A4` | 595.276 pt | 841.890 pt |
| `US_LETTER` | 612.000 pt | 792.000 pt |
| `US_LEGAL` | 612.000 pt | 1008.000 pt |

Backend PDF output must use the selected dimensions directly. Frontend printing
must download the backend PDF as a Blob and must not rebuild, resize, or print the
sheet through browser HTML/CSS.

## 5. Required Central Schema Impact

### 5.1 Existing Entities That Remain Valid

- `tests`, `test_parts`, `questions`, and `question_types` already support ordered
  parts and the five response types.
- `question_options`, `answer_keys`, and `accepted_answers` remain the scoring
  configuration for objective and short written responses.
- `answer_attachments` remains the private evidence store reference.
- `scan_verifications` remains the append-only accept/reject/rescan history for
  objective scan evidence.
- `answer_verifications` remains the append-only teacher evaluation history for
  non-objective responses.
- `rubrics`, `rubric_criteria`, and `answer_rubric_scores` remain the normalized
  essay/manual-scoring model.
- `test_assignments` remains the exact class delivery identified by download,
  scanning, result, and synchronization contracts.

### 5.2 Existing Entities That Need Contract Changes Before Migration

#### `omr_templates`

The current row is tied to one `question_type_id`, one option count, and one JSON
geometry. That is insufficient for mixed, dynamic, multi-page sheets.

Required target changes:

- Reference `paper_size_id` instead of relying on free-text `page_size`.
- Add an explicit immutable `template_version` and `geometry_hash`.
- Treat a template as one physical page layout, not a complete assessment.
- Move question-type-specific region capability out of the template header.
- Keep marker, QR, and printable-boundary geometry immutable after activation.
- Retire a template by status; never update an active geometry in place.

The existing `OMR-A4-10-MC-CTX-V2` row remains unchanged as a legacy validated
template.

#### `test_parts` and `questions`

- `test_parts.part_order` remains the question-type order authority.
- `test_parts.number_of_items` remains a validated snapshot, not an independent
  source of truth.
- `questions.question_type_id` must match the parent part's `question_type_id`.
- `questions.maximum_points` remains authoritative for variable-point questions.
- No database maximum such as 50 questions should be introduced.
- Backend activation and generation enforce a minimum of five total questions.

#### `scan_sessions`

The current row stores one `omr_template_id` and one image hash. A multi-page
attempt requires the session to become the learner/test scan aggregate while page
captures move to `scan_pages`.

Target session data includes the generated answer-sheet version, expected page
count, captured page count, learner/test-assignment context, overall status, and
rescan lineage. A session is accepted only when all required pages are present and
valid.

#### `omr_detections`

Add page and generated-region identity. Raw detections remain immutable. The unique
rule becomes one raw detection per captured page and generated question region,
not merely one question per whole scan session.

#### `answer_attachments`

Add captured page and generated-region references. Full-page images attach to a
`scan_page`; written response crops attach to both the generated region and the
eventual `student_answer` when available. The content hash, storage key, media
metadata, and crop coordinates remain immutable evidence.

### 5.3 New Entity Candidates Required By This Contract

#### `omr_template_regions`

Defines immutable regions inside one page template.

Core fields: `omr_template_region_id`, `omr_template_id`, `region_code`,
`region_order`, `region_type`, nullable `question_type_id`, coordinates/dimensions,
option geometry JSON, capacity, `geometry_hash`, and timestamps.

Recommended `region_type` values: `objective_bubbles`, `written_response`,
`page_identity`, and `registration_marker`.

#### `answer_sheet_versions`

Represents one immutable generated answer-sheet package for an exact
`test_assignment_id`, content version, and paper size.

Core fields: `answer_sheet_version_id`, `answer_sheet_uuid`,
`test_assignment_id`, `paper_size_id`, `test_version_number`, `total_questions`,
`total_pages`, `manifest_version`, `manifest_hash`, `generation_status`,
`generated_by_user_id`, and timestamps.

Generating again after assessment-content changes creates a new version. It does
not overwrite a package that may already have been printed or downloaded.

#### `answer_sheet_pages`

Represents every printable page in an answer-sheet version.

Core fields: `answer_sheet_page_id`, `page_uuid`, `answer_sheet_version_id`,
`omr_template_id`, `page_number`, `total_pages`, `qr_payload`, `qr_payload_hash`,
`page_geometry_hash`, and timestamps.

Enforce unique `(answer_sheet_version_id, page_number)` and require page numbers
from 1 through the version's declared total page count.

#### `answer_sheet_regions`

Maps an actual assessment question to an actual region on one generated page.

Core fields: `answer_sheet_region_id`, `region_uuid`, `answer_sheet_page_id`,
`omr_template_region_id`, `question_id`, `test_part_id`, `global_item_number`,
`part_item_number`, `region_type`, geometry snapshot JSON/hash, and timestamps.

This table is the authoritative question-to-page/coordinate manifest. It prevents
the mobile scanner from guessing coordinates from question order.

#### `scan_pages`

Represents one captured image for one required answer-sheet page.

Core fields: `scan_page_id`, `scan_page_uuid`, `scan_session_id`,
`answer_sheet_page_id`, `page_number`, `omr_template_id`, `scanner_version`,
`image_hash`, private image attachment reference, `page_status`, failure details,
`supersedes_scan_page_id`, capture timestamps, and central timestamps.

Recommended `page_status` values: `captured`, `processing`, `needs_verification`,
`accepted`, `rescan_requested`, `rejected`, `superseded`, and `failed`.

## 6. Relationship Summary

```text
test_assignments
  -> answer_sheet_versions
      -> answer_sheet_pages
          -> answer_sheet_regions -> questions -> test_parts
          -> omr_templates -> paper_sizes
                           -> omr_template_regions

test_results
  -> scan_sessions
      -> scan_pages -> answer_sheet_pages
          -> omr_detections
          -> answer_attachments
  -> student_answers
      -> answer_attachments
      -> answer_verifications
      -> answer_rubric_scores -> rubric_criteria -> rubrics
```

## 7. QR And Page Identity Contract

Every page QR must identify at least:

- QR payload version;
- exact template code/version;
- `answer_sheet_uuid`;
- `page_uuid`;
- `test_assignment_id` or stable assignment UUID;
- page number and total page count;
- page/manifest geometry hash.

The page QR must not be treated as learner identity. Mobile supplies the selected
`class_list_id` context and must reject a QR whose test assignment, page number,
template version, or geometry hash does not match the downloaded manifest.

The final compact JSON keys and size limit remain to be approved before a detector
or generator is implemented.

## 8. Required V3 API Contract Impact

### 8.1 Generation And Download

Recommended API surface:

- `GET /api/v3/answer-sheets/reference-data`
- `GET /api/v3/test-assignments/{testAssignmentId}/answer-sheet-eligibility`
- `POST /api/v3/test-assignments/{testAssignmentId}/answer-sheet-versions`
- `GET /api/v3/answer-sheet-versions/{answerSheetVersionId}`
- `GET /api/v3/answer-sheet-versions/{answerSheetVersionId}/pdf`

Generation request identifies `paperSizeCode`. The backend validates ownership,
active test assignment, at least five questions, complete answer/scoring rules,
compatible page templates, and a deterministic page plan before generating a
versioned PDF.

The eligibility response must return explicit blockers instead of silently
disabling print controls.

The mobile download contract must include:

- test assignment and content version;
- ordered parts and questions;
- answer-sheet version and page manifest;
- exact template versions and geometry hashes;
- question-to-region mappings;
- required scanner version;
- scoring/verification capability flags, without exposing protected answer keys.

### 8.2 Capture And Synchronization

Each learner result upload must preserve stable UUIDs for the result, scan session,
every scan page, every detection, every attachment, and every student answer.

The upload contract must carry page-level metadata and return success/failure per
page and per learner result. Retrying the same UUIDs must upsert the same records,
not create another attempt or analytics row.

The backend must reject:

- missing or duplicate required pages;
- mismatched paper/template versions;
- page QR/manifest mismatches;
- question regions not present in the generated manifest;
- cross-school, cross-class, cross-test-assignment, or cross-student ownership;
- objective answer values that differ from immutable detections;
- written/manual scores above the question or rubric maximum.

### 8.3 Verification And Scoring

Objective endpoints expose detection evidence and scan-level decisions only. They
must not accept a replacement MC/TF option.

Written-response endpoints expose private page/crop evidence and accept authorized
teacher transcription, accepted-answer evaluation, criterion scores, feedback,
finalization, and audited reopen actions.

Official scoring remains backend-owned:

- MC/TF points come from accepted immutable detections and answer keys.
- Identification/Enumeration points come from teacher-confirmed evaluation rules.
- Essay points come from completed rubric criteria or an explicitly approved
  manual-scoring rule.
- Results remain `pending_verification` until every required page and response is
  accepted/evaluated.

## 9. Backend And Mobile Responsibilities

### Backend

- Own paper-size records, activated template versions, generation manifests, PDF
  output, ownership validation, scoring, finalization, and audit enforcement.
- Never mutate active template geometry or raw capture evidence.
- Return explicit capability and eligibility errors.

### Mobile

- Store only downloaded template/manifests supported by its scanner version.
- Validate all page markers and the page QR before extraction.
- Preserve images, crops, detections, and UUIDs offline until acknowledged.
- Display objective answers as read-only.
- Allow teacher evaluation only for written response types.
- Upload page/result records idempotently and retain retry errors locally.

### Frontend

- Request the paper size and backend-generated PDF.
- Do not reproduce template geometry in HTML/CSS.
- Show generation blockers and version information.
- Download the PDF without automatic scaling or automatic print submission.

## 10. Contract Freeze Status

Backend decisions for template-code reservation, QR identity/encoding, assignment
UUID, initial A-D capability, written-response semantics, page/image limits,
evidence retention, manifest fields, and page-level retry acknowledgement are in
`V3_DYNAMIC_ANSWER_SHEET_BACKEND_CONTRACT_DECISIONS.md`.

The remaining blockers are:

1. Additive schema hardening for written-response counts, evidence variants,
   retention/purge metadata, and QR byte checks.
2. Mobile review and owner acceptance of the published DTO and shadow-migration
   mapping.
3. Physically validated immutable geometry assets for A4, US Letter, and US Legal.
4. Objective capacity derived from each physically validated activated template,
   not from unvalidated global row/column constants.
5. Exact written-region dimensions and line spacing for every supported preset.
6. Acceptance tests proving marker detection, page ordering, duplicate-page
   rejection, retry idempotency, objective immutability, and rubric totals on all
   three paper sizes.

Until those items are approved and physically validated, V3 must not advertise
dynamic answer-sheet scanning as implemented.
