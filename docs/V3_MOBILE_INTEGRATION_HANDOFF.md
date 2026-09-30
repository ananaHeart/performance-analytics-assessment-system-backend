# Mobile integration handoff — Milestone 5, Prompt 18

Reviewed 2026-09-14. This is the current integration entry point. Earlier packs
remain frozen historical contracts; their old deployment labels do not describe
the new local staging instance. No Mobile files or production SQLite were changed
by this backend session.

## Environment and evidence

- Backend and Android USB base URL: `http://127.0.0.1:8080` (origin only).
- User-confirmed port layout: backend 8080, Mobile Metro 8081. Previous 18082
  staging instructions are superseded; historical validation logs are unchanged.
- Connect an authorized Android device, then run `adb -s <serial> reverse tcp:8080 tcp:8080`.
  Check `adb -s <serial> reverse --list`. Reapply after reconnect/reboot as needed.
- On 2026-09-14, `adb devices -l` returned no devices. USB reachability and app
  cleartext HTTP behavior have NOT been verified. Permit HTTP only in the local
  debug configuration if needed; production uses an HTTPS hostname.
- Synthetic local account: backend workspace
  `output/local-mobile-staging/runtime/local-test-account.json`. Read locally;
  do not put credentials or bearer tokens into handoff reports or source code.
- `GET /api/v3/system/mobile-release-readiness` must show backendReady=true.
  `fullyConnected=false` is expected until actual acceptance. Configuration flags
  do not prove a device connection.
- Local staging baseline is V3_023: 79 tables, 200 FKs, 131 checks, 118 uniques.
  Normal V3 configuration still expects V3_014. No school database was migrated.
  Central migration version, contract pack, wire version, and SQLite version are
  independent. Never create 79 SQLite tables merely to mirror central counts.

See [staging evidence and operations](V3_PROMPT_18_STAGING_HANDOFF.md).

## Contract map

All paths below are relative to the base URL. Authenticated routes use Bearer.
Pack directories are under `docs/contracts/mobile-v3/` in the backend workspace.
Packs are incremental: **1.11.0 alone is not a complete client specification**.

| Capability | Exact route | Contract source |
|---|---|---|
| Login / MFA | POST `/api/v3/auth/login`, POST `/api/v3/auth/mfa/login/verify` | 1.0.0 SESSION_DOWNLOAD.md and auth schemas; Prompt 17 final session policy |
| Restore / logout | GET `/api/v3/auth/me`, POST `/api/v3/auth/logout` | Same; no refresh endpoint, default expiry 8h |
| Reference / download | GET `/api/v3/mobile/reference-data`, GET `/api/v3/mobile/download` | 1.0.0 plus current V3MobileDownloadResponse.java |
| Sheet manifest | GET `/api/v3/mobile/test-assignments/{assignmentUuid}/answer-sheets/{answerSheetUuid}/manifest` | 1.0.0 |
| Original scan | POST `/api/v3/mobile/scan-pages` | 1.0.0 scan schemas; ingestion/recovery handoffs; 1.11.0 for rescans |
| MC/TF detections | POST `/api/v3/mobile/scan-pages/{scanPageUuid}/detections` | 1.1.0 openapi.json |
| Objective verification | POST `/api/v3/mobile/verification-batches` | 1.2.0 openapi.json, wire 3.0 |
| Evaluation/rubrics | GET `/api/v3/mobile/test-assignments/{assignmentUuid}/evaluation-reference` | 1.5.0 openapi.json |
| Written evidence | POST `/api/v3/mobile/attachments` | 1.6.0 openapi.json |
| Written verification/scores | POST `/api/v3/mobile/verification-batches` | 1.7.0 openapi.json, wire 3.1 |
| Finalization | POST `/api/v3/scoring/results/{testResultId}/finalize` | 1.3.0 and 1.8.0 openapi.json; numeric central ID; no body |
| Result / analytics | GET `/api/v3/mobile/results/{resultUuid}` and `/analytics` | 1.4.0, 1.8.0; current state changes in 1.9.0–1.11.0 |
| Reconciliation | GET `/api/v3/mobile/syncs/{syncUuid}` | 1.4.0 plus later stage supplements |
| Reopen | POST `/api/v3/mobile/results/{resultUuid}/reopen` | 1.9.0 openapi.json |
| Correction and re-finalization | POST `/api/v3/mobile/results/{resultUuid}/corrections` | 1.10.0 openapi.json, wire 3.2 |
| Supersede / lineage | POST `/api/v3/mobile/results/{resultUuid}/supersede`, GET `/api/v3/mobile/results/{resultUuid}/supersession` | 1.11.0 openapi.json |

There is no separate generic manual-score endpoint: numeric/rubric scores,
comments and teacher decisions belong to the written verification contract.
Keep schemas and example fixtures together. Some old fixtures are abbreviated
illustrations, not scan-ready manifests or image bytes.

## Mobile alignment found in current source

Read-only inspection found existing auth support in
`D:/ThesisProjects/MobileAssessmentApp/src/services/v3/authClient.ts`.
`src/database/v3/migrationPlan.ts` still has all three production migration,
endpoint-switch and V2 carryover authorization constants false. Its pending
backend list is now stale for identity resolution, upload, scores and schedules;
reconcile each against the implemented contract before changing gates.

Backend download includes `classAssignmentSchedules` and
`classAssignments[].classStatus`. The Mobile V3 service source search did not find
these fields; inspect parser/types and persistence and add explicit alignment.
Do not substitute assignment status for class status. Preserve schedule timezone,
effective dates and status. Local image paths remain device-only.

## Bounded Mobile sessions (30–45 minute targets)

1. **M1 — session and download alignment.** Review active entry points, implement
   secure OS-backed token persistence and `/me` restoration, expiry/401 re-login,
   schedules/status parsing, and validate parallel SQLite. Preserve V1/V2 and
   unsynced work. No token in logs, ordinary SQLite or plain preference storage.
2. **M2 — objective queue integration.** Wire scan, detection and teacher
   verification stages with durable operation UUIDs; finalize using the resolved
   central result ID; retrieve authoritative result/analytics. Validate app
   restart and lost-response retries against local staging.
3. **M3 — written and audited changes.** Wire evaluation reference, answer crops,
   manual/rubric decisions, then reopen/correction/rescan/supersession UI. Use
   current result revisions and explicit reasons; never overwrite old evidence.
4. **M4 — device acceptance with Frontend.** Validate safe activation/rollback,
   offline scanning, process restart, retry and official report consistency on
   the presentation phone. Production SQLite cutover needs the concrete reviewed
   migration/backup and the owner's applicable authorization; this guide does
   not silently enable it.

Each session reports completed files, tests actually run, exact blockers and the
next remaining session. Do not reopen backend milestones for already implemented
contracts. Report genuine defects with a small reproduction.

## Queue and acceptance rules

- Persist UUIDs, hashes, operation intent and original payload before networking.
  Original upload is `metadata` JSON + `image`; written attachment is `metadata`
  JSON + `file`. Let the transport create the multipart boundary. Max image/file
  contract is 15 MiB; original scan validation additionally restricts format/pixels.
- Send UTC timestamp text ending in `Z`, not a number. Unknown write fields are
  rejected. Use the exact schema version for the operation.
- Retry the same operation with identical UUIDs, payload and bytes. A new teacher
  action uses fresh operation identities; a timeout is not a new action.
- HTTP 200 on a batch is not blanket success. Process every item/page status,
  error and retryability; GET sync after an uncertain outcome, then GET result
  for current revision and mappings. Record central IDs only after resolution.
- Finalization is explicit. Only matching ready officialScore/analytics with
  current version may appear as official. Pending/stale/unavailable values are
  not zero; reopening/superseding invalidates the old current display.
- Analytics in this slice are official total/max/percentage. Item analysis,
  mastery, intervention and Student 360 are unavailable here.
- HTTP acceptance covered the supported **one-page A4 ten-MC capture**. MC/TF
  detection and written scoring component tests do not prove other printed
  layouts can enter through scan upload. Dynamic QR and physical written/TF
  capture require their own acceptance; report unsupported cases explicitly.
- Do not work on SF1 in these sessions.

## Copy-paste instruction for the Mobile AI

> Continue Milestone 5 in D:/ThesisProjects/MobileAssessmentApp. Read
> D:/CAPSTONE_2/backend/assessment/docs/V3_MOBILE_INTEGRATION_HANDOFF.md and its
> staging handoff, then execute M1 only as a bounded session. Inspect actual
> Mobile entry points and preserve dirty work and V1/V2 data. Use the USB local
> backend origin http://127.0.0.1:8080 and the versioned contract map. Implement
> the authorized isolated integration work, validate it, and report pending
> production cutover/device gates honestly. Return any backend issue with route,
> wire/pack version, redacted request, status/error, UUID/revision, expected vs
> actual behavior and reproduction steps. No SF1.
