# Milestone 5 — Prompt 18: local staging and client handoffs

Date: 2026-09-14. Selected by the user: **local Android USB / adb reverse**.
Port correction requested by the user: backend **8080**, Mobile Metro **8081**.
The commands below now use 8080. Earlier Prompt 18 validation JSON/logs retain
their original 18082 port as historical evidence; they do not prove a new run on
8080. This configuration correction did not start or restart a server. Run only
one backend instance on 8080 at a time; the staging script refuses an occupied port.
V3 now declares `server.port=8080` directly. The Brevo launcher no longer sets
`V3_PORT`, and the staging launcher no longer passes `--server.port`. The packaged
staging application was refreshed for this port correction; previous jar hashes
remain historical validation evidence, not a fresh HTTP test of the new package.
This delivers a synthetic backend integration environment and reviewable Mobile
and Frontend instructions. It is not school deployment or physical acceptance.

Readiness route correction: `/api/v3/system/mobile-release-readiness` is now
registered under the ordinary `v3` profile as well. The usual Brevo launcher can
therefore query it without adding `v3-mobile-release`. A normal deployment with
release switches disabled returns HTTP 503 and `backendReady=false`, with the
failed checks in JSON; this is an intentional not-ready status, not a missing
endpoint. The endpoint does not enable uploads, migrate the database, or alter
authentication. Release startup validation remains limited to the release profile.

IDE diagnostic note: the Spring Boot 3.5.x OSS-support warning is real. The project
uses 3.5.16, which the [official Spring release notice](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)
identifies as the last OSS release in that generation. Resolving this warning
requires a tested upgrade to a supported generation (or applicable commercial
support); the readiness fix neither upgrades the framework nor suppresses the warning.

## Delivered environment

| Item | Value |
|---|---|
| Fixed backend origin | `http://127.0.0.1:8080` |
| Spring profiles | `v3,v3-mobile-release` |
| Database address | `127.0.0.1:33317`, separate local MariaDB instance |
| Synthetic database | `v3_scan_validation_full_110003` |
| Baseline used by this release instance | V3_023: 79 tables / 200 FKs / 131 checks / 118 uniques |
| Runtime directory | `D:/CAPSTONE_2/backend/assessment/output/local-mobile-staging/runtime` |
| Evidence storage | Runtime directory's `http-evidence/` |
| Immutable packaged application | `output/local-mobile-staging/assessment.jar` |
| Local synthetic teacher login | Runtime directory's `local-test-account.json`; read locally |
| Runtime readiness | GET `/api/v3/system/mobile-release-readiness` |

Runtime data, the copied jar, test account and logs are excluded from Git. The
database/evidence were copied while the isolated database was stopped, outside
Maven `target`, so an ordinary clean build does not delete this staging state.
No school database migration was run. The normal V3 profile still expects V3_014.

From the backend workspace, use:

```powershell
# Start only this dedicated instance; refuses occupied database/backend ports.
& ./tmp/start-local-mobile-staging.ps1

# Read status without changing it.
Invoke-RestMethod http://127.0.0.1:8080/api/v3/system/mobile-release-readiness

# Stop only the recorded and identity-checked staging processes; retain data.
& ./tmp/stop-local-mobile-staging.ps1
```

The server is a manual local staging process, not an auto-start Windows service.
After a PC restart, run the start script. Do not start another validation database
on port 33317 while it is running. The scripts refuse to replace an existing
listener; they never stop a process merely because its port matches.

## Android USB and browser connection

`adb` was found at `D:/Program/Android Studio/platform-tools/adb.exe`.
`adb devices -l` returned **no connected devices** during this session. Therefore
the USB reverse mapping has not been installed or verified on a phone.

After enabling USB debugging, connecting the phone and accepting its authorization:

```powershell
adb devices -l
adb -s <authorized-device-serial> reverse tcp:8080 tcp:8080
adb -s <authorized-device-serial> reverse --list
```

Use `http://127.0.0.1:8080` in the Mobile debug configuration and check readiness
from the phone/app. React Native does not need browser CORS, but Android debug
cleartext-network policy may need alignment. Reapply reverse after reconnection
when absent. Production remains an HTTPS hostname deployment.

Frontend uses `VITE_V3_API_URL=http://127.0.0.1:8080` and a Vite restart.
The HTTP CORS test used origin `http://localhost:5173`. This is not a completed
browser UI test. Do not append `/api/v3` to either client's configured origin.

## Migration, backup and validation evidence

The initial rehearsal was produced by `tmp/run-prompt18-staging.ps1` under
`target/scan-recovery-mariadb-20260914-110000`, then preserved in the runtime folder.
It used an independently initialized local database instance and rewrote explicit
canonical schema database names to a fresh synthetic schema before execution.
It never ran the entire migrations directory or V3_000.

1. Constructed the reviewed V3_014 baseline using canonical schema plus selected
   seed/template/calendar deltas. Canonical schema already includes V3_013.
2. Verified **67 tables / 163 FKs / 87 checks / 99 uniques**.
3. Created `pre-V3_015.sql` and restored it into a separate synthetic `_restore`
   schema. Compared baseline and restored constraint/table counts successfully.
   This is a synthetic schema/seed restore rehearsal, not a school-data recovery
   certification or migration rollback after new user writes.
4. Applied V3_015 through V3_023 in order and verified **79/200/131/118**.
   Exact SQL SHA-256 values are retained in `migration-hashes.json`.
5. Started the real Spring HTTP/security stack and exercised the supported
   objective workflow. `live-http-evidence.json` records statuses and synthetic
   official analytics/report output without bearer tokens or passwords.

Detailed machine-readable evidence: [Prompt 18 validation](V3_PROMPT_18_VALIDATION.json).
Local full evidence includes `baseline-counts.txt`, `restore-counts.txt`,
`release-counts.txt`, `http-tests.log`, `fixed-url-readiness.json`, backend logs,
`regression-suites.json` and `default-suites.json`.

The focused regression ran 223 discovered tests: **222 passed, 1 skipped**. This
includes **156 executed MariaDB persistence tests**. The new real-HTTP method is
deliberately separate because its readiness test needs the unchanged baseline
template inventory; other mutation tests intentionally alter that inventory.

The broader default Maven suite ran 669 tests: **511 passed, 157 skipped, 1 error**.
The error is the existing unprofiled `AssessmentApplicationTests.contextLoads`,
whose default datasource connection was refused. It could not execute its SQL
initializer. Within that run, **431 V3 tests passed, 157 were environment-gated**.
The jar was packaged with tests skipped only after recording those test outcomes;
this is not a claim that the unrestricted default suite passed.

To re-run the single HTTP check, stop staging first and run
`tmp/recheck-local-staging-http.ps1`. It creates fresh synthetic teacher/assessment
fixtures in this synthetic database and updates the local test-account file.
It is a validation command, not the normal start command. Do not run it while a
client is using the fixture account. Start the staged application afterward.

## Backend fixes discovered during staging

- Release preflight now runs during bean initialization, before the application
  accepts HTTP requests. ApplicationRunner would validate after web startup.
- Release multipart limits now match the existing 15 MiB evidence contract,
  with a 16 MiB request allowance. A DTO accepting 15 MiB does not override the
  servlet's smaller default multipart limit.
- Readiness rejects unknown deployment modes, production IP/loopback addresses,
  URL subpaths and invalid ports; bracketed IPv6 loopback is handled correctly.
- The HTTP test sends ISO UTC timestamp text; its first numeric-timestamp request
  was correctly rejected by the strict backend contract. No DTO was weakened.
- Background MariaDB launch now uses explicit console/log handles after an
  earlier copied-instance launch exited before JDBC validation.

## Handoffs and finite remaining route

- [Mobile integration guide](V3_MOBILE_INTEGRATION_HANDOFF.md): exact route/pack
  map, durable retries, session decision, schedules/status and M1–M4 sessions.
- [Frontend integration/design guide](V3_FRONTEND_INTEGRATION_DESIGN_HANDOFF.md):
  existing client/report route mapping, compact SMART design, states and F1–F2.
- [Milestone tracker](V3_MOBILE_MILESTONE_TRACKER.md): Prompt 19 remains client
  integration and shared acceptance within Milestone 5.

The two guides include copy-paste instructions. They have **not been sent to
another AI task**. Client AIs should return changed files, actual checks and a
redacted reproduction for backend defects. No Mobile/Frontend code, SQLite,
APK, phone settings or SF1 feature was changed in this backend session.

Acceptance still requires an actual phone, secure persisted Mobile session,
client queue/SQLite wiring, Frontend rendering, restart/offline/retry behavior
and matching current official result on both clients. The full new HTTP test is
objective; MFA, written and audit workflows retain their component/regression
evidence and still require coordinated live client acceptance.

Current original capture eligibility is one-page A4 ten-MC. Synthetic TF/written
tests do not prove other layouts can be scanned/uploaded. The synthetic API
fixtures are not presentation-ready printed sheets; prepare a real supported
assessment/manifest through the normal authoring/print flow before device tests.
Dynamic QR remains a physical acceptance gate. Basic Mobile analytics are only
official total/max/percentage; advanced modules are not enabled here.

Frontend report rows currently provide a numeric testResultId but not resultUuid;
direct UUID detail links need a supported mapping. The reviewed Mobile routes do
not provide secure image download for browser evidence review. Existing Web
Assessment Results can proceed while those optional detail/media actions remain
unavailable. These limitations must not be hidden by a green backendReady flag.
