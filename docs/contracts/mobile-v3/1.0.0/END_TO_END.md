# End-to-end target contract

All new routes below are **PROPOSED_NOT_IMPLEMENTED** unless explicitly marked
existing. JSON examples and field schemas are in index.json and schemas.json.
The complete flow is unavailable until the implementation/release gates pass.

## Route decisions

| Method/path | Body / purpose | Current status |
|---|---|---|
| POST `/api/v3/mobile/scan-pages` | multipart metadata + original JPEG image | EXISTING_DTO_ONLY |
| POST `/api/v3/mobile/scan-pages/{scanPageUuid}/detections` | immutable per-page detection batch | PROPOSED_NOT_IMPLEMENTED |
| POST `/api/v3/mobile/attachments` | multipart metadata + file | PROPOSED_NOT_IMPLEMENTED |
| POST `/api/v3/mobile/verification-batches` | independent result items containing page decisions and/or answer evaluations | PROPOSED_NOT_IMPLEMENTED |
| GET `/api/v3/mobile/test-assignments/{assignmentUuid}/evaluation-reference` | rubric criteria and safe evaluation metadata | PROPOSED_NOT_IMPLEMENTED |
| POST `/api/v3/scoring/results/{testResultId}/finalize` | no body; existing numeric-ID finalizer | SOURCE_IMPLEMENTED_UNVERIFIED |
| POST `/api/v3/mobile/results/{resultUuid}/reopen` | reason and expected revision/score version | PROPOSED_NOT_IMPLEMENTED |
| POST `/api/v3/mobile/results/{resultUuid}/supersede` | link a verified finalized replacement result | PROPOSED_NOT_IMPLEMENTED |
| GET `/api/v3/mobile/results/{resultUuid}` | authoritative result readback / UUID mappings / pending reasons | PROPOSED_NOT_IMPLEMENTED |
| GET `/api/v3/mobile/syncs/{syncUuid}` | durable per-result and per-page outcome readback | PROPOSED_NOT_IMPLEMENTED |
| GET `/api/v3/mobile/results/{resultUuid}/analytics` | version-bound backend analytics availability | PROPOSED_NOT_IMPLEMENTED |
| GET `/api/v3/reports/assessment-results?testId=...&classAssignmentId=...` | existing scoped report | SOURCE_IMPLEMENTED_UNVERIFIED |

All new operations require an active assigned teacher in the owning school.
There is no principal override in this initial Mobile contract. All new JSON
writes include contractVersion=3.0. Every write has one syncUuid; writes other than
the existing scan metadata also have operationUuid. Use a new syncUuid for a new
stage batch. A verification batch contains one delivery and a fixed item set.
A scan-stage batch may receive separate page POSTs using one syncUuid, but its
teacher/delivery/resultUuid/scanUuid/answerSheetUuid are fixed by the first request.
The manifest determines its required page set. Each scanPageUuid is a separate
immutable child receipt; never hash the first page as the payload of the whole
sync batch. A rescan capture uses a new syncUuid with the same scanUuid and a new
scanPageUuid. Detection/attachment/lifecycle batches contain one request each.

## Original scan-page upload: first implementation slice

Keep `V3ScanPageUploadMetadata` and `V3ScanPageUploadResponse` unchanged. Multipart
parts: `metadata` Content-Type application/json, `image` Content-Type image/jpeg.
Metadata fields are exactly the existing typed fixture: contractVersion, syncUuid,
resultUuid, scanUuid, scanPageUuid, answerSheetUuid, pageUuid, assignmentUuid,
classListId, pageNumber, captureNumber, scannerVersion, qrPayloadHash, imageHash,
capturedAt. UUIDs match the existing canonical lowercase version-1-through-5 regex;
new clients generate v4. SHA-256 values are lowercase hex. Preserve original bytes.

Target limits: 15 MiB/file, one image, 64 KiB metadata, at most 40 million decoded
pixels. These are implementation targets, not configured limits today. Sniff and
decode JPEG; reject MIME/byte mismatch, malformed/truncated images and oversized
dimensions. Reject paths/URLs instead of bytes. Compute SHA-256 on received original
bytes and compare imageHash. Filename is untrusted and never a storage key.

Resolve sheet/page and derive stored QR payload; verify supplied hash against the
stored manifest and its own payload hash. The legacy QR does not encode page or
learner identity. Do not infer either from its testId or claim server image QR
decoding was performed. Validate page number, template/scanner eligibility and
test/sheet snapshot before acknowledgement. Do not modify QR payload or geometry.

For a new resultUuid, Backend allocates a draft result with server-assigned attempt
number and maps classListId to studentId. For a new scanUuid, create its session
and result link. One scan session belongs to one result/sheet. Allocate original
attachment UUID once and store `original_page`, its checksum/size/MIME and private
storage identity. No detections, teacher decisions or official scores are accepted
in Phase 1. A repeated page may not create a new result, session or attachment.

First success: HTTP 201, uploadStatus=created. Exact replay: HTTP 200,
uploadStatus=replayed, same backendScanPageId/contentHash. The original receipt's
pageStatus remains stable even if the current page is later superseded; acknowledgedAt
is the response acknowledgement time and may advance on replay, as in the existing
fixtures. Use GET readback for current state. Errors never acknowledge ingestion
as success.

File/database ordering must be recoverable: stage privately, verify bytes, resolve
owned records and lock identities, persist/publish with transaction compensation
and durable recovery tracking. Never mark accepted until both bytes and committed
records are retrievable. A crash between storage and commit must not lose evidence
or acknowledge a dangling row. Concrete storage implementation is a later slice.

Rescan rule for later verification integration: an accepted rescan request appends
a decision and marks the old page rescan_requested. A subsequent scan-pages upload
with the same scanUuid/pageUuid and new scanPageUuid/captureNumber may replace it
only when captureNumber is the previous maximum + 1 and its predecessor is the
current rescan_requested page. Under one lock/transaction, mark predecessor
superseded, link supersedes_scan_page_id and insert replacement. This ordering is
required by uk_scan_pages_current. Never mutate original bytes or reuse its UUID.
No replacement of an accepted page/result without the correction workflow.

## Detections

Per-page JSON batch fields: contractVersion, syncUuid, operationUuid, detections.
Each observation has detectionUuid, regionUuid, questionUuid, detectionStatus,
detectedOption and confidence. Statuses: detected, blank, multiple_marks, uncertain.
Use stored option A/B/C/D, not the display label; TF T->A and F->B. `detected`
requires exactly one valid stored option; other statuses require null. Confidence
is a number 0..1, not a score. Never accept `isCorrect` or `pointsEarned` here.

Batch maximum 200 observations, one per region on the path page. Backend checks
region/question pairing, objective type, permitted option, page status and owner.
Every observation is immutable; changed UUID reuse conflicts. Persist the whole
page batch atomically; no partial observation acceptance. Result readback returns
resolved detection IDs. A detection alone never finalizes an answer or result.

## Additional attachments

Multipart parts: metadata application/json and file image/jpeg or image/png.
Same file/pixel limits as original capture. Required metadata includes syncUuid,
operationUuid, attachmentUuid, resultUuid, scanPageUuid, attachmentType, contentHash,
fileSizeBytes, mimeType, capturedAt and explicit nullable regionUuid,
sourceAttachmentUuid and crop. Supported new types: normalized_page, answer_crop,
teacher_evidence. Original evidence enters only through scan-pages; legacy
full_sheet/page_image values are not new upload types. Enhanced crops use answer_crop;
`enhanced_answer_crop` is rejected.

- normalized_page: sourceAttachmentUuid must resolve to original_page in the same
  page/session/result; regionUuid/crop are null; no cycles or cross-owner lineage.
- answer_crop: requires regionUuid and crop; sourceAttachmentUuid must be null
  because current SQL/validator permit this field only for normalized_page.
- teacher_evidence: optional regionUuid, null sourceAttachmentUuid/crop.

Crop describes its parent image using `baseAttachmentUuid`, coordinateSpace
`image_pixels_top_left`, baseWidthPx/baseHeightPx and x/y/width/height. Base must be
a committed original_page or normalized_page in the same scanPage. Bounds must fit
the decoded base image; the region must belong to the manifest page/question.
Store this derivation in crop_coordinates JSON; it is not the self-FK reserved
for normalized pages. Do not confuse pixel crop coordinates with manifest PDF
points. Never alter the source image to fit the crop. The server resolves all IDs.

Response: HTTP 201 created / 200 replayed with attachmentUuid, backendAttachmentId,
contentHash, uploadStatus and acknowledgedAt. Bytes are private; no public file URL
or device path appears in a DTO. Authenticated evidence retrieval can be designed
separately if required; these fixtures do not publish storage keys.

## Teacher verification batches

Request includes contractVersion, syncUuid, operationUuid, assignmentUuid, items
(1..50). Each item has resultUuid, expectedRevision, pageDecisions and answers.
At least one page decision or answer is required. Each result occurs once per
batch; a batch is immutable across retries. Each item is its own atomic database
transaction: one bad result cannot roll back another result's valid work.

`revision` is a proposed aggregate optimistic concurrency counter, distinct from
official scoreVersion. New results begin at revision 1. Every accepted state
mutation advances it. Read result state before a new mutation; identical replay
returns its original acknowledgement without rechecking old expectedRevision.
Where not supplied (scan/detection/attachment endpoints), immutable keys and
owner/state locks still prevent replacement of finalized or superseded work.

Page decisions: verificationUuid, scanPageUuid, action accepted/rescan_requested/
rejected, nullable reasonCode/comment, clientDecidedAt. Non-accepted action requires
reasonCode. The server stamps actual verifier and verifiedAt. Decisions are
append-only; client time is retained as evidence, not used as server audit time.
Objective answer acceptance requires its current page already accepted or accepted
in the same item; unresolved/uncertain marks cannot be converted into a chosen
answer. Request a rescan or reject them instead.

Each answer has answerUuid, verificationUuid, questionUuid, regionUuid,
scanPageUuid, evaluation, comment and clientDecidedAt. `evaluation` is one of:

| kind | Fields | Meaning |
|---|---|---|
| objective | detectionUuid | Backend derives answered/blank and option from accepted stored detection; no teacher-selected replacement or numeric score. |
| manual | answerStatus answered/blank, responseText nullable, attachmentUuids[], points | Identification, enumeration or essay without rubric; points 0..question maximum, <=2 decimal places. Blank requires 0 points. Answered requires text or retained crop/evidence. |
| rubric | answerStatus, responseText nullable, attachmentUuids[], rubricId, criterionScores[] | Essay with rubric; each criterion has rubricCriterionId, pointsAwarded, comment. Backend validates complete unique criteria and computes sum. No independently authoritative essay total. |

All referenced attachments must be committed, same result/page/question and not
purged. Preserve written evidence; text is teacher transcription, not automatic
grading. Enumeration completeness follows the downloaded expectedResponseCount;
the teacher awards bounded points; Mobile never matches hidden accepted answers.
Empty or missing required rubric criteria, foreign criteria, inconsistent rubric
IDs and over-maximum scores fail the entire result item. Client-supplied maximums,
correctness, verifier IDs, timestamps of official finalization and totals are rejected.

Backend appends page decisions to scan_verifications. Non-objective evaluation
history belongs to answer_verifications and versioned answer_rubric_scores;
objective decisions must not misuse the non-objective correction table. Populate
verified_by_user_id, server verified_at and finalized_at/evaluation_status on
accepted individual answers as required by the existing scorer. Result remains
pending_verification until explicit successful result finalization. A partial
answer set may be stored, but cannot finalize an incomplete assessment.

Response HTTP 200 for a well-formed processed batch, even with failed items:
envelope success=true means request processed, **not every item accepted**.
data has syncUuid, operationUuid, syncStatus success/partial_success/failed and
items. Each item contains resultUuid, status success/failed, disposition
created/replayed/rejected, nullable revision, idMappings[], nullable error
{code,message,retryable}. Mobile checks every item. A malformed/unauthorized batch
uses a top-level 4xx and writes no result item.

Exact replay of a completed immutable batch returns its recorded outcomes with
successful dispositions replayed. Transient failed operations can be retried with
the same syncUuid/operationUuid; successful items remain untouched. To correct
payload or expectedRevision, first reconcile and send a NEW operation/sync UUID
while retaining existing entity UUIDs where identities are unchanged. Reusing the
same operation UUID with modified content is always a 409 conflict.

## Evaluation reference (new read, prerequisite for offline rubrics)

Owned GET returns contractVersion, assignmentUuid, testVersionNumber,
evaluationReferenceHash, questions[{questionUuid,maximumPoints,rubricId,
expectedResponseCount}], rubrics[{rubricId,name,criteria[{rubricCriterionId,name,
maximumPoints,isRequired}]}]. No answer keys, accepted answers or model solutions.
Store an immutable version-bound snapshot before offline essay review. This data
is NOT present in current download: do not imply rubricId alone is sufficient.
Separate Mobile storage/upgrade work is required after this endpoint is released.

## Finalization, reopen and superseding

Existing finalization POST takes testResultId in path and no body. It derives
official totals from stored verified answers; it accepts neither a Mobile total
nor an expectedScoreVersion request field. Resolve the numeric ID through readback.
Unchanged re-finalization is intended to be idempotent. It currently checks answer
verification, ownership, score boundaries and rubric completeness. End-to-end
acceptance must additionally verify page/evidence gates and concurrency with review.

Reopen target request: contractVersion, syncUuid, operationUuid, expectedRevision,
expectedScoreVersion, reasonCode, comment. Only an assigned teacher may reopen a
finalized result. Preserve the previous official score and verification snapshot
in durable history; append an audit event and mark current result pending_verification.
Invalidate current analytics; do not expose old totals as current official values.
Increment revision, retain last official scoreVersion until re-finalization; that
finalization MUST create exactly the next scoreVersion. Existing scorer behavior
needs integration changes for reopened results, not just a new controller wrapper.
Written answers can be corrected with new verification events; objective marks
still require immutable rescan evidence. No history overwrites or answer-key edits.

Supersede target request adds replacementResultUuid to the reopen identity/version/
reason fields. Replacement must already be finalized, same delivery/student and
authorized owner, with a distinct server-assigned attempt identity. Lock both;
reject self-links/cycles/stale versions. Atomically mark old result superseded,
append durable link/reason/history and select the replacement for current reports.
No intermediate state may double-count both attempts. Replaying returns the same
replacement link. Retake versus correction linkage storage is a release blocker,
not implied by the existing result_status enum.

## Read-after-sync and analytics

GET result by UUID returns contractVersion, resultUuid, testResultId,
assignmentUuid, classListId, studentId, resultStatus, revision, scoreVersion,
pendingReasons[], idMappings[], pages[], nullable officialScore and analytics.
`officialScore` is the existing V3ScoredResultResponse shape only when finalized;
otherwise null (including reopened/superseded results). Page readback includes
scanUuid/scanPageUuid/pageUuid, status and originalAttachmentUuid. For rubric rows,
return rubricScoreMappings keyed by answerUuid/rubricCriterionId/scoreVersion with
centralAnswerRubricScoreId, because those rows do not have independent UUIDs.
Mappings must never rewrite Mobile local IDs. A missing UUID returns owned 404,
not an empty successful result. No new upload receipt alone sets a result finalized.

GET sync returns syncUuid, assignmentUuid, syncStatus and items with resultUuid,
status, error, and pageOutcomes[{scanPageUuid,status,error}]. Each page outcome is
an independent acknowledgement; item-level result success is not a claim that
the result is scored. For scan batches, success requires every manifest page in
that fixed scan to have a committed accepted upload receipt; missing pages keep
the stage pending/in_progress, and mixed committed/failed pages yield partial_success.
Verification-stage success applies only to its submitted result items. It does
not imply complete answers or finalization. Store/reconstruct durable page receipts without inventing
multiple sync_items rows for the same (sync_id,result_uuid). Retry state is local
bookkeeping; Backend returns its own authoritative statuses.

GET analytics returns contractVersion, resultUuid, nullable scoreVersion,
status unavailable/pending/ready/stale, reasonCode nullable, generatedAt nullable
and metrics nullable. `ready` requires a finalized current result and matching
scoreVersion. Initial ready metrics may include only the server-computed total,
maximum and percentage; optional itemAnalysis/mastery/interventions/student360
remain null with explicit unavailableModules entries until separately implemented.
Never substitute zero for absent analytics. Non-ready metrics are null. Reopen or
supersede invalidates prior analytics. A client may store metrics only as a
versioned server snapshot, never compute an official substitute.

## Errors, retries, limits and durable receipts

| HTTP / code | Client action |
|---|---|
| 401 AUTHENTICATION_REQUIRED | Pause/re-login; preserve pending payload and evidence. |
| 403 FORBIDDEN | Stop; ownership/role review, not transport retry. |
| 404 RESOURCE_NOT_FOUND | Refresh authorized references; never guess a central ID. |
| 409 IDEMPOTENCY_KEY_REUSE | Permanent conflict for that operation; reconcile identity/content. |
| 409 REVISION_CONFLICT / RESULT_NOT_READY / MANIFEST_MISMATCH | Read current state; resolve before a new operation. |
| 413 PAYLOAD_TOO_LARGE | Re-capture under limits; never silently recompress immutable original bytes and reuse UUID. |
| 415 UNSUPPORTED_MEDIA_TYPE | Correct file type with a new immutable capture/attachment identity. |
| 422 VALIDATION_FAILED / IMAGE_HASH_MISMATCH / LINEAGE_INVALID | Correct inputs or re-capture; no blind retry. |
| 429 RATE_LIMITED | Respect Retry-After if supplied. |
| 503 STORAGE_UNAVAILABLE / temporary server/network failure | Bounded exponential retry with jitter and unchanged identities. |

Generic error envelope is existing ApiResponse with errors.code and optional
field/retryable. Future error codes are targets; existing route-specific codes
remain unchanged. Never expect a missing handler to return a proposed code today.
Top-level failures after staging must not return success; partial outcomes only
apply to the explicitly independent verification-batch items.

Proposed JSON request cap: 1 MiB; strings max 4000 for comments, 10000 responseText,
50 reasonCode, 50 scannerVersion. Schema and domain limits both apply; per-question
maximumResponseLength may be smaller. Canonical payload hash covers every immutable
field and array element in order plus content hashes of binary parts; exclude only
transport boundary, filename, authentication token and acknowledgement timestamps.
Use deterministic UTF-8 JSON serialization with sorted object keys and normalized
numeric values in the implementation; clients do not supply a payload hash.
Receipt key is teacher + operation kind + stable UUID; scan-pages uses scanPageUuid
where the legacy DTO has no operationUuid. Entity identity is distinct from an
operation fingerprint: a result can participate in later sync stages without
changing its UUID, delivery/enrollment or owner. UUID reuse by a different
owner must not leak a receipt. Resolve security before replay. A changed owner,
delivery, enrollment, image hash or immutable identity is never a valid replay.

## Database and runtime release blockers (no schema change in this pack)

1. Live V3_014 inventory/readiness is unverified. Never assume configured counts
   prove an applied schema; do not run V3_000 or any migration during this milestone.
2. sync_items is unique per sync/result and lacks a per-operation/page receipt
   identity. Design durable request fingerprints and page outcomes before enabling
   multi-stage retries. Evaluate reuse of audit structures versus an additive
   migration; document the decision and validate concurrency on a disposable DB.
3. Proposed revision counter, reopen history and replacement linkage need durable
   storage/constraints. Existing scoreVersion and enums alone do not implement them.
4. V3_002 chk_student_answers_response rejects answered rows with both option and
   response_text NULL even though the scoring service accepts retained written
   evidence without text. Resolve this discrepancy before crop-only manual scoring;
   never insert fake transcription/empty text to evade the intended rule.
5. Current Mobile download omits rubric criteria; evaluation-reference and a
   versioned local storage plan are prerequisites for offline rubric acceptance.
6. Only validated A4 10-item MC generation is currently enabled. Written/TF fixture
   shapes do not activate templates or establish physical scanning reliability.
7. No scan storage/POST, verification, correction or reconciliation implementation
   is supplied by this pack. Proposed routes remain denied/unavailable until their
   narrow backend slices pass appropriate tests and capability release review.
