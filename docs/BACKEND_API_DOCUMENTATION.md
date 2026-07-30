# Backend API Documentation

Documentation timeline note: This document began as the backend API reference around late May 2026 and was updated through late June and July 2026 as rule-based LMS, student mastery, export, TiDB Cloud, Render Docker deployment support, CORS deployment fixes, TiDB SQL compatibility fixes, SF1-based class assignment filtering, July 27 database polishing notes, and the July 29 range-only part-skill mapping cleanup were added.

## 1. Overview
The backend is the Spring Boot REST API for the Performance Analytic Assessment System. It serves as the central application layer for authentication, school setup, assessment setup, mobile synchronization, analytics processing, and Excel-based reporting. It stores and exposes the data required by the web dashboard and the mobile checking workflow while keeping the main academic and assessment records in the backend database.

## 2. Technology Stack
- Java
- Spring Boot
- Spring Web
- MySQL-compatible database
- XAMPP/MySQL for local development
- TiDB Cloud for cloud deployment
- Spring Data JPA dependency with predominantly `JdbcTemplate`-based repository implementation in the current modules
- Maven
- Docker deployment support for Render

## 3. Backend Responsibilities
The backend is responsible for the following functions:

- Teacher and principal login
- Teacher account approval and status management
- Class assignment management
- Student import and enrollment management
- Assessment and test part creation
- Mobile sync download
- Mobile sync upload
- Analytics computation
- Excel export endpoints

In practice, the backend acts as the source of truth for academic structure, assessment metadata, uploaded checking results, and report generation.

## 4. Main Database Tables

### `user`
Stores principal and teacher accounts, including profile information, role, login credentials, and approval status.

### `academic_year`
Stores school year definitions used for class assignment, enrollment, and filtering of active records.

### `grade_level`
Stores grade level references used by sections and competency tagging.

### `section`
Stores school sections created from SF1 import. Each section is linked to a grade level and academic year so assignment filtering can distinguish the same section name across different school years.

### `subject`
Stores subject references used in class assignment and competency tagging.

### `class`
Represents a class assignment. In this system, a class means the combination of teacher, subject, section, and academic year.

### `student`
Stores the master student profile. This contains the student identity record independent of a specific school year placement.

### `student_enrollment`
Stores the student placement for a specific academic year and section. This separates long-term student identity from yearly enrollment.

### `competency_tags`
Stores competency references per grade level and subject. Root competencies and branch skills are represented in this table. Branch skills point to their root competency through `parent_competency_id`.

### `grading_period`
Stores grading period records such as period name, order, date range, academic year, and status.

July 27, 2026 data dictionary note:
This concept is being reviewed as `term_period`. The backend may still use the current implemented table name until a final migration is approved.

### `curriculum`
Planned/reference entity for storing the curriculum version used by the school system. It is recommended as the base reference for competency versions, especially when competencies differ across curriculum updates.

### `test`
Stores assessment headers such as test name, type, date, status, and the owning `class_id`.

### `test_part`
Stores competency-linked parts of an assessment. Each row belongs to a `test_id` and a `competency_id`.

### `part_skill_mapping`
Maps a test part to branch-level skills for deeper LMS analytics.

July 29, 2026 implementation note:
The active backend design is range-only. Each mapping uses `item_count`, `start_item`, and `end_item`. The previous `mapping_mode` / `CUSTOM` design was removed from the active backend path.

### `skill_item`
Historical table for exact custom item mapping under `part_skill_mapping`. As of the July 29, 2026 range-only cleanup, this table is no longer required by the active backend design.

### `intervention`
Planned/reference entity for storing intervention master-list records such as guided review, reteaching, or priority intervention. The current backend can generate intervention recommendation text dynamically, but a master-list entity may strengthen the database design.

### `answer_key`
Planned normalized table for storing one correct answer per test part item. This was reviewed on July 27, 2026 as a future improvement over storing comma-separated answer keys directly in `test_part`.

### `test_result`
Stores checked student assessment results uploaded from mobile, including the total score and raw answer data for one student in one test.

### `test_item_result`
Stores item-level correctness data per checked student result. This is the main source for item analysis and least mastered skill computation.

Example analytics meaning:

`Item 1: 12/60 students answered correctly`

### `sync_log`
Stores upload sync activity records, including teacher, test, timestamp, and sync status.

## 5. Authentication API

Approximate implementation/documentation period: late May 2026.

### `POST /api/auth/login`
Purpose: Authenticates a principal or teacher account.

The login response includes:
- `userId`
- `firstName`
- `lastName`
- `email`
- `role`
- `status`
- `token` if available

Related authentication endpoints in the current backend include teacher registration, current-user lookup, teacher account listing, and teacher approval status update.

## 6. School Setup APIs

Approximate implementation/documentation period: late May 2026.
Updated on July 13, 2026 to support SF1-based section availability for teacher class assignment.
The school setup module manages the academic reference data and class assignment structure needed before assessment creation and sync operations can proceed.

Main purposes covered by the current APIs:
- Teacher approval endpoints for listing teacher accounts and updating teacher status
- Class assignment endpoints for listing and creating teacher-subject-section assignments
- Subject, section, grade level, and academic year related references used by school setup and class formation

Current school setup endpoint groups include:
- `GET /api/school-setup/grade-levels`
- `GET /api/school-setup/subjects`
- `GET /api/school-setup/sections`
- `GET /api/school-setup/sections/available?gradeLevelId={gradeLevelId}&academicYearId={academicYearId}&subjectId={subjectId}`
- `GET /api/school-setup/teachers`
- `GET /api/school-setup/students`
- `GET /api/school-setup/class-assignments`
- `POST /api/school-setup/sections`
- `POST /api/school-setup/class-assignments`

Important July 13, 2026 workflow update:
- Sections are no longer intended to be manually pre-seeded for assignment.
- SF1 import is the source that creates section records.
- A section must have imported/enrolled students before it appears in the assignment dropdown.
- Available sections are filtered by grade level, academic year, and subject.
- A section is hidden from the available list once it is already assigned for the same subject and academic year.
- `POST /api/school-setup/sections` is retained for compatibility but rejects direct section creation because sections should come from SF1 import.

Teacher approval endpoints include:
- `GET /api/auth/teachers`
- `PUT /api/auth/teachers/{userId}/status`

In this backend, a class represents:

`teacher + subject + section + academic year`

This combined structure is stored in the `class` table and is used as the owner of assessments and the main filter for teacher-scoped sync data.

## 7. SF1 Import API

Approximate implementation/documentation period: late May 2026.
Updated on July 13, 2026 so confirmed SF1 import can create the section record using selected grade level, selected academic year, and detected section name.
The SF1 import API supports structured student intake from official school SF1 files.

Implemented endpoints:
- `POST /api/import/sf1/preview`
- `POST /api/import/sf1/confirm`

The SF1 workflow includes:
- Preview import before database write
- Confirm import for actual persistence
- Detected school year
- Detected section
- Section creation when the detected section does not yet exist for the selected grade level and academic year
- Student creation or update
- Student enrollment creation

The preview response includes detected metadata such as:
- `detectedSchoolYear`
- `detectedSectionName`
- total, valid, and invalid row counts

The confirm response summarizes:
- imported students
- updated students
- enrolled students
- skipped rows

Conceptually, the import follows this rule:
- `student` stores the master student profile
- `student_enrollment` stores the school-year placement

This design allows the same student to remain a single master record while yearly placement is recorded separately for a section and academic year.

Updated confirm request pattern:

`POST /api/import/sf1/confirm?gradeLevelId={gradeLevelId}&academicYearId={academicYearId}`

The backend uses the detected SF1 section name together with `gradeLevelId` and `academicYearId` to create or reuse the correct section before enrolling students.

## 8. Assessment Setup API

Approximate implementation/documentation period: late May 2026 for the core assessment setup, with grading period and skill mapping additions documented in late June 2026.
The assessment setup module manages the creation and retrieval of tests and their competency-linked parts.

Current endpoints include:
- `POST /api/assessments`
- `POST /api/assessments/{testId}/parts`
- `GET /api/assessments/teacher/{teacherId}`
- `GET /api/assessments/{testId}`
- `GET /api/assessments/classes/{classId}/competencies`

Related setup endpoints:
- `GET /api/grading-periods`
- `POST /api/grading-periods`
- `GET /api/competencies/tree`
- `POST /api/part-skill-mappings/preview`
- `POST /api/part-skill-mappings/save`
- `GET /api/part-skill-mappings/test-parts/{testPartId}`

Design rules:
- A `test` belongs to `class_id`
- A `test_part` belongs to `test_id` and one root `competency_id`
- Branch-level item mapping is stored in `part_skill_mapping` using `item_count`, `start_item`, and `end_item`

This means assessment ownership is always class-based, while item grouping and analytics alignment are competency-based through the test part records.

## 9. Mobile Sync Download API

Approximate implementation/documentation period: late May 2026, with restore support documented in late May to early June 2026.

### `GET /api/sync/download/{teacherId}`
Purpose: Downloads teacher-scoped setup data and previously uploaded checked results for the mobile application.

Downloaded data includes:
- `classes`
- `students`
- `tests`
- `testParts`
- `competencies`
- `testResults`
- `itemResponses`

Important behavior:
- Students include section, grade level, and academic year metadata to support matching and display on mobile
- `testResults` and `itemResponses` allow mobile restore after local reset
- Results are filtered by the teacher's assigned classes

The current backend implementation returns both setup metadata and uploaded checked-result data in a single download response so the mobile app can restore state without changing the upload contract.

## 10. Mobile Sync Upload API

Approximate implementation/documentation period: late May 2026.

### `POST /api/sync/upload`
Purpose: Uploads checked student results from the mobile application to the backend.

Upload payload includes:
- `teacherId`
- `testId`
- `testResults`
- `itemResponses`

Upload behavior:
- Mobile uploads checked results
- Unchecked students are not uploaded
- Duplicate prevention protects against repeated uploads
- Uploaded results are used by analytics

The upload path stores both summary-level result rows and item-level correctness rows so that later analytics and export endpoints can compute report data from synchronized mobile checking output.

## 11. Analytics APIs

Approximate implementation/documentation period: late May 2026 for core analytics, late June 2026 for deeper LMS and teacher intervention, and late June to early July 2026 for student skill mastery.
The analytics module computes teacher-level and principal-level academic performance insights from uploaded backend result data.

Current endpoints include:
- `GET /api/analytics/item-analysis?testId={testId}`
- `GET /api/analytics/lms?testId={testId}`
- `GET /api/analytics/lms-affected-students?testId={testId}`
- `GET /api/analytics/intervention?testId={testId}&competencyId={competencyId}`
- `GET /api/analytics/teacher-interventions?testId={testId}`
- `GET /api/analytics/student-skill-mastery?studentId={studentId}&classId={classId}`
- `GET /api/analytics/school-lms`
- `GET /api/analytics/trends?classId={classId}`
- `GET /api/analytics/sync-activity?teacherId={teacherId}`

Expected computations:

### Item analysis
Computed from item-level records using:

`correctResponses / totalResponses / difficulty`

This provides per-item performance information and correctness percentage for each test part item.

### Least mastered skills
LMS is computed as mastery rate grouped by competency. This identifies competencies with weaker class performance based on uploaded result data.

### Affected students
Affected students are identified from wrong responses per competency. This allows the backend to group which students were affected by the least mastered competencies.

### Intervention recommendations
The intervention endpoint returns students who need intervention for a selected test and competency. This supports teacher remediation planning.

The teacher intervention recommendation endpoint returns a teacher-facing recommendation generated from computed mastery status. It is not a direct message to students.

### Student skill mastery
The student skill mastery endpoint returns all assessed competency tags for one student in one class. It includes mastered, developing, and needs-support skills and is not limited to least mastered skills.

### School-wide LMS
If used, school-wide LMS provides principal-level mastery summaries and can be filtered by grade level, section, subject, or teacher.

### Trend analytics
If used, trend analytics provides average assessment score progression per class across multiple tests.

## 12. Export APIs

Approximate implementation/documentation period: late May 2026 for item analysis and LMS exports, and late June to early July 2026 for selected assessment student score export.
The backend exposes Excel report endpoints generated from backend analytics results.

Current export endpoints:
- `GET /api/export/item-analysis/{testId}`
- `GET /api/export/lms/{testId}`
- `GET /api/export/student-scores/{testId}`

Purpose:
- Item analysis Excel export
- LMS Excel export
- Student score Excel export for one selected assessment

These reports are generated from uploaded test results stored in the backend database, not from client-side temporary data.

## 13. Deployment Configuration

Approximate implementation/documentation period: July 2026.
Deployment-related updates were added on July 12, 2026 after Render/TiDB testing.

The backend supports both local development and cloud deployment without changing business logic.

Local default:
- Uses XAMPP/MySQL through `application.properties`
- Default URL points to `localhost:3306/performance_assessment_db`

Cloud deployment:
- Uses `application-tidb.properties`
- Activated with `SPRING_PROFILES_ACTIVE=tidb`
- Uses TiDB Cloud JDBC URL, username, and password from environment variables
- Uses `SPRING_SQL_INIT_MODE=never` to avoid re-running schema and seed scripts against the migrated cloud database

Docker deployment:
- `Dockerfile` builds the Maven project using the Maven Wrapper and runs the generated Spring Boot JAR
- `.dockerignore` removes local build output, logs, Git metadata, and editor folders from the Docker build context
- `server.port=${PORT:8080}` allows Render to assign the runtime port

Deployment debugging updates:
- July 12, 2026: Deployment approach changed from Render Java runtime expectation to Render Docker deployment because the available Render runtime options did not include Java for the account. The backend architecture and API logic were kept unchanged.
- July 12, 2026: CORS was configured for the deployed React frontend origin.
- July 12, 2026: SQL queries with subqueries inside `JOIN ON` conditions were rewritten for TiDB compatibility.
- July 13, 2026: Documentation was updated to include SF1-based section creation and available section filtering.

## 14. Data Integrity Rules
- One class assignment is unique by subject + section + academic year
- One test belongs to one class
- Student enrollment is separated from student profile
- Sections are created from SF1 import and linked to grade level + academic year
- A section must have enrolled students before it can be selected for class assignment
- A section cannot be assigned twice for the same subject and academic year
- Uploaded mobile results should not duplicate analytics
- Download sync uses complete metadata for mobile matching
- `test_item_result` uses `item_result_id` as primary key

These rules help keep class ownership, student history, analytics accuracy, and mobile restore behavior consistent across the system.

## 15. Current Confirmed Working Features
- Login works
- Teacher approval works
- Class assignment works
- Available section filtering for assignment exists
- SF1 import works
- SF1 import can create sections for the selected grade level and academic year
- Assessment setup works
- Sync download works
- Sync upload works
- Restore uploaded results works
- Analytics reflects uploaded mobile results
- Export endpoints exist
- Student score export exists
- Teacher intervention recommendation exists
- Student skill mastery endpoint exists
- TiDB Cloud profile is configured
- Docker deployment support for Render exists
- CORS configuration for the deployed frontend exists
- TiDB-compatible analytics SQL fixes exist

## 17. Dated Backend Revision Summary

| Date / Period | Backend Documentation Update | Related Feature |
| --- | --- | --- |
| Late May 2026 | Initial backend API documentation | Core backend, sync, analytics, assessment setup, import/export, authentication |
| Late June 2026 | Rule-based LMS and skill mapping documentation | `parent_competency_id`, `part_skill_mapping`, `skill_item` |
| Late June to Early July 2026 | Analytics and export documentation expanded | Teacher interventions, student skill mastery, student score export |
| July 12, 2026 | Deployment approach pivoted to Docker and troubleshooting notes were added | Render Docker deployment, CORS for Render frontend, and TiDB SQL compatibility |
| July 13, 2026 | School setup and SF1 workflow documentation updated | SF1-created sections and available section filtering for class assignment |
| July 27, 2026 | Database redesign and polishing notes documented | Curriculum, intervention, answer key normalization, term period review, student enrollment relationship, and item-result analytics meaning |
| July 29, 2026 | Part-skill mapping simplified to range-only | Removed active backend dependency on `mapping_mode`, `CUSTOM`, and `skill_item` |

## 16. Known Future Improvements
- Web correction/resubmission workflow
- Audit trail for corrected results
- Better security/token enforcement if needed
- Final production security hardening
- Final deployment runbook after Render deployment succeeds
- Better error handling/logging

## 18. July 27, 2026 Database Polishing Summary

These notes document database design review items only. They are not automatic code or schema changes until a final migration script is approved.

- `curriculum` is recommended as a reference entity for the curriculum version used by competencies.
- `intervention` is recommended as a simple master/reference table if the system needs a formal database entity for intervention choices.
- `student_enrollment` should remain linked to `section_id`, not `class_id`, because enrollment is section/year based while class is teacher/subject/section/year based.
- `grade_level_id` does not need to be duplicated in `student_enrollment` if `section` already contains `grade_level_id`.
- `test_result` stores one student's overall result for one test.
- `test_item_result` stores each student's correctness per item and is aggregated for item analytics such as `Item 1: 12/60 students answered correctly`.
- `answer_key` can be normalized later into one row per item through `answer_key(test_part_id, item_number, correct_answer, points)`.
- `status` should control active academic year and active term period workflow; `start_date` and `end_date` should remain supporting fields and may be nullable.
