# V3 Backend Migration Status

Status date: 2026-09-05

## Implemented Now

- Isolated Spring profile: `v3`
- Default local V3 port: `8082`
- Environment-configurable V3 datasource
- SQL/JPA automatic schema writes disabled
- Startup validation against the approved central V3 baseline
- Read-only `GET /api/v3/system/readiness`
- V3 security boundary that denies every V1/V2 endpoint under the V3 profile
- Exact hardened-baseline checks for 67 tables, 163 foreign keys, 87 checks, 99
  unique constraints, required columns/constraints, seeds, and active templates
- Cross-row answer-attachment lineage validator and focused tests
- Focused readiness/repository tests and full-suite regression validation
- Isolated V3 authentication and teacher-registration runtime under
  `/api/v3/auth/*`
- Public teacher registration with authoritative validation, BCrypt password
  hashing, email OTP verification, resend controls, and audit events
- `pending_email_verification -> pending_approval -> active` account-state gate
- 30-day cleanup of abandoned unverified registrations without retaining the
  original email or contact number
- Bearer-token login, current-user lookup, logout, failed-attempt lockout, and
  session revocation
- Fail-closed protection for accounts already marked `mfa_required`; optional
  authenticator enrollment/challenge remains pending
- Teacher-only read access to V3 Mobile reference data, owned full snapshots,
  and immutable answer-sheet manifests
- Token-derived teacher/school ownership checks repeated in the Mobile service
  and SQL repository boundary
- Typed Mobile download/manifest DTOs and shared JSON contract fixtures
- Strict scan-page upload metadata and retry-response DTO contract; runtime POST
  remains deliberately unavailable until evidence persistence is implemented
- Teacher-only V3 assessment reference data, complete draft creation, listing,
  retrieval, replacement-style draft editing, activation, and soft archive
- Dynamic assessment parts for `multiple_choice`, `true_false`,
  `identification`, `enumeration`, and `essay`
- Transactional persistence of options, answer keys, accepted answers, inline or
  existing rubrics, rubric criteria, part-skill ranges, schedule snapshots, and
  audit events
- Server-side teacher, school, class-assignment, academic-year/term, curriculum,
  skill, and rubric ownership validation
- Principal-only V3 teacher-account queue, profile detail, approval, and
  rejection endpoints under `/api/v3/users/teachers`
- Same-school scoping derived from the authenticated principal, verified-email
  queue filtering, transactional status decisions, and approval/rejection audit
  events
- Principal-owned V3 school profile/reference data, school-scoped classes,
  teacher assignments, assignment archive/reactivation, and class rosters
- Teacher roster access derived from an active owned class assignment instead
  of trusting a payload teacher or school identifier
- Principal-only manual student enrollment with reference-backed gender/suffix
  fields, 12-digit LRN validation, same-school ownership, and transactional
  audit records
- Idempotent same-class enrollment and one-active-class-per-student-per-year
  enforcement, including controlled reactivation of retained memberships
- Explicit student profile correction with immutable LRN and mandatory audit
  reason, plus soft enrollment lifecycle actions for enrolled, transferred,
  dropped, and completed states
- Duplicate-safe V3 SF1 preview and transactional confirmation with SHA-256
  file identity, UUID replay safety, row-level outcomes, and audit records
- One-active-class-per-student-per-academic-year enforcement during SF1 import,
  while allowing the same class roster to serve multiple subject assignments
- Explicit school ownership on `sections`, enforced by `V3_013` and included in
  the V3 startup-readiness checks
- Principal-managed, school-scoped academic years with exactly four ordered
  quarter records, explicit lifecycle actions, and one-active-year/term rules
- Teacher-owned class timetable creation, editing, listing, and soft archive,
  with assignment ownership, academic-year bounds, and overlap validation
- Schedule-aware assessment delivery: test windows must stay inside the term;
  an out-of-timetable close requires an explicit confirmation, reason, actor,
  and timestamp retained in `test_assignments`
- Optional academic-calendar automation implemented behind
  `V3_CALENDAR_AUTOMATION_ENABLED`; it remains disabled by default until the
  historical demo calendar dates are corrected
- Authenticated principal/teacher in-app notification list, unread count,
  owned mark-read, and mark-all-read endpoints under `/api/v3/notifications`
- Transactional notification events for verified teacher approval requests,
  principal approval/rejection decisions, and class-assignment
  create/archive/reactivate actions
- Recipient-scoped event idempotency through the existing
  `UNIQUE(recipient_user_id, event_key)` database constraint

Run locally:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=v3"
```

Expected readiness URL:

```text
http://localhost:8082/api/v3/system/readiness
```

## Verified Local Hardened Result

- Disposable validation database:
  `performance_assessment_v3_validation_20260830_182425`
- Applied local database: `performance_assessment_v3_db`
- Readiness: `true`
- Readiness checks: 19 of 19 passed through live HTTP
- Tables: 67
- Foreign keys: 163
- Check constraints: 87
- Unique constraints: 99
- Active question types: 5
- Active OMR templates: 1
- Active performance rule sets: 4
- Negative SQL/service-contract fixtures: 11 of 11 rejected as expected
- Persisted negative-fixture rows: 0
- Last fully completed baseline before this slice: 153 tests, 0 failures, 0
  errors, 0 skipped
- V3 principal teacher-account service tests: 10 passed, 0 failures/errors/skips
- Focused V3 school setup, SF1, and readiness tests: 30 passed, 0
  failures/errors/skips
- Current full Maven regression suite: 228 passed, 0 failures, 0 errors, 0
  skipped
- Focused notification and source-lifecycle service suite: 44 passed, 0
  failures, 0 errors, 0 skipped
- Live V3 notification runtime: unauthenticated list HTTP 401; principal login,
  notification list, unread count, and logout HTTP 200
- Live notification list returned 5 records with an unread count of 5; a
  non-owned/missing notification mark-read request returned HTTP 404 with
  `NOTIFICATION_NOT_FOUND`
- Notification CORS preflight from `http://localhost:5173`: HTTP 200 with the
  expected `Access-Control-Allow-Origin`
- Focused V3 assessment service tests: 14 passed
- Rollback-only repository probe against the real local V3 schema successfully
  exercised create, read, update, activate, and archive for all five question
  types without retaining probe data
- Live V3 registration reference data: HTTP 200
- Live V3 unauthenticated `/api/v3/auth/me`: HTTP 401 with the standard envelope
- Live authenticated teacher Mobile reference data: HTTP 200
- Live authenticated teacher Mobile full snapshot: HTTP 200 with one owned class
  assignment and two owned test assignments
- Live authenticated `POST /api/v3/mobile/scan-pages`: HTTP 403 because the
  persistence endpoint is intentionally not enabled
- Live authenticated V3 assessment acceptance: create HTTP 201; list, detail,
  draft update, activate, and archive HTTP 200; active-content update HTTP 409
- The live acceptance assessment (`testId=1012`) persisted all five question
  types, 5 parts, 5 items, and an 11-point maximum score, then ended archived
- Draft updates now distinguish a valid no-op MySQL update from a locked
  lifecycle row when header or schedule values are unchanged
- Smoke-test teacher session was logged out successfully
- CORS preflight: HTTP 200 for `http://localhost:5173` and
  `http://localhost:5174`
- Live V3 readiness after `V3_013`: HTTP 200 with all 17 checks passing at the
  exact 66/158/80/96 baseline
- Live V3 readiness after `V3_014`: HTTP 200 with all 19 checks passing at the
  exact 67/163/87/99 baseline
- Live unauthenticated calendar and teacher-timetable requests: HTTP 401
- Live principal login, school-scoped academic-year retrieval, and logout:
  HTTP 200; calendar retrieval returned one school-owned academic year
- Calendar CORS preflight from `http://localhost:5173`: HTTP 200 with the
  expected `Access-Control-Allow-Origin`
- The existing 2025-2026 year and its four term statuses remained unchanged
  while the V3 server ran, confirming that automatic rollover is opt-in

The approved `V3_006` through `V3_009` chain, `V3_013` school-scoped-section
migration, and `V3_014` academic-calendar/timetable migration are now applied
to the local central
`performance_assessment_v3_db`. A live V3 startup against that database passed
all 19 readiness checks at the exact 67/163/87/99 baseline. The migrations
were validated in disposable databases before controlled local application;
their pre-migration backups remain available under `docs/backups`.

## Deliberately Not Migrated Yet

- Authenticator MFA enrollment, recovery, and login challenge
- Mobile scan verification and idempotent upload persistence
- Scoring, analytics, Student 360, intervention, and exports
- React and Mobile SQLite endpoint switching
- TiDB staging or production deployment

## Validated And Applied Locally

- `V3_006_dynamic_answer_sheet_schema_DRAFT.sql`
- `V3_007_validated_a4_template_regions_DRAFT.sql`
- `V3_008_dynamic_answer_sheet_smoke_test_DRAFT.sql`
- `V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql`
- `V3_009_dynamic_answer_sheet_contract_hardening_smoke_test_DRAFT.sql`
- `V3_009_dynamic_answer_sheet_negative_fixtures_DRAFT.sql`
- `V3_013_school_scoped_sections.sql`
- `V3_014_academic_calendar_and_class_schedules.sql`
- `V3_DYNAMIC_ANSWER_SHEET_SCHEMA_DELTA_HANDOFF.md`
- `V3_DYNAMIC_ANSWER_SHEET_DISPOSABLE_VALIDATION_2026-08-30.md`
- `V3_DYNAMIC_ANSWER_SHEET_HARDENING_VALIDATION_2026-08-30.md`
- `V3_DYNAMIC_ANSWER_SHEET_BACKEND_CONTRACT_DECISIONS.md`
- `V3_013_SCHOOL_SCOPE_MIGRATION_VALIDATION.md`
- `V3_ACADEMIC_CALENDAR_AND_TIMETABLE_API.md`
- `V3_SCHOOL_SETUP_SF1_API.md`
- `postman/V3_School_Setup_SF1.postman_collection.json`

These files define and harden the proposed paper-size, template-region,
generated-page, question-region, scan-page, written-response count, evidence
lineage/retention, and QR-integrity structures. The complete chain passed in the
fresh disposable database
`performance_assessment_v3_validation_20260830_182425`: 66 tables, 157 foreign
keys, 80 checks, and 96 unique constraints. Eleven invalid database/service
contract cases were rejected and no fixture rows persisted. After explicit
owner/Mobile approval, the same schema chain was applied to the real local V3
database and passed the same structural and Spring readiness gates. The negative
fixture script remains disposable-only and was not executed against the real
database.

Full execution evidence, backup identity, rollback history, and final counts are
recorded in `V3_LOCAL_DYNAMIC_MIGRATION_EXECUTION_2026-08-30.md`.

The implemented authentication and registration endpoint contract, lifecycle,
error codes, and frontend handoff are recorded in
`V3_AUTH_ACCOUNT_LIFECYCLE_API.md`.

The implemented principal teacher-account review and decision contract is
recorded in `V3_PRINCIPAL_TEACHER_APPROVAL_API.md`.

The implemented manual student enrollment, profile correction, roster response,
and soft enrollment lifecycle contract is recorded in
`V3_MANUAL_STUDENT_ROSTER_API.md`.

The implemented V3 in-app notification endpoints, event types, recipient
ownership, idempotency, and frontend handoff are recorded in
`V3_NOTIFICATIONS_API.md`.

The implemented assessment-authoring contract, all-five-type sample payload,
validation rules, and Postman workflow are recorded in
`V3_ASSESSMENT_CREATION_API.md` and
`postman/V3_Assessment_Creation.postman_collection.json`.

## Next Backend Slice

Keep the verified 67-table local baseline controlled. V3 authentication,
registration, principal teacher approval, in-app notifications, school setup,
class/roster, manual
student enrollment and roster lifecycle, SF1,
Mobile read contracts, assessment authoring, academic-calendar management, and
teacher timetables are now isolated backend slices. The immediate gate is a
focused Postman/browser acceptance run for academic years, four terms,
timetable conflict handling, and in/out-of-schedule assessment windows. After
approval, perform one controlled React cutover for these matching V3 workflows
so the frontend does not mix V2 and V3 APIs.

The next Mobile-specific backend slice remains transactional scan-page evidence
persistence and idempotent upload, followed by protected verification and
scoring. Dynamic generator/PDF fixtures and A4, US Letter, and US Legal physical
validation remain a separate controlled slice. Do not switch production Mobile
SQLite, V2, or TiDB yet.

Do not enable `V3_CALENDAR_AUTOMATION_ENABLED` against the current historical
2025-2026 demo dates. Correct and approve the academic-year and four term dates
first, then exercise automatic rollover in a disposable or dedicated test year.
