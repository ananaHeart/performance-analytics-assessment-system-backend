# V3 Dynamic Answer Sheet Backend Contract Decisions

**Date:** August 30, 2026  
**Status:** Backend contract approved; local central V3 schema applied and validated  
**Scope:** Contract plus local database baseline; client integration remains blocked

## Review Verdict

The Mobile proposal is approved with the revisions below. V3_009 implements the
required additive hardening and passed both disposable validation and controlled
application to the local 66-table V3 database. It is safe to use these decisions
for the next repository/DTO implementation slice. It is not approval to activate
an unvalidated template, switch Mobile SQLite or React, or deploy to TiDB.

## Decision Matrix

| Requested decision | Backend decision |
|---|---|
| Page-template codes | Approved as reserved draft codes |
| Objective capacities | Candidate numbers rejected as fixed contract limits |
| Written presets | Approved using normalized size names; exact heights pending physical validation |
| QR contract | Approved with UUID, encoding, version, and byte-limit revisions |
| Page/image/upload limits | Approved as initial configurable V3 limits |
| Evidence retention | Approved as initial policy, subject to school privacy-policy review |
| Manifest DTO | Approved below |
| Page upload acknowledgement | Approved below |
| Test-assignment UUID | Confirmed: use `test_assignments.assignment_uuid` |
| Initial MC options | A-D only; A-E requires a new validated template capability |

## 1. Template Codes

Reserve these exact codes:

- `OMR-A4-DYNAMIC-CTX-V1`
- `OMR-US-LETTER-DYNAMIC-CTX-V1`
- `OMR-US-LEGAL-DYNAMIC-CTX-V1`

Rules:

- New rows remain `template_status = draft` until their exact PDF and Mobile maps
  pass physical validation.
- `template_version` is a string and is immutable after activation.
- Activating one paper size does not activate either of the other paper sizes.
- `OMR-A4-10-MC-CTX-V2` remains unchanged and is not part of this family.
- Regenerated geometry requires a new template version, not an update to an active
  row.

## 2. Objective Capacity

Do not publish `51`, `45`, or `66` as backend limits. They remain physical-test
candidates only.

The authoritative capacity of a page is the number and capability of its activated
immutable regions. The planner must:

1. place questions in configured part/question order;
2. use only regions compatible with the question type and option count;
3. start another page when the next region does not fit; and
4. never shrink bubbles, marker spacing, QR size, or row pitch to gain capacity.

The product has no fixed question-count maximum. Eligibility is limited by the
approved maximum page count and by the activated template capabilities.

## 3. Written-Response Presets

Use the normalized backend values already present in the V3 schema:

- `short`
- `medium`
- `long`
- `full_page`

Use `none` only for objective questions. `IDENTIFICATION_SHORT`, `ENUMERATION_3`,
and `ENUMERATION_5` may be UI labels or shortcuts, but they are not additional
database enums.

Required behavior:

- Identification normally has `expectedResponseCount = 1`.
- Enumeration stores an explicit `expectedResponseCount` and an immutable
  generated `responseLineCount` snapshot.
- Essay uses `medium`, `long`, or `full_page` according to the teacher's configured
  response space and rubric.
- Exact region height, crop rectangle, and ruled-line geometry are template/page
  geometry, not global constants.
- The proposed 42, 96, 144, and 250 point heights remain test candidates until
  printed and scanned.

## 4. Compact Page QR Contract

Approved key order:

```text
v, as, pg, ta, pn, pc, tc, tv, gh
```

Approved meanings:

| Key | Approved value |
|---|---|
| `v` | Integer payload version `3` |
| `as` | Answer-sheet UUID encoded as base64url UUID bytes |
| `pg` | Page UUID encoded as base64url UUID bytes |
| `ta` | `test_assignments.assignment_uuid` encoded as base64url UUID bytes |
| `pn` | One-based page number |
| `pc` | Total page count |
| `tc` | Exact template code |
| `tv` | Exact template version as a string |
| `gh` | Full SHA-256 page-geometry digest encoded base64url without padding |

Encoding rules:

- API and database UUIDs remain canonical UUID strings.
- QR UUID values encode the raw 16 UUID bytes as 22-character base64url without
  padding.
- `gh` encodes all 32 SHA-256 bytes as 43-character base64url without padding.
- JSON is UTF-8, compact, has no whitespace, and uses the fixed key order above.
- Maximum encoded payload is 256 bytes. Generation fails rather than producing a
  larger QR.
- Error correction is level M for the first physical-test family.
- `qrPayloadHash` is the lowercase 64-character SHA-256 hex digest of the exact
  UTF-8 QR payload.
- Mobile accepts a QR only when its exact payload/hash matches the authenticated
  downloaded manifest.
- No student, LRN, class-list, teacher, answer-key, or score value appears in QR.
- Numeric `test_assignment_id` is never used as the QR fallback.

Canonical shape:

```json
{"v":3,"as":"base64urlUuid22","pg":"base64urlUuid22","ta":"base64urlUuid22","pn":1,"pc":3,"tc":"OMR-A4-DYNAMIC-CTX-V1","tv":"1","gh":"base64urlSha256Digest43"}
```

## 5. Initial Technical Limits

| Concern | Approved initial rule |
|---|---|
| Maximum pages | 12 pages per generated answer sheet |
| Production image type | `image/jpeg` |
| Test-profile image type | `image/png` may be enabled only in test tooling |
| Normalized longest edge | 2400 pixels |
| Target normalized size | At most 3 MiB per page |
| Hard inbound limit | 5 MiB per page |
| Capture/upload unit | One physical page per request |
| Upload transport | `multipart/form-data` binary file plus JSON metadata |
| Base64 image in JSON | Prohibited |

The backend must verify media signature, dimensions, declared MIME type, content
hash, ownership, UUID idempotency, and manifest match. The 12-page limit is a
versioned/configurable service policy, not a fixed database question limit.

## 6. Evidence Retention

Initial policy:

- Mobile keeps original and normalized evidence until both page acknowledgement
  and result acknowledgement succeed.
- Mobile then keeps a seven-day recovery grace period before local purge, unless a
  retry, correction, rescan, or unresolved result is still open.
- Central private original pages, normalized pages, and answer crops are retained
  for 365 days after the later of result finalization or test-assignment closure.
- A correction review, dispute, investigation, or explicit retention hold pauses
  automatic purge.
- Purging removes the private binary object but retains UUID, content hash,
  capture metadata, verification decisions, score history, purge timestamp, and
  audit record.
- Evidence is never exposed through a public object-storage URL.

The 365-day default must be reviewed against the adopting school's formal privacy
and records-retention policy before production deployment.

## 7. Generated Manifest Download DTO

Proposed endpoint:

```text
GET /api/v3/mobile/test-assignments/{assignmentUuid}/answer-sheets/{answerSheetUuid}/manifest
Authorization: Bearer <teacher-token>
```

Response uses the standard API envelope. `data` has this shape:

```json
{
  "contractVersion": "3.0",
  "manifestVersion": 1,
  "answerSheetUuid": "56d628da-d7fc-4faa-b018-3f740e048bf0",
  "testAssignment": {
    "testAssignmentId": 12001,
    "assignmentUuid": "26b14ea2-4383-43f5-bb40-f60683ee46bb"
  },
  "paperSize": {
    "code": "A4",
    "widthPt": 595.276,
    "heightPt": 841.890,
    "orientation": "portrait"
  },
  "testVersionNumber": 1,
  "totalQuestions": 10,
  "totalPages": 1,
  "manifestHash": "lowercaseSha256Hex",
  "requiredScannerVersion": "3.0.0",
  "generatedAt": "2026-08-30T03:30:00Z",
  "pages": [
    {
      "pageUuid": "be42bd22-1f8b-4409-941d-9e689bc271d7",
      "pageNumber": 1,
      "totalPages": 1,
      "template": {
        "code": "OMR-A4-DYNAMIC-CTX-V1",
        "version": "1",
        "geometryHash": "lowercaseSha256Hex"
      },
      "qr": {
        "payloadVersion": 3,
        "payload": "compactJsonPayload",
        "payloadHash": "lowercaseSha256Hex",
        "errorCorrection": "M"
      },
      "coordinateSpace": {
        "unit": "pt",
        "origin": "pdf_bottom_left",
        "width": 595.276,
        "height": 841.890
      },
      "regions": [
        {
          "regionUuid": "c4eff0ab-8c19-4f56-bd9e-821882281363",
          "templateRegionCode": "OBJECTIVE_SLOT_01",
          "questionId": 30001,
          "questionUuid": "1dd4c493-013f-4251-9d87-ad9be9533298",
          "testPartId": 21001,
          "globalItemNumber": 1,
          "partItemNumber": 1,
          "questionType": "multiple_choice",
          "regionType": "objective_bubbles",
          "responseRegionSize": "none",
          "expectedResponseCount": null,
          "responseLineCount": null,
          "rectangle": {
            "x": 72.600,
            "y": 597.934,
            "width": 84.800,
            "height": 12.800
          },
          "geometryHash": "lowercaseSha256Hex",
          "options": [
            {"key": "A", "storedValue": "A", "centerX": 79.000, "centerY": 604.334},
            {"key": "B", "storedValue": "B", "centerX": 103.000, "centerY": 604.334},
            {"key": "C", "storedValue": "C", "centerX": 127.000, "centerY": 604.334},
            {"key": "D", "storedValue": "D", "centerX": 151.000, "centerY": 604.334}
          ]
        }
      ]
    }
  ]
}
```

The manifest never returns answer keys, accepted-answer text, correct option keys,
rubric solutions, student identity, or scores.

## 8. Page Upload And Retry DTO

Proposed endpoint:

```text
POST /api/v3/mobile/scan-pages
Authorization: Bearer <teacher-token>
Content-Type: multipart/form-data
```

Parts:

- `metadata`: `application/json`
- `file`: `image/jpeg`

Metadata:

```json
{
  "contractVersion": "3.0",
  "syncUuid": "63b15575-b1ca-45ea-902d-efeaec09915d",
  "resultUuid": "fa6f21ad-cfff-41a3-99bc-188927e51e77",
  "scanUuid": "e4c78a06-3c3a-4455-80bf-a57d098a9404",
  "scanPageUuid": "63113584-7301-478b-a177-6d9f855200cd",
  "answerSheetUuid": "56d628da-d7fc-4faa-b018-3f740e048bf0",
  "pageUuid": "be42bd22-1f8b-4409-941d-9e689bc271d7",
  "assignmentUuid": "26b14ea2-4383-43f5-bb40-f60683ee46bb",
  "classListId": 41001,
  "pageNumber": 1,
  "captureNumber": 1,
  "scannerVersion": "3.0.0",
  "qrPayloadHash": "lowercaseSha256Hex",
  "imageHash": "lowercaseSha256Hex",
  "capturedAt": "2026-08-30T03:35:00Z"
}
```

The teacher identity is taken only from the bearer token. The backend validates
same-school ownership and the exact assignment, class list, answer-sheet version,
page, result, and scan relationships.

Created response:

```json
{
  "success": true,
  "message": "Scan page acknowledged.",
  "data": {
    "syncUuid": "63b15575-b1ca-45ea-902d-efeaec09915d",
    "resultUuid": "fa6f21ad-cfff-41a3-99bc-188927e51e77",
    "scanUuid": "e4c78a06-3c3a-4455-80bf-a57d098a9404",
    "scanPageUuid": "63113584-7301-478b-a177-6d9f855200cd",
    "backendScanPageId": 88001,
    "uploadStatus": "created",
    "pageStatus": "captured",
    "contentHash": "lowercaseSha256Hex",
    "acknowledgedAt": "2026-08-30T03:35:04Z"
  },
  "errors": [],
  "timestamp": "2026-08-30T03:35:04Z"
}
```

Retry rules:

- Reusing the same stable UUIDs and identical hashes returns HTTP 200 with the
  same backend identifiers and `uploadStatus = replayed`.
- Reusing a UUID with different assignment, page, QR hash, or image hash returns
  HTTP 409 with `IDEMPOTENCY_KEY_REUSE`.
- Missing ownership returns HTTP 403.
- Unsupported media or an oversized page returns HTTP 415 or 413.
- A manifest/QR mismatch returns HTTP 422.
- Per-page acknowledgement does not finalize or score the learner result.
- One sync envelope belongs to only one `test_assignment_id`; results and pages
  remain individually acknowledged and retryable.

## 9. Stable Assignment Identity

The schema already has both:

- `test_assignments.test_assignment_id`
- `test_assignments.assignment_uuid`

Decision:

- `assignment_uuid` is authoritative across QR, Mobile SQLite, download manifests,
  and retry payloads.
- Numeric `test_assignment_id` may appear in authenticated API data for database
  joins, but it is not printed in QR and is never the offline identity fallback.

## 10. Multiple Choice And True/False Capability

- Initial dynamic Multiple Choice supports exactly A-D.
- A-E requires a separately versioned template capability, Mobile map, generator
  fixture, and physical validation.
- True/False displays T/F but stores A=True and B=False.
- Objective detections are read-only. Teachers may accept, reject, or request a
  rescan; they may not replace the detected objective value.

## Schema Hardening Draft Completed And Validated

The additive hardening was introduced without rewriting V3_006 through V3_008:

- `V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql`
- `V3_009_dynamic_answer_sheet_contract_hardening_smoke_test_DRAFT.sql`
- `V3_009_dynamic_answer_sheet_negative_fixtures_DRAFT.sql`

Completed additions:

1. `questions.expected_response_count` for Enumeration and other bounded written
   responses.
2. `answer_sheet_regions.expected_response_count_snapshot` and
   `response_line_count` for immutable generated-page behavior.
3. Distinct original and normalized page-evidence types in `answer_attachments`.
4. Evidence lifecycle fields: retention deadline, hold state/reason, purge state,
   purge time/user/reason, while retaining the content hash and audit metadata.
5. Database checks limiting generated and captured QR payloads to 256 UTF-8 bytes.

The complete 60-to-66-table chain passed in
`performance_assessment_v3_validation_20260830_182425` with 157 foreign keys, 80
checks, and 96 unique constraints. Eleven executable negative database/service
contract cases were rejected and zero fixture rows persisted. The Spring V3
readiness endpoint passed all 16 checks against that disposable database.

MariaDB cannot enforce source-row type, cross-page ownership, direct self, and
arbitrary cycles through a portable row `CHECK`. The reusable
`V3AnswerAttachmentLineageValidator` now enforces those cross-row rules and its
eight focused tests pass. The future attachment-write service must invoke it
before persistence. Detailed evidence is in
`V3_DYNAMIC_ANSWER_SHEET_HARDENING_VALIDATION_2026-08-30.md`.

## Remaining Physical Gates

- Exact A4 dynamic page geometry
- Exact US Letter geometry
- Exact US Legal geometry
- Objective capacity per activated template
- Exact written-region dimensions and line spacing
- PDF render checks at exact paper dimensions
- Mobile manifest/map fixtures
- Device and physical print/scan acceptance matrix

Until those gates pass, only `OMR-A4-10-MC-CTX-V2` remains operationally validated.

## Message For Mobile AI

Backend blocker remediation and controlled local central-schema application are
complete. The template code family, compact QR semantics,
assignment UUID, initial A-D capability, manifest shape, page-upload retry rules,
technical limits, and retention direction are approved with the revisions in this
document. Do not use `51`, `45`, or `66` as fixed capacities, and do not add
`ENUMERATION_3` or `ENUMERATION_5` as database enums. Use canonical API UUID
strings and compact base64url UUIDs only inside QR. The applied local package
passes exact 66/157/80/96 readiness checks, 11 disposable negative fixtures, and
Java lineage tests. Production SQLite and scanner switching remain blocked until shared
DTO fixtures and separate physical validation for every page template are
complete.
