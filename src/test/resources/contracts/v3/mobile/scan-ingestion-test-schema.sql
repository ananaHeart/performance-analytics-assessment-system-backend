-- Isolated H2 test projection of the columns used by ingestion, based on V3_000/001/002/003/006/009/013.
-- NOT a production migration or a reproduction/validation of the complete V3_014 MySQL schema.
CREATE TABLE statuses (status_id BIGINT PRIMARY KEY, status_name VARCHAR(30));
CREATE TABLE roles (role_id BIGINT PRIMARY KEY, role_name VARCHAR(30));
CREATE TABLE users (user_id BIGINT PRIMARY KEY, school_id VARCHAR(20), status_id BIGINT REFERENCES statuses,
    role_id BIGINT DEFAULT 1 REFERENCES roles);
CREATE TABLE sections (section_id BIGINT PRIMARY KEY, school_id VARCHAR(20));
CREATE TABLE classes (class_id BIGINT PRIMARY KEY, section_id BIGINT REFERENCES sections, status VARCHAR(20));
CREATE TABLE class_assignments (class_assignment_id BIGINT PRIMARY KEY, class_id BIGINT REFERENCES classes,
    user_id BIGINT REFERENCES users, status VARCHAR(20));
CREATE TABLE students (student_id BIGINT PRIMARY KEY, school_id VARCHAR(20), status VARCHAR(20));
CREATE TABLE class_lists (class_list_id BIGINT PRIMARY KEY, class_id BIGINT REFERENCES classes,
    student_id BIGINT REFERENCES students, enrollment_status VARCHAR(20));
CREATE TABLE tests (test_id BIGINT PRIMARY KEY, school_id VARCHAR(20), version_number INT,
    total_items INT, status VARCHAR(20));
CREATE TABLE test_assignments (test_assignment_id BIGINT PRIMARY KEY, assignment_uuid CHAR(36) UNIQUE,
    test_id BIGINT REFERENCES tests, class_assignment_id BIGINT REFERENCES class_assignments,
    assignment_status VARCHAR(20), open_at TIMESTAMP, close_at TIMESTAMP, allow_late_capture BOOLEAN);
CREATE TABLE question_types (question_type_id INT PRIMARY KEY, question_type_code VARCHAR(30));
CREATE TABLE test_parts (test_part_id BIGINT PRIMARY KEY, test_id BIGINT REFERENCES tests);
CREATE TABLE questions (question_id BIGINT PRIMARY KEY, test_part_id BIGINT REFERENCES test_parts,
    question_type_id INT REFERENCES question_types, maximum_points DECIMAL(8,2));
CREATE TABLE paper_sizes (paper_size_id INT PRIMARY KEY, paper_size_code VARCHAR(20));
CREATE TABLE omr_templates (omr_template_id BIGINT PRIMARY KEY, template_code VARCHAR(80),
    template_version VARCHAR(50), minimum_scanner_version VARCHAR(50), template_status VARCHAR(20));
CREATE TABLE answer_sheet_versions (answer_sheet_version_id BIGINT PRIMARY KEY, answer_sheet_uuid CHAR(36) UNIQUE,
    test_assignment_id BIGINT REFERENCES test_assignments, paper_size_id INT REFERENCES paper_sizes,
    test_version_number INT, total_questions INT, total_pages INT, generation_status VARCHAR(20));
CREATE TABLE answer_sheet_pages (answer_sheet_page_id BIGINT PRIMARY KEY, page_uuid CHAR(36) UNIQUE,
    answer_sheet_version_id BIGINT REFERENCES answer_sheet_versions, omr_template_id BIGINT REFERENCES omr_templates,
    page_number INT, total_pages INT, qr_payload VARCHAR(1000), qr_payload_hash CHAR(64), page_status VARCHAR(20));
CREATE TABLE answer_sheet_regions (answer_sheet_region_id BIGINT PRIMARY KEY,
    answer_sheet_page_id BIGINT REFERENCES answer_sheet_pages, question_id BIGINT REFERENCES questions);
CREATE TABLE test_results (test_result_id BIGINT AUTO_INCREMENT PRIMARY KEY, result_uuid CHAR(36) NOT NULL UNIQUE,
    test_assignment_id BIGINT NOT NULL REFERENCES test_assignments, class_list_id BIGINT NOT NULL REFERENCES class_lists,
    attempt_number INT NOT NULL CHECK (attempt_number BETWEEN 1 AND 65535),
    total_score DECIMAL(8,2) NOT NULL, max_score DECIMAL(8,2) NOT NULL, items_evaluated INT NOT NULL,
    result_status VARCHAR(30) NOT NULL, UNIQUE(test_assignment_id, class_list_id, attempt_number),
    CHECK(total_score >= 0 AND max_score >= total_score));
CREATE TABLE scan_sessions (scan_session_id BIGINT AUTO_INCREMENT PRIMARY KEY, scan_uuid CHAR(36) NOT NULL UNIQUE,
    answer_sheet_version_id BIGINT REFERENCES answer_sheet_versions, omr_template_id BIGINT REFERENCES omr_templates,
    test_assignment_id BIGINT NOT NULL REFERENCES test_assignments, class_list_id BIGINT NOT NULL REFERENCES class_lists,
    expected_page_count INT NOT NULL, captured_page_count INT NOT NULL, scanned_by_user_id BIGINT NOT NULL REFERENCES users,
    template_version VARCHAR(50), scanner_version VARCHAR(50) NOT NULL, scan_status VARCHAR(30) NOT NULL,
    scanned_at TIMESTAMP NOT NULL, CHECK(expected_page_count >= 1 AND captured_page_count <= expected_page_count));
CREATE TABLE test_result_scans (test_result_scan_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    test_result_id BIGINT NOT NULL REFERENCES test_results, scan_session_id BIGINT NOT NULL UNIQUE REFERENCES scan_sessions,
    link_status VARCHAR(20) NOT NULL, decided_by_user_id BIGINT NOT NULL REFERENCES users, decision_reason VARCHAR(255),
    selected_result_id BIGINT GENERATED ALWAYS AS (CASE WHEN link_status = 'selected' THEN test_result_id ELSE NULL END),
    UNIQUE(selected_result_id), UNIQUE(test_result_id, scan_session_id));
CREATE TABLE syncs (sync_id BIGINT AUTO_INCREMENT PRIMARY KEY, sync_uuid CHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES users, test_assignment_id BIGINT REFERENCES test_assignments,
    direction VARCHAR(20) NOT NULL, sync_status VARCHAR(30) NOT NULL, payload_hash CHAR(64), request_item_count INT,
    completed_at TIMESTAMP);
CREATE TABLE sync_items (sync_item_id BIGINT AUTO_INCREMENT PRIMARY KEY, sync_id BIGINT NOT NULL REFERENCES syncs,
    result_uuid CHAR(36) NOT NULL, test_result_id BIGINT REFERENCES test_results, sync_action VARCHAR(20) NOT NULL,
    sync_status VARCHAR(20) NOT NULL, attempt_count INT DEFAULT 0, last_attempt_at TIMESTAMP, processed_at TIMESTAMP,
    synced_at TIMESTAMP, error_code VARCHAR(50), error_message VARCHAR(500), UNIQUE(sync_id, result_uuid));
CREATE TABLE scan_pages (scan_page_id BIGINT AUTO_INCREMENT PRIMARY KEY, scan_page_uuid CHAR(36) NOT NULL UNIQUE,
    scan_session_id BIGINT NOT NULL REFERENCES scan_sessions, answer_sheet_page_id BIGINT NOT NULL REFERENCES answer_sheet_pages,
    omr_template_id BIGINT NOT NULL REFERENCES omr_templates, page_number INT NOT NULL, capture_number INT NOT NULL,
    scanner_version VARCHAR(50) NOT NULL, qr_payload VARCHAR(1000) NOT NULL, qr_payload_hash CHAR(64) NOT NULL,
    image_hash CHAR(64) NOT NULL, page_status VARCHAR(30) NOT NULL, captured_at TIMESTAMP NOT NULL,
    current_page_number INT GENERATED ALWAYS AS (CASE WHEN page_status NOT IN ('rejected','superseded','failed')
        THEN page_number ELSE NULL END), UNIQUE(scan_session_id, page_number, capture_number),
    UNIQUE(scan_session_id, current_page_number));
CREATE TABLE answer_attachments (answer_attachment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    attachment_uuid CHAR(36) NOT NULL UNIQUE, scan_session_id BIGINT REFERENCES scan_sessions,
    scan_page_id BIGINT REFERENCES scan_pages, attachment_type VARCHAR(30) NOT NULL, storage_provider VARCHAR(40) NOT NULL,
    storage_key VARCHAR(500) NOT NULL, mime_type VARCHAR(100) NOT NULL, file_size_bytes BIGINT NOT NULL,
    content_hash CHAR(64) NOT NULL, captured_at TIMESTAMP, CHECK(scan_session_id IS NOT NULL OR scan_page_id IS NOT NULL));
-- H2 projection of V3_015; JSON validity is exercised by the actual MariaDB migration separately.
CREATE TABLE mobile_scan_uploads (
    scan_page_uuid CHAR(36) PRIMARY KEY, teacher_user_id BIGINT NOT NULL REFERENCES users, school_id VARCHAR(20) NOT NULL,
    sync_uuid CHAR(36) NOT NULL, request_hash CHAR(64) NOT NULL, request_json CLOB NOT NULL,
    attachment_uuid CHAR(36) NOT NULL UNIQUE, storage_key VARCHAR(500) NOT NULL, file_size_bytes BIGINT NOT NULL,
    content_hash CHAR(64) NOT NULL, width_pixels INT NOT NULL, height_pixels INT NOT NULL,
    upload_state VARCHAR(20) NOT NULL DEFAULT 'pending', backend_scan_page_id BIGINT UNIQUE REFERENCES scan_pages,
    receipt_page_status VARCHAR(30), attempt_count INT NOT NULL DEFAULT 0, last_error_code VARCHAR(50),
    last_attempt_at TIMESTAMP, committed_at TIMESTAMP, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CHECK ((upload_state = 'pending' AND backend_scan_page_id IS NULL AND receipt_page_status IS NULL AND committed_at IS NULL)
        OR (upload_state = 'committed' AND backend_scan_page_id IS NOT NULL AND receipt_page_status = 'captured' AND committed_at IS NOT NULL)));
