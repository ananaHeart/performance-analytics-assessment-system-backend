# Mobile V3 pack 1.12.0: evaluation-reference 3.1 (answer keys)

Date: 2026-09-26. Same endpoint as before, now carrying answer keys so the phone
can show a **display-only preliminary score** right after scanning a sheet:

```http
GET /api/v3/mobile/test-assignments/{assignmentUuid}/evaluation-reference
```

The response's `contractVersion` is `"3.1"`. This is the evaluation-reference's
own version. It is unrelated to the written-verification request contract, which
is also numbered 3.1. Sample: [fixtures/evaluation-reference.json](fixtures/evaluation-reference.json).
Its hash is the real fingerprint of that data.

## What changed from 3.0

Added fields (every 3.0 field is unchanged):

| Where | Field | Meaning |
|---|---|---|
| `questions[]` | `questionType` | `multiple_choice`, `true_false`, `identification`, `enumeration` or `essay` |
| `questions[]` | `correctOptionKey` | MC/TF only, otherwise `null`. It is `question_options.option_key`, which equals the manifest's `region.options[].storedValue`. MC is `A`-`D`; T/F is always `A`=True and `B`=False, whatever label is printed. |
| `questions[]` | `acceptedAnswers[]` | Identification/enumeration only, otherwise `[]`. Each item is `{text, matchingMode (exact or normalized), caseSensitive, points}`. Only active answers are sent, in answer order. |
| `rubrics[].criteria[]` | `description` | `rubric_criteria.criterion_description` |
| `rubrics[].criteria[]` | `levelDefinition` | `rubric_criteria.level_definition`, passed through exactly as the teacher saved it, or `null` |

Still never sent: `answer_keys.answer_explanation` (the model solution).

## Rules

- **The phone score is preliminary and display-only.** It is never uploaded and
  never stored as a second score. The backend still computes the only official
  score at finalization, as before.
- **Identification and enumeration are not auto-graded, even by the backend.**
  The official points are the teacher's `points_earned`. `acceptedAnswers` is a
  guide shown next to the student's cropped answer.
- MC/TF match the backend scorer: correct option = full points; blank,
  multiple or uncertain marks = 0. Map the detected printed key to that option's
  `storedValue` from the manifest, then compare with `correctOptionKey`.
- Access is the same as 3.0: only the assigned active teacher, only for
  active/completed tests with a non-archived assignment. Drafts are never sent.
  Mobile stores keys (Keystore-encrypted) only while `captureAvailability` is
  `open` or `late_allowed`, and deletes them when the assignment is
  closed/archived or leaves the download.

## Fingerprint

`evaluationReferenceHash` now also covers `questionType`, `correctOptionKey`,
every accepted answer, and each criterion's `description` and `levelDefinition`.
Any key change therefore changes the hash, even with the same question UUIDs and
`testVersionNumber`. (Today every draft save also regenerates question UUIDs, but
detection no longer depends on that.)

**One-time effect on deploy:** every assignment's hash changes. A written
verification or correction sent with a 3.0 hash gets `409 EVALUATION_REFERENCE_STALE`.
Mobile re-fetches the reference and retries (the existing flow). No database change.

## Tests

- `V3EvaluationReferenceAnswerKeyTest`: keys appear only on the question type
  that uses them, and any key change changes the hash.
- `V3EvaluationReferenceControllerTest`: serves this fixture over HTTP.
- `V3ScanUploadMariaDbTest`: two cases updated/added. They run only on the
  isolated MariaDB validation instance (port 33317) and were **not run** for
  this change.
