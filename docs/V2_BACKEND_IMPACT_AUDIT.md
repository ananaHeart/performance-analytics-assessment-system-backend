# V2 Backend Impact Audit

**Audit date:** August 9, 2026  
**Status:** Architecture and migration planning only; the running backend still uses V1.

## Safety Boundary

- The current Spring Boot runtime remains connected to `performance_assessment_db`.
- The separate local `performance_assessment_v2_db` is a validated V2 target, not the active runtime database.
- The deployed TiDB database is not changed by this audit.
- Do not point `application.properties` or the `tidb` profile to V2 until the V2 repositories, DTOs, services, security rules, and clients pass their acceptance tests.
- Existing V1 sync changes in `SyncRepository.java` and `SyncServiceImpl.java` are preserved.

## Why a Datasource-Only Switch Is Unsafe

The backend primarily uses explicit `JdbcTemplate` SQL. Its repositories query singular V1 tables and V1 columns directly. V2 uses normalized plural tables and new relationships, so changing only the database name would produce SQL errors or incorrect ownership and analytics behavior.

## Module Impact Matrix

| Backend area | Current V1 source | Required V2 source or behavior | Main code surfaces |
|---|---|---|---|
| Authentication | `user` with role/status text | `users`, `roles`, `statuses`, `auth_sessions`, `login_attempts`, `audit_logs`; hashed passwords and server-side RBAC | `auth`, `config/SecurityConfig.java` |
| School setup | `section`, `class`, `student_enrollment` | `sections`, `classes`, `class_assignments`, `class_lists`; preserve SF1-first roster workflow | `schoolsetup`, `importexport` |
| Academic periods | `academic_year`, `grading_period` | `academic_years`, `term_periods` | `gradingperiod`, assessment validation |
| Assessment setup | `test`, `test_part`, comma-delimited answer key | `tests`, `test_parts`, `questions`, `answer_keys`; one row per question/answer | `assessmentsetup` |
| Competency mapping | `competency_tags`, `part_skill_mapping` ranges | `root_tags`, `competency_tags`, `skills`, `mappings`; GUI range selection expands to question mappings | `competency`, `assessmentsetup` |
| Mobile download | V1 class/test/part/answer records | Authenticated, versioned V2 download with class membership, questions, answer keys, and mappings | `sync` |
| Mobile OMR upload | `test_result`, `test_item_result`, `sync_log` | `scan_sessions`, `omr_detections`, `test_results`, `student_answers`, `syncs`, `sync_items`; UUID idempotency and partial success | `sync` |
| Analytics | correctness rows joined to part/range mappings | verified `student_answers` joined to `questions` and `mappings`, with part and competency aggregation | `analytics` |
| Intervention | generated text only plus legacy master data | `interventions` and `intervention_results`, linked to approved skills and verified analytics | `analytics` |
| Export | V1 result/student/test joins | V2 `test_results` + `class_lists` + `students`, with `max_score` denominator | `importexport/ExportServiceImpl.java` |

## Required Implementation Order

1. Add an explicit V2 profile and V2 repository implementations without changing the default V1 profile.
2. Implement authentication, password migration/reset, sessions, RBAC, ownership checks, login throttling, and audit logging.
3. Implement SF1, sections, classes, class assignments, and class-list membership.
4. Implement tests, parts, questions, answer keys, and normalized question-to-skill mappings.
5. Implement versioned V2 download/upload contracts and OMR verification persistence.
6. Rewrite analytics, intervention, and exports against verified V2 answers.
7. Run backend, mobile, frontend, security, migration, rollback, and complete workflow acceptance tests.
8. Switch a staging profile first. Switch production only after approval and a tested rollback window.

## Decisions Still Required Before Java Adaptation

### Non-bubble answer support

V2 currently models `answer_keys.correct_option` and `student_answers.selected_option` as one-character options. The V1 data includes an Identification part with full-text answers. Choose one rule before implementation:

- **Bubble-only V2:** archive or reject non-bubble parts and document the scope; or
- **Mixed question types:** extend the approved data model with text-answer fields and type-specific validation.

The migration rehearsal intentionally does not invent or truncate full-text answers.

### Intervention mapping

The four legacy intervention master rows do not identify a target skill. An adviser-approved mapping or default policy is required before migrating them into normalized V2 intervention records.

### Legacy credentials

The four V1 accounts do not contain production password hashes. They must enter V2 as pending/reset-required accounts or be recreated through an approved secure registration flow.

## Verification Gate

The V2 backend can be considered ready for staging only when all of these pass:

- default V1 profile still compiles and works;
- V2 profile starts against a restored V2 database;
- unauthenticated and wrong-role requests are denied server-side;
- SF1 to class assignment has no orphan or duplicate membership;
- assessment setup preserves question order, answer keys, and skill mapping;
- fixed-template OMR capture requires teacher verification for uncertain marks;
- repeated UUID upload is idempotent and batch partial failures are auditable;
- total and part scores never exceed their correct maximum score;
- analytics and interventions use verified student answers only;
- rollback restores the previously active application/database contract.

## Current Conclusion

The V2 local database and migration rehearsal are ready as engineering inputs. The current backend is **not yet V2-compatible**, and no runtime database switch should be made. The next coding phase starts only after the three decisions above are approved.

## August 10, 2026 Implementation Checkpoint: V2 Authentication Foundation

The first isolated V2 Java runtime slice is now implemented behind the explicit `v2` Spring profile. This does not switch the default V1 runtime, the deployed TiDB database, the React frontend, or the mobile SQLite database.

Implemented scope:

- separate `application-v2.properties` datasource and authentication settings;
- V2-only Spring Security chain with stateless opaque bearer sessions;
- BCrypt password verification;
- `POST /api/v2/auth/login`;
- `GET /api/v2/auth/me`;
- `POST /api/v2/auth/logout`;
- server-side session token hashing, expiration, last-use tracking, and revocation;
- failed-login recording, temporary account lockout, rate limiting, and login audit events;
- inactive, locked, pending, rejected, and unverified-email account rejection;
- deny-by-default protection for other `/api/v2/**` routes while the `v2` profile is active.

The login transaction explicitly retains expected rejection evidence. A rejected login does not roll back its failed-attempt counter, `login_attempts` record, account lock, or `audit_logs` record.

Verification completed:

- `mvn -q -DskipTests compile` passed;
- `V2AuthServiceTest` passed 6 tests with 0 failures and 0 errors;
- the earlier full test-suite run executed the six V2 tests successfully, while the pre-existing `AssessmentApplicationTests.contextLoads` could not connect to the local V1 MySQL service. That environment-dependent context test is not counted as V2 feature acceptance.

Still pending:

- registration, email verification delivery, password reset, and principal approval workflows;
- endpoint-specific principal/teacher role and resource-ownership rules;
- V2 school setup, assessment, OMR verification, sync, analytics, intervention, and export APIs;
- live V2 database/API integration testing;
- frontend/mobile client migration and TiDB staging migration.

## August 10, 2026 Implementation Checkpoint: V2 School Setup and Class Assignment

The third isolated V2 backend slice implements the school-reference and class-assignment prerequisite for future V2 assessment creation. It remains restricted to the explicit `v2` Spring profile and the separate local V2 datasource. The default V1 runtime, deployed TiDB database, React frontend, and mobile SQLite database remain unchanged.

Implemented scope:

- `GET /api/v2/school-setup/reference-data` returns active academic years, grade levels, and subjects;
- `GET /api/v2/school-setup/available-classes` returns eligible same-school classes for an academic year, grade level, and subject;
- `GET /api/v2/school-setup/class-assignments` lists same-school assignments and supports an optional academic-year filter;
- `POST /api/v2/school-setup/class-assignments` creates a primary-teacher or co-teacher assignment;
- principal-only authorization is enforced by Spring Security and verified again in the service;
- class and teacher school ownership, active status, subject existence, and enrolled-learner requirements are enforced server-side;
- exact active duplicate assignments are rejected;
- only one active primary teacher is allowed for a class and subject, while a co-teacher remains valid;
- successful assignment creation writes an audit event.

Verification completed:

- `V2SchoolSetupServiceTest` passed seven focused tests;
- the full Maven suite passed 19 tests with 0 failures and 0 errors while local MySQL was running;
- the focused coverage includes wrong-role denial, cross-school teacher denial, no-enrollment rejection, duplicate rejection, second-primary rejection, and valid co-teacher creation.

Still pending after this checkpoint:

- direct V2 section/class creation and class-list mutation;
- V2 assessment creation for tests, test parts, questions, answer keys, and normalized skill mappings;
- verification delivery and password-reset workflows;
- V2 OMR verification, synchronization, analytics, intervention, export, frontend/mobile integration, and TiDB staging migration.

## August 10, 2026 Implementation Checkpoint: Principal-Managed Teacher Accounts

The second isolated V2 backend slice adds a principal-managed teacher account lifecycle. It remains available only under the explicit `v2` Spring profile and the separate local V2 datasource. It does not change the default V1 APIs, the deployed TiDB database, the React frontend, or the mobile SQLite database.

Implemented scope:

- `POST /api/v2/users/teachers` creates a teacher account in the authenticated principal's school;
- `GET /api/v2/users/teachers` lists teacher accounts only from that principal's school and supports an optional `status` filter;
- `POST /api/v2/users/teachers/{userId}/approve` changes a pending teacher to active;
- `POST /api/v2/users/teachers/{userId}/reject` changes a pending teacher to rejected;
- server-side principal-role enforcement in both Spring Security and the service layer;
- school ownership enforcement that prevents one principal from managing another school's users;
- validation for identity fields, reference identifiers, email, contact number, address, and temporary-password strength;
- duplicate email/contact protection, BCrypt password hashing, transactional address/user creation, and audit logging;
- pending status for newly created accounts and pending-only approval/rejection transitions.

Current security boundary:

- approval does not automatically verify email or contact details;
- an approved teacher remains unable to log in until the required verification workflow records the account's verified contact state;
- password reset, verification delivery, and resend/expiry behavior remain pending.

Verification completed:

- `mvn -q -DskipTests compile` passed;
- `V2AuthServiceTest` and `V2TeacherAccountServiceTest` passed together;
- focused teacher-account tests cover same-school pending creation, wrong-role denial, duplicate-email rejection, approval, audit recording, and cross-school access denial.

Still pending after this checkpoint:

- V2 school setup, section/class creation, class assignments, and class-list membership;
- V2 assessment creation for tests, test parts, questions, answer keys, and skill mappings;
- email/contact verification and password-reset workflows;
- V2 OMR verification, synchronization, analytics, intervention, export, client integration, and TiDB staging migration.

## August 11, 2026 Implementation Checkpoint: V2 Assessment Creation

The fourth isolated V2 backend slice implements assessment creation under the explicit `v2` Spring profile. It uses only `/api/v2/assessments/**` routes and the normalized V2 tables. The default V1 runtime, V1 sync endpoints, deployed TiDB database, React frontend, and mobile SQLite database remain unchanged.

Implemented scope:

- `GET /api/v2/assessments/reference-data`;
- `POST /api/v2/assessments`;
- `GET /api/v2/assessments`;
- `GET /api/v2/assessments/{testId}`;
- `PUT /api/v2/assessments/{testId}`;
- `POST /api/v2/assessments/{testId}/activate`;
- `POST /api/v2/assessments/{testId}/archive`;
- teacher-only service authorization;
- active class-assignment ownership validation;
- same-school and same-academic-year validation;
- `multiple_choice` and `true_false` part support only;
- True/False answer-key normalization rule: `A = True`, `B = False`;
- full transactional draft create/update for `tests`, `test_parts`, `questions`, `answer_keys`, and `mappings`;
- GUI range mapping expansion into per-question `mappings(question_id, skill_id)` rows;
- activation checks for missing questions, answer keys, skill mappings, invalid True/False keys, and item-count snapshot mismatches;
- audit events for create, update, activate, and archive.

Verification completed:

- `mvn -q -DskipTests compile` passed;
- `V2AssessmentServiceTest` passed 12 tests with 0 failures and 0 errors.

Still pending after this checkpoint:

- live Postman/API verification against local `performance_assessment_v2_db`;
- full Maven suite confirmation after local database availability is verified;
- V2 mobile assessment download;
- OMR upload and teacher-verification APIs;
- V2 scoring/result persistence;
- batch synchronization;
- analytics, intervention runtime, export, frontend/mobile integration, and TiDB staging migration.

## August 10, 2026 Implementation Checkpoint: V2 Authentication Foundation

The first isolated V2 Java runtime slice is now implemented behind the explicit `v2` Spring profile. This does not switch the default V1 runtime, the deployed TiDB database, the React frontend, or the mobile SQLite database.

Implemented scope:

- separate `application-v2.properties` datasource and authentication settings;
- V2-only Spring Security chain with stateless opaque bearer sessions;
- BCrypt password verification;
- `POST /api/v2/auth/login`;
- `GET /api/v2/auth/me`;
- `POST /api/v2/auth/logout`;
- server-side session token hashing, expiration, last-use tracking, and revocation;
- failed-login recording, temporary account lockout, rate limiting, and login audit events;
- inactive, locked, pending, rejected, and unverified-email account rejection;
- deny-by-default protection for other `/api/v2/**` routes while the `v2` profile is active.

The login transaction explicitly retains expected rejection evidence. A rejected login does not roll back its failed-attempt counter, `login_attempts` record, account lock, or `audit_logs` record.

Verification completed:

- `mvn -q -DskipTests compile` passed;
- `V2AuthServiceTest` passed 6 tests with 0 failures and 0 errors;
- the earlier full test-suite run executed the six V2 tests successfully, while the pre-existing `AssessmentApplicationTests.contextLoads` could not connect to the local V1 MySQL service. That environment-dependent context test is not counted as V2 feature acceptance.

Still pending:

- registration, email verification delivery, password reset, and principal approval workflows;
- endpoint-specific principal/teacher role and resource-ownership rules;
- V2 school setup, assessment, OMR verification, sync, analytics, intervention, and export APIs;
- live V2 database/API integration testing;
- frontend/mobile client migration and TiDB staging migration.
