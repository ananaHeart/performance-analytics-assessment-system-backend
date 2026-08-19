# V2 Database Validation Report

**Validation dates:** August 8-9, 2026
**Database:** `performance_assessment_v2_db`
**Environment:** Local XAMPP MariaDB on `127.0.0.1:3306`

## Purpose

This report records the non-destructive creation and validation of the proposed V2 database based on `Data Dictionary CAP2_v8.docx` and the authoritative OMR recapture correction in `docs/Data Dictionary CAP2_v9.docx`. The V2 database was created separately so the current working local database, deployed backend, and TiDB Cloud database remain available while the V2 application migration is prepared.

## Database State

- V2 local database created: `performance_assessment_v2_db`
- Existing local working database retained: `performance_assessment_db`
- Initial August 8 state: **37 tables** and **54 foreign-key constraints**
- Current August 9 state after the versioned recapture migration: **38 tables** and **56 foreign-key constraints**
- Existing working-database tables after V2 setup: **19**
- Backend, mobile, and frontend are **not yet connected** to V2
- TiDB Cloud has **not yet been migrated** to V2

MariaDB displayed warning `#1280 Name 'pk_*' ignored for PRIMARY key` during schema creation. This warning means MariaDB ignored the custom name assigned to each primary-key constraint; the primary keys themselves were created.

## Reference Data

The idempotent reference seed was applied twice to verify that rerunning it does not create duplicates.

| Reference table | Final rows |
| --- | ---: |
| `genders` | 2 |
| `majors` | 9 |
| `educational_attainments` | 5 |
| `roles` | 2 |
| `statuses` | 5 |
| `curriculums` | 1 |
| `grade_levels` | 4 |
| `subjects` | 8 |

The subject-name capacity was corrected to `VARCHAR(100)` so names such as `Technology and Livelihood Education` are stored without truncation.

Reference data intentionally excludes operational records such as real schools, users, students, classes, assessments, answers, scans, synchronizations, and audit events.

## Transactional Workflow Test

The following complete relationship path was inserted inside one transaction:

```text
Separate school/user/student addresses
-> school profile
-> principal and teacher users
-> academic year and term period
-> grade level and section
-> student, class, class assignment, and class list
-> curriculum root tag, competency, skill, and intervention
-> test, test part, questions, answer keys, and mappings
-> retained OMR scan sessions and raw detections
-> teacher-verified test result, selected/rejected scan links, and student answers
-> generated intervention result
-> sync batch and sync item
-> auth session, login attempt, and audit log
```

Expected and observed assertion results:

| Assertion | Result |
| --- | ---: |
| Total score | 1.00 |
| Maximum score | 2.00 |
| Evaluated answers | 2 |
| Correct answers | 1 |
| OMR detections | 2 |
| Retained scan links | 2 |
| Selected scan links | 1 |
| Duplicate selected scan accepted | 0 |
| Generated interventions | 1 |
| Synchronization items | 1 |
| Authentication sessions | 1 |
| Login attempts | 1 |
| Audit events | 1 |

The transaction ended with `ROLLBACK`. Final validation confirmed zero remaining smoke-test schools, test results, result-scan links, scan sessions, and sync batches. Only approved reference/master data remains in V2.

## August 9 OMR Recapture Migration

- Backup created before migration: `docs/backups/performance_assessment_v2_pre_recapture_2026-08-09.sql`.
- Migration applied only to local `performance_assessment_v2_db`: `docs/migrations/V2_002_test_result_scans.sql`.
- Removed the direct `test_results.scan_session_id` relationship.
- Added `test_result_scans` to retain selected, superseded, and rejected scan captures.
- Enforced one result per scan and at most one selected scan per result through unique constraints and a generated-column guard.
- Re-ran the transactional smoke test successfully and confirmed that all operational test rows were rolled back.
- The working V1 database, deployed TiDB database, Spring Boot runtime, React frontend, and mobile SQLite database were not modified.

## Files

- `docs/performance_assessment_v2_schema.sql`: non-destructive V2 database and table definitions
- `docs/performance_assessment_v2_reference_seed.sql`: idempotent reference/master data
- `docs/performance_assessment_v2_smoke_test.sql`: rollback-based relational workflow test
- `docs/migrations/V2_002_test_result_scans.sql`: local V2 recapture-lineage migration
- `docs/Data Dictionary CAP2_v9.docx`: authoritative V9 recapture-retention addendum

## Migration Boundary

This validation confirms that the proposed V2 schema is internally executable and supports the sample end-to-end data path. It does **not** confirm that the current Spring Boot repositories, DTOs, services, REST responses, React frontend, React Native SQLite schema, or deployed TiDB database already use V2.

Before V2 becomes the active system database:

1. Freeze the V2 schema and API/sync contract.
2. Map current V1 tables and columns to V2.
3. Update and test backend repositories, DTOs, services, RBAC, validation, analytics, OMR, sync, and audit behavior.
4. Update mobile SQLite and sync contracts for the approved V2 subset.
5. Update frontend API consumers after backend contracts stabilize.
6. Rehearse migration and rollback using disposable local and TiDB staging databases.
7. Run full acceptance tests before switching production clients.

The existing local and TiDB databases must remain intact until those steps pass.
