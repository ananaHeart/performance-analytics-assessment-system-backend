-- V1 -> V2 read-only preflight checks.
-- Safe to run locally. This script does not insert, update, or delete data.

SELECT 'source_database_exists' AS check_name,
       COUNT(*) = 1 AS passed,
       COUNT(*) AS observed
FROM information_schema.schemata
WHERE schema_name = 'performance_assessment_db';

SELECT 'target_database_exists' AS check_name,
       COUNT(*) = 1 AS passed,
       COUNT(*) AS observed
FROM information_schema.schemata
WHERE schema_name = 'performance_assessment_v2_db';

SELECT 'target_operational_rows_must_be_zero' AS check_name,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.test_results)
       + (SELECT COUNT(*) FROM performance_assessment_v2_db.scan_sessions)
       + (SELECT COUNT(*) FROM performance_assessment_v2_db.syncs) = 0 AS passed,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.test_results)
       + (SELECT COUNT(*) FROM performance_assessment_v2_db.scan_sessions)
       + (SELECT COUNT(*) FROM performance_assessment_v2_db.syncs) AS observed;

SELECT 'tests_missing_term_period' AS check_name,
       COUNT(*) = 0 AS passed,
       COUNT(*) AS observed
FROM performance_assessment_db.test
WHERE grading_period_id IS NULL;

SELECT 'test_parts_with_answer_count_mismatch' AS check_name,
       COUNT(*) = 0 AS passed,
       COUNT(*) AS observed
FROM performance_assessment_db.test_part
WHERE number_of_items < 1
   OR answer_key IS NULL
   OR answer_key = ''
   OR (
        LENGTH(answer_key) - LENGTH(REPLACE(answer_key, ',', '')) + 1
      ) <> number_of_items;

SELECT 'results_without_class_membership' AS check_name,
       COUNT(*) = 0 AS passed,
       COUNT(*) AS observed
FROM performance_assessment_db.test_result tr
JOIN performance_assessment_db.test t ON t.test_id = tr.test_id
JOIN performance_assessment_db.class c ON c.class_id = t.class_id
LEFT JOIN performance_assessment_db.student_enrollment se
       ON se.student_id = tr.student_id
      AND se.section_id = c.section_id
      AND se.academic_year_id = c.academic_year_id
WHERE se.student_enrollment_id IS NULL;

SELECT 'results_exceeding_derived_max_score' AS check_name,
       COUNT(*) = 0 AS passed,
       COUNT(*) AS observed
FROM (
    SELECT tr.test_result_id,
           tr.total_score,
           COALESCE(SUM(tp.number_of_items * tp.points_per_item), 0) AS max_score
    FROM performance_assessment_db.test_result tr
    LEFT JOIN performance_assessment_db.test_part tp ON tp.test_id = tr.test_id
    GROUP BY tr.test_result_id, tr.total_score
) scores
WHERE max_score <= 0 OR total_score > max_score;

SELECT 'invalid_lrn' AS check_name,
       COUNT(*) = 0 AS passed,
       COUNT(*) AS observed
FROM performance_assessment_db.student
WHERE student_lrn NOT REGEXP '^[0-9]{12}$';

SELECT 'invalid_email' AS check_name,
       COUNT(*) = 0 AS passed,
       COUNT(*) AS observed
FROM performance_assessment_db.user
WHERE email IS NULL OR email = '' OR email NOT LIKE '%@%';

SELECT 'suspected_text_encoding_damage' AS check_name,
       COUNT(*) = 0 AS passed,
       COUNT(*) AS observed
FROM performance_assessment_db.user
WHERE CONCAT_WS(' ', first_name, middle_initial, last_name) LIKE '%�%';

SELECT 'legacy_passwords_need_reset' AS check_name,
       COUNT(*) = 0 AS passed,
       COUNT(*) AS observed
FROM performance_assessment_db.user
WHERE password NOT LIKE '$2a$%'
  AND password NOT LIKE '$2b$%'
  AND password NOT LIKE '$2y$%';

SELECT 'legacy_interventions_without_skill_mapping' AS check_name,
       COUNT(*) = 0 AS passed,
       COUNT(*) AS observed
FROM performance_assessment_db.intervention;
