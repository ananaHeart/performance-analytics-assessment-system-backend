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
  - Final local and TiDB database recreation/migration remains pending until adviser approval

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

The July 27 notes are documented, and the July 31 cloud compatibility adjustments are also documented. However, the final database migration or recreation scripts are still needed only after approval. The backend code and final local/TiDB schema should not be broadly changed until the final table and column names are confirmed.

### July 31, 2026 cloud mobile sync validation

The deployed mobile sync flow has been validated against Render and TiDB:
- `GET /api/sync/download/{teacherId}` succeeded from the mobile app.
- `POST /api/sync/upload` succeeded after checking students on the phone.
- Mobile reported 5 uploaded result records and 50 uploaded item response records.
- A temporary TiDB schema mismatch was documented and aligned for the working build.

This confirms the current deployed system works for mobile cloud sync, but it does not finalize the redesigned database. The planned final direction is still to recreate or migrate both local MySQL and TiDB after adviser approval.

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
- No OCR checking
- Mobile does not compute deeper LMS offline
- Analytics are computed from uploaded checked results

## Short Answer for Panel

The backend is documented through the main API documentation, rule-based LMS schema proposal, API testing notes, and development Gantt documents. The backend documentation already covers the implemented API modules, database structure, synchronization flow, analytics computation, exports, deployment configuration, and July 31 cloud mobile sync validation. Remaining documentation work is mainly final polish: security runbook, final ERD, screenshots, final database migration/recreation decision, and final limitations.
