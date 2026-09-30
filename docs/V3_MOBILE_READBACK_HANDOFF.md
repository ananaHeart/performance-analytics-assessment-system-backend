# Milestone 2 — Prompt 9: result/analytics readback and reconciliation

Date: 2026-09-12. **Backend readback components implemented and validated in
isolation. Production Mobile connectivity is not complete.**

See the [consolidated milestone tracker](V3_MOBILE_MILESTONE_TRACKER.md).
Milestone 2 objective backend components are complete through this prompt;
Milestone 3 written-answer workflow is next. Production release and physical
acceptance remain separate work.

## Implemented endpoints and contract

| GET route | Response |
|---|---|
| `/api/v3/mobile/results/{resultUuid}` | Current status/revision, backend result ID, page states, UUID mappings, rubric-row mappings, nullable official score and analytics state |
| `/api/v3/mobile/syncs/{syncUuid}` | Authoritative acknowledgements for the requested original-upload, detection or teacher-verification stage |
| `/api/v3/mobile/results/{resultUuid}/analytics` | Backend official score total, maximum and percentage, tied to the current score version |

[Contract pack 1.4.0](contracts/mobile-v3/1.4.0/README.md) contains OpenAPI 3.1,
six fixtures and a validator. All twelve included schemas are unchanged from the
1.0.0 design pack; wire `contractVersion` remains `3.0`. The validator also checks
three real synthetic response samples produced by the MariaDB suite. The older
six fixtures are illustrative parent examples; their broader multi-page example
does not enable multi-page capture.

The handlers require an active assigned teacher. Live account, assignment,
student membership and school scope are checked in a single repeatable-read
transaction. Foreign and unknown resources both return 404. A result revision
cannot be mixed with a score committed midway through the response. GETs do not
write, retry uploads, increment revisions, finalize results or recompute scores.
Successful HTTP responses use `Cache-Control: no-store`.

## Result and analytics semantics

UUIDs remain durable Mobile identities. `idMappings` reports actual central IDs
for result, captured scans/pages, sheet/version/pages/regions, answers, detections,
original or answer attachments, and applicable page/written verification records.
Mappings do not rewrite Mobile local IDs. Existing rubric identities are keyed by
answer UUID, criterion and score version. Objective audit UUIDs have no numeric ID
in V3_017 and are not mislabeled as written-verification mappings.

`officialScore` and ready analytics require `result_status=finalized` and a matching
saved first-finalization receipt: current revision/version, result/delivery/student
identity, totals, percentage, evaluated count and performance-rule snapshot agree.
The returned official score has `scoreChanged=false`; reading creates no score.
Current answer-key or rule edits are not used to recompute a saved result.

| State | Official score | Analytics metrics |
|---|---|---|
| Draft / first verification pending | null | null; pending |
| Finalized with matching receipt | Saved authoritative score | Ready total/max/percentage |
| Reopened / superseded | null | null; stale |
| Finalized but snapshot/version mismatch | null | null; stale |
| Finalized without Mobile receipt | null | null; unavailable |

`generatedAt` is the original scoring time. Item analysis, mastery, interventions
and Student 360 are explicitly null and listed in `unavailableModules`. No absent
metric is replaced with zero. Existing broader backend analytics are not wired or
claimed validated by this slice.

Pending reasons identify capture, page/answer verification and finalization work.
They are guidance, not a promise that first finalization will pass every evidence
and scoring gate. Finalization still validates actual retained bytes and keys.
Missing original attachment rows or malformed saved receipts return an explicit
state error rather than incomplete successful JSON. Arrays beyond the frozen
200-entry limits return 409; no IDs or pages are silently truncated.

## Sync reconciliation

An upload can have a durable pending intent before a result row exists. GET sync
reconstructs that original-upload outcome from its ledger and owned metadata.
Committed pages report success; pending attempts report pending or retryable
failure. Exact upload recovery changes subsequent readback to success under the
same identities. If the fixed sheet expects more pages than the registered
receipts provide, the item stays pending with `MANIFEST_PAGES_PENDING`.

Detection acknowledgements cover their page's detection stage. Verification
acknowledgements come from durable per-result receipts and preserve independent
success/failure/pending states, error codes and retryability. Page outcomes cover
only pages named in that submitted verification item. Partial result failure
remains visible; retry finishes remaining eligible work while committed items
remain successful. Unknown/non-Mobile sync stages are not synthesized as success.

**Stage success is not result finalization.** Original-upload success means
accepted bytes/persistence, not teacher acceptance. Verification success covers
the submitted answers/decisions, not necessarily every required answer. The
current result GET, not an older operation receipt, supplies the current revision.

Suggested Mobile reconciliation sequence after deployment:

1. GET sync after an uncertain upload/verification response; inspect every item
   and page outcome. Preserve immutable UUIDs and payloads for eligible retries.
2. GET result for current revision, page state and central ID resolution.
3. Invoke the existing bodyless numeric-ID finalizer when prerequisites are met.
4. GET result and analytics again; store only a matching ready score-version
   snapshot. Clear current official display when readback is pending/stale.

A missing sync is 404, not success. An authorized uncertain request may be retried
unchanged using the existing stage contract. GET itself performs no recovery.

## Release boundary

`app.v3.mobile.readback-enabled` / `V3_MOBILE_READBACK_ENABLED` defaults to false.
It returns HTTP 503 `MOBILE_READBACK_UNAVAILABLE` before database access, keeping
the current V3_014 baseline usable without querying missing draft tables. Enabling
the readback bridge requires separately reviewed V3_015..V3_018 deployment and
release checks. Existing GET security requires TEACHER; no security matcher was
relaxed. All Mobile write gates and the finalization flag remain unchanged.

No migration was added. The existing isolated V3_018 chain remains 73 tables,
175 FKs, 98 checks and 104 unique constraints. Configured baseline remains V3_014
(67/163/87/99). No school database was accessed or altered.

## Validation

[Machine-readable evidence](V3_MOBILE_READBACK_VALIDATION.json) records the suite
timestamps and hashes. Focused coverage includes 48 actual MariaDB tests (15 new
readback cases plus 33 existing ingress/detection/verification/finalization tests),
four handler/security tests, two release-gate/validation tests, nine scorer
regressions, five Java fixture checks and three existing Mobile write-gate tests:
**71 tests total**. The existing two-learner partial-failure test also now checks
reconciliation before and after retry.

Cases include revision/ID readback before scoring, finalized score analytics,
private field exclusion, pending intent recovery, historical stage receipts,
partial verification, current ownership changes, missing/foreign resources,
manifest incompleteness, stale/reopened/superseded scores, missing/malformed
receipts, mapping overflow, default-gate compatibility, Bearer role enforcement
and finalization committing during a consistent read snapshot.

MariaDB 10.4.32 used only loopback port 33317 with a separate data directory under
`target` and synthetic records. The temporary instance is stopped after validation.
No application datasource, school server or port 3306 was used. Production HTTP,
physical-phone scans, real power failure and live Mobile SQLite integration were
not exercised. TF remains synthetic, and the approved capture remains one-page
10-MC A4. SF1, V1/V2, Mobile code/SQLite/APK and QR/geometry were untouched.

```powershell
# Start and prepare the isolated instance with the existing tmp helpers first.
$validationPath=(Get-Content target/scan-recovery-validation-path.txt -Raw).Trim()
$testDatabase=(Get-Content (Join-Path $validationPath 'database.txt') -Raw).Trim()
mvn -q '-Dtest=V3ScanUploadMariaDbTest,V3MobileReadbackControllerTest,V3MobileReadbackGateTest,V3ScoringServiceTest,V3MobileContractFixtureTest,V3ScanPageSecurityGateTest' "-Dv3.scan.mariadb.database=$testDatabase" test
node docs/contracts/mobile-v3/1.4.0/validate.cjs --runtime-samples
```

Next: **Milestone 3 — Prompt 10: written-answer contract and evaluation-reference.**
Written evidence/manual/rubric upload, audited correction/reopen/superseding and
production/phone acceptance remain pending.
