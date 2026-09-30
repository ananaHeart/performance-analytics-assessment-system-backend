# V3 Mobile API Contract

Status date: 2026-08-31

This contract covers the controlled, read-only V3 Mobile integration slice. It
does not authorize migration of production Mobile SQLite, switching the current
mobile endpoints, or uploading scan evidence.

## Runtime Status

| Method | Path | Role | Status |
|---|---|---|---|
| `GET` | `/api/v3/mobile/reference-data` | Active teacher | Implemented |
| `GET` | `/api/v3/mobile/download` | Active teacher | Implemented |
| `GET` | `/api/v3/mobile/test-assignments/{assignmentUuid}/answer-sheets/{answerSheetUuid}/manifest` | Active teacher who owns the assignment | Implemented |
| `POST` | `/api/v3/mobile/scan-pages` | Active teacher | Contract only; endpoint deliberately unavailable |

All implemented endpoints require:

```http
Authorization: Bearer <v3-session-token>
```

The backend derives `userId`, `schoolId`, role, and account status from that
token. Payload or query values must never override the authenticated identity.
The V3 security chain denies non-teachers before the service runs, and every
repository query repeats teacher and same-school ownership restrictions.

## Standard Envelope

Success:

```json
{
  "success": true,
  "message": "V3 Mobile reference data retrieved successfully.",
  "data": {},
  "errors": null,
  "timestamp": "2026-08-31T00:17:04.904268200Z"
}
```

Authentication and contract failures use the same envelope. Expected HTTP
statuses include `401` for a missing/invalid session, `403` for a non-teacher or
inactive/non-school account, `404` for a manifest outside the teacher's owned
assignment scope, and `409` for an incomplete or inconsistent stored manifest.

## Reference Data

`GET /api/v3/mobile/reference-data` returns:

- contract version and UTC server time;
- all five active question-type capabilities;
- registered paper sizes and whether each is operationally supported;
- active OMR template metadata and immutable regions;
- shared status values; and
- download, UUID, idempotency, QR-size, and minimum-question rules.

Only `OMR-A4-10-MC-CTX-V2` is currently marked physically validated and
operational. A4 dynamic, US Letter, US Legal, mixed-type, multi-page, and written
response layouts remain unavailable until their exact generator and scanner
fixtures pass physical validation.

Authoritative sample:
`src/test/resources/contracts/v3/mobile/reference-data-response.json`.

## Teacher Snapshot Download

`GET /api/v3/mobile/download` returns one atomic `full_snapshot` for the
authenticated teacher. It includes only the teacher's active class assignments
and same-school records:

- teacher identity;
- class assignments, class assignment schedules, class-list memberships, and students;
- term periods and scheduled test assignments;
- tests, ordered test parts, questions, and display options;
- `part_skill_mappings` and referenced skills; and
- ready answer-sheet identities and manifest hashes.

`classAssignments[].status` is the teacher-subject assignment lifecycle status.
`classAssignments[].classStatus` is the section cohort lifecycle status and must
not be treated as interchangeable with assignment status. `classAssignmentSchedules`
contains the teacher-owned timetable rows for the downloaded assignments,
including ISO `dayOfWeek`, local `startTime`/`endTime`, `timezoneName`, effective
dates, and schedule lifecycle status.

`captureAllowedNow` and `captureAvailability` are backend-derived from the
assignment status, `openAt`, `closeAt`, and `allowLateCapture`. Supported
availability values are `planned`, `scheduled`, `open`, `closed`,
`late_allowed`, and `archived`.

The snapshot never includes answer keys, correct-option flags, accepted written
answers, rubric solutions, scores, or official analytics. (Since 2026-09-26 the
separate per-assignment evaluation-reference, contract 3.1, does carry answer
keys for a display-only preliminary score on the phone; see
`contracts/mobile-v3/1.12.0/README.md`. This download snapshot is unchanged.) Mobile must apply a
future approved snapshot transaction atomically: validate the complete response,
replace shared downloaded rows, and record the snapshot only after the local
transaction commits.

Authoritative sample:
`src/test/resources/contracts/v3/mobile/download-response.json`.

## Answer-Sheet Manifest

`GET /api/v3/mobile/test-assignments/{assignmentUuid}/answer-sheets/{answerSheetUuid}/manifest`
returns the immutable identity and geometry of one ready generated answer sheet:

- assignment and answer-sheet UUIDs;
- paper dimensions in PDF points;
- test version, manifest version/hash, and scanner version;
- ordered pages, page UUIDs, template/geometry hashes, and QR payload hashes; and
- ordered question response regions and objective option coordinates.

For True/False, paper/UI labels may be `T` and `F`, while `storedValue` remains
`A` for True and `B` for False. A manifest contains no student identity and no
answer or score material. Student/class context comes from the selected
`classListId` during future capture/upload.

Authoritative sample:
`src/test/resources/contracts/v3/mobile/answer-sheet-manifest-response.json`.

## Scan-Page Upload Contract

The intended endpoint is:

```http
POST /api/v3/mobile/scan-pages
Content-Type: multipart/form-data
Authorization: Bearer <v3-session-token>
```

Proposed parts:

- `metadata`: JSON matching `V3ScanPageUploadMetadata`
- `image`: the original JPEG page evidence

Required immutable metadata includes `syncUuid`, `resultUuid`, `scanUuid`,
`scanPageUuid`, `answerSheetUuid`, `pageUuid`, `assignmentUuid`, `classListId`,
page/capture numbers, scanner version, QR payload hash, image SHA-256, and UTC
capture time. UUIDs must be canonical lowercase UUIDs and hashes must be 64
lowercase hexadecimal characters.

The DTO and fixtures are published, but no controller method or persistence
transaction exists yet. Calls therefore remain denied. The endpoint must not be
enabled until it atomically persists original evidence, verifies ownership and
lineage, and implements these retry rules:

1. A first valid `scanPageUuid` creates one central row and returns `created`.
2. An identical retry reuses all UUIDs, returns the same backend identifier, and
   reports `replayed` without inserting a duplicate.
3. Reusing a UUID with a changed immutable hash or identity returns HTTP `409`
   with `IDEMPOTENCY_KEY_REUSE`.
4. One synchronization envelope belongs to one `testAssignmentId`.
5. `syncAction` is `upsert`; a retry never creates additional student results or
   analytics rows.

Contract fixtures:

- `scan-page-upload-metadata.json`
- `scan-page-upload-created-response.json`
- `scan-page-upload-replayed-response.json`
- `scan-page-upload-conflict-response.json`

## Shared Values

- `testAssignments`: `planned`, `open`, `closed`, `archived`
- `scanSessions`: `captured`, `processing`, `needs_verification`, `accepted`,
  `rescan_requested`, `rejected`, `superseded`, `failed`
- `omrDetections`: `detected`, `blank`, `multiple_marks`, `uncertain`
- `studentAnswers`: `answered`, `blank`, `multiple`, `uncertain`, `invalid`,
  `pending_manual`
- `answerEvaluation`: `pending_verification`, `needs_manual_scoring`, `scored`,
  `finalized`
- `testResults`: `draft`, `pending_verification`, `finalized`, `superseded`
- `syncs`: `pending`, `in_progress`, `partial_success`, `success`, `failed`
- `syncItems`: `pending`, `success`, `failed`, `skipped`
- `syncAction`: `upsert`

## Ownership Of Work

Backend owns authentication, same-school/assignment/class-list validation,
schedule decisions, manifests, evidence integrity, idempotency, scoring, and
official analytics. Mobile owns offline storage, capture UX, local retry state,
scanner execution, and preserving UUIDs/hashes across retries.

## Current Mobile Alignment Decisions

- V3 central baseline is `67` tables, `163` foreign keys, `87` check
  constraints, and `99` unique constraints.
- `class_assignment_schedules` is included in `/api/v3/mobile/download`.
- Class lifecycle is exposed separately as `classAssignments[].classStatus`.
- Login uses `POST /api/v3/auth/login`; the response contains `tokenType`,
  `accessToken`, and UTC `expiresAt`. There is currently no refresh endpoint.
  Mobile should store the bearer token in secure storage, call
  `POST /api/v3/auth/logout` when the user signs out, and return to login when
  the token expires or any protected call returns `401`.
- MFA-enabled accounts return `mfaRequired=true` with `mfaChallenge` instead of
  an access token; Mobile must complete the approved MFA flow before expecting a
  bearer token.
- Future scan upload will resolve `answerSheetUuid`, `pageUuid`, and
  `regionUuid` to central numeric IDs on the backend. Mobile must preserve and
  submit UUIDs and hashes; it must not invent central IDs.
- `enhanced_answer_crop` remains unsupported. Use `answer_crop` plus source
  attachment lineage.

Production Mobile SQLite, V1/V2 endpoints, TiDB, the existing detector, and the
answer-sheet generator were not changed in this slice.
