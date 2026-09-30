-- DRAFT: additive scan-page receipt/recovery ledger after V3_014.
-- Not applied to the school database. Select the intended database explicitly before execution.
-- No USE, DROP, or baseline configuration change: review/deploy separately after validation.
CREATE TABLE mobile_scan_uploads (
    scan_page_uuid CHAR(36) NOT NULL,
    teacher_user_id BIGINT UNSIGNED NOT NULL,
    school_id VARCHAR(20) NOT NULL,
    sync_uuid CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    request_json LONGTEXT NOT NULL,
    attachment_uuid CHAR(36) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    file_size_bytes BIGINT UNSIGNED NOT NULL,
    content_hash CHAR(64) NOT NULL,
    width_pixels INT UNSIGNED NOT NULL,
    height_pixels INT UNSIGNED NOT NULL,
    upload_state VARCHAR(20) NOT NULL DEFAULT 'pending',
    backend_scan_page_id BIGINT UNSIGNED NULL,
    receipt_page_status VARCHAR(30) NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    last_error_code VARCHAR(50) NULL,
    last_attempt_at TIMESTAMP NULL,
    committed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_mobile_scan_uploads PRIMARY KEY (scan_page_uuid),
    CONSTRAINT uk_mobile_scan_uploads_attachment UNIQUE (attachment_uuid),
    CONSTRAINT uk_mobile_scan_uploads_page UNIQUE (backend_scan_page_id),
    CONSTRAINT fk_mobile_scan_uploads_teacher FOREIGN KEY (teacher_user_id) REFERENCES users(user_id),
    CONSTRAINT fk_mobile_scan_uploads_page FOREIGN KEY (backend_scan_page_id) REFERENCES scan_pages(scan_page_id),
    CONSTRAINT chk_mobile_scan_uploads_json CHECK (JSON_VALID(request_json)),
    CONSTRAINT chk_mobile_scan_uploads_state CHECK (
        (upload_state = 'pending' AND backend_scan_page_id IS NULL AND receipt_page_status IS NULL AND committed_at IS NULL)
        OR (upload_state = 'committed' AND backend_scan_page_id IS NOT NULL AND receipt_page_status = 'captured' AND committed_at IS NOT NULL)
    ),
    CONSTRAINT chk_mobile_scan_uploads_size CHECK (file_size_bytes BETWEEN 1 AND 15728640),
    CONSTRAINT chk_mobile_scan_uploads_pixels CHECK (width_pixels > 0 AND height_pixels > 0
        AND CAST(width_pixels AS DECIMAL(20,0)) * height_pixels <= 40000000),
    INDEX idx_mobile_scan_uploads_recovery (upload_state, last_attempt_at, created_at),
    INDEX idx_mobile_scan_uploads_batch (sync_uuid, upload_state)
) ENGINE=InnoDB;
