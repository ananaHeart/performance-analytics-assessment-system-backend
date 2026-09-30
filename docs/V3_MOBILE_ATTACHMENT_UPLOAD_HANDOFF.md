# Milestone 3 — Prompt 11: attachment upload and persistence

Date: 2026-09-12. Component implemented and isolated-tested; production HTTP remains
denied. Mobile source, SQLite, APK, scanner geometry and SF1 were not changed.

## Contract and Mobile responsibilities

`POST /api/v3/mobile/attachments` accepts exactly one `metadata` part with
`application/json` and one `file` part with `image/jpeg` or `image/png`.
[Pack 1.6.0](contracts/mobile-v3/1.6.0/openapi.json) preserves the original 3.0 DTO.
All metadata fields are required, including explicit null fields. Unknown fields,
duplicate JSON keys, trailing JSON, fractional integers, scalar coercions, non-UTC
timestamps and device paths are rejected. Metadata max 64 KiB, file max 15 MiB,
decoded image max 40 million pixels. `capturedAt` must fit the existing MariaDB
TIMESTAMP range (epoch second 1..2147483647). Content SHA-256 and byte count must
match. PNG chunk CRCs, terminal IEND and full decode are validated; APNG is rejected.
Original page upload remains JPEG-only through the existing scan-pages route.

Mobile creates the crop/normalized/evidence bytes and durable attachment UUID,
operation UUID and **fresh sync UUID per attachment operation**. Retry all three
unchanged with identical metadata and file bytes. Keep local paths device-only.
Backend returns 201 created or 200 replayed with attachmentUuid, backendAttachmentId,
contentHash, uploadStatus and acknowledgedAt. Replay preserves the original ID and
acknowledgedAt. Neither response exposes the storage key nor a public image URL.

## Server ownership and lineage

- Server resolves result/page and optional region UUIDs in the active assigned
  teacher's school; original ingress must already be committed. The immutable
  page/version must own the region and its question/test. Existing capture geometry
  is unchanged; this does not enable written or mixed production templates.
- `normalized_page`: sourceAttachmentUuid resolves to the retained committed
  original of this page/session; region and crop are null. Its SQL source FK is set.
- `answer_crop`: requires a question region and crop with original/normalized
  baseAttachmentUuid. Top-level sourceAttachmentUuid is null. Derivation is stored
  in crop_coordinates JSON, preserving the normalized-only SQL source FK invariant.
- Crop base dimensions are compared to retained bytes, and bounds are checked in
  top-left image pixels without integer-overflow addition. A normalized base must
  itself derive from the same original. Self-links, crops as bases, foreign pages,
  uncommitted/purged evidence and malformed parent state are rejected.
- `teacher_evidence`: optional question region; source/crop null. It remains scoped
  to the original page/session. No student_answer is fabricated or scored here.

The server validates declared derivation and image integrity; it cannot prove
that client-produced crop pixels depict the intended written answer or that a
normalization is geometrically accurate. Those remain scanner/teacher acceptance
work. Crops may be resized/enhanced; crop bounds describe the base rectangle, not
necessarily output image dimensions. `enhanced_answer_crop` stays unsupported.

## Durability and reconciliation

Draft V3_019 adds one durable attachment intent/receipt table. The backend stages
and validates bytes, commits an immutable request hash plus reserved sync identity,
publishes without overwriting, then atomically inserts evidence, increments result
mobile_revision once, and commits receipt + sync success. New writes require an
active capture context, selected scan, captured/accepted page, matching sheet/test
version and draft/pending_verification result. Finalized/rejected/superseded states
cannot gain new evidence. Exact committed replay still rechecks current ownership
and retained file integrity but does not change result state or revision.

An exact retry recovers a pending intent after rollback/restart using its stored
image identity. Missing pending bytes can be resent. Published corrupt files are
never overwritten. Committed purged evidence is never resurrected. A lost commit
acknowledgement may return an error even though data committed: retry unchanged.
There is no new background attachment recovery scheduler in this slice; Mobile
retry drives recovery. Unreferenced staging from an uncertain intent commit may
require later operational cleanup; there is no destructive cleanup job here.

`GET /api/v3/mobile/syncs/{syncUuid}` now reports pending/failed/success for this
attachment stage, with its result and page. The existing response shape does not
list attachment UUIDs: correlate it using Mobile's saved operation metadata and
use upload replay or result readback for the attachment ID. Result readback now
includes page/session-scoped attachment mappings, still subject to the existing
200-mapping limit. Readbacks do not perform retries or imply score finalization.

HTTP errors distinguish identity/state conflicts, invalid lineage, hash/metadata
errors, missing evidence and storage/integrity failures. Retry storage/unavailable
failures unchanged; investigate identity, lifecycle, purge and integrity conflicts.
Successful evidence persistence does not imply teacher verification or scoring.

## Validation and release

See [recorded evidence](V3_MOBILE_ATTACHMENT_UPLOAD_VALIDATION.json) for test counts,
hashes and isolated MariaDB results. Cases cover JPEG/PNG/crop persistence, normalized
lineage, UUID/hash/size/MIME rejection, foreign result/page, invalid region/bounds,
purge and account state, concurrent retry, database rollback, lost acknowledgement,
missing pending files, corrupt published files and read-after-upload reconciliation.
HTTP tests verify multipart parsing and actual security tests verify denial.

Configured authoritative baseline remains V3_014 (67 tables / 163 FKs / 87 checks /
99 uniques). Isolated V3_019 draft chain has 74 tables / 179 FKs / 102 checks / 106
uniques. No school database migration was run. Attachment sync readback now requires
V3_019; deploy the reviewed chain before enabling its existing readback feature flag.
All upload routes remain denied and readback/finalization/reference flags default off.

## Remaining Milestone 3 work

Next: **Prompt 12 — manual/rubric teacher verification and scoring persistence**,
with explicit reference binding and the image-only answer representation decision.
The existing answered-row SQL check still rejects null text + null selected option;
this slice only stores evidence before answer verification. It neither removes that
check nor inserts fake transcription. Written finalization still needs its own
validation/bridge extension. Production Mobile integration and physical scanner
acceptance remain later coordinated work.
