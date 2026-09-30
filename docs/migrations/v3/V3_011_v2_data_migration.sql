-- V3_011: Controlled operational-data migration from V2 to the isolated V3 database.
-- Preconditions:
--   1. Rebuild performance_assessment_v3_db from performance_assessment_v3_schema.sql.
--   2. Apply V3_004_reference_seed.sql.
--   3. Run V3_010_v2_data_preflight.sql successfully.
-- Safety: the V2 database is read only in this script.

USE performance_assessment_v3_db;
SET time_zone = '+00:00';

START TRANSACTION;

-- Stable reference records. Existing V3 seed rows are updated by natural primary key.
INSERT INTO genders (gender_id, gender_name, description)
SELECT gender_id, gender_name, description
FROM performance_assessment_v2_db.genders
ON DUPLICATE KEY UPDATE
    gender_name = VALUES(gender_name),
    description = VALUES(description);

INSERT INTO majors (major_id, major_name)
SELECT major_id, major_name
FROM performance_assessment_v2_db.majors
ON DUPLICATE KEY UPDATE major_name = VALUES(major_name);

INSERT INTO educational_attainments (
    educational_attainment_id, attainment_name, attainment_order, description, is_active
)
SELECT educational_attainment_id, attainment_name, attainment_order, description, is_active
FROM performance_assessment_v2_db.educational_attainments
ON DUPLICATE KEY UPDATE
    attainment_name = VALUES(attainment_name),
    attainment_order = VALUES(attainment_order),
    description = VALUES(description),
    is_active = VALUES(is_active);

INSERT INTO suffixes (suffix_id, suffix_name, display_order, is_active)
SELECT suffix_id, suffix_name, display_order, is_active
FROM performance_assessment_v2_db.suffixes
ON DUPLICATE KEY UPDATE
    suffix_name = VALUES(suffix_name),
    display_order = VALUES(display_order),
    is_active = VALUES(is_active);

INSERT INTO roles (role_id, role_name)
SELECT role_id, role_name
FROM performance_assessment_v2_db.roles
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

-- V2 account statuses are intentionally not copied by ID. V3 has a six-state workflow.

INSERT INTO curriculums (curriculum_id, curriculum_name, version, description, status, created_at)
SELECT curriculum_id, curriculum_name, version, description, status, created_at
FROM performance_assessment_v2_db.curriculums
ON DUPLICATE KEY UPDATE
    curriculum_name = VALUES(curriculum_name),
    version = VALUES(version),
    description = VALUES(description),
    status = VALUES(status),
    created_at = VALUES(created_at);

INSERT INTO grade_levels (grade_level_id, grade_level_name)
SELECT grade_level_id, grade_level_name
FROM performance_assessment_v2_db.grade_levels
ON DUPLICATE KEY UPDATE grade_level_name = VALUES(grade_level_name);

INSERT INTO subjects (subject_id, subject_code, subject_name)
SELECT subject_id, subject_code, subject_name
FROM performance_assessment_v2_db.subjects
ON DUPLICATE KEY UPDATE
    subject_code = VALUES(subject_code),
    subject_name = VALUES(subject_name);

INSERT INTO addresses (
    address_id, country_code, region_code, region_name, province_code, province_name,
    city_municipality_code, city_municipality_name, barangay_code, barangay_name,
    address_line, postal_code, address_source, created_at, updated_at
)
SELECT
    address_id, country_code, region_code, region_name, province_code, province_name,
    city_municipality_code, city_municipality_name, barangay_code, barangay_name,
    address_line, postal_code, address_source, created_at, updated_at
FROM performance_assessment_v2_db.addresses;

INSERT INTO school_profiles (school_id, address_id, school_name, contact_number, email, created_at)
SELECT school_id, address_id, school_name, contact_number, email, created_at
FROM performance_assessment_v2_db.school_profiles;

INSERT INTO academic_years (
    academic_year_id, curriculum_id, year_name, start_date, end_date, status, created_at, updated_at
)
SELECT academic_year_id, curriculum_id, year_name, start_date, end_date, status, created_at, updated_at
FROM performance_assessment_v2_db.academic_years;

INSERT INTO sections (section_id, grade_level_id, section_name)
SELECT section_id, grade_level_id, section_name
FROM performance_assessment_v2_db.sections;

-- Account status mapping:
-- pending + unverified email -> pending_email_verification
-- pending + verified email   -> pending_approval
-- active/rejected/inactive/locked preserve their meaning using V3 status names.
INSERT INTO users (
    user_id, school_id, address_id, gender_id, major_id, educational_attainment_id,
    role_id, status_id, first_name, middle_name, last_name, suffix_id, birth_date,
    teaching_start_month, teaching_start_year, email, contact_number, password_hash,
    failed_login_count, locked_until_at, last_login_at, password_changed_at,
    email_verified_at, contact_verified_at, mfa_required, created_at, updated_at
)
SELECT
    u.user_id,
    u.school_id,
    u.address_id,
    u.gender_id,
    u.major_id,
    u.educational_attainment_id,
    u.role_id,
    CASE old_status.status_name
        WHEN 'pending' THEN CASE
            WHEN u.email_verified_at IS NULL THEN
                (SELECT status_id FROM statuses WHERE status_name = 'pending_email_verification')
            ELSE
                (SELECT status_id FROM statuses WHERE status_name = 'pending_approval')
        END
        WHEN 'active' THEN (SELECT status_id FROM statuses WHERE status_name = 'active')
        WHEN 'rejected' THEN (SELECT status_id FROM statuses WHERE status_name = 'rejected')
        WHEN 'inactive' THEN (SELECT status_id FROM statuses WHERE status_name = 'inactive')
        WHEN 'locked' THEN (SELECT status_id FROM statuses WHERE status_name = 'locked')
    END,
    u.first_name,
    u.middle_name,
    u.last_name,
    suffix_ref.suffix_id,
    u.birth_date,
    MONTH(u.teaching_start_date),
    YEAR(u.teaching_start_date),
    LOWER(TRIM(u.email)),
    u.contact_number,
    u.password_hash,
    u.failed_login_count,
    u.locked_until_at,
    u.last_login_at,
    u.password_changed_at,
    u.email_verified_at,
    u.contact_verified_at,
    FALSE,
    u.created_at,
    u.updated_at
FROM performance_assessment_v2_db.users u
JOIN performance_assessment_v2_db.statuses old_status
    ON old_status.status_id = u.status_id
LEFT JOIN suffixes suffix_ref
    ON LOWER(suffix_ref.suffix_name) = LOWER(TRIM(u.suffix));

-- V2 did not store term boundaries. Deterministic three-month windows are derived from
-- the academic-year start; the final term ends one day after academic_years.end_date.
INSERT INTO term_periods (
    term_period_id, academic_year_id, term_name, term_order, start_at, end_at,
    status, activation_mode, activated_at, completed_at, overridden_by_user_id,
    override_reason, overridden_at, created_at, updated_at
)
SELECT
    tp.term_period_id,
    tp.academic_year_id,
    tp.term_name,
    tp.term_order,
    TIMESTAMP(DATE_ADD(ay.start_date, INTERVAL ((tp.term_order - 1) * 3) MONTH)),
    CASE
        WHEN tp.term_order = max_term.max_term_order
            THEN TIMESTAMP(DATE_ADD(ay.end_date, INTERVAL 1 DAY))
        ELSE TIMESTAMP(DATE_ADD(ay.start_date, INTERVAL (tp.term_order * 3) MONTH))
    END,
    tp.status,
    'automatic',
    CASE WHEN tp.status = 'active' THEN TIMESTAMP(ay.start_date) ELSE NULL END,
    CASE WHEN tp.status = 'completed'
        THEN TIMESTAMP(DATE_ADD(ay.start_date, INTERVAL (tp.term_order * 3) MONTH))
        ELSE NULL
    END,
    NULL,
    NULL,
    NULL,
    ay.created_at,
    ay.updated_at
FROM performance_assessment_v2_db.term_periods tp
JOIN performance_assessment_v2_db.academic_years ay
    ON ay.academic_year_id = tp.academic_year_id
JOIN (
    SELECT academic_year_id, MAX(term_order) AS max_term_order
    FROM performance_assessment_v2_db.term_periods
    GROUP BY academic_year_id
) max_term ON max_term.academic_year_id = tp.academic_year_id;

INSERT INTO students (
    student_id, school_id, address_id, gender_id, student_lrn, first_name,
    middle_name, last_name, suffix_id, birth_date, status, status_reason,
    status_effective_at, status_changed_by_user_id, created_at, updated_at
)
SELECT
    s.student_id,
    s.school_id,
    s.address_id,
    s.gender_id,
    s.student_lrn,
    s.first_name,
    s.middle_name,
    s.last_name,
    suffix_ref.suffix_id,
    s.birth_date,
    s.status,
    NULL,
    NULL,
    NULL,
    s.created_at,
    s.created_at
FROM performance_assessment_v2_db.students s
LEFT JOIN suffixes suffix_ref
    ON LOWER(suffix_ref.suffix_name) = LOWER(TRIM(s.suffix));

INSERT INTO classes (class_id, academic_year_id, section_id, status, created_at, updated_at)
SELECT class_id, academic_year_id, section_id, status, created_at, updated_at
FROM performance_assessment_v2_db.classes;

INSERT INTO class_assignments (
    class_assignment_id, class_id, user_id, subject_id, assignment_role, assigned_at,
    ended_at, status, status_changed_by_user_id, status_reason, created_at, updated_at
)
SELECT
    class_assignment_id,
    class_id,
    user_id,
    subject_id,
    assignment_role,
    assigned_at,
    CASE WHEN status = 'archived' THEN updated_at ELSE NULL END,
    status,
    NULL,
    CASE WHEN status = 'archived' THEN 'Migrated from an archived V2 assignment.' ELSE NULL END,
    created_at,
    updated_at
FROM performance_assessment_v2_db.class_assignments;

-- Preserve every legacy membership ID for result traceability. Only one membership per
-- learner/year remains enrolled: result-bearing membership first, then newest legacy row.
DROP TEMPORARY TABLE IF EXISTS v3_membership_ranking;
CREATE TEMPORARY TABLE v3_membership_ranking AS
SELECT
    cl.class_list_id,
    cl.student_id,
    c.academic_year_id,
    ROW_NUMBER() OVER (
        PARTITION BY cl.student_id, c.academic_year_id
        ORDER BY
            EXISTS (
                SELECT 1
                FROM performance_assessment_v2_db.test_results tr
                WHERE tr.class_list_id = cl.class_list_id
            ) DESC,
            cl.class_list_id DESC
    ) AS membership_rank
FROM performance_assessment_v2_db.class_lists cl
JOIN performance_assessment_v2_db.classes c ON c.class_id = cl.class_id;

INSERT INTO class_lists (
    class_list_id, membership_uuid, class_id, student_id, academic_year_id,
    enrollment_status, enrollment_source, source_sf1_import_id, previous_class_list_id,
    enrolled_at, ended_at, status_reason, status_changed_by_user_id, created_at, updated_at
)
SELECT
    cl.class_list_id,
    CONCAT('10000000-0000-4000-8000-', LPAD(cl.class_list_id, 12, '0')),
    cl.class_id,
    cl.student_id,
    ranking.academic_year_id,
    CASE WHEN ranking.membership_rank = 1 THEN 'enrolled' ELSE 'transferred' END,
    'manual',
    NULL,
    NULL,
    c.created_at,
    CASE WHEN ranking.membership_rank = 1 THEN NULL ELSE c.updated_at END,
    CASE WHEN ranking.membership_rank = 1 THEN NULL
        ELSE 'Legacy duplicate membership closed during V3 migration.'
    END,
    NULL,
    c.created_at,
    c.updated_at
FROM performance_assessment_v2_db.class_lists cl
JOIN performance_assessment_v2_db.classes c ON c.class_id = cl.class_id
JOIN v3_membership_ranking ranking ON ranking.class_list_id = cl.class_list_id;

DROP TEMPORARY TABLE v3_membership_ranking;

INSERT INTO root_tags (root_tag_id, curriculum_id, root_tag_name, description, status, created_at)
SELECT root_tag_id, curriculum_id, root_tag_name, description, status, created_at
FROM performance_assessment_v2_db.root_tags;

INSERT INTO competency_tags (competency_id, root_tag_id, competency_name)
SELECT competency_id, root_tag_id, competency_name
FROM performance_assessment_v2_db.competency_tags;

INSERT INTO skills (skill_id, competency_id, term_period_id, grade_level_id, subject_id)
SELECT skill_id, competency_id, term_period_id, grade_level_id, subject_id
FROM performance_assessment_v2_db.skills;

INSERT INTO interventions (intervention_id, skill_id, intervention_type, description)
SELECT intervention_id, skill_id, intervention_type, description
FROM performance_assessment_v2_db.interventions;

-- V2 test ownership is lifted from class assignment into reusable assessment content.
INSERT INTO tests (
    test_id, test_uuid, school_id, created_by_user_id, version_number, source_test_id,
    term_period_id, test_name, test_type, instructions, total_items, status,
    published_at, content_locked_at, completed_at, archived_at, created_at, updated_at
)
SELECT
    t.test_id,
    CONCAT('20000000-0000-4000-8000-', LPAD(t.test_id, 12, '0')),
    u.school_id,
    ca.user_id,
    1,
    NULL,
    t.term_period_id,
    t.test_name,
    t.test_type,
    t.instructions,
    t.total_items,
    t.status,
    CASE WHEN t.status IN ('active', 'completed', 'archived') THEN t.updated_at ELSE NULL END,
    CASE WHEN t.status IN ('active', 'completed', 'archived') THEN t.updated_at ELSE NULL END,
    CASE WHEN t.status = 'completed' THEN t.updated_at ELSE NULL END,
    CASE WHEN t.status = 'archived' THEN t.updated_at ELSE NULL END,
    t.created_at,
    t.updated_at
FROM performance_assessment_v2_db.tests t
JOIN performance_assessment_v2_db.class_assignments ca
    ON ca.class_assignment_id = t.class_assignment_id
JOIN performance_assessment_v2_db.users u ON u.user_id = ca.user_id;

INSERT INTO test_assignments (
    test_assignment_id, assignment_uuid, test_id, class_assignment_id,
    assigned_by_user_id, open_at, close_at, assignment_status,
    allow_late_capture, assigned_at, updated_at
)
SELECT
    t.test_id,
    CONCAT('30000000-0000-4000-8000-', LPAD(t.test_id, 12, '0')),
    t.test_id,
    t.class_assignment_id,
    ca.user_id,
    TIMESTAMP(t.test_date),
    NULL,
    CASE t.status
        WHEN 'draft' THEN 'planned'
        WHEN 'active' THEN 'open'
        WHEN 'completed' THEN 'closed'
        WHEN 'archived' THEN 'archived'
    END,
    FALSE,
    t.created_at,
    t.updated_at
FROM performance_assessment_v2_db.tests t
JOIN performance_assessment_v2_db.class_assignments ca
    ON ca.class_assignment_id = t.class_assignment_id;

INSERT INTO test_parts (
    test_part_id, test_id, part_order, part_name, question_type_id,
    number_of_items, points_per_item, part_instructions, created_at, updated_at
)
SELECT
    tp.test_part_id,
    tp.test_id,
    tp.part_order,
    tp.part_name,
    qt.question_type_id,
    tp.number_of_items,
    tp.points_per_item,
    NULL,
    t.created_at,
    t.updated_at
FROM performance_assessment_v2_db.test_parts tp
JOIN question_types qt ON qt.question_type_code = tp.part_type
JOIN performance_assessment_v2_db.tests t ON t.test_id = tp.test_id;

INSERT INTO questions (
    question_id, question_uuid, test_part_id, question_type_id, item_number,
    question_text, maximum_points, rubric_id, response_instructions,
    answer_order_required, maximum_response_length, created_at, updated_at
)
SELECT
    q.question_id,
    CONCAT('40000000-0000-4000-8000-', LPAD(q.question_id, 12, '0')),
    q.test_part_id,
    tp.question_type_id,
    q.item_number,
    q.question_text,
    tp.points_per_item,
    NULL,
    NULL,
    FALSE,
    NULL,
    tp.created_at,
    tp.updated_at
FROM performance_assessment_v2_db.questions q
JOIN test_parts tp ON tp.test_part_id = q.test_part_id;

-- Objective options use stable derived IDs: question_id * 10 + option order.
INSERT INTO question_options (
    question_option_id, question_id, option_key, option_text,
    option_order, is_active, created_at, updated_at
)
SELECT q.question_id * 10 + 1, q.question_id, 'A', q.option_a, 1, TRUE, t.created_at, t.updated_at
FROM performance_assessment_v2_db.questions q
JOIN performance_assessment_v2_db.test_parts tp ON tp.test_part_id = q.test_part_id
JOIN performance_assessment_v2_db.tests t ON t.test_id = tp.test_id
UNION ALL
SELECT q.question_id * 10 + 2, q.question_id, 'B', q.option_b, 2, TRUE, t.created_at, t.updated_at
FROM performance_assessment_v2_db.questions q
JOIN performance_assessment_v2_db.test_parts tp ON tp.test_part_id = q.test_part_id
JOIN performance_assessment_v2_db.tests t ON t.test_id = tp.test_id
UNION ALL
SELECT q.question_id * 10 + 3, q.question_id, 'C', q.option_c, 3, TRUE, t.created_at, t.updated_at
FROM performance_assessment_v2_db.questions q
JOIN performance_assessment_v2_db.test_parts tp ON tp.test_part_id = q.test_part_id
JOIN performance_assessment_v2_db.tests t ON t.test_id = tp.test_id
UNION ALL
SELECT q.question_id * 10 + 4, q.question_id, 'D', q.option_d, 4, TRUE, t.created_at, t.updated_at
FROM performance_assessment_v2_db.questions q
JOIN performance_assessment_v2_db.test_parts tp ON tp.test_part_id = q.test_part_id
JOIN performance_assessment_v2_db.tests t ON t.test_id = tp.test_id;

INSERT INTO answer_keys (
    answer_key_id, question_id, answer_key_type, correct_question_option_id,
    scoring_method, rubric_id, answer_explanation, created_at, updated_at
)
SELECT
    ak.answer_key_id,
    ak.question_id,
    'option',
    ak.question_id * 10 + FIELD(ak.correct_option, 'A', 'B', 'C', 'D'),
    'exact',
    NULL,
    NULL,
    t.created_at,
    t.updated_at
FROM performance_assessment_v2_db.answer_keys ak
JOIN performance_assessment_v2_db.questions q ON q.question_id = ak.question_id
JOIN performance_assessment_v2_db.test_parts tp ON tp.test_part_id = q.test_part_id
JOIN performance_assessment_v2_db.tests t ON t.test_id = tp.test_id;

-- Each old question mapping becomes an exact one-item range. This is lossless and can be
-- consolidated into wider UI ranges later without changing mastery semantics.
INSERT INTO part_skill_mappings (
    part_skill_mapping_id, test_part_id, skill_id, start_item_number,
    end_item_number, item_count, created_at, updated_at
)
SELECT
    m.mapping_id,
    q.test_part_id,
    m.skill_id,
    q.item_number,
    q.item_number,
    1,
    t.created_at,
    t.updated_at
FROM performance_assessment_v2_db.mappings m
JOIN performance_assessment_v2_db.questions q ON q.question_id = m.question_id
JOIN performance_assessment_v2_db.test_parts tp ON tp.test_part_id = q.test_part_id
JOIN performance_assessment_v2_db.tests t ON t.test_id = tp.test_id;

-- Restore scan context through the authoritative V2 result-to-scan link.
INSERT INTO scan_sessions (
    scan_session_id, scan_uuid, omr_template_id, test_assignment_id, class_list_id,
    scanned_by_user_id, verified_by_user_id, device_identifier, template_version,
    scanner_version, image_hash, supersedes_scan_session_id, scan_status,
    failure_code, failure_detail, scanned_at, verified_at, created_at, updated_at
)
SELECT
    ss.scan_session_id,
    ss.scan_uuid,
    ot.omr_template_id,
    tr.test_id,
    tr.class_list_id,
    ss.scanned_by_user_id,
    ss.verified_by_user_id,
    ss.device_identifier,
    ss.template_version,
    ss.scanner_version,
    ss.image_hash,
    NULL,
    CASE ss.scan_status
        WHEN 'verified' THEN 'accepted'
        ELSE ss.scan_status
    END,
    NULL,
    NULL,
    ss.scanned_at,
    ss.verified_at,
    ss.created_at,
    ss.updated_at
FROM performance_assessment_v2_db.scan_sessions ss
JOIN performance_assessment_v2_db.test_result_scans trs
    ON trs.scan_session_id = ss.scan_session_id
JOIN performance_assessment_v2_db.test_results tr
    ON tr.test_result_id = trs.test_result_id
JOIN omr_templates ot ON ot.template_code = ss.template_version;

INSERT INTO omr_detections (
    omr_detection_id, detection_uuid, scan_session_id, question_id,
    detected_option, confidence_score, detection_status, raw_mark,
    detected_at, created_at
)
SELECT
    od.omr_detection_id,
    CONCAT('50000000-0000-4000-8000-', LPAD(od.omr_detection_id, 12, '0')),
    od.scan_session_id,
    od.question_id,
    od.detected_option,
    od.confidence_score,
    od.detection_status,
    od.raw_mark,
    od.detected_at,
    od.detected_at
FROM performance_assessment_v2_db.omr_detections od;

INSERT INTO scan_verifications (
    scan_verification_id, verification_uuid, scan_session_id, verified_by_user_id,
    verification_action, reason_code, reason_detail, decided_at, created_at
)
SELECT
    ss.scan_session_id,
    CONCAT('60000000-0000-4000-8000-', LPAD(ss.scan_session_id, 12, '0')),
    ss.scan_session_id,
    ss.verified_by_user_id,
    'accepted',
    'legacy_v2_verified',
    'Accepted scan state migrated from V2.',
    COALESCE(ss.verified_at, ss.updated_at),
    COALESCE(ss.verified_at, ss.updated_at)
FROM performance_assessment_v2_db.scan_sessions ss
WHERE ss.scan_status = 'verified' AND ss.verified_by_user_id IS NOT NULL;

INSERT INTO test_results (
    test_result_id, result_uuid, test_assignment_id, class_list_id, attempt_number,
    total_score, max_score, items_evaluated, result_status, submitted_at,
    verification_completed_at, scored_at, finalized_at, percentage_snapshot,
    performance_status, performance_rule_set_id, score_version,
    checked_at, created_at, updated_at
)
SELECT
    tr.test_result_id,
    tr.result_uuid,
    tr.test_id,
    tr.class_list_id,
    tr.attempt_number,
    tr.total_score,
    tr.max_score,
    tr.items_evaluated,
    'finalized',
    tr.created_at,
    tr.checked_at,
    tr.checked_at,
    tr.checked_at,
    CASE WHEN tr.max_score = 0 THEN 0
        ELSE ROUND((tr.total_score / tr.max_score) * 100, 4)
    END,
    NULL,
    NULL,
    1,
    tr.checked_at,
    tr.created_at,
    tr.updated_at
FROM performance_assessment_v2_db.test_results tr;

INSERT INTO student_answers (
    student_answer_id, test_result_id, question_id, verified_by_user_id,
    answer_uuid, selected_question_option_id, response_text, capture_source,
    verified_at, answer_status, evaluation_status, is_correct, points_earned,
    teacher_feedback, finalized_at, reopened_at, score_version, created_at, updated_at
)
SELECT
    sa.student_answer_id,
    sa.test_result_id,
    sa.question_id,
    sa.verified_by_user_id,
    sa.answer_uuid,
    CASE WHEN sa.selected_option IS NULL THEN NULL
        ELSE sa.question_id * 10 + FIELD(sa.selected_option, 'A', 'B', 'C', 'D')
    END,
    NULL,
    CASE sa.capture_source WHEN 'teacher_correction' THEN 'manual' ELSE sa.capture_source END,
    sa.verified_at,
    sa.answer_status,
    'finalized',
    sa.is_correct,
    sa.points_earned,
    sa.correction_reason,
    COALESCE(sa.verified_at, sa.updated_at),
    NULL,
    1,
    COALESCE(sa.verified_at, sa.updated_at),
    sa.updated_at
FROM performance_assessment_v2_db.student_answers sa;

-- Append-only migration audit. This does not permit future teacher edits to MC/TF answers.
INSERT INTO answer_verifications (
    answer_verification_id, verification_uuid, student_answer_id, verified_by_user_id,
    verification_action, previous_answer_status, previous_answer_value,
    new_answer_status, new_answer_value, previous_points, new_points,
    reason_code, reason_detail, evidence_attachment_id, verified_at
)
SELECT
    sa.student_answer_id,
    CONCAT('70000000-0000-4000-8000-', LPAD(sa.student_answer_id, 12, '0')),
    sa.student_answer_id,
    sa.verified_by_user_id,
    'finalized',
    od.detection_status,
    od.detected_option,
    sa.answer_status,
    sa.selected_option,
    NULL,
    sa.points_earned,
    CASE
        WHEN sa.capture_source = 'teacher_correction' OR od.verification_status = 'corrected'
            THEN 'legacy_v2_correction'
        ELSE 'legacy_v2_finalization'
    END,
    COALESCE(sa.correction_reason, 'Final verified answer migrated from V2.'),
    NULL,
    COALESCE(sa.verified_at, sa.updated_at)
FROM performance_assessment_v2_db.student_answers sa
JOIN performance_assessment_v2_db.test_results tr
    ON tr.test_result_id = sa.test_result_id
LEFT JOIN performance_assessment_v2_db.test_result_scans trs
    ON trs.test_result_id = tr.test_result_id AND trs.link_status = 'selected'
LEFT JOIN performance_assessment_v2_db.omr_detections od
    ON od.scan_session_id = trs.scan_session_id AND od.question_id = sa.question_id;

INSERT INTO test_result_scans (
    test_result_scan_id, test_result_id, scan_session_id, link_status,
    decided_by_user_id, decision_reason, linked_at, updated_at
)
SELECT
    test_result_scan_id,
    test_result_id,
    scan_session_id,
    link_status,
    decided_by_user_id,
    decision_reason,
    linked_at,
    updated_at
FROM performance_assessment_v2_db.test_result_scans;

INSERT INTO syncs (
    sync_id, sync_uuid, user_id, test_assignment_id, device_identifier,
    direction, sync_status, payload_hash, retry_count, last_retry_at,
    request_item_count, idempotency_version, started_at, completed_at, error_message
)
SELECT
    s.sync_id,
    s.sync_uuid,
    s.user_id,
    s.test_id,
    s.device_identifier,
    s.direction,
    s.sync_status,
    s.payload_hash,
    0,
    NULL,
    (SELECT COUNT(*) FROM performance_assessment_v2_db.sync_items si WHERE si.sync_id = s.sync_id),
    1,
    s.started_at,
    s.completed_at,
    s.error_message
FROM performance_assessment_v2_db.syncs s;

INSERT INTO sync_items (
    sync_item_id, sync_id, result_uuid, test_result_id, sync_action,
    sync_status, attempt_count, last_attempt_at, processed_at,
    error_code, error_message, synced_at
)
SELECT
    sync_item_id,
    sync_id,
    result_uuid,
    test_result_id,
    'upsert',
    sync_status,
    CASE WHEN sync_status IN ('success', 'failed', 'skipped') THEN 1 ELSE 0 END,
    COALESCE(synced_at, NULL),
    synced_at,
    error_code,
    error_message,
    synced_at
FROM performance_assessment_v2_db.sync_items;

INSERT INTO audit_logs (
    audit_log_id, audit_uuid, user_id, action, entity_type, entity_id,
    outcome, ip_address, device_identifier, user_agent, details, created_at
)
SELECT
    audit_log_id, audit_uuid, user_id, action, entity_type, entity_id,
    outcome, ip_address, device_identifier, user_agent, details, created_at
FROM performance_assessment_v2_db.audit_logs;

INSERT INTO auth_sessions (
    auth_session_id, session_uuid, user_id, authentication_level,
    user_mfa_factor_id, mfa_verified_at, refresh_token_hash,
    device_identifier, ip_address, user_agent, issued_at, expires_at,
    last_used_at, revoked_at
)
SELECT
    auth_session_id, session_uuid, user_id, 'password', NULL, NULL,
    refresh_token_hash, device_identifier, ip_address, user_agent,
    issued_at, expires_at, last_used_at, revoked_at
FROM performance_assessment_v2_db.auth_sessions;

INSERT INTO login_attempts (
    login_attempt_id, user_id, attempted_email, ip_address, device_identifier,
    user_agent, was_successful, failure_reason, attempted_at
)
SELECT
    login_attempt_id, user_id, attempted_email, ip_address, device_identifier,
    user_agent, was_successful, failure_reason, attempted_at
FROM performance_assessment_v2_db.login_attempts;

INSERT INTO notifications (
    notification_id, notification_uuid, recipient_user_id, notification_type,
    title, message, reference_type, reference_id, event_key, read_at, created_at
)
SELECT
    notification_id, notification_uuid, recipient_user_id, notification_type,
    title, message, reference_type, reference_id, event_key, read_at, created_at
FROM performance_assessment_v2_db.notifications;

INSERT INTO verification_challenges (
    verification_challenge_id, challenge_uuid, user_id, verification_purpose,
    delivery_channel, destination_masked, code_hash, challenge_status,
    delivery_status, delivery_provider, provider_message_id, attempt_count,
    maximum_attempt_count, resend_count, last_sent_at, next_resend_at,
    expires_at, verified_at, created_at, updated_at
)
SELECT
    otp.email_verification_otp_id,
    CONCAT('80000000-0000-4000-8000-', LPAD(otp.email_verification_otp_id, 12, '0')),
    otp.user_id,
    'teacher_registration',
    'email',
    CONCAT(LEFT(u.email, 1), '***@', SUBSTRING_INDEX(u.email, '@', -1)),
    otp.otp_hash,
    CASE
        WHEN otp.used_at IS NOT NULL THEN 'verified'
        WHEN otp.expires_at <= CURRENT_TIMESTAMP THEN 'expired'
        WHEN otp.attempt_count >= otp.max_attempts THEN 'locked'
        ELSE 'pending'
    END,
    'sent',
    'legacy_v2',
    NULL,
    LEAST(otp.attempt_count, otp.max_attempts),
    otp.max_attempts,
    0,
    otp.created_at,
    otp.resend_available_at,
    GREATEST(otp.expires_at, DATE_ADD(otp.created_at, INTERVAL 1 SECOND)),
    otp.used_at,
    otp.created_at,
    COALESCE(otp.used_at, otp.created_at)
FROM performance_assessment_v2_db.email_verification_otps otp
JOIN performance_assessment_v2_db.users u ON u.user_id = otp.user_id;

COMMIT;
