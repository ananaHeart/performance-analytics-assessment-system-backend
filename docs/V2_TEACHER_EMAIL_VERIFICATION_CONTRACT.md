# V2 Teacher Email Verification Contract

## Status model

The existing `pending` and `active` account statuses remain authoritative:

- `pending` plus `email_verified_at IS NULL`: waiting for email verification.
- `pending` plus `email_verified_at IS NOT NULL`: waiting for Principal approval.
- `active` plus `email_verified_at IS NOT NULL`: may log in.

Unverified teachers are excluded from the Principal teacher list. Principal approval is rejected with
`EMAIL_VERIFICATION_REQUIRED` until `email_verified_at` is populated.

## Public endpoints

All endpoints below work without a Bearer token.

### Register teacher

`POST /api/v2/auth/register-teacher`

Before registration submission, the client must let the applicant select one method returned by
`GET /api/v2/auth/teacher-registration/reference-data` under `verificationMethods`:

```json
[
  {
    "method": "email",
    "displayName": "Email",
    "available": true,
    "unavailableReason": null
  },
  {
    "method": "sms",
    "displayName": "Text message (SMS)",
    "available": false,
    "unavailableReason": "SMS verification is not available yet."
  }
]
```

The registration request requires `"verificationMethod": "email"`. SMS is reserved for a future
provider integration and is rejected with `VERIFICATION_METHOD_UNAVAILABLE` before any account is
saved. A successful request returns HTTP 201, creates a `pending` teacher, and issues a six-digit
OTP to the submitted email.

```json
{
  "success": true,
  "message": "Teacher account created. Verify the registered email before principal approval.",
  "data": {
    "userId": 910004,
    "email": "teacher@example.com",
    "role": "teacher",
    "status": "pending",
    "emailVerified": false
  },
  "errors": null,
  "timestamp": "2026-08-22T10:32:10.883303900Z"
}
```

### Verify email

`POST /api/v2/auth/verify-teacher-email`

```json
{
  "email": "teacher@example.com",
  "otp": "078095"
}
```

```json
{
  "success": true,
  "message": "Email verified successfully. The teacher account is now pending principal approval.",
  "data": {
    "email": "teacher@example.com",
    "emailVerified": true,
    "accountStatus": "pending",
    "expiresAt": null,
    "resendAvailableAt": null
  },
  "errors": null,
  "timestamp": "2026-08-22T10:32:46.104975800Z"
}
```

### Resend code

`POST /api/v2/auth/resend-teacher-verification`

```json
{
  "email": "teacher@example.com"
}
```

The response uses the same data shape and includes `expiresAt` and `resendAvailableAt` for the new
code. The default code lifetime is 10 minutes, resend cooldown is 1 minute, and maximum incorrect
attempts is 5.

## Error codes

- `INVALID_VERIFICATION_CODE`: incorrect OTP.
- `VERIFICATION_CODE_EXPIRED`: expired OTP.
- `VERIFICATION_CODE_USED`: OTP was already consumed.
- `VERIFICATION_ATTEMPTS_EXCEEDED`: maximum attempts reached.
- `VERIFICATION_RESEND_COOLDOWN`: resend requested too early.
- `EMAIL_ALREADY_VERIFIED`: the account is already verified.
- `EMAIL_VERIFICATION_REQUIRED`: Principal tried to approve an unverified account.
- `ACCOUNT_NOT_ACTIVE`: teacher tried to log in before Principal approval.

## Delivery configuration

Local development defaults to `V2_EMAIL_DELIVERY_MODE=log`; the OTP appears only in the backend log.
Real email delivery requires `V2_EMAIL_DELIVERY_MODE=smtp` and the following environment variables:

- `V2_SMTP_HOST`
- `V2_SMTP_PORT`
- `V2_SMTP_USERNAME`
- `V2_SMTP_PASSWORD`
- `V2_EMAIL_FROM_ADDRESS`

SMTP credentials must not be committed to source control.

## Frontend flow

1. Complete and validate all registration fields locally.
2. Show a separate `Choose verification method` step before calling the registration endpoint.
3. Render methods from reference data. Email is selectable; SMS is visible but disabled.
4. After the applicant selects Email and confirms, submit registration with
   `"verificationMethod": "email"`.
5. On HTTP 201, navigate to the OTP screen and retain the submitted email.
6. Verify through `/verify-teacher-email`.
7. On success, show `Email verified - pending Principal approval` and return to login.
8. Provide resend with a visible cooldown; do not generate or compare OTPs in the frontend.

This registration OTP verifies control of one contact channel. It must be described as contact/email
verification, not login two-factor authentication. Login 2FA remains a separate future security feature.
