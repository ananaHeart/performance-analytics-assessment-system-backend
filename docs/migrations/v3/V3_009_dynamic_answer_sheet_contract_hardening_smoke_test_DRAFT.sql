-- V3_009 DRAFT: Non-destructive smoke test for contract hardening.
--
-- Approved for disposable and isolated local V3 validation. Do not run against
-- V2, TiDB, production, or Mobile SQLite. Run after:
--   canonical V3 -> V3_004 -> V3_005 -> V3_006 -> V3_007 -> V3_008
--   -> V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql

USE performance_assessment_v3_db;

DELIMITER $$

DROP PROCEDURE IF EXISTS assert_v3_sheet_hardening_condition$$
DROP PROCEDURE IF EXISTS finish_v3_sheet_hardening_validation$$
DROP TEMPORARY TABLE IF EXISTS v3_sheet_hardening_validation_failures$$

CREATE TEMPORARY TABLE v3_sheet_hardening_validation_failures (
    failure_message VARCHAR(255) NOT NULL
)$$

CREATE PROCEDURE assert_v3_sheet_hardening_condition(
    IN condition_result BOOLEAN,
    IN failure_message VARCHAR(255)
)
BEGIN
    IF condition_result IS NULL OR condition_result = FALSE THEN
        INSERT INTO v3_sheet_hardening_validation_failures VALUES (failure_message);
    END IF;
END$$

CREATE PROCEDURE finish_v3_sheet_hardening_validation()
BEGIN
    IF EXISTS (SELECT 1 FROM v3_sheet_hardening_validation_failures) THEN
        SELECT failure_message FROM v3_sheet_hardening_validation_failures;
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V3 dynamic answer-sheet hardening validation failed.';
    ELSE
        SELECT
            'PASS' AS validation_status,
            66 AS central_table_count,
            157 AS foreign_key_count,
            80 AS check_constraint_count,
            96 AS unique_constraint_count,
            'performance_assessment_v3_db' AS validated_database;
    END IF;
END$$

DELIMITER ;

CALL assert_v3_sheet_hardening_condition(
    (SELECT COUNT(*) = 66
     FROM information_schema.tables
     WHERE table_schema = 'performance_assessment_v3_db'
       AND table_type = 'BASE TABLE'),
    'Hardened V3 must retain exactly 66 central tables.'
);

CALL assert_v3_sheet_hardening_condition(
    (SELECT COUNT(*) = 3
     FROM information_schema.columns
     WHERE table_schema = 'performance_assessment_v3_db'
       AND (
           (table_name = 'questions'
               AND column_name = 'expected_response_count'
               AND data_type = 'smallint')
           OR (table_name = 'answer_sheet_regions'
               AND column_name IN (
                   'expected_response_count_snapshot',
                   'response_line_count'
               )
               AND data_type = 'smallint')
       )),
    'Written-response count columns are missing or have the wrong type.'
);

CALL assert_v3_sheet_hardening_condition(
    (SELECT COUNT(*) = 13
     FROM information_schema.columns
     WHERE table_schema = 'performance_assessment_v3_db'
       AND table_name = 'answer_attachments'
       AND column_name IN (
           'source_answer_attachment_id',
           'retention_policy_code',
           'retention_until',
           'retention_hold',
           'retention_hold_reason',
           'retention_hold_set_by_user_id',
           'retention_hold_set_at',
           'purge_status',
           'last_purge_attempt_at',
           'purged_at',
           'purged_by_user_id',
           'purge_reason',
           'last_purge_error'
       )),
    'Evidence lineage or retention/purge columns are incomplete.'
);

CALL assert_v3_sheet_hardening_condition(
    (SELECT column_type LIKE '%original_page%'
        AND column_type LIKE '%normalized_page%'
     FROM information_schema.columns
     WHERE table_schema = 'performance_assessment_v3_db'
       AND table_name = 'answer_attachments'
       AND column_name = 'attachment_type'),
    'Original and normalized page evidence types are not distinct.'
);

CALL assert_v3_sheet_hardening_condition(
    (SELECT COUNT(*) = 3
     FROM information_schema.table_constraints
     WHERE table_schema = 'performance_assessment_v3_db'
       AND table_name = 'answer_attachments'
       AND constraint_type = 'FOREIGN KEY'
       AND constraint_name IN (
           'fk_answer_attachments_source',
           'fk_answer_attachments_hold_set_by_user',
           'fk_answer_attachments_purged_by_user'
       )),
    'Evidence source, hold actor, or purge actor foreign key is missing.'
);

CALL assert_v3_sheet_hardening_condition(
    (SELECT COUNT(*) = 12
     FROM information_schema.check_constraints
     WHERE constraint_schema = 'performance_assessment_v3_db'
       AND constraint_name IN (
           'chk_questions_expected_response_count',
           'chk_answer_sheet_regions_expected_count',
           'chk_answer_sheet_regions_line_count',
           'chk_answer_sheet_regions_response_shape',
           'chk_answer_attachments_normalized_source',
           'chk_answer_attachments_hold',
           'chk_answer_attachments_purge_state',
           'chk_answer_attachments_held_not_purged',
           'chk_answer_sheet_pages_qr_payload',
           'chk_answer_sheet_pages_qr_hash',
           'chk_scan_pages_qr_payload',
           'chk_scan_pages_qr_hash'
       )),
    'One or more V3_009 hardening checks are missing.'
);

CALL assert_v3_sheet_hardening_condition(
    (SELECT COUNT(*) = 0
     FROM questions
     WHERE expected_response_count IS NOT NULL
       AND expected_response_count < 1),
    'A question contains an invalid expected-response count.'
);

CALL assert_v3_sheet_hardening_condition(
    (SELECT COUNT(*) = 0
     FROM answer_sheet_regions
     WHERE expected_response_count_snapshot IS NOT NULL
           AND expected_response_count_snapshot < 1
        OR response_line_count IS NOT NULL
           AND response_line_count < 1
        OR region_type = 'objective_bubbles'
           AND (
               response_region_size <> 'none'
               OR expected_response_count_snapshot IS NOT NULL
               OR response_line_count IS NOT NULL
           )
        OR region_type = 'written_response'
           AND response_region_size = 'none'
        OR expected_response_count_snapshot IS NOT NULL
           AND response_line_count IS NOT NULL
           AND response_line_count < expected_response_count_snapshot),
    'A generated answer-sheet region violates its immutable response shape.'
);

CALL assert_v3_sheet_hardening_condition(
    (SELECT COUNT(*) = 0
     FROM answer_attachments
     WHERE source_answer_attachment_id = answer_attachment_id
        OR attachment_type = 'normalized_page'
           AND source_answer_attachment_id IS NULL
        OR attachment_type <> 'normalized_page'
           AND source_answer_attachment_id IS NOT NULL
        OR retention_hold = TRUE
           AND (
               retention_hold_reason IS NULL
               OR retention_hold_set_by_user_id IS NULL
               OR retention_hold_set_at IS NULL
           )
        OR retention_hold = FALSE
           AND (
               retention_hold_reason IS NOT NULL
               OR retention_hold_set_by_user_id IS NOT NULL
               OR retention_hold_set_at IS NOT NULL
           )
        OR retention_hold = TRUE AND purge_status = 'purged'),
    'An evidence attachment violates source lineage or retention-hold state.'
);

CALL assert_v3_sheet_hardening_condition(
    (SELECT COUNT(*) = 0
     FROM answer_attachments
     WHERE purge_status = 'retained'
           AND (
               purged_at IS NOT NULL
               OR purged_by_user_id IS NOT NULL
               OR purge_reason IS NOT NULL
               OR last_purge_error IS NOT NULL
           )
        OR purge_status = 'purged'
           AND (
               last_purge_attempt_at IS NULL
               OR purged_at IS NULL
               OR purge_reason IS NULL
               OR last_purge_error IS NOT NULL
           )
        OR purge_status = 'purge_failed'
           AND (
               last_purge_attempt_at IS NULL
               OR purged_at IS NOT NULL
               OR purged_by_user_id IS NOT NULL
               OR purge_reason IS NULL
               OR last_purge_error IS NULL
           )),
    'An evidence attachment contains an inconsistent purge state.'
);

CALL assert_v3_sheet_hardening_condition(
    (SELECT COUNT(*) = 0
     FROM answer_sheet_pages
     WHERE OCTET_LENGTH(qr_payload) NOT BETWEEN 2 AND 256
        OR BINARY qr_payload_hash NOT REGEXP '^[0-9a-f]{64}$'
        OR qr_payload_hash <> SHA2(qr_payload, 256)),
    'A generated page violates the QR byte or digest contract.'
);

CALL assert_v3_sheet_hardening_condition(
    (SELECT COUNT(*) = 0
     FROM scan_pages
     WHERE OCTET_LENGTH(qr_payload) NOT BETWEEN 2 AND 256
        OR BINARY qr_payload_hash NOT REGEXP '^[0-9a-f]{64}$'
        OR qr_payload_hash <> SHA2(qr_payload, 256)),
    'A captured page violates the QR byte or digest contract.'
);

CALL assert_v3_sheet_hardening_condition(
    (SELECT COUNT(*) = 1
     FROM omr_templates
     WHERE template_code = 'OMR-A4-10-MC-CTX-V2'
       AND template_version = '2'
       AND template_status = 'active'
       AND option_count = 4
       AND geometry_hash = SHA2(CAST(geometry_definition AS CHAR), 256)),
    'The physically validated fixed A4 template changed during hardening.'
);

CALL assert_v3_sheet_hardening_condition(
    (SELECT COUNT(*) = 0
     FROM omr_templates ot
     JOIN paper_sizes ps ON ps.paper_size_id = ot.paper_size_id
     WHERE ps.paper_size_code IN ('US_LETTER', 'US_LEGAL')
       AND ot.template_status = 'active'),
    'An unvalidated US Letter or US Legal template was activated.'
);

CALL assert_v3_sheet_hardening_condition(
    (SELECT COUNT(*) = 2
     FROM question_types
     WHERE question_type_code IN ('multiple_choice', 'true_false')
       AND supports_omr = TRUE
       AND requires_teacher_verification = TRUE
       AND allows_teacher_answer_edit = FALSE),
    'MC/TF objective detections must remain teacher-read-only.'
);

CALL assert_v3_sheet_hardening_condition(
    (SELECT COUNT(*) = 157
     FROM information_schema.table_constraints
     WHERE table_schema = 'performance_assessment_v3_db'
       AND constraint_type = 'FOREIGN KEY'),
    'Hardened V3 foreign-key count must be exactly 157.'
);

CALL assert_v3_sheet_hardening_condition(
    (SELECT COUNT(*) = 80
     FROM information_schema.table_constraints
     WHERE table_schema = 'performance_assessment_v3_db'
       AND constraint_type = 'CHECK'),
    'Hardened V3 check-constraint count must be exactly 80.'
);

CALL assert_v3_sheet_hardening_condition(
    (SELECT COUNT(*) = 96
     FROM information_schema.table_constraints
     WHERE table_schema = 'performance_assessment_v3_db'
       AND constraint_type = 'UNIQUE'),
    'Hardened V3 unique-constraint count must remain exactly 96.'
);

CALL finish_v3_sheet_hardening_validation();

DROP PROCEDURE assert_v3_sheet_hardening_condition;
DROP PROCEDURE finish_v3_sheet_hardening_validation;
DROP TEMPORARY TABLE v3_sheet_hardening_validation_failures;
