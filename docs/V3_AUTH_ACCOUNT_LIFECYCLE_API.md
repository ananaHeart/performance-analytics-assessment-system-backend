# V3 Authentication and Teacher Registration API

**Status:** Implemented and locally validated  
**Profile:** `v3`  
**Local base URL:** `http://localhost:8082`  
**Database:** `performance_assessment_v3_db`

This contract is isolated under `/api/v3/auth`. It does not switch the React
frontend, mobile application, V2 endpoints, or production/TiDB data to V3.

## Implemented Lifecycle

```text
Teacher registration
  -> pending_email_verification
  -> email OTP verified
  -> pending_approval
  -> principal approval
  -> active
  -> password login
```

- OTP lifetime: 10 minutes.
- Resend cooldown: 60 seconds.
- Maximum OTP attempts per challenge: 5.
- Maximum verification messages: 5 within 24 hours.
- Unverified registration retention: 30 days.
- Resending an OTP does not extend the 30-day registration deadline.
- After 30 days, an unverified provisional account is deleted and its email and
  contact number become reusable. Related login-attempt email values are
  anonymized and a non-sensitive audit event is retained.
- A correct email/password login during the retention period returns
  `EMAIL_VERIFICATION_REQUIRED`; it does not create an authenticated session.
- A wrong password always returns `INVALID_CREDENTIALS` and does not reveal the
  account state.
- An email-verified account remains `pending_approval` until a principal from
  the same school approves it through the V3 teacher-account API.
- Unverified teacher accounts are excluded from the principal approval queue.
- Authenticator MFA enrollment/challenge is not implemented yet. It is intended
  as an optional Settings feature after normal account activation. If an account
  is already marked `mfa_required`, password-only login fails closed.

## Standard Envelope

All responses use:

```json
{
  "success": true,
  "message": "Operation completed.",
  "data": {},
  "errors": null,
  "timestamp": "2026-08-31T01:26:17.314191800Z"
}
```

Errors use the same envelope with `success: false`, `data: null`, and an
`errors.code`. Validation errors also contain field names in `errors`.

## Endpoint Matrix

| Method | Path | Access | Status |
|---|---|---|---|
| GET | `/api/v3/auth/teacher-registration/reference-data` | Public | Implemented |
| POST | `/api/v3/auth/register-teacher` | Public | Implemented |
| POST | `/api/v3/auth/verify-teacher-email` | Public | Implemented |
| POST | `/api/v3/auth/resend-teacher-verification` | Public | Implemented |
| POST | `/api/v3/auth/login` | Public | Implemented |
| GET | `/api/v3/auth/me` | Bearer token | Implemented |
| POST | `/api/v3/auth/logout` | Bearer token | Implemented |
| GET | `/api/v3/users/teachers` | Principal | Implemented |
| GET | `/api/v3/users/teachers/{teacherUserId}` | Principal | Implemented |
| POST | `/api/v3/users/teachers/{teacherUserId}/approve` | Principal | Implemented |
| POST | `/api/v3/users/teachers/{teacherUserId}/reject` | Principal | Implemented |
| Any | Authenticator MFA enrollment/challenge | Authenticated user | Not implemented |

## Registration Reference Data

`GET /api/v3/auth/teacher-registration/reference-data`

The frontend must render the returned options and availability states. It must
not hardcode database IDs or show SMS as selectable while `available` is false.

```json
{
  "success": true,
  "message": "Teacher registration reference data retrieved successfully.",
  "data": {
    "genders": [{ "id": 2, "name": "Female" }],
    "suffixes": [{ "id": 1, "name": "Jr." }],
    "majors": [{ "id": 3, "name": "Science" }],
    "educationalAttainments": [{ "id": 1, "name": "Bachelor's Degree" }],
    "schools": [{
      "schoolId": "V2-LOCAL-TEST",
      "schoolName": "V2 Local Test School"
    }],
    "verificationMethods": [
      {
        "method": "email",
        "label": "Email",
        "available": true,
        "unavailableReason": null
      },
      {
        "method": "sms",
        "label": "Text message (SMS)",
        "available": false,
        "unavailableReason": "SMS verification is not available yet."
      }
    ],
    "verificationPolicy": {
      "otpTtlSeconds": 600,
      "resendCooldownSeconds": 60,
      "maximumAttempts": 5,
      "registrationRetentionDays": 30
    }
  },
  "errors": null,
  "timestamp": "2026-08-31T01:26:17.314191800Z"
}
```

## Register Teacher

`POST /api/v3/auth/register-teacher`

```json
{
  "schoolCode": "V2-LOCAL-TEST",
  "verificationMethod": "email",
  "firstName": "Maria",
  "middleName": "Santos",
  "lastName": "Reyes",
  "suffixId": null,
  "birthDate": "1995-04-12",
  "teachingStartMonth": 6,
  "teachingStartYear": 2018,
  "email": "maria.reyes@example.com",
  "contactNumber": "09171234567",
  "password": "StrongPass1!",
  "genderId": 2,
  "majorId": 3,
  "educationalAttainmentId": 1,
  "address": {
    "countryCode": "PH",
    "regionCode": "1200000000",
    "regionName": "SOCCSKSARGEN",
    "provinceCode": "1280000000",
    "provinceName": "Sarangani",
    "cityMunicipalityCode": "1280600000",
    "cityMunicipalityName": "Malungon",
    "barangayCode": "1280600100",
    "barangayName": "San Roque",
    "addressLine": "Unit 2, Mabini Street",
    "postalCode": "9503"
  }
}
```

Success is HTTP `201`:

```json
{
  "success": true,
  "message": "Teacher registration saved. Enter the code sent to the registered email.",
  "data": {
    "accountStatus": "pending_email_verification",
    "verificationMethod": "email",
    "emailMasked": "m***s@example.com",
    "challengeUuid": "2f7a7269-b505-49b3-b4c9-c04401eae97c",
    "deliveryStatus": "sent",
    "otpExpiresAt": "2026-08-31T01:40:00Z",
    "resendAvailableAt": "2026-08-31T01:31:00Z",
    "registrationExpiresAt": "2026-09-30T01:30:00Z"
  },
  "errors": null,
  "timestamp": "2026-08-31T01:30:00Z"
}
```

Authoritative validation includes:

- registered school code;
- email verification method only;
- valid and unique email;
- unique normalized Philippine contact number (`09XXXXXXXXX` becomes
  `+639XXXXXXXXX`);
- teacher age at least 18;
- teaching month/year not in the future and not before age 18;
- password of 10-128 characters with uppercase, lowercase, number, and special
  character;
- valid reference IDs for gender, optional suffix, major, and educational
  attainment;
- required city/municipality, barangay, and address line.

Example field error, HTTP `400`:

```json
{
  "success": false,
  "message": "Validation failed.",
  "data": null,
  "errors": {
    "code": "VALIDATION_FAILED",
    "birthDate": "Teacher must be at least 18 years old and birth date must be in the past.",
    "contactNumber": "Contact number must use 09XXXXXXXXX or +639XXXXXXXXX format."
  },
  "timestamp": "2026-08-31T01:30:00Z"
}
```

Duplicate email/contact is HTTP `409` with code `DUPLICATE_ACCOUNT_DATA`.
Expired unverified identities are purged before duplicate checking, allowing a
fresh registration after the 30-day retention period.

## Verify Email

`POST /api/v3/auth/verify-teacher-email`

```json
{
  "challengeUuid": "2f7a7269-b505-49b3-b4c9-c04401eae97c",
  "otp": "123456"
}
```

Success changes the account to `pending_approval`:

```json
{
  "success": true,
  "message": "Email verified successfully. The account is pending principal approval.",
  "data": {
    "accountStatus": "pending_approval",
    "emailVerified": true,
    "emailMasked": "m***s@example.com",
    "challengeUuid": "2f7a7269-b505-49b3-b4c9-c04401eae97c",
    "deliveryStatus": "sent",
    "otpExpiresAt": "2026-08-31T01:40:00Z",
    "resendAvailableAt": "2026-08-31T01:31:00Z",
    "registrationExpiresAt": "2026-09-30T01:30:00Z"
  },
  "errors": null,
  "timestamp": "2026-08-31T01:32:00Z"
}
```

Relevant errors:

- `INVALID_VERIFICATION_CODE` (`400`)
- `VERIFICATION_CHALLENGE_LOCKED` (`423`)
- `VERIFICATION_CODE_EXPIRED` (`410`)
- `REGISTRATION_EXPIRED` (`410`)
- `EMAIL_ALREADY_VERIFIED` (`409`)
- `INVALID_VERIFICATION_CHALLENGE` (`400`)

## Resend Verification

`POST /api/v3/auth/resend-teacher-verification`

```json
{
  "challengeUuid": "2f7a7269-b505-49b3-b4c9-c04401eae97c"
}
```

The old challenge is cancelled and a new `challengeUuid` is returned. The
original 30-day `registrationExpiresAt` is retained.

Relevant errors:

- `VERIFICATION_RESEND_COOLDOWN` (`429`)
- `VERIFICATION_DAILY_LIMIT_REACHED` (`429`)
- `REGISTRATION_EXPIRED` (`410`)
- `EMAIL_ALREADY_VERIFIED` (`409`)

## Login and Resume Verification

`POST /api/v3/auth/login`

```json
{
  "email": "maria.reyes@example.com",
  "password": "StrongPass1!",
  "deviceIdentifier": "web-chrome-local"
}
```

An active user receives a Bearer token:

```json
{
  "success": true,
  "message": "Login successful.",
  "data": {
    "tokenType": "Bearer",
    "accessToken": "raw-session-token-returned-once",
    "expiresAt": "2026-08-31T09:30:00Z",
    "user": {
      "userId": 920001,
      "schoolId": "V2-LOCAL-TEST",
      "firstName": "Maria",
      "middleName": "Santos",
      "lastName": "Reyes",
      "suffix": null,
      "email": "maria.reyes@example.com",
      "role": "teacher",
      "status": "active",
      "mfaRequired": false
    }
  },
  "errors": null,
  "timestamp": "2026-08-31T01:30:00Z"
}
```

Correct credentials for an unverified registration return HTTP `403` and enough
state for the frontend to resume the verification screen:

```json
{
  "success": false,
  "message": "Verify the registered email address before login.",
  "data": null,
  "errors": {
    "code": "EMAIL_VERIFICATION_REQUIRED",
    "accountStatus": "pending_email_verification",
    "verificationMethod": "email",
    "emailMasked": "m***s@example.com",
    "registrationExpiresAt": "2026-09-30T01:30:00Z",
    "challengeUuid": "2f7a7269-b505-49b3-b4c9-c04401eae97c",
    "deliveryStatus": "sent",
    "otpExpiresAt": "2026-08-31T01:40:00Z",
    "resendAvailableAt": "2026-08-31T01:31:00Z",
    "canResend": true
  },
  "timestamp": "2026-08-31T01:35:00Z"
}
```

Other login codes include `INVALID_CREDENTIALS` (`401`),
`ACCOUNT_PENDING_APPROVAL` (`403`), `ACCOUNT_REJECTED` (`403`),
`ACCOUNT_INACTIVE` (`403`), `ACCOUNT_LOCKED` (`423`),
`LOGIN_RATE_LIMITED` (`429`), and `MFA_REQUIRED` (`403`).

## Current User and Logout

Send `Authorization: Bearer <accessToken>`.

- `GET /api/v3/auth/me` returns the `user` object shown in the login response.
- `POST /api/v3/auth/logout` revokes the current session.
- Missing or invalid credentials return HTTP `401` with code
  `AUTHENTICATION_REQUIRED`.

## Principal Teacher Approval

The complete principal approval contract, request/response examples, status
filters, ownership rules, and error codes are documented in
`V3_PRINCIPAL_TEACHER_APPROVAL_API.md`.

Key lifecycle rules:

- The default list is limited to `pending_approval` teachers.
- Only teachers with `email_verified_at` may be listed, viewed, approved, or
  rejected.
- Every query and status update is scoped to the authenticated principal's
  `school_id`; a payload school identifier is never trusted.
- Approval changes `pending_approval` to `active`.
- Rejection changes `pending_approval` to `rejected` and requires a 5-500
  character reason.
- Decisions use a transaction, row lock, conditional status update, and audit
  log. A repeated or stale decision does not silently overwrite state.

## Frontend Integration Rules

1. Keep the current production UI/API selection unchanged until an explicit V3
   integration phase begins.
2. At integration time, use the heading **Choose verification method** and
   render `verificationMethods` from the backend response.
3. Submit only an available method. Email is currently the sole available
   method; SMS is visible only as unavailable with its backend-provided reason.
4. After registration, route to the email-code page using `challengeUuid`.
5. On login error `EMAIL_VERIFICATION_REQUIRED`, resume that same page using the
   returned challenge and timing fields.
6. After successful verification, show pending principal approval; do not log the
   teacher in. The principal workspace may then load the verified queue from
   `GET /api/v3/users/teachers`.
7. Store the raw access token only after a successful active-account login and
   send it as a Bearer token on `/me` and `/logout`.
8. Use ISO-8601 UTC timestamps from the API. Convert only for display.

## Email Delivery Configuration

Local default uses `V3_EMAIL_DELIVERY_MODE=log`, which writes the OTP to backend
logs. Real delivery uses `V3_EMAIL_DELIVERY_MODE=smtp` and environment variables:

```text
V3_SMTP_HOST
V3_SMTP_PORT
V3_SMTP_USERNAME
V3_SMTP_PASSWORD
V3_SMTP_AUTH
V3_SMTP_STARTTLS
V3_SMTP_STARTTLS_REQUIRED
V3_EMAIL_FROM_ADDRESS
```

SMTP credentials must not be committed to source control.

## Validation Evidence

- Focused V3 principal teacher-account service suite: 10 tests passed; 0
  failures, 0 errors, 0 skipped.
- Current non-context regression suite: 162 tests passed; 0 failures, 0 errors,
  0 skipped.
- The full 163-test command was also attempted on 2026-09-02: 0 assertion
  failures and 1 infrastructure error in `AssessmentApplicationTests` because
  no local MySQL process was listening on port 3306. This is not recorded as a
  full-suite pass.
- Live readiness: 16/16 checks passed against `performance_assessment_v3_db`.
- Live reference-data endpoint: HTTP `200`.
- Live unauthenticated `/me`: HTTP `401`, code `AUTHENTICATION_REQUIRED`.
- CORS preflight: HTTP `200` for `http://localhost:5173` and
  `http://localhost:5174`.
- A real registration/email-delivery mutation was intentionally not performed as
  part of the smoke test, to avoid adding a fake teacher to the local V3 database.
