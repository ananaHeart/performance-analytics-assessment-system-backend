# Backend API Documentation

Documentation timeline note: This document began as the backend API reference around late May 2026 and was updated through late June and July 2026 as rule-based LMS, student mastery, export, TiDB Cloud, and Render Docker deployment support were added.

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
Stores school sections and links each section to a grade level.

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

### `test`
Stores assessment headers such as test name, type, date, status, and the owning `class_id`.

### `test_part`
Stores competency-linked parts of an assessment. Each row belongs to a `test_id` and a `competency_id`.

### `part_skill_mapping`
Maps a test part to branch-level skills for deeper LMS analytics. It supports `RANGE` and `CUSTOM` item mapping modes.

### `skill_item`
Stores exact item numbers for custom skill mapping under `part_skill_mapping`.

### `test_result`
Stores checked student assessment results uploaded from mobile, including the total score and raw answer data for one student in one test.

### `test_item_result`
Stores item-level correctness data per checked student result. This is the main source for item analysis and least mastered skill computation.

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
The school setup module manages the academic reference data and class assignment structure needed before assessment creation and sync operations can proceed.

Main purposes covered by the current APIs:
- Teacher approval endpoints for listing teacher accounts and updating teacher status
- Class assignment endpoints for listing and creating teacher-subject-section assignments
- Subject, section, grade level, and academic year related references used by school setup and class formation

Current school setup endpoint groups include:
- `GET /api/school-setup/grade-levels`
- `GET /api/school-setup/subjects`
- `GET /api/school-setup/sections`
- `GET /api/school-setup/teachers`
- `GET /api/school-setup/students`
- `GET /api/school-setup/class-assignments`
- `POST /api/school-setup/sections`
- `POST /api/school-setup/class-assignments`

Teacher approval endpoints include:
- `GET /api/auth/teachers`
- `PUT /api/auth/teachers/{userId}/status`

In this backend, a class represents:

`teacher + subject + section + academic year`

This combined structure is stored in the `class` table and is used as the owner of assessments and the main filter for teacher-scoped sync data.

## 7. SF1 Import API

Approximate implementation/documentation period: late May 2026.
The SF1 import API supports structured student intake from official school SF1 files.

Implemented endpoints:
- `POST /api/import/sf1/preview`
- `POST /api/import/sf1/confirm`

The SF1 workflow includes:
- Preview import before database write
- Confirm import for actual persistence
- Detected school year
- Detected section
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
- Branch-level item mapping is stored separately in `part_skill_mapping` and `skill_item`

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

## 14. Data Integrity Rules
- One class is unique by teacher + subject + section + academic year
- One test belongs to one class
- Student enrollment is separated from student profile
- Uploaded mobile results should not duplicate analytics
- Download sync uses complete metadata for mobile matching
- `test_item_result` uses `item_result_id` as primary key

These rules help keep class ownership, student history, analytics accuracy, and mobile restore behavior consistent across the system.

## 15. Current Confirmed Working Features
- Login works
- Teacher approval works
- Class assignment works
- SF1 import works
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

## 16. Known Future Improvements
- Web correction/resubmission workflow
- Audit trail for corrected results
- Better security/token enforcement if needed
- Final production security hardening
- Final deployment runbook after Render deployment succeeds
- Better error handling/logging
