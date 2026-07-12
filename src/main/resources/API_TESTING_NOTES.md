# API Testing Notes

Documentation timeline note: Initial API testing notes were written around late May 2026. Deeper LMS, intervention, student skill mastery, selected assessment score export, and deployment notes were added from late June to July 2026.

## Sync Endpoints

Approximate testing/documentation period: late May 2026, with restore behavior checked again during later integration testing.

### `GET /api/sync/download/1`
- Purpose: Downloads assigned classes, students, tests, test parts, answer keys, and competency tags for teacher mobile SQLite.
- Status: Working.
- Expected: Returns `classes`, `students`, `tests`, `testParts`, `competencies`.

### `POST /api/sync/upload`
- Purpose: Uploads offline mobile test results and item responses to backend.
- Status: Working.
- Expected: Inserts `test_result`, `test_item_result`, and `sync_log`.
- Duplicate prevention: Sending the same payload again returns `uploadedResults = 0` and `uploadedItems = 0`.

## Analytics Endpoints

Approximate testing/documentation period: late May 2026 for core analytics; late June to early July 2026 for deeper LMS, teacher intervention, and student skill mastery.

### `GET /api/analytics/item-analysis?testId=1`
- Purpose: Computes item success rate from `test_item_result`.
- Status: Working.

### `GET /api/analytics/lms?testId=1`
- Purpose: Computes competency mastery and LMS from item-level responses grouped by competency tag.
- Status: Working.

### `GET /api/analytics/intervention?testId=1&competencyId=1`
- Purpose: Shows students below mastery threshold for teacher remediation planning.
- Status: Working.
- Note: This is teacher decision-support only, not direct-to-student intervention.

### `GET /api/analytics/teacher-interventions?testId=1`
- Purpose: Returns teacher-facing intervention recommendations based on computed mastery status.
- Status: Working.
- Expected: Returns competency, mastery rate, status, affected learner count, recommendation text, and affected students.
- Note: The recommendation text is for the teacher only and does not mention sync/synced results.

### `GET /api/analytics/student-skill-mastery?studentId=1&classId=1`
- Purpose: Returns all assessed skills for one student in one class.
- Status: Working.
- Expected: Returns mastered, developing, and needs-support skills, not only low-mastery records.
- Note: Uses branch skill item mapping when available and falls back to test part competency when no branch mapping exists.

### `GET /api/analytics/lms-affected-students?testId=1`
- Purpose: Groups affected students under each least mastered competency.
- Status: Working.

### `GET /api/analytics/school-lms`
- Purpose: Principal-level school-wide LMS aggregation.
- Status: Working.

### `GET /api/analytics/trends?classId=1`
- Purpose: Shows average score trend per assessment.
- Status: Working.

### `GET /api/analytics/sync-activity?teacherId=1`
- Purpose: Shows teacher synchronization activity as data freshness indicator.
- Status: Working.

## Assessment Setup Endpoints

Approximate testing/documentation period: late May 2026 for core setup; late June 2026 for grading period and skill mapping flows.

### `GET /api/assessments/teacher/1`
- Purpose: Lists assessments under the teacher's assigned classes.
- Status: Working.
- Test result: Returned `Quiz 1`.

### `GET /api/assessments/1`
- Purpose: Retrieves assessment details including test parts, answer key, and competency tag.
- Status: Working.
- Test result: Returned `Quiz 1` with `Part I`, answer key `A,B,C`, and competency `Linear Equations`.

### `GET /api/assessments/classes/1/competencies`
- Purpose: Retrieves available competency tags for the selected class based on grade level and subject.
- Status: Working.
- Test result: Returned `Linear Equations`.

### `POST /api/assessments`
- Purpose: Creates a new assessment header.
- Status: Working.
- Test body used:

```json
{
  "classId": 1,
  "testName": "Quiz 2",
  "testType": "Quiz",
  "testDate": "2026-05-22",
  "testStatus": "Active"
}
```

- Test result: Returned new `testId = 2`.

### `POST /api/assessments/2/parts`
- Purpose: Creates a test part linked to one competency tag with answer key.
- Status: Working.
- Test body used:

```json
{
  "competencyId": 1,
  "partOrder": "Part I",
  "partType": "Multiple Choice",
  "numberOfItems": 5,
  "pointsPerItem": 1,
  "answerKey": "A,B,C,D,A"
}
```

- Test result: Returned new `testPartId = 2`.

Important note:
The system does not store actual test questions. It only stores assessment metadata, test parts, answer keys, and competency tags. This supports paper-based assessments while still enabling backend analytics.

## Export Report Endpoints

Approximate testing/documentation period: late May 2026 for item analysis and LMS exports; late June to early July 2026 for student score export.

### `GET /api/export/item-analysis/1`
- Purpose: Downloads an Excel file for item analysis.
- Status: Working.
- Test result: Downloaded `item-analysis-test-1.xlsx`.
- Output columns: `Test Part ID`, `Item Number`, `Correctness Percentage`.

### `GET /api/export/lms/1`
- Purpose: Downloads an Excel file for LMS report.
- Status: Working.
- Test result: Downloaded `lms-report-test-1.xlsx`.
- Output columns: `Competency ID`, `Competency Name`, `Mastery Rate`, `Status`.

### `GET /api/export/student-scores/1`
- Purpose: Downloads an Excel file containing checked student score rows for one selected assessment.
- Status: Working.
- Test result: Downloads `student-scores-test-1.xlsx`.
- Output columns: `Student ID`, `LRN`, `Student Name`, `Gender`, `Grade Level`, `Section`, `Assessment Name`, `Test ID`, `Total Score`, `Max Score`, `Percentage`, `Status / Performance`, `Checked At`.

Important note:
Export reports use backend analytics results and Apache POI. The Excel files are generated from synchronized item-level records, not from frontend/mobile calculations.

## Deployment Configuration Notes

Approximate documentation period: July 2026.

### Local Development
- Default profile uses XAMPP/MySQL through `application.properties`.
- Local database URL points to `localhost:3306/performance_assessment_db`.
- SQL initialization defaults to `always` for local development.

### TiDB Cloud / Render
- Cloud profile file: `application-tidb.properties`.
- Activate with `SPRING_PROFILES_ACTIVE=tidb`.
- Required environment variables:
  - `SPRING_DATASOURCE_URL`
  - `SPRING_DATASOURCE_USERNAME`
  - `SPRING_DATASOURCE_PASSWORD`
  - `SPRING_SQL_INIT_MODE=never`
- Render Docker deployment is supported through `Dockerfile`.
- Runtime port uses `server.port=${PORT:8080}`.

## Import / Student Setup Endpoints

Approximate testing/documentation period: late May 2026.

### `GET /api/import/manual-students`
- Purpose: Retrieves manually encoded and imported student records with enrollment details.
- Status: Working.
- Test result: Returned students with LRN, name, gender, section, grade level, and academic year.

### `POST /api/import/manual-students`
- Purpose: Manually creates a student record and enrolls the student into a selected section and academic year.
- Status: Working.
- Test body used:

```json
{
  "studentLrn": "100000000100",
  "firstName": "Manual",
  "lastName": "Student",
  "gender": "female",
  "sectionId": 1,
  "academicYearId": 1
}
```

- Test result: Student created successfully.

### `PUT /api/import/manual-students/4`
- Purpose: Updates an existing manually encoded student record.
- Status: Working.
- Test result: Student updated successfully.

### `POST /api/import/sf1/preview`
- Purpose: Uploads an SF1 Excel file and generates a preview of parsed student rows without saving to the database.
- Status: Working.
- Test result using `SF1_DUMMY_TEST.xls`:
  - `totalRows: 5`
  - `validRows: 5`
  - `invalidRows: 0`

### `POST /api/import/sf1/confirm`
- Purpose: Confirms SF1 import by saving valid preview rows into the student and `student_enrollment` tables.
- Status: Working.
- Test result using `SF1_DUMMY_TEST.xls` with `sectionId = 1` and `academicYearId = 1`:

First upload:
- `importedStudents: 5`
- `updatedStudents: 0`
- `enrolledStudents: 5`
- `skippedRows: 0`

Second upload / duplicate test:
- `importedStudents: 0`
- `updatedStudents: 5`
- `enrolledStudents: 0`
- `skippedRows: 0`

Important notes:
- SF1 Smart Import only extracts fields needed by the system: LRN, first name, last name, gender, section, and academic year.
- Grade level is determined through the selected section, not guessed from the SF1 filename.
- The system does not import unnecessary sensitive SF1 fields such as birthdate, age, address, parent/guardian data, religion, mother tongue, remarks, 4Ps, or contact numbers.
- Manual Student Input is maintained as a fallback when SF1 Smart Import fails or when records need correction.
- Students are not system users. Student records are used only for class lists, assessment recording, analytics, LMS, and teacher remediation planning.

## Authentication and Teacher Approval Endpoints

Approximate testing/documentation period: late May 2026.

### `POST /api/auth/login`
- Purpose: Authenticates an approved teacher or principal account.
- Status: Working.
- Test body used:

```json
{
  "email": "teacher@example.com",
  "password": "temporary"
}
```

- Test result: Login successful and returned temporary token `LOCAL-TOKEN-1-teacher`.

### `GET /api/auth/me?userId=1`
- Purpose: Retrieves current user details for local testing.
- Status: Working.
- Test result: Returned active teacher account details.

### `POST /api/auth/register-teacher`
- Purpose: Allows a teacher to submit a registration request.
- Status: Working.
- Test body used:

```json
{
  "firstName": "New",
  "lastName": "Teacher",
  "gender": "female",
  "dateBirth": "1995-01-15",
  "email": "newteacher@example.com",
  "password": "temporary"
}
```

- Test result: Teacher registration submitted successfully with status `pending`.

### `GET /api/auth/teachers`
- Purpose: Allows the principal to view teacher accounts.
- Status: Working.
- Test result: Returned existing active teacher and new pending teacher.

### `PUT /api/auth/teachers/2/status`
- Purpose: Allows the principal to approve or reject a teacher account.
- Status: Working.
- Test body used:

```json
{
  "status": "active"
}
```

- Test result: Teacher account status updated successfully.

### `POST /api/auth/login using approved new teacher`
- Purpose: Confirms that newly approved teacher can login.
- Status: Working.
- Test body used:

```json
{
  "email": "newteacher@example.com",
  "password": "temporary"
}
```

- Test result: Login successful and returned temporary token `LOCAL-TOKEN-2-teacher`.

Important notes:
- Students are not system users and do not have login accounts.
- Only principal and approved teachers are direct users.
- Current token is temporary for local testing.
- JWT and real Spring Security protection will be added later before deployment.
- Teacher registration creates pending accounts.
- Principal approval changes teacher status to active or rejected.

## Temporary RBAC Testing

Protected principal-only endpoints:
- GET `/api/auth/teachers`
- PUT `/api/auth/teachers/{userId}/status`

Test 1:
Request without Authorization header.
Result:
400 Bad Request with message: Authorization token is required.

Test 2:
Request with principal bearer token.
Header:
Authorization: Bearer LOCAL-TOKEN-99-principal
Result:
200 OK. Principal can retrieve teacher accounts and update teacher approval status.

Important note:
This is a temporary local token guard for development testing only. It proves that teacher approval is principal-controlled. Before deployment, this must be replaced with real JWT/Spring Security authentication and authorization.

## Current Completed Backend Modules

1. Sync Module
2. Analytics Module
3. School Setup Module
4. Assessment Setup Module
5. Basic Export Reports Module
6. Import / Student Setup Module with Manual Input and SF1 Smart Import
7. Basic Authentication, Teacher Approval, and Temporary RBAC Module
8. Rule-Based LMS Mapping Module
9. Teacher Intervention Recommendation Endpoint
10. Student Skill Mastery Endpoint
11. Student Scores Export Endpoint
12. TiDB Cloud / Render Docker Deployment Configuration

## Defense Notes

- Analytics are computed in the backend, not mobile or frontend.
- Mobile captures item-level correctness and uploads synchronized records.
- Backend prevents duplicate uploads using unique constraints and existence checks.
- LMS is based on competency tags, not raw score only.
- Intervention output is for teacher planning, not direct student access.
