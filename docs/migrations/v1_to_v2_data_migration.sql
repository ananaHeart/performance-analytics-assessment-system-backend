-- Performance Analytics Assessment System
-- V1 -> V2 local migration rehearsal.
--
-- SAFETY: This script always ends with ROLLBACK. It does not modify V1 and it
-- leaves V2 unchanged. Change ROLLBACK to COMMIT only after an approved staging
-- rehearsal, resolved preflight blockers, backup verification, and sign-off.

SET NAMES utf8mb4;
USE performance_assessment_v2_db;

SELECT 'migration_mode' AS report, 'REHEARSAL_ONLY_FINAL_ROLLBACK' AS value;
SELECT 'source' AS report, 'performance_assessment_db' AS value;
SELECT 'target' AS report, 'performance_assessment_v2_db' AS value;

START TRANSACTION;

-- Stable helpers used only for this transaction.
CREATE TEMPORARY TABLE tmp_sequence (item_number INT UNSIGNED PRIMARY KEY);
INSERT INTO tmp_sequence (item_number)
SELECT ones.n + (tens.n * 10) + 1
FROM (
    SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) ones
CROSS JOIN (
    SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) tens;

CREATE TEMPORARY TABLE tmp_subject_map AS
SELECT old_subject.subject_id AS v1_subject_id,
       MIN(new_subject.subject_id) AS v2_subject_id
FROM performance_assessment_db.subject old_subject
JOIN performance_assessment_v2_db.subjects new_subject
  ON LOWER(TRIM(new_subject.subject_name)) = LOWER(TRIM(old_subject.subject_name))
GROUP BY old_subject.subject_id;

CREATE TEMPORARY TABLE tmp_class_map AS
SELECT old_class.class_id AS v1_class_id,
       cohort.v2_class_id
FROM performance_assessment_db.class old_class
JOIN (
    SELECT academic_year_id, section_id, MIN(class_id) AS v2_class_id
    FROM performance_assessment_db.class
    GROUP BY academic_year_id, section_id
) cohort
  ON cohort.academic_year_id = old_class.academic_year_id
 AND cohort.section_id = old_class.section_id;

-- Placeholder addresses preserve ownership without pretending that V1 held
-- complete residential data. All placeholders require review before cutover.
INSERT INTO performance_assessment_v2_db.addresses (
    address_id, country_code, region_name, address_line, address_source
)
SELECT 1, 'PH', region_division, 'Legacy school address pending review', 'manual'
FROM performance_assessment_db.school_profile;

INSERT INTO performance_assessment_v2_db.addresses (
    address_id, country_code, address_line, address_source
)
SELECT 1000000 + user_id,
       'PH',
       CONCAT('Legacy user address pending review: user ', user_id),
       'manual'
FROM performance_assessment_db.user;

INSERT INTO performance_assessment_v2_db.addresses (
    address_id, country_code, address_line, address_source
)
SELECT 2000000 + student_id,
       'PH',
       CONCAT('Legacy student address pending review: student ', student_id),
       'manual'
FROM performance_assessment_db.student;

INSERT INTO performance_assessment_v2_db.school_profiles (
    school_id, address_id, school_name, contact_number, email
)
SELECT school_id, 1, school_name, NULL, NULL
FROM performance_assessment_db.school_profile;

-- Keep the V1 curriculum distinct from the pre-seeded V2 MATATAG reference.
INSERT INTO performance_assessment_v2_db.curriculums (
    curriculum_id, curriculum_name, version, description, status, created_at
)
SELECT 2,
       curriculum_name,
       COALESCE(NULLIF(TRIM(version), ''), 'Legacy'),
       'Migrated V1 curriculum. Confirm version before production use.',
       CASE WHEN status = 'Active' THEN 'active' ELSE 'inactive' END,
       created_at
FROM performance_assessment_db.curriculum
ORDER BY curriculum_id
LIMIT 1;

INSERT INTO performance_assessment_v2_db.academic_years (
    academic_year_id, curriculum_id, year_name, start_date, end_date, status
)
SELECT academic_year_id,
       2,
       year_name,
       start_date,
       end_date,
       CASE WHEN status = 'Completed' THEN 'completed' ELSE 'active' END
FROM performance_assessment_db.academic_year;

INSERT INTO performance_assessment_v2_db.term_periods (
    term_period_id, academic_year_id, term_name, term_order, status
)
SELECT grading_period_id,
       academic_year_id,
       period_name,
       CAST(period_order AS UNSIGNED),
       CASE WHEN status = 'Completed' THEN 'completed' ELSE 'active' END
FROM performance_assessment_db.grading_period;

-- All current V1 tests have no grading period. This explicit historical bucket
-- prevents an invented assignment to a real term.
INSERT INTO performance_assessment_v2_db.term_periods (
    term_period_id, academic_year_id, term_name, term_order, status
)
SELECT 9001,
       academic_year_id,
       'Legacy Unassigned',
       255,
       'completed'
FROM performance_assessment_db.academic_year
ORDER BY academic_year_id
LIMIT 1;

INSERT INTO performance_assessment_v2_db.sections (
    section_id, grade_level_id, section_name
)
SELECT section_id, grade_level_id, section_name
FROM performance_assessment_db.section;

-- Legacy accounts are intentionally pending. Their plaintext/unknown passwords
-- are never promoted into an active V2 credential.
INSERT INTO performance_assessment_v2_db.users (
    user_id,
    school_id,
    address_id,
    gender_id,
    major_id,
    educational_attainment_id,
    role_id,
    status_id,
    first_name,
    middle_name,
    last_name,
    suffix,
    birth_date,
    teaching_start_date,
    email,
    contact_number,
    password_hash,
    created_at
)
SELECT old_user.user_id,
       school.school_id,
       1000000 + old_user.user_id,
       gender_ref.gender_id,
       NULL,
       NULL,
       role_ref.role_id,
       pending_status.status_id,
       old_user.first_name,
       old_user.middle_initial,
       old_user.last_name,
       NULL,
       old_user.date_birth,
       NULL,
       old_user.email,
       CONCAT('LEGACY-', old_user.user_id),
       CONCAT('LEGACY_RESET_REQUIRED:', old_user.user_id),
       old_user.created_at
FROM performance_assessment_db.user old_user
CROSS JOIN performance_assessment_db.school_profile school
JOIN performance_assessment_v2_db.genders gender_ref
  ON LOWER(gender_ref.gender_name) = LOWER(old_user.gender)
JOIN performance_assessment_v2_db.roles role_ref
  ON role_ref.role_name = old_user.role
JOIN performance_assessment_v2_db.statuses pending_status
  ON pending_status.status_name = 'pending';

-- One V2 class represents one section/year cohort. V1 subject/teacher rows move
-- to class_assignments instead of duplicating the cohort.
INSERT INTO performance_assessment_v2_db.classes (
    class_id, academic_year_id, section_id, status, created_at
)
SELECT MIN(class_id), academic_year_id, section_id, 'active', MIN(assigned_at)
FROM performance_assessment_db.class
GROUP BY academic_year_id, section_id;

INSERT INTO performance_assessment_v2_db.class_assignments (
    class_assignment_id,
    class_id,
    user_id,
    subject_id,
    assignment_role,
    assigned_at,
    status,
    created_at,
    updated_at
)
SELECT old_class.class_id,
       class_map.v2_class_id,
       old_class.user_id,
       subject_map.v2_subject_id,
       'primary',
       old_class.assigned_at,
       'active',
       old_class.assigned_at,
       old_class.assigned_at
FROM performance_assessment_db.class old_class
JOIN tmp_class_map class_map ON class_map.v1_class_id = old_class.class_id
JOIN tmp_subject_map subject_map ON subject_map.v1_subject_id = old_class.subject_id;

INSERT INTO performance_assessment_v2_db.students (
    student_id,
    school_id,
    address_id,
    gender_id,
    student_lrn,
    first_name,
    middle_name,
    last_name,
    suffix,
    birth_date,
    status
)
SELECT old_student.student_id,
       school.school_id,
       2000000 + old_student.student_id,
       gender_ref.gender_id,
       old_student.student_lrn,
       old_student.first_name,
       NULL,
       old_student.last_name,
       NULL,
       NULL,
       'active'
FROM performance_assessment_db.student old_student
CROSS JOIN performance_assessment_db.school_profile school
JOIN performance_assessment_v2_db.genders gender_ref
  ON LOWER(gender_ref.gender_name) = LOWER(old_student.gender);

INSERT INTO performance_assessment_v2_db.class_lists (
    class_list_id, class_id, student_id
)
SELECT enrollment.student_enrollment_id,
       cohort.v2_class_id,
       enrollment.student_id
FROM performance_assessment_db.student_enrollment enrollment
JOIN (
    SELECT academic_year_id, section_id, MIN(class_id) AS v2_class_id
    FROM performance_assessment_db.class
    GROUP BY academic_year_id, section_id
) cohort
  ON cohort.academic_year_id = enrollment.academic_year_id
 AND cohort.section_id = enrollment.section_id;

-- V1 root competencies become root tags; all V1 competency IDs remain stable.
INSERT INTO performance_assessment_v2_db.root_tags (
    root_tag_id, curriculum_id, root_tag_name, description, status
)
SELECT competency_id,
       2,
       competency_name,
       'Migrated V1 root competency; description pending curriculum review.',
       'active'
FROM performance_assessment_db.competency_tags
WHERE parent_competency_id IS NULL;

INSERT INTO performance_assessment_v2_db.competency_tags (
    competency_id, root_tag_id, competency_name
)
SELECT competency_id,
       COALESCE(parent_competency_id, competency_id),
       competency_name
FROM performance_assessment_db.competency_tags;

-- V1 has no term-specific skill entity. The migration creates a legacy skill
-- context without claiming that it belongs to a real grading period.
INSERT INTO performance_assessment_v2_db.skills (
    skill_id, competency_id, term_period_id, grade_level_id, subject_id
)
SELECT old_competency.competency_id,
       old_competency.competency_id,
       9001,
       old_competency.grade_level_id,
       subject_map.v2_subject_id
FROM performance_assessment_db.competency_tags old_competency
JOIN tmp_subject_map subject_map
  ON subject_map.v1_subject_id = old_competency.subject_id;

INSERT INTO performance_assessment_v2_db.tests (
    test_id,
    class_assignment_id,
    term_period_id,
    test_name,
    test_type,
    test_date,
    instructions,
    total_items,
    status
)
SELECT old_test.test_id,
       old_test.class_id,
       COALESCE(old_test.grading_period_id, 9001),
       old_test.test_name,
       CASE old_test.test_type
           WHEN 'Quiz' THEN 'quiz'
           WHEN 'Exam' THEN 'exam'
           WHEN 'Long Test' THEN 'long_test'
           ELSE 'other'
       END,
       old_test.test_date,
       CASE
           WHEN EXISTS (
               SELECT 1
               FROM performance_assessment_db.test_part unsupported_part
               WHERE unsupported_part.test_id = old_test.test_id
                 AND LOWER(unsupported_part.part_type) NOT IN ('multiple choice', 'true or false')
           ) THEN 'Legacy assessment includes a non-bubble part; manual review required.'
           ELSE 'Migrated V1 assessment; original question text/options unavailable.'
       END,
       (SELECT COALESCE(SUM(part.number_of_items), 0)
        FROM performance_assessment_db.test_part part
        WHERE part.test_id = old_test.test_id),
       CASE
           WHEN EXISTS (
               SELECT 1
               FROM performance_assessment_db.test_part unsupported_part
               WHERE unsupported_part.test_id = old_test.test_id
                 AND LOWER(unsupported_part.part_type) NOT IN ('multiple choice', 'true or false')
           ) THEN 'archived'
           WHEN old_test.test_status = 'Completed' THEN 'completed'
           WHEN old_test.test_status = 'Active' THEN 'active'
           ELSE 'draft'
       END
FROM performance_assessment_db.test old_test;

INSERT INTO performance_assessment_v2_db.test_parts (
    test_part_id,
    test_id,
    part_order,
    part_name,
    part_type,
    number_of_items,
    points_per_item
)
SELECT test_part_id,
       test_id,
       CAST(REPLACE(LOWER(part_order), 'part ', '') AS UNSIGNED),
       part_order,
       CASE LOWER(part_type)
           WHEN 'multiple choice' THEN 'multiple_choice'
           WHEN 'true or false' THEN 'true_false'
           WHEN 'identification' THEN 'identification'
           ELSE LOWER(REPLACE(TRIM(part_type), ' ', '_'))
       END,
       number_of_items,
       points_per_item
FROM performance_assessment_db.test_part;

-- V1 has no question text or options. Placeholders are explicit and every
-- generated item retains a deterministic part-local identity.
INSERT INTO performance_assessment_v2_db.questions (
    question_id,
    test_part_id,
    item_number,
    question_text,
    option_a,
    option_b,
    option_c,
    option_d,
    option_e
)
SELECT (old_part.test_part_id * 100000) + sequence.item_number,
       old_part.test_part_id,
       sequence.item_number,
       CONCAT('[LEGACY QUESTION TEXT UNAVAILABLE] Part ', old_part.test_part_id,
              ', item ', sequence.item_number),
       CASE WHEN LOWER(old_part.part_type) = 'true or false' THEN 'True' ELSE '[Legacy option A unavailable]' END,
       CASE WHEN LOWER(old_part.part_type) = 'true or false' THEN 'False' ELSE '[Legacy option B unavailable]' END,
       '[Legacy option C unavailable]',
       '[Legacy option D unavailable]',
       NULL
FROM performance_assessment_db.test_part old_part
JOIN tmp_sequence sequence ON sequence.item_number <= old_part.number_of_items;

-- Full-text Identification keys cannot fit V2 correct_option CHAR(1). They are
-- intentionally excluded and their parent test is archived for manual review.
INSERT INTO performance_assessment_v2_db.answer_keys (
    answer_key_id, question_id, correct_option
)
SELECT (old_part.test_part_id * 100000) + sequence.item_number,
       (old_part.test_part_id * 100000) + sequence.item_number,
       CASE
           WHEN LOWER(old_part.part_type) = 'true or false'
                AND LOWER(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(old_part.answer_key, ',', sequence.item_number), ',', -1))) = 'true'
               THEN 'A'
           WHEN LOWER(old_part.part_type) = 'true or false'
               THEN 'B'
           ELSE UPPER(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(old_part.answer_key, ',', sequence.item_number), ',', -1)))
       END
FROM performance_assessment_db.test_part old_part
JOIN tmp_sequence sequence ON sequence.item_number <= old_part.number_of_items
WHERE LOWER(old_part.part_type) IN ('multiple choice', 'true or false');

-- Range-based V1 UI mappings become normalized question-to-skill mappings.
INSERT INTO performance_assessment_v2_db.mappings (
    question_id, skill_id
)
SELECT DISTINCT
       new_question.question_id,
       COALESCE(old_mapping.competency_id, old_part.competency_id)
FROM performance_assessment_v2_db.questions new_question
JOIN performance_assessment_db.test_part old_part
  ON old_part.test_part_id = new_question.test_part_id
LEFT JOIN performance_assessment_db.part_skill_mapping old_mapping
  ON old_mapping.test_part_id = old_part.test_part_id
 AND new_question.item_number BETWEEN old_mapping.start_item AND old_mapping.end_item;

INSERT INTO performance_assessment_v2_db.test_results (
    test_result_id,
    result_uuid,
    test_id,
    class_list_id,
    attempt_number,
    total_score,
    max_score,
    items_evaluated,
    checked_at,
    created_at,
    updated_at
)
SELECT old_result.test_result_id,
       CONCAT('00000000-0000-0000-0000-', LPAD(old_result.test_result_id, 12, '0')),
       old_result.test_id,
       enrollment.student_enrollment_id,
       1,
       old_result.total_score,
       (SELECT SUM(part.number_of_items * part.points_per_item)
        FROM performance_assessment_db.test_part part
        WHERE part.test_id = old_result.test_id),
       (SELECT SUM(part.number_of_items)
        FROM performance_assessment_db.test_part part
        WHERE part.test_id = old_result.test_id),
       old_result.checked_at,
       old_result.checked_at,
       old_result.checked_at
FROM performance_assessment_db.test_result old_result
JOIN performance_assessment_db.test old_test ON old_test.test_id = old_result.test_id
JOIN performance_assessment_db.class old_class ON old_class.class_id = old_test.class_id
JOIN performance_assessment_db.student_enrollment enrollment
  ON enrollment.student_id = old_result.student_id
 AND enrollment.section_id = old_class.section_id
 AND enrollment.academic_year_id = old_class.academic_year_id;

-- V1 raw_answers records correctness flags, not the actual selected option.
-- Preserve correctness and points honestly; mark the unavailable answer invalid.
INSERT INTO performance_assessment_v2_db.student_answers (
    student_answer_id,
    test_result_id,
    question_id,
    verified_by_user_id,
    answer_uuid,
    capture_source,
    verified_at,
    selected_option,
    answer_status,
    is_correct,
    points_earned,
    correction_reason,
    updated_at
)
SELECT old_item.item_result_id,
       old_item.test_result_id,
       (old_item.test_part_id * 100000) + old_item.item_number,
       old_class.user_id,
       CONCAT('10000000-0000-0000-0000-', LPAD(old_item.item_result_id, 12, '0')),
       'manual',
       old_result.checked_at,
       NULL,
       'invalid',
       old_item.is_correct,
       CASE WHEN old_item.is_correct = 1 THEN old_part.points_per_item ELSE 0 END,
       'Migrated V1 correctness only; original selected answer was not stored.',
       old_result.checked_at
FROM performance_assessment_db.test_item_result old_item
JOIN performance_assessment_db.test_result old_result
  ON old_result.test_result_id = old_item.test_result_id
JOIN performance_assessment_db.test old_test ON old_test.test_id = old_result.test_id
JOIN performance_assessment_db.class old_class ON old_class.class_id = old_test.class_id
JOIN performance_assessment_db.test_part old_part ON old_part.test_part_id = old_item.test_part_id;

INSERT INTO performance_assessment_v2_db.syncs (
    sync_id,
    sync_uuid,
    user_id,
    test_id,
    device_identifier,
    direction,
    sync_status,
    payload_hash,
    started_at,
    completed_at,
    error_message
)
SELECT old_sync.sync_id,
       CONCAT('20000000-0000-0000-0000-', LPAD(old_sync.sync_id, 12, '0')),
       old_sync.user_id,
       old_sync.test_id,
       NULL,
       'upload',
       CASE WHEN old_sync.sync_status = 'Success' THEN 'success' ELSE 'failed' END,
       NULL,
       old_sync.sync_timestamp,
       old_sync.sync_timestamp,
       CASE WHEN old_sync.sync_status = 'Failed' THEN 'Migrated V1 failed sync; original error unavailable.' ELSE NULL END
FROM performance_assessment_db.sync_log old_sync;

INSERT INTO performance_assessment_v2_db.sync_items (
    sync_id,
    result_uuid,
    test_result_id,
    sync_action,
    sync_status,
    error_code,
    error_message,
    synced_at
)
SELECT old_sync.sync_id,
       new_result.result_uuid,
       new_result.test_result_id,
       'create',
       CASE WHEN old_sync.sync_status = 'Success' THEN 'success' ELSE 'failed' END,
       CASE WHEN old_sync.sync_status = 'Failed' THEN 'LEGACY_SYNC_FAILED' ELSE NULL END,
       CASE WHEN old_sync.sync_status = 'Failed' THEN 'Original V1 error detail unavailable.' ELSE NULL END,
       old_sync.sync_timestamp
FROM performance_assessment_db.sync_log old_sync
JOIN performance_assessment_v2_db.test_results new_result
  ON new_result.test_id = old_sync.test_id;

-- V1 interventions have no skill foreign key. Do not invent mappings.
SELECT 'manual_review_non_bubble_parts' AS report,
       COUNT(*) AS observed
FROM performance_assessment_db.test_part
WHERE LOWER(part_type) NOT IN ('multiple choice', 'true or false');

SELECT 'manual_review_legacy_interventions' AS report,
       COUNT(*) AS observed
FROM performance_assessment_db.intervention;

SELECT 'manual_review_password_resets' AS report,
       COUNT(*) AS observed
FROM performance_assessment_db.user;

SELECT 'rehearsal_counts_before_rollback' AS report,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.users) AS users,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.students) AS students,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.classes) AS classes,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.class_assignments) AS class_assignments,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.class_lists) AS class_lists,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.tests) AS tests,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.test_parts) AS test_parts,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.questions) AS questions,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.answer_keys) AS answer_keys,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.test_results) AS test_results,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.student_answers) AS student_answers,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.syncs) AS sync_batches,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.sync_items) AS sync_items;

SELECT 'rehearsal_integrity_before_rollback' AS report,
       (SELECT COUNT(*)
        FROM performance_assessment_v2_db.test_results
        WHERE total_score > max_score OR total_score < 0 OR max_score <= 0) AS invalid_scores,
       (SELECT COUNT(*)
        FROM performance_assessment_v2_db.student_answers answer
        LEFT JOIN performance_assessment_v2_db.questions question
          ON question.question_id = answer.question_id
        WHERE question.question_id IS NULL) AS orphan_answers,
       (SELECT COUNT(*)
        FROM performance_assessment_v2_db.test_results result_row
        LEFT JOIN performance_assessment_v2_db.class_lists class_member
          ON class_member.class_list_id = result_row.class_list_id
        WHERE class_member.class_list_id IS NULL) AS orphan_result_memberships,
       (SELECT COUNT(*)
        FROM performance_assessment_v2_db.users
        WHERE status_id <> (SELECT status_id FROM performance_assessment_v2_db.statuses WHERE status_name = 'pending'))
        AS unexpectedly_active_legacy_users;

ROLLBACK;

SELECT 'post_rollback_operational_counts' AS report,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.users) AS users,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.students) AS students,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.tests) AS tests,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.test_results) AS test_results,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.student_answers) AS student_answers,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.syncs) AS sync_batches;
