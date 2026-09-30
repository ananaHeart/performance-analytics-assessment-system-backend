-- H2 projection for teacher verification; actual schema-chain validation uses MariaDB separately.
ALTER TABLE scan_sessions ADD verified_by_user_id BIGINT REFERENCES users;
ALTER TABLE scan_sessions ADD verified_at TIMESTAMP;
CREATE TABLE student_answers(student_answer_id BIGINT AUTO_INCREMENT PRIMARY KEY,test_result_id BIGINT REFERENCES test_results,
    question_id BIGINT REFERENCES questions,verified_by_user_id BIGINT REFERENCES users,answer_uuid CHAR(36) UNIQUE,
    selected_question_option_id BIGINT REFERENCES question_options,response_text TEXT,capture_source VARCHAR(20),verified_at TIMESTAMP,
    answer_status VARCHAR(30),evaluation_status VARCHAR(30),is_correct BOOLEAN,points_earned DECIMAL(8,2) DEFAULT 0,
    teacher_feedback TEXT,finalized_at TIMESTAMP,score_version INT DEFAULT 1,UNIQUE(test_result_id,question_id));
CREATE TABLE mobile_verification_batches(operation_uuid CHAR(36) PRIMARY KEY,sync_id BIGINT UNIQUE REFERENCES syncs,
    request_hash CHAR(64),request_json CLOB,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE mobile_verification_items(sync_item_id BIGINT PRIMARY KEY REFERENCES sync_items,response_json CLOB);
CREATE TABLE scan_verifications(scan_verification_id BIGINT AUTO_INCREMENT PRIMARY KEY,verification_uuid CHAR(36) UNIQUE,
    scan_session_id BIGINT REFERENCES scan_sessions,scan_page_id BIGINT REFERENCES scan_pages,verified_by_user_id BIGINT REFERENCES users,
    verification_action VARCHAR(30),reason_code VARCHAR(50),reason_detail VARCHAR(4000),client_decided_at VARCHAR(40),
    mobile_operation_uuid CHAR(36) REFERENCES mobile_verification_batches,decided_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE mobile_objective_verifications(verification_uuid CHAR(36) PRIMARY KEY,student_answer_id BIGINT UNIQUE REFERENCES student_answers,
    omr_detection_id BIGINT REFERENCES omr_detections,operation_uuid CHAR(36) REFERENCES mobile_verification_batches,
    verified_by_user_id BIGINT REFERENCES users,comment VARCHAR(4000),client_decided_at VARCHAR(40),verified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE answer_verifications(answer_verification_id BIGINT AUTO_INCREMENT PRIMARY KEY,verification_uuid CHAR(36) UNIQUE);


