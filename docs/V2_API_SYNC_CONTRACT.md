# V2 API and Mobile Sync Contract

**Contract date:** August 9, 2026  
**Clarified:** August 11, 2026  
**Status:** Freeze candidate for backend, mobile, and frontend review  
**Database target:** `performance_assessment_v2_db`

## Purpose

This contract defines the V2 boundary between the Spring Boot backend, React Native offline application, React web dashboard, and the normalized V2 database. It preserves the working V1 clients while V2 is implemented and prevents database column names from becoming an undocumented client contract.

The current `/api/sync` endpoints remain V1-only during the transition. V2 must use versioned endpoints so the deployed mobile application is not broken while backend and SQLite changes are developed.

## Versioning Boundary

| Contract | Endpoint | Status |
| --- | --- | --- |
| V1 download | `GET /api/sync/download/{teacherId}` | Keep operational until V2 acceptance passes |
| V1 upload | `POST /api/sync/upload` | Keep operational until V2 acceptance passes |
| V2 download | `GET /api/v2/sync/download` | New; authenticated teacher is derived from the session/token |
| V2 upload | `POST /api/v2/sync/upload` | New; batch and result UUIDs provide idempotency |

The `{teacherId}` supplied by a client must not be the authorization decision in V2. The backend must use the authenticated user and verify that the user owns the requested class assignment and assessment.

## V2 Download Contract

The download response contains only data authorized for the authenticated teacher:

- `contractVersion`
- `generatedAt`
- `user`
- `classAssignments`
- `classes`
- `classLists`
- `students`
- `tests`
- `testParts`
- `questions`
- `answerKeys`
- `skills`
- `questionMappings`

Required identifiers include `classAssignmentId`, `classId`, `classListId`, `studentId`, `testId`, `testPartId`, `questionId`, and `skillId`. Mobile SQLite may use local primary keys internally, but it must retain these server identifiers for synchronization.

Each question must carry its `testPartId` and part-local `itemNumber`. The backend must provide one answer key per `questionId`. Question-to-skill analytics must use `questionMappings(questionId, skillId)` instead of rebuilding a range after synchronization.

## V2 Upload Contract

Illustrative request shape:

```json
{
  "contractVersion": "2.0",
  "syncUuid": "0a8d6ad4-c259-42b1-bc1d-1587e098ab31",
  "deviceIdentifier": "mobile-installation-id",
  "uploadedAt": "2026-08-09T14:30:00+08:00",
  "testId": 101,
  "results": [
    {
      "resultUuid": "2aa96d8f-9184-4145-b32a-2f2b90847835",
      "syncAction": "create",
      "classListId": 501,
      "attemptNumber": 1,
      "checkedAt": "2026-08-09T14:28:00+08:00",
      "scanSession": {
        "scanUuid": "d7686f15-cfde-4acd-aa95-0bb22b3a27ea",
        "templateVersion": "OMR-A4-10-MC-CTX-V2",
        "scannerVersion": "opencv-prototype-v1",
        "imageHash": "sha256-hex-or-null",
        "scanStatus": "verified",
        "scannedAt": "2026-08-09T14:20:00+08:00",
        "verifiedAt": "2026-08-09T14:28:00+08:00",
        "detections": []
      },
      "answers": [
        {
          "answerUuid": "0c64123d-6d13-40d1-95c8-d54b24dbe600",
          "questionId": 9001,
          "selectedOption": "B",
          "answerStatus": "answered",
          "captureSource": "omr",
          "verifiedAt": "2026-08-09T14:28:00+08:00",
          "correctionReason": null
        }
      ]
    }
  ]
}
```

For a fully manual result, the result still uses the same batch/result/answer UUID rules, but `scanSession` is explicitly `null` and every final answer uses `captureSource: manual`:

```json
{
  "resultUuid": "bbba6d8f-9184-4145-b32a-2f2b90847000",
  "syncAction": "create",
  "classListId": 501,
  "attemptNumber": 1,
  "checkedAt": "2026-08-09T14:35:00+08:00",
  "scanSession": null,
  "answers": [
    {
      "answerUuid": "bf54123d-6d13-40d1-95c8-d54b24dbe001",
      "questionId": 9001,
      "selectedOption": "B",
      "answerStatus": "answered",
      "captureSource": "manual",
      "verifiedAt": "2026-08-09T14:35:00+08:00",
      "correctionReason": null
    }
  ]
}
```

### Fixed-Template OMR Contract Decisions - August 9, 2026

The isolated OpenCV prototype may continue against the following frozen planning rules. These rules do not authorize SQLite, V1 sync, or TiDB integration yet.

1. `classListId` is the authoritative learner identity for an assessment attempt. The QR may include `studentId` only as redundant display or mismatch evidence. The backend resolves the student through `class_lists` and verifies that the membership belongs to the assessment class.
2. `questionId` is the authoritative answer and detection mapping. `itemNumber` remains required for display and diagnostics, but it is not sufficient for V2 persistence or upload.
3. Multiple-choice detections use canonical options `A` through `E`.
4. True/False sheets may print `T/F`, but normalized output uses `A` for True and `B` for False. Raw scanner evidence may retain the printed labels. This keeps `answer_keys.correct_option`, `omr_detections.detected_option`, and `student_answers.selected_option` consistent.
5. The first approved scanner release supports one question type per physical sheet. Mixed Multiple Choice and True/False sheets are deferred until a mixed-template specification and complete physical validation exist.
6. Physical OpenCV validation continues in parallel with DTO/API contract definition. No production integration starts until both are approved.
7. The authoritative 10-item Multiple Choice physical template for the current validated prototype is `OMR-A4-10-MC-CTX-V2`. Earlier sample text that used `OMR-A4-10-MC-V2` is superseded by this clarified template key.

Recommended compact QR identity:

```json
{
  "v": 2,
  "tv": "OMR-A4-10-MC-CTX-V2",
  "t": 101,
  "cl": 501,
  "qt": "multiple_choice",
  "n": 10
}
```

`cl` is `classListId`, `t` is a numeric `testId`, and `qt` is either `multiple_choice` or `true_false`. The authenticated user, not a QR user identifier, determines who performed and verified the scan.

Each `templateVersion` is an immutable registry key for exactly one physical layout. Its registry entry fixes the paper size, item count, question type, printed choices, alignment-marker positions, bubble coordinates, and geometry revision. A change to any of those properties requires a new version. A generator must not reuse `OMR-A4-10-MC-CTX-V2` for another item count or geometry.

Exact raw detection shape:

```json
{
  "questionId": 9001,
  "itemNumber": 1,
  "detectedOption": "A",
  "confidenceScore": 0.9810,
  "detectionStatus": "detected",
  "verificationStatus": "pending",
  "rawMarkInformation": {
    "markedOptions": ["A"],
    "optionScores": {
      "A": 0.9810,
      "B": 0.7310,
      "C": 0.6920,
      "D": 0.7020
    },
    "scoreGap": 0.2500
  },
  "detectedAt": "2026-08-09T14:20:05+08:00"
}
```

Allowed status values:

| Field | Allowed values |
| --- | --- |
| `scanStatus` | `captured`, `processing`, `needs_verification`, `verified`, `failed` |
| `detectionStatus` | `detected`, `blank`, `multiple_marks`, `uncertain` |
| `verificationStatus` | `pending`, `confirmed`, `corrected` |
| `answerStatus` | `answered`, `blank`, `multiple`, `invalid` |
| `captureSource` | `omr`, `teacher_correction`, `manual` |

`rawMarkInformation` is serialized as structured JSON text into `omr_detections.raw_mark`. For `blank`, `multiple_marks`, or `uncertain`, `detectedOption` must be null until teacher verification produces a final answer.

For OMR capture, the result-upload endpoint accepts only a teacher-verified scan session and therefore requires `scanStatus: verified`, `verifiedAt`, and final verified answers. Earlier scanner states remain local workflow states until verification. The backend validates the submitted status and stores the accepted session as `verified`; it does not trust the client to bypass teacher verification. For fully manual capture, no `scan_sessions`, `omr_detections`, or `test_result_scans` row is created; `scanSession` is `null` and final answers use `captureSource: manual`.

Retry-safe identifiers:

| Identifier | Creation and retry rule |
| --- | --- |
| `syncUuid` | One UUID per upload batch; reuse for retrying the same batch. |
| `resultUuid` | One UUID per learner assessment attempt; retain across edits and retries. |
| `scanUuid` | One UUID per captured physical image; retain for upload retries. A new physical recapture receives a new UUID. |
| `answerUuid` | One UUID per result and question; retain when the teacher corrects or retries the answer. |

No `detectionUuid` is required by the V2 central schema. A detection is idempotently identified by `scanUuid + questionId`. Mobile may retain a local-only detection key.

### Approved Recapture Retention Rule

V2 uses the audit-ready design. `test_results` no longer owns one direct `scan_session_id`. Every capture associated with a result is retained through `test_result_scans` with one of these dispositions:

- `selected`: the authoritative teacher-approved capture used for the final verified answers;
- `superseded`: a previously selected capture replaced by a later verified recapture; or
- `rejected`: a capture that the teacher explicitly declined to use.

Each scan can belong to only one result. A result can retain multiple scan links, but a generated unique enforcement key permits at most one `selected` row for that result. The backend must update the previous selected row to `superseded` and insert or promote the replacement inside one transaction. Manual results may have no scan links.

The backend derives `isCorrect`, `pointsEarned`, `totalScore`, `maxScore`, and `itemsEvaluated` only after teacher verification. Mobile may show provisional calculations, but it must not send them as authoritative values.

### Required Upload Rules

1. `syncUuid`, `resultUuid`, `answerUuid`, and `scanUuid` are generated offline and remain stable across retries.
2. A retry with the same UUID updates or returns the same central record; it must not create duplicates.
3. `classListId` identifies the learner membership. The backend verifies that it belongs to the assessment class.
4. `questionId` is authoritative. V2 does not persist result items by `testPartId + itemNumber` alone.
5. `selectedOption` is the final teacher-verified answer. Raw OMR detections remain separate in `omr_detections`.
6. The backend derives `isCorrect`, `pointsEarned`, `totalScore`, `maxScore`, and `itemsEvaluated` from the stored answer key and questions. Client totals are not trusted.
7. Blank, multiple, and invalid answers use `selectedOption: null` with the appropriate `answerStatus`.
8. A corrected OMR answer uses `captureSource: teacher_correction` and requires `correctionReason`.
9. Manual result creation is allowed only through an explicitly authorized workflow and uses `captureSource: manual`.
10. `syncAction` is required per result and maps to `sync_items.sync_action`; allowed values are `create` and `update`.
11. One upload batch is limited to one `testId`; mobile must create a separate `syncUuid` batch for another assessment.
12. All timestamps use ISO 8601 with an offset. The backend normalizes storage to UTC and returns offset-bearing timestamps.

## Upload Response

The response reports the batch and every result independently:

```json
{
  "syncUuid": "0a8d6ad4-c259-42b1-bc1d-1587e098ab31",
  "syncId": 8001,
  "testId": 101,
  "status": "partial_success",
  "completedAt": "2026-08-09T06:30:03Z",
  "items": [
    {
      "resultUuid": "2aa96d8f-9184-4145-b32a-2f2b90847835",
      "syncItemId": 9001,
      "testResultId": 7001,
      "scanSessionId": 6001,
      "status": "success",
      "errorCode": null,
      "errorMessage": null
    },
    {
      "resultUuid": "bbba6d8f-9184-4145-b32a-2f2b90847000",
      "syncItemId": 9002,
      "testResultId": null,
      "scanSessionId": null,
      "status": "failed",
      "errorCode": "INVALID_CLASS_LIST",
      "errorMessage": "Learner is not part of the assessment class."
    }
  ]
}
```

Batch values map to `syncs.sync_status`. Per-result values map to `sync_items.sync_status`. One rejected result must not hide successful results in the same batch.

## Database Mapping

| V2 payload concept | V2 table and field |
| --- | --- |
| Authenticated operator | `users.user_id` |
| Batch idempotency | `syncs.sync_uuid` |
| Assessment-specific batch | `syncs.test_id` |
| Per-result status | `sync_items` |
| Requested result operation | `sync_items.sync_action` |
| Offline result identity | `test_results.result_uuid` |
| Learner membership | `test_results.class_list_id` |
| Raw scan audit | `scan_sessions`, `test_result_scans`, and `omr_detections` |
| Final verified answer | `student_answers` |
| Question identity | `questions.question_id` |
| Correct option | `answer_keys.correct_option` |
| Question skill | `mappings(question_id, skill_id)` |
| Computed recommendation | `intervention_results` |

## V1 Transition Mapping

During the migration window, the backend may accept the existing fields only through the V1 endpoint:

| V1 field | Transitional interpretation | V2 destination |
| --- | --- | --- |
| `localResultId` | Offline result identifier | `result_uuid` after UUID validation/conversion |
| `studentId` | Resolve against assessment class | `class_list_id` |
| `testPartId + itemNumber` | Resolve to exactly one authorized question | `question_id` |
| `isCorrect` | Legacy assertion only; recompute when selected answer exists | Derived from answer key |
| `rawAnswers` | Legacy audit/backfill input | Parse into verified answers only during controlled migration |
| `uploadedAt` | Batch completion candidate | `syncs.completed_at` |

The final V2 client must not depend on this compatibility resolver.

## Validation and Security

- Only active, approved users may authenticate.
- Principals manage approved school setup and teacher accounts; teachers access only assigned classes and assessments.
- The backend enforces ownership even when a hidden or manipulated client sends another user's IDs.
- Login attempts, sensitive mutations, SF1 imports, OMR verification, sync, and denied actions create audit records without passwords or raw tokens.
- Duplicate UUIDs with conflicting ownership or payload identity return a conflict response, not a second record.
- Validation errors return stable machine-readable error codes plus safe user-facing messages.

## Freeze Decision

This document freezes the proposed V2 client boundary, not the implementation. Backend V2 repositories, DTOs, services, security, mobile SQLite, and frontend consumers are still pending. V1 remains the active runtime until V2 migration and acceptance tests pass.
