-- Final local database recreate script draft
-- Project: Performance Analytics Assessment System
-- Date: July 30, 2026
-- Basis: Data Dictionary CAP2_v5 naming convention review
-- Scope: Local XAMPP/phpMyAdmin first. Do not run on TiDB yet.
--
-- Naming convention:
--   - Table names use lowercase snake_case plural names.
--   - Column names use lowercase snake_case.
--
-- Important:
--   - This is a final-schema draft for adviser/database review.
--   - Current backend code still needs a separate compatibility update before this schema is runtime-ready.
--   - This script intentionally removes legacy school_profile and skill_item.
--   - This script uses term_periods instead of grading_period.
--   - This script uses answer_keys instead of test_parts.answer_key.
--   - This script separates root_tags from competency_tags.

SET FOREIGN_KEY_CHECKS = 0;
DROP DATABASE IF EXISTS performance_assessment_db;
CREATE DATABASE performance_assessment_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;
USE performance_assessment_db;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE users (
    user_id INT NOT NULL AUTO_INCREMENT,
    first_name VARCHAR(30) NOT NULL,
    middle_initial VARCHAR(2) NULL,
    last_name VARCHAR(30) NOT NULL,
    gender ENUM('male', 'female') NOT NULL,
    date_birth DATE NOT NULL,
    email VARCHAR(80) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    role ENUM('principal', 'teacher') NOT NULL,
    status ENUM('pending', 'active', 'rejected') NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE academic_years (
    academic_year_id INT NOT NULL AUTO_INCREMENT,
    year_name VARCHAR(15) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status ENUM('Active', 'Completed') NOT NULL DEFAULT 'Active',
    CONSTRAINT pk_academic_years PRIMARY KEY (academic_year_id),
    CONSTRAINT uk_academic_years_year_name UNIQUE (year_name)
);

CREATE TABLE term_periods (
    term_period_id INT NOT NULL AUTO_INCREMENT,
    academic_year_id INT NOT NULL,
    period_order INT NOT NULL,
    period_name VARCHAR(30) NOT NULL,
    status ENUM('Active', 'Completed') NOT NULL DEFAULT 'Active',
    CONSTRAINT pk_term_periods PRIMARY KEY (term_period_id),
    CONSTRAINT uk_term_periods_order UNIQUE (academic_year_id, period_order),
    CONSTRAINT uk_term_periods_name UNIQUE (academic_year_id, period_name),
    CONSTRAINT fk_term_periods_academic_year
        FOREIGN KEY (academic_year_id) REFERENCES academic_years (academic_year_id)
        ON DELETE CASCADE
);

CREATE TABLE grade_levels (
    grade_level_id INT NOT NULL AUTO_INCREMENT,
    grade_level_name VARCHAR(20) NOT NULL,
    CONSTRAINT pk_grade_levels PRIMARY KEY (grade_level_id),
    CONSTRAINT uk_grade_levels_name UNIQUE (grade_level_name)
);

CREATE TABLE sections (
    section_id INT NOT NULL AUTO_INCREMENT,
    grade_level_id INT NOT NULL,
    academic_year_id INT NOT NULL,
    section_name VARCHAR(50) NOT NULL,
    CONSTRAINT pk_sections PRIMARY KEY (section_id),
    CONSTRAINT uk_sections_grade_year_name UNIQUE (grade_level_id, academic_year_id, section_name),
    CONSTRAINT fk_sections_grade_level
        FOREIGN KEY (grade_level_id) REFERENCES grade_levels (grade_level_id),
    CONSTRAINT fk_sections_academic_year
        FOREIGN KEY (academic_year_id) REFERENCES academic_years (academic_year_id)
);

CREATE TABLE subjects (
    subject_id INT NOT NULL AUTO_INCREMENT,
    subject_code VARCHAR(20) NULL,
    subject_name VARCHAR(30) NOT NULL,
    CONSTRAINT pk_subjects PRIMARY KEY (subject_id),
    CONSTRAINT uk_subjects_code UNIQUE (subject_code),
    CONSTRAINT uk_subjects_name UNIQUE (subject_name)
);

CREATE TABLE students (
    student_id INT NOT NULL AUTO_INCREMENT,
    student_lrn VARCHAR(12) NOT NULL,
    first_name VARCHAR(30) NOT NULL,
    last_name VARCHAR(30) NOT NULL,
    gender ENUM('male', 'female') NOT NULL,
    CONSTRAINT pk_students PRIMARY KEY (student_id),
    CONSTRAINT uk_students_lrn UNIQUE (student_lrn)
);

CREATE TABLE student_enrollments (
    student_enrollment_id INT NOT NULL AUTO_INCREMENT,
    student_id INT NOT NULL,
    section_id INT NOT NULL,
    academic_year_id INT NOT NULL,
    CONSTRAINT pk_student_enrollments PRIMARY KEY (student_enrollment_id),
    CONSTRAINT uk_student_enrollments_student_year UNIQUE (student_id, academic_year_id),
    CONSTRAINT fk_student_enrollments_student
        FOREIGN KEY (student_id) REFERENCES students (student_id),
    CONSTRAINT fk_student_enrollments_section
        FOREIGN KEY (section_id) REFERENCES sections (section_id),
    CONSTRAINT fk_student_enrollments_academic_year
        FOREIGN KEY (academic_year_id) REFERENCES academic_years (academic_year_id)
);

CREATE TABLE classes (
    class_id INT NOT NULL AUTO_INCREMENT,
    academic_year_id INT NOT NULL,
    user_id INT NOT NULL,
    subject_id INT NOT NULL,
    section_id INT NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_classes PRIMARY KEY (class_id),
    CONSTRAINT uk_classes_assignment UNIQUE (academic_year_id, subject_id, section_id),
    CONSTRAINT fk_classes_academic_year
        FOREIGN KEY (academic_year_id) REFERENCES academic_years (academic_year_id),
    CONSTRAINT fk_classes_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_classes_subject
        FOREIGN KEY (subject_id) REFERENCES subjects (subject_id),
    CONSTRAINT fk_classes_section
        FOREIGN KEY (section_id) REFERENCES sections (section_id)
);

CREATE TABLE curriculums (
    curriculum_id INT NOT NULL AUTO_INCREMENT,
    curriculum_name VARCHAR(30) NOT NULL,
    version VARCHAR(30) NULL,
    status ENUM('Active', 'Completed') NOT NULL DEFAULT 'Active',
    CONSTRAINT pk_curriculums PRIMARY KEY (curriculum_id),
    CONSTRAINT uk_curriculums_name_version UNIQUE (curriculum_name, version)
);

CREATE TABLE root_tags (
    root_id INT NOT NULL AUTO_INCREMENT,
    root_name VARCHAR(30) NOT NULL,
    description VARCHAR(255) NULL,
    CONSTRAINT pk_root_tags PRIMARY KEY (root_id),
    CONSTRAINT uk_root_tags_name UNIQUE (root_name)
);

CREATE TABLE competency_tags (
    competency_id INT NOT NULL AUTO_INCREMENT,
    root_id INT NOT NULL,
    competency_name VARCHAR(50) NOT NULL,
    CONSTRAINT pk_competency_tags PRIMARY KEY (competency_id),
    CONSTRAINT uk_competency_tags_root_name UNIQUE (root_id, competency_name),
    CONSTRAINT fk_competency_tags_root
        FOREIGN KEY (root_id) REFERENCES root_tags (root_id)
);

CREATE TABLE skills (
    skill_id INT NOT NULL AUTO_INCREMENT,
    competency_id INT NOT NULL,
    grade_level_id INT NOT NULL,
    subject_id INT NOT NULL,
    term_period_id INT NOT NULL,
    CONSTRAINT pk_skills PRIMARY KEY (skill_id),
    CONSTRAINT uk_skills_scope UNIQUE (competency_id, grade_level_id, subject_id, term_period_id),
    CONSTRAINT fk_skills_competency
        FOREIGN KEY (competency_id) REFERENCES competency_tags (competency_id),
    CONSTRAINT fk_skills_grade_level
        FOREIGN KEY (grade_level_id) REFERENCES grade_levels (grade_level_id),
    CONSTRAINT fk_skills_subject
        FOREIGN KEY (subject_id) REFERENCES subjects (subject_id),
    CONSTRAINT fk_skills_term_period
        FOREIGN KEY (term_period_id) REFERENCES term_periods (term_period_id)
);

CREATE TABLE interventions (
    intervention_id INT NOT NULL AUTO_INCREMENT,
    intervention_name VARCHAR(50) NOT NULL,
    description VARCHAR(255) NULL,
    CONSTRAINT pk_interventions PRIMARY KEY (intervention_id),
    CONSTRAINT uk_interventions_name UNIQUE (intervention_name)
);

CREATE TABLE tests (
    test_id INT NOT NULL AUTO_INCREMENT,
    term_period_id INT NULL,
    class_id INT NOT NULL,
    test_name VARCHAR(50) NOT NULL,
    test_type ENUM('Quiz', 'Exam', 'Long Test') NOT NULL,
    test_date DATE NOT NULL,
    test_status ENUM('Draft', 'Active', 'Completed') NOT NULL DEFAULT 'Draft',
    CONSTRAINT pk_tests PRIMARY KEY (test_id),
    CONSTRAINT fk_tests_term_period
        FOREIGN KEY (term_period_id) REFERENCES term_periods (term_period_id),
    CONSTRAINT fk_tests_class
        FOREIGN KEY (class_id) REFERENCES classes (class_id)
);

CREATE TABLE test_parts (
    test_part_id INT NOT NULL AUTO_INCREMENT,
    test_id INT NOT NULL,
    competency_id INT NOT NULL,
    part_order VARCHAR(15) NOT NULL,
    question_type ENUM('multiple-choice', 'identification', 'enumeration') NOT NULL,
    number_of_items INT NOT NULL,
    points_per_item INT NOT NULL DEFAULT 1,
    CONSTRAINT pk_test_parts PRIMARY KEY (test_part_id),
    CONSTRAINT uk_test_parts_order UNIQUE (test_id, part_order),
    CONSTRAINT fk_test_parts_test
        FOREIGN KEY (test_id) REFERENCES tests (test_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_test_parts_competency
        FOREIGN KEY (competency_id) REFERENCES competency_tags (competency_id),
    CONSTRAINT chk_test_parts_number_of_items CHECK (number_of_items > 0),
    CONSTRAINT chk_test_parts_points CHECK (points_per_item > 0)
);

CREATE TABLE answer_keys (
    answer_key_id INT NOT NULL AUTO_INCREMENT,
    test_part_id INT NOT NULL,
    item_number INT NOT NULL,
    correct_answer TEXT NOT NULL,
    points_per_item INT NOT NULL DEFAULT 1,
    CONSTRAINT pk_answer_keys PRIMARY KEY (answer_key_id),
    CONSTRAINT uk_answer_keys_part_item UNIQUE (test_part_id, item_number),
    CONSTRAINT fk_answer_keys_test_part
        FOREIGN KEY (test_part_id) REFERENCES test_parts (test_part_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_answer_keys_item_number CHECK (item_number > 0),
    CONSTRAINT chk_answer_keys_points CHECK (points_per_item > 0)
);

CREATE TABLE test_results (
    test_result_id INT NOT NULL AUTO_INCREMENT,
    test_id INT NOT NULL,
    student_id INT NOT NULL,
    total_score INT NOT NULL,
    raw_answers TEXT NULL,
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_test_results PRIMARY KEY (test_result_id),
    CONSTRAINT uk_test_results_test_student UNIQUE (test_id, student_id),
    CONSTRAINT fk_test_results_test
        FOREIGN KEY (test_id) REFERENCES tests (test_id),
    CONSTRAINT fk_test_results_student
        FOREIGN KEY (student_id) REFERENCES students (student_id)
);

CREATE TABLE test_item_results (
    item_result_id INT NOT NULL AUTO_INCREMENT,
    test_part_id INT NOT NULL,
    test_result_id INT NOT NULL,
    item_number INT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    CONSTRAINT pk_test_item_results PRIMARY KEY (item_result_id),
    CONSTRAINT uk_test_item_results UNIQUE (test_result_id, test_part_id, item_number),
    CONSTRAINT fk_test_item_results_test_part
        FOREIGN KEY (test_part_id) REFERENCES test_parts (test_part_id),
    CONSTRAINT fk_test_item_results_test_result
        FOREIGN KEY (test_result_id) REFERENCES test_results (test_result_id)
        ON DELETE CASCADE
);

CREATE TABLE sync_logs (
    sync_id INT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    test_id INT NOT NULL,
    sync_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sync_status ENUM('Success', 'Failed') NOT NULL,
    CONSTRAINT pk_sync_logs PRIMARY KEY (sync_id),
    CONSTRAINT fk_sync_logs_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_sync_logs_test
        FOREIGN KEY (test_id) REFERENCES tests (test_id)
);

CREATE TABLE part_skill_mappings (
    mapping_id INT NOT NULL AUTO_INCREMENT,
    test_part_id INT NOT NULL,
    competency_id INT NOT NULL,
    item_count INT NOT NULL,
    start_item INT NOT NULL,
    end_item INT NOT NULL,
    CONSTRAINT pk_part_skill_mappings PRIMARY KEY (mapping_id),
    CONSTRAINT uk_part_skill_mappings_competency UNIQUE (test_part_id, competency_id),
    CONSTRAINT fk_part_skill_mappings_test_part
        FOREIGN KEY (test_part_id) REFERENCES test_parts (test_part_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_part_skill_mappings_competency
        FOREIGN KEY (competency_id) REFERENCES competency_tags (competency_id),
    CONSTRAINT chk_part_skill_mappings_item_count CHECK (item_count > 0),
    CONSTRAINT chk_part_skill_mappings_range CHECK (start_item >= 1 AND end_item >= start_item)
);

-- Required master/reference seed data only.

INSERT INTO academic_years (academic_year_id, year_name, start_date, end_date, status)
VALUES (1, '2025-2026', '2025-06-01', '2026-03-31', 'Active');

INSERT INTO term_periods (term_period_id, academic_year_id, period_order, period_name, status)
VALUES (1, 1, 1, 'First Grading', 'Active');

INSERT INTO grade_levels (grade_level_id, grade_level_name)
VALUES
    (1, 'Grade 7'),
    (2, 'Grade 8'),
    (3, 'Grade 9'),
    (4, 'Grade 10');

INSERT INTO subjects (subject_id, subject_code, subject_name)
VALUES
    (1, 'ENG', 'English'),
    (2, 'MATH', 'Mathematics'),
    (3, 'SCI', 'Science');

INSERT INTO curriculums (curriculum_id, curriculum_name, version, status)
VALUES (1, 'K to 12', 'Current', 'Active');

INSERT INTO interventions (intervention_id, intervention_name, description)
VALUES
    (1, 'Review', 'Review the competency with affected learners using guided examples and short practice exercises.'),
    (2, 'Reteach', 'Reteach the competency using simple examples and guided practice.'),
    (3, 'Priority Intervention', 'Provide focused small-group support and a short follow-up assessment.'),
    (4, 'Maintain', 'Continue monitoring. No immediate intervention is needed.');

