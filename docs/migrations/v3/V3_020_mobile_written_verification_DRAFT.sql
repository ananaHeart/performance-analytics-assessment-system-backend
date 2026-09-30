-- DRAFT after V3_019. Isolated validation only; no configured baseline or school DB change.
ALTER TABLE student_answers
    ADD COLUMN response_evidence_attachment_id BIGINT UNSIGNED NULL,
    ADD CONSTRAINT fk_student_answers_response_evidence FOREIGN KEY(response_evidence_attachment_id)
        REFERENCES answer_attachments(answer_attachment_id),
    DROP CONSTRAINT chk_student_answers_response,
    ADD CONSTRAINT chk_student_answers_response CHECK(
        selected_question_option_id IS NOT NULL OR response_text IS NOT NULL
        OR answer_status IN ('blank','multiple','uncertain','invalid','pending_manual')
        OR (capture_source='manual' AND response_evidence_attachment_id IS NOT NULL)),
    ADD CONSTRAINT chk_student_answers_evidence_source CHECK(
        response_evidence_attachment_id IS NULL OR (capture_source='manual' AND selected_question_option_id IS NULL));

-- Snapshot the accepted reference per result operation; later edits cannot erase its audit meaning.
CREATE TABLE mobile_written_references (
    sync_item_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    test_version_number INT UNSIGNED NOT NULL,
    evaluation_reference_hash CHAR(64) NOT NULL,
    reference_json LONGTEXT NOT NULL,
    accepted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mobile_written_reference_item FOREIGN KEY(sync_item_id) REFERENCES sync_items(sync_item_id),
    CONSTRAINT chk_mobile_written_reference_version CHECK(test_version_number > 0),
    CONSTRAINT chk_mobile_written_reference_json CHECK(JSON_VALID(reference_json))
) ENGINE=InnoDB;

ALTER TABLE answer_verifications
    MODIFY COLUMN reason_detail VARCHAR(4000) NULL,
    ADD COLUMN mobile_sync_item_id BIGINT UNSIGNED NULL,
    ADD COLUMN client_decided_at VARCHAR(40) NULL,
    ADD COLUMN evaluation_snapshot_json LONGTEXT NULL,
    ADD CONSTRAINT fk_answer_verifications_mobile_reference FOREIGN KEY(mobile_sync_item_id)
        REFERENCES mobile_written_references(sync_item_id),
    ADD CONSTRAINT chk_answer_verifications_evaluation_json CHECK(evaluation_snapshot_json IS NULL OR JSON_VALID(evaluation_snapshot_json));
