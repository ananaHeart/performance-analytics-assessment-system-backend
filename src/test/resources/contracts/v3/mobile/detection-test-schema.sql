-- Additional H2 projection only; full MariaDB migration validation is separate.
ALTER TABLE questions ADD question_uuid CHAR(36) UNIQUE;
ALTER TABLE answer_sheet_regions ADD region_uuid CHAR(36) UNIQUE;
ALTER TABLE answer_sheet_regions ADD answer_sheet_version_id BIGINT REFERENCES answer_sheet_versions;
ALTER TABLE answer_sheet_regions ADD test_part_id BIGINT REFERENCES test_parts;
ALTER TABLE answer_sheet_regions ADD question_type_id INT REFERENCES question_types;
ALTER TABLE answer_sheet_regions ADD region_type VARCHAR(30);
ALTER TABLE answer_sheet_regions ADD geometry_snapshot VARCHAR(4000);
ALTER TABLE test_results ADD mobile_revision BIGINT DEFAULT 1 NOT NULL CHECK (mobile_revision BETWEEN 1 AND 9007199254740991);
CREATE TABLE question_options(question_option_id BIGINT AUTO_INCREMENT PRIMARY KEY,question_id BIGINT REFERENCES questions,
    option_key VARCHAR(10),is_active BOOLEAN DEFAULT TRUE, UNIQUE(question_id,option_key));
CREATE TABLE omr_detections(omr_detection_id BIGINT AUTO_INCREMENT PRIMARY KEY,detection_uuid CHAR(36) UNIQUE,
    scan_session_id BIGINT REFERENCES scan_sessions,scan_page_id BIGINT REFERENCES scan_pages,
    answer_sheet_region_id BIGINT REFERENCES answer_sheet_regions,question_id BIGINT REFERENCES questions,
    detected_option CHAR(1),confidence_score DECIMAL(5,4) CHECK(confidence_score BETWEEN 0 AND 1),
    detection_status VARCHAR(30),raw_mark CLOB NOT NULL, UNIQUE(scan_page_id,answer_sheet_region_id));
CREATE TABLE mobile_detection_uploads(operation_uuid CHAR(36) PRIMARY KEY,sync_id BIGINT NOT NULL UNIQUE REFERENCES syncs,
    scan_page_id BIGINT NOT NULL REFERENCES scan_pages,request_hash CHAR(64) NOT NULL,response_json CLOB NOT NULL,
    committed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
