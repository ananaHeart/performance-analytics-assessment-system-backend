# V3 Reports API

**Status:** Slice 1 implemented locally under the isolated `v3` Spring profile  
**Runtime:** `http://localhost:8080`  
**Database:** `performance_assessment_v3_db`  
**Date:** September 5, 2026

## Scope

This slice provides role-aware report filters and one authoritative Assessment
Results report. It reads existing V3 records only; no schema migration is
required.

Official score fields come only from finalized `test_results` snapshots that
were computed by the V3 backend from finalized `student_answers`. Raw
`omr_detections`, frontend calculations, synchronization attempts, draft
results, and superseded attempts do not produce official report metrics.

## Authentication And Ownership

Both endpoints require:

```http
Authorization: Bearer <V3 access token>
```

- An active Principal can read records belonging to the Principal's school.
- An active Teacher can read only their own class assignments and assessments.
- The backend derives `userId`, role, and `schoolId` from the access token.
- `testId` and `classAssignmentId` are validated as distinct relationships.
- A cross-school or another-teacher request returns `403`; frontend filtering
  is not treated as authorization.

## Reference Data

```http
GET /api/v3/reports/reference-data
```

The response contains the records that the current user may use in cascading
report filters. Principal responses include a school-scoped teacher list.
Teacher responses return an empty `teachers` list because the teacher identity
is already fixed by the authenticated account.

```json
{
  "success": true,
  "message": "V3 report reference data retrieved successfully.",
  "data": {
    "role": "principal",
    "school": {
      "schoolId": "V2-LOCAL-TEST",
      "schoolName": "V2 Local Test School"
    },
    "academicYears": [
      {
        "academicYearId": 910003,
        "yearName": "2025-2026",
        "startDate": "2025-06-01",
        "endDate": "2026-03-31",
        "status": "active"
      }
    ],
    "termPeriods": [
      {
        "termPeriodId": 1,
        "academicYearId": 910003,
        "termName": "First Quarter",
        "termOrder": 1,
        "startAt": "2025-06-01T00:00:00Z",
        "endAt": "2025-08-31T23:59:59Z",
        "status": "active"
      }
    ],
    "gradeLevels": [
      {
        "gradeLevelId": 1,
        "gradeLevelName": "Grade 7"
      }
    ],
    "classes": [
      {
        "classId": 910002,
        "academicYearId": 910003,
        "academicYearName": "2025-2026",
        "gradeLevelId": 1,
        "gradeLevelName": "Grade 7",
        "sectionId": 910002,
        "sectionName": "Rizal",
        "status": "active"
      }
    ],
    "teachers": [
      {
        "teacherUserId": 910018,
        "fullName": "Heart Millan Anana",
        "status": "active"
      }
    ],
    "subjects": [
      {
        "subjectId": 1,
        "subjectCode": "ENG",
        "subjectName": "English"
      }
    ],
    "classAssignments": [
      {
        "classAssignmentId": 910016,
        "classId": 910002,
        "teacherUserId": 910018,
        "subjectId": 1,
        "academicYearId": 910003,
        "gradeLevelId": 1,
        "sectionId": 910002,
        "teacherName": "Heart Millan Anana",
        "subjectName": "English",
        "academicYearName": "2025-2026",
        "gradeLevelName": "Grade 7",
        "sectionName": "Rizal",
        "status": "active"
      }
    ],
    "assessments": [
      {
        "testId": 1013,
        "testAssignmentId": 920001,
        "classAssignmentId": 910016,
        "termPeriodId": 1,
        "testName": "English Quiz 1",
        "testType": "quiz",
        "status": "active",
        "assignmentStatus": "open",
        "openAt": "2025-08-20T00:00:00Z",
        "closeAt": "2025-08-21T00:00:00Z"
      }
    ],
    "students": [
      {
        "studentId": 930001,
        "classListId": 940001,
        "classId": 910002,
        "studentLrn": "100000000001",
        "fullName": "Juan Dela Cruz",
        "enrollmentStatus": "enrolled"
      }
    ]
  },
  "errors": null,
  "timestamp": "2026-09-05T02:00:00Z"
}
```

All arrays are present and may be empty. Frontend must join/filter by IDs, not
by display labels.

## Assessment Results

```http
GET /api/v3/reports/assessment-results?testId={testId}&classAssignmentId={classAssignmentId}
```

Both query parameters are required positive identifiers. The selected test
must be assigned through the selected class assignment.

```json
{
  "success": true,
  "message": "V3 assessment-results report generated successfully.",
  "data": {
    "reportType": "assessment_results",
    "generatedAt": "2026-09-05T02:00:00Z",
    "scope": {
      "schoolId": "V2-LOCAL-TEST",
      "schoolName": "V2 Local Test School",
      "academicYearId": 910003,
      "academicYearName": "2025-2026",
      "termPeriodId": 1,
      "termName": "First Quarter",
      "classId": 910002,
      "gradeLevelId": 1,
      "gradeLevelName": "Grade 7",
      "sectionId": 910002,
      "sectionName": "Rizal",
      "classAssignmentId": 910016,
      "teacherUserId": 910018,
      "teacherName": "Heart Millan Anana",
      "subjectId": 1,
      "subjectName": "English",
      "testId": 1013,
      "testAssignmentId": 920001,
      "testName": "English Quiz 1",
      "testType": "quiz",
      "testStatus": "active",
      "assignmentStatus": "open",
      "openAt": "2025-08-20T00:00:00Z",
      "closeAt": "2025-08-21T00:00:00Z"
    },
    "calculationPolicy": {
      "scoreSource": "Finalized test_results snapshots computed by the V3 backend from finalized student_answers.",
      "inclusionRule": "One latest non-superseded result per class-list enrollment; score metrics are exposed only for finalized results.",
      "percentageFormula": "Class mean percentage is weighted: sum(earnedPoints) / sum(maximumPoints) * 100 for finalized results.",
      "rounding": "HALF_UP to 2 decimal places.",
      "performanceRuleSets": [
        {
          "performanceRuleSetId": 1,
          "ruleSetName": "Default student score rules",
          "ruleVersion": "1.0"
        }
      ]
    },
    "dataStatus": "partial",
    "warnings": [
      {
        "code": "PENDING_TEACHER_VERIFICATION",
        "message": "Some submitted results are still pending teacher verification and are excluded from score metrics."
      }
    ],
    "summary": {
      "studentCount": 8,
      "submittedCount": 6,
      "verifiedCount": 5,
      "pendingCount": 1,
      "maximumPoints": 10.00,
      "classMeanPoints": 7.40,
      "classMeanPercentage": 74.00
    },
    "rows": [
      {
        "studentId": 930001,
        "classListId": 940001,
        "studentLrn": "100000000001",
        "fullName": "Juan Dela Cruz",
        "enrollmentStatus": "enrolled",
        "testResultId": 950001,
        "resultStatus": "finalized",
        "submittedAt": "2026-09-05T01:40:00Z",
        "verifiedAt": "2026-09-05T01:50:00Z",
        "earnedPoints": 8.00,
        "maximumPoints": 10.00,
        "percentage": 80.00,
        "performanceStatusCode": "maintain",
        "performanceStatusLabel": "Maintain",
        "pendingTeacherVerificationCount": 0
      },
      {
        "studentId": 930002,
        "classListId": 940002,
        "studentLrn": "100000000002",
        "fullName": "Ana Reyes",
        "enrollmentStatus": "enrolled",
        "testResultId": null,
        "resultStatus": "not_submitted",
        "submittedAt": null,
        "verifiedAt": null,
        "earnedPoints": null,
        "maximumPoints": null,
        "percentage": null,
        "performanceStatusCode": null,
        "performanceStatusLabel": null,
        "pendingTeacherVerificationCount": null
      }
    ]
  },
  "errors": null,
  "timestamp": "2026-09-05T02:00:00Z"
}
```

### Calculation Rules

- The roster is returned even when a learner has not submitted a result.
- Only the latest non-superseded result per `classListId` is selected.
- `earnedPoints`, `maximumPoints`, `percentage`, and performance status are
  `null` unless the result status is `finalized`.
- `classMeanPoints` is the arithmetic mean of finalized earned points.
- `classMeanPercentage` is weighted as
  `sum(earnedPoints) / sum(maximumPoints) * 100`.
- Numeric metrics are rounded to two decimal places using `HALF_UP`.
- `submittedCount` includes results with `submittedAt` and finalized results with
  stored earned/maximum score snapshots. Mobile finalization may leave
  `submittedAt` null; the report preserves that null timestamp and still counts
  the finalized result once. Draft/pending rows without `submittedAt` are not
  counted as submissions or included in official score metrics.
- `dataStatus` is `empty` when `submittedCount` is zero, `available` when every
  roster learner has a finalized score snapshot, and `partial` otherwise.
- Unknown/unavailable metrics are `null`, not a fabricated zero.
- A historical finalized result retains its stored maximum and performance rule
  snapshots even if the assessment or active rule later changes.

Possible warning codes:

| Code | Meaning |
|---|---|
| `NO_SUBMITTED_RESULTS` | No learner result has been submitted. |
| `PENDING_TEACHER_VERIFICATION` | Draft/pending results are excluded from official metrics. |
| `STUDENTS_WITHOUT_SUBMISSION` | At least one roster learner has no submitted result. |
| `RESULT_MAXIMUM_SNAPSHOT_DIFFERS` | A historical finalized result retained a different maximum snapshot. |
| `PERFORMANCE_RULE_LABEL_UNAVAILABLE` | A stored rule label could not be parsed; normalized status text was used. |

## Errors

Errors use the normal `ApiResponse` envelope:

```json
{
  "success": false,
  "message": "The selected assessment and class assignment do not belong together.",
  "data": null,
  "errors": {
    "code": "REPORT_FILTER_MISMATCH",
    "testId": "The assessment is not assigned through this classAssignmentId.",
    "classAssignmentId": "The class assignment does not own this assessment assignment."
  },
  "timestamp": "2026-09-05T02:00:00Z"
}
```

| HTTP | Codes / condition |
|---|---|
| `401` | `AUTHENTICATION_REQUIRED` or invalid/expired Bearer session |
| `403` | `ACTIVE_SCHOOL_ACCOUNT_REQUIRED`, `REPORT_SCOPE_FORBIDDEN` |
| `404` | `REPORT_SCHOOL_NOT_FOUND`, `ASSESSMENT_NOT_FOUND` |
| `422` | `INVALID_REPORT_FILTERS`, `REPORT_FILTER_MISMATCH` |

## Frontend Integration

1. Load `/api/v3/reports/reference-data` after authentication.
2. Cascade filter choices by their IDs. Do not infer relationships from names.
3. Request assessment results only after both `testId` and the matching
   `classAssignmentId` are selected.
4. Render the backend-provided score, percentage, label, warnings, and
   `dataStatus`; do not recalculate official values in React.
5. Preserve `null` as Pending/Not available and do not render it as zero.

Later slices added item analysis, the student performance profile, the
consolidated report, teacher sync activity and the learning competency report
below, each with `/excel` and `/pdf` downloads (see `V3ReportController`).

No database schema, seed data, V1/V2 endpoint, Mobile SQLite, OMR detector, or
TiDB environment was modified by this report slice.

## Learning Competency (SOP: Identify the learning competency)

```http
GET /api/v3/reports/learning-competency?termPeriodId=11&gradeLevelId=7&subjectId=3
GET /api/v3/reports/learning-competency/excel?termPeriodId=11&gradeLevelId=7&subjectId=3
GET /api/v3/reports/learning-competency/pdf?termPeriodId=11&gradeLevelId=7&subjectId=3
```

Drill-down: **Term -> Grade Level -> Subject -> Root Competency -> Specific
Competency (skill) -> weak students.** One call returns the whole tree for the
selected Term + Grade Level + Subject, aggregated across **every finalized
assessment** given in that term (not a single test).

- Teacher: only their own classes. Principal: the whole school.
- Optional `rootTagId` and/or `skillId` narrow the returned tree. Use them on
  the `/excel` and `/pdf` downloads to export only what the user selected.
- No new reference-data endpoint: the Term, Grade Level and Subject dropdowns
  cascade from the existing `/reference-data` (`termPeriods[].academicYearId`,
  then `classAssignments[]` filtered by `academicYearId` for grade levels, then
  by `gradeLevelId` for subjects). The Root and Skill dropdowns come from this
  response's `rootCompetencies[]` and `rootCompetencies[].skills[]`.

```json
{
  "reportType": "learning_competency",
  "generatedAt": "2026-09-25T02:00:00Z",
  "scope": {
    "schoolId": "SCHOOL-001", "schoolName": "SMART School",
    "academicYearId": 1, "academicYearName": "2026-2027",
    "termPeriodId": 11, "termName": "First Quarter",
    "gradeLevelId": 7, "gradeLevelName": "Grade 7",
    "subjectId": 3, "subjectName": "English"
  },
  "dataStatus": "available",
  "warnings": [],
  "assessments": [{ "testId": 1001, "testName": "Quiz 1" }],
  "rootCompetencies": [
    {
      "rootTagId": 1, "rootTagName": "Grammar",
      "masteryPercentage": 60.00, "masteryStatusCode": "developing",
      "skills": [
        {
          "skillId": 11, "competencyId": 111, "competencyName": "Parts of Speech",
          "studentCount": 2, "masteryPercentage": 35.00, "masteryStatusCode": "needs_support",
          "recommendationCode": "priority_intervention", "recommendationLabel": "Priority Intervention",
          "suggestion": "Prioritize intervention for Parts of Speech and monitor affected learners.",
          "weakStudentCount": 2,
          "weakStudents": [
            {
              "studentId": 502, "fullName": "Ben Diaz", "sectionName": "Rizal",
              "assessmentCount": 2, "masteryPercentage": 20.00, "masteryStatusCode": "needs_support",
              "recommendationCode": "priority_intervention", "recommendationLabel": "Priority Intervention",
              "suggestion": "Prioritize intervention for Parts of Speech and monitor affected learners."
            }
          ]
        }
      ]
    }
  ]
}
```

Rules:

- Roots and skills are sorted **least mastered first**; weak students are
  sorted **lowest mastery first**.
- `weakStudents` lists only students **below 80%** on that skill.
  `studentCount` counts every student assessed on it.
- A student's mastery is their earned points divided by the possible points of
  that skill's items, across the assessments **they actually took**, so a
  missed quiz is not counted as zero. `assessmentCount` says how many.
- Mastery percentages are weighted by points, never an average of averages.
- `recommendation*` / `suggestion` fields come from the school's active
  `intervention` rule set (same bands as the Student Performance Profile) and
  are `null` if none is configured (`INTERVENTION_RULES_UNAVAILABLE` warning).
  On a **skill** they are the class-level intervention, picked by the skill's
  overall `masteryPercentage` (e.g. 35% -> Priority Intervention). On a
  **weak student** they are that student's own intervention for the skill.
- The PDF/Excel "Least Mastered Competencies" table ranks every skill across
  all roots in one list, least mastered first.

| Warning | Meaning |
|---|---|
| `NO_SUBMITTED_RESULTS` | No finalized result yet for this term, grade level and subject. |
| `NO_COMPETENCY_MAPPING` | There are results, but the assessments have no skills mapped to their items. |
| `NO_MATCHING_COMPETENCY` | The requested `rootTagId`/`skillId` was not assessed in this term. |

Errors: `404 REPORT_FILTER_NOT_FOUND` when `termPeriodId` is not in the
user's school, or `gradeLevelId`/`subjectId` does not exist. The `errors` map
names the bad field.
