# Mobile V3 evaluation-reference supplement 1.5.0

Milestone 3 — Prompt 10 implements
`GET /api/v3/mobile/test-assignments/{assignmentUuid}/evaluation-reference`.
The six included schemas preserve the 1.0.0 wire definitions. Only the reference
GET is implemented here; attachment/manual/rubric schemas and write examples
remain implementation targets. The wire version remains `3.0`.

The reference returns each question's UUID, maximum points, nullable rubric ID and
expected response count; shared rubric names; and ordered criterion IDs, names,
maximum points and `isRequired` flags. Questions are ordered by canonical UUID,
rubrics by numeric ID, criteria by configured order then ID. Reference order is
not the printed question order; use the downloaded assessment for display order.

The assigned active school teacher may download a snapshot for an active/completed
assessment and a non-archived delivery. Unknown and foreign assignments both
return 404. Rubrics must be active and belong to the same school; missing,
cross-school or inconsistent references fail with 409 without leaking their names.
Written key strategies are checked internally against the existing scorer.
Answer keys, accepted responses and model solutions are never returned.

Direct point semantics: the sum of criterion maxima must equal the rubric total
and the linked question maximum. No implicit scaling is implemented. The existing
scorer requires a score row for **every criterion**, including `isRequired=false`.
The flag is returned faithfully; it does not authorize omitting those rows.

This is a **scoring-bounds reference**, not the complete rubric authoring document.
The frozen response omits criterion descriptions and level definitions, as well as
maximum response length already available in the assessment download. Full offline
rubric guidance may need a later versioned contract extension. Do not claim this
GET alone completes offline written grading.

## Fingerprint

Store `evaluationReferenceHash` alongside `assignmentUuid` and `testVersionNumber`.
It is lowercase SHA-256 of UTF-8 compact JSON with fields in this order:
`contractVersion`, `assignmentUuid`, `testVersionNumber`, `questions`, `rubrics`.
Question fields are `questionUuid`, `maximumPoints`, `rubricId`,
`expectedResponseCount`; rubric fields are `rubricId`, `name`, `criteria`; criterion
fields are `rubricCriterionId`, `name`, `maximumPoints`, `isRequired`.
For hashing only, point values are fixed two-decimal strings (`"5.00"`); IDs/version/
counts are JSON numbers, booleans and explicit nulls remain their JSON types.
The hash itself and envelope timestamps are excluded. Names are exact Unicode
strings; no trimming, normalization or ASCII conversion is applied. Array order
is the server order above. The validator independently reproduces the hash,
including a Unicode fixture and a real synthetic runtime response.

The hash fingerprints the returned bounds, not hidden answer keys, omitted rubric
descriptors or a stored historical reference. GET is a consistent snapshot and
does not persist history. Existing verification DTOs have no field for this hash;
do not add it to the current objective endpoint. Written upload needs an explicit
reference-binding decision before release (see the contract review below).

## Release and next writes

`app.v3.mobile.evaluation-reference-enabled` /
`V3_MOBILE_EVALUATION_REFERENCE_ENABLED` defaults to false, returning 503 before
database access. This read uses existing V3_014 schema columns and adds no
migration. It does not unlock scan, attachment or verification writes, enable
written templates, or authorize production Mobile SQLite migration.

See [written-answer contract review](../../../V3_MOBILE_WRITTEN_CONTRACT_REVIEW.md)
for crop lineage, blank/manual/rubric rules, the confirmed image-only SQL blocker
and the remaining reference-binding/history decision. No fake transcription or
dummy text may be inserted to bypass the SQL constraint.

Limits: 200 questions, 200 referenced rubrics, 100 criteria per rubric. Overflow
returns `EVALUATION_REFERENCE_LIMIT_EXCEEDED`; inconsistent scoring references
return `EVALUATION_REFERENCE_INVALID`; unavailable lifecycle returns
`EVALUATION_REFERENCE_NOT_READY`. Successful responses use `Cache-Control: no-store`.

Run `node validate.cjs --runtime-samples` after the isolated MariaDB suite.
These fixture/schema/hash checks are not full OpenAPI toolchain certification.
