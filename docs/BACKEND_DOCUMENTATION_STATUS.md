# Backend Documentation Status

Last reviewed: July 12, 2026, 17:51 Asia/Manila

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

Coverage:
- Backend overview and responsibilities
- Technology stack
- Main database tables
- Authentication and teacher approval APIs
- School setup APIs
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

Use this document as the main backend explanation for the panel.

### `docs/backend-rule-based-lms-schema-proposal.md`

Status: Historical design proposal with implementation status notes.

Approximate documentation period:
- Planning and proposal draft: late June 2026
- Updated to match implemented `parent_competency_id`, `part_skill_mapping`, and `skill_item`: July 2026

Coverage:
- Why deeper LMS needed branch skill mapping
- How root and branch competencies are represented
- `parent_competency_id` design in `competency_tags`
- `part_skill_mapping`
- `skill_item`
- Mapping modes: `RANGE` and `CUSTOM`
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

Coverage:
- Sync download and upload testing
- Analytics endpoint testing
- Assessment setup endpoint testing
- Export endpoint testing
- Import/student setup testing
- Authentication and teacher approval testing
- Temporary RBAC testing notes

Use this as a testing evidence document.

### `src/main/resources/DEVELOPMENT_GANTT_PLAN.md`

Status: Development plan document.

Approximate documentation period:
- Initial planning document: May 2026
- Still used as the phase-level project plan through July 2026

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

Coverage:
- Phase schedule
- Date ranges
- Duration
- Progress status

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
- Mobile sync download/upload workflow
- Restore of uploaded mobile results
- Rule-based LMS computation
- Branch skill mapping through `part_skill_mapping` and `skill_item`
- Teacher-facing intervention recommendation endpoint
- Student profile skill mastery endpoint
- Student score Excel export endpoint
- Item analysis and LMS Excel exports

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

### Final database ERD or schema diagram

The database tables are documented in text, but the panel may still expect an ERD. A final diagram should include:
- `user`
- `class`
- `student`
- `student_enrollment`
- `test`
- `test_part`
- `test_result`
- `test_item_result`
- `competency_tags`
- `part_skill_mapping`
- `skill_item`
- `grading_period`
- `sync_log`

### Final limitations section

The backend docs should clearly state what is intentionally not included:
- No online exam feature
- No student portal
- No AI-generated lesson plans
- No OCR checking
- Mobile does not compute deeper LMS offline
- Analytics are computed from uploaded checked results

## Short Answer for Panel

The backend is documented through the main API documentation, rule-based LMS schema proposal, API testing notes, and development Gantt documents. The backend documentation already covers the implemented API modules, database structure, synchronization flow, analytics computation, exports, and deployment configuration. Remaining documentation work is mainly final polish: security runbook, deployment proof, ERD, screenshots, and final limitations.
