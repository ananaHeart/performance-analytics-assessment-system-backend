-- V3_009 DRAFT: Reproducible expected-failure fixtures.
--
-- DISPOSABLE DATABASE ONLY. DO NOT RUN AGAINST LOCAL V3, V2, TIDB,
-- PRODUCTION, OR MOBILE SQLITE.
--
-- Run after the complete canonical -> V3_009 chain. The script creates valid
-- parent fixtures inside one transaction, executes invalid cases, verifies each
-- SQLSTATE, rolls back all fixture data, and fails if any case is unexpectedly
-- accepted. Cross-row source type/page/ownership rules are exercised through a
-- disposable validator matching the future Java service contract because a
-- portable CHECK constraint cannot inspect the referenced attachment row.

USE performance_assessment_v3_db;

DELIMITER $$

DROP PROCEDURE IF EXISTS validate_v3_attachment_lineage_fixture$$
DROP PROCEDURE IF EXISTS run_v3_009_negative_fixtures$$
DROP PROCEDURE IF EXISTS finish_v3_009_negative_fixtures$$
DROP TEMPORARY TABLE IF EXISTS v3_009_negative_results$$

CREATE TEMPORARY TABLE v3_009_negative_results (
    case_name VARCHAR(100) NOT NULL,
    expected_sqlstate CHAR(5) NOT NULL,
    actual_sqlstate CHAR(5) NULL,
    passed BOOLEAN NOT NULL,
    error_message VARCHAR(1000) NULL
) ENGINE=MEMORY$$

CREATE PROCEDURE validate_v3_attachment_lineage_fixture(
    IN candidate_attachment_type VARCHAR(40),
    IN candidate_source_id BIGINT UNSIGNED,
    IN candidate_scan_session_id BIGINT UNSIGNED,
    IN candidate_scan_page_id BIGINT UNSIGNED,
    IN candidate_student_answer_id BIGINT UNSIGNED,
    IN candidate_answer_sheet_region_id BIGINT UNSIGNED
)
BEGIN
    DECLARE source_type VARCHAR(40);
    DECLARE source_scan_session_id BIGINT UNSIGNED;
    DECLARE source_scan_page_id BIGINT UNSIGNED;
    DECLARE source_student_answer_id BIGINT UNSIGNED;
    DECLARE source_answer_sheet_region_id BIGINT UNSIGNED;
    DECLARE source_found BOOLEAN DEFAULT TRUE;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET source_found = FALSE;

    IF candidate_attachment_type <> 'normalized_page'
       AND candidate_source_id IS NOT NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Only normalized_page evidence may have a source.';
    END IF;

    IF candidate_attachment_type = 'normalized_page' THEN
        IF candidate_source_id IS NULL THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'normalized_page requires original_page evidence.';
        END IF;

        SELECT
            attachment_type,
            scan_session_id,
            scan_page_id,
            student_answer_id,
            answer_sheet_region_id
        INTO
            source_type,
            source_scan_session_id,
            source_scan_page_id,
            source_student_answer_id,
            source_answer_sheet_region_id
        FROM answer_attachments
        WHERE answer_attachment_id = candidate_source_id;

        IF source_found = FALSE THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Source attachment does not exist.';
        END IF;
        IF source_type <> 'original_page' THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'normalized_page source must be original_page.';
        END IF;
        IF NOT (source_scan_page_id <=> candidate_scan_page_id) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Source and normalized evidence must share a scan page.';
        END IF;
        IF NOT (source_scan_session_id <=> candidate_scan_session_id)
           OR NOT (source_student_answer_id <=> candidate_student_answer_id)
           OR NOT (source_answer_sheet_region_id <=> candidate_answer_sheet_region_id) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Source and normalized evidence must share ownership context.';
        END IF;
    END IF;
END$$

CREATE PROCEDURE run_v3_009_negative_fixtures()
BEGIN
    -- 1. Invalid expected-response count.
    BEGIN
        DECLARE actual_state CHAR(5) DEFAULT NULL;
        DECLARE actual_message VARCHAR(1000) DEFAULT NULL;
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
        BEGIN
            GET DIAGNOSTICS CONDITION 1
                actual_state = RETURNED_SQLSTATE,
                actual_message = MESSAGE_TEXT;
        END;

        INSERT INTO questions (
            question_id, question_uuid, test_part_id, question_type_id,
            item_number, question_text, maximum_points,
            expected_response_count, response_region_size
        ) VALUES (
            9900002, '99000000-0000-0000-0000-000000000002',
            9900001, @enumeration_type_id, 2, 'Invalid count fixture', 1.00,
            0, 'short'
        );

        INSERT INTO v3_009_negative_results VALUES (
            'question_expected_response_count_zero', '23000', actual_state,
            actual_state = '23000', actual_message
        );
    END;

    -- 2. Enumeration has fewer response lines than expected responses.
    BEGIN
        DECLARE actual_state CHAR(5) DEFAULT NULL;
        DECLARE actual_message VARCHAR(1000) DEFAULT NULL;
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
        BEGIN
            GET DIAGNOSTICS CONDITION 1
                actual_state = RETURNED_SQLSTATE,
                actual_message = MESSAGE_TEXT;
        END;

        INSERT INTO answer_sheet_regions (
            answer_sheet_region_id, region_uuid, answer_sheet_version_id,
            answer_sheet_page_id, omr_template_region_id, question_id,
            test_part_id, question_type_id, global_item_number,
            part_item_number, region_sequence, region_type,
            response_region_size, expected_response_count_snapshot,
            response_line_count, geometry_snapshot, geometry_hash
        ) VALUES (
            9900002, '99000000-0000-0000-0000-000000000102',
            9900001, 9900001, @template_region_id, 9900001,
            9900001, @enumeration_type_id, 1, 1, 1,
            'written_response', 'short', 3, 2, '{}', REPEAT('a', 64)
        );

        INSERT INTO v3_009_negative_results VALUES (
            'enumeration_lines_below_expected_count', '23000', actual_state,
            actual_state = '23000', actual_message
        );
    END;

    -- 3. A normalized page cannot omit its original source.
    BEGIN
        DECLARE actual_state CHAR(5) DEFAULT NULL;
        DECLARE actual_message VARCHAR(1000) DEFAULT NULL;
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
        BEGIN
            GET DIAGNOSTICS CONDITION 1
                actual_state = RETURNED_SQLSTATE,
                actual_message = MESSAGE_TEXT;
        END;

        INSERT INTO answer_attachments (
            attachment_uuid, scan_session_id, scan_page_id, attachment_type,
            storage_provider, storage_key, mime_type, file_size_bytes, content_hash
        ) VALUES (
            '99000000-0000-0000-0000-000000000203', 9900001, 9900001,
            'normalized_page', 'fixture', 'invalid/no-source', 'image/jpeg',
            100, REPEAT('3', 64)
        );

        INSERT INTO v3_009_negative_results VALUES (
            'normalized_page_without_source', '23000', actual_state,
            actual_state = '23000', actual_message
        );
    END;

    -- 4. A hold requires reason, actor, and time.
    BEGIN
        DECLARE actual_state CHAR(5) DEFAULT NULL;
        DECLARE actual_message VARCHAR(1000) DEFAULT NULL;
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
        BEGIN
            GET DIAGNOSTICS CONDITION 1
                actual_state = RETURNED_SQLSTATE,
                actual_message = MESSAGE_TEXT;
        END;

        INSERT INTO answer_attachments (
            attachment_uuid, scan_session_id, scan_page_id, attachment_type,
            storage_provider, storage_key, mime_type, file_size_bytes, content_hash,
            retention_hold
        ) VALUES (
            '99000000-0000-0000-0000-000000000204', 9900001, 9900001,
            'original_page', 'fixture', 'invalid/hold', 'image/jpeg',
            100, REPEAT('4', 64), TRUE
        );

        INSERT INTO v3_009_negative_results VALUES (
            'retention_hold_without_audit_fields', '23000', actual_state,
            actual_state = '23000', actual_message
        );
    END;

    -- 5. Purged evidence requires a complete purge audit state.
    BEGIN
        DECLARE actual_state CHAR(5) DEFAULT NULL;
        DECLARE actual_message VARCHAR(1000) DEFAULT NULL;
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
        BEGIN
            GET DIAGNOSTICS CONDITION 1
                actual_state = RETURNED_SQLSTATE,
                actual_message = MESSAGE_TEXT;
        END;

        INSERT INTO answer_attachments (
            attachment_uuid, scan_session_id, scan_page_id, attachment_type,
            storage_provider, storage_key, mime_type, file_size_bytes, content_hash,
            purge_status
        ) VALUES (
            '99000000-0000-0000-0000-000000000205', 9900001, 9900001,
            'original_page', 'fixture', 'invalid/purged', 'image/jpeg',
            100, REPEAT('5', 64), 'purged'
        );

        INSERT INTO v3_009_negative_results VALUES (
            'purged_evidence_without_audit_fields', '23000', actual_state,
            actual_state = '23000', actual_message
        );
    END;

    -- 6. Generated-page QR payload is limited to 256 UTF-8 bytes.
    BEGIN
        DECLARE actual_state CHAR(5) DEFAULT NULL;
        DECLARE actual_message VARCHAR(1000) DEFAULT NULL;
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
        BEGIN
            GET DIAGNOSTICS CONDITION 1
                actual_state = RETURNED_SQLSTATE,
                actual_message = MESSAGE_TEXT;
        END;

        INSERT INTO answer_sheet_pages (
            page_uuid, answer_sheet_version_id, omr_template_id,
            page_number, total_pages, qr_payload, qr_payload_hash,
            page_geometry_hash
        ) VALUES (
            '99000000-0000-0000-0000-000000000306', 9900001,
            @omr_template_id, 3, 3, REPEAT('x', 257),
            SHA2(REPEAT('x', 257), 256), REPEAT('6', 64)
        );

        INSERT INTO v3_009_negative_results VALUES (
            'generated_qr_over_256_bytes', '23000', actual_state,
            actual_state = '23000', actual_message
        );
    END;

    -- 7. Generated-page QR hash must match the exact payload.
    BEGIN
        DECLARE actual_state CHAR(5) DEFAULT NULL;
        DECLARE actual_message VARCHAR(1000) DEFAULT NULL;
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
        BEGIN
            GET DIAGNOSTICS CONDITION 1
                actual_state = RETURNED_SQLSTATE,
                actual_message = MESSAGE_TEXT;
        END;

        INSERT INTO answer_sheet_pages (
            page_uuid, answer_sheet_version_id, omr_template_id,
            page_number, total_pages, qr_payload, qr_payload_hash,
            page_geometry_hash
        ) VALUES (
            '99000000-0000-0000-0000-000000000307', 9900001,
            @omr_template_id, 4, 4, '{"fixture":7}',
            REPEAT('7', 64), REPEAT('7', 64)
        );

        INSERT INTO v3_009_negative_results VALUES (
            'generated_qr_wrong_digest', '23000', actual_state,
            actual_state = '23000', actual_message
        );
    END;

    -- 8. Captured-page QR payload has the same 256-byte ceiling.
    BEGIN
        DECLARE actual_state CHAR(5) DEFAULT NULL;
        DECLARE actual_message VARCHAR(1000) DEFAULT NULL;
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
        BEGIN
            GET DIAGNOSTICS CONDITION 1
                actual_state = RETURNED_SQLSTATE,
                actual_message = MESSAGE_TEXT;
        END;

        INSERT INTO scan_pages (
            scan_page_uuid, scan_session_id, answer_sheet_page_id,
            omr_template_id, page_number, capture_number, scanner_version,
            qr_payload, qr_payload_hash, image_hash, page_status
        ) VALUES (
            '99000000-0000-0000-0000-000000000408', 9900001, 9900001,
            @omr_template_id, 1, 2, 'fixture', REPEAT('y', 257),
            SHA2(REPEAT('y', 257), 256), REPEAT('8', 64), 'rejected'
        );

        INSERT INTO v3_009_negative_results VALUES (
            'captured_qr_over_256_bytes', '23000', actual_state,
            actual_state = '23000', actual_message
        );
    END;

    -- 9. Non-normalized evidence must never carry a source ID.
    BEGIN
        DECLARE actual_state CHAR(5) DEFAULT NULL;
        DECLARE actual_message VARCHAR(1000) DEFAULT NULL;
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
        BEGIN
            GET DIAGNOSTICS CONDITION 1
                actual_state = RETURNED_SQLSTATE,
                actual_message = MESSAGE_TEXT;
        END;

        INSERT INTO answer_attachments (
            attachment_uuid, scan_session_id, scan_page_id,
            source_answer_attachment_id, attachment_type, storage_provider,
            storage_key, mime_type, file_size_bytes, content_hash
        ) VALUES (
            '99000000-0000-0000-0000-000000000209', 9900001, 9900001,
            @original_attachment_id, 'answer_crop', 'fixture',
            'invalid/non-normalized-source', 'image/jpeg', 100, REPEAT('9', 64)
        );

        INSERT INTO v3_009_negative_results VALUES (
            'source_on_non_normalized_evidence', '23000', actual_state,
            actual_state = '23000', actual_message
        );
    END;

    -- 10. Future service rule: normalized source must be original_page.
    BEGIN
        DECLARE actual_state CHAR(5) DEFAULT NULL;
        DECLARE actual_message VARCHAR(1000) DEFAULT NULL;
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
        BEGIN
            GET DIAGNOSTICS CONDITION 1
                actual_state = RETURNED_SQLSTATE,
                actual_message = MESSAGE_TEXT;
        END;

        CALL validate_v3_attachment_lineage_fixture(
            'normalized_page', @answer_crop_attachment_id,
            9900001, 9900001, NULL, NULL
        );

        INSERT INTO v3_009_negative_results VALUES (
            'normalized_source_is_not_original_page', '45000', actual_state,
            actual_state = '45000', actual_message
        );
    END;

    -- 11. Future service rule: source must share page and ownership context.
    BEGIN
        DECLARE actual_state CHAR(5) DEFAULT NULL;
        DECLARE actual_message VARCHAR(1000) DEFAULT NULL;
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
        BEGIN
            GET DIAGNOSTICS CONDITION 1
                actual_state = RETURNED_SQLSTATE,
                actual_message = MESSAGE_TEXT;
        END;

        CALL validate_v3_attachment_lineage_fixture(
            'normalized_page', @original_attachment_id,
            9900001, 9900002, NULL, NULL
        );

        INSERT INTO v3_009_negative_results VALUES (
            'normalized_source_crosses_scan_page', '45000', actual_state,
            actual_state = '45000', actual_message
        );
    END;
END$$

CREATE PROCEDURE finish_v3_009_negative_fixtures()
BEGIN
    IF EXISTS (SELECT 1 FROM v3_009_negative_results WHERE passed = FALSE) THEN
        SELECT * FROM v3_009_negative_results ORDER BY case_name;
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'One or more V3_009 negative fixtures were unexpectedly accepted.';
    ELSE
        SELECT
            'PASS' AS validation_status,
            COUNT(*) AS expected_failure_count,
            SUM(expected_sqlstate = '23000') AS database_constraint_failures,
            SUM(expected_sqlstate = '45000') AS service_contract_failures,
            'performance_assessment_v3_db' AS validated_database
        FROM v3_009_negative_results;
        SELECT
            case_name,
            expected_sqlstate,
            actual_sqlstate,
            passed
        FROM v3_009_negative_results
        ORDER BY case_name;
    END IF;
END$$

DELIMITER ;

SET @teacher_role_id = (
    SELECT role_id FROM roles WHERE role_name = 'teacher' LIMIT 1
);
SET @active_status_id = (
    SELECT status_id FROM statuses WHERE status_name = 'active' LIMIT 1
);
SET @gender_id = (
    SELECT gender_id FROM genders ORDER BY gender_id LIMIT 1
);
SET @curriculum_id = (
    SELECT curriculum_id FROM curriculums ORDER BY curriculum_id LIMIT 1
);
SET @grade_level_id = (
    SELECT grade_level_id FROM grade_levels ORDER BY grade_level_id LIMIT 1
);
SET @subject_id = (
    SELECT subject_id FROM subjects ORDER BY subject_id LIMIT 1
);
SET @enumeration_type_id = (
    SELECT question_type_id
    FROM question_types
    WHERE question_type_code = 'enumeration'
    LIMIT 1
);
SET @paper_size_id = (
    SELECT paper_size_id FROM paper_sizes WHERE paper_size_code = 'A4' LIMIT 1
);
SET @omr_template_id = (
    SELECT omr_template_id
    FROM omr_templates
    WHERE template_code = 'OMR-A4-10-MC-CTX-V2'
    LIMIT 1
);
SET @template_region_id = (
    SELECT omr_template_region_id
    FROM omr_template_regions
    WHERE omr_template_id = @omr_template_id
      AND region_type = 'objective_bubbles'
    ORDER BY region_order
    LIMIT 1
);

START TRANSACTION;

INSERT INTO addresses (
    address_id, region_name, province_name, city_municipality_name,
    barangay_name, address_line, address_source
) VALUES (
    9900001, 'Fixture Region', 'Fixture Province', 'Fixture City',
    'Fixture Barangay', 'Disposable test only', 'manual'
);

INSERT INTO school_profiles (
    school_id, address_id, school_name, email
) VALUES (
    'V3NEG001', 9900001, 'V3 Negative Fixture School', 'v3-negative@example.invalid'
);

INSERT INTO users (
    user_id, school_id, address_id, gender_id, role_id, status_id,
    first_name, last_name, email, contact_number, password_hash,
    email_verified_at
) VALUES (
    9900001, 'V3NEG001', 9900001, @gender_id, @teacher_role_id,
    @active_status_id, 'Fixture', 'Teacher', 'v3-negative-teacher@example.invalid',
    '+639999900001', 'not-a-login-hash', CURRENT_TIMESTAMP
);

INSERT INTO academic_years (
    academic_year_id, curriculum_id, year_name, start_date, end_date, status
) VALUES (
    9900001, @curriculum_id, '2030-2031', '2030-06-01', '2031-03-31', 'planned'
);

INSERT INTO sections (section_id, grade_level_id, section_name)
VALUES (9900001, @grade_level_id, 'V3 Negative Fixture');

INSERT INTO classes (class_id, academic_year_id, section_id)
VALUES (9900001, 9900001, 9900001);

INSERT INTO class_assignments (
    class_assignment_id, class_id, user_id, subject_id
) VALUES (
    9900001, 9900001, 9900001, @subject_id
);

INSERT INTO students (
    student_id, school_id, address_id, gender_id, student_lrn,
    first_name, last_name
) VALUES (
    9900001, 'V3NEG001', 9900001, @gender_id, '999990000001',
    'Fixture', 'Learner'
);

INSERT INTO class_lists (
    class_list_id, membership_uuid, class_id, student_id,
    academic_year_id, enrollment_source
) VALUES (
    9900001, '99000000-0000-0000-0000-000000000501',
    9900001, 9900001, 9900001, 'manual'
);

INSERT INTO term_periods (
    term_period_id, academic_year_id, term_name, term_order,
    start_at, end_at, status
) VALUES (
    9900001, 9900001, 'Fixture Term', 1,
    '2030-06-01 00:00:00', '2030-08-31 23:59:59', 'planned'
);

INSERT INTO tests (
    test_id, test_uuid, school_id, created_by_user_id, term_period_id,
    test_name, test_type, total_items, status
) VALUES (
    9900001, '99000000-0000-0000-0000-000000000601',
    'V3NEG001', 9900001, 9900001, 'V3 Negative Fixture',
    'quiz', 5, 'active'
);

INSERT INTO test_parts (
    test_part_id, test_id, part_order, part_name, question_type_id,
    number_of_items, points_per_item
) VALUES (
    9900001, 9900001, 1, 'Fixture Enumeration',
    @enumeration_type_id, 5, 1.00
);

INSERT INTO questions (
    question_id, question_uuid, test_part_id, question_type_id,
    item_number, question_text, maximum_points,
    expected_response_count, response_region_size
) VALUES (
    9900001, '99000000-0000-0000-0000-000000000001',
    9900001, @enumeration_type_id, 1, 'Valid fixture question', 1.00,
    3, 'short'
);

INSERT INTO test_assignments (
    test_assignment_id, assignment_uuid, test_id, class_assignment_id,
    assigned_by_user_id, open_at, close_at, assignment_status
) VALUES (
    9900001, '99000000-0000-0000-0000-000000000701',
    9900001, 9900001, 9900001,
    '2030-06-01 00:00:00', '2030-06-02 00:00:00', 'open'
);

INSERT INTO answer_sheet_versions (
    answer_sheet_version_id, answer_sheet_uuid, test_assignment_id,
    paper_size_id, generation_number, test_version_number,
    total_questions, total_pages, manifest_version, manifest_hash,
    generation_status, generated_by_user_id, generated_at
) VALUES (
    9900001, '99000000-0000-0000-0000-000000000801',
    9900001, @paper_size_id, 1, 1, 5, 2, 1,
    REPEAT('1', 64), 'ready', 9900001, CURRENT_TIMESTAMP
);

SET @page_one_payload = '{"fixture":"page-one"}';
SET @page_two_payload = '{"fixture":"page-two"}';

INSERT INTO answer_sheet_pages (
    answer_sheet_page_id, page_uuid, answer_sheet_version_id,
    omr_template_id, page_number, total_pages, qr_payload,
    qr_payload_hash, page_geometry_hash
) VALUES
    (
        9900001, '99000000-0000-0000-0000-000000000901',
        9900001, @omr_template_id, 1, 2, @page_one_payload,
        SHA2(@page_one_payload, 256), REPEAT('a', 64)
    ),
    (
        9900002, '99000000-0000-0000-0000-000000000902',
        9900001, @omr_template_id, 2, 2, @page_two_payload,
        SHA2(@page_two_payload, 256), REPEAT('b', 64)
    );

INSERT INTO scan_sessions (
    scan_session_id, scan_uuid, answer_sheet_version_id, omr_template_id,
    test_assignment_id, class_list_id, expected_page_count,
    captured_page_count, scanned_by_user_id, scanner_version, scan_status
) VALUES (
    9900001, '99000000-0000-0000-0000-000000001001',
    9900001, @omr_template_id, 9900001, 9900001, 2, 2,
    9900001, 'fixture', 'captured'
);

INSERT INTO scan_pages (
    scan_page_id, scan_page_uuid, scan_session_id, answer_sheet_page_id,
    omr_template_id, page_number, capture_number, scanner_version,
    qr_payload, qr_payload_hash, image_hash
) VALUES
    (
        9900001, '99000000-0000-0000-0000-000000001101',
        9900001, 9900001, @omr_template_id, 1, 1, 'fixture',
        @page_one_payload, SHA2(@page_one_payload, 256), REPEAT('c', 64)
    ),
    (
        9900002, '99000000-0000-0000-0000-000000001102',
        9900001, 9900002, @omr_template_id, 2, 1, 'fixture',
        @page_two_payload, SHA2(@page_two_payload, 256), REPEAT('d', 64)
    );

INSERT INTO answer_attachments (
    attachment_uuid, scan_session_id, scan_page_id, attachment_type,
    storage_provider, storage_key, mime_type, file_size_bytes, content_hash
) VALUES (
    '99000000-0000-0000-0000-000000001201', 9900001, 9900001,
    'original_page', 'fixture', 'valid/original', 'image/jpeg',
    100, REPEAT('e', 64)
);
SET @original_attachment_id = LAST_INSERT_ID();

INSERT INTO answer_attachments (
    attachment_uuid, scan_session_id, scan_page_id, attachment_type,
    storage_provider, storage_key, mime_type, file_size_bytes, content_hash
) VALUES (
    '99000000-0000-0000-0000-000000001202', 9900001, 9900001,
    'answer_crop', 'fixture', 'valid/crop', 'image/jpeg',
    100, REPEAT('f', 64)
);
SET @answer_crop_attachment_id = LAST_INSERT_ID();

CALL run_v3_009_negative_fixtures();

ROLLBACK;

CALL finish_v3_009_negative_fixtures();

SELECT
    COUNT(*) AS persisted_fixture_rows
FROM answer_attachments
WHERE attachment_uuid LIKE '99000000-0000-0000-0000-0000000012%';

DROP PROCEDURE validate_v3_attachment_lineage_fixture;
DROP PROCEDURE run_v3_009_negative_fixtures;
DROP PROCEDURE finish_v3_009_negative_fixtures;
DROP TEMPORARY TABLE v3_009_negative_results;
