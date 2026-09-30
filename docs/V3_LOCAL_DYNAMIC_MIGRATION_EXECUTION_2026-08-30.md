# V3 Local Dynamic Migration Execution

**Execution date:** August 30, 2026  
**Target:** Local `performance_assessment_v3_db` only  
**Result:** PASS  
**Final baseline:** 66 tables / 157 foreign keys / 80 checks / 96 unique constraints

## Authorized Scope

Owner and Mobile approval authorized backup and controlled local application of
`V3_006` through `V3_009`. It did not authorize V2 changes, Mobile SQLite
migration, API switching, TiDB deployment, scanner replacement, or activation of
unvalidated templates.

## Backup And Restore Point

- Backup file:
  `docs/backups/performance_assessment_v3_db_pre_V3_006_009_20260830_184952_retry1.sql`
- Size: 456,127 bytes
- SHA-256:
  `C2F4DB5736E3B2EDFD4C85878EB94BCFF076AE421C93DD04C31439F11CECA5C4`
- Dump completion footer: present
- Restore test: PASS in temporary database
  `performance_assessment_v3_restore_check_20260830_184952`
- Restored table count: 60
- Common pre-existing tables with changed row counts after migration: 0 of 60
- Temporary restore database: removed after verification

The first dump attempt included MariaDB events and stopped because the local
event scheduler is disabled. The incomplete file
`performance_assessment_v3_db_pre_V3_006_009_20260830_184952.sql` must not be
restored. The verified retry excludes events and includes routines, triggers,
and binary-safe data.

## Applied Order

1. Pre-migration `V3_005_smoke_test.sql`: PASS, 60 tables
2. `V3_006_dynamic_answer_sheet_schema_DRAFT.sql`: applied
3. `V3_007_validated_a4_template_regions_DRAFT.sql`: applied
4. `V3_008_dynamic_answer_sheet_smoke_test_DRAFT.sql`: PASS
5. `V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql`: applied
6. `V3_009_dynamic_answer_sheet_contract_hardening_smoke_test_DRAFT.sql`: PASS

The executable V3_009 negative fixtures remain disposable-only. Their approved
result is 11 of 11 rejected with zero persisted fixture rows; they were not run
against the real database.

## Controlled Rollback Event

The first application attempt stopped during V3_008 because the original smoke
assertion rejected every objective `answer_verifications` row. The verified
backup was immediately restored. Post-restore checks confirmed:

- Real V3: 60 tables
- Dynamic tables: 0
- V2: 41 tables

The audit found 90 explicitly tagged historical V2 objective finalization rows:

- 80 `legacy_v2_finalization` rows with no answer-value change
- 10 `legacy_v2_correction` rows
- One historical correction changed a multiple-mark result to option A

No historical score or audit row was deleted or rewritten. V3_008 was narrowed
to allow only `verification_action = 'finalized'` rows whose reason is explicitly
`legacy_v2_finalization` or `legacy_v2_correction`. Any new or non-legacy MC/TF
answer-verification row still fails validation. V3 services must continue to
prevent teachers from replacing objective answers.

## Final SQL Result

- Central tables: 66
- Foreign keys: 157
- Check constraints: 80
- Unique constraints: 96
- Active paper sizes: 3
- Validated fixed-template regions: 15
- Active OMR templates: 1
- V2 tables: 41
- Common 60-table row-count changes: 0

Only `OMR-A4-10-MC-CTX-V2` is active and physically validated. US Letter, US
Legal, mixed-type, and written-response template geometries remain unavailable.

## Spring And Regression Validation

- Profile: `v3`
- Temporary port: 8082
- Endpoint: `GET /api/v3/system/readiness`
- HTTP result: 200
- `ready`: `true`
- Readiness checks: 16 of 16 passed
- Maven suites: 16
- Maven tests: 108
- Failures: 0
- Errors: 0
- Skipped: 0
- Temporary Spring server: stopped after validation

Execution logs are under `output/v3-migration/20260830_184952`.

## Current Boundary

The local central V3 database is structurally ready for the next backend slice.
No V3 functional assessment/download/sync API, Mobile SQLite migration, React
switch, TiDB migration, or unvalidated template activation is implied by this
database result.
