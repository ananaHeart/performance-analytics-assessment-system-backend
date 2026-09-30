-- V3_012: Post-migration reconciliation for V2 -> V3 operational data.
-- A FAIL row aborts the script after the complete result matrix is printed.

USE performance_assessment_v3_db;

DROP TEMPORARY TABLE IF EXISTS v3_reconciliation;
CREATE TEMPORARY TABLE v3_reconciliation (
    check_name VARCHAR(100) NOT NULL,
    expected_value DECIMAL(20,4) NOT NULL,
    actual_value DECIMAL(20,4) NOT NULL,
    check_status ENUM('PASS', 'FAIL') NOT NULL,
    check_detail VARCHAR(500) NOT NULL
);

INSERT INTO v3_reconciliation
SELECT 'users_count',
       (SELECT COUNT(*) FROM performance_assessment_v2_db.users),
       (SELECT COUNT(*) FROM users),
       IF((SELECT COUNT(*) FROM performance_assessment_v2_db.users) = (SELECT COUNT(*) FROM users), 'PASS', 'FAIL'),
       'Every V2 user is preserved with a mapped V3 account status.';

INSERT INTO v3_reconciliation
SELECT 'students_count',
       (SELECT COUNT(*) FROM performance_assessment_v2_db.students),
       (SELECT COUNT(*) FROM students),
       IF((SELECT COUNT(*) FROM performance_assessment_v2_db.students) = (SELECT COUNT(*) FROM students), 'PASS', 'FAIL'),
       'Every learner identity is preserved.';

INSERT INTO v3_reconciliation
SELECT 'class_memberships_count',
       (SELECT COUNT(*) FROM performance_assessment_v2_db.class_lists),
       (SELECT COUNT(*) FROM class_lists),
       IF((SELECT COUNT(*) FROM performance_assessment_v2_db.class_lists) = (SELECT COUNT(*) FROM class_lists), 'PASS', 'FAIL'),
       'All legacy memberships remain traceable, including closed duplicates.';

INSERT INTO v3_reconciliation
SELECT 'active_membership_duplicates', 0, COUNT(*), IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       'At most one enrolled class membership is allowed per learner and academic year.'
FROM (
    SELECT student_id, academic_year_id
    FROM class_lists
    WHERE enrollment_status = 'enrolled'
    GROUP BY student_id, academic_year_id
    HAVING COUNT(*) > 1
) duplicates;

INSERT INTO v3_reconciliation
SELECT 'tests_count',
       (SELECT COUNT(*) FROM performance_assessment_v2_db.tests),
       (SELECT COUNT(*) FROM tests),
       IF((SELECT COUNT(*) FROM performance_assessment_v2_db.tests) = (SELECT COUNT(*) FROM tests), 'PASS', 'FAIL'),
       'Reusable V3 test content count matches V2 assessments.';

INSERT INTO v3_reconciliation
SELECT 'test_assignments_count',
       (SELECT COUNT(*) FROM performance_assessment_v2_db.tests),
       (SELECT COUNT(*) FROM test_assignments),
       IF((SELECT COUNT(*) FROM performance_assessment_v2_db.tests) = (SELECT COUNT(*) FROM test_assignments), 'PASS', 'FAIL'),
       'Every V2 assessment has one initial V3 class delivery.';

INSERT INTO v3_reconciliation
SELECT 'questions_count',
       (SELECT COUNT(*) FROM performance_assessment_v2_db.questions),
       (SELECT COUNT(*) FROM questions),
       IF((SELECT COUNT(*) FROM performance_assessment_v2_db.questions) = (SELECT COUNT(*) FROM questions), 'PASS', 'FAIL'),
       'Question IDs and content are preserved.';

INSERT INTO v3_reconciliation
SELECT 'question_options_count',
       (SELECT COUNT(*) * 4 FROM performance_assessment_v2_db.questions),
       (SELECT COUNT(*) FROM question_options),
       IF((SELECT COUNT(*) * 4 FROM performance_assessment_v2_db.questions) = (SELECT COUNT(*) FROM question_options), 'PASS', 'FAIL'),
       'Current V2 Multiple Choice questions are normalized to exactly A-D options.';

INSERT INTO v3_reconciliation
SELECT 'answer_keys_count',
       (SELECT COUNT(*) FROM performance_assessment_v2_db.answer_keys),
       (SELECT COUNT(*) FROM answer_keys),
       IF((SELECT COUNT(*) FROM performance_assessment_v2_db.answer_keys) = (SELECT COUNT(*) FROM answer_keys), 'PASS', 'FAIL'),
       'Every V2 correct option resolves to one normalized V3 option.';

INSERT INTO v3_reconciliation
SELECT 'part_skill_mappings_count',
       (SELECT COUNT(*) FROM performance_assessment_v2_db.mappings),
       (SELECT COUNT(*) FROM part_skill_mappings),
       IF((SELECT COUNT(*) FROM performance_assessment_v2_db.mappings) = (SELECT COUNT(*) FROM part_skill_mappings), 'PASS', 'FAIL'),
       'Every question-to-skill mapping is preserved as a one-item part range.';

INSERT INTO v3_reconciliation
SELECT 'test_results_count',
       (SELECT COUNT(*) FROM performance_assessment_v2_db.test_results),
       (SELECT COUNT(*) FROM test_results),
       IF((SELECT COUNT(*) FROM performance_assessment_v2_db.test_results) = (SELECT COUNT(*) FROM test_results), 'PASS', 'FAIL'),
       'Every distinct learner attempt is preserved once.';

INSERT INTO v3_reconciliation
SELECT 'duplicate_result_attempts', 0, COUNT(*), IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       'Retries must not create duplicate test-assignment, learner, and attempt tuples.'
FROM (
    SELECT test_assignment_id, class_list_id, attempt_number
    FROM test_results
    GROUP BY test_assignment_id, class_list_id, attempt_number
    HAVING COUNT(*) > 1
) duplicate_results;

INSERT INTO v3_reconciliation
SELECT 'invalid_result_scores', 0, COUNT(*), IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       'Every total score is within zero and max score.'
FROM test_results
WHERE total_score < 0 OR max_score < 0 OR total_score > max_score;

INSERT INTO v3_reconciliation
SELECT 'result_total_score_sum',
       (SELECT COALESCE(SUM(total_score), 0) FROM performance_assessment_v2_db.test_results),
       (SELECT COALESCE(SUM(total_score), 0) FROM test_results),
       IF(
           (SELECT COALESCE(SUM(total_score), 0) FROM performance_assessment_v2_db.test_results)
           = (SELECT COALESCE(SUM(total_score), 0) FROM test_results),
           'PASS', 'FAIL'
       ),
       'Aggregate earned points match exactly.';

INSERT INTO v3_reconciliation
SELECT 'result_max_score_sum',
       (SELECT COALESCE(SUM(max_score), 0) FROM performance_assessment_v2_db.test_results),
       (SELECT COALESCE(SUM(max_score), 0) FROM test_results),
       IF(
           (SELECT COALESCE(SUM(max_score), 0) FROM performance_assessment_v2_db.test_results)
           = (SELECT COALESCE(SUM(max_score), 0) FROM test_results),
           'PASS', 'FAIL'
       ),
       'Aggregate maximum points match exactly.';

INSERT INTO v3_reconciliation
SELECT 'student_answers_count',
       (SELECT COUNT(*) FROM performance_assessment_v2_db.student_answers),
       (SELECT COUNT(*) FROM student_answers),
       IF((SELECT COUNT(*) FROM performance_assessment_v2_db.student_answers) = (SELECT COUNT(*) FROM student_answers), 'PASS', 'FAIL'),
       'Every final learner answer is preserved.';

INSERT INTO v3_reconciliation
SELECT 'student_answer_points_sum',
       (SELECT COALESCE(SUM(points_earned), 0) FROM performance_assessment_v2_db.student_answers),
       (SELECT COALESCE(SUM(points_earned), 0) FROM student_answers),
       IF(
           (SELECT COALESCE(SUM(points_earned), 0) FROM performance_assessment_v2_db.student_answers)
           = (SELECT COALESCE(SUM(points_earned), 0) FROM student_answers),
           'PASS', 'FAIL'
       ),
       'Verified answer points match exactly.';

INSERT INTO v3_reconciliation
SELECT 'scan_sessions_count',
       (SELECT COUNT(*) FROM performance_assessment_v2_db.scan_sessions),
       (SELECT COUNT(*) FROM scan_sessions),
       IF((SELECT COUNT(*) FROM performance_assessment_v2_db.scan_sessions) = (SELECT COUNT(*) FROM scan_sessions), 'PASS', 'FAIL'),
       'Every raw scan session is preserved with explicit test and learner context.';

INSERT INTO v3_reconciliation
SELECT 'omr_detections_count',
       (SELECT COUNT(*) FROM performance_assessment_v2_db.omr_detections),
       (SELECT COUNT(*) FROM omr_detections),
       IF((SELECT COUNT(*) FROM performance_assessment_v2_db.omr_detections) = (SELECT COUNT(*) FROM omr_detections), 'PASS', 'FAIL'),
       'Every immutable raw OMR detection is preserved.';

INSERT INTO v3_reconciliation
SELECT 'scan_verifications_count',
       (SELECT COUNT(*) FROM performance_assessment_v2_db.scan_sessions WHERE scan_status = 'verified'),
       (SELECT COUNT(*) FROM scan_verifications),
       IF(
           (SELECT COUNT(*) FROM performance_assessment_v2_db.scan_sessions WHERE scan_status = 'verified')
           = (SELECT COUNT(*) FROM scan_verifications),
           'PASS', 'FAIL'
       ),
       'Legacy verified scans have append-only V3 verification records.';

INSERT INTO v3_reconciliation
SELECT 'answer_verifications_count',
       (SELECT COUNT(*) FROM performance_assessment_v2_db.student_answers),
       (SELECT COUNT(*) FROM answer_verifications),
       IF((SELECT COUNT(*) FROM performance_assessment_v2_db.student_answers) = (SELECT COUNT(*) FROM answer_verifications), 'PASS', 'FAIL'),
       'Legacy finalization is retained as audit history without enabling MC/TF edits.';

INSERT INTO v3_reconciliation
SELECT 'wrong_result_class_context', 0, COUNT(*), IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       'Every result learner belongs to the exact class in its test assignment.'
FROM test_results tr
JOIN test_assignments ta ON ta.test_assignment_id = tr.test_assignment_id
JOIN class_assignments ca ON ca.class_assignment_id = ta.class_assignment_id
JOIN class_lists cl ON cl.class_list_id = tr.class_list_id
WHERE ca.class_id <> cl.class_id;

INSERT INTO v3_reconciliation
SELECT 'syncs_count',
       (SELECT COUNT(*) FROM performance_assessment_v2_db.syncs),
       (SELECT COUNT(*) FROM syncs),
       IF((SELECT COUNT(*) FROM performance_assessment_v2_db.syncs) = (SELECT COUNT(*) FROM syncs), 'PASS', 'FAIL'),
       'Stable sync UUID envelopes are preserved.';

INSERT INTO v3_reconciliation
SELECT 'sync_items_count',
       (SELECT COUNT(*) FROM performance_assessment_v2_db.sync_items),
       (SELECT COUNT(*) FROM sync_items),
       IF((SELECT COUNT(*) FROM performance_assessment_v2_db.sync_items) = (SELECT COUNT(*) FROM sync_items), 'PASS', 'FAIL'),
       'Stable result UUID sync items are preserved and normalized to upsert.';

INSERT INTO v3_reconciliation
SELECT 'sync_request_item_mismatches', 0, COUNT(*), IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       'Each sync envelope request count matches its item rows.'
FROM (
    SELECT s.sync_id
    FROM syncs s
    LEFT JOIN sync_items si ON si.sync_id = s.sync_id
    GROUP BY s.sync_id, s.request_item_count
    HAVING s.request_item_count <> COUNT(si.sync_item_id)
) mismatches;

INSERT INTO v3_reconciliation
SELECT 'result_uuid_identity_matches',
       (SELECT COUNT(*) FROM performance_assessment_v2_db.test_results),
       COUNT(*),
       IF(COUNT(*) = (SELECT COUNT(*) FROM performance_assessment_v2_db.test_results), 'PASS', 'FAIL'),
       'Offline result UUID and central result ID pairs remain unchanged.'
FROM performance_assessment_v2_db.test_results old_result
JOIN test_results new_result
    ON new_result.test_result_id = old_result.test_result_id
   AND new_result.result_uuid = old_result.result_uuid;

SELECT check_name, expected_value, actual_value, check_status, check_detail
FROM v3_reconciliation
ORDER BY check_status, check_name;

DROP PROCEDURE IF EXISTS assert_v3_reconciliation;
DELIMITER //
CREATE PROCEDURE assert_v3_reconciliation()
BEGIN
    IF EXISTS (SELECT 1 FROM v3_reconciliation WHERE check_status = 'FAIL') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V3 data reconciliation failed. Review FAIL rows before integration.';
    END IF;
END//
DELIMITER ;

CALL assert_v3_reconciliation();
DROP PROCEDURE assert_v3_reconciliation;
