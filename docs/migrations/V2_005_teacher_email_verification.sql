-- Teacher email OTP verification support.
-- Apply only to performance_assessment_v2_db.

USE performance_assessment_v2_db;

CREATE TABLE IF NOT EXISTS email_verification_otps (
    email_verification_otp_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    otp_hash VARCHAR(100) NOT NULL,
    attempt_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    max_attempts SMALLINT UNSIGNED NOT NULL DEFAULT 5,
    expires_at DATETIME NOT NULL,
    resend_available_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_email_verification_otps PRIMARY KEY (email_verification_otp_id),
    CONSTRAINT fk_email_verification_otps_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON DELETE CASCADE,
    INDEX idx_email_verification_otps_user_active (user_id, used_at, expires_at),
    INDEX idx_email_verification_otps_resend (user_id, resend_available_at)
) COMMENT='Hashed, expiring, one-time email verification codes for teacher registration.';
