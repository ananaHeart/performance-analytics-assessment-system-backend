# Mobile V3 detection contract supplement 1.1.0

The [OpenAPI 3.1 definition](openapi.json) documents the implemented, release-blocked
`POST /api/v3/mobile/scan-pages/{scanPageUuid}/detections` handler. This is a focused
supplement to frozen pack 1.0.0, not a replacement for all its routes. The wire
`contractVersion` remains `3.0`; request/acknowledgement fields match the proposed
1.0.0 detection contract. Nine copied fixtures retain their illustrative IDs.

**Production security denies this route. Do not begin production Mobile wiring or
SQLite migration based on this supplement.** Draft V3_015 and V3_016, deployment
validation and the remaining verification/scoring/reconciliation workflow are
still release gates. See the [implementation handoff](../../../V3_MOBILE_DETECTION_HANDOFF.md).

## Implemented semantics

- Upload 1..200 observations as one atomic page batch; JSON is bounded to 128 KiB.
  Include all fields, including explicit `detectedOption: null` for ambiguous marks.
  Unknown fields, duplicate JSON keys and numeric strings are rejected.
- Each observation uses durable detection, region and question UUIDs. Backend
  derives central IDs from the committed page and stored manifest relationships.
  `answer_sheet_region_options` is a Mobile projection, not a central SQL table:
  Backend checks `geometry_snapshot.option_keys` plus active `question_options`.
- MC uses stored A/B/C/D keys; TF uses A=True and B=False. `T`/`F` display labels are
  invalid. `blank`, `multiple_marks` and `uncertain` require a null selected option.
- Confidence is 0..1. Exact reported precision is retained in `raw_mark` JSON;
  `confidence_score DECIMAL(5,4)` is a rounded projection. Per-bubble darkness is
  not supplied in this DTO and is not fabricated. Confidence is never a score.
- A new operation requires a new `syncUuid`. Retry the same operation UUID, sync
  UUID, detection UUIDs and content. Array reordering and equivalent decimal
  spelling replay successfully; changed content conflicts. A new operation cannot
  replace an occupied page region, even with an identical observation.
- Disjoint region batches on a page are permitted until the capture is verified
  or locked. No request implicitly claims that every page region is complete.
- Created/replayed acknowledgements contain detection ID mappings and the original
  receipt revision and `acknowledgedAt`. Replay does not advance the revision.
  Mapping order is canonical by detection UUID; correlate by UUID, not array index.
- Each new batch advances `test_results.mobile_revision` once. The column starts
  at 1; integration with future Mobile verification/correction/finalization writes
  remains pending. It is not `scoreVersion` or a completed whole-system concurrency
  contract. Existing scoring routes do not yet advance it.
- New writes require an owned, committed, selected and unverified capture. Exact
  committed replay may survive later lifecycle changes but still checks current
  teacher access. No answer/verification/score is created by a detection upload.

TF tests use synthetic stored records. The approved scan ingestion template remains
the existing one-page 10-MC A4 template; TF print/scanner acceptance is not enabled.

## Fixture validation

```text
node docs/contracts/mobile-v3/1.1.0/validate.cjs
```

This checks the focused schemas, examples and unchanged parent schema shapes;
Java H2/MariaDB tests validate runtime persistence and retry behavior. It is not a
full OpenAPI toolchain conformance test or a phone-to-backend acceptance test.
