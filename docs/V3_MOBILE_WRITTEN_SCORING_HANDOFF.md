# Milestone 3 — Prompt 12: manual/rubric teacher verification

Date: 2026-09-12. Backend component implemented and validated in disposable MariaDB.
Production verification HTTP remains denied. No Mobile code, SQLite migration,
APK, school database, SF1 or production printed geometry was changed.

## What Mobile can implement against this contract

Use `POST /api/v3/mobile/verification-batches` with explicit **contractVersion 3.1**
for written evaluations. The existing 3.0 objective payload remains unchanged and
still rejects written fields. Login, download, attachment and readback DTO versions
are not globally changed. [OpenAPI and fixtures](contracts/mobile-v3/1.7.0/README.md)
provide the versioned handoff before production wiring.

Each item has resultUuid, expectedRevision, testVersionNumber,
evaluationReferenceHash, pageDecisions and answers. Download the reference from
the existing evaluation-reference GET; use its test version and hash. Fetch the
current result revision **after attachment uploads**, since new evidence increments
it. A written batch may contain manual and rubric evaluations for up to 50 distinct
results, with 1..200 answers per result. It cannot mix objective answers into 3.1.
Body maximum is 2 MiB; submit smaller independent batches if needed.

Page decisions here are optional **initial accepted decisions** only. An answer's
page must already be accepted or be accepted in the same result transaction. Use
the existing page-verification contract for reject/rescan requests; changing an
existing decision remains correction work. The question's immutable region must
be `written_response`, with matching question, page, sheet, test and question type.

## Manual and rubric scores

- Manual: `kind=manual`, answerStatus answered/blank, nullable responseText,
  attachmentUuids and numeric points. Identification/enumeration follow their
  existing accepted-text/manual configuration. An essay permits direct manual
  points only when configured without an assigned rubric and with a manual key.
- Rubric: `kind=rubric`, answerStatus, nullable responseText, attachmentUuids,
  rubricId and criterionScores. Rubric scoring is restricted to an essay with
  that rubric assigned consistently to its question/key. Each criterion contains
  rubricCriterionId, pointsAwarded and nullable comment. Backend computes the sum;
  clients cannot supply an essay total or correctness flag.
- Scores are nonnegative, at most two decimal places, within question/criterion
  maxima. Every rubric criterion is required, including criteria marked optional
  by the existing reference flag; this matches the current scorer. Blank answers
  require zero total (therefore zero for each nonnegative criterion).
- Answer and criterion comments allow up to 4,000 characters. Text has the bounded
  DTO limit of 10,000 characters plus the question's maximumResponseLength.
  Absent transcription is null, never an empty/whitespace placeholder. Answered
  responses need real text or uploaded evidence; blanks cannot contain text.

Teacher judgment supplies the scores. The backend validates and records it; this
slice adds no automatic essay grader or AI judgment.

## Evidence and image-only representation

Up to 20 unique attachment UUIDs per answer are supported in this version. Each
attachment can be assigned to only one answer. Attachments must already be committed,
private, retained `answer_crop` or `teacher_evidence` from that same scanned page.
A crop must match the answer's region; teacher evidence may have that region or
no region. Original/normalized lineage, crop dimensions/bounds, metadata identity
and actual file integrity are rechecked under the write transaction. Originals
and normalized pages cannot themselves be passed as answer evidence.

Draft V3_020 adds nullable `student_answers.response_evidence_attachment_id` with
a real FK. The existing response check is extended only for manual answers with
this evidence reference; image-only answered rows without it are still rejected.
An extra check prevents the evidence reference being used for OMR/selected-option
rows. The service resolves ownership and claims the attachment while holding locks,
then stores the answer, evidence association and audit atomically. A FK alone does
not prove same-answer/school ownership; those cross-table rules are explicitly
enforced by this service transaction. Other future writers must enforce them too.

The primary evidence is the first UUID in canonical sorted order. All submitted
attachments remain linked through student_answer_id and in evaluation audit JSON.
No fake transcription or unverified pending answer is invented to satisfy SQL.

## Reference binding and history

Each result's transaction locks current assignment/test, question/key, rubric and
criterion rows using current locking reads under REPEATABLE READ. Its reference
must match the submitted hash and test version before page decisions or answers
can commit. A stale submission receives item error `EVALUATION_REFERENCE_STALE`;
Mobile must refresh and have the teacher review the scores, then use a **new**
operation/sync UUID with the current result revision. Do not silently rebase scores.

The accepted public reference is copied into `mobile_written_references` keyed by
sync item. `answer_verifications` links to it and stores client decision time and
canonical evaluation JSON. Server verified_at remains authoritative. Future rubric
edits do not rewrite this accepted audit snapshot or scores. Exact successful replay
returns its historical receipt after current ownership checks without rescoring.

The existing reference hash covers returned scoring bounds, labels, required flags,
question references and test version. It does not cover hidden answer keys or rubric
descriptions/level definitions absent from the existing GET. This slice preserves
the accepted bounds snapshot; it does not provide a complete rubric-guidance history
or enable finalization against arbitrarily edited current rubrics. The next written
finalization slice must compare its stored reference before official scoring.

## Persistence, retries and readback

The existing immutable verification batch ledger is reused. Ownership for every
referenced result/page is checked before registration; each result then processes
in its own transaction. A successful item writes:

1. The accepted reference snapshot and any initial page acceptance audit.
2. student_answers with teacher points, feedback, verifier and real response/evidence.
3. Append-only answer_verifications with evaluation, evidence and reference linkage.
4. For essays, answer_rubric_scores with criterion comments, verification ID and version.
5. One mobile_revision increment and the durable item acknowledgement.

At answer level, evaluation_status is finalized, matching the existing teacher
verification/scorer convention. **The result remains pending_verification**; official
result finalization and official analytics readiness are not implied.

HTTP 200 uses the unchanged verification response: inspect each item's status,
disposition, revision, ID mappings and retryable error. A valid learner result can
commit while another fails. Retryable persistence/dependency failures may be retried
with the exact original batch; successful items replay without duplicates. Nonretryable
validation/reference/revision outcomes remain frozen for that operation. Corrected
submissions need new operation/sync IDs. A lost commit acknowledgement checks the
durable receipt before recording failure.

Sync readback now understands the stored 3.1 written request while preserving its
3.0 readback shape. Result readback returns answer, verification, attachment and rubric
score mappings through existing fields. It does not newly expose full provisional
teacher evaluation bodies; Mobile retains its accepted local evaluations and uses
receipt/mapping reconciliation. Official score/analytics still await finalization.

## Validation and release

[Validation evidence](V3_MOBILE_WRITTEN_SCORING_VALIDATION.json) records 172 passing
focused tests, including 23 new Prompt 12 cases. MariaDB cases cover real image-only
manual persistence, rubric totals/history, direct manual essays, stale bounds/version,
score/text/criteria limits, purge/region rejection, concurrent retries, rollback and
lost acknowledgement, concurrent rubric edits, partial success, current teacher access,
and the new FK/check behavior. HTTP tests cover strict 3.1 dispatch and rejection of
missing bindings, coercions, extra verifier/score fields and non-UTC client times.
All eight versioned contract validators pass; 1.7.0 checks four actual runtime samples.

The synthetic written tests modify only disposable fixture question/region metadata
after original ingestion. They validate the persistence component, **not** physical
written scanner geometry or complete written capture. The approved production
first-capture template remains one-page 10-MC A4.

Configured baseline remains V3_014: 67 tables / 163 FKs / 87 checks / 99 uniques.
Draft V3_020 test chain: 75 tables / 182 FKs / 106 checks / 106 uniques. The new migration
was applied only to the isolated instance. Production release requires reviewed
deployment of the draft chain and coordinated Mobile integration. Verification
and other upload routes remain denied; finalization/readback/reference flags remain off.

## Next bounded session

Update after Prompt 13: the planned backend finalization work below is now complete
in isolated tests; see the [Prompt 13 handoff](V3_MOBILE_WRITTEN_FINALIZATION_HANDOFF.md).
The validation counts above remain the historical Prompt 12 results.

**Milestone 3 — Prompt 13: written finalization and workflow validation.** Extend the
currently objective-only Mobile finalization checks to the stored written evaluations,
retained evidence and accepted reference; verify official totals/readback without
rescoring against changed rubrics. Audited correction/reopen remains Milestone 4;
production phone/scanner integration remains Milestone 5.
