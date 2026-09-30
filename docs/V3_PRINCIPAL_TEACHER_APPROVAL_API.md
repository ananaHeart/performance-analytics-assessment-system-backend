# V3 Principal Teacher Approval API

**Status:** Implemented and locally tested  
**Profile:** `v3`  
**Local base URL:** `http://localhost:8082`  
**Database:** `performance_assessment_v3_db`

This API completes the approved teacher-account lifecycle without changing V1,
V2, the React runtime, Mobile SQLite, or TiDB.

## Authorization And Ownership

All endpoints require:

```http
Authorization: Bearer <principal-access-token>
```

The backend derives the principal identity, role, status, and `school_id` from
the authenticated token/session. It does not accept a principal ID or school ID
from the request. Repository reads, row locks, and writes repeat the same-school
scope. Only an active principal with a school may access this API.

Only email-verified teacher accounts are visible. The default queue contains
only `pending_approval`; accounts still in `pending_email_verification` are never
shown to the principal.

## Endpoint Matrix

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v3/users/teachers` | List verified teachers; default status is `pending_approval` |
| GET | `/api/v3/users/teachers/{teacherUserId}` | Get one verified teacher profile from the principal's school |
| POST | `/api/v3/users/teachers/{teacherUserId}/approve` | Change `pending_approval` to `active` |
| POST | `/api/v3/users/teachers/{teacherUserId}/reject` | Change `pending_approval` to `rejected` with a reason |

List status values accepted by the API are `pending_approval`, `active`,
`rejected`, `inactive`, and `locked`. Omitting `status` uses
`pending_approval`.

## List Pending Teachers

```http
GET /api/v3/users/teachers
Authorization: Bearer <principal-access-token>
```

Optional explicit filter:

```http
GET /api/v3/users/teachers?status=active
```

Response:

```json
{
  "success": true,
  "message": "V3 teacher accounts retrieved successfully.",
  "data": [
    {
      "userId": 920001,
      "schoolId": "SCHOOL-001",
      "fullName": "Maria Santos Reyes",
      "email": "maria.reyes@example.edu.ph",
      "contactNumber": "+639171234567",
      "status": "pending_approval",
      "emailVerifiedAt": "2026-09-02T01:12:30Z",
      "createdAt": "2026-09-02T01:00:00Z",
      "updatedAt": "2026-09-02T01:12:30Z"
    }
  ],
  "errors": null,
  "timestamp": "2026-09-02T01:20:00Z"
}
```

## Teacher Detail

```http
GET /api/v3/users/teachers/920001
Authorization: Bearer <principal-access-token>
```

The response includes reference IDs and labels, professional information,
verification timestamps, and the normalized address snapshot. Nullable fields
remain JSON `null`; IDs are never interchangeable.

```json
{
  "success": true,
  "message": "V3 teacher account retrieved successfully.",
  "data": {
    "userId": 920001,
    "schoolId": "SCHOOL-001",
    "addressId": 930001,
    "genderId": 2,
    "genderName": "Female",
    "majorId": 3,
    "majorName": "Science",
    "educationalAttainmentId": 1,
    "educationalAttainmentName": "Bachelor's Degree",
    "suffixId": null,
    "suffixName": null,
    "firstName": "Maria",
    "middleName": "Santos",
    "lastName": "Reyes",
    "fullName": "Maria Santos Reyes",
    "birthDate": "1995-06-14",
    "teachingStartMonth": 6,
    "teachingStartYear": 2018,
    "email": "maria.reyes@example.edu.ph",
    "contactNumber": "+639171234567",
    "role": "teacher",
    "status": "pending_approval",
    "emailVerified": true,
    "contactVerified": false,
    "emailVerifiedAt": "2026-09-02T01:12:30Z",
    "contactVerifiedAt": null,
    "createdAt": "2026-09-02T01:00:00Z",
    "updatedAt": "2026-09-02T01:12:30Z",
    "address": {
      "countryCode": "PH",
      "regionCode": "1200000000",
      "regionName": "SOCCSKSARGEN",
      "provinceCode": "1280000000",
      "provinceName": "Sarangani",
      "cityMunicipalityCode": "1280500000",
      "cityMunicipalityName": "Malungon",
      "barangayCode": "1280501000",
      "barangayName": "San Roque",
      "addressLine": "Unit 2",
      "postalCode": "9503",
      "addressSource": "psgc_api"
    }
  },
  "errors": null,
  "timestamp": "2026-09-02T01:20:00Z"
}
```

## Approve

```http
POST /api/v3/users/teachers/920001/approve
Authorization: Bearer <principal-access-token>
```

There is no request body. A successful response returns the full teacher detail
with `status: "active"`. The teacher may then use normal V3 password login.

## Reject

```http
POST /api/v3/users/teachers/920001/reject
Authorization: Bearer <principal-access-token>
Content-Type: application/json

{
  "reason": "Applicant is not part of the current school faculty."
}
```

The reason is required and must contain 5-500 characters. A successful response
returns the full teacher detail with `status: "rejected"`. The reason is retained
in the decision audit record and is not placed in the public registration
response.

## Transaction And Audit Rules

Approval and rejection run in one transaction:

1. Validate the authenticated active principal and school.
2. Lock the same-school teacher row.
3. Require role `teacher`, verified email, and status `pending_approval`.
4. Perform a conditional `pending_approval -> active|rejected` update.
5. Write `teacher.approve` or `teacher.reject` to `audit_logs` with actor,
   target user, previous/new status, request metadata, and rejection reason when
   applicable.
6. Return the updated teacher detail.

Any failure rolls back both the status transition and audit write. Repeating an
already-completed decision returns a conflict instead of changing the account
again.

## Error Contract

All errors use the standard `ApiResponse` envelope.

```json
{
  "success": false,
  "message": "Only a teacher with pending_approval status may be approved or rejected.",
  "data": null,
  "errors": {
    "code": "TEACHER_NOT_PENDING_APPROVAL"
  },
  "timestamp": "2026-09-02T01:20:00Z"
}
```

| HTTP | Code | Meaning |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Invalid status filter or rejection reason |
| 401 | `AUTHENTICATION_REQUIRED` | Missing, invalid, expired, or revoked token |
| 403 | `PRINCIPAL_ROLE_REQUIRED` | Authenticated account is not a principal |
| 403 | `ACTIVE_SCHOOL_ACCOUNT_REQUIRED` | Principal is inactive or has no school |
| 404 | `TEACHER_NOT_FOUND` | Teacher is absent, unverified, or outside the principal's school |
| 409 | `EMAIL_VERIFICATION_REQUIRED` | Decision target is not email verified |
| 409 | `TEACHER_NOT_PENDING_APPROVAL` | Teacher is not awaiting approval |
| 409 | `TEACHER_ACCOUNT_STATE_CHANGED` | Concurrent state change prevented the decision |

The not-found response intentionally does not disclose whether a teacher exists
in another school.

## Integration Boundary

This backend API is ready for controlled Postman/runtime acceptance. The current
React principal screen has not been switched to it. Do not mix these V3 paths
with V2 teacher-approval endpoints, and do not change Mobile SQLite or TiDB as
part of this slice.
