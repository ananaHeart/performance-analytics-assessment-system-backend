-- V3_001: Core identity, enrollment, curriculum, assessment delivery, and SF1 structure.
-- Apply only to the isolated performance_assessment_v3_db.

USE performance_assessment_v3_db;

ALTER TABLE users
    ADD COLUMN suffix_id TINYINT UNSIGNED NULL AFTER last_name,
    ADD COLUMN teaching_start_month TINYINT UNSIGNED NULL AFTER birth_date,
    ADD COLUMN teaching_start_year SMALLINT UNSIGNED NULL AFTER teaching_start_month,
    ADD COLUMN mfa_required BOOLEAN NOT NULL DEFAULT FALSE AFTER contact_verified_at,
    DROP COLUMN suffix,
    DROP COLUMN teaching_start_date,
    ADD CONSTRAINT fk_users_suffix
        FOREIGN KEY (suffix_id) REFERENCES suffixes (suffix_id),
    ADD CONSTRAINT chk_users_teaching_start_month
        CHECK (teaching_start_month IS NULL OR teaching_start_month BETWEEN 1 AND 12),
    ADD CONSTRAINT chk_users_teaching_start_year
        CHECK (teaching_start_year IS NULL OR teaching_start_year BETWEEN 1900 AND 2200);

ALTER TABLE students
    ADD COLUMN suffix_id TINYINT UNSIGNED NULL AFTER last_name,
    ADD COLUMN status_reason VARCHAR(255) NULL AFTER status,
    ADD COLUMN status_effective_at TIMESTAMP NULL AFTER status_reason,
    ADD COLUMN status_changed_by_user_id BIGINT UNSIGNED NULL AFTER status_effective_at,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP AFTER created_at,
    DROP COLUMN suffix,
    ADD CONSTRAINT fk_students_suffix
        FOREIGN KEY (suffix_id) REFERENCES suffixes (suffix_id),
    ADD CONSTRAINT fk_students_status_changed_by_user
        FOREIGN KEY (status_changed_by_user_id) REFERENCES users (user_id),
    ADD CONSTRAINT chk_students_lrn
        CHECK (student_lrn REGEXP '^[0-9]{12}$');

ALTER TABLE class_assignments
    ADD COLUMN ended_at TIMESTAMP NULL AFTER assigned_at,
    ADD COLUMN status_changed_by_user_id BIGINT UNSIGNED NULL AFTER status,
    ADD COLUMN status_reason VARCHAR(255) NULL AFTER status_changed_by_user_id,
    ADD CONSTRAINT fk_class_assignments_status_changed_by_user
        FOREIGN KEY (status_changed_by_user_id) REFERENCES users (user_id);

ALTER TABLE term_periods
    ADD COLUMN start_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER term_order,
    ADD COLUMN end_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER start_at,
    ADD COLUMN activation_mode ENUM('automatic', 'manual') NOT NULL DEFAULT 'automatic' AFTER status,
    ADD COLUMN activated_at TIMESTAMP NULL AFTER activation_mode,
    ADD COLUMN completed_at TIMESTAMP NULL AFTER activated_at,
    ADD COLUMN overridden_by_user_id BIGINT UNSIGNED NULL AFTER completed_at,
    ADD COLUMN override_reason VARCHAR(255) NULL AFTER overridden_by_user_id,
    ADD COLUMN overridden_at TIMESTAMP NULL AFTER override_reason,
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER overridden_at,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP AFTER created_at,
    ADD CONSTRAINT fk_term_periods_overridden_by_user
        FOREIGN KEY (overridden_by_user_id) REFERENCES users (user_id),
    ADD CONSTRAINT chk_term_periods_dates CHECK (end_at > start_at),
    ADD CONSTRAINT chk_term_periods_override
        CHECK (overridden_by_user_id IS NULL OR (override_reason IS NOT NULL AND overridden_at IS NOT NULL));

CREATE TABLE question_types (
    question_type_id SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    question_type_code VARCHAR(40) NOT NULL,
    question_type_name VARCHAR(80) NOT NULL,
    capture_mode ENUM('omr', 'ocr', 'manual', 'hybrid') NOT NULL,
    scoring_mode ENUM('automatic', 'manual', 'hybrid') NOT NULL,
    supports_omr BOOLEAN NOT NULL DEFAULT FALSE,
    supports_ocr BOOLEAN NOT NULL DEFAULT FALSE,
    supports_multiple_response BOOLEAN NOT NULL DEFAULT FALSE,
    requires_attachment BOOLEAN NOT NULL DEFAULT FALSE,
    requires_teacher_verification BOOLEAN NOT NULL DEFAULT TRUE,
    allows_teacher_answer_edit BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_question_types PRIMARY KEY (question_type_id),
    CONSTRAINT uk_question_types_code UNIQUE (question_type_code),
    CONSTRAINT uk_question_types_name UNIQUE (question_type_name)
) COMMENT='Supported response formats and their capture, scoring, and verification capabilities.';

CREATE TABLE performance_rule_sets (
    performance_rule_set_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    rule_set_uuid CHAR(36) NOT NULL,
    school_id VARCHAR(20) NULL,
    rule_set_name VARCHAR(120) NOT NULL,
    rule_version VARCHAR(30) NOT NULL,
    metric_scope ENUM('student_score', 'skill_mastery', 'class_mastery', 'intervention') NOT NULL,
    rule_definition JSON NOT NULL,
    rule_status ENUM('draft', 'active', 'retired') NOT NULL DEFAULT 'draft',
    approved_by_user_id BIGINT UNSIGNED NULL,
    effective_from_at TIMESTAMP NULL,
    effective_until_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_performance_rule_sets PRIMARY KEY (performance_rule_set_id),
    CONSTRAINT uk_performance_rule_sets_uuid UNIQUE (rule_set_uuid),
    CONSTRAINT uk_performance_rule_sets_version
        UNIQUE (school_id, rule_set_name, rule_version, metric_scope),
    CONSTRAINT fk_performance_rule_sets_school
        FOREIGN KEY (school_id) REFERENCES school_profiles (school_id),
    CONSTRAINT fk_performance_rule_sets_approved_by_user
        FOREIGN KEY (approved_by_user_id) REFERENCES users (user_id),
    CONSTRAINT chk_performance_rule_sets_dates
        CHECK (effective_until_at IS NULL OR effective_from_at IS NULL OR effective_until_at > effective_from_at),
    INDEX idx_performance_rule_sets_lookup (school_id, metric_scope, rule_status)
) COMMENT='Versioned and auditable scoring, mastery, and intervention thresholds.';

CREATE TABLE rubrics (
    rubric_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    rubric_uuid CHAR(36) NOT NULL,
    school_id VARCHAR(20) NOT NULL,
    created_by_user_id BIGINT UNSIGNED NOT NULL,
    rubric_name VARCHAR(120) NOT NULL,
    description TEXT NULL,
    total_points DECIMAL(8,2) NOT NULL,
    rubric_status ENUM('draft', 'active', 'archived') NOT NULL DEFAULT 'draft',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_rubrics PRIMARY KEY (rubric_id),
    CONSTRAINT uk_rubrics_uuid UNIQUE (rubric_uuid),
    CONSTRAINT uk_rubrics_school_name UNIQUE (school_id, rubric_name),
    CONSTRAINT fk_rubrics_school
        FOREIGN KEY (school_id) REFERENCES school_profiles (school_id),
    CONSTRAINT fk_rubrics_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES users (user_id),
    CONSTRAINT chk_rubrics_total_points CHECK (total_points > 0)
) COMMENT='Reusable school-owned rubrics for manual and essay scoring.';

CREATE TABLE rubric_criteria (
    rubric_criterion_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    rubric_id BIGINT UNSIGNED NOT NULL,
    criterion_order SMALLINT UNSIGNED NOT NULL,
    criterion_name VARCHAR(120) NOT NULL,
    criterion_description TEXT NOT NULL,
    maximum_points DECIMAL(8,2) NOT NULL,
    level_definition JSON NULL,
    is_required BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_rubric_criteria PRIMARY KEY (rubric_criterion_id),
    CONSTRAINT uk_rubric_criteria_order UNIQUE (rubric_id, criterion_order),
    CONSTRAINT fk_rubric_criteria_rubric
        FOREIGN KEY (rubric_id) REFERENCES rubrics (rubric_id) ON DELETE CASCADE,
    CONSTRAINT chk_rubric_criteria_maximum_points CHECK (maximum_points > 0)
) COMMENT='Ordered scoring criteria and point limits within a rubric.';

ALTER TABLE tests
    DROP FOREIGN KEY fk_tests_class_assignment,
    DROP INDEX idx_tests_assignment_term,
    ADD COLUMN test_uuid CHAR(36) NOT NULL AFTER test_id,
    ADD COLUMN school_id VARCHAR(20) NOT NULL AFTER test_uuid,
    ADD COLUMN created_by_user_id BIGINT UNSIGNED NOT NULL AFTER school_id,
    ADD COLUMN version_number INT UNSIGNED NOT NULL DEFAULT 1 AFTER created_by_user_id,
    ADD COLUMN source_test_id BIGINT UNSIGNED NULL AFTER version_number,
    ADD COLUMN published_at TIMESTAMP NULL AFTER status,
    ADD COLUMN content_locked_at TIMESTAMP NULL AFTER published_at,
    ADD COLUMN completed_at TIMESTAMP NULL AFTER content_locked_at,
    ADD COLUMN archived_at TIMESTAMP NULL AFTER completed_at,
    DROP COLUMN class_assignment_id,
    DROP COLUMN test_date,
    ADD CONSTRAINT uk_tests_uuid UNIQUE (test_uuid),
    ADD CONSTRAINT fk_tests_school
        FOREIGN KEY (school_id) REFERENCES school_profiles (school_id),
    ADD CONSTRAINT fk_tests_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES users (user_id),
    ADD CONSTRAINT fk_tests_source_test
        FOREIGN KEY (source_test_id) REFERENCES tests (test_id),
    ADD INDEX idx_tests_owner_term_status (school_id, created_by_user_id, term_period_id, status);

CREATE TABLE test_assignments (
    test_assignment_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    assignment_uuid CHAR(36) NOT NULL,
    test_id BIGINT UNSIGNED NOT NULL,
    class_assignment_id BIGINT UNSIGNED NOT NULL,
    assigned_by_user_id BIGINT UNSIGNED NOT NULL,
    open_at TIMESTAMP NULL,
    close_at TIMESTAMP NULL,
    assignment_status ENUM('planned', 'open', 'closed', 'archived') NOT NULL DEFAULT 'planned',
    allow_late_capture BOOLEAN NOT NULL DEFAULT FALSE,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_test_assignments PRIMARY KEY (test_assignment_id),
    CONSTRAINT uk_test_assignments_uuid UNIQUE (assignment_uuid),
    CONSTRAINT uk_test_assignments_test_class UNIQUE (test_id, class_assignment_id),
    CONSTRAINT fk_test_assignments_test
        FOREIGN KEY (test_id) REFERENCES tests (test_id),
    CONSTRAINT fk_test_assignments_class_assignment
        FOREIGN KEY (class_assignment_id) REFERENCES class_assignments (class_assignment_id),
    CONSTRAINT fk_test_assignments_assigned_by_user
        FOREIGN KEY (assigned_by_user_id) REFERENCES users (user_id),
    CONSTRAINT chk_test_assignments_dates CHECK (close_at IS NULL OR open_at IS NULL OR close_at > open_at),
    INDEX idx_test_assignments_class_status (class_assignment_id, assignment_status, open_at, close_at)
) COMMENT='Delivery of reusable assessment content to a specific teacher, class, and subject assignment.';

ALTER TABLE test_parts
    ADD COLUMN question_type_id SMALLINT UNSIGNED NOT NULL AFTER part_name,
    ADD COLUMN part_instructions TEXT NULL AFTER points_per_item,
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER part_instructions,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP AFTER created_at,
    DROP COLUMN part_type,
    ADD CONSTRAINT fk_test_parts_question_type
        FOREIGN KEY (question_type_id) REFERENCES question_types (question_type_id);

ALTER TABLE questions
    ADD COLUMN question_uuid CHAR(36) NOT NULL AFTER question_id,
    ADD COLUMN question_type_id SMALLINT UNSIGNED NOT NULL AFTER test_part_id,
    ADD COLUMN maximum_points DECIMAL(8,2) NOT NULL DEFAULT 1.00 AFTER question_text,
    ADD COLUMN rubric_id BIGINT UNSIGNED NULL AFTER maximum_points,
    ADD COLUMN response_instructions TEXT NULL AFTER rubric_id,
    ADD COLUMN answer_order_required BOOLEAN NOT NULL DEFAULT FALSE AFTER response_instructions,
    ADD COLUMN maximum_response_length INT UNSIGNED NULL AFTER answer_order_required,
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER maximum_response_length,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP AFTER created_at,
    DROP COLUMN option_a,
    DROP COLUMN option_b,
    DROP COLUMN option_c,
    DROP COLUMN option_d,
    DROP COLUMN option_e,
    ADD CONSTRAINT uk_questions_uuid UNIQUE (question_uuid),
    ADD CONSTRAINT fk_questions_question_type
        FOREIGN KEY (question_type_id) REFERENCES question_types (question_type_id),
    ADD CONSTRAINT fk_questions_rubric
        FOREIGN KEY (rubric_id) REFERENCES rubrics (rubric_id),
    ADD CONSTRAINT chk_questions_maximum_points CHECK (maximum_points > 0),
    ADD CONSTRAINT chk_questions_maximum_response_length
        CHECK (maximum_response_length IS NULL OR maximum_response_length > 0);

CREATE TABLE question_options (
    question_option_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    question_id BIGINT UNSIGNED NOT NULL,
    option_key VARCHAR(10) NOT NULL,
    option_text TEXT NOT NULL,
    option_order SMALLINT UNSIGNED NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_question_options PRIMARY KEY (question_option_id),
    CONSTRAINT uk_question_options_key UNIQUE (question_id, option_key),
    CONSTRAINT uk_question_options_order UNIQUE (question_id, option_order),
    CONSTRAINT fk_question_options_question
        FOREIGN KEY (question_id) REFERENCES questions (question_id) ON DELETE CASCADE,
    CONSTRAINT chk_question_options_order CHECK (option_order > 0)
) COMMENT='Normalized options for Multiple Choice and True/False questions.';

CREATE TABLE accepted_answers (
    accepted_answer_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    question_id BIGINT UNSIGNED NOT NULL,
    answer_order SMALLINT UNSIGNED NULL,
    accepted_text VARCHAR(500) NOT NULL,
    normalized_text VARCHAR(500) NOT NULL,
    matching_mode ENUM('exact', 'normalized') NOT NULL DEFAULT 'normalized',
    is_case_sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    points DECIMAL(8,2) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_accepted_answers PRIMARY KEY (accepted_answer_id),
    CONSTRAINT uk_accepted_answers_variant
        UNIQUE (question_id, answer_order, normalized_text),
    CONSTRAINT fk_accepted_answers_question
        FOREIGN KEY (question_id) REFERENCES questions (question_id) ON DELETE CASCADE,
    CONSTRAINT chk_accepted_answers_points CHECK (points >= 0)
) COMMENT='Accepted text answers and matching rules for identification and enumeration.';

ALTER TABLE answer_keys DROP CONSTRAINT chk_answer_keys_option;

ALTER TABLE answer_keys
    ADD COLUMN answer_key_type ENUM('option', 'accepted_text', 'rubric', 'manual') NOT NULL AFTER question_id,
    ADD COLUMN correct_question_option_id BIGINT UNSIGNED NULL AFTER answer_key_type,
    ADD COLUMN scoring_method ENUM('exact', 'normalized', 'rubric', 'manual') NOT NULL AFTER correct_question_option_id,
    ADD COLUMN rubric_id BIGINT UNSIGNED NULL AFTER scoring_method,
    ADD COLUMN answer_explanation TEXT NULL AFTER rubric_id,
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER answer_explanation,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP AFTER created_at,
    DROP COLUMN correct_option,
    ADD CONSTRAINT fk_answer_keys_correct_question_option
        FOREIGN KEY (correct_question_option_id) REFERENCES question_options (question_option_id),
    ADD CONSTRAINT fk_answer_keys_rubric
        FOREIGN KEY (rubric_id) REFERENCES rubrics (rubric_id),
    ADD CONSTRAINT chk_answer_keys_strategy CHECK (
        (answer_key_type = 'option' AND correct_question_option_id IS NOT NULL AND rubric_id IS NULL)
        OR (answer_key_type = 'accepted_text' AND correct_question_option_id IS NULL AND rubric_id IS NULL)
        OR (answer_key_type = 'rubric' AND correct_question_option_id IS NULL AND rubric_id IS NOT NULL)
        OR (answer_key_type = 'manual' AND correct_question_option_id IS NULL)
    );

DROP TABLE mappings;

CREATE TABLE part_skill_mappings (
    part_skill_mapping_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    test_part_id BIGINT UNSIGNED NOT NULL,
    skill_id BIGINT UNSIGNED NOT NULL,
    start_item_number INT UNSIGNED NOT NULL,
    end_item_number INT UNSIGNED NOT NULL,
    item_count INT UNSIGNED NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_part_skill_mappings PRIMARY KEY (part_skill_mapping_id),
    CONSTRAINT uk_part_skill_mappings_range
        UNIQUE (test_part_id, skill_id, start_item_number, end_item_number),
    CONSTRAINT fk_part_skill_mappings_test_part
        FOREIGN KEY (test_part_id) REFERENCES test_parts (test_part_id) ON DELETE CASCADE,
    CONSTRAINT fk_part_skill_mappings_skill
        FOREIGN KEY (skill_id) REFERENCES skills (skill_id),
    CONSTRAINT chk_part_skill_mappings_range CHECK (
        start_item_number >= 1
        AND end_item_number >= start_item_number
        AND item_count = end_item_number - start_item_number + 1
    ),
    INDEX idx_part_skill_mappings_part_range (test_part_id, start_item_number, end_item_number)
) COMMENT='Range-based test-part mappings used for competency mastery analytics.';

CREATE TABLE sf1_imports (
    sf1_import_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    import_uuid CHAR(36) NOT NULL,
    school_id VARCHAR(20) NOT NULL,
    academic_year_id INT UNSIGNED NOT NULL,
    target_class_id BIGINT UNSIGNED NULL,
    uploaded_by_user_id BIGINT UNSIGNED NOT NULL,
    source_file_name VARCHAR(255) NOT NULL,
    source_file_hash CHAR(64) NOT NULL,
    detected_school_year VARCHAR(20) NULL,
    detected_grade_level VARCHAR(30) NULL,
    detected_section_name VARCHAR(100) NULL,
    import_status ENUM('previewed', 'processing', 'completed', 'partial_success', 'failed', 'cancelled')
        NOT NULL DEFAULT 'previewed',
    total_row_count INT UNSIGNED NOT NULL DEFAULT 0,
    created_student_count INT UNSIGNED NOT NULL DEFAULT 0,
    updated_student_count INT UNSIGNED NOT NULL DEFAULT 0,
    unchanged_student_count INT UNSIGNED NOT NULL DEFAULT 0,
    conflict_row_count INT UNSIGNED NOT NULL DEFAULT 0,
    invalid_row_count INT UNSIGNED NOT NULL DEFAULT 0,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sf1_imports PRIMARY KEY (sf1_import_id),
    CONSTRAINT uk_sf1_imports_uuid UNIQUE (import_uuid),
    CONSTRAINT fk_sf1_imports_school
        FOREIGN KEY (school_id) REFERENCES school_profiles (school_id),
    CONSTRAINT fk_sf1_imports_academic_year
        FOREIGN KEY (academic_year_id) REFERENCES academic_years (academic_year_id),
    CONSTRAINT fk_sf1_imports_target_class
        FOREIGN KEY (target_class_id) REFERENCES classes (class_id),
    CONSTRAINT fk_sf1_imports_uploaded_by_user
        FOREIGN KEY (uploaded_by_user_id) REFERENCES users (user_id),
    INDEX idx_sf1_imports_duplicate_warning (school_id, academic_year_id, source_file_hash),
    INDEX idx_sf1_imports_uploader_time (uploaded_by_user_id, created_at)
) COMMENT='Auditable SF1 import operation; repeated file hashes warn but do not block incremental processing.';

ALTER TABLE class_lists
    ADD COLUMN membership_uuid CHAR(36) NOT NULL AFTER class_list_id,
    ADD COLUMN academic_year_id INT UNSIGNED NOT NULL AFTER student_id,
    ADD COLUMN enrollment_status ENUM('enrolled', 'transferred', 'dropped', 'completed')
        NOT NULL DEFAULT 'enrolled' AFTER academic_year_id,
    ADD COLUMN active_academic_year_id INT UNSIGNED AS (
        CASE WHEN enrollment_status = 'enrolled' THEN academic_year_id ELSE NULL END
    ) VIRTUAL AFTER enrollment_status,
    ADD COLUMN enrollment_source ENUM('sf1', 'manual', 'transfer') NOT NULL DEFAULT 'manual'
        AFTER active_academic_year_id,
    ADD COLUMN source_sf1_import_id BIGINT UNSIGNED NULL AFTER enrollment_source,
    ADD COLUMN previous_class_list_id BIGINT UNSIGNED NULL AFTER source_sf1_import_id,
    ADD COLUMN enrolled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER previous_class_list_id,
    ADD COLUMN ended_at TIMESTAMP NULL AFTER enrolled_at,
    ADD COLUMN status_reason VARCHAR(255) NULL AFTER ended_at,
    ADD COLUMN status_changed_by_user_id BIGINT UNSIGNED NULL AFTER status_reason,
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER status_changed_by_user_id,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP AFTER created_at,
    ADD CONSTRAINT uk_class_lists_membership_uuid UNIQUE (membership_uuid),
    ADD CONSTRAINT uk_class_lists_active_student_year UNIQUE (student_id, active_academic_year_id),
    ADD CONSTRAINT fk_class_lists_academic_year
        FOREIGN KEY (academic_year_id) REFERENCES academic_years (academic_year_id),
    ADD CONSTRAINT fk_class_lists_source_sf1_import
        FOREIGN KEY (source_sf1_import_id) REFERENCES sf1_imports (sf1_import_id),
    ADD CONSTRAINT fk_class_lists_previous_membership
        FOREIGN KEY (previous_class_list_id) REFERENCES class_lists (class_list_id),
    ADD CONSTRAINT fk_class_lists_status_changed_by_user
        FOREIGN KEY (status_changed_by_user_id) REFERENCES users (user_id),
    ADD CONSTRAINT chk_class_lists_end_state CHECK (
        (enrollment_status = 'enrolled' AND ended_at IS NULL)
        OR (enrollment_status <> 'enrolled' AND ended_at IS NOT NULL)
    );

CREATE TABLE sf1_import_items (
    sf1_import_item_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sf1_import_id BIGINT UNSIGNED NOT NULL,
    row_number INT UNSIGNED NOT NULL,
    student_lrn_snapshot VARCHAR(20) NOT NULL,
    source_row_hash CHAR(64) NOT NULL,
    student_id BIGINT UNSIGNED NULL,
    class_list_id BIGINT UNSIGNED NULL,
    outcome_status ENUM('created', 'updated', 'existing_unchanged', 'already_enrolled', 'enrollment_conflict', 'invalid') NOT NULL,
    warning_code VARCHAR(50) NULL,
    outcome_message VARCHAR(255) NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sf1_import_items PRIMARY KEY (sf1_import_item_id),
    CONSTRAINT uk_sf1_import_items_row UNIQUE (sf1_import_id, row_number),
    CONSTRAINT fk_sf1_import_items_import
        FOREIGN KEY (sf1_import_id) REFERENCES sf1_imports (sf1_import_id) ON DELETE CASCADE,
    CONSTRAINT fk_sf1_import_items_student
        FOREIGN KEY (student_id) REFERENCES students (student_id),
    CONSTRAINT fk_sf1_import_items_class_list
        FOREIGN KEY (class_list_id) REFERENCES class_lists (class_list_id),
    INDEX idx_sf1_import_items_lrn (student_lrn_snapshot),
    INDEX idx_sf1_import_items_outcome (sf1_import_id, outcome_status)
) COMMENT='Per-row incremental SF1 outcomes, warnings, and enrollment conflicts.';
