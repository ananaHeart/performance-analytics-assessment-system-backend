# V3 Dynamic Answer-Sheet Contract Hardening Validation

Validation date: 2026-08-30

> Historical isolation note: this report records the hardening validation before
> owner/Mobile approval. The package was subsequently applied to local central V3;
> see `V3_LOCAL_DYNAMIC_MIGRATION_EXECUTION_2026-08-30.md` for current status.

## Verdict

`PASS` in a fresh disposable MariaDB database. The V3_009 contract-hardening
draft was validated on top of the complete 60-to-66-table dynamic answer-sheet
chain. It was not applied to the current local V3 database, V2, TiDB, React, or
Mobile SQLite.

## Disposable Environment

- Database: `performance_assessment_v3_validation_20260830_182425`
- Generated validation copies: `../output/v3-validation/20260830_182425`
- Current real V3 database during the isolation check: 60 tables
- Current V2 database during the isolation check: 41 tables

The validation copies changed only the target database name. The canonical and
migration source files remained the authoritative inputs.

## Execution Chain

1. `performance_assessment_v3_schema.sql`
2. `V3_004_reference_seed.sql`
3. `V3_005_smoke_test.sql`: `PASS`, 60 tables
4. `V3_006_dynamic_answer_sheet_schema_DRAFT.sql`
5. `V3_007_validated_a4_template_regions_DRAFT.sql`
6. `V3_008_dynamic_answer_sheet_smoke_test_DRAFT.sql`: `PASS`, 66 tables
7. `V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql`
8. `V3_009_dynamic_answer_sheet_contract_hardening_smoke_test_DRAFT.sql`:
   `PASS`
9. `V3_009_dynamic_answer_sheet_negative_fixtures_DRAFT.sql`: `PASS`, 11
   expected failures and zero persisted fixture rows

The MariaDB client was run with `--abort-source-on-error` for the final rebuild
so an SQL failure could not be hidden behind a successful process exit code.

## Final Metadata

| Check | Result |
|---|---:|
| Tables | 66 |
| Primary keys | 66 |
| Foreign keys | 157 |
| Check constraints | 80 |
| Unique constraints | 96 |
| Exact paper-size records | 3 |
| Physically validated fixed A4 regions | 15 |
| Active validated fixed templates | 1 |
| Active Letter/Legal templates | 0 |

The only active physically validated template remains
`OMR-A4-10-MC-CTX-V2`. Dynamic A4, Letter, Legal, written-response, and mixed
templates remain unavailable pending their own generated fixtures and physical
scanner validation.

## V3_009 Additions

- `questions.expected_response_count`
- `answer_sheet_regions.expected_response_count_snapshot`
- `answer_sheet_regions.response_line_count`
- expanded `answer_attachments.attachment_type` evidence variants
- immutable normalized-to-original evidence lineage through
  `source_answer_attachment_id`
- retention policy, deadline, hold, purge, actor, reason, and failure metadata
- generated-page and captured-page QR payload limits using UTF-8 byte length
- exact lowercase SHA-256 checks for stored QR payload hashes

The table count stays at 66 because V3_009 hardens existing V3_006 tables rather
than introducing another table.

## Negative Tests

The checked-in disposable fixture script correctly rejected nine database
constraint violations with SQLSTATE `23000`:

1. Question expected-response count of zero.
2. Enumeration region with three expected responses but only two response lines.
3. Normalized page evidence without an original source attachment.
4. Retention hold without reason, actor, and timestamp.
5. Purged evidence without required purge audit fields.
6. Generated-page QR payload longer than 256 bytes.
7. Generated-page QR payload with an incorrect digest.
8. Captured-page QR payload longer than 256 bytes.
9. A non-normalized attachment carrying a source attachment ID.

It also exercised the cross-row service contract and rejected two invalid
lineages with SQLSTATE `45000`:

1. A normalized page sourced from `answer_crop` instead of `original_page`.
2. A normalized page sourced from a different scan page.

All 11 cases are executable in
`V3_009_dynamic_answer_sheet_negative_fixtures_DRAFT.sql`. The parent data and
invalid attempts run in one transaction, the transaction is rolled back, and
the final persisted fixture-row count was zero.

## MariaDB Constraint Boundary

MariaDB cannot enforce source-row type, page ownership, or arbitrary cycle
traversal in a portable row `CHECK`. The database now enforces the exclusive
shape rule: only `normalized_page` may have a source, and every
`normalized_page` must have one. `V3AnswerAttachmentLineageValidator` provides
the cross-row checks for source type, same page/session/owner, direct self, and
cycles. Its eight focused tests pass. The future attachment-write service must
call this validator before persistence.

## Spring Runtime Verification

The `v3` Spring profile was started temporarily on port `18082` against the
fresh disposable database. `GET /api/v3/system/readiness` returned HTTP 200,
`ready = true`, and all 16 checks passed, including:

- exact 66/157/80/96 metadata counts
- all six dynamic tables
- all 16 V3_009 columns and 15 named hardening constraints
- the three exact paper-size seeds
- 15 regions for `OMR-A4-10-MC-CTX-V2`
- exactly one approved active template and zero unapproved active templates

The temporary Spring process was stopped after the request. The full Maven suite
then passed: 108 tests, 0 failures, 0 errors, and 0 skipped.

## Isolation Result

- `performance_assessment_v2_db`: 41 tables
- `performance_assessment_v3_db`: 60 tables
- Dynamic tables present in real V3: 0
- Disposable hardened database: 66 tables

The V3_009 package is disposable-validated but still a draft. Mobile contract
acceptance is required before applying it to local V3. Production Mobile SQLite,
React, TiDB, and the active V2 flow must remain unchanged during that approval.
