-- V3_005: Non-destructive structural and reference-data smoke tests.
-- Run after the canonical V3 schema and V3_004_reference_seed.sql.

USE performance_assessment_v3_db;

DELIMITER $$

DROP PROCEDURE IF EXISTS assert_v3_condition$$
DROP PROCEDURE IF EXISTS finish_v3_validation$$

CREATE TEMPORARY TABLE v3_validation_failures (
    failure_message VARCHAR(255) NOT NULL
)$$

CREATE PROCEDURE assert_v3_condition(
    IN condition_result BOOLEAN,
    IN failure_message VARCHAR(255)
)
BEGIN
    IF condition_result IS NULL OR condition_result = FALSE THEN
        INSERT INTO v3_validation_failures VALUES (failure_message);
    END IF;
END$$

CREATE PROCEDURE finish_v3_validation()
BEGIN
    IF EXISTS (SELECT 1 FROM v3_validation_failures) THEN
        SELECT failure_message FROM v3_validation_failures;
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V3 database smoke validation failed.';
    ELSE
        SELECT
            'PASS' AS validation_status,
            60 AS central_table_count,
            'performance_assessment_v3_db' AS validated_database;
    END IF;
END$$

DELIMITER ;

CALL assert_v3_condition(
    (SELECT COUNT(*) = 60
     FROM information_schema.tables
     WHERE table_schema = 'performance_assessment_v3_db'),
    'V3 must contain exactly 60 central tables.'
);

CALL assert_v3_condition(
    (SELECT COUNT(*) = 0
     FROM information_schema.tables
     WHERE table_schema = 'performance_assessment_v3_db'
       AND table_name IN ('mappings', 'email_verification_otps',
                          'term_one', 'term_two', 'term_three', 'term_four')),
    'A removed or rejected legacy table is present.'
);

CALL assert_v3_condition(
    (SELECT COUNT(*) = 5 FROM question_types),
    'All five approved question types must be preloaded.'
);

CALL assert_v3_condition(
    (SELECT COUNT(*) = 2
     FROM question_types
     WHERE question_type_code IN ('multiple_choice', 'true_false')
       AND supports_omr = TRUE
       AND requires_teacher_verification = TRUE
       AND allows_teacher_answer_edit = FALSE),
    'Multiple Choice and True/False must be OMR-verifiable and teacher-read-only.'
);

CALL assert_v3_condition(
    (SELECT COUNT(*) = 1
     FROM omr_templates
     WHERE template_code = 'OMR-A4-10-MC-CTX-V2'
       AND page_size = 'A4'
       AND page_orientation = 'portrait'
       AND minimum_item_count = 10
       AND maximum_item_count = 10
       AND option_count = 4
       AND qr_payload_version = 2
       AND template_status = 'active'
       AND JSON_VALID(geometry_definition) = 1
       AND JSON_UNQUOTE(JSON_EXTRACT(geometry_definition, '$.page_width_pt')) = '595.276'
       AND JSON_UNQUOTE(JSON_EXTRACT(geometry_definition, '$.page_height_pt')) = '841.89'
       AND JSON_LENGTH(JSON_EXTRACT(geometry_definition, '$.markers.centers_pt')) = 4
       AND JSON_LENGTH(JSON_EXTRACT(geometry_definition, '$.bubbles.y_centers_pt')) = 10),
    'Validated OMR template geometry is missing or changed.'
);

CALL assert_v3_condition(
    (SELECT COUNT(*) = 4
     FROM performance_rule_sets
     WHERE rule_set_name = 'SMART Standard Performance Bands'
       AND rule_version = '1.0'
       AND school_id IS NULL
       AND rule_status = 'active'
       AND JSON_VALID(rule_definition) = 1
       AND JSON_LENGTH(JSON_EXTRACT(rule_definition, '$.bands')) = 4),
    'All four versioned system performance rule scopes must be preloaded.'
);

CALL assert_v3_condition(
    (SELECT COUNT(*) = 4
     FROM performance_rule_sets
     WHERE rule_set_name = 'SMART Standard Performance Bands'
       AND rule_version = '1.0'
       AND JSON_UNQUOTE(JSON_EXTRACT(rule_definition, '$.bands[0].minimum_percentage')) = '80'
       AND JSON_UNQUOTE(JSON_EXTRACT(rule_definition, '$.bands[1].minimum_percentage')) = '60'
       AND JSON_UNQUOTE(JSON_EXTRACT(rule_definition, '$.bands[2].minimum_percentage')) = '40'
       AND JSON_UNQUOTE(JSON_EXTRACT(rule_definition, '$.bands[3].minimum_percentage')) = '0'),
    'System performance bands must retain the validated 80/60/40 boundaries.'
);

CALL assert_v3_condition(
    (SELECT COUNT(*) = 2
     FROM information_schema.statistics
     WHERE table_schema = 'performance_assessment_v3_db'
       AND table_name = 'class_lists'
       AND index_name = 'uk_class_lists_active_student_year'
       AND non_unique = 0),
    'One-active-class-per-learner/year uniqueness is missing.'
);

CALL assert_v3_condition(
    (SELECT COUNT(*) = 2
     FROM information_schema.statistics
     WHERE table_schema = 'performance_assessment_v3_db'
       AND table_name = 'sync_items'
       AND index_name = 'uk_sync_items_batch_result_uuid'
       AND non_unique = 0),
    'Sync item idempotency key must be UNIQUE(sync_id, result_uuid).'
);

CALL assert_v3_condition(
    (SELECT column_type LIKE '%partial_success%'
     FROM information_schema.columns
     WHERE table_schema = 'performance_assessment_v3_db'
       AND table_name = 'syncs'
       AND column_name = 'sync_status'),
    'syncs.sync_status must support partial_success.'
);

CALL assert_v3_condition(
    (SELECT COUNT(*) = 0
     FROM information_schema.columns
     WHERE table_schema = 'performance_assessment_v3_db'
       AND extra LIKE '%on update%'
       AND column_name <> 'updated_at'),
    'Only updated_at columns may auto-update; evidence timestamps must be immutable.'
);

CALL assert_v3_condition(
    (SELECT COUNT(*) = 6 FROM statuses),
    'All six account lifecycle statuses must be preloaded.'
);

CALL assert_v3_condition(
    (SELECT COUNT(*) = 3
     FROM information_schema.tables
     WHERE table_schema = 'performance_assessment_v3_db'
       AND table_name IN ('user_mfa_factors', 'mfa_recovery_codes',
                          'mfa_authentication_challenges')),
    'Authenticator-app MFA tables are incomplete.'
);

CALL finish_v3_validation();

DROP PROCEDURE assert_v3_condition;
DROP PROCEDURE finish_v3_validation;
