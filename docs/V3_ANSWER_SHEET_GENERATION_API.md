# V3 Answer-Sheet Generation API

**Status:** Implemented locally under the `v3` Spring profile.

This slice generates only the physically validated fixed template
`OMR-A4-10-MC-CTX-V2`. It does not activate dynamic, US Letter, US Legal,
True/False, mixed-question, or written-response scanning.

## Runtime

- Base URL: `http://localhost:8082/api/v3` when the standard V3 profile is used.
- Authentication: `Authorization: Bearer <teacher-access-token>`.
- Role: active `teacher` only.
- Database: `performance_assessment_v3_db` only.

## Endpoints

### Reference data

`GET /api/v3/answer-sheets/reference-data`

Returns the minimum product question count and paper-size capabilities. A4 is
currently available; US Letter and US Legal are returned with
`generationAvailable: false` until separate physical templates are validated.

### Eligibility

`GET /api/v3/test-assignments/{testAssignmentId}/answer-sheet-eligibility?paperSizeCode=A4`

Example response data:

```json
{
  "testAssignmentId": 1004,
  "assignmentUuid": "4d668126-3918-4af1-9cc0-bba5e99fb1ba",
  "paperSizeCode": "A4",
  "totalQuestions": 10,
  "questionTypeCounts": {
    "multiple_choice": 10
  },
  "eligible": true,
  "templateCode": "OMR-A4-10-MC-CTX-V2",
  "templateVersion": "2",
  "blockers": []
}
```

The endpoint returns `200` even when ineligible. The frontend must inspect
`data.eligible` and display `data.blockers`.

### Generate an immutable version

`POST /api/v3/test-assignments/{testAssignmentId}/answer-sheet-versions`

```json
{
  "paperSizeCode": "A4"
}
```

Success is `201` with an `ApiResponse` containing:

```json
{
  "answerSheetVersionId": 1,
  "answerSheetUuid": "5f1e675e-836c-48d3-8cca-728851069a83",
  "testAssignmentId": 1004,
  "assignmentUuid": "4d668126-3918-4af1-9cc0-bba5e99fb1ba",
  "paperSizeCode": "A4",
  "generationNumber": 1,
  "testVersionNumber": 1,
  "totalQuestions": 10,
  "totalPages": 1,
  "manifestVersion": 1,
  "manifestHash": "<sha256>",
  "generationStatus": "ready",
  "pdfContentHash": "<sha256>",
  "pdfFileSizeBytes": 12345,
  "generatedAt": "2026-09-01T08:00:00Z",
  "pdfDownloadPath": "/api/v3/answer-sheet-versions/1/pdf"
}
```

Each successful call deliberately creates a new immutable generation. The
frontend must disable duplicate submissions and must not automatically retry
this POST after an ambiguous network failure.

### Retrieve metadata and PDF

- `GET /api/v3/answer-sheet-versions/{answerSheetVersionId}`
- `GET /api/v3/answer-sheet-versions/{answerSheetVersionId}/pdf`

The PDF endpoint returns `application/pdf` as an attachment with private,
no-store caching. It rechecks the stored file size and SHA-256 hash before
returning bytes.

## Authorization and ownership

The backend validates all of the following:

- authenticated, active teacher account and active school;
- the `testAssignmentId` belongs to the teacher and school;
- the class assignment is active;
- the assessment is active;
- the test assignment status is `planned` or `open`;
- answer-sheet version metadata and PDF belong to the same teacher and school.

Frontend visibility is not an authorization control.

## Current eligibility rules

The product minimum is five questions, but the only validated physical template
currently supports exactly ten questions. Generation therefore requires:

- exactly 10 `multiple_choice` questions;
- exactly four active options `A`, `B`, `C`, and `D` per question;
- exactly one valid answer key per question;
- at least one part-skill mapping covering each question;
- matching `tests.total_items` and `test_parts.number_of_items` snapshots;
- a valid stored A4 template with four registration markers, one QR region, ten
  objective response regions, and 100% print scale.

Common blocker codes include:

- `MINIMUM_QUESTION_COUNT_NOT_MET`
- `UNSUPPORTED_TEMPLATE_ITEM_COUNT`
- `UNSUPPORTED_QUESTION_TYPE`
- `UNSUPPORTED_QUESTION_OPTIONS`
- `INCOMPLETE_ANSWER_KEYS`
- `INCOMPLETE_SKILL_MAPPINGS`
- `NO_PHYSICALLY_VALIDATED_TEMPLATE`
- `VALIDATED_TEMPLATE_GEOMETRY_INVALID`
- `TEST_ITEM_SNAPSHOT_MISMATCH`
- `TEST_PART_ITEM_SNAPSHOT_MISMATCH`

Generation with blockers returns `409 ANSWER_SHEET_NOT_ELIGIBLE`.

## Transaction and persistence

Generation is transactional. The backend locks the owned test assignment,
creates the version/page/question-region rows, writes the PDF, records hashes,
marks the version ready, and writes an audit event. A transaction rollback also
removes the newly written PDF file so partial metadata and files are not left
behind.

## Frontend handoff

1. Load reference data.
2. Evaluate eligibility for the selected `testAssignmentId` and paper size.
3. Enable **Generate Bubble Answer Sheet** only when `eligible` is true.
4. Show backend blocker messages when it is false.
5. POST once, retain the returned version ID, then download the PDF as a Blob
   using the returned `pdfDownloadPath` and Bearer token.
6. Keep test-questionnaire printing separate from Bubble Answer Sheet
   generation.

## Mobile handoff

No Mobile SQLite, detector, or scanner contract changed in this slice. Mobile
continues to recognize only the validated `OMR-A4-10-MC-CTX-V2` geometry. The V3
mobile manifest/read contract may distribute generated version metadata, but
scan upload remains intentionally disabled until transactional evidence
persistence and retry-safe idempotency are implemented.

## Verification evidence

- Maven: 139 tests, 0 failures, 0 errors, 0 skipped.
- V3 readiness: 16/16 checks passed against the 66-table local V3 database.
- Authenticated teacher reference-data request passed.
- Assignment 1004 eligibility passed; assignment 1003 was correctly blocked by
  `UNSUPPORTED_TEMPLATE_ITEM_COUNT`.
- PDF fixture test confirms exact A4 dimensions, one page, four corner markers,
  and the exact decodable fixed QR payload.
- CORS preflight from `http://localhost:5173` passed.

