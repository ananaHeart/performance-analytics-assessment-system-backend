-- V3_004: Stable reference data and the physically validated OMR template contract.
-- This script is idempotent and inserts no operational users, learners, classes, or results.

USE performance_assessment_v3_db;

START TRANSACTION;

INSERT INTO genders (gender_id, gender_name, description) VALUES
    (1, 'Male', 'Male learner or user.'),
    (2, 'Female', 'Female learner or user.')
ON DUPLICATE KEY UPDATE
    gender_name = VALUES(gender_name),
    description = VALUES(description);

INSERT INTO majors (major_id, major_name) VALUES
    (1, 'English'),
    (2, 'Mathematics'),
    (3, 'Science'),
    (4, 'Filipino'),
    (5, 'Araling Panlipunan'),
    (6, 'MAPEH'),
    (7, 'Technology and Livelihood Education'),
    (8, 'Values Education'),
    (9, 'General Education')
ON DUPLICATE KEY UPDATE major_name = VALUES(major_name);

INSERT INTO educational_attainments (
    educational_attainment_id,
    attainment_name,
    attainment_order,
    description,
    is_active
) VALUES
    (1, 'Bachelor''s Degree', 1, 'Completed bachelor''s degree.', TRUE),
    (2, 'Master''s Units', 2, 'Completed graduate-level master''s units.', TRUE),
    (3, 'Master''s Degree', 3, 'Completed master''s degree.', TRUE),
    (4, 'Doctoral Units', 4, 'Completed graduate-level doctoral units.', TRUE),
    (5, 'Doctorate Degree', 5, 'Completed doctorate degree.', TRUE)
ON DUPLICATE KEY UPDATE
    attainment_name = VALUES(attainment_name),
    attainment_order = VALUES(attainment_order),
    description = VALUES(description),
    is_active = VALUES(is_active);

INSERT INTO suffixes (suffix_id, suffix_name, display_order, is_active) VALUES
    (1, 'Jr.', 1, TRUE),
    (2, 'Sr.', 2, TRUE),
    (3, 'II', 3, TRUE),
    (4, 'III', 4, TRUE),
    (5, 'IV', 5, TRUE)
ON DUPLICATE KEY UPDATE
    suffix_name = VALUES(suffix_name),
    display_order = VALUES(display_order),
    is_active = VALUES(is_active);

INSERT INTO roles (role_id, role_name) VALUES
    (1, 'principal'),
    (2, 'teacher')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

INSERT INTO statuses (status_id, status_name, is_active) VALUES
    (1, 'pending_email_verification', TRUE),
    (2, 'pending_approval', TRUE),
    (3, 'active', TRUE),
    (4, 'rejected', TRUE),
    (5, 'inactive', TRUE),
    (6, 'locked', TRUE)
ON DUPLICATE KEY UPDATE
    status_name = VALUES(status_name),
    is_active = VALUES(is_active);

INSERT INTO curriculums (
    curriculum_id,
    curriculum_name,
    version,
    description,
    status
) VALUES (
    1,
    'MATATAG Curriculum',
    '2024',
    'Initial curriculum master record. Confirm the version with the school before production deployment.',
    'active'
)
ON DUPLICATE KEY UPDATE
    curriculum_name = VALUES(curriculum_name),
    version = VALUES(version),
    description = VALUES(description),
    status = VALUES(status);

INSERT INTO grade_levels (grade_level_id, grade_level_name) VALUES
    (1, 'Grade 7'),
    (2, 'Grade 8'),
    (3, 'Grade 9'),
    (4, 'Grade 10')
ON DUPLICATE KEY UPDATE grade_level_name = VALUES(grade_level_name);

INSERT INTO subjects (subject_id, subject_code, subject_name) VALUES
    (1, 'ENG', 'English'),
    (2, 'MATH', 'Mathematics'),
    (3, 'SCI', 'Science'),
    (4, 'FIL', 'Filipino'),
    (5, 'AP', 'Araling Panlipunan'),
    (6, 'MAPEH', 'MAPEH'),
    (7, 'TLE', 'Technology and Livelihood Education'),
    (8, 'VE', 'Values Education')
ON DUPLICATE KEY UPDATE
    subject_code = VALUES(subject_code),
    subject_name = VALUES(subject_name);

INSERT INTO question_types (
    question_type_id,
    question_type_code,
    question_type_name,
    capture_mode,
    scoring_mode,
    supports_omr,
    supports_ocr,
    supports_multiple_response,
    requires_attachment,
    requires_teacher_verification,
    allows_teacher_answer_edit,
    is_active
) VALUES
    (1, 'multiple_choice', 'Multiple Choice', 'omr', 'automatic', TRUE, FALSE, FALSE, TRUE, TRUE, FALSE, TRUE),
    (2, 'true_false', 'True or False', 'omr', 'automatic', TRUE, FALSE, FALSE, TRUE, TRUE, FALSE, TRUE),
    (3, 'identification', 'Identification', 'hybrid', 'hybrid', FALSE, TRUE, FALSE, TRUE, TRUE, TRUE, TRUE),
    (4, 'enumeration', 'Enumeration', 'hybrid', 'hybrid', FALSE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE),
    (5, 'essay', 'Essay', 'hybrid', 'manual', FALSE, FALSE, FALSE, TRUE, TRUE, TRUE, TRUE)
ON DUPLICATE KEY UPDATE
    question_type_name = VALUES(question_type_name),
    capture_mode = VALUES(capture_mode),
    scoring_mode = VALUES(scoring_mode),
    supports_omr = VALUES(supports_omr),
    supports_ocr = VALUES(supports_ocr),
    supports_multiple_response = VALUES(supports_multiple_response),
    requires_attachment = VALUES(requires_attachment),
    requires_teacher_verification = VALUES(requires_teacher_verification),
    allows_teacher_answer_edit = VALUES(allows_teacher_answer_edit),
    is_active = VALUES(is_active);

INSERT INTO omr_templates (
    template_code,
    template_name,
    question_type_id,
    page_size,
    page_orientation,
    minimum_item_count,
    maximum_item_count,
    option_count,
    geometry_definition,
    qr_payload_version,
    minimum_scanner_version,
    template_status,
    created_by_user_id,
    activated_at
)
SELECT
    'OMR-A4-10-MC-CTX-V2',
    'A4 10-item Multiple Choice Bubble Answer Sheet',
    question_type_id,
    'A4',
    'portrait',
    10,
    10,
    4,
    '{"page_width_pt":595.276,"page_height_pt":841.89,"print_scale_percent":100,"markers":{"outer_size_pt":15,"inset_ratio":0.28,"margin_pt":20,"centers_pt":[[27.5,814.39],[567.776,814.39],[27.5,27.5],[567.776,27.5]]},"bubbles":{"option_keys":["A","B","C","D"],"x_centers_pt":[79.0,103.0,127.0,151.0],"y_centers_pt":[604.334,571.223,538.112,505.001,471.89,438.778,405.667,372.556,339.445,306.334],"radius_pt":6.4}}',
    2,
    '1.0',
    'active',
    NULL,
    CURRENT_TIMESTAMP
FROM question_types
WHERE question_type_code = 'multiple_choice'
ON DUPLICATE KEY UPDATE
    template_name = VALUES(template_name),
    question_type_id = VALUES(question_type_id),
    geometry_definition = VALUES(geometry_definition),
    qr_payload_version = VALUES(qr_payload_version),
    template_status = VALUES(template_status);

-- These system defaults preserve the current validated analytics behavior while
-- moving thresholds out of Java code. A school-specific approved version may
-- supersede them later without changing historical score snapshots.
INSERT INTO performance_rule_sets (
    rule_set_uuid,
    school_id,
    rule_set_name,
    rule_version,
    metric_scope,
    rule_definition,
    rule_status,
    approved_by_user_id,
    effective_from_at,
    effective_until_at
) VALUES
    (
        '00000000-0000-4000-8000-000000000101',
        NULL,
        'SMART Standard Performance Bands',
        '1.0',
        'student_score',
        '{"metric":"percentage","formula":"total_score / max_score * 100","rounding_scale":2,"bands":[{"minimum_percentage":80,"maximum_percentage":100,"minimum_inclusive":true,"maximum_inclusive":true,"status":"maintain","label":"Maintain"},{"minimum_percentage":60,"maximum_percentage":80,"minimum_inclusive":true,"maximum_inclusive":false,"status":"review","label":"Review"},{"minimum_percentage":40,"maximum_percentage":60,"minimum_inclusive":true,"maximum_inclusive":false,"status":"reteach","label":"Reteach"},{"minimum_percentage":0,"maximum_percentage":40,"minimum_inclusive":true,"maximum_inclusive":false,"status":"priority_intervention","label":"Priority Intervention"}],"source":"SMART V2 validated analytics baseline"}',
        'active',
        NULL,
        '2025-06-01 00:00:00',
        NULL
    ),
    (
        '00000000-0000-4000-8000-000000000102',
        NULL,
        'SMART Standard Performance Bands',
        '1.0',
        'skill_mastery',
        '{"metric":"mastery_percentage","formula":"sum(earned_points) / sum(possible_points) * 100","rounding_scale":2,"include_all_assessed_skills":true,"bands":[{"minimum_percentage":80,"maximum_percentage":100,"minimum_inclusive":true,"maximum_inclusive":true,"status":"maintain","label":"Maintain"},{"minimum_percentage":60,"maximum_percentage":80,"minimum_inclusive":true,"maximum_inclusive":false,"status":"review","label":"Review"},{"minimum_percentage":40,"maximum_percentage":60,"minimum_inclusive":true,"maximum_inclusive":false,"status":"reteach","label":"Reteach"},{"minimum_percentage":0,"maximum_percentage":40,"minimum_inclusive":true,"maximum_inclusive":false,"status":"priority_intervention","label":"Priority Intervention"}],"source":"SMART V2 validated analytics baseline"}',
        'active',
        NULL,
        '2025-06-01 00:00:00',
        NULL
    ),
    (
        '00000000-0000-4000-8000-000000000103',
        NULL,
        'SMART Standard Performance Bands',
        '1.0',
        'class_mastery',
        '{"metric":"class_mastery_percentage","formula":"sum(earned_points) / sum(possible_points) * 100","aggregation":"weighted_points","rounding_scale":2,"bands":[{"minimum_percentage":80,"maximum_percentage":100,"minimum_inclusive":true,"maximum_inclusive":true,"status":"maintain","label":"Maintain"},{"minimum_percentage":60,"maximum_percentage":80,"minimum_inclusive":true,"maximum_inclusive":false,"status":"review","label":"Review"},{"minimum_percentage":40,"maximum_percentage":60,"minimum_inclusive":true,"maximum_inclusive":false,"status":"reteach","label":"Reteach"},{"minimum_percentage":0,"maximum_percentage":40,"minimum_inclusive":true,"maximum_inclusive":false,"status":"priority_intervention","label":"Priority Intervention"}],"source":"SMART V2 validated analytics baseline"}',
        'active',
        NULL,
        '2025-06-01 00:00:00',
        NULL
    ),
    (
        '00000000-0000-4000-8000-000000000104',
        NULL,
        'SMART Standard Performance Bands',
        '1.0',
        'intervention',
        '{"metric":"skill_mastery_percentage","teacher_facing_only":true,"bands":[{"minimum_percentage":80,"maximum_percentage":100,"minimum_inclusive":true,"maximum_inclusive":true,"status":"maintain","label":"Maintain","recommendation_template":"Maintain {competency_name} through regular practice."},{"minimum_percentage":60,"maximum_percentage":80,"minimum_inclusive":true,"maximum_inclusive":false,"status":"review","label":"Review","recommendation_template":"Review {competency_name} with short guided practice."},{"minimum_percentage":40,"maximum_percentage":60,"minimum_inclusive":true,"maximum_inclusive":false,"status":"reteach","label":"Reteach","recommendation_template":"Reteach {competency_name} using focused examples and checking."},{"minimum_percentage":0,"maximum_percentage":40,"minimum_inclusive":true,"maximum_inclusive":false,"status":"priority_intervention","label":"Priority Intervention","recommendation_template":"Prioritize intervention for {competency_name} and monitor affected learners."}],"source":"SMART V2 validated analytics baseline"}',
        'active',
        NULL,
        '2025-06-01 00:00:00',
        NULL
    )
ON DUPLICATE KEY UPDATE
    rule_set_name = VALUES(rule_set_name),
    rule_version = VALUES(rule_version),
    metric_scope = VALUES(metric_scope),
    rule_definition = VALUES(rule_definition),
    rule_status = VALUES(rule_status),
    effective_from_at = VALUES(effective_from_at),
    effective_until_at = VALUES(effective_until_at);

COMMIT;
