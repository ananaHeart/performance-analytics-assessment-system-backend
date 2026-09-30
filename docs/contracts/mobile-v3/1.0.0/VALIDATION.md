# Contract validation and release gates

Run the read-only fixture validator from any directory:

```powershell
node docs/contracts/mobile-v3/1.0.0/validate.cjs
```

The validator needs Ajv 6 (JSON Schema draft-07). It first resolves an installed
`ajv`, or uses the existing sibling web-dashboard installation on this workspace.
Alternatively set `SMART_CONTRACT_AJV_PATH` to an existing Ajv package directory.
It does not install packages, start servers, execute Java tests or connect to SQL.

Checks: JSON Schema validity; indexed positive/negative fixture shapes; immutable
legacy fixture checksums and source-copy equality; replay identity consistency;
batch partial-success consistency; error codes; score/readback consistency;
attachment crop bounds/lineage examples; private local-path exclusion; route status
labels and index completeness. The existing Mobile read examples are source copies,
not complete generated manifests, so the validator intentionally does not claim
complete geometry/hash or ten-question acceptance from those excerpts.

## Milestone 1 acceptance

- All existing routes and proposed route names are explicitly classified.
- Original scan upload DTO/fixtures remain unchanged.
- Authentication uses existing expiry + re-login; no invented refresh route.
- Schedules, class status, durable UUIDs and nullable central-ID mapping are explicit.
- Versioned examples cover upload/replay/conflict, objective detection, written
  crops/manual/rubric review, batch partial failure, finalization, reopen/supersede,
  result readback, sync outcomes and analytics unavailable/ready states.
- New examples validate structurally; semantic cases state prerequisites.
- Remaining schema/runtime/client work is visible and no capability is enabled.

## Future backend persistence acceptance (not executed in this milestone)

Use a disposable V3-equivalent database and temporary private storage, not the
working school database. Validate real FK/check/unique constraints and transactions.

1. Owned valid original JPEG persists one result/session/page/original attachment.
2. Identical retry and simultaneous duplicate requests produce one identity set.
3. UUID reuse with changed file, owner, enrollment or page never overwrites evidence.
4. Invalid MIME/hash/size/QR relationship leaves no accepted dangling capture.
5. Storage error, DB rollback and process-crash recovery are exercised separately.
6. Multi-page partial upload reports each page; one failure does not falsely mark
   the result complete. Sync item uniqueness is respected.
7. TF stored A/B mapping and uncertain/multiple-mark rescan gates are tested.
8. Foreign page/region/attachment/rubric references are rejected within owned scope.
9. Review batches atomically accept/reject each result and replay successful items.
10. Partial verified answers cannot finalize; manual/rubric bounds and crop-only
    schema behavior are validated against the applied DB, not only mocked services.
11. Finalize/reopen/re-finalize increments score history exactly once, rejects stale
    revisions and invalidates old analytics; superseding cannot double-count results.
12. Lost-response replay and readback agree on UUIDs, numeric IDs and score versions.

## Future Mobile/device acceptance (separate authorized task)

Secure token restart/expiry/logout, same-owner queue resumption, schedule/class
status persistence, preservation of pending rows on download refresh, schema
upgrade/rollback, LAN/adb/HTTPS reachability and offline/network interruption all
require actual Mobile tests. Scanner QR reliability and physical paper validation
are independent blockers; valid API JSON never establishes scanner acceptance.

## Execution record

See `validation-result.json` for the local contract-validator result. That result
is documentation/fixture validation only. No HTTP persistence test, Maven test,
database migration, Mobile test, APK build or physical device test is represented.
