# V3 Authenticator App MFA API

**Status:** Backend implemented and unit-tested

**Profile:** `v3`

**Local base URL:** `http://localhost:8082`

## Scope

Authenticator App MFA is optional and may be enabled later from an authenticated
principal or teacher account's Security Settings. It is not part of teacher
registration and does not replace registration email verification or principal
approval.

The account lifecycle remains:

```text
teacher registration
-> email verification
-> principal approval
-> active account
-> optional authenticator enrollment from Settings
```

Once MFA is enabled, a password-only login never receives an access token. The
user must complete the returned MFA challenge first.

## Security Model

- TOTP follows RFC 6238 with SHA-1, six digits, and a 30-second period.
- The server accepts the current time step plus the configured verification
  window. The default is one step before or after the current step.
- TOTP secrets are encrypted at rest with AES-256-GCM.
- Recovery codes are generated once, returned once, and stored only as hashes.
- A recovery code is consumed atomically and cannot be reused.
- Reusing the same accepted TOTP time step is rejected.
- Login challenges expire after five minutes by default.
- Five invalid MFA attempts lock the challenge by default.
- Enrollment, recovery-code regeneration, and disable require the current
  password plus a valid authenticator or recovery code where applicable.
- Disabling MFA revokes all other sessions for the account.
- MFA enrollment, rejection, login challenge, recovery-code regeneration, and
  disable operations are audit logged.
- Only active, email-verified `principal` and `teacher` accounts can manage MFA.

The required schema already exists in V3:

- `users.mfa_required`
- `user_mfa_factors`
- `mfa_recovery_codes`
- `mfa_authentication_challenges`
- MFA fields in `auth_sessions`
- MFA failure values in `login_attempts`

No new database migration is required for this slice.

## API Envelope

All responses use:

```json
{
  "success": true,
  "message": "Request completed successfully.",
  "data": {},
  "errors": null,
  "timestamp": "2026-09-04T08:00:00Z"
}
```

Protected endpoints require:

```http
Authorization: Bearer <access-token>
Content-Type: application/json
```

## 1. Get MFA Status

```http
GET /api/v3/auth/mfa/status
```

Roles: `principal`, `teacher`

```json
{
  "success": true,
  "message": "Authenticator security status retrieved successfully.",
  "data": {
    "available": true,
    "enabled": false,
    "factorUuid": null,
    "factorName": null,
    "verifiedAt": null,
    "lastUsedAt": null,
    "unusedRecoveryCodeCount": 0
  },
  "errors": null,
  "timestamp": "2026-09-04T08:00:00Z"
}
```

`available=false` means the server has not been configured with a valid MFA
encryption key. The frontend must disable setup and show a configuration message.

## 2. Start Enrollment

```http
POST /api/v3/auth/mfa/enrollment
```

Roles: `principal`, `teacher`

Request:

```json
{
  "password": "CurrentPassword1!",
  "factorName": "My phone"
}
```

Success data:

```json
{
  "factorUuid": "11e6c035-b0ca-42c9-8fc4-a2858c67b216",
  "factorName": "My phone",
  "status": "pending",
  "manualEntryKey": "JBSWY3DPEHPK3PXP",
  "otpauthUri": "otpauth://totp/SMART%20Assessment%3Ateacher%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=SMART%20Assessment&algorithm=SHA1&digits=6&period=30",
  "qrCodeDataUrl": "data:image/png;base64,iVBORw0KGgo...",
  "enrolledAt": "2026-09-04T08:00:00Z"
}
```

Frontend behavior:

1. Reconfirm the current password.
2. Show the QR image from `qrCodeDataUrl`.
3. Also show `manualEntryKey` as the accessibility/manual fallback.
4. Do not mark MFA enabled yet. Enrollment remains `pending` until confirmation.

Starting enrollment again revokes an older pending enrollment. It does not
disable an already active factor.

## 3. Confirm Enrollment

```http
POST /api/v3/auth/mfa/enrollment/confirm
```

Roles: `principal`, `teacher`

Request:

```json
{
  "factorUuid": "11e6c035-b0ca-42c9-8fc4-a2858c67b216",
  "code": "123456"
}
```

Success data:

```json
{
  "enabled": true,
  "factorUuid": "11e6c035-b0ca-42c9-8fc4-a2858c67b216",
  "recoveryCodes": [
    "ABCD-2345-EFGH",
    "JKLM-6789-NPQR"
  ]
}
```

The real response contains ten recovery codes by default. They are shown only
in this response. The frontend must require the user to download, print, or
explicitly confirm they stored the codes before closing the success step.

The current authenticated session is upgraded to MFA, so the existing token may
continue to be used after successful confirmation.

## 4. Password Login When MFA Is Enabled

```http
POST /api/v3/auth/login
```

Public endpoint.

Request:

```json
{
  "email": "teacher@example.com",
  "password": "CurrentPassword1!",
  "deviceIdentifier": "web-chrome"
}
```

Password accepted, MFA required:

```json
{
  "success": true,
  "message": "Authenticator verification required.",
  "data": {
    "tokenType": null,
    "accessToken": null,
    "expiresAt": null,
    "user": null,
    "mfaRequired": true,
    "mfaChallenge": {
      "challengeUuid": "a98ece17-b5bf-43ef-9393-18f8c779a571",
      "expiresAt": "2026-09-04T08:05:00Z",
      "maximumAttempts": 5,
      "verificationMethods": ["authenticator", "recovery_code"],
      "emailMasked": "t***@example.com"
    }
  },
  "errors": null,
  "timestamp": "2026-09-04T08:00:00Z"
}
```

The frontend must not save a token, set an authenticated user, or navigate to a
dashboard from this response. It should retain only the challenge UUID and show
the authenticator-code screen.

## 5. Complete MFA Login

```http
POST /api/v3/auth/mfa/login/verify
```

Public endpoint. No Bearer token is expected because login is not complete.

Authenticator request:

```json
{
  "challengeUuid": "a98ece17-b5bf-43ef-9393-18f8c779a571",
  "code": "123456",
  "verificationMethod": "authenticator",
  "deviceIdentifier": "web-chrome"
}
```

Recovery-code request:

```json
{
  "challengeUuid": "a98ece17-b5bf-43ef-9393-18f8c779a571",
  "code": "ABCD-2345-EFGH",
  "verificationMethod": "recovery_code",
  "deviceIdentifier": "web-chrome"
}
```

Success data has the normal login shape:

```json
{
  "tokenType": "Bearer",
  "accessToken": "<raw-access-token>",
  "expiresAt": "2026-09-04T16:00:00Z",
  "user": {
    "userId": 910002,
    "schoolId": "SCHOOL-001",
    "firstName": "Maria",
    "middleName": null,
    "lastName": "Teacher",
    "suffix": null,
    "email": "teacher@example.com",
    "role": "teacher",
    "status": "active",
    "mfaRequired": true
  },
  "mfaRequired": false,
  "mfaChallenge": null
}
```

Only after this response may the frontend store `accessToken` and establish an
authenticated session.

## 6. Regenerate Recovery Codes

```http
POST /api/v3/auth/mfa/recovery-codes/regenerate
```

Roles: `principal`, `teacher`

```json
{
  "password": "CurrentPassword1!",
  "code": "123456",
  "verificationMethod": "authenticator"
}
```

The response uses the same shape as enrollment confirmation. Every previous
recovery code is invalidated. `verificationMethod` may be `authenticator` or
`recovery_code`; omitted values default to `authenticator`.

## 7. Disable MFA

```http
POST /api/v3/auth/mfa/disable
```

Roles: `principal`, `teacher`

```json
{
  "password": "CurrentPassword1!",
  "code": "123456",
  "verificationMethod": "authenticator"
}
```

Success:

```json
{
  "success": true,
  "message": "Authenticator security disabled.",
  "data": null,
  "errors": null,
  "timestamp": "2026-09-04T08:00:00Z"
}
```

Other active sessions are revoked. The session used to authorize the disable
request remains active.

## Error Contract

Example:

```json
{
  "success": false,
  "message": "The authenticator or recovery code is invalid.",
  "data": null,
  "errors": {
    "code": "MFA_CODE_INVALID",
    "attemptsRemaining": 4
  },
  "timestamp": "2026-09-04T08:00:00Z"
}
```

| HTTP | Code | Meaning |
|---:|---|---|
| 400 | `VALIDATION_FAILED` | Missing, malformed, or oversized field |
| 400 | `PASSWORD_CONFIRMATION_FAILED` | Current password is incorrect |
| 400/401 | `MFA_CODE_INVALID` | Invalid authenticator or recovery code |
| 401 | `MFA_CHALLENGE_INVALID` | Missing, completed, cancelled, or unknown challenge |
| 401 | `MFA_CHALLENGE_EXPIRED` | Challenge TTL elapsed; restart password login |
| 403 | `ACCOUNT_NOT_ACTIVE` | Account is not active and email verified |
| 404 | `MFA_ENROLLMENT_NOT_FOUND` | Pending enrollment does not exist |
| 409 | `MFA_ALREADY_ENABLED` | Active factor already exists |
| 409 | `MFA_ENROLLMENT_CONFLICT` | Enrollment state changed concurrently |
| 409 | `MFA_CODE_REPLAYED` | Accepted TOTP step was already used |
| 409 | `MFA_NOT_ENABLED` | Account has no active MFA factor |
| 409 | `MFA_STATE_CONFLICT` | Factor changed during the request |
| 429 | `MFA_CHALLENGE_LOCKED` | Challenge reached its attempt limit |
| 429 | `MFA_RATE_LIMITED` | Recent MFA failures exceeded the limit |
| 429 | `SENSITIVE_ACTION_RATE_LIMITED` | Too many password confirmation failures |
| 503 | `MFA_CONFIGURATION_ERROR` | MFA is disabled or encryption key is unavailable |
| 503 | `MFA_KEY_VERSION_UNAVAILABLE` | Stored factor uses an unavailable encryption-key version |

## Server Configuration

Required production environment settings:

```text
V3_MFA_ENABLED=true
V3_MFA_ISSUER=SMART Assessment
V3_MFA_CHALLENGE_TTL=5m
V3_MFA_MAX_ATTEMPTS=5
V3_MFA_RECOVERY_CODE_COUNT=10
V3_MFA_VERIFICATION_WINDOW=1
V3_MFA_SECRET_KEY_VERSION=1
V3_MFA_QR_CODE_SIZE=240
V3_MFA_ENCRYPTION_KEY=<base64-encoded 32-byte secret>
```

Generate a local key in PowerShell:

```powershell
$key = [byte[]]::new(32)
[Security.Cryptography.RandomNumberGenerator]::Fill($key)
[Convert]::ToBase64String($key)
```

Store the output as `V3_MFA_ENCRYPTION_KEY` in the runtime environment. Never
commit it. Losing or changing the key without a key-rotation plan makes existing
authenticator factors unreadable. Render must receive the same environment
variable through its secret configuration.

### Local Windows launcher

`run-v3-with-brevo.ps1` now enables Authenticator MFA together with Brevo SMTP.
It resolves the encryption key in this order:

1. Use a valid process-level `V3_MFA_ENCRYPTION_KEY`, when supplied.
2. Otherwise load the Windows-user-protected key from
   `%LOCALAPPDATA%\SMARTAssessment\secrets\v3-mfa-encryption-key.dpapi`.
3. If neither exists, generate one cryptographically random 32-byte key and
   save only its Windows DPAPI-protected representation at that path.

The plaintext key is exposed only to the launched Spring Boot process and is
removed from the launcher environment when the backend exits. The protected
file can be decrypted only by the same Windows account on the same machine.
Back it up securely and do not delete or regenerate it after users enroll.

Start the complete local V3 registration-email and Authenticator MFA runtime:

```powershell
powershell -ExecutionPolicy Bypass -File .\run-v3-with-brevo.ps1
```

The launcher is a local-development convenience only. On Render or another
server, configure `V3_MFA_ENCRYPTION_KEY` as a persistent secret environment
variable and start the V3 Spring profile normally. Do not upload the local
DPAPI file because it is not portable.

## Frontend Integration Checklist

1. Add a Security section for active principal and teacher accounts.
2. Load `/api/v3/auth/mfa/status` and distinguish `available` from `enabled`.
3. Implement enrollment QR/manual-key and six-digit confirmation steps.
4. Display recovery codes once and require an explicit storage confirmation.
5. Branch `/api/v3/auth/login` on `data.mfaRequired`.
6. Never store a token from the password step when MFA is required.
7. Add authenticator and recovery-code modes to the challenge screen.
8. Add recovery-code regeneration and MFA disable confirmations.
9. Clear an expired/locked challenge and return to password login.
10. Use only `VITE_V3_API_URL` and `/api/v3`; do not add V1/V2 fallbacks.

## Explicit Non-Changes

- Teacher registration email OTP is unchanged.
- Principal approval is unchanged.
- SMS verification is unchanged and remains a separate future provider concern.
- No frontend, Mobile, OMR, analytics, printing, V1/V2, TiDB, or schema migration
  is part of this backend slice.
