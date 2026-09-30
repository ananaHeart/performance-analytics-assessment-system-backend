-- V3_002: Versioned OMR templates, immutable scan evidence, mixed responses, and rubric scoring.
-- Apply after V3_001_core_domain.sql.

USE performance_assessment_v3_db;

CREATE TABLE omr_templates (
    omr_template_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    template_code VARCHAR(80) NOT NULL,
    template_name VARCHAR(120) NOT NULL,
    question_type_id SMALLINT UNSIGNED NOT NULL,
    page_size VARCHAR(20) NOT NULL DEFAULT 'A4',
    page_orientation ENUM('portrait', 'landscape') NOT NULL DEFAULT 'portrait',
    minimum_item_count SMALLINT UNSIGNED NOT NULL,
    maximum_item_count SMALLINT UNSIGNED NOT NULL,
    option_count TINYINT UNSIGNED NOT NULL,
    geometry_definition JSON NOT NULL,
    qr_payload_version SMALLINT UNSIGNED NOT NULL,
    minimum_scanner_version VARCHAR(50) NOT NULL,
    template_status ENUM('draft', 'active', 'retired') NOT NULL DEFAULT 'draft',
    created_by_user_id BIGINT UNSIGNED NULL,
    activated_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_omr_templates PRIMARY KEY (omr_template_id),
    CONSTRAINT uk_omr_templates_code UNIQUE (template_code),
    CONSTRAINT fk_omr_templates_question_type
        FOREIGN KEY (question_type_id) REFERENCES question_types (question_type_id),
    CONSTRAINT fk_omr_templates_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES users (user_id),
    CONSTRAINT chk_omr_templates_item_count CHECK (
        minimum_item_count >= 1 AND maximum_item_count >= minimum_item_count
    ),
    CONSTRAINT chk_omr_templates_option_count CHECK (option_count BETWEEN 2 AND 4),
    INDEX idx_omr_templates_capability
        (question_type_id, template_status, minimum_item_count, maximum_item_count)
) COMMENT='Immutable, versioned paper-template geometry shared by backend printing and mobile scanning.';

ALTER TABLE scan_sessions
    MODIFY COLUMN scanned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT 'Image-capture timestamp.',
    ADD COLUMN omr_template_id BIGINT UNSIGNED NOT NULL AFTER scan_uuid,
    ADD COLUMN test_assignment_id BIGINT UNSIGNED NOT NULL AFTER omr_template_id,
    ADD COLUMN class_list_id BIGINT UNSIGNED NOT NULL AFTER test_assignment_id,
    ADD COLUMN supersedes_scan_session_id BIGINT UNSIGNED NULL AFTER image_hash,
    ADD COLUMN failure_code VARCHAR(50) NULL AFTER scan_status,
    ADD COLUMN failure_detail VARCHAR(500) NULL AFTER failure_code,
    MODIFY COLUMN scan_status ENUM(
        'captured',
        'processing',
        'needs_verification',
        'accepted',
        'rescan_requested',
        'rejected',
        'superseded',
        'failed'
    ) NOT NULL DEFAULT 'captured',
    ADD CONSTRAINT fk_scan_sessions_omr_template
        FOREIGN KEY (omr_template_id) REFERENCES omr_templates (omr_template_id),
    ADD CONSTRAINT fk_scan_sessions_test_assignment
        FOREIGN KEY (test_assignment_id) REFERENCES test_assignments (test_assignment_id),
    ADD CONSTRAINT fk_scan_sessions_class_list
        FOREIGN KEY (class_list_id) REFERENCES class_lists (class_list_id),
    ADD CONSTRAINT fk_scan_sessions_supersedes_scan_session
        FOREIGN KEY (supersedes_scan_session_id) REFERENCES scan_sessions (scan_session_id),
    ADD INDEX idx_scan_sessions_assignment_student
        (test_assignment_id, class_list_id, scan_status, scanned_at),
    ADD INDEX idx_scan_sessions_rescan_lineage (supersedes_scan_session_id);

ALTER TABLE omr_detections DROP CONSTRAINT chk_omr_detections_option;

ALTER TABLE omr_detections
    MODIFY COLUMN detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT 'Detection timestamp.',
    ADD COLUMN detection_uuid CHAR(36) NOT NULL AFTER omr_detection_id,
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER detected_at,
    DROP COLUMN verification_status,
    ADD CONSTRAINT uk_omr_detections_uuid UNIQUE (detection_uuid),
    ADD CONSTRAINT chk_omr_detections_option
        CHECK (detected_option IS NULL OR detected_option IN ('A', 'B', 'C', 'D'));

ALTER TABLE test_results
    DROP FOREIGN KEY fk_test_results_test,
    DROP INDEX uk_test_results_attempt,
    MODIFY COLUMN checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT 'Teacher verification or checking completion timestamp.',
    ADD COLUMN test_assignment_id BIGINT UNSIGNED NOT NULL AFTER result_uuid,
    ADD COLUMN result_status ENUM('draft', 'pending_verification', 'finalized', 'superseded')
        NOT NULL DEFAULT 'draft' AFTER items_evaluated,
    ADD COLUMN submitted_at TIMESTAMP NULL AFTER result_status,
    ADD COLUMN verification_completed_at TIMESTAMP NULL AFTER submitted_at,
    ADD COLUMN scored_at TIMESTAMP NULL AFTER verification_completed_at,
    ADD COLUMN finalized_at TIMESTAMP NULL AFTER scored_at,
    ADD COLUMN percentage_snapshot DECIMAL(7,4) NULL AFTER finalized_at,
    ADD COLUMN performance_status VARCHAR(50) NULL AFTER percentage_snapshot,
    ADD COLUMN performance_rule_set_id BIGINT UNSIGNED NULL AFTER performance_status,
    ADD COLUMN score_version INT UNSIGNED NOT NULL DEFAULT 1 AFTER performance_rule_set_id,
    DROP COLUMN test_id,
    ADD CONSTRAINT uk_test_results_attempt
        UNIQUE (test_assignment_id, class_list_id, attempt_number),
    ADD CONSTRAINT fk_test_results_test_assignment
        FOREIGN KEY (test_assignment_id) REFERENCES test_assignments (test_assignment_id),
    ADD CONSTRAINT fk_test_results_performance_rule_set
        FOREIGN KEY (performance_rule_set_id)
        REFERENCES performance_rule_sets (performance_rule_set_id),
    ADD CONSTRAINT chk_test_results_percentage
        CHECK (percentage_snapshot IS NULL OR percentage_snapshot BETWEEN 0 AND 100),
    ADD INDEX idx_test_results_assignment_status
        (test_assignment_id, result_status, finalized_at);

ALTER TABLE student_answers DROP CONSTRAINT chk_student_answers_option;

ALTER TABLE student_answers
    ADD COLUMN selected_question_option_id BIGINT UNSIGNED NULL AFTER answer_uuid,
    ADD COLUMN response_text TEXT NULL AFTER selected_question_option_id,
    ADD COLUMN evaluation_status ENUM(
        'pending_verification',
        'needs_manual_scoring',
        'scored',
        'finalized'
    ) NOT NULL DEFAULT 'pending_verification' AFTER answer_status,
    ADD COLUMN teacher_feedback TEXT NULL AFTER points_earned,
    ADD COLUMN finalized_at TIMESTAMP NULL AFTER teacher_feedback,
    ADD COLUMN reopened_at TIMESTAMP NULL AFTER finalized_at,
    ADD COLUMN score_version INT UNSIGNED NOT NULL DEFAULT 1 AFTER reopened_at,
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER score_version,
    MODIFY COLUMN verified_by_user_id BIGINT UNSIGNED NULL,
    MODIFY COLUMN capture_source ENUM('omr', 'ocr', 'manual') NOT NULL,
    MODIFY COLUMN verified_at TIMESTAMP NULL,
    MODIFY COLUMN answer_status ENUM(
        'answered',
        'blank',
        'multiple',
        'uncertain',
        'invalid',
        'pending_manual'
    ) NOT NULL,
    MODIFY COLUMN is_correct BOOLEAN NULL,
    MODIFY COLUMN points_earned DECIMAL(8,2) NOT NULL DEFAULT 0,
    DROP COLUMN selected_option,
    DROP COLUMN correction_reason,
    ADD CONSTRAINT fk_student_answers_selected_question_option
        FOREIGN KEY (selected_question_option_id)
        REFERENCES question_options (question_option_id),
    ADD CONSTRAINT chk_student_answers_response CHECK (
        selected_question_option_id IS NOT NULL
        OR response_text IS NOT NULL
        OR answer_status IN ('blank', 'multiple', 'uncertain', 'invalid', 'pending_manual')
    );

CREATE TABLE answer_attachments (
    answer_attachment_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    attachment_uuid CHAR(36) NOT NULL,
    student_answer_id BIGINT UNSIGNED NULL,
    scan_session_id BIGINT UNSIGNED NULL,
    attachment_type ENUM('full_sheet', 'answer_crop', 'teacher_evidence') NOT NULL,
    storage_provider VARCHAR(40) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT UNSIGNED NOT NULL,
    content_hash CHAR(64) NOT NULL,
    crop_coordinates JSON NULL,
    captured_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_answer_attachments PRIMARY KEY (answer_attachment_id),
    CONSTRAINT uk_answer_attachments_uuid UNIQUE (attachment_uuid),
    CONSTRAINT fk_answer_attachments_student_answer
        FOREIGN KEY (student_answer_id) REFERENCES student_answers (student_answer_id),
    CONSTRAINT fk_answer_attachments_scan_session
        FOREIGN KEY (scan_session_id) REFERENCES scan_sessions (scan_session_id),
    CONSTRAINT chk_answer_attachments_owner
        CHECK (student_answer_id IS NOT NULL OR scan_session_id IS NOT NULL),
    CONSTRAINT chk_answer_attachments_file_size CHECK (file_size_bytes > 0),
    INDEX idx_answer_attachments_scan_type (scan_session_id, attachment_type),
    INDEX idx_answer_attachments_answer_type (student_answer_id, attachment_type)
) COMMENT='Private full-sheet and item-crop evidence for scan and non-objective response review.';

CREATE TABLE scan_verifications (
    scan_verification_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    verification_uuid CHAR(36) NOT NULL,
    scan_session_id BIGINT UNSIGNED NOT NULL,
    verified_by_user_id BIGINT UNSIGNED NOT NULL,
    verification_action ENUM('accepted', 'rescan_requested', 'rejected', 'superseded') NOT NULL,
    reason_code VARCHAR(50) NULL,
    reason_detail VARCHAR(500) NULL,
    decided_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_scan_verifications PRIMARY KEY (scan_verification_id),
    CONSTRAINT uk_scan_verifications_uuid UNIQUE (verification_uuid),
    CONSTRAINT fk_scan_verifications_scan_session
        FOREIGN KEY (scan_session_id) REFERENCES scan_sessions (scan_session_id),
    CONSTRAINT fk_scan_verifications_verified_by_user
        FOREIGN KEY (verified_by_user_id) REFERENCES users (user_id),
    CONSTRAINT chk_scan_verifications_reason CHECK (
        verification_action = 'accepted'
        OR reason_code IS NOT NULL
    ),
    INDEX idx_scan_verifications_session_time (scan_session_id, decided_at)
) COMMENT='Append-only whole-scan decisions; never stores or replaces an objective learner answer.';

CREATE TABLE answer_verifications (
    answer_verification_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    verification_uuid CHAR(36) NOT NULL,
    student_answer_id BIGINT UNSIGNED NOT NULL,
    verified_by_user_id BIGINT UNSIGNED NOT NULL,
    verification_action ENUM(
        'transcribed',
        'ocr_confirmed',
        'ocr_corrected',
        'manual_scored',
        'reopened',
        'finalized'
    ) NOT NULL,
    previous_answer_status VARCHAR(30) NULL,
    previous_answer_value TEXT NULL,
    new_answer_status VARCHAR(30) NOT NULL,
    new_answer_value TEXT NULL,
    previous_points DECIMAL(8,2) NULL,
    new_points DECIMAL(8,2) NOT NULL,
    reason_code VARCHAR(50) NULL,
    reason_detail VARCHAR(500) NULL,
    evidence_attachment_id BIGINT UNSIGNED NULL,
    verified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_answer_verifications PRIMARY KEY (answer_verification_id),
    CONSTRAINT uk_answer_verifications_uuid UNIQUE (verification_uuid),
    CONSTRAINT fk_answer_verifications_student_answer
        FOREIGN KEY (student_answer_id) REFERENCES student_answers (student_answer_id),
    CONSTRAINT fk_answer_verifications_verified_by_user
        FOREIGN KEY (verified_by_user_id) REFERENCES users (user_id),
    CONSTRAINT fk_answer_verifications_evidence_attachment
        FOREIGN KEY (evidence_attachment_id) REFERENCES answer_attachments (answer_attachment_id),
    CONSTRAINT chk_answer_verifications_points CHECK (new_points >= 0),
    CONSTRAINT chk_answer_verifications_reason CHECK (
        verification_action NOT IN ('ocr_corrected', 'reopened') OR reason_code IS NOT NULL
    ),
    INDEX idx_answer_verifications_answer_time (student_answer_id, verified_at)
) COMMENT='Append-only OCR, transcription, manual-score, and correction history for non-objective answers.';

CREATE TABLE answer_rubric_scores (
    answer_rubric_score_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    student_answer_id BIGINT UNSIGNED NOT NULL,
    rubric_criterion_id BIGINT UNSIGNED NOT NULL,
    answer_verification_id BIGINT UNSIGNED NULL,
    scored_by_user_id BIGINT UNSIGNED NOT NULL,
    score_version INT UNSIGNED NOT NULL DEFAULT 1,
    points_awarded DECIMAL(8,2) NOT NULL,
    criterion_feedback TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_answer_rubric_scores PRIMARY KEY (answer_rubric_score_id),
    CONSTRAINT uk_answer_rubric_scores_version
        UNIQUE (student_answer_id, rubric_criterion_id, score_version),
    CONSTRAINT fk_answer_rubric_scores_student_answer
        FOREIGN KEY (student_answer_id) REFERENCES student_answers (student_answer_id),
    CONSTRAINT fk_answer_rubric_scores_criterion
        FOREIGN KEY (rubric_criterion_id) REFERENCES rubric_criteria (rubric_criterion_id),
    CONSTRAINT fk_answer_rubric_scores_verification
        FOREIGN KEY (answer_verification_id) REFERENCES answer_verifications (answer_verification_id),
    CONSTRAINT fk_answer_rubric_scores_scored_by_user
        FOREIGN KEY (scored_by_user_id) REFERENCES users (user_id),
    CONSTRAINT chk_answer_rubric_scores_points CHECK (points_awarded >= 0),
    INDEX idx_answer_rubric_scores_answer_version (student_answer_id, score_version)
) COMMENT='Criterion-level, versioned rubric scores for essay and other manually scored responses.';
