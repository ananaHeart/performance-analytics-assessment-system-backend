-- Temporary local development seed data for sync API testing only.
-- Safe to run multiple times on a dev database with the existing schema.sql and data.sql.

INSERT IGNORE INTO `user` (
    user_id,
    first_name,
    last_name,
    gender,
    date_birth,
    email,
    `password`,
    role,
    status
)
VALUES (
    1,
    'Maria',
    'Santos',
    'female',
    '1990-01-01',
    'teacher@example.com',
    'temporary',
    'teacher',
    'active'
);

INSERT IGNORE INTO section (
    section_id,
    grade_level_id,
    section_name
)
VALUES (
    1,
    (SELECT grade_level_id FROM grade_level WHERE grade_level_name = 'Grade 7'),
    'Rizal'
);

INSERT IGNORE INTO `class` (
    class_id,
    academic_year_id,
    user_id,
    subject_id,
    section_id
)
VALUES (
    1,
    (SELECT academic_year_id FROM academic_year WHERE year_name = '2025-2026'),
    1,
    (SELECT subject_id FROM subject WHERE subject_name = 'Mathematics'),
    1
);

INSERT IGNORE INTO student (
    student_id,
    student_lrn,
    first_name,
    last_name,
    gender
)
VALUES
    (1, '100000000001', 'Juan', 'Dela Cruz', 'male'),
    (2, '100000000002', 'Ana', 'Reyes', 'female');

INSERT IGNORE INTO student_enrollment (
    student_id,
    section_id,
    academic_year_id
)
VALUES
    (1, 1, (SELECT academic_year_id FROM academic_year WHERE year_name = '2025-2026')),
    (2, 1, (SELECT academic_year_id FROM academic_year WHERE year_name = '2025-2026'));

INSERT IGNORE INTO competency_tags (
    competency_id,
    grade_level_id,
    subject_id,
    competency_name
)
VALUES (
    1,
    (SELECT grade_level_id FROM grade_level WHERE grade_level_name = 'Grade 7'),
    (SELECT subject_id FROM subject WHERE subject_name = 'Mathematics'),
    'Linear Equations'
);

INSERT IGNORE INTO `test` (
    test_id,
    class_id,
    test_name,
    test_type,
    test_date,
    test_status
)
VALUES (
    1,
    1,
    'Quiz 1',
    'Quiz',
    '2026-05-21',
    'Active'
);

INSERT IGNORE INTO test_part (
    test_part_id,
    test_id,
    competency_id,
    part_order,
    part_type,
    number_of_items,
    points_per_item,
    answer_key
)
VALUES (
    1,
    1,
    1,
    'Part I',
    'Multiple Choice',
    3,
    1,
    'A,B,C'
);
