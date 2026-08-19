# V2 Assessment Creation API

**Phase:** Phase 2 of 5 - V2 Assessment Creation  
**Status:** Backend implementation checkpoint  
**Runtime boundary:** `/api/v2/**` only, active only under the `v2` Spring profile  
**Database target:** `performance_assessment_v2_db`  

## Scope

This phase implements the first V2 assessment setup workflow:

```text
Authenticated Teacher
-> Active Class Assignment
-> Create Draft Test
-> Add Test Parts
-> Add Questions
-> Add Answer Keys
-> Expand Skill Ranges to mappings
-> Validate Complete Assessment
-> Activate Assessment
```

This phase does **not** implement OMR upload, mobile SQLite, synchronization, scoring/results upload, analytics, intervention generation, frontend changes, or TiDB migration.

## Endpoints

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/v2/assessments/reference-data` | Returns active teacher class assignments. With `classAssignmentId`, also returns term periods. With `classAssignmentId` and `termPeriodId`, also returns valid skills. |
| `POST` | `/api/v2/assessments` | Creates a complete draft assessment with parts, questions, answer keys, and mappings. |
| `GET` | `/api/v2/assessments?classAssignmentId={id}` | Lists assessments owned by the authenticated teacher, optionally filtered by assignment. |
| `GET` | `/api/v2/assessments/{testId}` | Gets one complete assessment detail. |
| `PUT` | `/api/v2/assessments/{testId}` | Replaces a draft assessment's header, parts, questions, answer keys, and mappings. |
| `POST` | `/api/v2/assessments/{testId}/activate` | Activates a complete draft assessment after validation. |
| `POST` | `/api/v2/assessments/{testId}/archive` | Archives an owned assessment. |

All endpoints require an authenticated V2 bearer session. The service layer still checks teacher role and ownership; frontend hiding is not treated as authorization.

## Create / Update Request

```json
{
  "classAssignmentId": 500,
  "termPeriodId": 11,
  "testName": "Quiz 1",
  "testType": "quiz",
  "testDate": "2026-08-20",
  "instructions": "Shade only one answer.",
  "parts": [
    {
      "partOrder": 1,
      "partName": "Part I",
      "partType": "multiple_choice",
      "pointsPerItem": 1.00,
      "questions": [
        {
          "itemNumber": 1,
          "questionText": "What is 1 + 1?",
          "optionA": "1",
          "optionB": "2",
          "optionC": "3",
          "optionD": "4",
          "optionE": null,
          "correctOption": "B",
          "skillIds": []
        },
        {
          "itemNumber": 2,
          "questionText": "What is 2 + 2?",
          "optionA": "2",
          "optionB": "3",
          "optionC": "4",
          "optionD": "5",
          "optionE": null,
          "correctOption": "C",
          "skillIds": []
        }
      ],
      "skillMappings": [
        {
          "fromItemNumber": 1,
          "toItemNumber": 2,
          "skillIds": [100]
        }
      ]
    },
    {
      "partOrder": 2,
      "partName": "Part II",
      "partType": "true_false",
      "pointsPerItem": 1.00,
      "questions": [
        {
          "itemNumber": 1,
          "questionText": "Java is strongly typed.",
          "correctOption": "A",
          "skillIds": [101]
        }
      ]
    }
  ]
}
```

Notes:

- `testType` accepts `quiz`, `exam`, `diagnostic`, `long_test`, or `other`.
- `partType` accepts only `multiple_choice` and `true_false`.
- For True/False, backend stores `A = True` and `B = False`; `C`, `D`, and `E` are not valid answer keys.
- `skillMappings` is the GUI range mapping input. The backend expands each range into individual `mappings(question_id, skill_id)` rows.
- A question may also provide direct `skillIds`; direct IDs and range IDs are merged.
- Identification and Enumeration are intentionally excluded from initial OMR-ready V2.

## Response Shape

```json
{
  "success": true,
  "message": "Draft assessment created successfully.",
  "data": {
    "testId": 900,
    "classAssignmentId": 500,
    "classId": 700,
    "academicYearId": 1,
    "yearName": "2026-2027",
    "gradeLevelId": 1,
    "gradeLevelName": "Grade 7",
    "sectionId": 10,
    "sectionName": "Rizal",
    "subjectId": 3,
    "subjectName": "Computer",
    "termPeriodId": 11,
    "termName": "First Quarter",
    "testName": "Quiz 1",
    "testType": "quiz",
    "testDate": "2026-08-20",
    "instructions": "Shade only one answer.",
    "totalItems": 3,
    "status": "draft",
    "parts": [
      {
        "testPartId": 1001,
        "partOrder": 1,
        "partName": "Part I",
        "partType": "multiple_choice",
        "numberOfItems": 2,
        "pointsPerItem": 1.00,
        "questions": [
          {
            "questionId": 2001,
            "itemNumber": 1,
            "correctOption": "B",
            "skillIds": [100]
          }
        ]
      }
    ]
  }
}
```

## Authorization and Validation Rules

- Only authenticated V2 teachers may create, list, view, edit, activate, or archive assessments.
- The selected `classAssignmentId` must belong to the authenticated teacher.
- The class assignment and class must both be active.
- The assignment must belong to the teacher's school.
- `termPeriodId` must belong to the same academic year as the assignment's class.
- New assessments start as `draft`.
- Draft edit uses full replacement of the assessment parts/questions/keys/mappings.
- A draft cannot be moved to another class assignment.
- `partOrder` must be unique per assessment.
- `itemNumber` must be unique inside a test part.
- Every question must have exactly one answer key.
- Every question must map to at least one valid skill.
- Skill IDs must match the assessment term, class grade level, assignment subject, and active root tag.
- `total_items` and `number_of_items` are computed snapshots based on actual questions.
- Activation fails when there are no questions, missing answer keys, missing skill mappings, invalid True/False keys, or item-count snapshot mismatches.

## Transaction Behavior

`POST /api/v2/assessments` and `PUT /api/v2/assessments/{testId}` are service-level transactions. The backend inserts or replaces the test header, parts, questions, answer keys, and mappings as one unit. If a conflict or database constraint error occurs, Spring rolls the transaction back and no success audit is recorded.

`PUT` deletes existing draft `test_parts`; the schema cascades child `questions`, `answer_keys`, and `mappings`, then reinserts the submitted graph. This is allowed only for `draft` assessments.

`activate` and `archive` lock the target test row before status change and write an `audit_logs` record after a successful mutation.

## Implemented Files

- `src/main/java/com/capstone/assessment/v2/assessment/controller/V2AssessmentController.java`
- `src/main/java/com/capstone/assessment/v2/assessment/service/V2AssessmentService.java`
- `src/main/java/com/capstone/assessment/v2/assessment/repository/V2AssessmentRepository.java`
- `src/main/java/com/capstone/assessment/v2/assessment/dto/*`
- `src/main/java/com/capstone/assessment/v2/assessment/model/*`
- `src/test/java/com/capstone/assessment/v2/assessment/service/V2AssessmentServiceTest.java`

## Focused Test Coverage

The focused `V2AssessmentServiceTest` covers:

- teacher creates a valid draft assessment;
- teacher adds multiple test parts in one draft payload;
- teacher cannot use another teacher's assignment;
- principal cannot create assessments;
- unauthenticated service calls cannot create assessments;
- duplicate `part_order` is rejected;
- duplicate item number within a part is rejected;
- missing answer keys block activation;
- missing skill mappings block activation;
- term period from another academic year is rejected;
- True/False accepts only `A` or `B`;
- repository failure during save does not record a success audit.

Latest focused result:

```text
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
```

## Remaining Phase 2 Gate Items

- Run the full Maven test suite after the local database is available.
- Verify the endpoints through Postman against `performance_assessment_v2_db`.
- Owner must approve the Postman results before Phase 3 starts.

## Safety Confirmation

This phase does not modify:

- V1 `/api/**` endpoints;
- existing V1 database schema;
- deployed TiDB database;
- React frontend;
- React Native mobile app or SQLite schema;
- OMR upload/sync/analytics/intervention runtime.
