# V2 Database Defense Brief

**Phase:** Phase 1 of 5 - Database Understanding and Baseline Audit  
**Status:** Analysis and documentation only  
**Database target:** `performance_assessment_v2_db`  
**Prepared for:** Adviser/database defense and next backend implementation AI  

## 1. Executive Summary

The V2 database is a separate, normalized redesign of the existing Performance Analytics Assessment System. Hindi nito pinapalitan agad ang working V1 system. The current V1 database, deployed TiDB database, existing frontend, and existing mobile app must remain operational while V2 is implemented and tested behind versioned `/api/v2` endpoints.

The database design supports this approved flow:

```text
School Setup / SF1
-> Classes and Class Lists
-> Teacher Assignment
-> Assessment Creation
-> Mobile Download
-> Paper Bubble Sheet
-> OMR Detection
-> Teacher Verification
-> SQLite Offline Storage
-> Batch Synchronization
-> Spring Boot Validation and Scoring
-> V2 MySQL/TiDB
-> Analytics and Intervention
```

The important defense point is: **V2 modernizes the answer-capture layer without discarding the analytics logic.** Instead of the teacher manually encoding every answer, student responses are captured from paper bubble sheets through OMR, reviewed by the teacher, stored offline first, and synchronized later. The final verified answers still become structured result data for item analysis, least-mastered skills, and intervention recommendations.

## 2. Boundary: V1 Versus V2

| Area | V1 | V2 |
| --- | --- | --- |
| Runtime status | Current working system | Isolated redesign |
| Database | Existing local `performance_assessment_db` and deployed TiDB | Local `performance_assessment_v2_db`; TiDB V2 not migrated |
| Backend API | Existing non-versioned and `/api/sync` endpoints | Versioned `/api/v2/**` endpoints |
| Mobile capture | Teacher inputs/checks answers manually | Planned fixed-template OMR capture with teacher verification |
| Sync | Existing V1 sync contract | Planned UUID-based batch sync with partial success |
| Analytics | Existing analytics from V1 result rows | Planned analytics from verified `student_answers` |

Do not switch `application.properties` or TiDB to V2 just because the database exists. The backend uses explicit `JdbcTemplate` SQL, so a datasource-only switch would break many V1 repositories and could produce incorrect ownership/analytics behavior.

## 3. Confirmed Current V2 Runtime Status

### Implemented and locally validated in V2

- V2 profile and datasource config: `application-v2.properties`
- V2 authentication foundation:
  - `POST /api/v2/auth/login`
  - `GET /api/v2/auth/me`
  - `POST /api/v2/auth/logout`
  - stateless bearer session lookup
  - refresh-token hashing/session revocation model
  - failed-login recording, lockout support, and audit events
- Principal-managed teacher accounts:
  - `POST /api/v2/users/teachers`
  - `GET /api/v2/users/teachers`
  - `POST /api/v2/users/teachers/{userId}/approve`
  - `POST /api/v2/users/teachers/{userId}/reject`
- School reference data and class assignment:
  - `GET /api/v2/school-setup/reference-data`
  - `GET /api/v2/school-setup/available-classes`
  - `GET /api/v2/school-setup/class-assignments`
  - `POST /api/v2/school-setup/class-assignments`

### Designed but not yet implemented

- V2 assessment creation for tests, parts, questions, answer keys, and mappings
- V2 mobile assessment download
- OMR upload APIs
- Teacher-verification APIs
- V2 scoring and result persistence
- Batch synchronization runtime
- V2 analytics and intervention runtime
- V2 export/report runtime

### Pending integration or deployment

- React frontend V2 integration
- React Native mobile V2 integration
- Mobile SQLite V2 schema/contract
- TiDB V2 staging migration
- Production cutover and rollback rehearsal

## 4. Database Table Groups

The V2 schema currently defines **38 tables** and the validation report confirms **56 foreign-key constraints** after the August 9 recapture-retention correction.

### A. School, Identity, Security, and Audit

These tables answer: “Who is using the system, what school owns them, and what security/audit trail exists?”

| Table | Purpose | PK | Main FK / owner | Source of data | Owning role | Lifecycle |
| --- | --- | --- | --- | --- | --- | --- |
| `addresses` | Normalized address records for school, users, and students | `address_id` | None | Address API, manual entry, or SF1 import | Principal/system | Created during school/user/student setup; updated when address changes |
| `school_profiles` | Official school identity/contact record | `school_id` | `address_id -> addresses` | Principal/school setup | Principal | Created once per school; updated as school profile changes |
| `genders` | Reference values for user/student gender | `gender_id` | None | Reference seed | System/admin | Seeded, rarely changed |
| `majors` | Teacher specialization reference | `major_id` | None | Reference seed/admin setup | Principal/system | Seeded; extended if needed |
| `educational_attainments` | Highest educational-attainment reference | `educational_attainment_id` | None | Reference seed | System/admin | Seeded; active/inactive status controls use |
| `roles` | Authorization roles such as principal/teacher | `role_id` | None | Reference seed | System | Stable security reference |
| `statuses` | Account lifecycle states | `status_id` | None | Reference seed | System | Stable security reference |
| `users` | Principal and teacher accounts, profile, role, status, login-security fields | `user_id` | `school_id`, `address_id`, `gender_id`, `major_id`, `educational_attainment_id`, `role_id`, `status_id` | Principal-created teacher accounts, secure registration/migration | Principal owns teachers in same school; system validates login | Pending -> active/rejected/inactive; login fields updated over time |
| `auth_sessions` | Secure authenticated sessions and token lifecycle | `auth_session_id` | `user_id -> users` | Login API | Authenticated user/system | Issued -> used -> expired/revoked |
| `login_attempts` | Successful/failed login records for lockout/security monitoring | `login_attempt_id` | Optional `user_id -> users` | Login API | System | Append-only security evidence |
| `audit_logs` | Business/security audit trail | `audit_log_id` | Optional `user_id -> users` | Backend service events | System | Append-only; should not be edited as business data |

Defense note: This group makes V2 more defensible than V1 because role/status values are normalized, sessions are tracked, failed logins are retained, and sensitive operations have audit records.

### B. Academic Structure, Classes, and Class Lists

These tables answer: “Which students belong to which class, in which year/section, and who is assigned to teach them?”

| Table | Purpose | PK | Main FK / owner | Source of data | Owning role | Lifecycle |
| --- | --- | --- | --- | --- | --- | --- |
| `curriculums` | Curriculum versions such as MATATAG | `curriculum_id` | None | Reference seed/admin | System/principal | Planned/active/inactive/archived |
| `academic_years` | School year tied to curriculum | `academic_year_id` | `curriculum_id -> curriculums` | School setup/reference seed | Principal/system | Planned -> active -> completed |
| `term_periods` | Grading periods/quarters inside an academic year | `term_period_id` | `academic_year_id -> academic_years` | School setup/reference seed | Principal/system | Planned -> active -> completed |
| `grade_levels` | Supported grade levels | `grade_level_id` | None | Reference seed | System | Stable reference |
| `sections` | Section names under grade levels | `section_id` | `grade_level_id -> grade_levels` | School setup/SF1 | Principal | Created per grade/section; reused by class cohorts |
| `subjects` | Subject reference records | `subject_id` | None | Reference seed | System/principal | Stable reference |
| `students` | Learner master records | `student_id` | `school_id`, `address_id`, `gender_id` | SF1 import or manual student setup | Principal/teacher under school boundary | Active -> inactive/transferred/graduated |
| `classes` | Section cohort for one academic year | `class_id` | `academic_year_id`, `section_id` | School setup/SF1 class creation | Principal | Active -> completed/archived |
| `class_assignments` | Teacher-subject assignment for a class cohort | `class_assignment_id` | `class_id`, `user_id`, `subject_id` | Principal assignment workflow | Principal | Active -> completed/archived |
| `class_lists` | Learner membership in a class cohort | `class_list_id` | `class_id`, `student_id` | SF1 import/manual enrollment | Principal/teacher through school rules | Created when enrolled; retained for historical results |

### Why `class_assignments` exists

`class_assignments` is not just a link table. It is the ownership boundary for teacher work. It says: this teacher can handle this subject for this class in this academic context. V2 tests are owned through `class_assignment_id`, so assessment creation, mobile download, sync upload, and analytics can verify that the authenticated teacher is allowed to access the class/test.

Important protections:

- `UNIQUE (class_id, user_id, subject_id)` prevents duplicate teacher-subject assignments.
- Service rules add stronger behavior: one active primary teacher per class/subject, while co-teachers may be allowed.
- Same-school ownership must be enforced in backend services.

### Why `class_lists` exists

`class_lists` solves the identity problem for assessment attempts. A student can exist as a school-level learner, but an assessment result must belong to the student's membership in a specific class/year. V2 therefore uses `class_list_id` as the authoritative learner identity for a test attempt and QR contract.

Important protections:

- `UNIQUE (class_id, student_id)` prevents duplicate enrollment in the same class.
- `test_results.class_list_id` ensures results are attached to the correct class membership, not only to a generic student record.

### C. Curriculum, Skills, and Interventions

These tables answer: “What learning competency does each question measure, and what intervention can be recommended?”

| Table | Purpose | PK | Main FK / owner | Source of data | Owning role | Lifecycle |
| --- | --- | --- | --- | --- | --- | --- |
| `root_tags` | Broad curriculum competency categories | `root_tag_id` | `curriculum_id -> curriculums` | Curriculum/reference setup | System/principal | Active/inactive |
| `competency_tags` | Official competency statements | `competency_id` | `root_tag_id -> root_tags` | Curriculum setup | System/principal | Created/maintained as curriculum content |
| `skills` | Contextualized competency by term, grade level, and subject | `skill_id` | `competency_id`, `term_period_id`, `grade_level_id`, `subject_id` | Curriculum setup | System/principal/teacher setup | Unique per competency-term-grade-subject |
| `interventions` | Master intervention guidance per skill | `intervention_id` | `skill_id -> skills` | Intervention bank setup | Principal/teacher/system | Active reusable recommendation source |

Defense note: V2 separates broad competency (`competency_tags`) from contextual skill (`skills`). This matters because the same competency statement may be taught in a specific term, grade level, and subject context.

### D. Assessment Definition and Answer Key

These tables answer: “What assessment was created, what are its parts/questions, what is the correct answer, and what skill does each question measure?”

| Table | Purpose | PK | Main FK / owner | Source of data | Owning role | Lifecycle |
| --- | --- | --- | --- | --- | --- | --- |
| `tests` | Assessment header owned by a teacher-class-subject assignment | `test_id` | `class_assignment_id`, `term_period_id` | Teacher assessment creation | Teacher, validated by assignment ownership | Draft -> active -> completed/archived |
| `test_parts` | Ordered assessment parts | `test_part_id` | `test_id -> tests` | Teacher assessment creation | Teacher | Created with test; deleted with parent test in draft workflows |
| `questions` | Individual question records with options | `question_id` | `test_part_id -> test_parts` | Teacher assessment creation/import | Teacher | One row per item; part-local item number is unique |
| `answer_keys` | Correct answer per question | `answer_key_id` | `question_id -> questions` | Teacher assessment setup | Teacher | One answer key per question |
| `mappings` | Question-to-skill mapping | `mapping_id` | `question_id`, `skill_id` | Teacher/assessment setup from selected competencies | Teacher/system validation | Created during assessment setup; drives analytics |

### Why `mappings` exists

V1 used range-style part skill mapping. V2 normalizes this into per-question skill mapping. This means analytics do not need to guess that “items 1-5 measure skill X” after sync. The backend can directly join `student_answers -> questions -> mappings -> skills`.

Important protections:

- `UNIQUE (question_id, skill_id)` prevents duplicate skill links for the same question.
- `question_id` is the authoritative answer/detection identity in V2 upload; `itemNumber` is only for display and diagnostics.

### E. OMR Capture, Verification, Results, and Interventions

These tables answer: “What did the scanner see, what did the teacher verify, what score did the backend derive, and what intervention was generated?”

| Table | Purpose | PK | Main FK / owner | Source of data | Owning role | Lifecycle |
| --- | --- | --- | --- | --- | --- | --- |
| `scan_sessions` | One captured physical OMR sheet/session | `scan_session_id` | `scanned_by_user_id`, optional `verified_by_user_id` | Mobile OMR scan upload after teacher verification | Teacher | Captured -> processing -> needs_verification -> verified/failed |
| `omr_detections` | Raw scanner interpretation per question | `omr_detection_id` | `scan_session_id`, `question_id` | Mobile/OpenCV scanner output | Teacher verifies; backend stores evidence | Pending -> confirmed/corrected |
| `test_results` | Verified overall learner attempt | `test_result_id` | `test_id`, `class_list_id` | Backend after verification/sync | Teacher/backend | Created after verified upload or authorized manual entry |
| `test_result_scans` | Audit link between result and one/more scan sessions | `test_result_scan_id` | `test_result_id`, `scan_session_id`, `decided_by_user_id` | Backend verification/recapture decision | Teacher/backend | Selected/superseded/rejected |
| `student_answers` | Final verified answer per question | `student_answer_id` | `test_result_id`, `question_id`, `verified_by_user_id` | Teacher-verified OMR/manual answer | Teacher/backend | One final answer per result/question |
| `intervention_results` | Generated intervention recommendations for a result | `intervention_result_id` | `test_result_id`, `intervention_id` | Analytics/intervention service | Backend/teacher sees output | Generated after scoring/analytics |

### Why `test_result_scans` exists

This is the V9 correction that makes recapture auditable. Earlier V8-style design had `test_results.scan_session_id`, which allowed only one direct scan source. That was weak because a teacher may rescan a sheet if the first image is blurry or rejected.

V2 now uses:

```text
test_results (1)
-> test_result_scans (many)
<- scan_sessions (1)
```

This allows:

- manual result: zero scan links;
- normal OMR result: one selected scan;
- recapture: old selected scan becomes `superseded`, new scan becomes `selected`;
- rejected capture: retained as `rejected` for audit.

Important protections:

- `UNIQUE (scan_session_id)` means one scan cannot be reused for multiple results.
- `UNIQUE (test_result_id, scan_session_id)` prevents duplicate links.
- Generated `selected_result_id` plus `UNIQUE (selected_result_id)` enforces at most one selected scan per result.
- `decided_by_user_id` records who made the scan disposition decision.

### Why `intervention_results` exists

`intervention_results` separates reusable intervention guidance from actual generated recommendations. `interventions` is the master bank; `intervention_results` records which recommendation was produced for a specific learner result.

Important protections:

- `UNIQUE (test_result_id, intervention_id)` prevents the same recommendation from being duplicated for one result.
- The FK to `test_results` lets recommendations disappear if a rollback/test result cleanup deletes the result.

### F. Synchronization

These tables answer: “What mobile batch was uploaded/downloaded, and what happened to each result inside that batch?”

| Table | Purpose | PK | Main FK / owner | Source of data | Owning role | Lifecycle |
| --- | --- | --- | --- | --- | --- | --- |
| `syncs` | One mobile synchronization batch | `sync_id` | `user_id`, optional `test_id` | Mobile sync request | Authenticated teacher/backend | Pending -> in_progress -> partial_success/success/failed |
| `sync_items` | Per-result processing status inside the batch | `sync_item_id` | `sync_id`, optional `test_result_id` | Backend while processing upload | Backend | Pending -> success/failed/skipped |

### Why `sync_items` exists

A batch can contain many student results. If one result fails validation, the whole batch should not hide the successful results. `sync_items` gives item-level success/failure and supports retry-safe diagnostics.

Important protections:

- `syncs.sync_uuid` is unique per batch and must be reused for retrying the same batch.
- `sync_items` uses `result_uuid` so a result can be tracked before or after the central `test_result_id` exists.
- SQL reference uses `UNIQUE (sync_id, result_uuid)`, which is the correct retry uniqueness.
- `sync_items.test_result_id` is nullable until insertion/match succeeds.

## 5. Complete Data Journey: One Student

1. **School setup:** A principal creates or confirms `school_profiles`, reference addresses, grade levels, sections, subjects, and school year records.
2. **SF1/class list:** A learner is inserted in `students`; the school creates a `classes` row for the section/year; the learner joins the class through `class_lists`.
3. **Teacher assignment:** The principal assigns a teacher and subject to the class through `class_assignments`. This becomes the teacher's ownership boundary.
4. **Assessment creation:** The teacher creates `tests`, `test_parts`, `questions`, and `answer_keys`. Each question is linked to a skill through `mappings`.
5. **Mobile download:** The future V2 mobile app downloads only the authenticated teacher's authorized `classAssignments`, `classes`, `classLists`, `students`, `tests`, `testParts`, `questions`, `answerKeys`, `skills`, and `questionMappings`.
6. **Paper bubble sheet:** The printed sheet uses an immutable `templateVersion` and compact QR identity. The QR should include `testId`, `classListId`, question type, item count, and template version.
7. **OMR detection:** The mobile app scans the sheet offline and produces raw detections per `questionId`: detected option, confidence, status, and raw mark information.
8. **Teacher verification:** The teacher confirms or corrects ambiguous/blank/multiple detections. Only final verified answers are upload-ready.
9. **SQLite offline storage:** Mobile stores `syncUuid`, `resultUuid`, `scanUuid`, `answerUuid`, scan evidence, detections, and verified answers locally while offline.
10. **Batch sync:** When internet is available, the mobile app uploads a V2 batch. `syncs` records the batch; `sync_items` records each result's processing status.
11. **Backend validation/scoring:** The backend verifies teacher ownership, class membership, question identity, answer status, and UUID idempotency. It derives `is_correct`, `points_earned`, `total_score`, `max_score`, and `items_evaluated`; it does not trust client-calculated scores.
12. **Result persistence:** The backend inserts or matches `scan_sessions`, `omr_detections`, `test_results`, `test_result_scans`, and `student_answers`.
13. **Analytics:** Analytics reads verified `student_answers`, joins to `questions`, `mappings`, and `skills`, then calculates item analysis and least-mastered skills.
14. **Intervention:** The backend links weak skills to `interventions` and stores generated recommendations in `intervention_results`.
15. **Audit:** Sensitive events such as login, teacher approval, OMR verification, sync, and denied actions should create `audit_logs`.

## 6. Data-Integrity Protections

### Foreign keys

V2 uses foreign keys to prevent orphan records. Examples:

- `users.school_id -> school_profiles.school_id`
- `students.school_id -> school_profiles.school_id`
- `classes.section_id -> sections.section_id`
- `class_assignments.class_id -> classes.class_id`
- `class_lists.student_id -> students.student_id`
- `tests.class_assignment_id -> class_assignments.class_assignment_id`
- `questions.test_part_id -> test_parts.test_part_id`
- `answer_keys.question_id -> questions.question_id`
- `student_answers.test_result_id -> test_results.test_result_id`
- `omr_detections.scan_session_id -> scan_sessions.scan_session_id`
- `sync_items.sync_id -> syncs.sync_id`

### Composite unique constraints and duplicate prevention

Important duplicate-prevention rules:

- `sections`: one section name per grade level.
- `classes`: one section cohort per academic year.
- `class_assignments`: one teacher-subject assignment per class.
- `class_lists`: one student membership per class.
- `skills`: one competency-term-grade-subject context.
- `test_parts`: one part order per test.
- `questions`: one item number per test part.
- `answer_keys`: one answer key per question.
- `mappings`: one question-skill link.
- `test_results`: one attempt number per test and class-list membership.
- `student_answers`: one verified answer per result and question.
- `intervention_results`: one intervention recommendation per result.
- `sync_items`: one result UUID per sync batch.

### UUID idempotency

The V2 sync contract uses stable offline UUIDs:

- `syncUuid`: one upload batch; reused for retry.
- `resultUuid`: one learner assessment attempt; retained across retries.
- `scanUuid`: one physical capture; new recapture gets a new UUID.
- `answerUuid`: one result/question answer; retained through correction/retry.

This prevents duplicate rows when mobile retries after weak connectivity.

### Role ownership

V2 must use the authenticated user, not client-supplied teacher IDs, as the authorization source. Teachers can access only their assigned classes and assessments. Principals manage school setup and teacher accounts only within their school.

### Answer verification

Raw OMR detections are not final answers. `omr_detections` stores scanner evidence; `student_answers` stores the final teacher-verified answer. Blank, multiple, and invalid answers use `selected_option = NULL` with a clear status.

### Rescan retention

The V9 correction keeps all selected, superseded, and rejected scan links through `test_result_scans`. This makes the system defensible because it can explain why one capture was used and another was not.

### Audit logs

`audit_logs` records security-sensitive and business-critical actions. It should include action, entity, outcome, user, IP/device/user-agent metadata, and safe JSON details without passwords or raw tokens.

## 7. Implementation Status by Module

| Module | Status label | Explanation |
| --- | --- | --- |
| V1 authentication | Existing V1 implementation | Current working backend path; not redesigned in this phase |
| V1 school setup/SF1 | Existing V1 implementation | Still connected to current frontend/mobile/database |
| V1 assessment setup | Existing V1 implementation | Uses V1 tables and current API contracts |
| V1 sync | Existing V1 implementation | `/api/sync` remains V1-only during transition |
| V1 analytics/intervention/export | Existing V1 implementation | Must remain working until V2 acceptance passes |
| V2 database schema | Implemented and locally validated in V2 | 38 tables and 56 FKs validated in isolated local V2 database |
| V2 reference seed | Implemented and locally validated in V2 | Reference data inserted idempotently |
| V2 smoke test | Implemented and locally validated in V2 | End-to-end relationship path passed inside a rollback transaction |
| V2 auth/session/login audit | Implemented and locally validated in V2 | `/api/v2/auth` implemented behind `v2` profile |
| V2 principal-managed teacher accounts | Implemented and locally validated in V2 | `/api/v2/users/teachers` implemented behind `v2` profile |
| V2 school reference/available classes/class assignments | Implemented and locally validated in V2 | `/api/v2/school-setup` reference and assignment endpoints implemented |
| V2 direct section/class/class-list mutation | Designed but not yet implemented | Class assignment exists; full class-list mutation still pending |
| V2 assessment creation | Designed but not yet implemented | Tables exist; endpoints/services pending |
| V2 assessment download | Designed but not yet implemented | Contract exists; runtime pending |
| V2 OMR upload/teacher verification | Designed but not yet implemented | Contract and readiness audit exist; runtime pending |
| V2 scoring/results persistence | Designed but not yet implemented | Tables and rules exist; services pending |
| V2 batch synchronization | Designed but not yet implemented | Tables and API contract exist; endpoints pending |
| V2 analytics/intervention runtime | Designed but not yet implemented | Tables exist; analytics rewrite pending |
| Mobile SQLite V2 | Pending integration or deployment | Must align to approved V2 subset and UUID contract |
| React frontend V2 | Pending integration or deployment | Must wait for stable V2 backend contract |
| TiDB V2 migration | Pending integration or deployment | Local V2 only; staging migration still required |

## 8. Contradictions and Stale References Found

These should be acknowledged during defense; do not hide them.

1. **V9 data dictionary body still contains stale V8-style `test_results.scan_session_id`.**  
   The same V9 document later includes the “Approved OMR Recapture Retention Addendum,” which supersedes the direct scan-session design. The authoritative SQL and API contract remove `test_results.scan_session_id` and use `test_result_scans`.

2. **V9 data dictionary body still shows older sync definitions in places.**  
   It lists `syncs.sync_status` without `partial_success` and suggests `UNIQUE (sync_id, test_result_id)` for `sync_items`. The authoritative SQL/API contract use `partial_success`, nullable `test_result_id`, required `result_uuid`, and `UNIQUE (sync_id, result_uuid)`.

3. **V9 data dictionary body has minor naming/type inconsistencies.**  
   Examples include `education_attainment_id` versus SQL `educational_attainment_id`, singular FK names such as `class`/`user`/`subject` in descriptions, and `subjects.subject_name` shown as `VARCHAR(30)` while SQL uses `VARCHAR(100)` to avoid truncating long subject names.

4. **V2 backend impact audit contains duplicated “V2 Authentication Foundation” checkpoint text.**  
   This is documentation duplication, not a schema contradiction.

5. **Non-bubble question types are unresolved.**  
   V2 currently stores selected options `A` through `E`. V1 may have identification/text-answer parts. Before Java migration, the team must choose bubble-only V2 or extend the model for mixed question types.

6. **Legacy intervention mapping is unresolved.**  
   Existing V1 intervention masters are not clearly linked to skills, so adviser-approved mapping is required before migration.

## 9. Adviser-Ready Explanation

In Taglish:

Ang V2 database ay hindi simpleng dagdag table lang. It is a normalized redesign para malinaw ang ownership, identity, answer capture, verification, sync retry, analytics, and audit trail.

Ang pinakamahalagang change is sa data capture. Sa V1, teacher ang nag-eencode/check ng answers manually. Sa V2, paper-based pa rin ang assessment, pero OMR-ready na: student shades the bubble sheet, mobile app detects the marks offline, teacher verifies unclear answers, then syncs the verified result later. Kaya mas modern siya without forcing every student to have an online account or constant internet connection.

The backend analytics remain useful because the final output is still structured answers and scores. The difference is that V2 separates:

- raw scanner evidence: `omr_detections`;
- teacher-verified final answers: `student_answers`;
- overall score: `test_results`;
- scan lineage and recapture history: `test_result_scans`;
- generated recommendations: `intervention_results`.

This is stronger for defense because the system can explain not only the score, but also where the answer came from, who verified it, whether there was a rescan, and how the recommendation was generated.

## 10. Phase 1 Completion Gate

Phase 1 can be considered complete only after the owner confirms:

- The database explanation is understandable and adviser-ready.
- All 38 tables have an identified purpose.
- The complete workflow has been logically validated.
- V1, implemented V2, designed V2, and pending integration are clearly separated.
- No unresolved contradiction is hidden.

Until then, do not proceed to Phase 2 implementation.
