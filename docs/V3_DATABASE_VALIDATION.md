# V3 Database Validation

Validation date: 2026-08-30  
Database engine: MariaDB 10.4.32  
Validated database: `performance_assessment_v3_db`

> Historical baseline note: this report records the original validated 60-table
> V3 build. The approved V3_006 through V3_009 package was later applied locally,
> producing the current 66/157/80/96 baseline. See
> `V3_LOCAL_DYNAMIC_MIGRATION_EXECUTION_2026-08-30.md` for current evidence.

## Result

The isolated V3 central-database schema has been created and validated locally.
It is a separate database and does not replace `performance_assessment_v2_db`.

| Check | Result |
|---|---:|
| Central tables | 60 |
| Foreign keys | 129 |
| Check constraints | 48 |
| Unique constraints | 78 |
| Total V3 rows after migration | 1,720 |
| V2 source rows reconciled | 1,375 |
| V2 tables after validation | 41 |
| V3 smoke test | PASS |
| V2-to-V3 data reconciliation | 26/26 PASS |

## Authoritative Files

- `performance_assessment_v3_schema.sql`: complete fresh V3 schema baseline
- `migrations/v3/V3_004_reference_seed.sql`: idempotent stable reference seed
- `migrations/v3/V3_005_smoke_test.sql`: non-destructive structural smoke test
- `migrations/v3/V3_010_v2_data_preflight.sql`: read-only source and target readiness checks
- `migrations/v3/V3_011_v2_data_migration.sql`: controlled operational-data migration
- `migrations/v3/V3_012_data_reconciliation.sql`: row, ownership, UUID, score, scan, and sync reconciliation
- `migrations/v3/V3_000_v2_schema_snapshot.sql`: V2 structure snapshot used during design
- `migrations/v3/V3_001_core_domain.sql`: V2-shape to V3 core structural changes
- `migrations/v3/V3_002_assessment_capture.sql`: OMR, mixed-response, and verification changes
- `migrations/v3/V3_003_security_sync_intervention.sql`: sync, intervention, OTP, and MFA changes

The canonical schema plus the reference seed are the normal fresh-install path.
`V3_001` through `V3_003` document and test the structural transition from the
captured V2 shape; they are not an operational-data migration.

## Added Or Replaced Central Tables

The V3 baseline adds these tables relative to the V2 runtime snapshot:

- `accepted_answers`
- `answer_attachments`
- `answer_rubric_scores`
- `answer_verifications`
- `mfa_authentication_challenges`
- `mfa_recovery_codes`
- `omr_templates`
- `part_skill_mappings` (replaces `mappings`)
- `performance_rule_sets`
- `question_options`
- `question_types`
- `rubrics`
- `rubric_criteria`
- `scan_verifications`
- `sf1_imports`
- `sf1_import_items`
- `student_intervention_cases`
- `student_intervention_updates`
- `test_assignments`
- `user_mfa_factors`
- `verification_challenges` (replaces `email_verification_otps`)

No `term_one`, `term_two`, `term_three`, or `term_four` tables were created.
The four grading periods remain rows in `term_periods`.

## Confirmed Rules

- Repeated SF1 file hashes are indexed for warnings but are not uniquely blocked.
- Every SF1 row retains a created, updated, unchanged, conflict, or invalid outcome.
- A learner may have only one active `class_lists` membership per academic year.
- Tests are reusable content; class delivery is represented by `test_assignments`.
- Assessment availability uses `open_at`, `close_at`, and an explicit lifecycle status.
- `part_skill_mappings` stores part-relative item ranges and a normalized `skill_id`.
- Five question types are preloaded: Multiple Choice, True/False, Identification,
  Enumeration, and Essay.
- Multiple Choice and True/False require verification but set
  `allows_teacher_answer_edit = false`.
- Objective scanner evidence is retained in `omr_detections`; whole-sheet decisions
  are append-only in `scan_verifications`.
- Non-objective transcription, OCR review, manual scoring, reopen, and correction
  history are append-only in `answer_verifications`.
- The validated `OMR-A4-10-MC-CTX-V2` geometry is preloaded for mobile scanner
  version `1.0`; the JSON contains four markers, ten rows, and A-D bubble positions.
- Sync batches support `partial_success`; `sync_uuid`, result UUIDs, and
  `UNIQUE(sync_id, result_uuid)` protect retry idempotency.
- Four active, versioned system performance rule sets store the current
  80/60/40 student-score, skill-mastery, class-mastery, and intervention bands
  as validated JSON instead of requiring hard-coded V3 analytics thresholds.
- Authenticator-app support uses encrypted TOTP-factor metadata and hashed recovery
  codes. No plaintext authenticator secret or recovery code is stored.
- Only `updated_at` columns auto-update. Scan, detection, score, and verification
  evidence timestamps remain immutable.

## Reference Data Loaded

- 2 roles
- 6 account statuses
- 2 genders
- 5 suffixes
- 9 majors
- 5 educational attainments
- 4 grade levels
- 8 subjects
- 5 question types
- 1 physically validated OMR template
- 4 active system performance rule sets

## Operational Data Migrated

The isolated local V3 database now includes the reconciled V2 operational data:

| Data | V3 rows |
|---|---:|
| Users | 8 |
| Students | 13 |
| Class memberships | 27 |
| Class assignments | 8 |
| Tests / initial test assignments | 7 / 7 |
| Test parts / questions | 10 / 57 |
| Normalized question options | 228 |
| Answer keys / part-skill mappings | 57 / 57 |
| Test results / student answers | 9 / 90 |
| Scan sessions / raw OMR detections | 9 / 90 |
| Scan / answer verification history | 9 / 90 |
| Syncs / sync items | 2 / 9 |
| Audit logs / auth sessions / login attempts | 281 / 158 / 171 |
| Notifications / verification challenges | 7 / 5 |

The 27 memberships include all legacy rows for audit and result traceability.
Nine learners had multiple memberships in one academic year. The migration kept
one `enrolled` membership per learner/year and marked the other legacy rows as
`transferred`; there are zero active-membership duplicates after migration.

Score reconciliation is exact: 9 result UUIDs were preserved, aggregate earned
score remains `49.00`, aggregate maximum score remains `90.00`, and all 90
student answers retain an aggregate `49.00` earned points.

## Rebuild And Validate

The canonical schema rebuild is destructive only to the isolated V3 database.
Back up V3 data before rerunning it after operational migration begins.

```powershell
& 'C:\xampp2\mysql\bin\mysql.exe' -uroot `
  -e "source D:/CAPSTONE_2/backend/assessment/docs/performance_assessment_v3_schema.sql;"

& 'C:\xampp2\mysql\bin\mysql.exe' -uroot performance_assessment_v3_db `
  -e "source D:/CAPSTONE_2/backend/assessment/docs/migrations/v3/V3_004_reference_seed.sql;"

& 'C:\xampp2\mysql\bin\mysql.exe' -uroot performance_assessment_v3_db `
  -e "source D:/CAPSTONE_2/backend/assessment/docs/migrations/v3/V3_010_v2_data_preflight.sql;"

& 'C:\xampp2\mysql\bin\mysql.exe' -uroot performance_assessment_v3_db `
  -e "source D:/CAPSTONE_2/backend/assessment/docs/migrations/v3/V3_011_v2_data_migration.sql;"

& 'C:\xampp2\mysql\bin\mysql.exe' -uroot performance_assessment_v3_db `
  -e "source D:/CAPSTONE_2/backend/assessment/docs/migrations/v3/V3_012_data_reconciliation.sql;"

& 'C:\xampp2\mysql\bin\mysql.exe' -uroot performance_assessment_v3_db `
  -e "source D:/CAPSTONE_2/backend/assessment/docs/migrations/v3/V3_005_smoke_test.sql;"
```

Expected final output:

```text
validation_status  central_table_count  validated_database
PASS               60                   performance_assessment_v3_db
```

## Still Pending

These are not part of the completed database baseline:

- Spring Boot V3 business repositories, services, validation, RBAC, and APIs
- React V3 endpoint integration
- Mobile SQLite V3 migration and shared naming alignment
- mobile download/upload contract implementation
- TiDB V3 staging or production deployment
- SMS provider integration
- TOTP enrollment/login service and UI
- physically validated dynamic page-template versions for A4, US Letter, and US
  Legal, including objective and written-response region capacities
- OCR/manual-scoring runtime and private attachment storage
- analytics, Student 360, intervention, and export runtime using V3 records

V2 remains the active application database until these integration stages are
implemented and accepted.

The approved target requirements and schema/API impact for dynamic 5-or-more-item,
multi-page answer sheets are documented in
`V3_DYNAMIC_ANSWER_SHEET_DESIGN_CONTRACT.md`. They are not included in this
completed 60-table validation result.

The additive package in `V3_006` through `V3_009` passed a fresh full-chain
MariaDB rebuild on 2026-08-30. The hardened disposable result was 66 tables, 157
foreign keys, 80 check constraints, 96 unique constraints, three exact paper-size
records, and 15 regions for `OMR-A4-10-MC-CTX-V2`. Eight invalid count, region,
evidence, retention, purge, and QR cases were rejected. It remains unapplied and
therefore does not change this verified 60-table runtime result. V2 remained at
41 tables and real V3 remained at 60 during the isolation check. See
`V3_DYNAMIC_ANSWER_SHEET_DISPOSABLE_VALIDATION_2026-08-30.md` and
`V3_DYNAMIC_ANSWER_SHEET_HARDENING_VALIDATION_2026-08-30.md` for the execution
order, evidence paths, boundary tests, and source-database safety checks.

## Spring Boot Migration Foundation

The first isolated backend migration slice is implemented:

- `v3` profile targets only `performance_assessment_v3_db` and defaults to port
  `8082`.
- Startup baseline validation rejects the wrong database, wrong table count, or
  incomplete question-type, OMR-template, and performance-rule seeds.
- `GET /api/v3/system/readiness` exposes a read-only baseline report.
- V1/V2 endpoints are denied while the V3 profile is active.
- Local runtime check passed with 60 tables, 129 foreign keys, 48 checks, 78 unique
  constraints, five active question types, one active OMR template, and four active
  performance rule sets.
- Full Maven suite passed: 83 tests, 0 failures, 0 errors, 0 skipped.

This foundation does not switch React, Mobile SQLite, V2 runtime, or TiDB to V3.
