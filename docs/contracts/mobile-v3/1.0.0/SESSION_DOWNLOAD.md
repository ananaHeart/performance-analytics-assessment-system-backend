# Session, download, and identity decisions

## Authentication policy for the first V3 release

Use the existing opaque bearer session. There is **no refresh-token endpoint**.
Re-login after `expiresAt` or a protected-request HTTP 401; MFA-enabled accounts
complete the new challenge again. The default 8-hour TTL is configurable: use the
returned UTC expiry, never a hardcoded client duration. `/me` does not extend TTL;
backend session activity updates do not constitute renewal.

Existing routes: POST `/api/v3/auth/login`, POST `/api/v3/auth/mfa/login/verify`,
GET `/api/v3/auth/me`, POST `/api/v3/auth/logout`. See current fixtures for both
successful login and the HTTP 200 MFA challenge branch. In the challenge branch,
`mfaRequired=true` and accessToken/user/expiry are null. Never treat that response
as an authenticated session. MFA methods are `authenticator` and `recovery_code`.

Planned Mobile behavior (not implemented here):

| Event | Required behavior |
|---|---|
| Successful login/MFA | Store bearer token using an OS-protected credential store, associated with userId, schoolId, backend origin and expiresAt. Never SQLite/AsyncStorage plaintext. Do not retain password, OTP or recovery code. |
| App restart, online | Load secure session; check expiry and `/me`; require the same active teacher/school/origin before resuming its queue. |
| Offline restart / expired token | Preserve downloaded content and pending evidence. Offline capture/review may continue only within the previously authenticated local owner's workspace and retained assignment policy. No automatic upload or server-authorization claim. Device access protection remains a Mobile acceptance gate. |
| Protected HTTP 401 | Pause network queue, clear unusable credential, show re-login, retain UUIDs/files/payloads. No infinite replay loop. Resume exact requests only after the same owner reauthenticates. |
| HTTP 403 | Stop that operation and show authorization failure; do not repeatedly re-login. |
| Network timeout/lost response | Keep pending; retry identical request identities. Treat outcome as unknown until replay or reconciliation confirms it. |
| Logout online | Request server revocation, then clear local credential even if response fails. A network failure does not prove server revocation; report it accurately. |
| Logout offline | Clear local credential, lock that owner's local workspace; do not delete unsynced evidence. Remote revocation is unconfirmed until server contact or expiry. |
| Different user/school/backend | Do not reuse or upload another owner's queue. No destructive local database reset to switch accounts. |

Memory-only storage remains the current diagnostics implementation. Secure
persistence is a required future Mobile change, not a completed backend feature.

## Read/download contract

Current GETs under `/api/v3/mobile`: `/reference-data`, `/download`, and
`/test-assignments/{assignmentUuid}/answer-sheets/{answerSheetUuid}/manifest`.
All require an active teacher bearer token. Download is a `full_snapshot` of
authorized data; it is not a sync acknowledgement or an authoritative score feed.

| JSON | SQLite destination | Rule |
|---|---|---|
| `classAssignments[].classId` and cohort attributes | `classes` | Deduplicate cohort rows by backend classId. |
| `classAssignments[].classStatus` | `classes.status` | Authoritative cohort status; never copy assignment status into it. |
| `classAssignments[].status` | `class_assignments.assignment_status` | Teacher/subject assignment lifecycle only. |
| `classAssignmentSchedules[]` | `class_assignment_schedules` | Preserve schedule ID/UUID and classAssignmentId, effective dates, schedule status. |
| `classLists[]` | `class_lists` | Membership identity is classListId; studentId is not interchangeable. |
| `questions[].rubricId` | `questions.rubric_id` | Identifier only; current download does not supply rubric criteria. |
| Manifest pages/regions/options | `answer_sheet_pages`, `answer_sheet_regions`, `answer_sheet_region_options` | Retain UUIDs, coordinate space, immutable hashes and versions. Region options are a Mobile projection, not a new central table. |

Schedule `dayOfWeek` uses ISO 1=Monday through 7=Sunday. `startTime`/`endTime`
are local clock values interpreted with `timezoneName` (current schedule service:
Asia/Manila); effective dates are local dates. Capture/open/close/generated/expiry
instants remain UTC. Do not convert local schedule clock strings as UTC instants.

`captureAllowedNow` is a snapshot-time decision, not a permanent offline grant.
Mobile reevaluates cached openAt/closeAt/allowLateCapture and warns on stale data;
Backend checks current ownership/status and plausibility of capture time on upload.
The proposed upload policy accepts an in-window capture uploaded after close;
after-close capture requires allowLateCapture. Archived assignment/inactive
ownership blocks new ingestion. Client capturedAt is evidence, not trusted proof:
future-time/skew and suspicious offline dates require review, not silent acceptance.

Validate the entire snapshot before committing a local transaction. Reconcile
downloaded rows within the authenticated scope; never cascade-delete unsynced
results/evidence because a row is omitted from a newer snapshot. Preserve historical
manifests used by pending captures. Missing rows are not deletion authorization.
Current Mobile lacks schedule/classStatus parsing/persistence despite having the
schedule table; that is a separate Mobile alignment change.

Current read DTOs contain no answer keys, correct-option flags, accepted answers,
official totals, or analytics. Future evaluation-reference returns only teacher
evaluation criteria/maximums, not objective keys or accepted-answer solutions.

## IDs and resolution

| Identity | Backend resolution / Mobile rule |
|---|---|
| schoolId, userId | Derive from authenticated session; never trust payload ownership. |
| classId | Cohort; not classAssignmentId. |
| classAssignmentId | Teacher/subject/cohort assignment. |
| studentId | Learner identity; not enrollment. |
| classListId | Existing backend enrollment; must belong to the delivery's class/school. |
| testId / testPartId / questionId | Definition / part / question, never substitute IDs. |
| assignmentUuid / testAssignmentId | UUID / numeric ID of delivery, not teacher assignment. |
| answerSheetUuid | Resolve owned `answer_sheet_versions.answer_sheet_uuid`. |
| pageUuid | Resolve page under that exact generated sheet. |
| regionUuid | Resolve region under that page; verify matching questionUuid/type. |
| scanUuid / scanPageUuid | Client-generated session / capture UUIDs; stable on retries. |
| resultUuid / answerUuid | Client-generated learner result / answer UUIDs; stable on retries. |
| detectionUuid / attachmentUuid / verificationUuid | Stable event/evidence identities; never regenerate on transport retry. |
| operationUuid | Proposed durable request receipt identity; not a SQLite row ID. |
| syncUuid | One stage batch for one teacher/school/delivery; retries reuse it. A scan batch covers the pages of one fixed result/scan/sheet; a verification batch has a fixed result-item set. Later stages/rescan sessions use a new syncUuid. |

Backend walks teacher -> class assignment -> delivery -> class-list enrollment,
sheet -> page -> region -> question, and scan -> result links before writes.
Same-school access alone is insufficient. Cross-owner UUID probes return 404
without disclosing IDs; a non-teacher is rejected by role rules. Version/geometry
mismatch in owned data returns 409. Numeric IDs must be positive JS-safe integers;
if actual backend IDs exceed that range, a coordinated string-ID contract is
required before Mobile storage (never silently round them).

Proposed reconciliation returns `idMappings[{entityType,uuid,centralId}]` for
`test_result`, `scan_session`, `scan_page`, `answer_sheet_version`,
`answer_sheet_page`, `answer_sheet_region`, `student_answer`, `omr_detection`,
`answer_attachment`, `scan_verification`, `answer_verification`, `sync`.
Mobile maps these to its matching nullable `central_*_id`, retaining local primary
keys and UUIDs. Criterion IDs come from evaluation-reference. Never upload local
autoincrement IDs, image_uri/file paths, server storage keys, userId, schoolId,
verifiedByUserId, isCorrect, result totals, or an official performance category.

The current scan response exposes only `backendScanPageId`; preserve that shape.
Other mappings come from the proposed result readback. Original scan upload has
no attachmentUuid field; Backend allocates the original attachment UUID once and
returns it in readback so a later normalized page can reference it.

## Network policy

Native React Native requests are not subject to browser CORS enforcement.
The server must still be reachable and authorize the request. Local physical
device access uses a configured LAN origin or `adb reverse tcp:8082 tcp:8082`;
localhost on the phone only reaches the computer with forwarding. Android emulator
default is `http://10.0.2.2:8082`. Production uses a configurable HTTPS hostname.
No wildcard CORS change, hardcoded production LAN address, or disabled production
TLS verification is part of this contract. Stable URL/device connectivity remains
an acceptance test; this handoff does not assert a reachable production server.
