# Frontend Handoff: Teacher Registration Verification Method

## Required flow

Registration form and review
-> Choose verification method
-> Create pending teacher and send OTP
-> Enter OTP
-> Pending Principal approval

The method-selection screen must appear before the frontend calls
`POST /api/v2/auth/register-teacher`. The current registration endpoint creates the pending account
and sends the OTP in the same transaction.

## Reference data

Read `data.verificationMethods` from:

`GET /api/v2/auth/teacher-registration/reference-data`

```json
{
  "verificationMethods": [
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
}
```

Do not hardcode method availability. Render unavailable methods as disabled choices with their
backend-provided reason.

## Registration request change

Add the selected method to the existing request:

```json
{
  "schoolCode": "SCHOOL-001",
  "verificationMethod": "email",
  "email": "teacher@example.com"
}
```

All existing registration fields remain required as before. Only the new field is shown above for
brevity.

Backend validation:

- Missing/unknown method: `INVALID_VERIFICATION_METHOD` with a `verificationMethod` field error.
- SMS selected while unavailable: `VERIFICATION_METHOD_UNAVAILABLE` with a
  `verificationMethod` field error.
- No account or OTP is inserted when method validation fails.

## Screen behavior

- Insert a separate `Choose verification method` step after Account/Review and before the OTP page.
- Show Email as an enabled choice and display the applicant's email address.
- Show Text message (SMS) as disabled with `SMS verification is not available yet.`
- Require explicit Email selection before enabling `Send verification code`.
- Keep the completed registration form in component state when the user goes Back.
- Call `registerTeacherV2` only after `Send verification code` is clicked.
- Include `verificationMethod: 'email'` in the payload.
- On HTTP 201, store the verification email and navigate to the existing OTP page.
- Keep existing 409, 503, resend cooldown, OTP expiry, and field-error handling.

Recommended labels:

- Heading: `Choose verification method`
- Supporting text: `Select where SMART should send your registration verification code.`
- Primary action: `Send verification code`

Do not label this registration step as two-factor authentication. It verifies control of one contact
channel. Login 2FA is a separate future feature.
