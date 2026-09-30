# V3 Manual Student Enrollment and Roster Lifecycle API

Status: implemented in the isolated `v3` Spring profile

Base URL: `http://localhost:8082/api/v3`

All endpoints in this document require `Authorization: Bearer <principal-token>`.
The backend derives the principal and school from the token. It does not accept
`schoolId` or `principalUserId` from the request body.

## Reference Data

`GET /school-setup/reference-data`

The existing school-setup response now also contains active learner form
references:

```json
{
  "success": true,
  "data": {
    "genders": [
      { "genderId": 1, "genderName": "Male" },
      { "genderId": 2, "genderName": "Female" }
    ],
    "suffixes": [
      { "suffixId": 1, "suffixName": "Jr." }
    ]
  }
}
```

The frontend must submit these identifiers and must not hardcode them. A suffix
is optional; gender is required.

## List A Class Roster

`GET /school-setup/classes/{classId}/students`

Optional query parameter: `enrollmentStatus=enrolled|transferred|dropped|completed`

When omitted, the endpoint defaults to `enrolled` so the normal class roster
does not mix active learners with retained history. Supply an explicit status
to retrieve transferred, dropped, or completed memberships.

## Manually Enroll A Student

`POST /school-setup/classes/{classId}/students`

```json
{
  "studentLrn": "123456789012",
  "firstName": "Maria",
  "middleName": "Santos",
  "lastName": "Dela Cruz",
  "suffixId": null,
  "genderId": 2,
  "birthDate": "2012-05-14"
}
```

Returns HTTP `201` when a membership is created and HTTP `200` when the same
student is already enrolled in the same class or an ended membership is
reactivated.

```json
{
  "success": true,
  "message": "V3 student enrolled successfully.",
  "data": {
    "student": {
      "classListId": 1029,
      "membershipUuid": "771945f8-868c-47e9-8686-1611432e232a",
      "studentId": 1015,
      "studentLrn": "123456789012",
      "firstName": "Maria",
      "middleName": "Santos",
      "lastName": "Dela Cruz",
      "suffixName": null,
      "fullName": "Maria Santos Dela Cruz",
      "gender": "Female",
      "birthDate": "2012-05-14",
      "studentStatus": "active",
      "enrollmentStatus": "enrolled",
      "enrollmentSource": "manual",
      "enrolledAt": "2026-09-04T01:30:00Z",
      "endedAt": null,
      "statusReason": "Manually enrolled by an authorized principal.",
      "statusChangedByUserId": 900001,
      "updatedAt": "2026-09-04T01:30:00Z"
    },
    "studentCreated": true,
    "enrollmentCreated": true,
    "enrollmentReactivated": false
  }
}
```

Rules:

- `studentLrn` is exactly 12 digits and globally unique.
- The target class must be active and owned by the principal's school.
- A student may have only one `enrolled` class membership per academic year.
- Repeating the same request for the same class is idempotent.
- An LRN owned by another school is rejected.
- An existing LRN with different identity fields is never silently overwritten.
  The client must use the explicit profile-correction endpoint.
- A new manual student receives an address placeholder owned by the student;
  detailed address collection can be added through a later approved profile API.
- All writes are transactional and recorded in `audit_logs`.

## Correct A Student Profile

`PUT /school-setup/classes/{classId}/students/{studentId}`

```json
{
  "firstName": "Maria",
  "middleName": "Santos",
  "lastName": "Dela Cruz",
  "suffixId": null,
  "genderId": 2,
  "birthDate": "2012-05-14",
  "reason": "Corrected spelling after checking the learner record."
}
```

The LRN is intentionally absent and immutable in this operation. The principal
must provide a 5-255 character reason. The change is school-scoped and audited.

## Change Enrollment Status

`PATCH /school-setup/class-lists/{classListId}/status`

```json
{
  "enrollmentStatus": "transferred",
  "reason": "Transferred to another school on 2026-09-04."
}
```

Approved status values are `enrolled`, `transferred`, `dropped`, and
`completed`. Non-enrolled states retain `endedAt`; re-enrollment clears it and
records a new `enrolledAt`. Re-enrollment is rejected if another active class
membership exists for the student in the same academic year.

Changing a class membership does not silently change the master
`students.status` value.

## Error Behavior

Errors use the standard API envelope. Common codes include:

- `401 AUTHENTICATION_REQUIRED`: missing, invalid, or expired bearer token.
- `403 ROLE_FORBIDDEN`: authenticated user is not a principal.
- `404 CLASS_NOT_FOUND`, `STUDENT_NOT_FOUND`, or `CLASS_LIST_NOT_FOUND`.
- `400 VALIDATION_FAILED`: field-level request validation failed.
- `409 STUDENT_ALREADY_ENROLLED`: another active class exists in the same year.
- `409 STUDENT_PROFILE_REVIEW_REQUIRED`: same LRN, different identity data.
- `409 STUDENT_LRN_OWNED_BY_ANOTHER_SCHOOL`: cross-school LRN collision.
- `409 STUDENT_ENROLLMENT_CONFLICT`: a concurrent/database uniqueness conflict.

No endpoint in this slice physically deletes a student or class-list row.

## Frontend Integration Boundary

This backend slice is ready for a focused frontend integration after API
acceptance. The frontend should:

1. Load `genders` and `suffixes` from V3 school-setup reference data.
2. Use `classId` for roster/manual-enrollment/profile routes.
3. Use `classListId` only for enrollment-status changes.
4. Use `studentId` only for the learner profile route.
5. Show explicit status actions and require a reason; do not expose hard delete.
6. Handle `409` identity and enrollment conflicts without retry loops.

This slice does not change Mobile SQLite, scanning, synchronization, scoring,
analytics, printing, V1/V2 runtime, or TiDB.
