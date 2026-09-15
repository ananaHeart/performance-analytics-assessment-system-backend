# V2 Analytics API

## Source of Truth

Official analytics use only backend-scored `test_results` and teacher-verified
`student_answers`. Raw `omr_detections` and upload-attempt counts are not used as
student performance records.

All endpoints require `Authorization: Bearer <token>` and use the standard
`ApiResponse` envelope. A teacher may read only assessments owned through their
active school assignment. A principal may read assessments inside their school.

## Implemented Endpoints

### Skill Mastery / LMS

`GET /api/v2/analytics/lms?testId=1006`

```json
{
  "success": true,
  "message": "Assessment skill mastery retrieved successfully.",
  "data": [
    {
      "skillId": 55,
      "competencyId": 44,
      "competencyName": "Chemical Reactions",
      "earnedPoints": 28.00,
      "possiblePoints": 40.00,
      "masteryRate": 70.00,
      "status": "Review",
      "respondentCount": 8,
      "affectedStudents": 4
    }
  ],
  "errors": null,
  "timestamp": "2026-08-24T08:00:00Z"
}
```

Formula: `masteryRate = earnedPoints / possiblePoints * 100`.

### Item Analysis

`GET /api/v2/analytics/item-analysis?testId=1006`

```json
{
  "success": true,
  "message": "Item analysis retrieved successfully.",
  "data": [
    {
      "questionId": 70041,
      "testPartId": 2001,
      "itemNumber": 1,
      "skillId": 55,
      "competencyId": 44,
      "competencyName": "Chemical Reactions",
      "correctResponses": 6,
      "totalResponses": 8,
      "correctnessPercentage": 75.00,
      "difficultyLevel": "Moderate"
    }
  ],
  "errors": null,
  "timestamp": "2026-08-24T08:00:00Z"
}
```

Each verified answer is counted once even when a question has multiple skill
mappings.

### Test-Part Results

`GET /api/v2/analytics/test-part-results?testId=1006&testPartId=2001`

```json
{
  "success": true,
  "message": "Test-part student results retrieved successfully.",
  "data": [
    {
      "studentId": 3001,
      "studentName": "Juan Miguel Dela Cruz",
      "studentLrn": "100000000001",
      "testId": 1006,
      "testPartId": 2001,
      "partScore": 3.00,
      "maxScore": 5.00,
      "percentage": 60.00,
      "performance": "Developing",
      "checkedAt": "2026-08-24T04:00:00Z",
      "syncedAt": "2026-08-24T04:01:00Z"
    }
  ],
  "errors": null,
  "timestamp": "2026-08-24T08:00:00Z"
}
```

The sync timestamp comes from the successful `sync_items.synced_at` record. It
does not use the test creation date.

### Sync Activity

`GET /api/v2/analytics/sync-activity`

Optional filters: `gradeLevelId`, `sectionId`, `teacherUserId`, `subjectId`, and
`classId`.

- A teacher always receives only their own upload batches. Supplying another
  `teacherUserId` is rejected.
- A principal receives only batches initiated by users in their school and may
  filter by a teacher in that school.
- The response counts successful central `test_results`, not mobile retries.

```json
{
  "success": true,
  "message": "Synchronization activity retrieved successfully.",
  "data": [
    {
      "syncId": 77,
      "syncUuid": "4c66ff15-3c07-428c-939d-25213dcd4514",
      "teacherUserId": 900001,
      "teacherName": "Heart Millan Anana",
      "testId": 1006,
      "testName": "English Quiz 2",
      "classId": 910002,
      "gradeLevelName": "Grade 7",
      "sectionName": "Rizal",
      "subjectName": "English",
      "syncStatus": "success",
      "successfulResults": 8,
      "failedResults": 0,
      "skippedResults": 0,
      "startedAt": "2026-08-24T04:00:00Z",
      "completedAt": "2026-08-24T04:01:00Z",
      "lastSyncedAt": "2026-08-24T04:01:00Z"
    }
  ]
}
```

### Principal School Overview

`GET /api/v2/analytics/school-overview`

This endpoint is Principal-only. It accepts the same optional filters as sync
activity and returns `totalAssessments`, verified-answer LMS rows, grade-level
mastery, and per-assessment trends. Every query is constrained by the
authenticated Principal's `schoolId`.

```json
{
  "success": true,
  "message": "School analytics retrieved successfully.",
  "data": {
    "totalAssessments": 5,
    "lms": [],
    "gradeLevels": [
      {
        "gradeLevelId": 7,
        "gradeLevelName": "Grade 7",
        "earnedPoints": 56.00,
        "possiblePoints": 80.00,
        "masteryRate": 70.00,
        "respondentCount": 8
      }
    ],
    "trends": []
  }
}
```

## Configurable Rules

Defaults are declared in `application-v2.properties` and may be overridden by
environment variables:

| Result | Default rule |
|---|---:|
| Maintain / Mastered | at least 80% |
| Review / Developing | at least 60% |
| Reteach | at least 40% |
| Priority Intervention / Needs Support | below the applicable threshold |
| Easy item | at least 80% correct |
| Moderate item | at least 50% correct |
| Difficult item | below 50% correct |

## Frontend Integration

The analytics screen must not combine a V2 assessment ID with legacy `/api/*`
analytics calls. Authenticated helpers in `apiV2Client.js` cover all endpoints
above and pass the current V2 bearer token.

Replace these legacy calls in `ClassRecordsPage.jsx`:

| Legacy call | V2 replacement |
|---|---|
| `GET /api/assessments/{testId}` | `GET /api/v2/assessments/{testId}` |
| `GET /api/analytics/lms` | `GET /api/v2/analytics/lms` |
| `GET /api/analytics/test-part-results` | `GET /api/v2/analytics/test-part-results` |

V2 intervention, student-skill-mastery, and export endpoints are still pending.
The frontend must not silently fall back to legacy endpoints for those features.
