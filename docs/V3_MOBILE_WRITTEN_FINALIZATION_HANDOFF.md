# Mobile V3 — Prompt 13 written finalization handoff

Completed 2026-09-12. **Milestone 3 backend components are complete through Prompt 13
in isolated tests; production release and physical Mobile acceptance remain gated.**

## Resulting behavior

The existing bodyless `POST /api/v3/scoring/results/{testResultId}/finalize` now handles
validated written-only and mixed objective/written stored results. It preserves the
teacher's manual or rubric-awarded points and uses the existing backend scorer to
compute official part totals, total/max, percentage and performance classification.
This does not introduce AI grading or a new scoring formula.

The integrated mixed fixture finalizes 10 answers to **23.50/28 = 83.93%**. Its essay
keeps the accepted 7 points. The written-only fixture covers a direct manual essay
without an assigned rubric and a blank zero-point answer, totaling 15.50/28. These
are synthetic backend fixtures, not evidence that a written paper can yet be scanned
through the production Mobile app.

## Checks before the first official score

All checks run in the existing scoring transaction before official result mutation:

1. Reauthorize the assigned teacher and lock the result. Require the selected,
   accepted current sheet page, teacher page audit, committed original upload receipt
   and retained original bytes. The one-page first-capture restriction remains.
2. Require supported question types and complete accepted detection/teacher-audit
   coverage for every MC/TF question. Existing answer-key validation still applies.
3. Require every written answer to have finalized teacher evaluation state, an
   initial manual-scoring audit and successful matching written sync receipt. Compare
   saved request/evaluation facts with current answers, comments, points and IDs.
4. Lock the current public evaluation reference through scoring commit. Compare its
   version/hash and full saved snapshot with the accepted reference. A changed
   reference blocks first finalization with `WRITTEN_REFERENCE_STALE`.
5. Compare persisted rubric rows exactly with accepted criterion scores, comments,
   audit/verifier links and score version. Validate current scoring bounds with the
   existing scorer. Direct manual answers must have no assigned rubric/criterion rows.
6. Require exact accepted attachment links and primary evidence pointer, matching
   written region/page, committed retained lineage and verified file size/hash.
   Answered image-only responses need actual evidence; blank answers follow the
   existing zero-point scoring rule.

Missing written evaluation/audit context returns `WRITTEN_VERIFICATION_INCOMPLETE`;
conflicting stored facts return `WRITTEN_AUDIT_MISMATCH` (both HTTP 409). Purged or
invalid attachment lineage returns `ATTACHMENT_LINEAGE_INVALID` (422), missing
retained bytes `SCAN_EVIDENCE_NOT_FOUND` (404), and corrupted bytes
`SCAN_EVIDENCE_INTEGRITY_FAILED` (500). These failures leave the result unfinalized.
Mobile should preserve its pending work and display the returned reason; it must
not silently replace an accepted evaluation to resolve a conflict.

The reference fingerprint covers the public reference currently returned by the
backend. It does not add omitted rubric level descriptors or hidden answer keys to
that fingerprint. This session does not implement a complete rubric-history system.

## Commit, retries and readback

Successful first finalization writes official totals, the scoring audit, one advanced
result revision and an immutable finalization receipt atomically. Concurrent retries
produce one initial commit; a lost acknowledgement can be retried safely.

A retry reauthorizes current ownership, validates the saved official snapshot against
the result identity/header and returns it with `scoreChanged=false`. It preserves
the original `scoredAt` and score version. Later rubric edits or evidence purge do
not recompute an already-finalized historical score. Malformed or inconsistent
receipts return `FINALIZATION_STATE_CONFLICT`. Changing an official result requires
the separate audited correction/reopen workflow planned for Milestone 4.

After finalization, Mobile can use these existing, release-gated contracts:

| Route | Purpose |
|---|---|
| `GET /api/v3/mobile/results/{resultUuid}` | Official score, revision, score version and entity/rubric mappings |
| `GET /api/v3/mobile/results/{resultUuid}/analytics` | Backend-authoritative total/max/percentage with readiness status |
| `GET /api/v3/mobile/syncs/{syncUuid}` | Historical upload-stage receipt and per-result reconciliation |

A successful verification sync receipt does not itself finalize the result. Advanced
item analysis, mastery, interventions and Student 360 remain explicitly unavailable.

## Mobile contract package

[Pack 1.8.0](contracts/mobile-v3/1.8.0/README.md) supplies OpenAPI and four synthetic
response fixtures. All 13 included named schemas are identical to their 1.0.0
definitions. Finalization remains bodyless and uses the backend central result ID
from mappings; readback uses durable UUIDs. Written verification uploads continue
using explicit request contractVersion 3.1 from pack 1.7.0. Other wire versions are
unchanged. The replay fixture is from a separate synthetic test result.

## Validation and release boundary

[Validation evidence](V3_MOBILE_WRITTEN_FINALIZATION_VALIDATION.json) records **129
passing focused tests, including 15 new Prompt 13 MariaDB cases**. Coverage includes
mixed/written totals, official readback, incomplete evaluations, stale references,
modified points/comments/criteria, purged/missing/corrupt/detached evidence, forged
reference snapshots, missing objective keys, frozen replay, concurrent finalization,
rollback/lost acknowledgement, malformed receipts and concurrent rubric edits blocked
until scoring commit. Existing scorer and release-gate checks pass. All nine contract
pack validators pass; pack 1.8.0 validates four fresh synthetic runtime responses.

The written fixtures alter disposable question/region metadata after original
ingestion. They validate persistence and finalization, **not printed written geometry,
QR reliability, a physical phone or the complete production upload path**. The
approved production first-capture template remains one-page 10-MC A4.

No migration was added in Prompt 13. Configured baseline remains V3_014: 67 tables /
163 FKs / 87 checks / 99 uniques. The isolated draft chain through V3_020 has 75 tables /
182 FKs / 106 checks / 106 uniques. Only synthetic disposable MariaDB data was used;
the temporary instance was stopped afterward. The school database was not accessed.

Upload HTTP routes remain denied; finalization/readback/evaluation-reference flags
remain false. No Mobile code, SQLite, APK, printed layout, SF1 or V1/V2 changes were
made for this session. Reviewed deployment, secure Mobile sessions, production wiring,
reachable URL and physical end-to-end/scanner acceptance remain Milestone 5 work.

## Next milestone

**Milestone 4 — Audited corrections:** reopen/correction history, superseding and
score-version/analytics consistency. This is planned work, not implemented by Prompt 13.
