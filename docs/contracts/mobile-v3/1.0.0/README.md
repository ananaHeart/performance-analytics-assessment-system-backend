# SMART Mobile V3 contract pack 1.0.0

Status: **DESIGN HANDOFF; NOT END-TO-END PRODUCTION READY**.
Source review date: 2026-09-12. No live application or database acceptance claim.

## Read this pack in order

1. [Session, download, and identity decisions](SESSION_DOWNLOAD.md)
2. [End-to-end write and reconciliation contract](END_TO_END.md)
3. [Machine-readable route and fixture index](index.json)
4. [JSON Schema definitions](schemas.json) and `fixtures/`
5. [Validation and release gates](VALIDATION.md)

`index.json` labels every route/fixture as `SOURCE_IMPLEMENTED_UNVERIFIED` or
`PROPOSED_NOT_IMPLEMENTED`. The current scan upload examples have a third label,
`EXISTING_DTO_ONLY`: these are published Java DTO/fixture shapes without a handler.
Use these labels when generating clients. Never expose proposed routes merely
because a fixture parses. The index is an inventory, not an OpenAPI document.

## Version boundaries

| Name | Value / meaning |
|---|---|
| Contract pack | `1.0.0`, a reviewable documentation/fixture edition |
| Existing Mobile wire version | `3.0`; unchanged |
| Proposed new operation wire version | `3.0`, only on the proposed routes |
| Mobile SQLite draft | `3`, `AssessmentStorageV3.db`, 39 declared tables |
| Expected central baseline | `V3_014`, 67 tables / 163 FKs / 87 checks / 99 unique constraints |
| Manifest / template / scanner versions | Separate fields; never infer from pack/wire version |

The latest migration file found is V3_014. These are configured/source baselines,
not a fresh live `information_schema` result. Do not execute the migration
directory as a chain: V3_000 selects the V2 database and contains table drops.

Existing response envelopes stay `{success,message,data,errors,timestamp}`.
Existing endpoints do not acquire a new request body or version field from this
pack. The numeric-ID finalizer still has no request body. New write DTOs reject
unknown request fields. Readers tolerate unknown additive response fields but
must validate required fields and stop on an unsupported wire version.

Frozen implementation target does not mean released capability. A change to a
target requires a new pack edition and synchronized fixtures before client wiring.
Breaking changes to an already released wire contract require a coordinated wire
version change, not just a pack edit. Do not mutate copied legacy fixture files.

## Evidence boundaries

The seven files in `fixtures/current/mobile/` are byte-for-byte copies of the
existing `src/test/resources/contracts/v3/mobile/` examples. Their source paths
and SHA-256 checksums are in the index. They are abbreviated illustrations:
the sample advertises 10 items but contains one question/region, and repeated
hash values are placeholders. They are **not** physically validated complete
manifests or executable image-upload fixtures. No image bytes accompany them.

New authentication/scoring examples are synthetic, matched to source DTO shapes.
New operation examples are synthetic future contracts. Example tokens are dummy
strings and example learner/account data are not read from any live database.
Negative examples carry explicit structural or semantic expected failures.

JSON Schema checks shape; it cannot establish ownership, database uniqueness,
transaction isolation, binary integrity, QR geometry, or device reliability.
See VALIDATION.md for exactly which checks were executed and which remain pending.

## Source anchors (repository-relative paths and reviewed lines)

| Finding | Source |
|---|---|
| Current Mobile GET routes | `src/main/java/com/capstone/assessment/v3/mobile/controller/V3MobileController.java:28` |
| Upload unavailable | `src/main/java/com/capstone/assessment/v3/mobile/service/V3MobileService.java:33` |
| Schedules queried; class state returned | `src/main/java/com/capstone/assessment/v3/mobile/repository/V3MobileRepository.java:177`, `:215` |
| Existing upload metadata/response | `src/main/java/com/capstone/assessment/v3/mobile/dto/V3ScanPageUploadMetadata.java:10`; `V3ScanPageUploadResponse.java:5` |
| MFA/login/logout | `src/main/java/com/capstone/assessment/v3/auth/controller/V3AuthController.java:90`; `V3MfaController.java:40` |
| Session expiry/revocation | `src/main/java/com/capstone/assessment/v3/auth/repository/V3AuthRepository.java:275` |
| Finalizer and scored response | `src/main/java/com/capstone/assessment/v3/scoring/controller/V3ScoringController.java:28`; `scoring/dto/V3ScoredResultResponse.java:9` |
| Teacher verification prerequisite | `src/main/java/com/capstone/assessment/v3/scoring/service/V3ScoringService.java:267` |
| Objective rescan requirement | `src/main/java/com/capstone/assessment/v3/scoring/service/V3ScoringService.java:304` |
| Written/rubric rules | `src/main/java/com/capstone/assessment/v3/scoring/service/V3ScoringService.java:343` |
| Existing JSON reports | `src/main/java/com/capstone/assessment/v3/report/controller/V3ReportController.java:27` |
| Evidence lineage restriction | `src/main/java/com/capstone/assessment/v3/evidence/service/V3AnswerAttachmentLineageValidator.java:26` |
| Scan-page uniqueness | `docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:352` |
| Result-scoped sync uniqueness | `docs/migrations/v3/V3_000_v2_schema_snapshot.sql:579`; `V3_003_security_sync_intervention.sql:102` |
| Crop-only response CHECK | `docs/migrations/v3/V3_002_assessment_capture.sql:144` |
| Baseline counts | `src/main/resources/application-v3.properties:17` |

Related read-only sources: `D:/ThesisProjects/MobileAssessmentApp/src/database/v3/schema.ts:1`,
`contracts.ts:212`, `contracts.ts:356`, `downloadRepository.ts:382`,
`src/prototypes/v3OfflineDiagnostics/V3OfflineDiagnosticsScreen.tsx:119`.
No Mobile, frontend, Java, SQL, configuration, APK, or existing test file is
changed by this pack. SF1 and V1/V2 migrations are outside this milestone.
