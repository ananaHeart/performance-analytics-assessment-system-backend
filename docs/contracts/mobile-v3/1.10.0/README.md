# Mobile V3 contract pack 1.10.0 — correction and re-finalization

Pack 1.10.0 introduces the explicit **3.2 correction request**. Initial objective
3.0 and written 3.1 DTOs remain unchanged. The response reuses the official
finalization, result, analytics and sync contracts.

- `POST /api/v3/mobile/results/{resultUuid}/corrections`
- Owned result must have a committed, unconsumed reopen operation from pack 1.9.0.
- Send every current answer, including unchanged evaluations, with fresh
  verification UUIDs, the existing answer/question/region/page identities,
  expected current revision/score version, downloaded evaluation reference hash
  and test version, and a required reason. Nullable fields must be present.
- Objective: original detection UUID plus teacher-selected A/B/C/D or blank;
  true/false allows only A/B. The server computes points. The original detection
  is retained even when the teacher changes its interpretation.
- Written: existing 3.1 manual/rubric evaluation rules, corrected transcription,
  retained evidence UUIDs, numeric points or all rubric criteria, and comments.
- One result per request, at most 200 answers and 2 MiB JSON. Complete coverage,
  current assignment/capture ownership, reference validity, evidence lineage and
  hashes, rubric ownership and score bounds are checked by the server.
- Corrections, answer history, official scoring, score version +1, revision +1,
  audit and sync receipt commit together. There is no partially committed review
  and no second finalize call. Equal totals still receive a new score version.
- Retry the identical typed request with unchanged operation/sync/verification
  UUIDs. Preserve array order, values and decimal representation. Altered content
  under an existing operation conflicts. A committed retry returns its historical
  score with `scoreChanged=false`, even after later corrections. Always GET the
  current result/analytics after acknowledgement; never overwrite a newer local
  version with an older replay. A new corrected intent needs fresh UUIDs and a
  fresh review of current revision/reference state.
- Sync readback returns one successful result item, with empty `pageOutcomes`:
  this receipt acknowledges correction, not scan upload. Errors affect the whole
  requested result. A persistence/acknowledgement error requires identical retry.

`openapi.json` contains the route and strict schemas. Fixtures include objective
and mixed written correction requests, created/replayed official responses,
current result/analytics and sync readback. They use synthetic identities from
disposable persistence tests, not usable school records or login credentials.

Run `node docs/contracts/mobile-v3/1.10.0/validate.cjs --runtime-samples` after the
MariaDB suite. Ajv 6 is resolved using the same mechanism as earlier packs.

**Release gated:** HTTP corrections are denied and service enablement defaults
to false. Draft V3_015 through V3_022 must be reviewed/deployed before coordinated
activation. The configured school baseline remains V3_014. This pack does not
implement replacement scans/superseding, public score-history browsing, production
Mobile wiring, secure session storage, SQLite migration or phone acceptance.
