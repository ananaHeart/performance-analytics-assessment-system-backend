# V3 Authoritative Scoring API

**Status:** Implemented locally under the isolated `v3` Spring profile  
**Runtime:** `http://localhost:8082`  
**Database:** `performance_assessment_v3_db`  
**Date:** September 5, 2026

## Scope

This slice finalizes one existing V3 learner result from teacher-finalized
`student_answers`. It does not create scan sessions, upload evidence, verify
answers, migrate Mobile SQLite, compute class analytics, or generate reports.

Raw `omr_detections` never drive official scores. The scoring source is the
finalized `student_answers` graph plus the assessment's questions, answer keys,
rubrics, and active `student_score` performance rule.

## Endpoint

```http
POST /api/v3/scoring/results/{testResultId}/finalize
Authorization: Bearer <V3 teacher access token>
X-Device-Identifier: <optional client identifier>
```

No request body is required. The endpoint returns `200 OK` for both the first
successful finalization and an unchanged idempotent retry.

Only an active teacher may call it. The service independently verifies that the
teacher owns the assessment's class assignment and that the test, learner,
class membership, and school relationships agree.

## Success Response

```json
{
  "success": true,
  "message": "V3 assessment result scored and finalized successfully.",
  "data": {
    "testResultId": 41001,
    "resultUuid": "b7c891c5-67fa-4fb9-a70d-d75fe379b5f6",
    "testAssignmentId": 21001,
    "testId": 1013,
    "classListId": 31001,
    "studentId": 11001,
    "studentName": "Juan Dela Cruz",
    "attemptNumber": 1,
    "totalScore": 7.50,
    "maxScore": 10.00,
    "percentage": 75.00,
    "performanceStatus": "review",
    "performanceLabel": "Review",
    "performanceRuleSetId": 1,
    "itemsEvaluated": 5,
    "scoreVersion": 1,
    "resultStatus": "finalized",
    "scoredAt": "2026-09-05T01:30:00Z",
    "scoreChanged": true,
    "parts": [
      {
        "testPartId": 51001,
        "partOrder": 1,
        "partName": "Objective",
        "totalScore": 4.00,
        "maxScore": 6.00,
        "percentage": 66.67,
        "itemsEvaluated": 3
      },
      {
        "testPartId": 51002,
        "partOrder": 2,
        "partName": "Written",
        "totalScore": 3.50,
        "maxScore": 4.00,
        "percentage": 87.50,
        "itemsEvaluated": 2
      }
    ]
  },
  "errors": null,
  "timestamp": "2026-09-05T01:30:00Z"
}
```

On an unchanged retry, the stored score version and timestamp are retained and
`scoreChanged` is `false`. The retry does not add another scoring audit event.

## Scoring Rules

- `maximum_points` is read per question; there is no global one-point assumption.
- Per-part maximum and score are sums of that part's actual questions.
- Whole-test maximum and score are sums across all parts.
- Percentage is `totalScore / maxScore * 100`, rounded using the active rule set.
- `performanceStatus` and label come from one active school-specific or system
  `performance_rule_sets.metric_scope = student_score` record.
- Multiple Choice and True/False correctness is recomputed server-side from the
  selected option and `answer_keys`.
- Blank objective answers receive zero. Multiple, uncertain, invalid, and
  unresolved objective marks block finalization and require rescan.
- Identification, Enumeration, and Essay use finalized teacher points bounded by
  each question's `maximum_points`.
- A nonblank written answer must retain response text or an `answer_crop` /
  `teacher_evidence` attachment. Handwriting OCR text is not mandatory.
- Essay rubric scores must use the current answer score version, cover every
  rubric criterion, stay within criterion maximums, and sum to `points_earned`.
- All answers must be finalized by the assigned teacher before the result can be
  finalized.

The result update, objective recomputation, optimistic score-version check, and
audit event run in one database transaction. A failure rolls back the operation.

## Error Shape

```json
{
  "success": false,
  "message": "Question 7001 has a multiple, uncertain, invalid, or unresolved objective mark.",
  "data": null,
  "errors": {
    "code": "OBJECTIVE_RESCAN_REQUIRED"
  },
  "timestamp": "2026-09-05T01:30:00Z"
}
```

Common statuses and codes:

| HTTP | Codes |
|---|---|
| `401` | `AUTHENTICATION_REQUIRED` |
| `403` | `TEACHER_ROLE_REQUIRED`, `ACTIVE_SCHOOL_ACCOUNT_REQUIRED`, `RESULT_ACCESS_DENIED`, `ANSWER_VERIFIER_MISMATCH` |
| `404` | `RESULT_NOT_FOUND` |
| `409` | `RESULT_NOT_READY`, `ANSWER_COVERAGE_INVALID`, `SCORING_RULE_NOT_CONFIGURED`, `SCORE_UPDATE_CONFLICT`, `RESULT_CLASS_MISMATCH`, `RESULT_SUPERSEDED`, `ASSESSMENT_NOT_SCORABLE`, `ASSIGNMENT_ARCHIVED`, `ANSWER_KEY_MISSING`, `ANSWER_MISSING`, `ANSWER_NOT_FINALIZED`, `QUESTION_POINTS_INVALID`, `QUESTION_TYPE_UNSUPPORTED`, `ANSWER_KEY_INVALID`, `OBJECTIVE_RESCAN_REQUIRED`, `OBJECTIVE_ANSWER_INVALID`, `WRITTEN_ANSWER_NOT_READY`, `WRITTEN_RESPONSE_MISSING`, `MANUAL_SCORE_OUT_OF_RANGE`, `BLANK_ANSWER_HAS_POINTS`, `ESSAY_SCORING_CONFIGURATION_INVALID`, `RUBRIC_SCORE_INVALID`, `RUBRIC_SCORE_INCOMPLETE`, `SCORE_PRECISION_INVALID` |
| `500` | `SCORING_RULE_INVALID` |

## Integration Boundary

The endpoint requires an existing `test_results` record and exactly one
teacher-finalized `student_answers` row for every question. V3 scan/evidence
upload, retry-safe synchronization, and teacher-verification endpoints are still
separate pending slices. Frontend and Mobile must not calculate or submit an
official aggregate score.

No SQL schema, V1/V2 behavior, Mobile code, or TiDB environment was changed by
this scoring slice.
