-- V3_010: Read-only V2 data preflight before copying operational data to V3.
-- Expected order: canonical V3 schema -> V3_004_reference_seed.sql -> this file.
-- This script never modifies performance_assessment_v2_db.

USE performance_assessment_v3_db;

DROP TEMPORARY TABLE IF EXISTS v3_preflight_findings;
CREATE TEMPORARY TABLE v3_preflight_findings (
    severity ENUM('ERROR', 'WARNING', 'INFO') NOT NULL,
    finding_code VARCHAR(80) NOT NULL,
    affected_rows INT UNSIGNED NOT NULL,
    finding_detail VARCHAR(500) NOT NULL
);

INSERT INTO v3_preflight_findings
SELECT
    'ERROR',
    'TARGET_NOT_EMPTY',
    source_count,
    'Rebuild the isolated V3 database before migration; operational V3 tables are not empty.'
FROM (
    SELECT
        (SELECT COUNT(*) FROM users)
      + (SELECT COUNT(*) FROM students)
      + (SELECT COUNT(*) FROM classes)
      + (SELECT COUNT(*) FROM class_assignments)
      + (SELECT COUNT(*) FROM class_lists)
      + (SELECT COUNT(*) FROM tests)
      + (SELECT COUNT(*) FROM test_results)
      + (SELECT COUNT(*) FROM scan_sessions)
      + (SELECT COUNT(*) FROM syncs) AS source_count
) counts
WHERE source_count > 0;

INSERT INTO v3_preflight_findings
SELECT
    'ERROR',
    'UNSUPPORTED_PART_TYPE',
    COUNT(*),
    'A V2 test part has no active V3 question_types match.'
FROM performance_assessment_v2_db.test_parts tp
LEFT JOIN question_types qt
    ON qt.question_type_code = tp.part_type
   AND qt.is_active = TRUE
WHERE qt.question_type_id IS NULL
HAVING COUNT(*) > 0;

INSERT INTO v3_preflight_findings
SELECT
    'ERROR',
    'UNSUPPORTED_OBJECTIVE_OPTION',
    COUNT(*),
    'V2 contains an answer key or student answer outside A-D.'
FROM (
    SELECT correct_option AS option_value
    FROM performance_assessment_v2_db.answer_keys
    UNION ALL
    SELECT selected_option
    FROM performance_assessment_v2_db.student_answers
    WHERE selected_option IS NOT NULL
) options
WHERE option_value NOT IN ('A', 'B', 'C', 'D')
HAVING COUNT(*) > 0;

INSERT INTO v3_preflight_findings
SELECT
    'ERROR',
    'SCAN_RESULT_LINK_COUNT',
    COUNT(*),
    'Every legacy scan must resolve to exactly one test result for V3 assignment and learner context.'
FROM (
    SELECT ss.scan_session_id
    FROM performance_assessment_v2_db.scan_sessions ss
    LEFT JOIN performance_assessment_v2_db.test_result_scans trs
        ON trs.scan_session_id = ss.scan_session_id
    GROUP BY ss.scan_session_id
    HAVING COUNT(trs.test_result_scan_id) <> 1
) invalid_scans
HAVING COUNT(*) > 0;

INSERT INTO v3_preflight_findings
SELECT
    'ERROR',
    'RESULT_CLASS_MISMATCH',
    COUNT(*),
    'A result membership is not in the class to which its source V2 test was assigned.'
FROM performance_assessment_v2_db.test_results tr
JOIN performance_assessment_v2_db.tests t
    ON t.test_id = tr.test_id
JOIN performance_assessment_v2_db.class_assignments ca
    ON ca.class_assignment_id = t.class_assignment_id
JOIN performance_assessment_v2_db.class_lists cl
    ON cl.class_list_id = tr.class_list_id
WHERE ca.class_id <> cl.class_id
HAVING COUNT(*) > 0;

INSERT INTO v3_preflight_findings
SELECT
    'ERROR',
    'INVALID_RESULT_SCORE',
    COUNT(*),
    'A V2 result has a negative score or total_score greater than max_score.'
FROM performance_assessment_v2_db.test_results
WHERE total_score < 0 OR max_score < 0 OR total_score > max_score
HAVING COUNT(*) > 0;

INSERT INTO v3_preflight_findings
SELECT
    'ERROR',
    'CROSS_TEST_SYNC_BATCH',
    COUNT(*),
    'A V2 sync contains results for more than one test; V3 sync envelopes are test-assignment scoped.'
FROM (
    SELECT si.sync_id
    FROM performance_assessment_v2_db.sync_items si
    JOIN performance_assessment_v2_db.test_results tr
        ON tr.test_result_id = si.test_result_id
    GROUP BY si.sync_id
    HAVING COUNT(DISTINCT tr.test_id) > 1
) invalid_syncs
HAVING COUNT(*) > 0;

INSERT INTO v3_preflight_findings
SELECT
    'ERROR',
    'MISSING_OMR_TEMPLATE',
    COUNT(*),
    'A legacy scan template_version has no active V3 omr_templates match.'
FROM performance_assessment_v2_db.scan_sessions ss
LEFT JOIN omr_templates ot
    ON ot.template_code = ss.template_version
   AND ot.template_status = 'active'
WHERE ot.omr_template_id IS NULL
HAVING COUNT(*) > 0;

INSERT INTO v3_preflight_findings
SELECT
    'WARNING',
    'MULTIPLE_CLASS_MEMBERSHIPS',
    COUNT(*),
    'Learners have multiple V2 memberships in one academic year. All rows will be retained, but only one will remain enrolled.'
FROM (
    SELECT cl.student_id, c.academic_year_id
    FROM performance_assessment_v2_db.class_lists cl
    JOIN performance_assessment_v2_db.classes c ON c.class_id = cl.class_id
    GROUP BY cl.student_id, c.academic_year_id
    HAVING COUNT(*) > 1
) duplicate_memberships
HAVING COUNT(*) > 0;

INSERT INTO v3_preflight_findings VALUES
    ('INFO', 'SOURCE_USERS', (SELECT COUNT(*) FROM performance_assessment_v2_db.users), 'V2 users to migrate.'),
    ('INFO', 'SOURCE_STUDENTS', (SELECT COUNT(*) FROM performance_assessment_v2_db.students), 'V2 students to migrate.'),
    ('INFO', 'SOURCE_CLASS_LISTS', (SELECT COUNT(*) FROM performance_assessment_v2_db.class_lists), 'V2 class memberships to migrate.'),
    ('INFO', 'SOURCE_TESTS', (SELECT COUNT(*) FROM performance_assessment_v2_db.tests), 'V2 assessments to migrate.'),
    ('INFO', 'SOURCE_RESULTS', (SELECT COUNT(*) FROM performance_assessment_v2_db.test_results), 'V2 results to migrate.'),
    ('INFO', 'SOURCE_SCANS', (SELECT COUNT(*) FROM performance_assessment_v2_db.scan_sessions), 'V2 scan sessions to migrate.'),
    ('INFO', 'SOURCE_SYNCS', (SELECT COUNT(*) FROM performance_assessment_v2_db.syncs), 'V2 synchronization envelopes to migrate.');

SELECT severity, finding_code, affected_rows, finding_detail
FROM v3_preflight_findings
ORDER BY FIELD(severity, 'ERROR', 'WARNING', 'INFO'), finding_code;

DROP PROCEDURE IF EXISTS assert_v3_preflight;
DELIMITER //
CREATE PROCEDURE assert_v3_preflight()
BEGIN
    IF EXISTS (SELECT 1 FROM v3_preflight_findings WHERE severity = 'ERROR') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V3 migration preflight failed. Resolve ERROR findings before migration.';
    END IF;
END//
DELIMITER ;

CALL assert_v3_preflight();
DROP PROCEDURE assert_v3_preflight;
