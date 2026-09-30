# V3 Academic Calendar And Class Timetable API

**Status:** Implemented for the isolated local `v3` profile  
**Runtime:** `http://localhost:8082`  
**Database:** `performance_assessment_v3_db`  
**Date:** September 3, 2026

## Boundary

This slice adds school-owned academic calendars, exactly four grading terms,
teacher-owned class timetable rows, and schedule-aware assessment validation.
It does not switch React, Mobile SQLite, scanning, analytics, exports, V1, V2,
or TiDB.

All timestamps are ISO-8601 instants in UTC. Timetable wall-clock values use
`Asia/Manila`; `dayOfWeek` follows ISO values `1=Monday` through `7=Sunday`.

## Principal Academic Calendar

All endpoints require `Authorization: Bearer <principal-token>`. The backend
derives `schoolId` from the authenticated principal; it is never accepted from
the request body.

| Method | Path | Purpose | Success |
|---|---|---|---|
| GET | `/api/v3/school-setup/academic-years` | List the principal's school calendars | `200` |
| POST | `/api/v3/school-setup/academic-years` | Create one year and four terms atomically | `201` |
| GET | `/api/v3/school-setup/academic-years/{academicYearId}` | Read one school-owned calendar | `200` |
| PUT | `/api/v3/school-setup/academic-years/{academicYearId}` | Replace a planned calendar | `200` |
| PATCH | `/api/v3/school-setup/academic-years/{academicYearId}/activate` | Manually activate a planned year | `200` |
| PATCH | `/api/v3/school-setup/academic-years/{academicYearId}/complete` | Complete an active year | `200` |
| PATCH | `/api/v3/school-setup/academic-years/{academicYearId}/term-periods/{termPeriodId}/activate` | Manually activate the next term | `200` |
| PATCH | `/api/v3/school-setup/academic-years/{academicYearId}/term-periods/{termPeriodId}/complete` | Complete the active term | `200` |

Create/update request:

```json
{
  "curriculumId": 1,
  "yearName": "2026-2027",
  "startDate": "2026-06-01",
  "endDate": "2027-03-31",
  "termPeriods": [
    {
      "termName": "First Quarter",
      "termOrder": 1,
      "startAt": "2026-05-31T16:00:00Z",
      "endAt": "2026-08-14T16:00:00Z",
      "activationMode": "automatic"
    },
    {
      "termName": "Second Quarter",
      "termOrder": 2,
      "startAt": "2026-08-14T16:00:00Z",
      "endAt": "2026-10-30T16:00:00Z",
      "activationMode": "automatic"
    },
    {
      "termName": "Third Quarter",
      "termOrder": 3,
      "startAt": "2026-10-30T16:00:00Z",
      "endAt": "2027-01-14T16:00:00Z",
      "activationMode": "automatic"
    },
    {
      "termName": "Fourth Quarter",
      "termOrder": 4,
      "startAt": "2027-01-14T16:00:00Z",
      "endAt": "2027-03-31T16:00:00Z",
      "activationMode": "automatic"
    }
  ]
}
```

Manual lifecycle request:

```json
{ "reason": "Approved school calendar transition" }
```

Rules:

- `yearName` must match the start/end years, and the end year is start year + 1.
- Exactly four terms are required with canonical names and orders.
- Term ranges must be chronological, non-overlapping, and inside the academic year.
- A school can have only one active academic year and one active term in that year.
- Terms activate and complete in order. All terms must be complete before the year.
- Only a fully planned calendar can be edited.
- Manual lifecycle changes require a reason and are audited.
- Automatic transitions are implemented but fail-safe disabled by default. Enable
  them only after calendar dates are reviewed with
  `V3_CALENDAR_AUTOMATION_ENABLED=true`.

## Teacher Class Timetable

All endpoints require `Authorization: Bearer <teacher-token>`. A teacher can
manage timetable rows only for an assignment they own in their school. This
does not let a teacher change the Principal-owned class, subject, academic year,
role, or assignment status.

| Method | Path | Purpose | Success |
|---|---|---|---|
| GET | `/api/v3/teacher/class-assignments/{classAssignmentId}/schedules` | List owned timetable rows | `200` |
| POST | `/api/v3/teacher/class-assignments/{classAssignmentId}/schedules` | Create a timetable row | `201` |
| PUT | `/api/v3/teacher/class-assignments/{classAssignmentId}/schedules/{scheduleId}` | Edit an active row | `200` |
| PATCH | `/api/v3/teacher/class-assignments/{classAssignmentId}/schedules/{scheduleId}/archive` | Soft-archive with reason | `200` |

Create/update request:

```json
{
  "dayOfWeek": 1,
  "startTime": "08:00:00",
  "endTime": "09:00:00",
  "timezoneName": "Asia/Manila",
  "effectiveFrom": "2026-06-01",
  "effectiveTo": "2027-03-31"
}
```

Archive request:

```json
{ "reason": "Class timetable was revised" }
```

Rules:

- Effective dates must stay inside the assignment academic year.
- End time must be later than start time.
- Active schedules for the same teacher cannot overlap across assignments.
- Schedule writes serialize on the teacher record to prevent concurrent overlap races.
- Archived rows remain as history and cannot be edited.

## Assessment Schedule Integration

`POST /api/v3/assessments` and `PUT /api/v3/assessments/{testId}` now accept:

```json
{
  "openAt": "2026-07-06T00:00:00Z",
  "closeAt": "2026-07-06T01:00:00Z",
  "allowLateCapture": false,
  "confirmOutsideClassSchedule": false,
  "outsideClassScheduleReason": null
}
```

- `openAt` and `closeAt`, when supplied, must be inside the selected term.
- `closeAt` must be later than `openAt`.
- If timetable rows exist, `closeAt` should fall in an effective meeting window.
- An outside-timetable deadline is allowed only with explicit confirmation and a
  reason of at least five characters. The backend stores the teacher, reason,
  and confirmation time for auditability.
- Existing assignments with no timetable remain backward compatible.
- Assessment activation still rejects a past `closeAt`.

## Database Migration

The isolated migration is:

`docs/migrations/v3/V3_014_academic_calendar_and_class_schedules.sql`

It adds school ownership and active-row uniqueness to academic calendars,
creates `class_assignment_schedules`, and adds outside-timetable audit fields
to `test_assignments`. Apply it only after backup and disposable validation.
