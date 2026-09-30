-- V3_009 DRAFT: Contract hardening for dynamic answer-sheet evidence and manifests.
--
-- Approved and applied on 2026-08-30 to the isolated local V3 database after
-- disposable validation. Do not apply to V2, TiDB, production, or Mobile SQLite.
--
-- Required order in a fresh disposable database:
--   canonical 60-table V3 schema -> V3_004 -> V3_005
--   -> V3_006 DRAFT -> V3_007 DRAFT -> V3_008 DRAFT
--   -> this file -> V3_009 hardening smoke test
--
-- This migration is additive. It does not rewrite the disposable-validated
-- V3_006 through V3_008 files or activate any dynamic paper template.

USE performance_assessment_v3_db;

-- Identification normally snapshots one expected response. Enumeration must
-- explicitly store its configured response count. Objective and essay questions
-- leave this null. Question-type-specific activation rules remain service-side
-- because a CHECK constraint cannot query question_types.
ALTER TABLE questions
    ADD COLUMN expected_response_count SMALLINT UNSIGNED NULL
        AFTER maximum_response_length,
    ADD CONSTRAINT chk_questions_expected_response_count CHECK (
        expected_response_count IS NULL OR expected_response_count >= 1
    );

-- Generated regions preserve the exact response count and ruled-line count used
-- by the immutable PDF/manifest even if the source question is later versioned.
ALTER TABLE answer_sheet_regions
    ADD COLUMN expected_response_count_snapshot SMALLINT UNSIGNED NULL
        AFTER response_region_size,
    ADD COLUMN response_line_count SMALLINT UNSIGNED NULL
        AFTER expected_response_count_snapshot,
    ADD CONSTRAINT chk_answer_sheet_regions_expected_count CHECK (
        expected_response_count_snapshot IS NULL
        OR expected_response_count_snapshot >= 1
    ),
    ADD CONSTRAINT chk_answer_sheet_regions_line_count CHECK (
        response_line_count IS NULL OR response_line_count >= 1
    ),
    ADD CONSTRAINT chk_answer_sheet_regions_response_shape CHECK (
        (
            region_type = 'objective_bubbles'
            AND response_region_size = 'none'
            AND expected_response_count_snapshot IS NULL
            AND response_line_count IS NULL
        )
        OR (
            region_type = 'written_response'
            AND response_region_size <> 'none'
            AND (
                expected_response_count_snapshot IS NULL
                OR response_line_count IS NULL
                OR response_line_count >= expected_response_count_snapshot
            )
        )
    );

-- Keep old attachment values readable while requiring new page captures to
-- distinguish the original camera evidence from normalized detector input.
-- Normalized pages retain a self-reference to their immutable original page.
ALTER TABLE answer_attachments
    MODIFY COLUMN attachment_type ENUM(
        'full_sheet',
        'page_image',
        'original_page',
        'normalized_page',
        'answer_crop',
        'teacher_evidence'
    ) NOT NULL,
    ADD COLUMN source_answer_attachment_id BIGINT UNSIGNED NULL
        AFTER answer_sheet_region_id,
    ADD COLUMN retention_policy_code VARCHAR(60) NOT NULL
        DEFAULT 'CENTRAL_PRIVATE_365D_V1' AFTER content_hash,
    ADD COLUMN retention_until TIMESTAMP NULL AFTER retention_policy_code,
    ADD COLUMN retention_hold BOOLEAN NOT NULL DEFAULT FALSE
        AFTER retention_until,
    ADD COLUMN retention_hold_reason VARCHAR(500) NULL
        AFTER retention_hold,
    ADD COLUMN retention_hold_set_by_user_id BIGINT UNSIGNED NULL
        AFTER retention_hold_reason,
    ADD COLUMN retention_hold_set_at TIMESTAMP NULL
        AFTER retention_hold_set_by_user_id,
    ADD COLUMN purge_status ENUM('retained', 'purged', 'purge_failed')
        NOT NULL DEFAULT 'retained' AFTER retention_hold_set_at,
    ADD COLUMN last_purge_attempt_at TIMESTAMP NULL AFTER purge_status,
    ADD COLUMN purged_at TIMESTAMP NULL AFTER last_purge_attempt_at,
    ADD COLUMN purged_by_user_id BIGINT UNSIGNED NULL AFTER purged_at,
    ADD COLUMN purge_reason VARCHAR(500) NULL AFTER purged_by_user_id,
    ADD COLUMN last_purge_error VARCHAR(1000) NULL AFTER purge_reason,
    ADD CONSTRAINT fk_answer_attachments_source
        FOREIGN KEY (source_answer_attachment_id)
        REFERENCES answer_attachments (answer_attachment_id),
    ADD CONSTRAINT fk_answer_attachments_hold_set_by_user
        FOREIGN KEY (retention_hold_set_by_user_id) REFERENCES users (user_id),
    ADD CONSTRAINT fk_answer_attachments_purged_by_user
        FOREIGN KEY (purged_by_user_id) REFERENCES users (user_id),
    ADD CONSTRAINT chk_answer_attachments_normalized_source CHECK (
        (
            attachment_type = 'normalized_page'
            AND source_answer_attachment_id IS NOT NULL
        )
        OR (
            attachment_type <> 'normalized_page'
            AND source_answer_attachment_id IS NULL
        )
    ),
    ADD CONSTRAINT chk_answer_attachments_hold CHECK (
        (
            retention_hold = FALSE
            AND retention_hold_reason IS NULL
            AND retention_hold_set_by_user_id IS NULL
            AND retention_hold_set_at IS NULL
        )
        OR (
            retention_hold = TRUE
            AND retention_hold_reason IS NOT NULL
            AND retention_hold_set_by_user_id IS NOT NULL
            AND retention_hold_set_at IS NOT NULL
        )
    ),
    ADD CONSTRAINT chk_answer_attachments_purge_state CHECK (
        (
            purge_status = 'retained'
            AND purged_at IS NULL
            AND purged_by_user_id IS NULL
            AND purge_reason IS NULL
            AND last_purge_error IS NULL
        )
        OR (
            purge_status = 'purged'
            AND last_purge_attempt_at IS NOT NULL
            AND purged_at IS NOT NULL
            AND purge_reason IS NOT NULL
            AND last_purge_error IS NULL
        )
        OR (
            purge_status = 'purge_failed'
            AND last_purge_attempt_at IS NOT NULL
            AND purged_at IS NULL
            AND purged_by_user_id IS NULL
            AND purge_reason IS NOT NULL
            AND last_purge_error IS NOT NULL
        )
    ),
    ADD CONSTRAINT chk_answer_attachments_held_not_purged CHECK (
        retention_hold = FALSE OR purge_status <> 'purged'
    ),
    ADD INDEX idx_answer_attachments_source (source_answer_attachment_id),
    ADD INDEX idx_answer_attachments_retention (
        purge_status,
        retention_hold,
        retention_until
    );

-- Keep VARCHAR capacity for backward-compatible reading, but reject generated or
-- captured QR payloads above the approved 256-byte UTF-8 contract. The stored
-- hash must be the lowercase SHA-256 digest of the exact payload bytes.
ALTER TABLE answer_sheet_pages
    ADD CONSTRAINT chk_answer_sheet_pages_qr_payload CHECK (
        OCTET_LENGTH(qr_payload) BETWEEN 2 AND 256
    ),
    ADD CONSTRAINT chk_answer_sheet_pages_qr_hash CHECK (
        BINARY qr_payload_hash REGEXP '^[0-9a-f]{64}$'
        AND qr_payload_hash = SHA2(qr_payload, 256)
    );

ALTER TABLE scan_pages
    ADD CONSTRAINT chk_scan_pages_qr_payload CHECK (
        OCTET_LENGTH(qr_payload) BETWEEN 2 AND 256
    ),
    ADD CONSTRAINT chk_scan_pages_qr_hash CHECK (
        BINARY qr_payload_hash REGEXP '^[0-9a-f]{64}$'
        AND qr_payload_hash = SHA2(qr_payload, 256)
    );

-- End of V3_009 DRAFT. Eligibility for automatic purge is derived from
-- retention_until, retention_hold, and purge_status; it is not stored as a
-- second status that can drift from the authoritative timestamps. MariaDB does
-- not permit an AUTO_INCREMENT column in a CHECK expression and a CHECK cannot
-- inspect the referenced row. Before save, the backend must require an
-- original_page source in the same scan page and ownership context and reject
-- direct or cyclic source-attachment references.
