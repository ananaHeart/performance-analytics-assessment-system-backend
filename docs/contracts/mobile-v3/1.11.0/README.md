# Mobile V3 pack 1.11.0 — supersession and rescan

Milestone 4 Prompt 16. [OpenAPI](openapi.json) and synthetic fixtures cover the
existing 3.0 supersede and scan DTOs. No named 1.0.0 schema is changed. Initial
objective 3.0, written verification 3.1 and correction 3.2 remain separate contracts.

## Page rescan before answer acceptance

Use existing `POST /api/v3/mobile/verification-batches` with a page-only
`rescan_requested` decision, fresh operation/sync/verification UUIDs, current
revision and required reason. After its success:

- Upload to `POST /api/v3/mobile/scan-pages` using exactly one `metadata`
  application/json part and one `image` part.
- Keep resultUuid, scanUuid, answerSheetUuid, pageUuid, assignmentUuid and
  classListId; use a new scanPageUuid and syncUuid, the next captureNumber, new
  image hash and capturedAt. Captures 1–5 are supported; numbers cannot skip.
- The predecessor must be the latest capture of that same page, with a committed
  teacher rescan request. The result cannot already have accepted answers or an
  official score. Accepted/official work uses the separate-result flow below.
- The transaction marks the predecessor superseded, links
  supersedes_scan_page_id, persists new original evidence and receipt, resets the
  session for verification and advances result revision. One current page remains;
  captured_page_count describes current pages, not photo attempts.
- Old images, detections, scan decisions and upload receipts stay retained. Upload
  retries use the identical metadata/image and return the committed capture receipt.
  A historical receipt still says captured; GET result for current page states.
- Upload fresh detections for the new scanPageUuid, accept the new page and its
  answers, then use existing backend finalization. Old detections cannot be used
  as new-page answers. Finalization checks contiguous audited page lineage and
  scores only answers from the accepted current capture.
- A competing upload that loses the predecessor race receives a conflict. Reconcile
  before forming new intent; do not keep changing an already reserved page UUID.

Existing eligibility stays one-page, ten-MC, A4 with the immutable approved sheet.
Metadata limit 64 KiB, original image limit 15 MiB; existing size/hash/pixel/type
validation applies. No scanner algorithm, printed layout or QR geometry changes.

## Replacing an official result

1. Retain the source UUID and its current revision/score version. An optional
   audited reopen can mark its analytics stale while work is reviewed.
2. Capture the replacement using new resultUuid, scanUuid, scanPageUuid and syncUuid,
   captureNumber=1. The server allocates its distinct attempt number; Mobile must
   not submit a central ID or attempt number. Use the same delivery/student.
3. Complete detection upload, teacher verification, any written evidence/scoring,
   and backend finalization of that replacement using the existing contracts.
4. POST `/api/v3/mobile/results/{sourceResultUuid}/supersede` with the frozen 3.0
   SupersedeRequest: operation/sync UUIDs, expected source revision/score version,
   required reason, nullable comment and replacementResultUuid. Maximum 32 KiB.
5. The replacement must be finalized, currently owned, the latest non-superseded
   attempt, newer than the source, and not already consumed as another source's
   replacement. Its accepted original capture must still validate. Both contexts
   are locked; source can be finalized or an audited reopened official result.
6. Success atomically marks the source superseded, increments source revision,
   stores both official snapshots, appends audit/sync and result/session lineage.
   Scores, answer histories and evidence remain; neither score version increments
   merely because a link is made. Correction/re-finalization uses pack 1.10.0.
7. Retry identical request/identities after timeout or uncertain commit. A committed
   retry returns the original immediate link with disposition=replayed. New content
   under the same operation conflicts. Source expected versions apply to new intent;
   the replacement's current official snapshot is locked and recorded at commit.
8. GET `/api/v3/mobile/results/{sourceResultUuid}/supersession` recovers that durable
   immediate link after app restart. Follow each successor if the replacement was
   itself later superseded. No link returns 404 SUPERSESSION_NOT_FOUND. Both source
   and successor are reauthorized. GET result/analytics and sync for current state.

The sync response has one source-result item with empty pageOutcomes. It acknowledges
the link only. Source analytics return stale/RESULT_SUPERSEDED and no officialScore;
replacement readback exposes its own current official score.

Reports preserve their existing policy: one latest non-superseded attempt per
student/delivery, including a pending attempt while it is being worked on. Thus
the replacement can be the report's current attempt before the explicit link is
made; this pack does not introduce a hidden staging attempt or change retake policy.
Two attempts are never summed in the current assessment report. Per-result historical
readback is not an aggregate; Mobile must follow lineage/current report selection.
Reason/audit identifies the replacement intent, including rescan versus retake.

## Release state and validation

Supersede POST and supersession GET are denied by V3 security; the supersede service
flag defaults false. Existing write/readback/finalization gates remain. Draft V3_023
adds mobile_result_supersessions after V3_022. Configured V3_014 stays unchanged.
School database migration and production activation require the coordinated
Milestone 5 deployment. No Mobile production wiring or phone acceptance is implied.

Run `node docs/contracts/mobile-v3/1.11.0/validate.cjs --runtime-samples` after the
disposable MariaDB suite. Eleven fixtures cover supersede create/replay/link,
source result/analytics, replacement result, sync, and rescan upload/current result.
Fixtures are synthetic examples, not usable school records. The validator checks
the unchanged parent schemas as well as actual service outputs.
