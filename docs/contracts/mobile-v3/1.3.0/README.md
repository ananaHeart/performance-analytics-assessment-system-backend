# Mobile V3 scoring/finalization supplement 1.3.0

Focused OpenAPI 3.1 contract for Prompt 8. The original 1.0.0 pack and its
`OfficialScore`, `PartScore`, and `FinalizationResponse` shapes are unchanged.

`POST /api/v3/scoring/results/{testResultId}/finalize` uses a Bearer session and
**no request body**. Call after every required objective answer and selected page
has an accepted backend verification receipt. Use the backend-resolved numeric
result ID; UUID readback/reconciliation is Prompt 9 and remains pending.

The server computes correctness, earned points, total/max score, percentage,
performance band and part totals. First finalization increments `mobile_revision`
once; that revision is not added to this existing response DTO. An unchanged
retry returns the saved score/time/parts with `scoreChanged=false`. The envelope
timestamp can change. Neither answer-key nor performance-rule edits cause a
replayed Mobile result to be rescored. A lost response is retried using the same
result ID. There is no finalization batch or per-page response at this endpoint.

This bodyless contract has no caller-supplied expected revision. Owner/result locks
serialize finalization with upload and teacher review; they do not detect whether
the caller was looking at an older screen. Prompt 9 must supply authoritative
current-state readback. Do not send Mobile totals or add speculative request fields.

The implementation is limited to first finalization of the current one-page
objective capture. Written-answer upload, audited correction/reopen, superseding,
analytics readback, reconciliation and phone acceptance are not implemented here.
The original bytes must be retained and match their committed size/hash at first
finalization. A saved successful receipt is replayed without rescoring or rereading
the image; evidence recovery is handled by the upload workflow.

Production scan-backed finalization remains disabled:
`app.v3.mobile.finalization-enabled=false`. This guards all scan-linked/OMR-answer
results, including preexisting paper results without Mobile receipts. Other
existing V3 finalization behavior remains, with an added live account lock.
Only separately reviewed deployment of drafts V3_015 through V3_018 and release
validation can enable this bridge. Enabling it does not unlock the denied Mobile
upload/verification routes. Configured baseline remains V3_014.

See [the handoff](../../../V3_MOBILE_FINALIZATION_HANDOFF.md) for failure codes,
validation evidence and the remaining release work. Run `node validate.cjs` in
this directory to check the three illustrative fixtures and parent schema parity.
These checks do not constitute full OpenAPI toolchain certification.
