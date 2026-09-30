# Frontend integration and design handoff — Milestone 5

Reviewed 2026-09-14. This session inspected Frontend source without editing it.
Backend staging is separate from UI acceptance. Use
[staging operations](V3_PROMPT_18_STAGING_HANDOFF.md) and the
[complete endpoint/contract map](V3_MOBILE_INTEGRATION_HANDOFF.md).

## Existing implementation to preserve

- Workspace: `D:/CAPSTONE_2/web-dashboard`.
- `src/api/apiV3Client.js` already uses `VITE_V3_API_URL`, a shared Bearer client
  and `smart:v3-auth-expired`. It already has `getReportReferenceDataV3` and
  `getAssessmentResultsReportV3`. Extend the existing client instead of creating
  a second token store or independent fetch conventions.
- Keep the existing `AppLayout.jsx`, `TeacherDashboard.jsx`, Reports page and
  navigation/role boundaries. Confirm their live route entry points before edits.
- Reports styling already uses a 1320px maximum width. Preserve the compact SMART
  layout, current labels/data, cool-gray borders, restrained shadows and green
  emphasis for meaningful actions/status. No unrelated dashboard redesign.
- Reports currently enables Assessment Results while advanced report choices
  remain disabled. This backend slice does not enable those other modules.

## Connection and API ownership

For local development set `VITE_V3_API_URL=http://127.0.0.1:8080` and restart Vite.
This follows the user's port correction: backend 8080, Mobile Metro 8081;
previous 18082 instructions are superseded. Frontend itself keeps its existing
Vite port. The later user-requested port alignment also set the Frontend local
V3 URL and Mobile V3 defaults to 8080. Running client processes were not restarted.
The value is an origin, without `/api/v3`. Use the synthetic account file locally
as described in the staging guide. The automated HTTP test verified CORS OPTIONS
from `http://localhost:5173`; actual browser UI login/rendering is still pending.

| UI work | Backend API and identity |
|---|---|
| Report filters | GET `/api/v3/reports/reference-data` |
| Assessment Results list | GET `/api/v3/reports/assessment-results?testId=...&classAssignmentId=...`; numeric IDs from reference response |
| Teacher result state | GET `/api/v3/mobile/results/{resultUuid}`; durable UUID supplied by actual result data |
| Basic official analytics | GET `/api/v3/mobile/results/{resultUuid}/analytics` |
| Finalize eligible result | POST `/api/v3/scoring/results/{testResultId}/finalize`; resolved numeric central result ID, no body |
| Audited reopen/correction | Mobile contract packs 1.9.0 and 1.10.0; UUID, expected revision, unique operation ID, reason |
| Replacement link | Pack 1.11.0 supersession readback |

Mobile routes enforce assigned-teacher ownership. A principal report view must
use its supported report APIs; do not call teacher-only mutations merely because
the principal can view the assessment. Foreign/unknown teacher resources can
return 404. The current `V3AssessmentResultsReportResponse.StudentResultRow`
contains `testResultId` but **does not contain `resultUuid`**. Therefore the existing
report row alone cannot open a Mobile UUID detail route. F1 can complete report
display; defer the UUID detail link until an additive backend field/contract is
provided, or use a UUID already returned by a supported owned download/readback.
Never guess a UUID from the numeric ID. This is a known cross-client linking gap,
not a reason to block the existing Assessment Results list.

## Reviewable UI design

**Results page:** reuse current report filter bar and Assessment Results table.
Show learner, assessment, result status and backend official total/max/percentage
where the returned DTO supplies them. Use the backend's current attempt selection;
do not count superseded attempts as additional current learners.

**Result detail drawer/panel:** where a real result UUID is available, show status,
page/verification progress, official score state and backend revision/version.
Keep technical operation UUIDs in diagnostics rather than normal teacher flow.
Use a compact two-column layout on desktop and stacked layout on narrow screens.

**States:** finalized/ready gets a restrained green badge; pending verification
is neutral or amber; reopened/stale explains that the official score is awaiting
correction; superseded points to the replacement when returned; unavailable is
an explicit empty state. Do not turn null into zero or build charts with fake data.
Include loading, no results, denied scope, expired session and retryable failure
states. Retain filters after recoverable failures and cancel/discard stale fetches
when switching assessment/student rapidly.

**Teacher actions:** finalize only when eligible; show a deliberate reason form
for audited reopen/correction. Retain the operation UUID for a network retry and
handle revision conflict by fetching current state. Refresh result and analytics
after success; do not locally invent the new official score. Keep corrections
optional to the first presentation view if existing Mobile owns that interaction.

**Evidence boundary:** the reviewed V3 Mobile controllers offer attachment upload,
not an authenticated image-download API. A filesystem storageKey is not a browser
URL. Do not implement broken preview buttons or expose the evidence directory as
public static files. Initial presentation review can use the Mobile-held image;
if browser evidence review is required, return the exact secure media-read
contract gap to Backend. It is not included in this session's completion claim.

## Two bounded Frontend sessions

1. **F1 — existing reports and official result display (30–45 min target).** Inspect
   live routes and DTOs, connect the local staging origin, preserve current design,
   wire valid result detail links, and validate filters/status/official values.
   Run focused lint/build and inspect actual browser loading/error/empty/results.
2. **F2 — lifecycle feedback and shared acceptance (30–45 min target).** Implement
   only supported teacher actions needed for the presentation, stale/reopened/
   superseded feedback, and verify the same learner/result shown on Mobile and
   Web after finalization/correction. Report backend field/authorization gaps
   concretely; never fabricate a route or recompute official analytics on Web.

No SF1, no broad styling rewrite, and no claim of completed Frontend acceptance
from a successful backend CORS response.

## Copy-paste instruction for the Frontend AI

> Continue in D:/CAPSTONE_2/web-dashboard. Read
> D:/CAPSTONE_2/backend/assessment/docs/V3_FRONTEND_INTEGRATION_DESIGN_HANDOFF.md
> and the linked staging guide, then execute F1 only. Inspect the active routes,
> shared API client, report DTOs and existing layout before editing. Preserve
> dirty work, labels, role boundaries and compact SMART styling. Connect the
> authorized local staging origin through VITE_V3_API_URL. Use backend official
> values and genuine UUID mappings, validate the UI and report exact remaining
> defects or unsupported media/detail contracts. No SF1 or invented analytics.

Both client AIs should return a handoff report listing changed files, tests,
observed API statuses and acceptance gaps. Backend communication uses those
reports and a minimal redacted reproduction, not contradictory copies of DTOs.
