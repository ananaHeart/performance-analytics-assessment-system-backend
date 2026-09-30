# Dynamic A4 backend contract — implementation handoff

This extends the V3 Mobile contracts with a real dynamic A4 **multiple-choice
(A–D)** generator. It does not enable Letter/Legal or written/mixed-layout
generation. Existing written scoring endpoints remain separate capabilities.
Physical scanner acceptance is pending; a successful PDF/QR/backend test is not
physical acceptance.

## Exact versions and geometry

| Field | Implemented value |
|---|---|
| Template code | `OMR-A4-DYNAMIC-CTX-V3` |
| Template version | string `"3"` |
| QR payload version | integer `3` |
| Manifest version | integer `2` |
| Download/manifest/upload wire contract | string `"3.0"` |
| Minimum scanner version | `3.0.0` (numeric dotted version) |
| Paper | A4 portrait, 595.276 × 841.890 PDF points |
| Coordinates | `pt`, `pdf_bottom_left` |
| Print scale | 100%, no Fit to Page |
| MC options | A/B/C/D |
| Bubble radius / row pitch | 6.4 pt / 34 pt |
| Body top / bottom | 603.890 pt / 80 pt |
| Page capacity | 16 questions, one column |
| Maximum pages | 12, existing versioned service policy |
| Assessment size | 5–192 questions |

Capacity is `floor((603.890 - 80 - 2×6.4) / 34) + 1 = 16`.
The 17th row would exceed the body boundary. Each page retains the same bubble
radius and spacing. Maximum questions are `16 × 12 = 192`; this is the implemented
layout/pagination capacity, not a universal A4 or scanner limit. Counts 1–4 and
193+ are rejected. Five, seven and ten questions fit on one page; 17 uses two.
The final page can contain fewer than five questions; the minimum applies to the
whole assessment.

## Generation and download

The owning active teacher calls:

```http
GET /api/v3/test-assignments/{testAssignmentId}/answer-sheet-eligibility?paperSizeCode=A4
POST /api/v3/test-assignments/{testAssignmentId}/answer-sheet-versions
Content-Type: application/json

{"paperSizeCode":"A4"}
```

Generation requires active assessment/class assignment, planned/open delivery,
valid item/part snapshots, four active A–D options, an option answer key, and skill
mapping coverage. It creates a new sheet UUID and generation number, page UUIDs,
region UUIDs, PDF, hashes, and audit record. It does not produce fixed V2 sheets.

```http
GET /api/v3/answer-sheet-versions/{answerSheetVersionId}/pdf
GET /api/v3/mobile/download
GET /api/v3/mobile/test-assignments/{assignmentUuid}/answer-sheets/{answerSheetUuid}/manifest
```

Download returns `answerSheets[]` containing `answerSheetUuid`, `assignmentUuid`,
`testAssignmentId`, `paperSize`, `generationNumber`, `testVersionNumber`,
`totalQuestions`, `totalPages`, `manifestVersion`, `manifestHash`,
`requiredScannerVersion`, and `generatedAt`. Use the separate authenticated
manifest endpoint for pages and coordinates. Download never generates a sheet.

## UUID QR v3

Compact JSON key order is exactly:

```text
v, as, pg, ta, pn, pc, tc, tv, gh
```

- `v`: integer 3; `tc`: exact template code; `tv`: string `"3"`.
- `as`, `pg`, `ta`: raw 16-byte sheet/page/assignment UUIDs encoded as 22-character
  base64url without padding. Decode them to canonical UUID strings for API use.
- `pn`, `pc`: one-based page number and total page count.
- `gh`: all 32 bytes of the page SHA-256 geometry digest encoded as 43-character
  base64url without padding.
- Compact UTF-8 JSON, maximum 256 bytes, QR error correction M.
- `qrPayloadHash`: lowercase SHA-256 hex of the exact QR payload bytes.
- No student, LRN, answer key, score, or numeric assignment fallback in QR.

Do not substitute the older prototype's numeric `tv` or its prerelease scanner
version string. Use this implemented contract and returned manifest.

## Manifest v2 fields

Existing top-level identities remain unchanged. Each `pages[]` entry contains:

```text
pageUuid, pageNumber, totalPages
template { code, version, geometryHash }
qr { payloadVersion, payload, payloadHash, errorCorrection }
coordinateSpace { unit, origin, width, height }
pageGeometryHash
templateRegions[]
regions[]
```

`template.geometryHash` identifies the reusable template. `pageGeometryHash`
identifies that page's geometry/question mapping and is the digest represented by
QR `gh`; do not compare `gh` with the template hash.

`templateRegions[]` makes each dynamic manifest self-contained: four registration
markers, one QR rectangle, and 16 reusable MC slots. Entries have `regionCode`,
`regionType`, `rectangle`, `geometry`, `geometryHash` and the existing reference
region fields. Markers use the stored nested-square geometry. QR rectangle is
96 × 96 pt at `(463.276, 663.890)` with an 8 pt quiet zone.

`regions[]` contains **only the actual questions on this page**, with durable
`regionUuid`, `questionUuid`, numeric question/part IDs, global and part-local item
numbers, question/region type, rectangle, geometry hash and option coordinates.
Unused template slots are not question regions and must not be uploaded as answers.

## Upload and reconciliation — identities preserved

`POST /api/v3/mobile/scan-pages` remains multipart with exactly `metadata`
(`application/json`) and `image`. Metadata remains:

```text
contractVersion="3.0", syncUuid, resultUuid, scanUuid, scanPageUuid,
answerSheetUuid, pageUuid, assignmentUuid, classListId,
pageNumber, captureNumber, scannerVersion, qrPayloadHash, imageHash, capturedAt
```

One request uploads one physical page. Keep `resultUuid`, `scanUuid`, sheet and
assignment identity stable across all pages of a result. Each capture has a
distinct `scanPageUuid`; retries reuse its UUID and identical metadata/image.
`pageUuid` comes from the backend manifest. UUID-to-central-ID resolution stays
server-side. Capture numbers are 1–5 **per page**, not per assessment.

The existing detection and verification DTOs are unchanged:

- `POST /api/v3/mobile/scan-pages/{scanPageUuid}/detections`: objective contract 3.0.
- `POST /api/v3/mobile/verification-batches`: objective 3.0; written 3.1 remains a
  separate existing contract, not a capability of this MC generator.
- `POST /api/v3/scoring/results/{testResultId}/finalize`: numeric backend result ID;
  no new request body.

Upload verifies the QR's sheet/page/assignment UUIDs and page counts, scanner
version, stored geometry digest, current assessment version and question mapping.
It accepts dynamic pages in any page order. A page acknowledgement does not mean
the result is finalized. Sync readback reports `in_progress` until committed captures
cover every manifest page, including captures uploaded under another sync UUID.
Rescan history is retained and does not inflate the count of current pages.

Finalization requires every current page to be teacher-accepted, every objective
answer verified, and exactly one retained original with a committed receipt and
verified content hash per current page. It rejects missing pages, broken rescan
lineage, missing evidence, or incomplete answers. Rescanning an unaccepted page
does not discard accepted answers on other pages. Official results still require
the existing audited correction/reopen/supersession workflow.

Result readback supports up to 1,071 identity mappings for dynamic manifests
(bounded from 192 questions and 12 pages with rescan history). Legacy results keep
their existing 200-mapping limit. Mobile must accommodate the dynamic bound.

## Local staging and acceptance boundary

- API remains `http://127.0.0.1:8080`; Metro remains 8081.
- Staging uses an isolated loopback MariaDB instance on 33319 and a database named
  `v3_dynamic_staging_<timestamp>`, cloned from the existing local V3 data.
- V3_015–V3_023 install the release workflow schema; V3_024 adds the immutable
  dynamic template and 21 regions. Release totals remain 79 tables, 200 foreign
  keys, 131 checks, 118 unique constraints; active templates become 2.
- Profiles: `v3,v3-mobile-release,v3-dynamic-staging`. The dynamic staging guard
  refuses the normal school database and validates the new seed/geometry.
- Writes are initially disabled. The launcher checks all other readiness gates,
  then restarts with writes enabled; startup preflight must pass before serving.
- Existing Windows-protected MFA key is reused through the child environment.
  No MFA factor/password reset, Mobile edit, V1/V2 edit or production migration.
- `mobileWiringVerified`, `physicalScannerVerified`, and `fullyConnected` remain
  false until actual client/device acceptance. Do not change them to hide a failure.

Mobile AI should align manifest v2/QR v3 parsing and geometry first, then verify
assignments 1005 (7 items) and 1006 (10 items) using the exact returned identities.
Do not fabricate identities or reuse fixed-template QR payloads. This document is
the implementation handoff; it does not authorize an automatic production cutover.

## Verified local deployment — 14 September 2026

The running API on **8080** uses `v3_dynamic_staging_20260914192006` on isolated
database port 33319. Both readiness routes return HTTP 200; `backendReady=true`,
`databaseReady=true`, and `writeApiEnabled=true`. All release checks passed before
writes were enabled. `fullyConnected=false` remains intentional.

| Assignment | Items / pages | Sheet version ID | Sheet UUID | Page UUID |
|---|---|---|---|---|
| Quiz 1 / 1005 | 7 / 1 | 24 | `75452cc4-90e7-4113-b9d9-3ee0d5a81101` | `1539cca6-64ed-4f69-afb8-db435c7489f7` |
| Quiz 2 / 1006 | 10 / 1 | 25 | `77b81d39-2beb-4997-b4dd-7f03edf6e0d0` | `5c5666d9-e46e-48e9-9799-99401e4ea258` |

Actual authenticated HTTP manifest responses, without credentials:

- [Quiz 1 manifest fixture](contracts/v3/mobile/dynamic-a4-v3/assignment-1005-manifest.json)
- [Quiz 2 manifest fixture](contracts/v3/mobile/dynamic-a4-v3/assignment-1006-manifest.json)

These UUIDs identify this staging snapshot. Always use a fresh download/manifest
after regeneration. Older unused staging generations were retired with audit
records; synthetic boundary assessments were archived, with no evidence deleted.

Validation completed:

- Geometry/QR unit tests: 2 passed.
- Dynamic database workflow test: passed 4/5/17/192/193-item boundaries, PDF QR
  decoding, 17-item and 192-item upload/detection/verification/finalization,
  replay, missing-page rejection, page-local rescan, analytics and reconciliation.
- Readback controller/gate tests: 6 passed.
- Focused existing MariaDB readback/rescan regressions: 21 passed in a fresh
  synthetic schema. This rerun preserves the legacy 200-mapping rejection.
- Maven package succeeded. PDF pages visually inspected.
- Live authenticated HTTP reference-data, download, both manifests and both PDFs
  passed; downloaded PDF hashes match the stored hashes.

The live check used a temporary test-issued session in the isolated clone and
revoked it afterwards. It does **not** certify password/MFA login or a physical
Mobile session. Synthetic PDF captures do not certify camera/scanner accuracy.
The original database still has its original 67-table baseline and zero generated
answer sheets; no release migration or sheet generation was applied there.

For this isolated deployment, the reusable launcher is:

```powershell
powershell -ExecutionPolicy Bypass -File .\run-v3-dynamic-staging.ps1
```

It reuses the validated staging JAR and existing isolated database, keeps API port
8080, and repeats disabled-write preflight when starting. If staging is already
ready, it reports that without restarting. It refuses to replace another backend.
The existing `run-v3-with-brevo.ps1` remains the normal local-database launcher;
it is not the launcher for this isolated staging snapshot.
