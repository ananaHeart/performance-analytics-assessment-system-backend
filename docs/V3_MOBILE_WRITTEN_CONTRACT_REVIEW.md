# Milestone 3 — Prompt 10: written-answer contract review

Date: 2026-09-12. This review separates the implemented evaluation-reference read
from the still-pending evidence/manual/rubric write contracts. It does not enable
written capture, scoring upload, production Mobile wiring or a schema migration.

Historical Prompt 10 review: attachment implementation has since been completed
as the gated [Prompt 11 component](V3_MOBILE_ATTACHMENT_UPLOAD_HANDOFF.md). The
pending-write statements below describe the state at this review. Initial manual/rubric
persistence, explicit image-only evidence representation and bounds-reference binding
have since been completed as the gated [Prompt 12 component](V3_MOBILE_WRITTEN_SCORING_HANDOFF.md).
Written finalization and complete rubric-guidance history remain separate work.

## Reference contract now implemented

`GET /api/v3/mobile/test-assignments/{assignmentUuid}/evaluation-reference`
returns the original 1.0.0 `EvaluationReference` wire shape. Pack
[1.5.0](contracts/mobile-v3/1.5.0/README.md) supplies OpenAPI, schema parity checks,
fixtures and a reproducible hash algorithm. Questions expose UUID, maximum points,
rubric ID and expected response count. Rubrics expose names and ordered criterion
IDs/names, maximum points and required flags.

Store the reference as an assignment/test-version/hash snapshot. Compare its
test version with the assessment download and immutable sheet manifest before
using it for offline review. A mismatch needs refreshed references, not a guessed
rubric mapping or silently recalculated local score.

The hash covers returned bounds only. Descriptions and scoring-level definitions
are omitted by the frozen shape; no answer keys, accepted answers or model
solutions are returned. This is not a complete offline rubric-guidance document.
If Mobile needs those descriptors, extend a versioned contract before that UI's
acceptance. Do not claim their omission was solved by criterion names alone.

## Reviewed write targets — still pending

| Area | Agreed target | Remaining implementation |
|---|---|---|
| Evidence | Multipart `POST /api/v3/mobile/attachments`; attachment/operation/sync UUIDs, hash, size, MIME, page/result/region context | Private storage, durable receipt and ID resolution, retry/recovery, lineage/purge/state checks |
| Manual scoring | `kind=manual`, answered/blank, nullable text, attachment UUIDs, numeric points | Written DTO acceptance, evidence checks, question bounds and text limits, atomic answer/audit/receipt persistence |
| Rubric scoring | `kind=rubric`, rubric ID and unique criterion scores | Complete criteria, current reference binding, backend sum, criterion feedback, immutable verification history |
| Finalization | Existing backend scorer derives official totals from verified stored answers | Extend the current objective-only Mobile finalization bridge for retained written evidence and audited evaluations |

The included manual/rubric fixture examples remain design targets. The active
verification handler accepts only `kind=objective`; attachments have no upload
handler in this slice and writes remain denied. Do not send new fields or written
payloads to the current objective endpoint.

## Concrete validation rules for subsequent slices

- Original pages enter through the original-image path. `normalized_page` links
  to a committed original in the same page/session/result using
  `sourceAttachmentUuid` and the existing normalized-source FK.
- `answer_crop` requires a region and `crop.baseAttachmentUuid` pointing to an
  original/normalized page. Top-level `sourceAttachmentUuid` stays null: current
  SQL permits that FK only for normalized pages. Store crop derivation in the
  existing crop JSON; do not repurpose the normalized-source FK.
- Crop bounds use decoded image pixels from the top-left, not PDF points. The
  region/question must belong to the immutable page. Preserve the source bytes.
  `enhanced_answer_crop` is not a valid new attachment type.
- `teacher_evidence` has nullable region, no crop, and null source FK. All evidence
  must remain private, committed, in scope and retained when used for scoring.
- Manual points must be within the question maximum and have at most two decimal
  places. A blank answer is zero. Never synthesize transcription to satisfy SQL.
- Rubric criteria must be unique, belong to the assigned rubric, and respect each
  criterion maximum. The backend derives the essay total. Current scorer behavior
  requires **all criteria**, even `isRequired=false`; blanks must have zero scores.
- Reference totals are direct points: criterion maxima sum to rubric total and
  question maximum. An inconsistent configuration is rejected; no implicit scale
  factor is introduced.
- Use the existing download's `maximumResponseLength` along with the proposed
  overall text cap. The reference GET does not replace that download field.

## Confirmed SQL blocker: image-only answered rows

The actual canonical-plus-draft test chain still has
`chk_student_answers_response`: an answered row needs a non-null selected option
or response text. The check permits pending/blank/uncertain states, but does not
consult retained attachments. An isolated MariaDB test inserted a pending manual
answer with retained evidence metadata, then confirmed that changing it to
answered with both fields null is rejected and leaves the pending row unchanged.

The existing scorer can inspect retained written attachments, so persistence and
scoring requirements are inconsistent for image-only answers. Before enabling
that path, design an additive evidence-aware representation/constraint and its
transactional validation. A CHECK cannot simply look up attachment ownership in
another table. Do not remove the invariant broadly, insert a fake answer, or mark
this blocker resolved in Mobile. No migration was authored or applied here.

## Remaining stale-reference decision

The proposed manual/rubric verification batch carries `expectedRevision` for the
result but has no `evaluationReferenceHash` or test-version field. Result revision
alone does not detect an edited question/rubric. The GET hash is a deterministic
read fingerprint, not durable historical rubric storage or a server-issued write
precondition.

Before written verification is released, define explicit binding to a captured
evaluation-reference version/hash, preserve the applicable rubric history or
reject outdated references, and test concurrent rubric edits. Version that write
extension separately; do not silently add an unsupported field to 1.2.0 objective
requests. Prompt 10 does not claim this future write-side protection is implemented.

## Next bounded work

**Milestone 3 — Prompt 11: written evidence/crop upload and persistence.**
Implement the evidence component and immutable page/region lineage first. Keep
manual/rubric verification and its image-only SQL/reference-binding decisions in
the subsequent scoring slice. Written template/geometry and physical capture
acceptance remain coordinated release work; the approved capture still is the
one-page 10-MC A4 template. SF1 stays excluded.
