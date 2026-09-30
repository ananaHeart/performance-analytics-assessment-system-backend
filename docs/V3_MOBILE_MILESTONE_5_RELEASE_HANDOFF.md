# V3 Mobile Milestone 5 — Prompt 17 release handoff

Updated: 2026-09-14

## Result of this session

The backend now has a fail-closed Mobile release profile and a machine-readable
preflight endpoint. This prepares deployment; it does not migrate a school
database, wire Mobile SQLite, provide a public host, or prove the physical scanner.

Use both Spring profiles for a release candidate:

```text
v3,v3-mobile-release
```

The `v3-mobile-release` profile selects the reviewed draft chain through `V3_023`
(79 tables, 200 foreign keys, 131 checks and 118 unique constraints). Mobile write
routes remain closed until `V3_MOBILE_HTTP_ENABLED=true`. The ordinary `v3` profile
continues to expect V3_014 (67/163/87/99) and continues to deny those routes.

## Release preflight

Before enabling writes, provision these deployment values:

```text
V3_MOBILE_RELEASE_MODE=development
V3_MOBILE_PUBLIC_BASE_URL=http://<LAN-IP>:8082
V3_SCAN_EVIDENCE_STORAGE_DIRECTORY=<existing-absolute-writable-directory>
V3_SCAN_RECOVERY_ENABLED=true
V3_MOBILE_FINALIZATION_ENABLED=true
V3_MOBILE_READBACK_ENABLED=true
V3_MOBILE_EVALUATION_REFERENCE_ENABLED=true
V3_MOBILE_REOPEN_ENABLED=true
V3_MOBILE_CORRECTION_ENABLED=true
V3_MOBILE_SUPERSEDE_ENABLED=true
```

For Android USB testing with `adb reverse`, use a loopback base URL and explicitly
set `V3_ADB_REVERSE_ENABLED=true`. Loopback is rejected unless that development
mode is declared, and it is always rejected in production.

For production, set `V3_MOBILE_RELEASE_MODE=production`, use an HTTPS hostname,
configure SMTP delivery, and provide the MFA encryption key.

After the database backup and reviewed `V3_015` through `V3_023` migration, set:

```text
V3_MOBILE_HTTP_ENABLED=true
```

When that switch is true, startup stops with an error if any preflight item fails.
The release endpoint is:

```http
GET /api/v3/system/mobile-release-readiness
```

It returns HTTP 200 only when the backend activation checks pass. It separately
reports `mobileWiringVerified`, `physicalScannerVerified`, and `fullyConnected`.
Therefore a green backend cannot by itself declare the whole Mobile workflow ready.
The endpoint does not disclose credentials or the evidence filesystem path.

## Authentication decision for Mobile

The final backend contract continues to use the database-backed bearer session:

- Login or MFA verification returns the bearer token and `expiresAt`.
- Mobile stores the raw token in platform secure storage and restores it on restart.
- Mobile calls `GET /api/v3/auth/me` after restoration before starting sync.
- Logout revokes the server session; Mobile removes its stored token even if its
  local cleanup must finish after a network error.
- There is no refresh-token endpoint in this release. On expiry or HTTP 401, Mobile
  clears the token and performs password login plus MFA when required.
- The release preflight accepts a session TTL from 1 minute through 24 hours. The
  configured default remains 8 hours.

This finalizes the backend expiry/refresh decision. Secure token persistence is a
Mobile implementation task and is not claimed by this backend change.

## Reviewed migration order

Create a backup and confirm the source is the exact V3_014 baseline before applying,
in order:

1. `V3_015_mobile_scan_upload_receipts_DRAFT.sql`
2. `V3_016_mobile_detection_receipts_DRAFT.sql`
3. `V3_017_mobile_teacher_verification_DRAFT.sql`
4. `V3_018_mobile_result_finalization_DRAFT.sql`
5. `V3_019_mobile_attachment_uploads_DRAFT.sql`
6. `V3_020_mobile_written_verification_DRAFT.sql`
7. `V3_021_mobile_result_reopen_DRAFT.sql`
8. `V3_022_mobile_result_corrections_DRAFT.sql`
9. `V3_023_mobile_result_supersession_DRAFT.sql`

Do not apply the chain to the school database until its backup, target database name,
credentials, maintenance window and rollback owner have been reviewed. This session
did not execute that deployment.

## Presentation acceptance scenario

Run the following on one teacher account and one controlled assessment:

1. Check release readiness, then login/MFA and `/me`.
2. Download reference data, teacher scope and answer-sheet manifest.
3. Put the phone offline, scan MC/TF and written pages, and verify uncertain answers.
4. Restore connectivity and upload the same page twice to prove replay behavior.
5. Upload detections, written evidence, comments and rubric/numeric scores.
6. Finalize and retrieve the backend-authoritative result and analytics.
7. Reopen one result, correct it, re-finalize it, then test page rescan or supersession.
8. Confirm current readback, score version, audit history and reconciliation state.

Record the response IDs and hashes. Keep dynamic QR/scanner reliability marked
pending until this exact scenario passes on the presentation phone and printed sheet.

## Remaining Milestone 5 prompts

- **Prompt 18 — staging deployment and live backend smoke test:** reviewed backup,
  migration, provisioned storage, stable URL and live HTTP evidence.
- **Prompt 19 — Mobile production wiring and device acceptance:** secure token storage,
  V3 SQLite activation/migration, retry queue wiring and the physical end-to-end run.

Prompt 19 belongs primarily to the Mobile workspace. Backend support may be needed
for contract defects discovered during the device run.
