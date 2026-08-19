-- Performance Analytics Assessment System
-- Stable reference/master seed data for performance_assessment_v2_db.
-- This script is idempotent and does not insert operational users, students,
-- classes, tests, results, scans, sync batches, or security logs.

USE performance_assessment_v2_db;

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
ON DUPLICATE KEY UPDATE
    major_name = VALUES(major_name);

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

INSERT INTO roles (role_id, role_name) VALUES
    (1, 'principal'),
    (2, 'teacher')
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name);

INSERT INTO statuses (status_id, status_name, is_active) VALUES
    (1, 'pending', TRUE),
    (2, 'active', TRUE),
    (3, 'rejected', TRUE),
    (4, 'inactive', TRUE),
    (5, 'locked', TRUE)
ON DUPLICATE KEY UPDATE
    status_name = VALUES(status_name),
    is_active = VALUES(is_active);

INSERT INTO curriculums (
    curriculum_id,
    curriculum_name,
    version,
    description,
    status
) VALUES
    (
        1,
        'MATATAG Curriculum',
        '2024',
        'Initial curriculum master record for V2 configuration. Confirm the version with the school before production use.',
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
ON DUPLICATE KEY UPDATE
    grade_level_name = VALUES(grade_level_name);

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
