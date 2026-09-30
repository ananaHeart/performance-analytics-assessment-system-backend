# V3 Assessment Creation API

**Status:** Implemented locally under the isolated `v3` Spring profile  
**Runtime:** `http://localhost:8082`  
**Database:** `performance_assessment_v3_db`  
**Date:** September 3, 2026

## Scope And Boundaries

This slice implements server-side V3 assessment authoring. It does not switch the React frontend, Mobile SQLite, scanner, TiDB, V1, or V2.

Implemented in this slice:

- Teacher-owned assessment reference data
- Draft creation with dynamic test parts
- Multiple Choice, True/False, Identification, Enumeration, and Essay
- Options, answer keys, accepted answers, inline or existing rubrics, and part-skill ranges
- Draft retrieval, listing, replacement-style editing, validation, activation, and soft archive
- Initial test assignment and schedule snapshot
- Transactional persistence and audit events

Still separate or pending:

- React assessment-editor migration to `/api/v3`
- Mobile write/synchronization APIs
- Dynamic mixed-format answer-sheet generation and physical validation
- TiDB V3 deployment

Only the fixed `OMR-A4-10-MC-CTX-V2` sheet remains physically validated. Creating a five-type assessment does not imply that every type already has a production scanner template.

## Authentication And Ownership

All endpoints below require:

```http
Authorization: Bearer <V3 access token>
Content-Type: application/json
```

The Spring Security route requires role `teacher`. The service then independently checks that:

- The account is active and associated with a school.
- The `classAssignmentId` is active.
- The authenticated teacher owns that assignment.
- The assignment belongs to the authenticated teacher's school.
- The selected term belongs to the assignment's academic year.
- Every skill belongs to the selected term, grade level, and subject.
- Existing rubrics belong to the same school and are usable by the teacher.

IDs are not interchangeable. `classAssignmentId`, `classId`, `testId`, `testAssignmentId`, `testPartId`, `questionId`, `skillId`, and `rubricId` identify different records.

## Endpoint Matrix

| Method | Path | Purpose | Result |
|---|---|---|---|
| GET | `/api/v3/assessments/reference-data` | Discover owned assignments and active question types | `200` |
| GET | `/api/v3/assessments/reference-data?classAssignmentId={id}` | Load terms for one assignment | `200` |
| GET | `/api/v3/assessments/reference-data?classAssignmentId={id}&termPeriodId={id}` | Load filtered skills and teacher rubrics | `200` |
| POST | `/api/v3/assessments` | Create a complete draft graph | `201` |
| GET | `/api/v3/assessments?classAssignmentId={id}` | List owned assessments | `200` |
| GET | `/api/v3/assessments/{testId}` | Read one complete assessment graph | `200` |
| PUT | `/api/v3/assessments/{testId}` | Replace the editable content of a draft | `200` |
| POST | `/api/v3/assessments/{testId}/activate` | Validate and activate the draft | `200` |
| POST | `/api/v3/assessments/{testId}/archive` | Soft-archive an owned assessment | `200` |

There is no pagination in this first V3 assessment slice.

## Reference-Data Sequence

Use the endpoint progressively because skills depend on assignment context and term.

1. Call without query parameters and select an owned `classAssignmentId`.
2. Call with `classAssignmentId` and select a `termPeriodId`.
3. Call with both IDs and select one or more returned `skillId` values.

The response contains:

- `assignments`
- `selectedAssignment`
- `termPeriods`
- `questionTypes`
- `skills`
- `rubrics`
- `testTypes`: `diagnostic`, `exam`, `long_test`, `other`, `quiz`
- `responseRegionSizes`: `none`, `short`, `medium`, `long`, `full_page`

## Create And Update Request

The same request contract is used by `POST` and `PUT`.

```json
{
  "classAssignmentId": 77,
  "termPeriodId": 9,
  "testName": "Five Type Assessment",
  "testType": "quiz",
  "instructions": "Read every part carefully.",
  "openAt": "2026-09-01T02:00:00Z",
  "closeAt": "2026-09-01T04:00:00Z",
  "allowLateCapture": false,
  "confirmOutsideClassSchedule": false,
  "outsideClassScheduleReason": null,
  "parts": [
    {
      "partOrder": 1,
      "partName": "Multiple Choice",
      "questionTypeCode": "multiple_choice",
      "numberOfItems": 1,
      "pointsPerItem": 1,
      "partInstructions": "Select the best answer.",
      "questions": [
        {
          "itemNumber": 1,
          "questionText": "Which state has a fixed volume but no fixed shape?",
          "maximumPoints": 1,
          "options": [
            { "optionKey": "A", "optionText": "Solid", "optionOrder": 1 },
            { "optionKey": "B", "optionText": "Liquid", "optionOrder": 2 },
            { "optionKey": "C", "optionText": "Gas", "optionOrder": 3 },
            { "optionKey": "D", "optionText": "Plasma", "optionOrder": 4 }
          ],
          "correctOptionKey": "B",
          "answerExplanation": "A liquid keeps its volume but takes the container shape."
        }
      ],
      "skillMappings": [
        { "startItemNumber": 1, "endItemNumber": 1, "skillIds": [501] }
      ]
    },
    {
      "partOrder": 2,
      "partName": "True or False",
      "questionTypeCode": "true_false",
      "numberOfItems": 1,
      "pointsPerItem": 1,
      "questions": [
        {
          "itemNumber": 1,
          "questionText": "Water boils at 100 degrees Celsius at sea level.",
          "maximumPoints": 1,
          "correctOptionKey": "A"
        }
      ],
      "skillMappings": [
        { "startItemNumber": 1, "endItemNumber": 1, "skillIds": [501] }
      ]
    },
    {
      "partOrder": 3,
      "partName": "Identification",
      "questionTypeCode": "identification",
      "numberOfItems": 1,
      "pointsPerItem": 2,
      "questions": [
        {
          "itemNumber": 1,
          "questionText": "Name the process plants use to make food.",
          "maximumPoints": 2,
          "maximumResponseLength": 120,
          "expectedResponseCount": 1,
          "responseRegionSize": "short",
          "matchingMode": "normalized",
          "acceptedAnswers": [
            {
              "acceptedText": "Photosynthesis",
              "points": 2,
              "primary": true,
              "caseSensitive": false
            }
          ]
        }
      ],
      "skillMappings": [
        { "startItemNumber": 1, "endItemNumber": 1, "skillIds": [501] }
      ]
    },
    {
      "partOrder": 4,
      "partName": "Enumeration",
      "questionTypeCode": "enumeration",
      "numberOfItems": 1,
      "pointsPerItem": 2,
      "questions": [
        {
          "itemNumber": 1,
          "questionText": "Give two states of matter.",
          "maximumPoints": 2,
          "maximumResponseLength": 200,
          "expectedResponseCount": 2,
          "responseRegionSize": "medium",
          "matchingMode": "normalized",
          "acceptedAnswers": [
            {
              "answerOrder": 1,
              "acceptedText": "Solid",
              "points": 1,
              "primary": true,
              "caseSensitive": false
            },
            {
              "answerOrder": 2,
              "acceptedText": "Liquid",
              "points": 1,
              "primary": true,
              "caseSensitive": false
            }
          ]
        }
      ],
      "skillMappings": [
        { "startItemNumber": 1, "endItemNumber": 1, "skillIds": [501] }
      ]
    },
    {
      "partOrder": 5,
      "partName": "Essay",
      "questionTypeCode": "essay",
      "numberOfItems": 1,
      "pointsPerItem": 5,
      "questions": [
        {
          "itemNumber": 1,
          "questionText": "Explain how matter changes state.",
          "maximumPoints": 5,
          "responseInstructions": "Use complete sentences.",
          "maximumResponseLength": 1000,
          "responseRegionSize": "long",
          "forcePageBreakBefore": true,
          "rubric": {
            "rubricName": "Science Explanation Rubric",
            "description": "Scores scientific accuracy and clarity.",
            "criteria": [
              {
                "criterionOrder": 1,
                "criterionName": "Scientific accuracy",
                "criterionDescription": "Uses correct scientific ideas.",
                "maximumPoints": 3,
                "required": true
              },
              {
                "criterionOrder": 2,
                "criterionName": "Clarity",
                "criterionDescription": "Explains the answer clearly.",
                "maximumPoints": 2,
                "required": true
              }
            ]
          }
        }
      ],
      "skillMappings": [
        { "startItemNumber": 1, "endItemNumber": 1, "skillIds": [501] }
      ]
    }
  ]
}
```

Replace the illustrative IDs with values returned by reference data. Item numbers are local to each part and must start at 1.

`openAt` and `closeAt`, when present, must stay inside the selected term. If an
active class timetable exists and `closeAt` is outside its effective meeting
window, the request must set `confirmOutsideClassSchedule` to `true` and provide
an `outsideClassScheduleReason` of at least five characters. The response then
includes the persisted confirmation flag, reason, teacher user ID, and UTC
confirmation timestamp. Assignments without timetable rows remain compatible.

## Question-Type Rules

| Type | Required authoring data | Server behavior |
|---|---|---|
| `multiple_choice` | Exactly A-D options in orders 1-4 and `correctOptionKey` A-D | Stores an option answer key |
| `true_false` | No `options`; `correctOptionKey` A or B | Generates A=True and B=False |
| `identification` | One or more accepted variants; one response; `exact` or `normalized` matching | Exactly one primary variant; every variant awards full question points |
| `enumeration` | `expectedResponseCount`; ordered accepted answers with explicit points | Every order needs a primary answer; primary-order points total `maximumPoints` |
| `essay` | Manual scoring or exactly one `rubricId`/inline `rubric` | Rubric criteria total must equal `maximumPoints` |

Additional rules:

- Part orders must be unique and contiguous from 1.
- Item numbers must be unique and contiguous from 1 within each part.
- `numberOfItems` must equal the actual question count.
- Maximum 200 questions per part and 500 per assessment.
- Every local item must be covered by at least one skill range.
- Skill ranges may overlap when an item assesses more than one skill.
- `maximumPoints` defaults to `pointsPerItem` when omitted.
- `closeAt`, when both values exist, must be later than `openAt`.
- Activation also rejects an already elapsed `closeAt`.
- A draft may be edited. Active content is locked; archive is a soft lifecycle transition.

## Success Envelope

All responses use the shared envelope:

```json
{
  "success": true,
  "message": "V3 draft assessment created successfully.",
  "data": {
    "testId": 123,
    "testUuid": "3bb925cb-75b1-4c73-a9d3-1c2a393838f4",
    "testAssignmentId": 456,
    "assignmentUuid": "b258eb76-a8a0-4911-8fdc-a4fcfa32ec57",
    "classAssignmentId": 77,
    "termPeriodId": 9,
    "testName": "Five Type Assessment",
    "testType": "quiz",
    "totalItems": 5,
    "maximumScore": 11,
    "status": "draft",
    "versionNumber": 1,
    "assignmentStatus": "planned",
    "allowLateCapture": false,
    "parts": []
  },
  "errors": null,
  "timestamp": "2026-09-01T00:00:00Z"
}
```

The actual `parts` array contains the saved questions, generated IDs/UUIDs, options, answer keys, accepted answers, rubrics, and expanded part-skill mappings.

## Error Examples

Unauthenticated (`401`):

```json
{
  "success": false,
  "message": "Authentication is required.",
  "data": null,
  "errors": { "code": "AUTHENTICATION_REQUIRED" },
  "timestamp": "2026-09-01T00:00:00Z"
}
```

Wrong role or ownership (`403`):

```json
{
  "success": false,
  "message": "The class assignment does not belong to the authenticated teacher.",
  "data": null,
  "errors": { "code": "CLASS_ASSIGNMENT_FORBIDDEN" },
  "timestamp": "2026-09-01T00:00:00Z"
}
```

Field validation (`400`):

```json
{
  "success": false,
  "message": "Assessment validation failed.",
  "data": null,
  "errors": {
    "code": "VALIDATION_FAILED",
    "parts.skillMappings": "Every item must be covered by at least one skill range. Missing local item 1."
  },
  "timestamp": "2026-09-01T00:00:00Z"
}
```

Lifecycle conflict (`409`):

```json
{
  "success": false,
  "message": "Only draft assessments may be edited. Create a new version for active content.",
  "data": null,
  "errors": { "code": "ASSESSMENT_CONTENT_LOCKED" },
  "timestamp": "2026-09-01T00:00:00Z"
}
```

## Transaction Behavior

Create and update execute in Spring transactions. A failure in any nested part, question, option, answer key, accepted answer, rubric, criterion, mapping, assignment, or audit write rolls back the complete operation. Update writes a new draft content graph only after ownership and validation succeed. An unchanged draft header or schedule is treated as an idempotent no-op instead of a lifecycle conflict. Activation validates the persisted graph and uses optimistic state checks before changing status.

## Postman Verification

Import `docs/postman/V3_Assessment_Creation.postman_collection.json` and run the requests in order:

1. Login Teacher
2. Discover Owned Assignments
3. Load Assignment Terms
4. Load Term Skills
5. Create Five-Type Draft
6. List Assessments
7. Get Assessment
8. Update Draft
9. Activate Assessment
10. Reject Active Update
11. Archive Assessment
12. Reject Missing Token

The collection stores the access token and discovered IDs as collection variables. Set only `teacherEmail` and `teacherPassword`; do not hardcode credentials into source control.

Authenticated local acceptance on September 1, 2026 passed the complete flow:
create `201`; list, detail, draft update, activate, and archive `200`; active
update rejected with `409`. The accepted fixture contained all five question
types, 5 parts, 5 items, and an 11-point maximum score.
