# Mobile V3 readback supplement 1.4.0

Prompt 9 implements these existing 1.0.0 contract targets without changing their
wire schemas (`contractVersion: "3.0"`):

| GET route | Purpose |
|---|---|
| `/api/v3/mobile/results/{resultUuid}` | Current result identity, revision, page state, ID mappings and official score |
| `/api/v3/mobile/syncs/{syncUuid}` | Durable acknowledgements for the requested upload/detection/verification stage |
| `/api/v3/mobile/results/{resultUuid}/analytics` | Versioned backend score totals, maximum and percentage |

All require an active assigned teacher's Bearer session. Unknown and foreign
resources return the same 404. Every response is a repeatable-read snapshot.
Successful responses use `Cache-Control: no-store`; Mobile may explicitly persist
an authorized versioned snapshot in its application database later.

The release flag `app.v3.mobile.readback-enabled` /
`V3_MOBILE_READBACK_ENABLED` defaults to false and returns 503
`MOBILE_READBACK_UNAVAILABLE` before accessing any database tables. The existing
V3_015..V3_018 draft chain is required to enable it; Prompt 9 adds no migration.
Existing Mobile write gates remain closed. This supplement does not authorize
production Mobile SQLite migration or replace the V1/V2 flow.

Result readback returns actual numeric IDs alongside durable UUIDs. Never replace
local SQLite identities with those numeric IDs. `rubricScoreMappings` returns any
existing versioned rubric-row identities; this does not enable written/rubric
uploads. Objective verification audit UUIDs have no separate numeric identity in
V3_017, so they are not mislabeled as written `answer_verification` IDs.

Official score and ready analytics require a finalized result whose current
revision, score version, totals and identity match its saved finalization receipt.
Pending results have null official scores and metrics. Reopened/superseded or
mismatched state hides old scores and reports stale analytics. A finalized result
without a Mobile receipt is unavailable, not an invented zero or a recomputed
score. Readback never calls the finalizer or reads current answer keys to rescore.

Initial analytics are **score totals only**. `itemAnalysis`, `mastery`,
`interventions` and `student360` remain null and are listed in
`unavailableModules`. `generatedAt` identifies the original backend scoring time.

Sync status describes only the requested stage. Original-upload success means
bytes and records were committed, not that the teacher accepted the page.
Detection success does not mean verification; verification success covers only
the submitted items, not complete answers or finalization. Pending original
intents can be read even before the result row exists. A failed original upload
can report retryable failure and later success after recovery using the same UUIDs.
Verification reads retain independent successful/failed/pending result outcomes.
Missing manifest pages keep the scan incomplete. The current ingress still only
supports the approved one-page first-capture template.

Reconciliation order: GET sync after an uncertain operation; retain successful
items and retry only according to the unchanged upload/verification contract;
GET result for the **current** revision/central IDs; finalize only when ready;
GET result and analytics for the current official score version. Historical
receipts never override a newer result revision. A missing sync returns 404 and
must not be treated as success; retry an authorized uncertain request unchanged.

Arrays are capped at 200 by the frozen contract. Overflow returns 409
`READBACK_LIMIT_EXCEEDED`, never truncated success. Missing original attachment
rows or malformed receipts return 409 `READBACK_STATE_INCONSISTENT`. Unsupported
non-Mobile sync stages return 409 `SYNC_STAGE_UNSUPPORTED`. No pagination,
reopen/correction, superseding or recovery mutation is introduced by these GETs.

Run `node validate.cjs`; `node validate.cjs --runtime-samples` additionally checks
three synthetic responses emitted by the MariaDB persistence suite. The six
illustrative fixtures are unchanged parent examples; `sync-page-partial` remains
a broader future multi-page example, not evidence of enabled multi-page ingress.
See [Prompt 9 handoff](../../../V3_MOBILE_READBACK_HANDOFF.md) and
[milestone tracker](../../../V3_MOBILE_MILESTONE_TRACKER.md).
