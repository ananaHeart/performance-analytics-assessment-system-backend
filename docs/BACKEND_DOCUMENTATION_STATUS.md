# Backend Documentation Status

Last reviewed: July 31, 2026, Asia/Manila

Date basis: Dates in this document are approximate project timeline dates based on the development schedule and implementation history. They are written by month or week where exact creation dates were not recorded.

## Purpose

This file summarizes where the backend is already documented, what each document covers, and what still needs to be completed before panel review or final submission.

## Completed Backend Documentation

### `docs/BACKEND_API_DOCUMENTATION.md`

Status: Updated backend reference document.

Approximate documentation period:
- Initial backend API documentation: late May 2026
- Sync, analytics, assessment setup, import/export documentation: late May 2026
- Rule-based LMS, grading period, skill mapping, and recommendation updates: late June 2026
- TiDB Cloud, Render Docker deployment, student mastery, and final documentation status updates: July 2026
- July 12, 2026 deployment pivot: Render deployment was changed from the expected Java runtime approach to Docker deployment because the available Render runtime options did not include Java for the account
- CORS, TiDB SQL compatibility, SF1-created sections, and available section filtering documented on July 13, 2026
- Database redesign and polishing notes documented on July 27, 2026, covering curriculum, intervention, answer key normalization, term period naming, student enrollment relationships, and item result analytics meaning
- Cloud mobile sync validation and temporary TiDB schema compatibility notes documented on July 31, 2026

Coverage:
- Backend overview and responsibilities
- Technology stack
- Main database tables
- Authentication and teacher approval APIs
- School setup APIs
- SF1-based section creation and available section filtering
- SF1 import APIs
- Assessment setup APIs
- Grading period support
- Rule-based skill mapping support
- Mobile sync download and upload APIs
- Analytics APIs
- Teacher intervention recommendation endpoint
- Student skill mastery endpoint
- Excel export APIs
- Local and cloud deployment configuration
- Confirmed working features and remaining improvements
- July 31 cloud mobile sync download/upload validation against Render and TiDB
- Temporary database compatibility notes while the final schema recreation is still under review

Use this document as the main backend explanation for the panel.

### `docs/backend-rule-based-lms-schema-proposal.md`

Status: Historical design proposal with implementation status notes.

Approximate documentation period:
- Planning and proposal draft: late June 2026
- Updated to match implemented `parent_competency_id`, `part_skill_mapping`, and historical `skill_item` design: July 2026
- Updated on July 13, 2026 with TiDB-compatible LMS SQL implementation status
- Updated on July 27, 2026 with database polishing notes for curriculum, intervention, term period, answer key, student enrollment, and item-result analytics meaning
- Updated on July 29, 2026 with the final range-only part-skill mapping cleanup

Coverage:
- Why deeper LMS needed branch skill mapping
- How root and branch competencies are represented
- `parent_competency_id` design in `competency_tags`
- `part_skill_mapping` range-only design
- Historical `skill_item` / `CUSTOM` design context
- LMS computation direction
- Backward compatibility with old tests
- Mobile sync decision: mapping tables remain backend/web-side unless mobile needs offline deeper analytics

Use this document when explaining the rule-based LMS design decision.

### `src/main/resources/API_TESTING_NOTES.md`

Status: API testing record.

Approximate documentation period:
- Core API testing notes: late May 2026
- Mobile sync, LMS, affected students, and deeper analytics notes: late June 2026
- Student skill mastery, teacher intervention, student score export, and deployment notes: July 2026
- CORS/TiDB deployment fixes and SF1-based class assignment filtering notes: July 13, 2026
- Database redesign/polishing documentation notes: July 27, 2026
- Cloud mobile sync and TiDB compatibility testing notes: July 31, 2026

Coverage:
- Sync download and upload testing
- Analytics endpoint testing
- Assessment setup endpoint testing
- Export endpoint testing
- Import/student setup testing
- School setup available section filtering testing notes
- Authentication and teacher approval testing
- Temporary RBAC testing notes

Use this as a testing evidence document.

### `src/main/resources/DEVELOPMENT_GANTT_PLAN.md`

Status: Development plan document.

Approximate documentation period:
- Initial planning document: May 2026
- Still used as the phase-level project plan through July 2026
- Updated on July 13, 2026 to include SF1-based section handling, deployment debugging, and final documentation timeline notes

Coverage:
- Project phases
- Objectives per phase
- Main tasks per phase
- Expected outputs
- Integration and deployment preparation phase

Use this as the development-process explanation.

### `src/main/resources/DEVELOPMENT_GANTT_SCHEDULE.md`

Status: Schedule document.

Approximate documentation period:
- Initial schedule: May 2026
- Updated progress status: July 2026
- Updated on July 13, 2026 with a dated backend update timeline for Gantt chart presentation

Coverage:
- Phase schedule
- Date ranges
- Duration
- Progress status
- Dated backend function timeline from late May 2026 to July 13, 2026

Use this as the timeline reference.

### `HELP.md`

Status: Generated Spring Boot helper reference.

Approximate documentation period:
- Generated during initial Spring Boot backend setup around May 2026

Coverage:
- Spring Boot and Maven reference links
- Generic project startup references

This is not a panel-facing system document. It can stay as a developer reference only.

## Backend Areas Now Documented

- Local MySQL/XAMPP development configuration
- TiDB Cloud profile configuration
- Docker deployment support for Render
- July 12, 2026 Render Docker deployment pivot from Java runtime expectation to Docker runtime preparation
- Mobile sync download/upload workflow
- Cloud mobile sync download/upload validation using the deployed Render backend and TiDB Cloud database
- Restore of uploaded mobile results
- Rule-based LMS computation
- Branch skill mapping through range-only `part_skill_mapping`
- Teacher-facing intervention recommendation endpoint
- Student profile skill mastery endpoint
- Student score Excel export endpoint
- Item analysis and LMS Excel exports
- CORS configuration for deployed frontend
- TiDB-compatible SQL fixes for deployed analytics
- SF1-created sections linked to grade level and academic year
- Available section filtering for class assignment
- July 27, 2026 database polishing decisions and discussion notes:
  - `curriculum` as a version/base reference for competencies
  - `intervention` as a possible master/reference entity
  - `answer_key` normalization reviewed as a future option
  - `test_result` and `test_item_result` meanings clarified
  - `student_enrollment` confirmed as section-based, not class-based
  - `term_period` reviewed as the renamed grading period concept
- July 31, 2026 deployed mobile sync validation:
  - React Native mobile app downloaded data from Render and TiDB
  - Mobile upload stored 5 result rows and 50 item response rows
  - Temporary TiDB `test_result` compatibility fields were aligned with the active backend sync contract
  - Final local and TiDB database recreation/migration remained pending at that time

## Still Missing or Needs Final Polish

### Security documentation

Current backend uses basic/local token behavior for development and testing. A final security section should document the intended production authentication approach if JWT or stricter Spring Security rules are added.

### Final deployment runbook

Render Docker deployment is configured, but a final runbook should still be created after successful deployment. It should include:
- Render service type
- Docker deployment steps
- Required environment variables
- TiDB Cloud connection settings
- Health check or smoke-test endpoint
- Common deployment errors and fixes

### Final API testing screenshots or evidence

The API testing notes list working endpoints, but final defense materials should include screenshots or exported evidence from:
- Postman
- Render deployed URL
- TiDB Cloud database
- Web dashboard calls
- Mobile sync/upload flow
- SF1 import to available section dropdown flow
- Teacher class assignment using an available imported section

### Final database ERD or schema diagram

The database tables are documented in text, but the panel may still expect an ERD. A final diagram should include:
- `user`
- `curriculum`
- `class`
- `student`
- `student_enrollment`
- `test`
- `test_part`
- `test_result`
- `test_item_result`
- `competency_tags`
- `part_skill_mapping`
- `skill_item` only as historical/custom-mapping context; removed from active range-only design on July 29, 2026
- `term_period` or current implemented `grading_period`, depending on final naming
- `intervention` if the master/reference table is approved
- `answer_key` if normalized answer key storage is approved
- `sync_log`

### Final migration decision for July 27 database polishing

The July 27 notes and July 31 cloud compatibility adjustments are documented. On August 8, 2026, a separate V2 local schema was created and validated without replacing the working database. Backend/client compatibility work and TiDB migration are still pending.

### July 31, 2026 cloud mobile sync validation

The deployed mobile sync flow has been validated against Render and TiDB:
- `GET /api/sync/download/{teacherId}` succeeded from the mobile app.
- `POST /api/sync/upload` succeeded after checking students on the phone.
- Mobile reported 5 uploaded result records and 50 uploaded item response records.
- A temporary TiDB schema mismatch was documented and aligned for the working build.

This confirms the current deployed system works for mobile cloud sync. A separate local V2 schema was later created on August 8, 2026, but the deployed backend and TiDB still use the working V1 contract until coordinated migration testing is complete.

### July 29, 2026 range-only mapping update

The part-skill mapping cleanup has been approved and documented. The active backend path no longer depends on `skill_item`, `CUSTOM`, or `mapping_mode`. Mapping is now explained as range-only using `item_count`, `start_item`, and `end_item`.

Verified local API checks:
- `POST /api/part-skill-mappings/preview`
- `POST /api/part-skill-mappings/save`
- `GET /api/part-skill-mappings/test-parts/1`
- `GET /api/analytics/lms?testId=1`

### Final limitations section

The backend docs should clearly state what is intentionally not included:
- No online exam feature
- No student portal
- No AI-generated lesson plans
- Current V1 has no OMR scanner implementation; V2 has a validated fixed-template OMR data model, while mobile scanner and backend integration remain pending
- Mobile does not compute deeper LMS offline
- Analytics are computed from uploaded checked results

## Short Answer for Panel

The backend is documented through the main API documentation, rule-based LMS schema proposal, API testing notes, development Gantt documents, and the August 8-9 V2 database validation report. Documentation now includes the executable V2 schema, reference seed, rollback-based integration evidence, V9 recapture-retention addendum, V2 API/sync freeze candidate, V1-to-V2 migration and rollback runbook, and backend impact audit. Remaining work includes adviser approval of the complete ERD, backend/mobile/frontend implementation, TiDB staging validation, screenshots, and final acceptance evidence.

## August 8, 2026 V2 Database Status

### August 9, 2026 validation and migration rehearsal

- Created a dated SQL backup of `performance_assessment_v2_db` and proved that it restores into a disposable verification database.
- Reverified the V2 target as 38 tables and 56 foreign keys after the local-only recapture-lineage migration, with reference data present and operational tables empty.
- Added the V2 API/mobile sync freeze candidate in `docs/V2_API_SYNC_CONTRACT.md`.
- Added read-only preflight, rollback-only migration rehearsal, and rollback guidance under `docs/migrations/` and `docs/V1_TO_V2_MIGRATION_PLAN.md`.
- Added `docs/V2_BACKEND_IMPACT_AUDIT.md` to identify every backend module affected by the normalized V2 schema.
- The current Spring Boot runtime, working local V1 database, and deployed TiDB database remain unchanged.

- Created separate local database `performance_assessment_v2_db`; the working `performance_assessment_db` remains intact.
- Initial August 8 creation verified 37 V2 tables and 54 foreign-key constraints.
- August 9 added `test_result_scans`, removed direct `test_results.scan_session_id`, and verified the current local V2 state as 38 tables and 56 foreign keys.
- Created `docs/Data Dictionary CAP2_v9.docx` as the authoritative recapture-retention correction while preserving V8.
- Backed up local V2 before applying `docs/migrations/V2_002_test_result_scans.sql`.
- Re-ran the rollback-based workflow test with retained scans, exactly one selected scan, duplicate-selected rejection, intervention, sync, authentication, and audit assertions.
- Applied idempotent reference data for genders, majors, educational attainments, roles, statuses, curriculum, grade levels, and subjects.
- Passed a rollback-based end-to-end relational test covering OMR capture, teacher verification, results, per-question answers, intervention, batch sync, authentication, login monitoring, and audit logging.
- Confirmed zero remaining operational smoke-test rows after rollback.
- Current Spring Boot runtime, mobile SQLite, React frontend, and TiDB Cloud have not yet migrated to V2.
- Validation evidence: `docs/V2_DATABASE_VALIDATION.md`.

## August 10, 2026 V2 Backend Runtime Checkpoint

- Added an isolated `v2` Spring profile and separate V2 datasource configuration without changing the default V1 datasource or deployed TiDB configuration.
- Implemented V2 login, current-user, and logout endpoints backed by `users`, `roles`, `statuses`, `auth_sessions`, `login_attempts`, and `audit_logs`.
- Added opaque hashed server-side sessions, BCrypt password verification, account-status and email-verification checks, rate limiting, lockout persistence, logout revocation, and audit events.
- Added deny-by-default V2 HTTP security. Endpoint-specific principal/teacher authorization and ownership rules remain pending because no protected business modules have been migrated yet.
- Verified compilation and six focused authentication service tests. The existing V1 application-context test still requires a reachable local MySQL service.
- No V2 database migration, TiDB change, frontend change, mobile SQLite change, or production sync change was performed in this checkpoint.

## August 10, 2026 V2 School Setup and Class Assignment Checkpoint

- Implemented principal-only V2 reference-data, available-class, class-assignment listing, and class-assignment creation APIs under the explicit `v2` Spring profile.
- Enforced principal role and school ownership in Spring Security and again in the service layer.
- Required active same-school classes and teachers, a valid subject, and at least one enrolled same-school learner before an assignment can be created.
- Rejected exact active duplicates and prevented more than one active primary teacher for the same class and subject while allowing a co-teacher assignment.
- Recorded successful class assignments in `audit_logs`.
- Added seven focused service tests covering authorization, cross-school access, enrollment, duplicate protection, primary-teacher uniqueness, and co-teacher behavior.
- The complete Maven test suite passed 19 tests with 0 failures and 0 errors while local MySQL was running.
- V2 assessment creation, OMR verification, synchronization, analytics, intervention, exports, client migration, and TiDB staging migration remain pending.
- The default V1 runtime, deployed TiDB database, React frontend, and mobile SQLite database were not changed by this checkpoint.
