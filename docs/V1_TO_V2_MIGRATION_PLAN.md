# V1 to V2 Migration and Rollback Plan

**Prepared:** August 9, 2026  
**Source:** `performance_assessment_db`  
**Target:** `performance_assessment_v2_db`  
**Production/TiDB status:** Not executed

## Safety Rule

The working V1 local database and TiDB database remain untouched. Migration is rehearsed only against the separate local V2 database or disposable copies. The supplied migration script ends with `ROLLBACK` until an approved operator intentionally changes it to `COMMIT` after reviewing all assertions.

## Verified Starting Point

- V1 local database: 19 tables and current operational/demo records.
- V2 local database: 38 tables, 56 foreign keys, approved reference data, and zero operational rows after the August 9 recapture-lineage migration.
- Dated V2 backup: `docs/backups/performance_assessment_v2_db_2026-08-09.sql`.
- Pre-recapture migration backup: `docs/backups/performance_assessment_v2_pre_recapture_2026-08-09.sql`.
- Backup restore check: passed in a disposable database and then removed.

The local V2-only structural correction is versioned as `docs/migrations/V2_002_test_result_scans.sql`. It removes the direct result-to-scan foreign key and adds `test_result_scans` so recaptures remain auditable. This migration has not been applied to V1 or TiDB.

## Known Semantic Gaps

These are not SQL syntax problems and must not be hidden:

1. V1 stores a comma-separated answer key but not actual question text/options. Migrated legacy questions require clearly labeled placeholders until manually backfilled.
2. V1 passwords are not guaranteed to be secure hashes. Migrated accounts must be disabled/pending and require a controlled password reset before V2 login.
3. V1 has no user/student residential address or verified contact number. Migration creates explicit placeholder address/contact values that require review.
4. All current V1 tests have a null grading period. Historical tests require a controlled `Legacy Unassigned` term or manual term assignment.
5. V1 has no scan session or OMR detection. Legacy results must remain `manual`; they must never be presented as scanned results.
6. V1 result rows reference `student_id`; V2 uses `class_list_id`. The migration resolves membership through the test class, section, and academic year.
7. V1 `test_item_result` stores correctness but not a guaranteed final selected option. `raw_answers` is used only when its item order can be deterministically resolved.
8. V1 competency ranges are expanded into V2 question-to-skill rows. V2 no longer stores range boundaries.
9. Existing V1 intervention masters are not linked to a skill. They need a reviewed skill mapping before migration.
10. One observed user name contains an encoding replacement character and must be corrected before production migration.

## Migration Order

1. Run `v1_to_v2_preflight.sql` and resolve all blocking counts.
2. Restore the dated V2 backup into a disposable rehearsal database.
3. Run the rollback-by-default data migration script.
4. Review in-transaction assertion counts and sample records.
5. Run the V2 smoke test and API-contract tests against the rehearsal database.
6. Re-run the migration with `COMMIT` only in an approved staging database.
7. Update and test Spring Boot V2 repositories and versioned endpoints.
8. Update mobile SQLite and mobile V2 sync.
9. Update React frontend consumers.
10. Deploy to a separate TiDB staging database, run acceptance tests, and rehearse rollback.
11. Switch production only after explicit approval.

## Rollback Strategy

Because V2 is a separate database, the safest rollback is a connection-profile switch back to V1. No reverse destructive migration is required.

- Before cutover: restore the dated V2 backup or recreate the isolated V2 database.
- During a failed rehearsal: the migration's final `ROLLBACK` removes all inserted operational rows.
- After staging cutover: stop V2 writes, preserve a V2 incident backup, switch clients/backend to V1, and investigate.
- Never drop, truncate, or overwrite V1 as part of a V2 rollback.

`v1_to_v2_rollback.sql` contains verification queries and an optional target-only cleanup block. The destructive cleanup remains commented and must be run only against a confirmed disposable V2 database.

## Client Impact

| Component | Required V2 work |
| --- | --- |
| Backend | Versioned repositories/DTOs/services, UUID idempotency, RBAC, validation, derived scores, audit logs |
| Mobile | Fixed-template scan data, teacher verification, V2 SQLite subset, UUIDs, question-based answers, batch sync |
| Frontend | Consume stabilized V2 responses, display verified scores/analytics/interventions, preserve role boundaries |
| Database | Local rehearsal, migration assertions, TiDB staging compatibility, backup and rollback evidence |

## Acceptance Gate

V2 cannot become active until all of these pass:

- Registration/login/session/role/ownership validation
- SF1 import, class assignment, and duplicate prevention
- Assessment/question/answer-key/skill setup
- Fixed-template OMR capture and teacher verification
- Offline save, retry-safe batch sync, and partial failure handling
- Derived result, item, skill mastery, and intervention calculations
- Export/report correctness
- Audit and safe error behavior
- Local, TiDB staging, backend, mobile, and frontend integration tests
