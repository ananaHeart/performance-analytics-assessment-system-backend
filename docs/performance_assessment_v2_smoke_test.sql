-- Performance Analytics Assessment System
-- Transactional V2 integration smoke test.
-- All sample operational records are rolled back at the end.

USE performance_assessment_v2_db;
START TRANSACTION;

-- School and addresses. These are separate address records by design.
INSERT INTO addresses (
    address_id, country_code, region_name, province_name,
    city_municipality_name, barangay_name, address_line,
    postal_code, address_source
) VALUES
    (1001, 'PH', 'Test Region', 'Test Province', 'Test City', 'School Barangay', 'School Street', '1000', 'manual'),
    (1002, 'PH', 'Test Region', 'Test Province', 'Test City', 'Teacher Barangay', 'Teacher Street', '1000', 'manual'),
    (1003, 'PH', 'Test Region', 'Test Province', 'Test City', 'Student Barangay', 'Student Street', '1000', 'sf1_import');

INSERT INTO school_profiles (
    school_id, address_id, school_name, contact_number, email
) VALUES (
    'V2-TEST-SCHOOL', 1001, 'V2 Integration Test School', '09000000001', 'school.v2.test@example.invalid'
);

-- The password values are non-login test hashes, never plain-text passwords.
INSERT INTO users (
    user_id, school_id, address_id, gender_id, major_id,
    educational_attainment_id, role_id, status_id,
    first_name, last_name, birth_date, teaching_start_date,
    email, contact_number, password_hash,
    email_verified_at, contact_verified_at
) VALUES
    (
        1001, 'V2-TEST-SCHOOL', 1002, 2, NULL,
        3, 1, 2,
        'Test', 'Principal', '1980-01-01', NULL,
        'principal.v2.test@example.invalid', '09000000002',
        '$2a$10$V2SmokeTestHashNotForAuthentication000000000000000000',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ),
    (
        1002, 'V2-TEST-SCHOOL', 1002, 1, 1,
        1, 2, 2,
        'Test', 'Teacher', '1990-01-01', '2015-06-01',
        'teacher.v2.test@example.invalid', '09000000003',
        '$2a$10$V2SmokeTestHashNotForAuthentication000000000000000001',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    );

INSERT INTO academic_years (
    academic_year_id, curriculum_id, year_name,
    start_date, end_date, status
) VALUES (
    1001, 1, '2026-2027', '2026-06-01', '2027-03-31', 'active'
);

INSERT INTO term_periods (
    term_period_id, academic_year_id, term_name, term_order, status
) VALUES (
    1001, 1001, 'First Quarter', 1, 'active'
);

INSERT INTO sections (section_id, grade_level_id, section_name)
VALUES (1001, 1, 'V2 Test Section');

INSERT INTO students (
    student_id, school_id, address_id, gender_id, student_lrn,
    first_name, last_name, birth_date, status
) VALUES (
    1001, 'V2-TEST-SCHOOL', 1003, 1, '990000001001',
    'Test', 'Learner', '2013-01-01', 'active'
);

INSERT INTO classes (class_id, academic_year_id, section_id, status)
VALUES (1001, 1001, 1001, 'active');

INSERT INTO class_assignments (
    class_assignment_id, class_id, user_id, subject_id,
    assignment_role, status
) VALUES (
    1001, 1001, 1002, 1, 'primary', 'active'
);

INSERT INTO class_lists (class_list_id, class_id, student_id)
VALUES (1001, 1001, 1001);

-- Curriculum hierarchy, skill, and intervention master.
INSERT INTO root_tags (
    root_tag_id, curriculum_id, root_tag_name, description, status
) VALUES (
    1001, 1, 'Grammar', 'V2 test root competency.', 'active'
);

INSERT INTO competency_tags (
    competency_id, root_tag_id, competency_name
) VALUES (
    1001, 1001, 'Identify the correct use of clauses.'
);

INSERT INTO skills (
    skill_id, competency_id, term_period_id, grade_level_id, subject_id
) VALUES (
    1001, 1001, 1001, 1, 1
);

INSERT INTO interventions (
    intervention_id, skill_id, intervention_type, description
) VALUES (
    1001, 1001, 'review',
    'Review clauses with the affected learners using guided examples and short practice exercises.'
);

-- Assessment structure and normalized answer keys/mappings.
INSERT INTO tests (
    test_id, class_assignment_id, term_period_id, test_name,
    test_type, test_date, instructions, total_items, status
) VALUES (
    1001, 1001, 1001, 'V2 OMR Smoke Test',
    'quiz', '2026-08-08', 'Shade one answer for each item.', 2, 'active'
);

INSERT INTO test_parts (
    test_part_id, test_id, part_order, part_name,
    part_type, number_of_items, points_per_item
) VALUES (
    1001, 1001, 1, 'Part 1', 'multiple_choice', 2, 1.00
);

INSERT INTO questions (
    question_id, test_part_id, item_number, question_text,
    option_a, option_b, option_c, option_d
) VALUES
    (1001, 1001, 1, 'V2 test question one.', 'A1', 'B1', 'C1', 'D1'),
    (1002, 1001, 2, 'V2 test question two.', 'A2', 'B2', 'C2', 'D2');

INSERT INTO answer_keys (answer_key_id, question_id, correct_option) VALUES
    (1001, 1001, 'A'),
    (1002, 1002, 'B');

INSERT INTO mappings (mapping_id, question_id, skill_id) VALUES
    (1001, 1001, 1001),
    (1002, 1002, 1001);

-- OMR capture followed by teacher verification.
INSERT INTO scan_sessions (
    scan_session_id, scan_uuid, scanned_by_user_id,
    verified_by_user_id, device_identifier,
    template_version, scanner_version, image_hash,
    scan_status, scanned_at, verified_at
) VALUES
    (
        1001, '00000000-0000-4000-8000-000000001001', 1002,
        1002, 'V2-TEST-DEVICE',
        'fixed-template-v1', 'opencv-test-v1',
        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        'verified', '2026-08-08 10:00:00', '2026-08-08 10:01:00'
    ),
    (
        1002, '00000000-0000-4000-8000-000000001002', 1002,
        1002, 'V2-TEST-DEVICE',
        'fixed-template-v1', 'opencv-test-v1',
        'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd',
        'verified', '2026-08-08 09:58:00', '2026-08-08 09:59:00'
    ),
    (
        1003, '00000000-0000-4000-8000-000000001003', 1002,
        1002, 'V2-TEST-DEVICE',
        'fixed-template-v1', 'opencv-test-v1',
        'eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee',
        'verified', '2026-08-08 10:02:00', '2026-08-08 10:03:00'
    );

INSERT INTO omr_detections (
    omr_detection_id, scan_session_id, question_id,
    detected_option, confidence_score, detection_status,
    verification_status, raw_mark, detected_at
) VALUES
    (1001, 1001, 1001, 'A', 0.9900, 'detected', 'confirmed', '{"A":0.99,"B":0.02,"C":0.01,"D":0.01}', '2026-08-08 10:00:10'),
    (1002, 1001, 1002, 'C', 0.9300, 'detected', 'confirmed', '{"A":0.03,"B":0.08,"C":0.93,"D":0.02}', '2026-08-08 10:00:11');

INSERT INTO test_results (
    test_result_id, result_uuid, test_id, class_list_id,
    attempt_number, total_score, max_score,
    items_evaluated, checked_at
) VALUES (
    1001, '00000000-0000-4000-8000-000000002001', 1001, 1001,
    1, 1.00, 2.00, 2, '2026-08-08 10:01:00'
);

INSERT INTO test_result_scans (
    test_result_scan_id, test_result_id, scan_session_id,
    link_status, decided_by_user_id, decision_reason, linked_at
) VALUES
    (1001, 1001, 1001, 'selected', 1002, NULL, '2026-08-08 10:01:00'),
    (1002, 1001, 1002, 'rejected', 1002, 'Earlier capture replaced during teacher verification.', '2026-08-08 10:01:00');

-- Deliberately try to select a second scan for the same result. The generated
-- nullable unique key must reject/ignore this row, leaving scan 1003 retained
-- in scan_sessions but not authoritative for the result.
INSERT IGNORE INTO test_result_scans (
    test_result_scan_id, test_result_id, scan_session_id,
    link_status, decided_by_user_id, decision_reason, linked_at
) VALUES (
    1003, 1001, 1003, 'selected', 1002,
    'Constraint test: a result must not have two selected scans.',
    '2026-08-08 10:03:00'
);

SELECT
    COUNT(*) AS duplicate_selected_rows
FROM test_result_scans
WHERE test_result_id = 1001
  AND scan_session_id = 1003;
-- Expected: duplicate_selected_rows = 0.

INSERT INTO student_answers (
    student_answer_id, test_result_id, question_id,
    verified_by_user_id, answer_uuid, capture_source,
    verified_at, selected_option, answer_status,
    is_correct, points_earned, correction_reason
) VALUES
    (
        1001, 1001, 1001, 1002,
        '00000000-0000-4000-8000-000000003001', 'omr',
        '2026-08-08 10:01:00', 'A', 'answered', TRUE, 1.00, NULL
    ),
    (
        1002, 1001, 1002, 1002,
        '00000000-0000-4000-8000-000000003002', 'omr',
        '2026-08-08 10:01:00', 'C', 'answered', FALSE, 0.00, NULL
    );

INSERT INTO intervention_results (
    intervention_result_id, test_result_id, intervention_id
) VALUES (1001, 1001, 1001);

-- Batch synchronization with one result item.
INSERT INTO syncs (
    sync_id, sync_uuid, user_id, test_id, device_identifier,
    direction, sync_status, payload_hash, started_at, completed_at
) VALUES (
    1001, '00000000-0000-4000-8000-000000004001', 1002, 1001,
    'V2-TEST-DEVICE', 'upload', 'success',
    'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
    '2026-08-08 10:02:00', '2026-08-08 10:02:02'
);

INSERT INTO sync_items (
    sync_item_id, sync_id, result_uuid, test_result_id,
    sync_action, sync_status, synced_at
) VALUES (
    1001, 1001, '00000000-0000-4000-8000-000000002001', 1001,
    'create', 'success', '2026-08-08 10:02:02'
);

-- Security and audit records.
INSERT INTO auth_sessions (
    auth_session_id, session_uuid, user_id, refresh_token_hash,
    device_identifier, ip_address, user_agent,
    issued_at, expires_at, last_used_at
) VALUES (
    1001, '00000000-0000-4000-8000-000000005001', 1002,
    'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
    'V2-TEST-DEVICE', '127.0.0.1', 'V2 smoke-test client',
    '2026-08-08 09:55:00', '2026-08-15 09:55:00', '2026-08-08 10:02:02'
);

INSERT INTO login_attempts (
    login_attempt_id, user_id, attempted_email, ip_address,
    device_identifier, user_agent, was_successful,
    failure_reason, attempted_at
) VALUES (
    1001, 1002, 'teacher.v2.test@example.invalid', '127.0.0.1',
    'V2-TEST-DEVICE', 'V2 smoke-test client', TRUE,
    NULL, '2026-08-08 09:55:00'
);

INSERT INTO audit_logs (
    audit_log_id, audit_uuid, user_id, action,
    entity_type, entity_id, outcome, ip_address,
    device_identifier, user_agent, details, created_at
) VALUES (
    1001, '00000000-0000-4000-8000-000000006001', 1002,
    'VERIFY_OMR', 'test_results', '1001', 'success', '127.0.0.1',
    'V2-TEST-DEVICE', 'V2 smoke-test client',
    '{"result_uuid":"00000000-0000-4000-8000-000000002001","answers":2}',
    '2026-08-08 10:01:00'
);

-- Expected: one row, score 1.00/2.00, two answers, one correct,
-- one intervention, one successful sync item, and complete security/audit data.
SELECT
    tr.result_uuid,
    tr.total_score,
    tr.max_score,
    tr.items_evaluated,
    COUNT(DISTINCT sa.student_answer_id) AS answer_count,
    COUNT(DISTINCT CASE WHEN sa.is_correct THEN sa.student_answer_id END) AS correct_count,
    COUNT(DISTINCT ir.intervention_result_id) AS intervention_count,
    COUNT(DISTINCT si.sync_item_id) AS sync_item_count,
    COUNT(DISTINCT trs.test_result_scan_id) AS retained_scan_count,
    COUNT(DISTINCT CASE WHEN trs.link_status = 'selected' THEN trs.test_result_scan_id END) AS selected_scan_count,
    COUNT(DISTINCT od.omr_detection_id) AS selected_scan_detection_count
FROM test_results tr
JOIN student_answers sa ON sa.test_result_id = tr.test_result_id
LEFT JOIN intervention_results ir ON ir.test_result_id = tr.test_result_id
LEFT JOIN sync_items si ON si.test_result_id = tr.test_result_id
LEFT JOIN test_result_scans trs ON trs.test_result_id = tr.test_result_id
LEFT JOIN omr_detections od
  ON od.scan_session_id = trs.scan_session_id
 AND trs.link_status = 'selected'
WHERE tr.test_result_id = 1001
GROUP BY
    tr.result_uuid,
    tr.total_score,
    tr.max_score,
    tr.items_evaluated;

SELECT
    (SELECT COUNT(*) FROM auth_sessions WHERE auth_session_id = 1001) AS auth_session_count,
    (SELECT COUNT(*) FROM login_attempts WHERE login_attempt_id = 1001) AS login_attempt_count,
    (SELECT COUNT(*) FROM audit_logs WHERE audit_log_id = 1001) AS audit_log_count;

ROLLBACK;

-- Expected after rollback: no operational smoke-test rows remain.
SELECT
    (SELECT COUNT(*) FROM school_profiles WHERE school_id = 'V2-TEST-SCHOOL') AS remaining_test_schools,
    (SELECT COUNT(*) FROM test_results WHERE test_result_id = 1001) AS remaining_test_results,
    (SELECT COUNT(*) FROM test_result_scans WHERE test_result_id = 1001) AS remaining_test_result_scans,
    (SELECT COUNT(*) FROM scan_sessions WHERE scan_session_id IN (1001, 1002, 1003)) AS remaining_scan_sessions,
    (SELECT COUNT(*) FROM syncs WHERE sync_id = 1001) AS remaining_sync_batches;
