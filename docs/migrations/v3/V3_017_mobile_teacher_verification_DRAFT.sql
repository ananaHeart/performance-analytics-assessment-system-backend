-- DRAFT after V3_016. Apply only in reviewed deployment; no database selection or automatic startup migration.
CREATE TABLE mobile_verification_batches (
    operation_uuid CHAR(36) NOT NULL PRIMARY KEY,
    sync_id BIGINT UNSIGNED NOT NULL,
    request_hash CHAR(64) NOT NULL,
    request_json LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mobile_verification_batches_sync UNIQUE (sync_id),
    CONSTRAINT fk_mobile_verification_batches_sync FOREIGN KEY (sync_id) REFERENCES syncs(sync_id),
    CONSTRAINT chk_mobile_verification_batches_json CHECK (JSON_VALID(request_json))
) ENGINE=InnoDB;

CREATE TABLE mobile_verification_items (
    sync_item_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    response_json LONGTEXT NULL,
    CONSTRAINT fk_mobile_verification_items_sync_item FOREIGN KEY (sync_item_id) REFERENCES sync_items(sync_item_id),
    CONSTRAINT chk_mobile_verification_items_json CHECK (response_json IS NULL OR JSON_VALID(response_json))
) ENGINE=InnoDB;

ALTER TABLE scan_verifications
    MODIFY COLUMN reason_detail VARCHAR(4000) NULL,
    ADD COLUMN client_decided_at VARCHAR(40) NULL COMMENT 'Untrusted client UTC instant; server decided_at remains authoritative.',
    ADD COLUMN mobile_operation_uuid CHAR(36) NULL,
    ADD CONSTRAINT fk_scan_verifications_mobile_operation FOREIGN KEY (mobile_operation_uuid)
        REFERENCES mobile_verification_batches(operation_uuid);

-- Objective audit is separate from answer_verifications, which is reserved for written evaluation/correction.
CREATE TABLE mobile_objective_verifications (
    verification_uuid CHAR(36) NOT NULL PRIMARY KEY,
    student_answer_id BIGINT UNSIGNED NOT NULL,
    omr_detection_id BIGINT UNSIGNED NOT NULL,
    operation_uuid CHAR(36) NOT NULL,
    verified_by_user_id BIGINT UNSIGNED NOT NULL,
    comment VARCHAR(4000) NULL,
    client_decided_at VARCHAR(40) NOT NULL,
    verified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mobile_objective_verifications_answer UNIQUE (student_answer_id),
    CONSTRAINT fk_mobile_objective_verifications_answer FOREIGN KEY (student_answer_id) REFERENCES student_answers(student_answer_id),
    CONSTRAINT fk_mobile_objective_verifications_detection FOREIGN KEY (omr_detection_id) REFERENCES omr_detections(omr_detection_id),
    CONSTRAINT fk_mobile_objective_verifications_operation FOREIGN KEY (operation_uuid) REFERENCES mobile_verification_batches(operation_uuid),
    CONSTRAINT fk_mobile_objective_verifications_teacher FOREIGN KEY (verified_by_user_id) REFERENCES users(user_id)
) ENGINE=InnoDB;
