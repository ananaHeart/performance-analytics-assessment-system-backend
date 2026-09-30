-- DRAFT, after V3_015. Review/deploy separately; not applied to the school database.
-- No database selection or automatic baseline change.
ALTER TABLE test_results
    ADD COLUMN mobile_revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    ADD CONSTRAINT chk_test_results_mobile_revision CHECK (mobile_revision BETWEEN 1 AND 9007199254740991);

CREATE TABLE mobile_detection_uploads (
    operation_uuid CHAR(36) NOT NULL,
    sync_id BIGINT UNSIGNED NOT NULL,
    scan_page_id BIGINT UNSIGNED NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_json LONGTEXT NOT NULL,
    committed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_mobile_detection_uploads PRIMARY KEY (operation_uuid),
    CONSTRAINT uk_mobile_detection_uploads_sync UNIQUE (sync_id),
    CONSTRAINT fk_mobile_detection_uploads_sync FOREIGN KEY (sync_id) REFERENCES syncs(sync_id),
    CONSTRAINT fk_mobile_detection_uploads_page FOREIGN KEY (scan_page_id) REFERENCES scan_pages(scan_page_id),
    CONSTRAINT chk_mobile_detection_uploads_response CHECK (JSON_VALID(response_json))
) ENGINE=InnoDB;
