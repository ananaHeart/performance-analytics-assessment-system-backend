# V3 School Setup, Class Roster, and SF1 API

**Status:** Implemented and locally validated  
**Spring profile:** `v3`  
**Local base URL:** `http://localhost:8082`  
**Database:** `performance_assessment_v3_db`  
**Required migration:** `V3_013_school_scoped_sections.sql`

This contract is isolated under `/api/v3`. It does not replace or modify V1,
V2, React, Mobile SQLite, or TiDB integrations.

## Shared Rules

- Send `Authorization: Bearer <token>` on every endpoint in this document.
- Principal endpoints derive `schoolId` and `principalUserId` from the token.
  The client must not send either value to choose an ownership scope.
- Teacher roster access is allowed only when the authenticated teacher has an
  active `class_assignments` row for the requested class in the same school.
- IDs are not interchangeable:
  - `classId` identifies the grade/section cohort for an academic year.
  - `classAssignmentId` identifies one teacher + class + subject assignment.
  - `classListId` identifies one student membership in a class.
  - `studentId` identifies the learner.
- Timestamps are ISO-8601 UTC instants, for example
  `2026-09-02T07:30:00Z`.
- All responses use the shared envelope:

```json
{
  "success": true,
  "message": "Request completed successfully.",
  "data": {},
  "errors": null,
  "timestamp": "2026-09-02T07:30:00Z"
}
```

## Endpoint Status Matrix

| Method | Path | Role | Status |
|---|---|---|---|
| GET | `/api/v3/school-setup/profile` | principal | Implemented |
| PUT | `/api/v3/school-setup/profile` | principal | Implemented |
| GET | `/api/v3/school-setup/reference-data` | principal | Implemented |
| GET | `/api/v3/school-setup/classes` | principal | Implemented |
| POST | `/api/v3/school-setup/classes` | principal | Implemented |
| GET | `/api/v3/school-setup/classes/{classId}/students` | principal | Implemented |
| GET | `/api/v3/school-setup/class-assignments` | principal | Implemented |
| POST | `/api/v3/school-setup/class-assignments` | principal | Implemented |
| PATCH | `/api/v3/school-setup/class-assignments/{classAssignmentId}/archive` | principal | Implemented |
| PATCH | `/api/v3/school-setup/class-assignments/{classAssignmentId}/reactivate` | principal | Implemented |
| GET | `/api/v3/teacher/classes/{classId}/students` | teacher | Implemented |
| POST | `/api/v3/import/sf1/preview` | principal | Implemented |
| POST | `/api/v3/import/sf1/confirm` | principal | Implemented |

## School Profile

### GET `/api/v3/school-setup/profile`

Returns only the authenticated principal's school.

### PUT `/api/v3/school-setup/profile`

```json
{
  "schoolName": "SMART Demonstration School",
  "contactNumber": "09171234567",
  "email": "school@example.edu.ph",
  "address": {
    "regionCode": "1200000000",
    "regionName": "SOCCSKSARGEN",
    "provinceCode": "1280000000",
    "provinceName": "Sarangani",
    "cityMunicipalityCode": "1280040000",
    "cityMunicipalityName": "Malungon",
    "barangayCode": "1280040070",
    "barangayName": "San Roque",
    "addressLine": "Building 1, Campus Road",
    "postalCode": "9503",
    "addressSource": "api"
  }
}
```

Rules:

- `schoolName` and `address` are required.
- `addressSource`: `api` or `manual`.
- Every address code/name pair must be supplied together or omitted together.
- Contact accepts `09XXXXXXXXX` or `+639XXXXXXXXX`; storage normalizes it to
  `+639XXXXXXXXX`.
- Optional values may be `null` or omitted where Bean Validation allows it.

Example `data`:

```json
{
  "schoolId": "V2-LOCAL-TEST",
  "schoolName": "SMART Demonstration School",
  "contactNumber": "+639171234567",
  "email": "school@example.edu.ph",
  "address": {
    "countryCode": "PH",
    "regionCode": "1200000000",
    "regionName": "SOCCSKSARGEN",
    "provinceCode": "1280000000",
    "provinceName": "Sarangani",
    "cityMunicipalityCode": "1280040000",
    "cityMunicipalityName": "Malungon",
    "barangayCode": "1280040070",
    "barangayName": "San Roque",
    "addressLine": "Building 1, Campus Road",
    "postalCode": "9503",
    "addressSource": "api"
  }
}
```

## Reference Data

### GET `/api/v3/school-setup/reference-data`

Returns the principal's school plus active/reference records required by the
school setup UI: academic years, term periods, grade levels, subjects, and
active email-verified teachers belonging to the same school.

Example `data`:

```json
{
  "school": {
    "schoolId": "V2-LOCAL-TEST",
    "schoolName": "V2 Local Test School",
    "contactNumber": null,
    "email": null,
    "address": null
  },
  "academicYears": [
    {
      "academicYearId": 910003,
      "curriculumId": 1,
      "yearName": "2025-2026",
      "startDate": "2025-06-01",
      "endDate": "2026-03-31",
      "status": "active"
    }
  ],
  "termPeriods": [
    {
      "termPeriodId": 910001,
      "academicYearId": 910003,
      "termName": "First Quarter",
      "termOrder": 1,
      "startAt": "2025-06-01T00:00:00Z",
      "endAt": "2025-08-31T23:59:59Z",
      "status": "active",
      "activationMode": "manual"
    }
  ],
  "gradeLevels": [
    { "gradeLevelId": 1, "gradeLevelName": "Grade 7" }
  ],
  "subjects": [
    { "subjectId": 1, "subjectCode": "ENG", "subjectName": "English" }
  ],
  "teachers": [
    {
      "teacherUserId": 910002,
      "fullName": "Manual Teacher",
      "email": "teacher.manual.local@example.com"
    }
  ]
}
```

## Classes

### GET `/api/v3/school-setup/classes`

Optional query parameters: `academicYearId`, `gradeLevelId`.

### POST `/api/v3/school-setup/classes`

```json
{
  "academicYearId": 910003,
  "gradeLevelId": 2,
  "sectionName": "Narra"
}
```

The operation is reuse-safe. If the exact active class already exists in the
principal's school, the same class is returned with `created: false`. An
archived/completed class is not silently reactivated and returns
`CLASS_NOT_ACTIVE`.

Example `data`:

```json
{
  "classId": 910020,
  "academicYearId": 910003,
  "academicYearName": "2025-2026",
  "sectionId": 910020,
  "gradeLevelId": 2,
  "gradeLevelName": "Grade 8",
  "sectionName": "Narra",
  "status": "active",
  "enrolledStudentCount": 0,
  "created": true
}
```

`sections.school_id` plus
`UNIQUE(school_id, grade_level_id, section_name)` prevents schools from
sharing or colliding on section identities.

## Class Assignments

### GET `/api/v3/school-setup/class-assignments`

Optional query parameter: `academicYearId`.

### POST `/api/v3/school-setup/class-assignments`

```json
{
  "classId": 910003,
  "teacherUserId": 910002,
  "subjectId": 1,
  "assignmentRole": "primary"
}
```

Allowed assignment roles: `primary`, `co_teacher`.

The backend verifies that the class is active and belongs to the principal's
school, and that the teacher is active, email-verified, and belongs to that
same school. Repeating the same active assignment is reuse-safe. Reusing an
archived exact assignment reactivates that row instead of inserting a duplicate.

### PATCH archive/reactivate

```json
{
  "reason": "Teacher reassigned for the new grading schedule."
}
```

The reason is required and must contain 5 to 255 characters. Archive and
reactivate operations preserve history and write an audit log; they do not
physically delete the assignment. Reactivation fails when its class or teacher
is no longer active.

## Class Rosters

Principal:

`GET /api/v3/school-setup/classes/{classId}/students`

Teacher:

`GET /api/v3/teacher/classes/{classId}/students`

Optional query parameter `enrollmentStatus` accepts `enrolled`, `transferred`,
`dropped`, or `completed`; omitted/blank defaults to `enrolled`.

Example `data` item:

```json
{
  "classListId": 910101,
  "membershipUuid": "907ab011-d580-46e8-b040-e2362f05d9da",
  "studentId": 910050,
  "studentLrn": "100000000101",
  "firstName": "Juan",
  "middleName": "Santos",
  "lastName": "Dela Cruz",
  "suffixName": null,
  "fullName": "DELA CRUZ, JUAN SANTOS",
  "gender": "male",
  "birthDate": null,
  "studentStatus": "active",
  "enrollmentStatus": "enrolled",
  "enrollmentSource": "sf1",
  "enrolledAt": "2026-09-02T07:30:00Z",
  "endedAt": null
}
```

## Duplicate-Safe SF1 Import

### Preview

`POST /api/v3/import/sf1/preview?academicYearId=910003&gradeLevelId=2&sectionName=Narra`

Content type: `multipart/form-data`; file part name: `file`.

The preview is read-only. It calculates SHA-256, checks prior completed imports,
compares selected and detected context, and returns the planned result for each
learner row.

Example `data`:

```json
{
  "importUuid": "98002799-7f16-4e0f-b21c-d905da3f6688",
  "sourceFileName": "SF1_2025_Grade8_Narra.xlsx",
  "sourceFileHash": "ad20f22e07f12d91cba5e0948b813da31f28564fe443067b93cda16e225ecc57",
  "duplicateFile": true,
  "previousCompletedImportCount": 1,
  "selectedContext": {
    "academicYearId": 910003,
    "academicYearName": "2025-2026",
    "gradeLevelId": 2,
    "gradeLevelName": "Grade 8",
    "sectionName": "Narra",
    "existingClassId": 910020
  },
  "detectedContext": {
    "academicYearName": "2025-2026",
    "gradeLevelName": "Grade 8",
    "sectionName": "Narra"
  },
  "requiresContextOverride": false,
  "warnings": [
    "This file content was imported previously. Existing learners will not be duplicated; new learner rows can still be enrolled."
  ],
  "totalRows": 2,
  "validRows": 2,
  "invalidRows": 0,
  "rows": [
    {
      "rowNumber": 7,
      "studentLrn": "100000000101",
      "firstName": "Juan",
      "lastName": "Dela Cruz",
      "gender": "male",
      "parserStatus": "VALID",
      "plannedOutcome": "already_enrolled",
      "warningCode": "ALREADY_ENROLLED",
      "message": "The learner is already enrolled in this class."
    }
  ]
}
```

### Confirm

`POST /api/v3/import/sf1/confirm`

Content type: `multipart/form-data`; use these query parameters:

- `importUuid`: exact UUID returned by preview.
- `academicYearId`, `gradeLevelId`, `sectionName`: same selected context.
- `expectedFileHash`: exact SHA-256 returned by preview.
- `acceptContextMismatch`: `true` only after the principal explicitly accepts
  a detected/selected context mismatch; otherwise use `false`.

Send the same Excel file again as multipart part `file`.

Example `data` summary:

```json
{
  "sf1ImportId": 42,
  "importUuid": "98002799-7f16-4e0f-b21c-d905da3f6688",
  "importStatus": "partial_success",
  "sourceFileName": "SF1_2025_Grade8_Narra.xlsx",
  "sourceFileHash": "ad20f22e07f12d91cba5e0948b813da31f28564fe443067b93cda16e225ecc57",
  "duplicateFile": true,
  "previousCompletedImportCount": 1,
  "targetClass": {
    "classId": 910020,
    "academicYearId": 910003,
    "academicYearName": "2025-2026",
    "gradeLevelId": 2,
    "gradeLevelName": "Grade 8",
    "sectionId": 910020,
    "sectionName": "Narra"
  },
  "totalRows": 2,
  "createdStudents": 1,
  "updatedStudents": 0,
  "unchangedStudents": 1,
  "conflictRows": 0,
  "invalidRows": 0,
  "startedAt": "2026-09-02T07:30:00Z",
  "completedAt": "2026-09-02T07:30:01Z",
  "replayed": false,
  "rows": []
}
```

Import statuses: `previewed`, `processing`, `completed`, `partial_success`,
`failed`, `cancelled`.

Row outcome statuses: `created`, `updated`, `existing_unchanged`,
`already_enrolled`, `enrollment_conflict`, `invalid`.

Important integrity behavior:

- Re-uploading the same file is allowed because it may contain new learners.
- Existing LRNs are not duplicated.
- A learner may have only one active class enrollment in an academic year.
- A same-file retry with the same `importUuid`, file hash, and context returns
  the stored response with `replayed: true`.
- Reusing an `importUuid` with changed file/context is rejected.
- Confirm is transactional: header, learner/enrollment changes, row outcomes,
  summary, and audit log commit together or roll back together.
- Accepted files: `.xls`, `.xlsx`; maximum size: 10 MiB.

## Error Examples

Validation error (`400`):

```json
{
  "success": false,
  "message": "School-setup validation failed.",
  "data": null,
  "errors": {
    "code": "VALIDATION_FAILED",
    "assignmentRole": "Allowed values: [co_teacher, primary]"
  },
  "timestamp": "2026-09-02T07:30:00Z"
}
```

Ownership error (`404`, deliberately does not expose another school):

```json
{
  "success": false,
  "message": "The class was not found in the authenticated principal's school.",
  "data": null,
  "errors": { "code": "CLASS_NOT_FOUND" },
  "timestamp": "2026-09-02T07:30:00Z"
}
```

Conflict (`409`):

```json
{
  "success": false,
  "message": "The uploaded SF1 content no longer matches the preview.",
  "data": null,
  "errors": { "code": "SF1_FILE_CHANGED" },
  "timestamp": "2026-09-02T07:30:00Z"
}
```

Unauthenticated requests return `401`; valid users with the wrong role or an
inactive/non-school account return `403`.

## Frontend Integration Gate

Do not switch the active React routes yet. First run the supplied Postman
collection against `http://localhost:8082`, approve ownership/duplicate-file
results, and then perform one controlled React cutover for this slice. Mobile
SQLite, scan upload, scoring, analytics, dynamic template activation, and TiDB
remain outside this contract.
