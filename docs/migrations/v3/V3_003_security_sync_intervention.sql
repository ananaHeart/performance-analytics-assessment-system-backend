-- V3_003: Retry-safe sync, interventions, generalized verification, and TOTP MFA.
-- Apply after V3_002_assessment_capture.sql.

USE performance_assessment_v3_db;

ALTER TABLE intervention_results
    ADD COLUMN performance_rule_set_id BIGINT UNSIGNED NOT NULL AFTER intervention_id,
    ADD COLUMN mastery_rate_snapshot DECIMAL(7,4) NOT NULL AFTER performance_rule_set_id,
    ADD COLUMN recommendation_status ENUM('maintain', 'review', 'reteach', 'priority_intervention')
        NOT NULL AFTER mastery_rate_snapshot,
    ADD COLUMN recommendation_snapshot VARCHAR(500) NOT NULL AFTER recommendation_status,
    ADD COLUMN generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER recommendation_snapshot,
    ADD COLUMN acknowledged_by_user_id BIGINT UNSIGNED NULL AFTER generated_at,
    ADD COLUMN acknowledged_at TIMESTAMP NULL AFTER acknowledged_by_user_id,
    ADD CONSTRAINT fk_intervention_results_performance_rule_set
        FOREIGN KEY (performance_rule_set_id)
        REFERENCES performance_rule_sets (performance_rule_set_id),
    ADD CONSTRAINT fk_intervention_results_acknowledged_by_user
        FOREIGN KEY (acknowledged_by_user_id) REFERENCES users (user_id),
    ADD CONSTRAINT chk_intervention_results_mastery
        CHECK (mastery_rate_snapshot BETWEEN 0 AND 100);

CREATE TABLE student_intervention_cases (
    student_intervention_case_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    case_uuid CHAR(36) NOT NULL,
    school_id VARCHAR(20) NOT NULL,
    student_id BIGINT UNSIGNED NOT NULL,
    academic_year_id INT UNSIGNED NOT NULL,
    term_period_id INT UNSIGNED NULL,
    class_list_id BIGINT UNSIGNED NOT NULL,
    skill_id BIGINT UNSIGNED NULL,
    source_test_result_id BIGINT UNSIGNED NULL,
    performance_rule_set_id BIGINT UNSIGNED NULL,
    opened_by_user_id BIGINT UNSIGNED NOT NULL,
    assigned_to_user_id BIGINT UNSIGNED NULL,
    case_status ENUM('open', 'in_progress', 'monitoring', 'resolved', 'closed')
        NOT NULL DEFAULT 'open',
    priority_level ENUM('normal', 'review', 'priority') NOT NULL DEFAULT 'normal',
    recommendation_snapshot VARCHAR(500) NOT NULL,
    opened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_at TIMESTAMP NULL,
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_student_intervention_cases PRIMARY KEY (student_intervention_case_id),
    CONSTRAINT uk_student_intervention_cases_uuid UNIQUE (case_uuid),
    CONSTRAINT fk_student_intervention_cases_school
        FOREIGN KEY (school_id) REFERENCES school_profiles (school_id),
    CONSTRAINT fk_student_intervention_cases_student
        FOREIGN KEY (student_id) REFERENCES students (student_id),
    CONSTRAINT fk_student_intervention_cases_academic_year
        FOREIGN KEY (academic_year_id) REFERENCES academic_years (academic_year_id),
    CONSTRAINT fk_student_intervention_cases_term_period
        FOREIGN KEY (term_period_id) REFERENCES term_periods (term_period_id),
    CONSTRAINT fk_student_intervention_cases_class_list
        FOREIGN KEY (class_list_id) REFERENCES class_lists (class_list_id),
    CONSTRAINT fk_student_intervention_cases_skill
        FOREIGN KEY (skill_id) REFERENCES skills (skill_id),
    CONSTRAINT fk_student_intervention_cases_source_result
        FOREIGN KEY (source_test_result_id) REFERENCES test_results (test_result_id),
    CONSTRAINT fk_student_intervention_cases_rule_set
        FOREIGN KEY (performance_rule_set_id)
        REFERENCES performance_rule_sets (performance_rule_set_id),
    CONSTRAINT fk_student_intervention_cases_opened_by_user
        FOREIGN KEY (opened_by_user_id) REFERENCES users (user_id),
    CONSTRAINT fk_student_intervention_cases_assigned_to_user
        FOREIGN KEY (assigned_to_user_id) REFERENCES users (user_id),
    CONSTRAINT chk_student_intervention_cases_dates
        CHECK (resolved_at IS NULL OR resolved_at >= opened_at),
    INDEX idx_student_intervention_cases_student_status
        (student_id, academic_year_id, case_status),
    INDEX idx_student_intervention_cases_assignee_status
        (assigned_to_user_id, case_status, due_at)
) COMMENT='Teacher-facing learner intervention cases with traceable analytics evidence.';

CREATE TABLE student_intervention_updates (
    student_intervention_update_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    student_intervention_case_id BIGINT UNSIGNED NOT NULL,
    updated_by_user_id BIGINT UNSIGNED NOT NULL,
    update_type ENUM('note', 'action', 'status_change', 'follow_up', 'outcome') NOT NULL,
    previous_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NULL,
    action_taken VARCHAR(255) NULL,
    update_notes TEXT NULL,
    outcome_status ENUM('pending', 'improved', 'no_change', 'regressed') NULL,
    follow_up_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_student_intervention_updates PRIMARY KEY (student_intervention_update_id),
    CONSTRAINT fk_student_intervention_updates_case
        FOREIGN KEY (student_intervention_case_id)
        REFERENCES student_intervention_cases (student_intervention_case_id) ON DELETE CASCADE,
    CONSTRAINT fk_student_intervention_updates_user
        FOREIGN KEY (updated_by_user_id) REFERENCES users (user_id),
    CONSTRAINT chk_student_intervention_updates_status_change CHECK (
        update_type <> 'status_change'
        OR (previous_status IS NOT NULL AND new_status IS NOT NULL)
    ),
    INDEX idx_student_intervention_updates_case_time
        (student_intervention_case_id, created_at)
) COMMENT='Append-only intervention notes, actions, status changes, and learner outcomes.';

ALTER TABLE syncs
    DROP FOREIGN KEY fk_syncs_test,
    ADD COLUMN test_assignment_id BIGINT UNSIGNED NULL AFTER user_id,
    ADD COLUMN retry_count SMALLINT UNSIGNED NOT NULL DEFAULT 0 AFTER payload_hash,
    ADD COLUMN last_retry_at TIMESTAMP NULL AFTER retry_count,
    ADD COLUMN request_item_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER last_retry_at,
    ADD COLUMN idempotency_version SMALLINT UNSIGNED NOT NULL DEFAULT 1 AFTER request_item_count,
    DROP COLUMN test_id,
    ADD CONSTRAINT fk_syncs_test_assignment
        FOREIGN KEY (test_assignment_id) REFERENCES test_assignments (test_assignment_id),
    ADD INDEX idx_syncs_assignment_started (test_assignment_id, started_at),
    ADD CONSTRAINT chk_syncs_request_item_count CHECK (request_item_count >= 0);

ALTER TABLE sync_items
    ADD COLUMN attempt_count SMALLINT UNSIGNED NOT NULL DEFAULT 0 AFTER sync_status,
    ADD COLUMN last_attempt_at TIMESTAMP NULL AFTER attempt_count,
    ADD COLUMN processed_at TIMESTAMP NULL AFTER last_attempt_at,
    MODIFY COLUMN sync_action ENUM('upsert') NOT NULL DEFAULT 'upsert';

CREATE TABLE verification_challenges (
    verification_challenge_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    challenge_uuid CHAR(36) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    verification_purpose ENUM(
        'teacher_registration',
        'email_verification',
        'contact_verification',
        'sensitive_action'
    ) NOT NULL,
    delivery_channel ENUM('email', 'sms') NOT NULL,
    destination_masked VARCHAR(160) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    challenge_status ENUM('pending', 'verified', 'expired', 'locked', 'cancelled')
        NOT NULL DEFAULT 'pending',
    delivery_status ENUM('queued', 'sent', 'failed') NOT NULL DEFAULT 'queued',
    delivery_provider VARCHAR(50) NULL,
    provider_message_id VARCHAR(120) NULL,
    attempt_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    maximum_attempt_count SMALLINT UNSIGNED NOT NULL DEFAULT 5,
    resend_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    last_sent_at TIMESTAMP NULL,
    next_resend_at TIMESTAMP NULL,
    expires_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_verification_challenges PRIMARY KEY (verification_challenge_id),
    CONSTRAINT uk_verification_challenges_uuid UNIQUE (challenge_uuid),
    CONSTRAINT fk_verification_challenges_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT chk_verification_challenges_attempts CHECK (
        maximum_attempt_count > 0 AND attempt_count <= maximum_attempt_count
    ),
    CONSTRAINT chk_verification_challenges_expiry CHECK (expires_at > created_at),
    INDEX idx_verification_challenges_user_status
        (user_id, verification_purpose, challenge_status, expires_at),
    INDEX idx_verification_challenges_resend (user_id, delivery_channel, next_resend_at)
) COMMENT='Hashed, expiring, rate-limited email and SMS verification challenges.';

DROP TABLE email_verification_otps;

CREATE TABLE user_mfa_factors (
    user_mfa_factor_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    factor_uuid CHAR(36) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    factor_type ENUM('totp') NOT NULL DEFAULT 'totp',
    factor_name VARCHAR(80) NOT NULL DEFAULT 'Authenticator app',
    secret_ciphertext VARBINARY(1024) NOT NULL,
    secret_key_version SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    totp_algorithm ENUM('SHA1', 'SHA256', 'SHA512') NOT NULL DEFAULT 'SHA1',
    totp_digits TINYINT UNSIGNED NOT NULL DEFAULT 6,
    totp_period_seconds SMALLINT UNSIGNED NOT NULL DEFAULT 30,
    factor_status ENUM('pending', 'active', 'disabled', 'revoked') NOT NULL DEFAULT 'pending',
    active_user_id BIGINT UNSIGNED AS (
        CASE WHEN factor_status = 'active' THEN user_id ELSE NULL END
    ) VIRTUAL,
    enrolled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP NULL,
    last_used_at TIMESTAMP NULL,
    disabled_at TIMESTAMP NULL,
    revoked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_mfa_factors PRIMARY KEY (user_mfa_factor_id),
    CONSTRAINT uk_user_mfa_factors_uuid UNIQUE (factor_uuid),
    CONSTRAINT uk_user_mfa_factors_one_active UNIQUE (active_user_id, factor_type),
    CONSTRAINT fk_user_mfa_factors_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT chk_user_mfa_factors_totp CHECK (
        totp_digits IN (6, 8) AND totp_period_seconds BETWEEN 15 AND 120
    ),
    INDEX idx_user_mfa_factors_user_status (user_id, factor_status)
) COMMENT='Encrypted TOTP authenticator factors. Secrets must never be stored as plain text.';

CREATE TABLE mfa_recovery_codes (
    mfa_recovery_code_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_mfa_factor_id BIGINT UNSIGNED NOT NULL,
    recovery_batch_uuid CHAR(36) NOT NULL,
    code_hash CHAR(64) NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_mfa_recovery_codes PRIMARY KEY (mfa_recovery_code_id),
    CONSTRAINT uk_mfa_recovery_codes_hash UNIQUE (user_mfa_factor_id, code_hash),
    CONSTRAINT fk_mfa_recovery_codes_factor
        FOREIGN KEY (user_mfa_factor_id)
        REFERENCES user_mfa_factors (user_mfa_factor_id) ON DELETE CASCADE,
    INDEX idx_mfa_recovery_codes_batch
        (user_mfa_factor_id, recovery_batch_uuid, used_at)
) COMMENT='One-time hashed recovery codes issued during authenticator enrollment.';

CREATE TABLE mfa_authentication_challenges (
    mfa_authentication_challenge_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    challenge_uuid CHAR(36) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    user_mfa_factor_id BIGINT UNSIGNED NOT NULL,
    challenge_status ENUM('pending', 'verified', 'expired', 'locked', 'cancelled')
        NOT NULL DEFAULT 'pending',
    attempt_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    maximum_attempt_count SMALLINT UNSIGNED NOT NULL DEFAULT 5,
    expires_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_mfa_authentication_challenges PRIMARY KEY (mfa_authentication_challenge_id),
    CONSTRAINT uk_mfa_authentication_challenges_uuid UNIQUE (challenge_uuid),
    CONSTRAINT fk_mfa_authentication_challenges_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_mfa_authentication_challenges_factor
        FOREIGN KEY (user_mfa_factor_id)
        REFERENCES user_mfa_factors (user_mfa_factor_id) ON DELETE CASCADE,
    CONSTRAINT chk_mfa_authentication_challenges_attempts CHECK (
        maximum_attempt_count > 0 AND attempt_count <= maximum_attempt_count
    ),
    CONSTRAINT chk_mfa_authentication_challenges_expiry CHECK (expires_at > created_at),
    INDEX idx_mfa_authentication_challenges_user_status
        (user_id, challenge_status, expires_at)
) COMMENT='Short-lived login challenges for password-plus-authenticator authentication.';

ALTER TABLE auth_sessions
    MODIFY COLUMN expires_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT 'Session expiration timestamp.',
    ADD COLUMN authentication_level ENUM('password', 'mfa') NOT NULL DEFAULT 'password'
        AFTER user_id,
    ADD COLUMN user_mfa_factor_id BIGINT UNSIGNED NULL AFTER authentication_level,
    ADD COLUMN mfa_verified_at TIMESTAMP NULL AFTER user_mfa_factor_id,
    ADD CONSTRAINT fk_auth_sessions_mfa_factor
        FOREIGN KEY (user_mfa_factor_id) REFERENCES user_mfa_factors (user_mfa_factor_id),
    ADD CONSTRAINT chk_auth_sessions_mfa_state CHECK (
        authentication_level = 'password'
        OR (user_mfa_factor_id IS NOT NULL AND mfa_verified_at IS NOT NULL)
    );

ALTER TABLE login_attempts
    MODIFY COLUMN failure_reason ENUM(
        'invalid_credentials',
        'pending_email_verification',
        'pending_approval',
        'rejected_account',
        'inactive_account',
        'locked_account',
        'email_not_verified',
        'mfa_required',
        'invalid_mfa_code',
        'mfa_locked',
        'mfa_not_enrolled',
        'rate_limited',
        'other'
    ) NULL;
