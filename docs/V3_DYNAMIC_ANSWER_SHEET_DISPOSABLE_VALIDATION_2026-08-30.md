# V3 Dynamic Answer-Sheet Disposable Validation

Validation date: 2026-08-30

> Historical isolation note: this report records the disposable validation before
> owner/Mobile approval. The package was subsequently applied to local central V3;
> see `V3_LOCAL_DYNAMIC_MIGRATION_EXECUTION_2026-08-30.md` for current status.

## Scope

This validation tested the additive dynamic answer-sheet package in a uniquely
named disposable MariaDB database. It did not apply the package to the current
`performance_assessment_v3_db`, V2, TiDB, React, or Mobile SQLite.

## Backup And Isolation

- Current V3 backup:
  `backups/performance_assessment_v3_db_pre_dynamic_20260830_112625.sql`
- Backup size: 455,888 bytes
- Backup SHA-256:
  `588FAF57A32DF0000FDD9D219A5D82E29E9352B3A874EE2C4E93428A576D4336`
- Dump completion marker: `2026-08-30 11:26:26`
- Disposable database:
  `performance_assessment_v3_validation_20260830_112725`
- Validation evidence:
  `../output/v3-validation/20260830_112725`

The canonical 60-table schema was copied into the disposable database by
replacing only the database name in temporary validation copies. The source SQL
files were not rewritten. `V3_000_v2_schema_snapshot.sql` was not executed because
it is a V2 snapshot; the canonical V3 schema is the approved clean baseline.

## Execution Order And Results

1. `performance_assessment_v3_schema.sql`: imported successfully.
2. `V3_004_reference_seed.sql`: imported successfully.
3. `V3_005_smoke_test.sql`: `PASS`, 60 tables.
4. `V3_006_dynamic_answer_sheet_schema_DRAFT.sql`: applied successfully.
5. `V3_007_validated_a4_template_regions_DRAFT.sql`: applied successfully.
6. `V3_008_dynamic_answer_sheet_smoke_test_DRAFT.sql`: `PASS`, 66 tables.

Final disposable metadata:

| Check | Result |
|---|---:|
| Tables | 66 |
| Primary keys | 66 |
| Foreign keys | 154 |
| Check constraints | 68 |
| Unique constraints | 96 |
| Paper sizes | 3 |
| Validated legacy regions | 15 |

The six added tables are:

- `answer_sheet_pages`
- `answer_sheet_regions`
- `answer_sheet_versions`
- `omr_template_regions`
- `paper_sizes`
- `scan_pages`

## Geometry Result

- A4: `595.276 x 841.890` points
- US Letter: `612.000 x 792.000` points
- US Legal: `612.000 x 1008.000` points
- Active validated template: `OMR-A4-10-MC-CTX-V2`
- Coordinate origin: `pdf_bottom_left`
- Required print scale: `100.00%`
- Registration markers: 4, each `15 x 15` points
- Objective rows: 10, options A-D
- Bubble X centers: `79`, `103`, `127`, `151` points
- First item Y center: `604.334` points
- Last item Y center: `306.334` points
- Bubble radius: `6.4` points

Letter, Legal, mixed-type, and written-response template geometries remain
unseeded and unavailable until each layout passes PDF and physical scanner
validation.

## Source Database Safety Check

After disposable validation:

- `performance_assessment_v2_db`: 41 tables
- `performance_assessment_v3_db`: 60 tables
- New dynamic tables in the real V3 database: 0
- Disposable validation database: 66 tables

Therefore, the current V3 runtime is still the verified 60-table baseline. The
dynamic package is technically validated but remains unapplied pending final DTO,
QR, retention, upload-limit, and Mobile shadow-migration contract approval.
