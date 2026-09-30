-- V3_008 DRAFT: Non-destructive validation for the dynamic answer-sheet delta.
--
-- Approved on 2026-08-30 for controlled local V3 validation after a verified
-- backup. Do not run against V2, TiDB, production, or Mobile SQLite.
-- Intended order:
--   V3_000 -> V3_001 -> V3_002 -> V3_003 -> V3_004 -> V3_005
--   -> V3_006 DRAFT -> V3_007 DRAFT -> V3_008 DRAFT

USE performance_assessment_v3_db;

DELIMITER $$

DROP PROCEDURE IF EXISTS assert_v3_dynamic_sheet_condition$$
DROP PROCEDURE IF EXISTS finish_v3_dynamic_sheet_validation$$
DROP TEMPORARY TABLE IF EXISTS v3_dynamic_sheet_validation_failures$$

CREATE TEMPORARY TABLE v3_dynamic_sheet_validation_failures (
    failure_message VARCHAR(255) NOT NULL
)$$

CREATE PROCEDURE assert_v3_dynamic_sheet_condition(
    IN condition_result BOOLEAN,
    IN failure_message VARCHAR(255)
)
BEGIN
    IF condition_result IS NULL OR condition_result = FALSE THEN
        INSERT INTO v3_dynamic_sheet_validation_failures VALUES (failure_message);
    END IF;
END$$

CREATE PROCEDURE finish_v3_dynamic_sheet_validation()
BEGIN
    IF EXISTS (SELECT 1 FROM v3_dynamic_sheet_validation_failures) THEN
        SELECT failure_message FROM v3_dynamic_sheet_validation_failures;
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V3 dynamic answer-sheet validation failed.';
    ELSE
        SELECT
            'PASS' AS validation_status,
            66 AS central_table_count,
            3 AS paper_size_count,
            15 AS validated_legacy_region_count,
            'performance_assessment_v3_db' AS validated_database;
    END IF;
END$$

DELIMITER ;

CALL assert_v3_dynamic_sheet_condition(
    (SELECT COUNT(*) = 66
     FROM information_schema.tables
     WHERE table_schema = 'performance_assessment_v3_db'
       AND table_type = 'BASE TABLE'),
    'Expanded V3 must contain exactly 66 central tables.'
);

CALL assert_v3_dynamic_sheet_condition(
    (SELECT COUNT(*) = 6
     FROM information_schema.tables
     WHERE table_schema = 'performance_assessment_v3_db'
       AND table_name IN (
           'paper_sizes',
           'omr_template_regions',
           'answer_sheet_versions',
           'answer_sheet_pages',
           'answer_sheet_regions',
           'scan_pages'
       )),
    'One or more dynamic answer-sheet tables are missing.'
);

CALL assert_v3_dynamic_sheet_condition(
    (SELECT COUNT(*) = 3
     FROM paper_sizes
     WHERE is_active = TRUE
       AND (
           (paper_size_code = 'A4'
               AND width_points = 595.276
               AND height_points = 841.890)
           OR (paper_size_code = 'US_LETTER'
               AND width_points = 612.000
               AND height_points = 792.000)
           OR (paper_size_code = 'US_LEGAL'
               AND width_points = 612.000
               AND height_points = 1008.000)
       )),
    'A4, US Letter, and US Legal dimensions must match the approved point sizes.'
);

CALL assert_v3_dynamic_sheet_condition(
    (SELECT COUNT(*) = 1
     FROM omr_templates ot
     JOIN paper_sizes ps ON ps.paper_size_id = ot.paper_size_id
     WHERE ot.template_code = 'OMR-A4-10-MC-CTX-V2'
       AND ot.template_version = '2'
       AND ps.paper_size_code = 'A4'
       AND ot.page_size = 'A4'
       AND ot.page_orientation = 'portrait'
       AND ot.coordinate_origin = 'pdf_bottom_left'
       AND ot.required_print_scale_percent = 100.00
       AND ot.minimum_item_count = 10
       AND ot.maximum_item_count = 10
       AND ot.option_count = 4
       AND ot.template_status = 'active'
       AND ot.geometry_hash = SHA2(CAST(ot.geometry_definition AS CHAR), 256)),
    'The validated OMR-A4-10-MC-CTX-V2 header or geometry hash changed.'
);

CALL assert_v3_dynamic_sheet_condition(
    (SELECT COUNT(*) = 15
     FROM omr_template_regions otr
     JOIN omr_templates ot ON ot.omr_template_id = otr.omr_template_id
     WHERE ot.template_code = 'OMR-A4-10-MC-CTX-V2'),
    'Validated A4 template must have exactly 15 immutable regions.'
);

CALL assert_v3_dynamic_sheet_condition(
    (SELECT COUNT(*) = 4
     FROM omr_template_regions otr
     JOIN omr_templates ot ON ot.omr_template_id = otr.omr_template_id
     WHERE ot.template_code = 'OMR-A4-10-MC-CTX-V2'
       AND otr.region_type = 'registration_marker'
       AND otr.width_points = 15.000
       AND otr.height_points = 15.000
       AND otr.region_code IN (
           'MARKER_TOP_LEFT',
           'MARKER_TOP_RIGHT',
           'MARKER_BOTTOM_LEFT',
           'MARKER_BOTTOM_RIGHT'
       )),
    'Validated A4 template must retain all four 15-point corner markers.'
);

CALL assert_v3_dynamic_sheet_condition(
    (SELECT COUNT(*) = 1
     FROM omr_template_regions otr
     JOIN omr_templates ot ON ot.omr_template_id = otr.omr_template_id
     WHERE ot.template_code = 'OMR-A4-10-MC-CTX-V2'
       AND otr.region_code = 'PAGE_QR'
       AND otr.region_type = 'page_identity'
       AND otr.x_points = 477.276
       AND otr.y_points = 698.890
       AND otr.width_points = 83.000
       AND otr.height_points = 83.000
       AND JSON_UNQUOTE(JSON_EXTRACT(
           otr.geometry_definition,
           '$.error_correction'
       )) = 'M'),
    'Validated A4 QR boundary or error-correction level changed.'
);

CALL assert_v3_dynamic_sheet_condition(
    (SELECT COUNT(*) = 10
     FROM omr_template_regions otr
     JOIN omr_templates ot ON ot.omr_template_id = otr.omr_template_id
     JOIN question_types qt ON qt.question_type_id = otr.question_type_id
     WHERE ot.template_code = 'OMR-A4-10-MC-CTX-V2'
       AND otr.region_type = 'objective_bubbles'
       AND qt.question_type_code = 'multiple_choice'
       AND otr.x_points = 72.600
       AND otr.width_points = 84.800
       AND otr.height_points = 12.800
       AND JSON_UNQUOTE(JSON_EXTRACT(
           otr.geometry_definition,
           '$.bubble_radius_pt'
       )) = '6.4'
       AND JSON_LENGTH(JSON_EXTRACT(
           otr.geometry_definition,
           '$.option_keys'
       )) = 4),
    'Validated A4 sheet must retain ten A-D bubble rows with 6.4-point radius.'
);

CALL assert_v3_dynamic_sheet_condition(
    (SELECT COUNT(*) = 0
     FROM omr_template_regions
     WHERE geometry_hash <> SHA2(CAST(geometry_definition AS CHAR), 256)),
    'A template-region geometry hash does not match its stored JSON.'
);

CALL assert_v3_dynamic_sheet_condition(
    (SELECT COUNT(*) = 2
     FROM omr_template_regions otr
     JOIN omr_templates ot ON ot.omr_template_id = otr.omr_template_id
     WHERE ot.template_code = 'OMR-A4-10-MC-CTX-V2'
       AND (
           (otr.region_code = 'MC_ITEM_01'
               AND JSON_UNQUOTE(JSON_EXTRACT(
                   otr.geometry_definition,
                   '$.bubble_centers_pt[0][1]'
               )) = '604.334')
           OR (otr.region_code = 'MC_ITEM_10'
               AND JSON_UNQUOTE(JSON_EXTRACT(
                   otr.geometry_definition,
                   '$.bubble_centers_pt[0][1]'
               )) = '306.334')
       )),
    'First or last validated A4 bubble-row position changed.'
);

CALL assert_v3_dynamic_sheet_condition(
    (SELECT COUNT(*) = 0
     FROM omr_template_regions otr
     JOIN omr_templates ot ON ot.omr_template_id = otr.omr_template_id
     JOIN paper_sizes ps ON ps.paper_size_id = ot.paper_size_id
     WHERE otr.x_points < 0
        OR otr.y_points < 0
        OR otr.x_points + otr.width_points > CASE
            WHEN ot.page_orientation = 'portrait' THEN ps.width_points
            ELSE ps.height_points
        END
        OR otr.y_points + otr.height_points > CASE
            WHEN ot.page_orientation = 'portrait' THEN ps.height_points
            ELSE ps.width_points
        END),
    'A template region falls outside its exact paper boundary.'
);

CALL assert_v3_dynamic_sheet_condition(
    (SELECT COUNT(*) = 4
     FROM information_schema.columns
     WHERE table_schema = 'performance_assessment_v3_db'
       AND (
           (table_name = 'questions'
               AND column_name IN (
                   'response_region_size',
                   'force_page_break_before'
               ))
           OR (table_name = 'scan_sessions'
               AND column_name IN (
                   'answer_sheet_version_id',
                   'expected_page_count'
               ))
       )),
    'Required question or scan-session expansion columns are missing.'
);

CALL assert_v3_dynamic_sheet_condition(
    (SELECT COUNT(DISTINCT index_name) = 2
     FROM information_schema.statistics
     WHERE table_schema = 'performance_assessment_v3_db'
       AND table_name = 'omr_detections'
       AND index_name IN (
           'uk_omr_detections_legacy_scan_question',
           'uk_omr_detections_page_region'
       )
       AND non_unique = 0),
    'Both legacy-only and page-region OMR duplicate guards must be unique.'
);

CALL assert_v3_dynamic_sheet_condition(
    (SELECT COUNT(*) = 0
     FROM answer_sheet_pages asp
     JOIN answer_sheet_versions asv
       ON asv.answer_sheet_version_id = asp.answer_sheet_version_id
     JOIN omr_templates ot ON ot.omr_template_id = asp.omr_template_id
     WHERE asp.total_pages <> asv.total_pages
        OR ot.paper_size_id <> asv.paper_size_id),
    'Generated page count or paper size disagrees with its sheet version.'
);

CALL assert_v3_dynamic_sheet_condition(
    (SELECT COUNT(*) = 0
     FROM answer_sheet_regions asr
     JOIN answer_sheet_pages asp
       ON asp.answer_sheet_page_id = asr.answer_sheet_page_id
     JOIN answer_sheet_versions asv
       ON asv.answer_sheet_version_id = asr.answer_sheet_version_id
     JOIN test_assignments ta
       ON ta.test_assignment_id = asv.test_assignment_id
     JOIN omr_template_regions otr
       ON otr.omr_template_region_id = asr.omr_template_region_id
     JOIN questions q ON q.question_id = asr.question_id
     JOIN test_parts tp ON tp.test_part_id = q.test_part_id
     WHERE asr.answer_sheet_version_id <> asp.answer_sheet_version_id
        OR otr.omr_template_id <> asp.omr_template_id
        OR q.test_part_id <> asr.test_part_id
        OR q.question_type_id <> asr.question_type_id
        OR tp.test_id <> ta.test_id
        OR asr.geometry_hash <> SHA2(CAST(asr.geometry_snapshot AS CHAR), 256)),
    'A generated region has a page, template, assignment, part, or type mismatch.'
);

CALL assert_v3_dynamic_sheet_condition(
    (SELECT COUNT(*) = 0
     FROM scan_pages sp
     JOIN scan_sessions ss ON ss.scan_session_id = sp.scan_session_id
     JOIN answer_sheet_pages asp
       ON asp.answer_sheet_page_id = sp.answer_sheet_page_id
     JOIN answer_sheet_versions asv
       ON asv.answer_sheet_version_id = asp.answer_sheet_version_id
     WHERE ss.answer_sheet_version_id IS NULL
        OR ss.answer_sheet_version_id <> asp.answer_sheet_version_id
        OR ss.test_assignment_id <> asv.test_assignment_id
        OR sp.omr_template_id <> asp.omr_template_id
        OR sp.page_number <> asp.page_number),
    'A captured scan page does not match its session manifest page.'
);

CALL assert_v3_dynamic_sheet_condition(
    (SELECT COUNT(*) = 0
     FROM answer_rubric_scores
     WHERE maximum_points_snapshot IS NOT NULL
       AND points_awarded > maximum_points_snapshot),
    'A rubric criterion score exceeds its stored maximum-points snapshot.'
);

CALL assert_v3_dynamic_sheet_condition(
    (SELECT COUNT(*) = 0
     FROM answer_verifications av
     JOIN student_answers sa ON sa.student_answer_id = av.student_answer_id
     JOIN questions q ON q.question_id = sa.question_id
     JOIN question_types qt ON qt.question_type_id = q.question_type_id
     WHERE qt.question_type_code IN ('multiple_choice', 'true_false')
       AND NOT (
           av.verification_action = 'finalized'
           AND av.reason_code IN (
               'legacy_v2_finalization',
               'legacy_v2_correction'
           )
       )),
    'Objective MC/TF may retain only explicitly tagged finalized V2 audit rows.'
);

CALL assert_v3_dynamic_sheet_condition(
    (SELECT COUNT(*) = 2
     FROM question_types
     WHERE question_type_code IN ('multiple_choice', 'true_false')
       AND supports_omr = TRUE
       AND requires_teacher_verification = TRUE
       AND allows_teacher_answer_edit = FALSE),
    'MC/TF must remain OMR-verifiable and teacher-read-only.'
);

CALL assert_v3_dynamic_sheet_condition(
    (SELECT COUNT(*) = 0
     FROM omr_templates ot
     JOIN paper_sizes ps ON ps.paper_size_id = ot.paper_size_id
     WHERE ps.paper_size_code IN ('US_LETTER', 'US_LEGAL')
       AND ot.template_status = 'active'),
    'Unvalidated US Letter or US Legal templates must not be active.'
);

CALL finish_v3_dynamic_sheet_validation();

-- Historical V2 rows are retained for auditability. They are compatibility
-- evidence only and do not authorize V3 services to replace objective answers.
SELECT
    av.reason_code,
    COUNT(*) AS legacy_objective_audit_rows,
    SUM(NOT (av.previous_answer_value <=> av.new_answer_value))
        AS historical_value_change_rows
FROM answer_verifications av
JOIN student_answers sa ON sa.student_answer_id = av.student_answer_id
JOIN questions q ON q.question_id = sa.question_id
JOIN question_types qt ON qt.question_type_id = q.question_type_id
WHERE qt.question_type_code IN ('multiple_choice', 'true_false')
  AND av.verification_action = 'finalized'
  AND av.reason_code IN ('legacy_v2_finalization', 'legacy_v2_correction')
GROUP BY av.reason_code
ORDER BY av.reason_code;

DROP PROCEDURE assert_v3_dynamic_sheet_condition;
DROP PROCEDURE finish_v3_dynamic_sheet_validation;
DROP TEMPORARY TABLE v3_dynamic_sheet_validation_failures;
