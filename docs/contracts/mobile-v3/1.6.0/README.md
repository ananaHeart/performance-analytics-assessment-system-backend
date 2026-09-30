# Mobile V3 1.6.0 — attachment evidence component

Prompt 11 implements `POST /api/v3/mobile/attachments` in isolated tests. Production
HTTP remains denied. The wire version stays `3.0`; request/ack schemas are unchanged
from 1.0.0. [OpenAPI](openapi.json) describes multipart `metadata` + `file` and errors.
Six fixtures are illustrative; the validation command additionally checks three
responses/requests produced by actual synthetic MariaDB service execution.

Run `node docs/contracts/mobile-v3/1.6.0/validate.cjs --runtime-samples` after the
opt-in persistence suite. Existing Ajv is reused; no package installation required.

Read the [backend handoff](../../../V3_MOBILE_ATTACHMENT_UPLOAD_HANDOFF.md) before
production wiring. In particular: fresh sync UUID per attachment operation, no
scores in evidence uploads, retained same-page lineage, no automatic crop accuracy
claim, and draft V3_019 deployment dependency for attachment sync readback.
