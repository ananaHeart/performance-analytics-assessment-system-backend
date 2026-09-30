# V3 Data Migration Runbook

This procedure rebuilds and loads only the isolated local
`performance_assessment_v3_db`. It never writes to `performance_assessment_v2_db`,
V1, TiDB, React, or Mobile SQLite.

## Approved Order

1. Back up the current V3 database if it contains new V3-only operational data.
2. Rebuild the V3 schema from the canonical 60-table baseline.
3. Load stable reference data and the validated OMR template.
4. Run the read-only V2 data preflight.
5. Migrate V2 operational data.
6. Run data reconciliation.
7. Run the 60-table structural smoke test.
8. Apply the approved dynamic answer-sheet schema and validated A4 region seed.
9. Run the 66-table dynamic and hardening smoke tests.

```powershell
& 'C:\xampp2\mysql\bin\mysql.exe' -uroot `
  -e "source D:/CAPSTONE_2/backend/assessment/docs/performance_assessment_v3_schema.sql;"

& 'C:\xampp2\mysql\bin\mysql.exe' -uroot `
  -e "source D:/CAPSTONE_2/backend/assessment/docs/migrations/v3/V3_004_reference_seed.sql;"

& 'C:\xampp2\mysql\bin\mysql.exe' -uroot `
  -e "source D:/CAPSTONE_2/backend/assessment/docs/migrations/v3/V3_010_v2_data_preflight.sql;"

& 'C:\xampp2\mysql\bin\mysql.exe' -uroot `
  -e "source D:/CAPSTONE_2/backend/assessment/docs/migrations/v3/V3_011_v2_data_migration.sql;"

& 'C:\xampp2\mysql\bin\mysql.exe' -uroot `
  -e "source D:/CAPSTONE_2/backend/assessment/docs/migrations/v3/V3_012_data_reconciliation.sql;"

& 'C:\xampp2\mysql\bin\mysql.exe' -uroot `
  -e "source D:/CAPSTONE_2/backend/assessment/docs/migrations/v3/V3_005_smoke_test.sql;"

& 'C:\xampp2\mysql\bin\mysql.exe' -uroot `
  -e "source D:/CAPSTONE_2/backend/assessment/docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql;"

& 'C:\xampp2\mysql\bin\mysql.exe' -uroot `
  -e "source D:/CAPSTONE_2/backend/assessment/docs/migrations/v3/V3_007_validated_a4_template_regions_DRAFT.sql;"

& 'C:\xampp2\mysql\bin\mysql.exe' -uroot `
  -e "source D:/CAPSTONE_2/backend/assessment/docs/migrations/v3/V3_008_dynamic_answer_sheet_smoke_test_DRAFT.sql;"

& 'C:\xampp2\mysql\bin\mysql.exe' -uroot `
  -e "source D:/CAPSTONE_2/backend/assessment/docs/migrations/v3/V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql;"

& 'C:\xampp2\mysql\bin\mysql.exe' -uroot `
  -e "source D:/CAPSTONE_2/backend/assessment/docs/migrations/v3/V3_009_dynamic_answer_sheet_contract_hardening_smoke_test_DRAFT.sql;"
```

## Expected Result

- Preflight: no `ERROR`; one warning currently reports 9 learner/year duplicate groups.
- Reconciliation: 26 checks, all `PASS`.
- Pre-delta structural smoke test: `PASS`, 60 tables.
- Final dynamic smoke tests: `PASS`, 66 tables, 157 foreign keys, 80 checks,
  and 96 unique constraints.
- V2 table count remains 41 and V2 source row counts remain unchanged.

Do not run `V3_009_dynamic_answer_sheet_negative_fixtures_DRAFT.sql` against the
real local database. It is destructive test evidence intended only for a fresh
disposable validation database.

## Retry And Rollback

The safe rollback boundary is the entire isolated V3 database. Do not partially
delete rows by hand after a failed migration because foreign keys and migrated
audit history are interdependent.

To retry before V3 integration begins, restore the complete timestamped backup or
rerun the canonical schema command. The canonical schema command replaces tables
only inside `performance_assessment_v3_db`; rerun every approved step above in
order. Never point these files at V2 or production TiDB.

Once V3 clients start creating V3-only data, first create a timestamped V3 backup;
do not rebuild without explicit owner approval and a tested restore procedure.

The August 30 controlled application, verified backup hash, temporary restore
test, rollback event, and final readiness results are recorded in
`V3_LOCAL_DYNAMIC_MIGRATION_EXECUTION_2026-08-30.md`.
