-- V3_006 DRAFT: Dynamic, multi-paper, multi-page answer-sheet schema expansion.
--
-- Approved and applied on 2026-08-30 to the isolated local V3 database after a
-- verified backup. Do not apply to V2, TiDB, production, or Mobile SQLite.
-- This script is intentionally additive and keeps legacy one-page OMR columns
-- nullable/available until the data backfill and API contracts are approved.
-- Apply only after V3_005_smoke_test.sql passes and an explicit database backup
-- and rollback point have been recorded.

USE performance_assessment_v3_db;

-- Exact physical page dimensions used by PDF generation and mobile validation.
CREATE TABLE paper_sizes (
    paper_size_id SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    paper_size_code VARCHAR(20) NOT NULL,
    paper_size_name VARCHAR(60) NOT NULL,
    width_points DECIMAL(9,3) NOT NULL,
    height_points DECIMAL(9,3) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_paper_sizes PRIMARY KEY (paper_size_id),
    CONSTRAINT uk_paper_sizes_code UNIQUE (paper_size_code),
    CONSTRAINT chk_paper_sizes_dimensions CHECK (
        width_points > 0 AND height_points > 0
    )
) COMMENT='Exact immutable physical dimensions available to answer-sheet templates.';

-- Structural seed required before omr_templates can reference paper_size_id.
INSERT INTO paper_sizes (
    paper_size_id,
    paper_size_code,
    paper_size_name,
    width_points,
    height_points,
    is_active
) VALUES
    (1, 'A4', 'A4', 595.276, 841.890, TRUE),
    (2, 'US_LETTER', 'US Letter', 612.000, 792.000, TRUE),
    (3, 'US_LEGAL', 'US Legal', 612.000, 1008.000, TRUE);

-- Preserve the old page_size/question capability fields for legacy readers.
-- New mixed page templates use paper_size_id and omr_template_regions.
ALTER TABLE omr_templates
    DROP CONSTRAINT chk_omr_templates_item_count,
    DROP CONSTRAINT chk_omr_templates_option_count,
    ADD COLUMN paper_size_id SMALLINT UNSIGNED NULL AFTER question_type_id,
    ADD COLUMN template_version VARCHAR(50) NULL AFTER template_name,
    ADD COLUMN coordinate_origin ENUM('pdf_bottom_left')
        NOT NULL DEFAULT 'pdf_bottom_left' AFTER page_orientation,
    ADD COLUMN required_print_scale_percent DECIMAL(5,2)
        NOT NULL DEFAULT 100.00 AFTER coordinate_origin,
    ADD COLUMN geometry_hash CHAR(64) NULL AFTER geometry_definition,
    ADD COLUMN retired_at TIMESTAMP NULL AFTER activated_at,
    MODIFY COLUMN question_type_id SMALLINT UNSIGNED NULL,
    MODIFY COLUMN minimum_item_count SMALLINT UNSIGNED NULL,
    MODIFY COLUMN maximum_item_count SMALLINT UNSIGNED NULL,
    MODIFY COLUMN option_count TINYINT UNSIGNED NULL,
    ADD CONSTRAINT chk_omr_templates_item_count CHECK (
        (minimum_item_count IS NULL AND maximum_item_count IS NULL)
        OR (
            minimum_item_count >= 1
            AND maximum_item_count >= minimum_item_count
        )
    ),
    ADD CONSTRAINT chk_omr_templates_option_count CHECK (
        option_count IS NULL OR option_count BETWEEN 2 AND 4
    ),
    ADD CONSTRAINT chk_omr_templates_print_scale CHECK (
        required_print_scale_percent = 100.00
    );

UPDATE omr_templates ot
JOIN paper_sizes ps
  ON ps.paper_size_code = CASE
      WHEN UPPER(REPLACE(ot.page_size, ' ', '_')) = 'LETTER' THEN 'US_LETTER'
      WHEN UPPER(REPLACE(ot.page_size, ' ', '_')) = 'LEGAL' THEN 'US_LEGAL'
      ELSE UPPER(REPLACE(ot.page_size, ' ', '_'))
  END
SET ot.paper_size_id = ps.paper_size_id,
    ot.template_version = CASE
        WHEN ot.template_code = 'OMR-A4-10-MC-CTX-V2' THEN '2'
        ELSE COALESCE(ot.template_version, 'legacy-1')
    END,
    ot.geometry_hash = SHA2(CAST(ot.geometry_definition AS CHAR), 256);

ALTER TABLE omr_templates
    MODIFY COLUMN paper_size_id SMALLINT UNSIGNED NOT NULL,
    MODIFY COLUMN template_version VARCHAR(50) NOT NULL,
    MODIFY COLUMN geometry_hash CHAR(64) NOT NULL,
    ADD CONSTRAINT fk_omr_templates_paper_size
        FOREIGN KEY (paper_size_id) REFERENCES paper_sizes (paper_size_id),
    ADD INDEX idx_omr_templates_paper_status
        (paper_size_id, page_orientation, template_status);

-- Immutable landmarks and assignable response slots within one page template.
CREATE TABLE omr_template_regions (
    omr_template_region_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    region_uuid CHAR(36) NOT NULL,
    omr_template_id BIGINT UNSIGNED NOT NULL,
    region_code VARCHAR(80) NOT NULL,
    region_order SMALLINT UNSIGNED NOT NULL,
    region_type ENUM(
        'objective_bubbles',
        'written_response',
        'page_identity',
        'registration_marker'
    ) NOT NULL,
    question_type_id SMALLINT UNSIGNED NULL,
    layout_variant VARCHAR(40) NULL,
    response_region_size ENUM('none', 'short', 'medium', 'long', 'full_page') NULL,
    x_points DECIMAL(9,3) NOT NULL,
    y_points DECIMAL(9,3) NOT NULL,
    width_points DECIMAL(9,3) NOT NULL,
    height_points DECIMAL(9,3) NOT NULL,
    geometry_definition JSON NOT NULL,
    geometry_hash CHAR(64) NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_omr_template_regions PRIMARY KEY (omr_template_region_id),
    CONSTRAINT uk_omr_template_regions_uuid UNIQUE (region_uuid),
    CONSTRAINT uk_omr_template_regions_code UNIQUE (omr_template_id, region_code),
    CONSTRAINT uk_omr_template_regions_order UNIQUE (omr_template_id, region_order),
    CONSTRAINT fk_omr_template_regions_template
        FOREIGN KEY (omr_template_id) REFERENCES omr_templates (omr_template_id),
    CONSTRAINT fk_omr_template_regions_question_type
        FOREIGN KEY (question_type_id) REFERENCES question_types (question_type_id),
    CONSTRAINT chk_omr_template_regions_bounds CHECK (
        x_points >= 0
        AND y_points >= 0
        AND width_points > 0
        AND height_points > 0
    ),
    CONSTRAINT chk_omr_template_regions_question_type CHECK (
        (region_type IN ('objective_bubbles', 'written_response')
            AND question_type_id IS NOT NULL)
        OR (region_type IN ('page_identity', 'registration_marker')
            AND question_type_id IS NULL)
    ),
    INDEX idx_omr_template_regions_capability
        (omr_template_id, region_type, question_type_id, response_region_size)
) COMMENT='Immutable marker, QR, objective, and written-response regions in one physical page template.';

-- One immutable generated package for an exact assessment delivery and paper size.
CREATE TABLE answer_sheet_versions (
    answer_sheet_version_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    answer_sheet_uuid CHAR(36) NOT NULL,
    test_assignment_id BIGINT UNSIGNED NOT NULL,
    paper_size_id SMALLINT UNSIGNED NOT NULL,
    generation_number INT UNSIGNED NOT NULL DEFAULT 1,
    test_version_number INT UNSIGNED NOT NULL,
    total_questions INT UNSIGNED NOT NULL,
    total_pages SMALLINT UNSIGNED NOT NULL,
    manifest_version SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    manifest_hash CHAR(64) NOT NULL,
    pdf_storage_key VARCHAR(500) NULL,
    pdf_content_hash CHAR(64) NULL,
    pdf_file_size_bytes BIGINT UNSIGNED NULL,
    generation_status ENUM('generating', 'ready', 'retired', 'failed')
        NOT NULL DEFAULT 'generating',
    source_answer_sheet_version_id BIGINT UNSIGNED NULL,
    generated_by_user_id BIGINT UNSIGNED NOT NULL,
    generated_at TIMESTAMP NULL,
    retired_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_answer_sheet_versions PRIMARY KEY (answer_sheet_version_id),
    CONSTRAINT uk_answer_sheet_versions_uuid UNIQUE (answer_sheet_uuid),
    CONSTRAINT uk_answer_sheet_versions_generation UNIQUE (
        test_assignment_id,
        paper_size_id,
        generation_number
    ),
    CONSTRAINT uk_answer_sheet_versions_manifest UNIQUE (
        test_assignment_id,
        paper_size_id,
        manifest_hash
    ),
    CONSTRAINT fk_answer_sheet_versions_test_assignment
        FOREIGN KEY (test_assignment_id) REFERENCES test_assignments (test_assignment_id),
    CONSTRAINT fk_answer_sheet_versions_paper_size
        FOREIGN KEY (paper_size_id) REFERENCES paper_sizes (paper_size_id),
    CONSTRAINT fk_answer_sheet_versions_source
        FOREIGN KEY (source_answer_sheet_version_id)
        REFERENCES answer_sheet_versions (answer_sheet_version_id),
    CONSTRAINT fk_answer_sheet_versions_generated_by_user
        FOREIGN KEY (generated_by_user_id) REFERENCES users (user_id),
    CONSTRAINT chk_answer_sheet_versions_minimum_questions CHECK (
        total_questions >= 5
    ),
    CONSTRAINT chk_answer_sheet_versions_pages CHECK (total_pages >= 1),
    CONSTRAINT chk_answer_sheet_versions_generation CHECK (generation_number >= 1),
    CONSTRAINT chk_answer_sheet_versions_content_version CHECK (
        test_version_number >= 1 AND manifest_version >= 1
    ),
    CONSTRAINT chk_answer_sheet_versions_pdf_size CHECK (
        pdf_file_size_bytes IS NULL OR pdf_file_size_bytes > 0
    ),
    INDEX idx_answer_sheet_versions_assignment_status
        (test_assignment_id, generation_status, generated_at)
) COMMENT='Immutable generated answer-sheet package for an exact assessment delivery.';

-- Every physical page has its own template and QR/page identity.
CREATE TABLE answer_sheet_pages (
    answer_sheet_page_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    page_uuid CHAR(36) NOT NULL,
    answer_sheet_version_id BIGINT UNSIGNED NOT NULL,
    omr_template_id BIGINT UNSIGNED NOT NULL,
    page_number SMALLINT UNSIGNED NOT NULL,
    total_pages SMALLINT UNSIGNED NOT NULL,
    qr_payload VARCHAR(1000) NOT NULL,
    qr_payload_hash CHAR(64) NOT NULL,
    page_geometry_hash CHAR(64) NOT NULL,
    page_status ENUM('ready', 'retired') NOT NULL DEFAULT 'ready',
    retired_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_answer_sheet_pages PRIMARY KEY (answer_sheet_page_id),
    CONSTRAINT uk_answer_sheet_pages_uuid UNIQUE (page_uuid),
    CONSTRAINT uk_answer_sheet_pages_number UNIQUE (
        answer_sheet_version_id,
        page_number
    ),
    CONSTRAINT uk_answer_sheet_pages_qr_hash UNIQUE (qr_payload_hash),
    CONSTRAINT fk_answer_sheet_pages_version
        FOREIGN KEY (answer_sheet_version_id)
        REFERENCES answer_sheet_versions (answer_sheet_version_id),
    CONSTRAINT fk_answer_sheet_pages_template
        FOREIGN KEY (omr_template_id) REFERENCES omr_templates (omr_template_id),
    CONSTRAINT chk_answer_sheet_pages_number CHECK (
        page_number >= 1 AND total_pages >= page_number
    ),
    INDEX idx_answer_sheet_pages_template (omr_template_id, page_status)
) COMMENT='Immutable physical pages and page-specific QR identity in one generated answer-sheet version.';

-- Concrete question-to-page/coordinate manifest downloaded by Mobile.
CREATE TABLE answer_sheet_regions (
    answer_sheet_region_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    region_uuid CHAR(36) NOT NULL,
    answer_sheet_version_id BIGINT UNSIGNED NOT NULL,
    answer_sheet_page_id BIGINT UNSIGNED NOT NULL,
    omr_template_region_id BIGINT UNSIGNED NOT NULL,
    question_id BIGINT UNSIGNED NOT NULL,
    test_part_id BIGINT UNSIGNED NOT NULL,
    question_type_id SMALLINT UNSIGNED NOT NULL,
    global_item_number INT UNSIGNED NOT NULL,
    part_item_number INT UNSIGNED NOT NULL,
    region_sequence SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    region_type ENUM('objective_bubbles', 'written_response') NOT NULL,
    response_region_size ENUM('none', 'short', 'medium', 'long', 'full_page')
        NOT NULL DEFAULT 'none',
    geometry_snapshot JSON NOT NULL,
    geometry_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_answer_sheet_regions PRIMARY KEY (answer_sheet_region_id),
    CONSTRAINT uk_answer_sheet_regions_uuid UNIQUE (region_uuid),
    CONSTRAINT uk_answer_sheet_regions_question_sequence UNIQUE (
        answer_sheet_version_id,
        question_id,
        region_sequence
    ),
    CONSTRAINT uk_answer_sheet_regions_page_slot UNIQUE (
        answer_sheet_page_id,
        omr_template_region_id
    ),
    CONSTRAINT fk_answer_sheet_regions_version
        FOREIGN KEY (answer_sheet_version_id)
        REFERENCES answer_sheet_versions (answer_sheet_version_id),
    CONSTRAINT fk_answer_sheet_regions_page
        FOREIGN KEY (answer_sheet_page_id) REFERENCES answer_sheet_pages (answer_sheet_page_id),
    CONSTRAINT fk_answer_sheet_regions_template_region
        FOREIGN KEY (omr_template_region_id)
        REFERENCES omr_template_regions (omr_template_region_id),
    CONSTRAINT fk_answer_sheet_regions_question
        FOREIGN KEY (question_id) REFERENCES questions (question_id),
    CONSTRAINT fk_answer_sheet_regions_test_part
        FOREIGN KEY (test_part_id) REFERENCES test_parts (test_part_id),
    CONSTRAINT fk_answer_sheet_regions_question_type
        FOREIGN KEY (question_type_id) REFERENCES question_types (question_type_id),
    CONSTRAINT chk_answer_sheet_regions_item_numbers CHECK (
        global_item_number >= 1
        AND part_item_number >= 1
        AND region_sequence >= 1
    ),
    INDEX idx_answer_sheet_regions_page_order
        (answer_sheet_page_id, global_item_number, region_sequence)
) COMMENT='Authoritative mapping from assessment questions to immutable generated page regions.';

-- The session becomes the learner/test aggregate; legacy one-page columns remain.
ALTER TABLE scan_sessions
    ADD COLUMN answer_sheet_version_id BIGINT UNSIGNED NULL AFTER scan_uuid,
    ADD COLUMN expected_page_count SMALLINT UNSIGNED NOT NULL DEFAULT 1
        AFTER class_list_id,
    ADD COLUMN captured_page_count SMALLINT UNSIGNED NOT NULL DEFAULT 0
        AFTER expected_page_count,
    MODIFY COLUMN omr_template_id BIGINT UNSIGNED NULL,
    MODIFY COLUMN template_version VARCHAR(50) NULL,
    ADD CONSTRAINT fk_scan_sessions_answer_sheet_version
        FOREIGN KEY (answer_sheet_version_id)
        REFERENCES answer_sheet_versions (answer_sheet_version_id),
    ADD CONSTRAINT chk_scan_sessions_capture_model CHECK (
        answer_sheet_version_id IS NOT NULL OR omr_template_id IS NOT NULL
    ),
    ADD CONSTRAINT chk_scan_sessions_page_counts CHECK (
        expected_page_count >= 1
        AND captured_page_count <= expected_page_count
    ),
    ADD INDEX idx_scan_sessions_sheet_version
        (answer_sheet_version_id, class_list_id, scan_status);

UPDATE scan_sessions
SET captured_page_count = 1
WHERE omr_template_id IS NOT NULL
  AND captured_page_count = 0;

-- One immutable captured page image. Rescans create another row and supersede it.
CREATE TABLE scan_pages (
    scan_page_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    scan_page_uuid CHAR(36) NOT NULL,
    scan_session_id BIGINT UNSIGNED NOT NULL,
    answer_sheet_page_id BIGINT UNSIGNED NOT NULL,
    omr_template_id BIGINT UNSIGNED NOT NULL,
    page_number SMALLINT UNSIGNED NOT NULL,
    capture_number SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    scanner_version VARCHAR(50) NOT NULL,
    qr_payload VARCHAR(1000) NOT NULL,
    qr_payload_hash CHAR(64) NOT NULL,
    image_hash CHAR(64) NOT NULL,
    captured_rotation_degrees SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    page_status ENUM(
        'captured',
        'processing',
        'needs_verification',
        'accepted',
        'rescan_requested',
        'rejected',
        'superseded',
        'failed'
    ) NOT NULL DEFAULT 'captured',
    failure_code VARCHAR(50) NULL,
    failure_detail VARCHAR(500) NULL,
    supersedes_scan_page_id BIGINT UNSIGNED NULL,
    captured_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    current_page_number SMALLINT UNSIGNED GENERATED ALWAYS AS (
        CASE
            WHEN page_status NOT IN ('rejected', 'superseded', 'failed')
                THEN page_number
            ELSE NULL
        END
    ) VIRTUAL,
    CONSTRAINT pk_scan_pages PRIMARY KEY (scan_page_id),
    CONSTRAINT uk_scan_pages_uuid UNIQUE (scan_page_uuid),
    CONSTRAINT uk_scan_pages_capture UNIQUE (
        scan_session_id,
        page_number,
        capture_number
    ),
    CONSTRAINT uk_scan_pages_current UNIQUE (scan_session_id, current_page_number),
    CONSTRAINT uk_scan_pages_supersedes UNIQUE (supersedes_scan_page_id),
    CONSTRAINT fk_scan_pages_session
        FOREIGN KEY (scan_session_id) REFERENCES scan_sessions (scan_session_id),
    CONSTRAINT fk_scan_pages_answer_sheet_page
        FOREIGN KEY (answer_sheet_page_id) REFERENCES answer_sheet_pages (answer_sheet_page_id),
    CONSTRAINT fk_scan_pages_template
        FOREIGN KEY (omr_template_id) REFERENCES omr_templates (omr_template_id),
    CONSTRAINT fk_scan_pages_supersedes
        FOREIGN KEY (supersedes_scan_page_id) REFERENCES scan_pages (scan_page_id),
    CONSTRAINT chk_scan_pages_numbers CHECK (
        page_number >= 1 AND capture_number >= 1
    ),
    CONSTRAINT chk_scan_pages_rotation CHECK (
        captured_rotation_degrees IN (0, 90, 180, 270)
    ),
    INDEX idx_scan_pages_session_status
        (scan_session_id, page_status, page_number),
    INDEX idx_scan_pages_rescan_lineage (supersedes_scan_page_id)
) COMMENT='Immutable page-level capture evidence and rescan lineage inside one scan session.';

-- New detections identify both the captured page and generated question region.
-- Existing legacy detections retain null page/region links until backfilled.
ALTER TABLE omr_detections
    DROP INDEX uk_omr_detections_scan_question,
    ADD COLUMN scan_page_id BIGINT UNSIGNED NULL AFTER scan_session_id,
    ADD COLUMN answer_sheet_region_id BIGINT UNSIGNED NULL AFTER scan_page_id,
    ADD COLUMN legacy_scan_session_id BIGINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN scan_page_id IS NULL THEN scan_session_id ELSE NULL END
    ) VIRTUAL AFTER answer_sheet_region_id,
    ADD COLUMN legacy_question_id BIGINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN scan_page_id IS NULL THEN question_id ELSE NULL END
    ) VIRTUAL AFTER legacy_scan_session_id,
    ADD CONSTRAINT fk_omr_detections_scan_page
        FOREIGN KEY (scan_page_id) REFERENCES scan_pages (scan_page_id),
    ADD CONSTRAINT fk_omr_detections_answer_sheet_region
        FOREIGN KEY (answer_sheet_region_id)
        REFERENCES answer_sheet_regions (answer_sheet_region_id),
    ADD CONSTRAINT chk_omr_detections_page_region CHECK (
        (scan_page_id IS NULL AND answer_sheet_region_id IS NULL)
        OR (scan_page_id IS NOT NULL AND answer_sheet_region_id IS NOT NULL)
    ),
    ADD CONSTRAINT uk_omr_detections_page_region UNIQUE (
        scan_page_id,
        answer_sheet_region_id
    ),
    ADD CONSTRAINT uk_omr_detections_legacy_scan_question UNIQUE (
        legacy_scan_session_id,
        legacy_question_id
    ),
    ADD INDEX idx_omr_detections_session_question
        (scan_session_id, question_id);

-- Page images and written-response crops retain exact source-page/region lineage.
ALTER TABLE answer_attachments
    DROP CONSTRAINT chk_answer_attachments_owner,
    ADD COLUMN scan_page_id BIGINT UNSIGNED NULL AFTER scan_session_id,
    ADD COLUMN answer_sheet_region_id BIGINT UNSIGNED NULL AFTER scan_page_id,
    MODIFY COLUMN attachment_type ENUM(
        'full_sheet',
        'page_image',
        'answer_crop',
        'teacher_evidence'
    ) NOT NULL,
    ADD CONSTRAINT fk_answer_attachments_scan_page
        FOREIGN KEY (scan_page_id) REFERENCES scan_pages (scan_page_id),
    ADD CONSTRAINT fk_answer_attachments_answer_sheet_region
        FOREIGN KEY (answer_sheet_region_id)
        REFERENCES answer_sheet_regions (answer_sheet_region_id),
    ADD CONSTRAINT chk_answer_attachments_owner CHECK (
        student_answer_id IS NOT NULL
        OR scan_session_id IS NOT NULL
        OR scan_page_id IS NOT NULL
    ),
    ADD CONSTRAINT chk_answer_attachments_region_page CHECK (
        answer_sheet_region_id IS NULL OR scan_page_id IS NOT NULL
    ),
    ADD INDEX idx_answer_attachments_page_type
        (scan_page_id, attachment_type),
    ADD INDEX idx_answer_attachments_region_type
        (answer_sheet_region_id, attachment_type);

-- A verification may apply to the complete session or one captured page.
ALTER TABLE scan_verifications
    ADD COLUMN scan_page_id BIGINT UNSIGNED NULL AFTER scan_session_id,
    ADD CONSTRAINT fk_scan_verifications_scan_page
        FOREIGN KEY (scan_page_id) REFERENCES scan_pages (scan_page_id),
    ADD INDEX idx_scan_verifications_page_time (scan_page_id, decided_at);

-- Written-region size is a fixed template capability, not arbitrary PDF scaling.
ALTER TABLE questions
    ADD COLUMN response_region_size ENUM(
        'none',
        'short',
        'medium',
        'long',
        'full_page'
    ) NOT NULL DEFAULT 'none' AFTER maximum_response_length,
    ADD COLUMN force_page_break_before BOOLEAN NOT NULL DEFAULT FALSE
        AFTER response_region_size;

-- Store the criterion maximum used by each score version for auditable bounds.
-- It is nullable only during compatibility/backfill; new APIs must always supply it.
ALTER TABLE answer_rubric_scores
    ADD COLUMN maximum_points_snapshot DECIMAL(8,2) NULL AFTER points_awarded,
    ADD CONSTRAINT chk_answer_rubric_scores_maximum CHECK (
        maximum_points_snapshot IS NULL
        OR (
            maximum_points_snapshot > 0
            AND points_awarded <= maximum_points_snapshot
        )
    );

-- End of DRAFT schema expansion. No new Letter/Legal or dynamic response
-- geometry is activated by this file.
