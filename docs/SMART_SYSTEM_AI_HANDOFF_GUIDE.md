# SMART Assessment System AI Handoff Guide

Generated: 2026-09-15

Purpose: this guide is for any new AI assistant joining the SMART assessment project. Read this first before changing code. It explains the backend, frontend, and mobile-side structure, current progress, remaining work, and safety rules. It is intentionally honest: some modules are implemented only in isolated tests or staging, and that must not be described as full production acceptance.

## Identity Of This Handoff

Hello. I am the consolidated handoff voice for three workstreams:

- Backend: Spring Boot API, MySQL/MariaDB/TiDB-compatible database work, DTO contracts, authentication, OMR upload/scoring, reports, and release gates.
- Frontend: React/Vite dashboard for principal and teacher workflows, including login, teacher approval, assessment setup, class records, reports, analytics display, security settings, and V2/V3 API clients.
- Mobile-side: React Native Android app for teacher login, offline data, OMR scanning, SQLite storage, upload/retry, verification, and isolated V3 diagnostics.

Treat this guide as context, not as permission to edit. If the user says "no coding", "scan only", "confirmation only", or "wait", do not modify files, run migrations, build APKs, stop servers, or push code.

## Current Repository Map

Main backend repo:

- Path: `D:\CAPSTONE_2\backend\assessment`
- GitHub remote: `https://github.com/ananaHeart/performance-analytics-assessment-system-backend.git`
- Current branch recently pushed: `agent/v2-sf1-import`
- Latest pushed branch commit observed: `a547d0a Update backend integration and V2 support`
- `main` was still behind that branch at the time of this handoff.

Frontend repo:

- Path: `D:\CAPSTONE_2\web-dashboard`
- Vite/React dashboard.
- Runtime dev port is usually Vite default `5173`.

Mobile repo:

- Path: `D:\ThesisProjects\MobileAssessmentApp`
- React Native Android app.
- Metro port is `8081`.
- Backend should be `8080` for local backend. Do not reintroduce `18082` unless the user explicitly requests a temporary isolated staging server.

OMR prototype/reference assets:

- Path: `D:\ThesisProjects\OMRPrototype`
- Contains approved dynamic mixed-layout PDF and manifest references for A4, US Letter, and US Legal.
- These must be compared before backend dynamic template alignment. Do not invent OMR geometry.

## Core Technology Stack

Backend:

- Java 17
- Spring Boot 3.5.16
- Maven wrapper and Maven build
- Spring Web
- Spring Security
- Spring Validation
- Spring Mail
- MySQL connector
- JdbcTemplate-style repository code, with some JPA dependency present
- PDFBox for PDF generation/inspection
- ZXing for QR code generation/reading
- Apache POI for Excel/SF1/import/export work
- Dockerfile uses Eclipse Temurin 17 and exposes port `8080`

Frontend:

- React 19
- Vite 8
- Tailwind CSS 4 plus project CSS files
- Radix UI primitives
- lucide-react icons
- Recharts for analytics/report charts
- API clients in `src/api`

Mobile:

- React Native 0.83.1
- React 19.2
- TypeScript
- Jest and ESLint
- react-native-quick-sqlite
- Android native Kotlin/Java bridge modules
- ZX/OpenCV-like native scanning logic through local native detector modules
- Android Keystore-backed secure session module was added in the mobile project

## Runtime Ports And Local Rules

Use these normal local ports:

- Backend API: `8080`
- React Native Metro: `8081`
- Frontend Vite: commonly `5173`

For Android USB local testing:

- `adb reverse tcp:8080 tcp:8080`
- `adb reverse tcp:8081 tcp:8081`

Do not hardcode LAN IPs into production. For production, use an HTTPS hostname. React Native is not browser-CORS restricted, but the phone must be able to reach the backend.

## Backend Folder Structure

Backend root important files:

- `pom.xml`: Maven dependencies and Java version.
- `Dockerfile`: two-stage Java 17 build and runtime image.
- `mvnw`, `mvnw.cmd`, `.mvn`: Maven wrapper.
- `HELP.md`: generated Spring help.
- `.gitignore`: should exclude output evidence and local staging folders.
- `run-v2-with-brevo.ps1`, `run-v2-with-gmail.ps1`, `run-v3-with-brevo.ps1`: local helper scripts for email-enabled runs.
- `run-v3-dynamic-staging.ps1`: temporary dynamic staging helper. Be careful: dynamic OMR alignment was paused because the backend implementation did not yet match the approved Mobile/prototype template.

Backend source:

- `src/main/java/com/capstone/assessment/AssessmentApplication.java`: Spring Boot entry point.
- `src/main/java/com/capstone/assessment/common`: shared response wrappers and exception handling.
- `src/main/java/com/capstone/assessment/config`: global CORS/security configuration.
- `src/main/java/com/capstone/assessment/auth`, `analytics`, `assessmentsetup`, `competency`, `gradingperiod`, `importexport`, `schoolsetup`, `sync`: older/root flows from the earlier system.
- `src/main/java/com/capstone/assessment/v2`: V2 implementation.
- `src/main/java/com/capstone/assessment/v3`: V3 implementation.

Backend resources:

- `src/main/resources/application.properties`: default local V1-ish config, port from `${PORT:8080}`, database `performance_assessment_db`.
- `src/main/resources/application-v2.properties`: V2 profile, default database `performance_assessment_v2_db`, session settings, analytics thresholds, email verification settings.
- `src/main/resources/application-v3.properties`: V3 profile, default database `performance_assessment_v3_db`, V3 baseline checks, answer-sheet/evidence output directories, auth/MFA/email settings, and mobile feature gates defaulting to false.
- `src/main/resources/application-v3-mobile-release.properties`: V3 mobile release profile, expects 79-table release staging totals and keeps HTTP/write gates disabled unless explicitly enabled.
- `src/main/resources/application-v3-dynamic-staging.properties`: temporary dynamic staging profile. Do not treat as production.

Backend docs:

- `docs/BACKEND_API_DOCUMENTATION.md`: broader API documentation.
- `docs/BACKEND_DOCUMENTATION_STATUS.md`: documentation status.
- `docs/V1_TO_V2_MIGRATION_PLAN.md`: V1 to V2 migration plan.
- `docs/V2_*`: V2 API, analytics, database, OMR, and teacher email verification docs.
- `docs/V3_*`: V3 API, mobile, reports, auth, MFA, school setup, migration, and handoff docs.
- `docs/contracts/mobile-v3`: versioned Mobile V3 contract packs from `1.0.0` through `1.11.0`.
- `docs/migrations`: V2 and V1-to-V2 migrations.
- `docs/migrations/v3`: V3 migration files. Some are final baseline files; some are draft/staging files.
- `docs/backups`: database backups. Do not commit new backups casually; they may contain sensitive hashes/session history.

Backend generated/local folders:

- `output`: generated PDFs, validation output, staging output, evidence output. Usually not for deploy commits.
- `tmp`: temporary scripts and scratch files. Usually not for deploy commits.
- `patch_fix`: patch/scratch folder. Usually not for deploy commits.
- `.codex*.log`, `debug.log`: local logs. Do not commit unless the user explicitly asks.

## Backend V2 Structure

`src/main/java/com/capstone/assessment/v2` contains:

- `auth`: login, sessions, token validation, email verification, Spring Security filter, entrypoint, and access-denied handling.
- `account`: teacher registration/account management, reference data, principal approval support.
- `schoolsetup`: academic years/classes/sections/assignments setup.
- `assessment`: assessment creation, parts, questions, OMR fixed answer sheet printing, questionnaire PDF support.
- `sync`: mobile sync/download/upload style V2 contracts.
- `analytics`: V2 analytics, LMS-like responses, item/grade/mastery/trend DTOs.
- `importexport`: SF1 and import logic.
- `notification`: notification DTOs, repository, controller, service.
- `teacherroster`: teacher-owned roster view support.

V2 is closer to production dashboard support than V3 Mobile, but still check actual deployment branch and current DB before saying it is fully deployed.

Recent V2 modifications include teacher email verification, analytics/reporting support, SF1 import improvements, class assignment update/reactivation support, notification work, and OMR/PDF service updates. These were pushed to GitHub branch `agent/v2-sf1-import`.

## Backend V3 Structure

`src/main/java/com/capstone/assessment/v3` contains:

- `auth`: V3 login, logout, `/me`, email verification, MFA enrollment/login challenge, recovery codes, token service, audit service, security filter, and auth exception handling.
- `account`: teacher account approval and account detail/summary responses.
- `school`: principal school setup, academic calendar, calendar automation, teacher roster.
- `schedule`: class schedule/timetable support.
- `assessment`: V3 assessment creation with richer question types, accepted answers, rubrics, term-period-aware tests.
- `answersheet`: answer-sheet eligibility, generation, PDF rendering, repository, storage, and dynamic staging code.
- `mobile`: Mobile V3 download, manifest, scan upload, detection upload, verification, attachment, finalization, readback, reopen, correction, supersede, and DTO contracts.
- `evidence`: answer attachment lineage validation.
- `scoring`: backend-authoritative scoring.
- `report`: report/reference and assessment results read APIs for the web dashboard.
- `notification`: V3 notifications.
- `importexport`: V3 SF1/import support.
- `system`: database readiness and mobile release readiness checks.

V3 has a large backend implementation, but normal `v3` profile keeps many mobile write/readback feature switches false. Do not declare Mobile "fully connected" simply because login/download or isolated tests pass.

## Backend V3 Mobile Contract Packs

Contract packs are stored under `docs/contracts/mobile-v3`.

Observed versions:

- `1.0.0`: early end-to-end/download/session contract material.
- `1.1.0` to `1.11.0`: incremental Mobile V3 contracts through upload, detection, verification, finalization, written evidence/scoring, correction/reopen, supersession, readback, and release gates.

Latest inspected pack:

- `1.11.0`: supersession and rescan. It explicitly says existing eligibility stays one-page, ten-MC, A4 with the immutable approved sheet. This conflicts with any claim that dynamic mixed OMR is already finalized for production.

Important: contract pack version, wire version, SQLite schema version, QR payload version, and DB migration version are different concepts. Do not merge them into one number.

## Database Versions And Meaning

V1 database:

- Default database in `application.properties`: `performance_assessment_db`.
- Represents the original/local system flow.
- Older root packages under `auth`, `analytics`, `assessmentsetup`, `competency`, `gradingperiod`, `importexport`, `schoolsetup`, and `sync` relate to this older layer.
- V1/V2 data must be preserved. Do not drop, reset, or migrate it without explicit approval.

V2 database:

- Default V2 profile database: `performance_assessment_v2_db`.
- Schema docs: `docs/performance_assessment_v2_schema.sql`.
- Reference seed: `docs/performance_assessment_v2_reference_seed.sql`.
- Migration files include `docs/migrations/V2_003_registration_reference_security.sql`, `V2_004_notifications.sql`, and `V2_005_teacher_email_verification.sql`.
- V2 supports dashboard workflows, teacher account improvements, school setup/class assignment changes, sync, OMR printing/scanning support, analytics, and email verification work.

V3 database:

- Default V3 profile database: `performance_assessment_v3_db`.
- Configured baseline in `application-v3.properties`: V3_014 with 67 tables, 163 foreign keys, 87 checks, 99 unique constraints.
- V3_014 includes academic calendar and class schedules.
- V3_015 to V3_023 were used for synthetic local mobile release staging and bring the expected staging/release totals to 79 tables, 200 FKs, 131 checks, 118 unique constraints.
- V3_023 synthetic staging is not automatically the normal school database.
- V3_024 dynamic A4 template work exists locally, but the dynamic OMR implementation was paused/retracted because it did not yet match the approved Mobile/prototype mixed-layout template.

Do not say "V3 is fully production ready" unless the actual school DB has the reviewed migrations, release gates are enabled deliberately, Mobile physical acceptance passes, and frontend/mobile are verified against that exact backend.

## Mobile App Structure

Mobile path: `D:\ThesisProjects\MobileAssessmentApp`.

Key root files:

- `package.json`: React Native scripts and dependencies.
- `android/app/src/main/java/com/mobileassessmentapp/MainActivity.kt`: Android entry activity.
- `android/app/src/main/java/com/mobileassessmentapp/MainApplication.kt`: registers native packages.
- `android/app/src/main/java/com/mobileassessmentapp/OmrScannerPackage.kt`, `OmrScannerModule.kt`, `OmrDetector.kt`: older/fixed scanner bridge.
- `android/app/src/main/java/com/mobileassessmentapp/DynamicOmrDetector.kt`: dynamic scanner-side detector logic.
- `android/app/src/main/java/com/mobileassessmentapp/V3SecureSessionModule.kt`, `V3SecureSessionPackage.kt`: Android Keystore-backed V3 secure session bridge.

Mobile `src` folders:

- `components`: active mobile screens/components such as login, checking grid, OMR scanner modal, analytics view, navigation lists, V3 objective workflow modal.
- `config`: API configuration.
- `database`: legacy and V2/V3 SQLite layers.
- `database/v2`: V2 SQLite schema, contracts, repository, sync, result, download, UUID logic.
- `database/v3`: isolated V3 SQLite schema, parser, contracts, manifest repository, download repository, integrity checks, diagnostics repository.
- `native`: TypeScript wrappers for native OMR scanner and V3 secure session module.
- `prototypes`: isolated V3 prototypes, including dynamic scanner, offline diagnostics, written response review.
- `services`: legacy and V2 service clients.
- `services/v3`: V3 auth client, diagnostics connection, secure session service, offline data, objective client/sync, mobile read client.
- `types`: local TypeScript declarations.

Known Mobile state:

- V1/V2 remain the active production-style app paths.
- V3 remains isolated/prototype/diagnostics unless explicitly wired into production.
- Mobile has isolated V3 login/MFA, `/me`, logout, bearer requests, reference-data/download/manifest parsing, parallel SQLite persistence, and secure session work.
- Mobile APK/release build and automated tests passed in a prior handoff, but physical device testing was interrupted/pending at points.
- Scanner acceptance must be separated from API acceptance. Dynamic QR/physical scanner reliability still needs actual phone/paper validation.

## Frontend Structure

Frontend path: `D:\CAPSTONE_2\web-dashboard`.

Key files:

- `src/main.jsx`: React entry point.
- `src/App.jsx`: app routes and primary composition.
- `src/api/apiClient.js`: older/general API client.
- `src/api/apiV2Client.js`: V2 API calls.
- `src/api/apiV3Client.js`: V3 API calls.
- `src/components/AppLayout.jsx`: common application layout.
- `src/components/ProtectedRoute.jsx`: auth route protection.
- `src/components/NotificationCenter.jsx`: notifications.
- `src/components/MfaSecurityPanel.jsx`: MFA/security settings.
- `src/components/V2Sf1ImportPanel.jsx`, `V3Sf1ImportPanel.jsx`: SF1 import panels by version.
- `src/pages`: route pages for login, principal/teacher dashboards, class records, assessment setup, analytics, reports, exports, settings, teacher approval, signup, email verification.
- `src/v2`: V2-specific shell/routes/pages, teacher flow, OMR print page, assessment editor/list, login.
- `src/styles`: global CSS, reports CSS, auth/security/notification/system/date-time styles, V2 demo and SF1 modal styles.
- `src/components/ui`: small reusable UI primitives such as button, badge, input, select, popover, label, card, table.

Known Frontend state:

- Existing reports route is mostly source-wired to V3 report APIs.
- Reports display backend-authoritative values; frontend should not calculate official scores.
- PDF/Excel exports, evidence/image preview, direct result detail links, and advanced Student 360/intervention reports need explicit backend contracts before UI support.
- No redesign should be done for F1; preserve existing UI design and use current panels/routes.

## High-Level System Flow

Current intended end-to-end flow:

1. Teacher/principal authenticate.
2. Teacher downloads reference data, assigned assessments, students, and manifests.
3. Teacher scans offline on Mobile.
4. Teacher verifies detections and written-answer evidence where required.
5. Mobile uploads scan pages, original evidence, detections, scores/comments/verification decisions.
6. Backend persists idempotently and computes official scores.
7. Teacher finalizes or reopens/corrects/supersedes with audit trail.
8. Mobile and Frontend read backend-authoritative official results and analytics.

Do not mark the flow fully complete until actual Mobile device acceptance and backend readiness are verified against the same running backend and same database.

## Adviser/Professor Requirements To Preserve

The following business rules came from user/adviser/professor discussions and should guide future work:

- Duplicate SF1/import files should not blindly duplicate students. If the file has some existing learners and some new learners, only add the new ones and return warnings for existing records.
- A student can be enrolled in only one class/section at a time, but can appear in multiple class assignments for different teacher/subject contexts.
- Bubble-sheet download/print should require at least 5 assessment items.
- Assessments need schedule/time windows, similar to closing an assessment while still allowing controlled edits.
- Sync must be clear for teachers with multiple class assignments using the same assessment.
- Question types should support multiple choice, true/false, identification, enumeration, and essay, but scanner capability differs by type.
- Identification/enumeration/essay require teacher verification/manual handling; do not claim scanner-only scoring for written responses.
- Ambiguous multiple marks should not be casually edited without audit rules.
- SMS OTP was requested as a future integration idea.
- Principal reports should filter by term period, class, assessment, and school-year context.
- Tests are separated by term periods; class assignments are not necessarily divided by term.
- Student 360 should eventually show status, interventions, test scores, performance status, and evidence useful to defend grading decisions.
- Time handling should be consistent and reliable; UTC/server-authoritative time strategy is preferred over device guesses.

## Dynamic OMR Warning

This is the biggest trap for a new AI.

The approved dynamic mixed-template references are in `D:\ThesisProjects\OMRPrototype\output\pdf` and are mirrored in Mobile assets. They include A4, US Letter, and US Legal PDFs and manifest JSON files. The actual template identifiers include:

- `OMR-A4-DYNAMIC-CTX-V3`
- `OMR-US-LETTER-DYNAMIC-CTX-V3`
- `OMR-US-LEGAL-DYNAMIC-CTX-V3`

Known approved manifest properties from prior read-only comparison:

- contract version `3.0`
- manifest version `2`
- QR payload version `3`
- template version `3`
- required scanner version `3.0.0-prototype.2`
- mixed question layout: MC, TF, identification, enumeration, essay
- native paper geometry per paper size
- QR payload field `tv` is numeric `3`, not string `"3"`

A prior backend dynamic implementation assumed an incompatible A4-only MC layout and a 16-items-per-page rule. That work must not be presented as Mobile-aligned. Before implementing dynamic OMR, inspect:

- the OMRPrototype generator
- the Mobile bundled manifests
- the Mobile parser/detector
- backend answer-sheet generator and upload validation

Then produce a correction plan before editing.

## What Is Done

Backend:

- V2 support has been expanded and pushed to GitHub branch `agent/v2-sf1-import`.
- V2/V3 code compiles with `mvn -q -DskipTests compile`.
- V3 auth, MFA, teacher approval, school setup, academic calendar/timetable, assessment creation, answer-sheet generation, mobile upload contracts, detection, verification, written scoring, finalization, correction/reopen/supersede, readback/report APIs, and readiness services exist in source.
- V3 Mobile contract packs up to `1.11.0` exist in docs.
- Synthetic local staging has been used for V3 mobile release-style validation.

Mobile:

- V3 secure session storage work exists with Android Keystore.
- V3 diagnostics flow has login/download/session restoration work.
- V3 SQLite layer is isolated.
- Build/tests reportedly passed in previous mobile handoff.

Frontend:

- V3 Reports page integration is mostly source-wired for backend-authoritative report display.
- Existing UI design should be preserved.
- Runtime browser verification had limits in prior handoff, but source/build checks passed in the frontend report.

## What Is Not Done

Do not claim these are complete:

- Full production Mobile V3 cutover.
- Physical scanner acceptance for dynamic QR and mixed paper layouts.
- Dynamic backend OMR generator aligned to approved A4/Letter/Legal Mobile/prototype assets.
- School production database migration through V3_023 or any later release migration.
- Mobile writes enabled in normal production profile.
- Evidence image browser preview contract.
- PDF/Excel report export contracts.
- Advanced item analysis, competency mastery, Student 360, interventions, and whole-school longitudinal analytics.
- SMS OTP integration.
- Full frontend result-detail navigation by `resultUuid`.

## Git And Deployment Rules

Never run:

- `git reset --hard`
- `git clean`
- `git checkout -- <file>` to discard unknown work
- `git add .` in this repo without careful review

Do not commit:

- `.codex*.log`
- `debug.log`
- `output/`
- `tmp/`
- `patch_fix/`
- `docs/backups/`
- database dumps with session/auth hashes
- local generated evidence

Safe GitHub update pattern:

1. Run `git status --short`.
2. Run `mvn -q -DskipTests compile`.
3. Stage only intended source/docs/migrations.
4. Check `git diff --cached --stat`.
5. Commit.
6. Push the intended branch.
7. Merge to `main` only if the deployment source is `main` and the user confirms.

## Recommended Next Route

1. Stabilize GitHub/deploy branch policy.
2. Audit current backend against Mobile approved dynamic OMR assets before any OMR edits.
3. Correct dynamic OMR contract only after that audit.
4. Run backend compile and focused tests.
5. Coordinate Mobile physical device testing on `8080`/`8081`.
6. Verify Frontend reports against backend-authoritative values.
7. Prepare presentation-safe demo path:
   Login -> Download -> Scan/Verify -> Upload -> Finalize -> same official score on Mobile and Web.

## Instructions For The Next AI

Start every new session with:

1. Ask whether the user wants scan-only or implementation.
2. Inspect current `git status --short`.
3. Identify active branch and remote.
4. Read current source, not only docs/screenshots.
5. Preserve dirty work and generated artifacts.
6. Keep backend `8080`, Mobile Metro `8081`.
7. Do not use `18082` unless explicitly requested.
8. Do not modify V1/V2 while working on V3 unless the task clearly says so.
9. Do not declare Mobile fully connected from login/download alone.
10. Do not invent OMR template geometry.

If the user is near presentation, prefer narrow, demo-protecting work over broad refactors.

