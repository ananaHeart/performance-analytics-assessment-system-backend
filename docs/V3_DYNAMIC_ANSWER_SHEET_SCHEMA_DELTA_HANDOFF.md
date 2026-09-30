# V3 Dynamic Answer Sheet Schema Delta Handoff

**Date:** August 30, 2026  
**Status:** Approved, applied, and locally validated through V3_009  
**Previous V3 runtime baseline:** 60 tables  
**Current V3 runtime baseline:** 66 tables

## Scope And Safety Boundary

This package translates the approved dynamic answer-sheet direction into exact
DDL, one physically validated geometry seed, and non-destructive validation SQL.
After disposable validation and explicit owner/Mobile approval, it was applied
only to the local central `performance_assessment_v3_db`. It did not modify V2,
TiDB, Mobile SQLite, Java functional APIs, or frontend code.

Prepared files:

1. `migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql`
2. `migrations/v3/V3_007_validated_a4_template_regions_DRAFT.sql`
3. `migrations/v3/V3_008_dynamic_answer_sheet_smoke_test_DRAFT.sql`
4. `migrations/v3/V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql`
5. `migrations/v3/V3_009_dynamic_answer_sheet_contract_hardening_smoke_test_DRAFT.sql`

The filenames retain the `DRAFT` suffix for migration-history continuity. Their
approved local application is recorded in
`V3_LOCAL_DYNAMIC_MIGRATION_EXECUTION_2026-08-30.md`; they are not authorization
for TiDB, production, or Mobile SQLite migration.

## Six Added Tables

### `paper_sizes`

Central reference data for exact PDF dimensions. Initial codes are `A4`,
`US_LETTER`, and `US_LEGAL`. A paper-size row does not mean a scannable template
for that size already exists.

### `omr_template_regions`

Immutable registration-marker, QR/page-identity, objective-bubble, and written
response slots belonging to one physical `omr_templates` page layout. Coordinates
use PDF points and the template declares `pdf_bottom_left` as its origin.

### `answer_sheet_versions`

One immutable generated PDF/manifest package for an exact `test_assignment_id`,
test content version, and paper size. Regeneration creates another generation;
it does not overwrite a package that may already be printed or downloaded.

### `answer_sheet_pages`

Every physical page in a generated package. Each page has its own UUID, template,
page number, total-page count, QR payload/hash, and geometry hash.

### `answer_sheet_regions`

The authoritative assignment of an actual question to an actual page/region. It
stores global and per-part item numbers so Mobile never guesses coordinates from
display order.

### `scan_pages`

Immutable page-level capture evidence inside a `scan_sessions` aggregate. A
rescan creates another UUID/capture number and links to the page it supersedes.
Only one non-rejected/non-superseded capture may remain current for a session page.

## Existing Table Changes

| Existing table | Additive/compatibility change |
|---|---|
| `omr_templates` | Adds normalized paper size, explicit version, coordinate origin, required 100% print scale, geometry hash, and retirement time. Legacy capability columns remain while mixed templates may leave them null. |
| `scan_sessions` | Adds answer-sheet version and expected/captured page counts. Legacy one-page template fields remain available and nullable for transition. |
| `omr_detections` | Adds captured-page and generated-region links. A generated legacy-only session/question key preserves old duplicate protection, while the new page/region key permits immutable rescan history. |
| `answer_attachments` | Adds page/region and normalized-to-original lineage; original, normalized, crop, and teacher-evidence variants; and retention hold/purge audit metadata. Existing evidence ownership remains valid. |
| `scan_verifications` | Adds optional page scope while retaining whole-session decisions. |
| `questions` | Adds a written-response region-size preset, explicit page-break hint, and optional expected-response count. |
| `answer_sheet_regions` | Adds immutable expected-response-count and ruled-line snapshots for written regions. |
| `answer_sheet_pages` | Enforces the approved 256-byte UTF-8 QR ceiling and exact lowercase SHA-256 payload digest. |
| `scan_pages` | Applies the same QR byte-limit and digest rules to captured page evidence. |
| `answer_rubric_scores` | Adds the maximum-points snapshot used for auditable criterion bounds. |

## Compatibility Decisions

- No existing table or evidence column is dropped.
- Existing one-page scan rows remain valid without generated page-manifest links.
- New multi-page API records must supply the new version/page/region UUIDs.
- Legacy rows remain unique by generated `(legacy_scan_session_id,
  legacy_question_id)`. Page-aware rows are unique by `(scan_page_id,
  answer_sheet_region_id)`, allowing a rescan to preserve another immutable
  detection for the same question without overwriting the first capture.
- `scan_sessions.template_version` becomes nullable because one mixed session may
  contain pages using different template codes/versions.
- Objective MC/TF answers remain teacher-read-only. New V3 objective
  `answer_verifications` rows are prohibited. The smoke test permits only
  explicitly tagged historical V2 `finalized` rows using
  `legacy_v2_finalization` or `legacy_v2_correction`, preserving prior audit
  history without allowing future objective answer replacement. Service-layer
  enforcement remains mandatory.
- Written response crops and rubric scoring remain teacher-evaluated; OpenCV only
  aligns pages and extracts configured regions.
- MariaDB enforces normalized-evidence source presence and its self foreign key,
  but cannot compare the `AUTO_INCREMENT` attachment ID in the required `CHECK`.
  The Java service must reject direct self-references and cyclic attachment
  lineage before save.

## Seed Classification

### Structurally seeded

- `A4`: 595.276 x 841.890 points
- `US_LETTER`: 612.000 x 792.000 points
- `US_LEGAL`: 612.000 x 1008.000 points

### Physically validated and region-seeded

- `OMR-A4-10-MC-CTX-V2`
- Four 15 x 15 point nested-square markers
- One 83 x 83 point QR boundary using error correction M
- Ten exact A-D bubble rows with a 6.4 point radius

### Not seeded or advertised as available

- Dynamic A4 layouts
- US Letter scanner templates
- US Legal scanner templates
- True/False scanner templates
- Identification, Enumeration, or Essay written-region templates
- Mixed-question-type multi-page layouts

Those geometries require generation rules, final compact QR fields, Mobile map
alignment, rendered-PDF review, and physical scanner validation first.

## Intended Disposable Validation Order

```text
performance_assessment_v3_schema.sql
-> V3_004_reference_seed.sql
-> V3_005_smoke_test.sql
-> V3_006_dynamic_answer_sheet_schema_DRAFT.sql
-> V3_007_validated_a4_template_regions_DRAFT.sql
-> V3_008_dynamic_answer_sheet_smoke_test_DRAFT.sql
-> V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql
-> V3_009_dynamic_answer_sheet_contract_hardening_smoke_test_DRAFT.sql
-> V3_009_dynamic_answer_sheet_negative_fixtures_DRAFT.sql
```

The canonical V3 schema is the clean 60-table baseline. Do not execute
`V3_000_v2_schema_snapshot.sql` as a V3 baseline because it creates and selects the
V2 database. `V3_001` through `V3_003` are retained as migration history already
incorporated into the canonical schema.

Expected final disposable result: 66 central tables, three paper sizes, one active
physically validated OMR template, and 15 exact regions for that template.

## Disposable Validation Result

The original dynamic package was validated against
`performance_assessment_v3_validation_20260830_112725`. The complete hardened
chain was then rebuilt and validated against the fresh disposable database
`performance_assessment_v3_validation_20260830_182425`:

- Original baseline smoke test: `PASS`, 60 tables
- Dynamic schema and geometry seed: applied successfully
- Dynamic smoke test: `PASS`, 66 tables
- V3_009 hardening smoke test: `PASS`
- Metadata: 157 foreign keys, 80 checks, 96 unique constraints
- Eleven executable invalid count, region, evidence-lineage, retention, purge,
  and QR cases rejected; zero fixture rows persisted
- Live Spring readiness: HTTP 200, `ready = true`, all 16 checks passed
- Maven: 108 tests, 0 failures, 0 errors, 0 skipped
- Source safety during disposable validation: real V3 remained at 60 tables; V2
  remained at 41 tables

After approval, the same chain was applied to local
`performance_assessment_v3_db` and independently revalidated:

- Local V3: 66 tables, 157 foreign keys, 80 checks, 96 unique constraints
- Live Spring readiness: HTTP 200, all 16 checks passed
- Maven: 108 tests, 0 failures, 0 errors, 0 skipped
- V2 remained at 41 tables
- The verified 60-table backup passed a temporary restore test

Detailed evidence is in
`V3_DYNAMIC_ANSWER_SHEET_DISPOSABLE_VALIDATION_2026-08-30.md` and
`V3_DYNAMIC_ANSWER_SHEET_HARDENING_VALIDATION_2026-08-30.md`.

## Completed Local Application Gates

1. Field names, statuses, FKs, and compatibility rules reviewed.
2. Current 60-table local V3 backed up and the backup restore-tested.
3. Complete chain validated in a separate disposable database.
4. Original 60-table and expanded 66-table smoke tests passed.
5. Table, FK, check, unique, and seed counts reconciled.
6. QR keys, DTO direction, upload limits, retention, and Mobile mapping reviewed.
7. Owner/Mobile approval received and the chain applied to local V3.

Backend decisions are documented in
`V3_DYNAMIC_ANSWER_SHEET_BACKEND_CONTRACT_DECISIONS.md`. The database gate is
complete; Java repository/API work may begin without switching clients.

Rollback during validation is database restore/recreate from the recorded backup,
not manual deletion of evidence relationships.

## Mobile Handoff Message

The backend has applied the approved V3_006 through V3_009 schema package only to
the local central V3 database. The current central baseline is 66 tables with
exact paper sizes, immutable template regions, generated answer-sheet
versions/pages, question-to-region manifests, immutable scan pages,
written-response count snapshots, evidence lineage/retention, and bounded hashed
QR payloads. Shared future Mobile SQLite names remain `omr_templates`,
`answer_sheet_versions`, `answer_sheet_pages`, `answer_sheet_regions`,
`scan_sessions`, `scan_pages`, and `omr_detections` where practical. Do not
migrate production SQLite or switch APIs yet. Only
`OMR-A4-10-MC-CTX-V2` has exact seeded/physically validated geometry; Letter,
Legal, mixed-type, and written-response layouts remain unavailable until their
own immutable maps pass PDF and physical scanner validation.
