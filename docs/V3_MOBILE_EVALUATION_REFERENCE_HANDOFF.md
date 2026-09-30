# Milestone 3 — Prompt 10: evaluation-reference

> **Update 2026-09-26: contract 3.1.** The reference now also returns answer keys
> (`correctOptionKey`, `acceptedAnswers[]`, criterion `description` and
> `levelDefinition`) for a display-only preliminary score on the phone, and the
> hash covers them. The "no answer leakage" statements below describe 3.0. The
> model solution (`answer_explanation`) is still never sent. See
> [pack 1.12.0](contracts/mobile-v3/1.12.0/README.md).

Date: 2026-09-12. **Scoring-reference component implemented and tested; Milestone 3
is still in progress.** Written evidence/manual/rubric upload remains pending.

Implemented `GET /api/v3/mobile/test-assignments/{assignmentUuid}/evaluation-reference`
through `V3EvaluationReferenceController`, `V3EvaluationReferenceService` and
`V3EvaluationReferenceRepository`, using the existing V3_014 schema columns.
No migration was added. The configured baseline and existing Mobile gates remain
unchanged.

The endpoint supplies question score bounds/expected counts and shared rubric
criteria under the unchanged `EvaluationReference` contract. It verifies the live
teacher account, owned assignment and school. Rubrics must be active and in the
same school, strategies must agree with existing written scoring, and criterion,
rubric and question maximum totals must agree. Limits are explicit: 200 questions
and 100 criteria per rubric, with overflow errors instead of truncated success.

One repeatable-read snapshot covers authorization, assessment version, questions
and rubrics. Questions/rubrics/criteria have deterministic ordering and a SHA-256
fingerprint over the public reference. Repeat reads are stable; changed public
bounds/names or test version change the hash. Secret answer explanations do not.
GET does not persist a historical reference or grade any answer.

[Pack 1.5.0](contracts/mobile-v3/1.5.0/README.md) contains OpenAPI 3.1, six unchanged
parent schemas, five fixtures and an independent JavaScript hash validator. A real
synthetic runtime response also passes schema and hash validation. Manual/rubric
schemas in this pack are reviewed write targets only. The reference omits rubric
descriptions and levels; it supplies bounds, not complete offline grading guidance.

The [written-answer contract review](V3_MOBILE_WRITTEN_CONTRACT_REVIEW.md) records
the next implementation rules and unresolved write-side requirements:

- Image-only answered rows remain blocked by `chk_student_answers_response`,
  confirmed on actual MariaDB even with retained evidence metadata.
- Existing scoring requires all rubric criteria, including those marked optional.
- Written writes need explicit stale-reference binding/history; a result revision
  alone cannot detect rubric edits. The new GET hash does not implement that write
  precondition.
- Crop lineage must use its base-attachment crop JSON, preserving the separate
  normalized-page source-FK rule. No fake transcription or broad constraint bypass.

## Release status

`app.v3.mobile.evaluation-reference-enabled` /
`V3_MOBILE_EVALUATION_REFERENCE_ENABLED` defaults to false. Disabled reads return
503 `EVALUATION_REFERENCE_UNAVAILABLE` before any database access. Enabled reads
still require TEACHER and domain ownership; successful responses are `no-store`.
Unknown and foreign assignments return identical 404s. Bad references and limits
return explicit 409s; no hidden rubric content appears in errors.

No security matcher was relaxed. Attachment and verification writes remain
unavailable; written templates, Mobile production SQLite/wiring and phone/scanner
acceptance are not enabled by this GET.

## Validation

[Evidence JSON](V3_MOBILE_EVALUATION_REFERENCE_VALIDATION.json) records the focused
suite results and hashes. **83 tests passed**: 61 actual MariaDB tests (13 new
reference/schema cases plus 48 existing Mobile regressions), three handler/security
tests, two gate/validation tests, nine scorer tests, five Java fixture checks and
three existing write-gate tests.

Cases cover mixed objective/manual/rubric reference data, shared rubric reuse,
manual essays without rubrics, stable and changed hashes, Unicode names, no answer
leakage, inactive/cross-school/missing/inconsistent rubrics, wrong ownership,
assignment lifecycle, oversized questions/criteria, missing strategies, concurrent
rubric changes, release gates and the image-only SQL invariant.

MariaDB 10.4.32 ran only at loopback port 33317 with its own data directory under
`target`; the existing V3_018 test chain remained 73 tables / 175 FKs / 98 checks /
104 uniques. It was stopped after validation. The configured V3_014 baseline
remains 67 / 163 / 87 / 99. No school database, application datasource, SF1, V1/V2,
Mobile code/SQLite/APK, QR payload or printed geometry was changed. Synthetic
written questions do not establish physical written-template acceptance.

```powershell
# Start and prepare the isolated instance using the existing tmp helpers first.
$validationPath=(Get-Content target/scan-recovery-validation-path.txt -Raw).Trim()
$testDatabase=(Get-Content (Join-Path $validationPath 'database.txt') -Raw).Trim()
mvn -q '-Dtest=V3ScanUploadMariaDbTest,V3EvaluationReferenceControllerTest,V3EvaluationReferenceGateTest,V3ScoringServiceTest,V3MobileContractFixtureTest,V3ScanPageSecurityGateTest' "-Dv3.scan.mariadb.database=$testDatabase" test
node docs/contracts/mobile-v3/1.5.0/validate.cjs --runtime-samples
```

Next: **Milestone 3 — Prompt 11: written evidence/crop upload and persistence**.
See the [milestone tracker](V3_MOBILE_MILESTONE_TRACKER.md).
