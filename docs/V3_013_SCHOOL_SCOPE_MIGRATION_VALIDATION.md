# V3_013 School-Scoped Sections Validation

**Date:** 2026-09-02  
**Target:** Local `performance_assessment_v3_db` only  
**Migration:** `docs/migrations/v3/V3_013_school_scoped_sections.sql`

## Purpose

The original `sections` identity was global by grade and section name. That
could cause two schools to share the same section record indirectly because
`academic_years` and `classes` are intentionally curriculum/cohort records and
do not carry `school_id` themselves.

`V3_013` makes each section explicitly owned by one school:

- adds non-null `sections.school_id`;
- adds `fk_sections_school` to `school_profiles.school_id`;
- replaces global uniqueness with
  `UNIQUE(school_id, grade_level_id, section_name)`;
- preserves an explicit `idx_sections_grade_level` index for the existing
  grade-level foreign key.

## Controlled Execution

1. Confirmed local MySQL source database: `performance_assessment_v3_db`.
2. Preflight result: `cross_school_sections=0`, `unused_sections=0`.
3. First disposable run correctly exposed that the old unique index supported
   `fk_sections_grade_level`; no real database was changed.
4. Added the required dedicated grade-level index to the migration and
   canonical V3 schema.
5. Applied and reran the migration in disposable database
   `performance_assessment_v3_validation_20260902_school_scope`.
6. Verified the rerun guard rejected a second application without changing
   data.
7. Backed up the real V3 database to:

```text
docs/backups/performance_assessment_v3_db_pre_V3_013_20260902_150421.sql
```

8. Applied the validated migration to the real local V3 database.
9. Removed the disposable database after validation.

## Result

| Check | Result |
|---|---:|
| Tables | 66 |
| Foreign keys | 158 |
| Check constraints | 80 |
| Unique constraints | 96 |
| `sections.school_id` non-null column | 1 |
| `fk_sections_school` | 1 |
| school-scoped section unique rule | 1 |
| explicit grade-level index | 1 |
| section rows with null school | 0 |
| existing section rows retained | 5 |

No V1, V2, Mobile SQLite, React, or TiDB database was migrated.

## Runtime Baseline

The V3 Spring readiness configuration now expects:

```text
66 tables / 158 foreign keys / 80 checks / 96 unique constraints
```

The additional readiness rule checks the school-scoped section column,
foreign key, and uniqueness contract before a V3 runtime is considered ready.

## Application Verification

- Focused school setup, SF1, and readiness tests: 30 passed, 0 failures,
  0 errors, 0 skipped.
- Full Maven regression suite: 177 passed, 0 failures, 0 errors, 0 skipped.
- Temporary V3 runtime: started successfully on port `8082`.
- Live readiness: HTTP 200, `ready=true`, 17 of 17 checks passed.
- Frontend CORS preflight from `http://localhost:5173`: HTTP 200.
- The temporary verification runtime was stopped after the checks.
