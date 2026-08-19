-- Performance Analytics Assessment System
-- V2 database schema draft based on Data Dictionary CAP2_v8.docx
-- Target: MySQL/MariaDB (XAMPP) and TiDB-compatible SQL
-- Safety: creates a separate database and does not alter the working database.

CREATE DATABASE IF NOT EXISTS performance_assessment_v2_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE performance_assessment_v2_db;

CREATE TABLE addresses (
    address_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique address identifier.',
    country_code CHAR(2) NOT NULL DEFAULT 'PH' COMMENT 'ISO country code.',
    region_code VARCHAR(20) NULL COMMENT 'Region code returned by the address API.',
    region_name VARCHAR(100) NULL COMMENT 'Region display name.',
    province_code VARCHAR(20) NULL COMMENT 'Province code returned by the address API.',
    province_name VARCHAR(100) NULL COMMENT 'Province display name.',
    city_municipality_code VARCHAR(20) NULL COMMENT 'City or municipality code returned by the address API.',
    city_municipality_name VARCHAR(120) NULL COMMENT 'City or municipality display name.',
    barangay_code VARCHAR(20) NULL COMMENT 'Barangay code returned by the address API.',
    barangay_name VARCHAR(120) NULL COMMENT 'Barangay display name.',
    address_line VARCHAR(255) NULL COMMENT 'House, building, subdivision, or street information.',
    postal_code VARCHAR(10) NULL COMMENT 'Postal or ZIP code.',
    address_source ENUM('api', 'manual', 'sf1_import') NOT NULL DEFAULT 'manual' COMMENT 'Source used to capture the address.',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation timestamp.',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp.',
    CONSTRAINT pk_addresses PRIMARY KEY (address_id),
    INDEX idx_addresses_location (region_code, province_code, city_municipality_code, barangay_code)
) COMMENT='Normalized address records for schools, users, and students.';

CREATE TABLE school_profiles (
    school_id VARCHAR(20) NOT NULL COMMENT 'Official DepEd school identifier.',
    address_id BIGINT UNSIGNED NOT NULL COMMENT 'Address of the school.',
    school_name VARCHAR(120) NOT NULL COMMENT 'Official school name.',
    contact_number VARCHAR(20) NULL COMMENT 'Official school contact number.',
    email VARCHAR(120) NULL COMMENT 'Official school email address.',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation timestamp.',
    CONSTRAINT pk_school_profiles PRIMARY KEY (school_id),
    CONSTRAINT uk_school_profiles_email UNIQUE (email),
    CONSTRAINT fk_school_profiles_address
        FOREIGN KEY (address_id) REFERENCES addresses (address_id)
) COMMENT='School identity and contact information.';

CREATE TABLE genders (
    gender_id TINYINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique gender identifier.',
    gender_name VARCHAR(20) NOT NULL COMMENT 'Gender display value.',
    description VARCHAR(255) NULL COMMENT 'Optional explanation of the gender value.',
    CONSTRAINT pk_genders PRIMARY KEY (gender_id),
    CONSTRAINT uk_genders_name UNIQUE (gender_name)
) COMMENT='Reference values used for user and student gender.';

CREATE TABLE majors (
    major_id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique major identifier.',
    major_name VARCHAR(100) NOT NULL COMMENT 'Teacher major or specialization name.',
    CONSTRAINT pk_majors PRIMARY KEY (major_id),
    CONSTRAINT uk_majors_name UNIQUE (major_name)
) COMMENT='Teacher major and specialization reference values.';

CREATE TABLE educational_attainments (
    educational_attainment_id TINYINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique educational-attainment identifier.',
    attainment_name VARCHAR(100) NOT NULL COMMENT 'Educational-attainment display name.',
    attainment_order TINYINT UNSIGNED NOT NULL COMMENT 'Display and ranking order.',
    description VARCHAR(255) NULL COMMENT 'Additional information about the attainment.',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Whether the attainment may be assigned.',
    CONSTRAINT pk_educational_attainments PRIMARY KEY (educational_attainment_id),
    CONSTRAINT uk_educational_attainments_name UNIQUE (attainment_name),
    CONSTRAINT uk_educational_attainments_order UNIQUE (attainment_order)
) COMMENT='Normalized highest educational-attainment choices for users.';

CREATE TABLE roles (
    role_id TINYINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique authorization-role identifier.',
    role_name VARCHAR(30) NOT NULL COMMENT 'Role name such as principal or teacher.',
    CONSTRAINT pk_roles PRIMARY KEY (role_id),
    CONSTRAINT uk_roles_name UNIQUE (role_name)
) COMMENT='System authorization roles.';

CREATE TABLE statuses (
    status_id TINYINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique account-status identifier.',
    status_name VARCHAR(30) NOT NULL COMMENT 'Account status such as pending, active, rejected, or inactive.',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Whether the status may be assigned.',
    CONSTRAINT pk_statuses PRIMARY KEY (status_id),
    CONSTRAINT uk_statuses_name UNIQUE (status_name)
) COMMENT='User-account lifecycle statuses.';

CREATE TABLE users (
    user_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique user identifier.',
    school_id VARCHAR(20) NOT NULL COMMENT 'School that owns the account.',
    address_id BIGINT UNSIGNED NOT NULL COMMENT 'Residential address of the user.',
    gender_id TINYINT UNSIGNED NOT NULL COMMENT 'Gender reference.',
    major_id INT UNSIGNED NULL COMMENT 'Teacher major or specialization; optional for principals.',
    educational_attainment_id TINYINT UNSIGNED NULL COMMENT 'Highest educational attainment.',
    role_id TINYINT UNSIGNED NOT NULL COMMENT 'Authorization role.',
    status_id TINYINT UNSIGNED NOT NULL COMMENT 'Account lifecycle status.',
    first_name VARCHAR(50) NOT NULL COMMENT 'First name.',
    middle_name VARCHAR(50) NULL COMMENT 'Middle name.',
    last_name VARCHAR(50) NOT NULL COMMENT 'Last name.',
    suffix VARCHAR(10) NULL COMMENT 'Optional suffix such as Jr. or III.',
    birth_date DATE NULL COMMENT 'Birth date used to derive age.',
    teaching_start_date DATE NULL COMMENT 'Date used to derive years of teaching experience.',
    email VARCHAR(120) NOT NULL COMMENT 'Unique login email.',
    contact_number VARCHAR(20) NOT NULL COMMENT 'Unique primary contact number.',
    password_hash VARCHAR(255) NOT NULL COMMENT 'Secure password hash; never stores a plain-text password.',
    failed_login_count SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Consecutive failed-login counter.',
    locked_until_at TIMESTAMP NULL COMMENT 'Account lock expiration timestamp.',
    last_login_at TIMESTAMP NULL COMMENT 'Most recent successful-login timestamp.',
    password_changed_at TIMESTAMP NULL COMMENT 'Most recent password-change timestamp.',
    email_verified_at TIMESTAMP NULL COMMENT 'Email-verification timestamp.',
    contact_verified_at TIMESTAMP NULL COMMENT 'Contact-number verification timestamp.',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Account creation timestamp.',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last account update timestamp.',
    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_contact_number UNIQUE (contact_number),
    CONSTRAINT fk_users_school
        FOREIGN KEY (school_id) REFERENCES school_profiles (school_id),
    CONSTRAINT fk_users_address
        FOREIGN KEY (address_id) REFERENCES addresses (address_id),
    CONSTRAINT fk_users_gender
        FOREIGN KEY (gender_id) REFERENCES genders (gender_id),
    CONSTRAINT fk_users_major
        FOREIGN KEY (major_id) REFERENCES majors (major_id),
    CONSTRAINT fk_users_educational_attainment
        FOREIGN KEY (educational_attainment_id) REFERENCES educational_attainments (educational_attainment_id),
    CONSTRAINT fk_users_role
        FOREIGN KEY (role_id) REFERENCES roles (role_id),
    CONSTRAINT fk_users_status
        FOREIGN KEY (status_id) REFERENCES statuses (status_id),
    INDEX idx_users_school_role_status (school_id, role_id, status_id)
) COMMENT='Principal and teacher accounts, identity, security, and profile information.';

CREATE TABLE curriculums (
    curriculum_id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique curriculum identifier.',
    curriculum_name VARCHAR(80) NOT NULL COMMENT 'Curriculum name such as MATATAG Curriculum.',
    version VARCHAR(30) NOT NULL COMMENT 'Curriculum version or release label.',
    description TEXT NULL COMMENT 'Curriculum description.',
    status ENUM('active', 'inactive', 'archived') NOT NULL DEFAULT 'active' COMMENT 'Curriculum lifecycle status.',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation timestamp.',
    CONSTRAINT pk_curriculums PRIMARY KEY (curriculum_id),
    CONSTRAINT uk_curriculums_name_version UNIQUE (curriculum_name, version)
) COMMENT='Curriculum versions used by the school.';

CREATE TABLE academic_years (
    academic_year_id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique academic-year identifier.',
    curriculum_id INT UNSIGNED NOT NULL COMMENT 'Curriculum used during the academic year.',
    year_name VARCHAR(15) NOT NULL COMMENT 'Display value such as 2026-2027.',
    start_date DATE NOT NULL COMMENT 'Official opening date.',
    end_date DATE NOT NULL COMMENT 'Official closing date.',
    status ENUM('planned', 'active', 'completed') NOT NULL DEFAULT 'planned' COMMENT 'Academic-year lifecycle status.',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation timestamp.',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp.',
    CONSTRAINT pk_academic_years PRIMARY KEY (academic_year_id),
    CONSTRAINT uk_academic_years_name UNIQUE (year_name),
    CONSTRAINT fk_academic_years_curriculum
        FOREIGN KEY (curriculum_id) REFERENCES curriculums (curriculum_id),
    CONSTRAINT chk_academic_years_dates CHECK (end_date >= start_date)
) COMMENT='School academic years and their curriculum version.';

CREATE TABLE term_periods (
    term_period_id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique term-period identifier.',
    academic_year_id INT UNSIGNED NOT NULL COMMENT 'Academic year containing the term.',
    term_name VARCHAR(50) NOT NULL COMMENT 'Display name such as First Quarter.',
    term_order TINYINT UNSIGNED NOT NULL COMMENT 'Chronological order within the academic year.',
    status ENUM('planned', 'active', 'completed') NOT NULL DEFAULT 'planned' COMMENT 'Term lifecycle status.',
    CONSTRAINT pk_term_periods PRIMARY KEY (term_period_id),
    CONSTRAINT uk_term_periods_order UNIQUE (academic_year_id, term_order),
    CONSTRAINT uk_term_periods_name UNIQUE (academic_year_id, term_name),
    CONSTRAINT fk_term_periods_academic_year
        FOREIGN KEY (academic_year_id) REFERENCES academic_years (academic_year_id)
) COMMENT='Academic-year terms or grading periods.';

CREATE TABLE grade_levels (
    grade_level_id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique grade-level identifier.',
    grade_level_name VARCHAR(20) NOT NULL COMMENT 'Grade-level display name such as Grade 7.',
    CONSTRAINT pk_grade_levels PRIMARY KEY (grade_level_id),
    CONSTRAINT uk_grade_levels_name UNIQUE (grade_level_name)
) COMMENT='Supported school grade levels.';

CREATE TABLE sections (
    section_id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique section identifier.',
    grade_level_id INT UNSIGNED NOT NULL COMMENT 'Grade level containing the section.',
    section_name VARCHAR(50) NOT NULL COMMENT 'Official section name such as Rizal.',
    CONSTRAINT pk_sections PRIMARY KEY (section_id),
    CONSTRAINT uk_sections_grade_name UNIQUE (grade_level_id, section_name),
    CONSTRAINT fk_sections_grade_level
        FOREIGN KEY (grade_level_id) REFERENCES grade_levels (grade_level_id)
) COMMENT='Section master records independent of a specific academic year.';

CREATE TABLE subjects (
    subject_id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique subject identifier.',
    subject_code VARCHAR(10) NULL COMMENT 'Official or internal subject code.',
    subject_name VARCHAR(100) NOT NULL COMMENT 'Official subject display name such as Technology and Livelihood Education.',
    CONSTRAINT pk_subjects PRIMARY KEY (subject_id),
    CONSTRAINT uk_subjects_code UNIQUE (subject_code),
    CONSTRAINT uk_subjects_name UNIQUE (subject_name)
) COMMENT='Subject master records.';

CREATE TABLE students (
    student_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Internal learner identifier.',
    school_id VARCHAR(20) NOT NULL COMMENT 'School that owns the learner record.',
    address_id BIGINT UNSIGNED NOT NULL COMMENT 'Residential address of the learner.',
    gender_id TINYINT UNSIGNED NOT NULL COMMENT 'Gender reference.',
    student_lrn VARCHAR(12) NOT NULL COMMENT 'Official 12-digit Learner Reference Number.',
    first_name VARCHAR(50) NOT NULL COMMENT 'First name.',
    middle_name VARCHAR(50) NULL COMMENT 'Middle name.',
    last_name VARCHAR(50) NOT NULL COMMENT 'Last name.',
    suffix VARCHAR(10) NULL COMMENT 'Optional name suffix.',
    birth_date DATE NULL COMMENT 'Birth date.',
    status ENUM('active', 'inactive', 'transferred', 'graduated') NOT NULL DEFAULT 'active' COMMENT 'Learner lifecycle status.',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation timestamp.',
    CONSTRAINT pk_students PRIMARY KEY (student_id),
    CONSTRAINT uk_students_lrn UNIQUE (student_lrn),
    CONSTRAINT fk_students_school
        FOREIGN KEY (school_id) REFERENCES school_profiles (school_id),
    CONSTRAINT fk_students_address
        FOREIGN KEY (address_id) REFERENCES addresses (address_id),
    CONSTRAINT fk_students_gender
        FOREIGN KEY (gender_id) REFERENCES genders (gender_id),
    INDEX idx_students_school_name (school_id, last_name, first_name)
) COMMENT='School learner master records.';

CREATE TABLE classes (
    class_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique section-and-academic-year cohort identifier.',
    academic_year_id INT UNSIGNED NOT NULL COMMENT 'Academic year of the cohort.',
    section_id INT UNSIGNED NOT NULL COMMENT 'Section represented by the cohort.',
    status ENUM('active', 'completed', 'archived') NOT NULL DEFAULT 'active' COMMENT 'Class lifecycle status.',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation timestamp.',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp.',
    CONSTRAINT pk_classes PRIMARY KEY (class_id),
    CONSTRAINT uk_classes_year_section UNIQUE (academic_year_id, section_id),
    CONSTRAINT fk_classes_academic_year
        FOREIGN KEY (academic_year_id) REFERENCES academic_years (academic_year_id),
    CONSTRAINT fk_classes_section
        FOREIGN KEY (section_id) REFERENCES sections (section_id)
) COMMENT='Section cohorts for a specific academic year.';

CREATE TABLE class_assignments (
    class_assignment_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique teacher-subject assignment identifier.',
    class_id BIGINT UNSIGNED NOT NULL COMMENT 'Assigned section-and-year cohort.',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'Assigned teacher.',
    subject_id INT UNSIGNED NOT NULL COMMENT 'Assigned subject.',
    assignment_role ENUM('primary', 'co_teacher') NOT NULL DEFAULT 'primary' COMMENT 'Teacher role in the class and subject.',
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Assignment timestamp.',
    status ENUM('active', 'completed', 'archived') NOT NULL DEFAULT 'active' COMMENT 'Assignment lifecycle status.',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation timestamp.',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp.',
    CONSTRAINT pk_class_assignments PRIMARY KEY (class_assignment_id),
    CONSTRAINT uk_class_assignments_teacher_subject UNIQUE (class_id, user_id, subject_id),
    CONSTRAINT fk_class_assignments_class
        FOREIGN KEY (class_id) REFERENCES classes (class_id),
    CONSTRAINT fk_class_assignments_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_class_assignments_subject
        FOREIGN KEY (subject_id) REFERENCES subjects (subject_id),
    INDEX idx_class_assignments_class_subject (class_id, subject_id, status)
) COMMENT='Teacher and subject assignments for a class cohort.';

CREATE TABLE class_lists (
    class_list_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique learner-membership identifier.',
    class_id BIGINT UNSIGNED NOT NULL COMMENT 'Class cohort containing the learner.',
    student_id BIGINT UNSIGNED NOT NULL COMMENT 'Learner in the class cohort.',
    CONSTRAINT pk_class_lists PRIMARY KEY (class_list_id),
    CONSTRAINT uk_class_lists_class_student UNIQUE (class_id, student_id),
    CONSTRAINT fk_class_lists_class
        FOREIGN KEY (class_id) REFERENCES classes (class_id),
    CONSTRAINT fk_class_lists_student
        FOREIGN KEY (student_id) REFERENCES students (student_id),
    INDEX idx_class_lists_student (student_id)
) COMMENT='Normalized learner membership for each class cohort.';

CREATE TABLE root_tags (
    root_tag_id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique root-tag identifier.',
    curriculum_id INT UNSIGNED NOT NULL COMMENT 'Curriculum that defines the broad topic.',
    root_tag_name VARCHAR(100) NOT NULL COMMENT 'Broad curriculum topic name.',
    description TEXT NULL COMMENT 'Definition or scope of the broad topic.',
    status ENUM('active', 'inactive') NOT NULL DEFAULT 'active' COMMENT 'Root-tag availability status.',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation timestamp.',
    CONSTRAINT pk_root_tags PRIMARY KEY (root_tag_id),
    CONSTRAINT uk_root_tags_curriculum_name UNIQUE (curriculum_id, root_tag_name),
    CONSTRAINT fk_root_tags_curriculum
        FOREIGN KEY (curriculum_id) REFERENCES curriculums (curriculum_id)
) COMMENT='Curriculum-specific root competency categories.';

CREATE TABLE competency_tags (
    competency_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique competency identifier.',
    root_tag_id INT UNSIGNED NOT NULL COMMENT 'Broad root category of the competency.',
    competency_name VARCHAR(255) NOT NULL COMMENT 'Official competency statement or label.',
    CONSTRAINT pk_competency_tags PRIMARY KEY (competency_id),
    CONSTRAINT uk_competency_tags_root_name UNIQUE (root_tag_id, competency_name),
    CONSTRAINT fk_competency_tags_root_tag
        FOREIGN KEY (root_tag_id) REFERENCES root_tags (root_tag_id)
) COMMENT='Competency statements under curriculum root categories.';

CREATE TABLE skills (
    skill_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique contextualized-skill identifier.',
    competency_id BIGINT UNSIGNED NOT NULL COMMENT 'Competency represented by the skill.',
    term_period_id INT UNSIGNED NOT NULL COMMENT 'Term in which the skill is taught or assessed.',
    grade_level_id INT UNSIGNED NOT NULL COMMENT 'Target grade level.',
    subject_id INT UNSIGNED NOT NULL COMMENT 'Target subject.',
    CONSTRAINT pk_skills PRIMARY KEY (skill_id),
    CONSTRAINT uk_skills_context UNIQUE (competency_id, term_period_id, grade_level_id, subject_id),
    CONSTRAINT fk_skills_competency
        FOREIGN KEY (competency_id) REFERENCES competency_tags (competency_id),
    CONSTRAINT fk_skills_term_period
        FOREIGN KEY (term_period_id) REFERENCES term_periods (term_period_id),
    CONSTRAINT fk_skills_grade_level
        FOREIGN KEY (grade_level_id) REFERENCES grade_levels (grade_level_id),
    CONSTRAINT fk_skills_subject
        FOREIGN KEY (subject_id) REFERENCES subjects (subject_id)
) COMMENT='Competencies contextualized by term, grade level, and subject.';

CREATE TABLE interventions (
    intervention_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique intervention identifier.',
    skill_id BIGINT UNSIGNED NOT NULL COMMENT 'Skill addressed by the intervention.',
    intervention_type ENUM('remediation', 'review', 'practice', 'enrichment', 'other') NOT NULL COMMENT 'Type of instructional support.',
    description TEXT NOT NULL COMMENT 'Teacher-facing intervention guidance.',
    CONSTRAINT pk_interventions PRIMARY KEY (intervention_id),
    CONSTRAINT uk_interventions_skill_type UNIQUE (skill_id, intervention_type),
    CONSTRAINT fk_interventions_skill
        FOREIGN KEY (skill_id) REFERENCES skills (skill_id)
) COMMENT='Master intervention recommendations for specific skills.';

CREATE TABLE tests (
    test_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique assessment identifier.',
    class_assignment_id BIGINT UNSIGNED NOT NULL COMMENT 'Teacher, subject, and class assignment that owns the assessment.',
    term_period_id INT UNSIGNED NOT NULL COMMENT 'Term period of the assessment.',
    test_name VARCHAR(120) NOT NULL COMMENT 'Assessment title.',
    test_type ENUM('quiz', 'exam', 'diagnostic', 'long_test', 'other') NOT NULL COMMENT 'Assessment category.',
    test_date DATE NOT NULL COMMENT 'Scheduled or administered date.',
    instructions TEXT NULL COMMENT 'General assessment instructions.',
    total_items INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Validated snapshot of the total question count.',
    status ENUM('draft', 'active', 'completed', 'archived') NOT NULL DEFAULT 'draft' COMMENT 'Assessment lifecycle status.',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation timestamp.',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp.',
    CONSTRAINT pk_tests PRIMARY KEY (test_id),
    CONSTRAINT fk_tests_class_assignment
        FOREIGN KEY (class_assignment_id) REFERENCES class_assignments (class_assignment_id),
    CONSTRAINT fk_tests_term_period
        FOREIGN KEY (term_period_id) REFERENCES term_periods (term_period_id),
    INDEX idx_tests_assignment_term (class_assignment_id, term_period_id, status)
) COMMENT='Assessments owned by a teacher-class-subject assignment.';

CREATE TABLE test_parts (
    test_part_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique assessment-part identifier.',
    test_id BIGINT UNSIGNED NOT NULL COMMENT 'Parent assessment.',
    part_order SMALLINT UNSIGNED NOT NULL COMMENT 'Display and processing order.',
    part_name VARCHAR(80) NOT NULL COMMENT 'Part label such as Part I.',
    part_type VARCHAR(30) NOT NULL DEFAULT 'multiple_choice' COMMENT 'Question format; the initial OMR scope uses multiple choice.',
    number_of_items SMALLINT UNSIGNED NOT NULL COMMENT 'Validated snapshot of questions in the part.',
    points_per_item DECIMAL(5,2) NOT NULL DEFAULT 1.00 COMMENT 'Default points for each correct answer.',
    CONSTRAINT pk_test_parts PRIMARY KEY (test_part_id),
    CONSTRAINT uk_test_parts_order UNIQUE (test_id, part_order),
    CONSTRAINT fk_test_parts_test
        FOREIGN KEY (test_id) REFERENCES tests (test_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_test_parts_number_of_items CHECK (number_of_items > 0),
    CONSTRAINT chk_test_parts_points_per_item CHECK (points_per_item > 0)
) COMMENT='Ordered groups of questions inside an assessment.';

CREATE TABLE questions (
    question_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique question identifier.',
    test_part_id BIGINT UNSIGNED NOT NULL COMMENT 'Test part containing the question.',
    item_number INT UNSIGNED NOT NULL COMMENT 'Question number within the part.',
    question_text TEXT NOT NULL COMMENT 'Actual assessment question.',
    option_a VARCHAR(255) NOT NULL COMMENT 'Text for option A.',
    option_b VARCHAR(255) NOT NULL COMMENT 'Text for option B.',
    option_c VARCHAR(255) NOT NULL COMMENT 'Text for option C.',
    option_d VARCHAR(255) NOT NULL COMMENT 'Text for option D.',
    option_e VARCHAR(255) NULL COMMENT 'Optional text for option E.',
    CONSTRAINT pk_questions PRIMARY KEY (question_id),
    CONSTRAINT uk_questions_part_item UNIQUE (test_part_id, item_number),
    CONSTRAINT fk_questions_test_part
        FOREIGN KEY (test_part_id) REFERENCES test_parts (test_part_id)
        ON DELETE CASCADE
) COMMENT='Individual multiple-choice questions used by answer keys and OMR.';

CREATE TABLE answer_keys (
    answer_key_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique answer-key identifier.',
    question_id BIGINT UNSIGNED NOT NULL COMMENT 'Question answered by this key.',
    correct_option CHAR(1) NOT NULL COMMENT 'Correct option from A through E.',
    CONSTRAINT pk_answer_keys PRIMARY KEY (answer_key_id),
    CONSTRAINT uk_answer_keys_question UNIQUE (question_id),
    CONSTRAINT fk_answer_keys_question
        FOREIGN KEY (question_id) REFERENCES questions (question_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_answer_keys_option CHECK (correct_option IN ('A', 'B', 'C', 'D', 'E'))
) COMMENT='One correct answer for each assessment question.';

CREATE TABLE mappings (
    mapping_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique question-to-skill mapping identifier.',
    question_id BIGINT UNSIGNED NOT NULL COMMENT 'Question being classified.',
    skill_id BIGINT UNSIGNED NOT NULL COMMENT 'Skill measured by the question.',
    CONSTRAINT pk_mappings PRIMARY KEY (mapping_id),
    CONSTRAINT uk_mappings_question_skill UNIQUE (question_id, skill_id),
    CONSTRAINT fk_mappings_question
        FOREIGN KEY (question_id) REFERENCES questions (question_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_mappings_skill
        FOREIGN KEY (skill_id) REFERENCES skills (skill_id)
) COMMENT='Normalized many-to-many mapping between questions and measured skills.';

CREATE TABLE scan_sessions (
    scan_session_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Central scan-session identifier.',
    scan_uuid CHAR(36) NOT NULL COMMENT 'Offline-generated idempotency identifier.',
    scanned_by_user_id BIGINT UNSIGNED NOT NULL COMMENT 'Teacher operating the scanner.',
    verified_by_user_id BIGINT UNSIGNED NULL COMMENT 'Teacher who verified the OMR detections.',
    device_identifier VARCHAR(100) NULL COMMENT 'Mobile installation or device identifier.',
    template_version VARCHAR(50) NOT NULL COMMENT 'OMR template version used.',
    scanner_version VARCHAR(50) NOT NULL COMMENT 'Scanner or OpenCV version used.',
    image_hash CHAR(64) NULL COMMENT 'SHA-256 image hash for duplicate detection and audit.',
    scan_status ENUM('captured', 'processing', 'needs_verification', 'verified', 'failed') NOT NULL DEFAULT 'captured' COMMENT 'Current scan workflow status.',
    scanned_at TIMESTAMP NOT NULL COMMENT 'Image-capture timestamp.',
    verified_at TIMESTAMP NULL COMMENT 'Teacher-verification completion timestamp.',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation timestamp.',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp.',
    CONSTRAINT pk_scan_sessions PRIMARY KEY (scan_session_id),
    CONSTRAINT uk_scan_sessions_uuid UNIQUE (scan_uuid),
    CONSTRAINT fk_scan_sessions_scanned_by_user
        FOREIGN KEY (scanned_by_user_id) REFERENCES users (user_id),
    CONSTRAINT fk_scan_sessions_verified_by_user
        FOREIGN KEY (verified_by_user_id) REFERENCES users (user_id),
    INDEX idx_scan_sessions_user_status (scanned_by_user_id, scan_status, scanned_at)
) COMMENT='Central audit record for each uploaded OMR scan.';

CREATE TABLE test_results (
    test_result_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Central verified-result identifier.',
    result_uuid CHAR(36) NOT NULL COMMENT 'Offline-generated idempotency identifier.',
    test_id BIGINT UNSIGNED NOT NULL COMMENT 'Assessment taken.',
    class_list_id BIGINT UNSIGNED NOT NULL COMMENT 'Learner membership that took the assessment.',
    attempt_number SMALLINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Attempt sequence for the learner and assessment.',
    total_score DECIMAL(8,2) NOT NULL COMMENT 'Sum of verified points earned.',
    max_score DECIMAL(8,2) NOT NULL COMMENT 'Maximum possible points snapshot.',
    items_evaluated INT UNSIGNED NOT NULL COMMENT 'Number of evaluated questions.',
    checked_at TIMESTAMP NOT NULL COMMENT 'Teacher verification or checking completion timestamp.',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation timestamp.',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp.',
    CONSTRAINT pk_test_results PRIMARY KEY (test_result_id),
    CONSTRAINT uk_test_results_uuid UNIQUE (result_uuid),
    CONSTRAINT uk_test_results_attempt UNIQUE (test_id, class_list_id, attempt_number),
    CONSTRAINT fk_test_results_test
        FOREIGN KEY (test_id) REFERENCES tests (test_id),
    CONSTRAINT fk_test_results_class_list
        FOREIGN KEY (class_list_id) REFERENCES class_lists (class_list_id),
    CONSTRAINT chk_test_results_scores CHECK (total_score >= 0 AND max_score >= 0 AND total_score <= max_score)
) COMMENT='Verified overall learner attempts from OMR or approved manual capture.';

CREATE TABLE test_result_scans (
    test_result_scan_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique result-to-scan audit-link identifier.',
    test_result_id BIGINT UNSIGNED NOT NULL COMMENT 'Verified learner result associated with the capture.',
    scan_session_id BIGINT UNSIGNED NOT NULL COMMENT 'Captured OMR scan retained for the result audit trail.',
    link_status ENUM('selected', 'superseded', 'rejected') NOT NULL COMMENT 'Whether this capture is authoritative, replaced, or rejected.',
    selected_result_id BIGINT UNSIGNED AS (
        CASE WHEN link_status = 'selected' THEN test_result_id ELSE NULL END
    ) VIRTUAL COMMENT 'Generated key used to enforce one selected scan per result.',
    decided_by_user_id BIGINT UNSIGNED NOT NULL COMMENT 'Teacher who selected, superseded, or rejected the capture.',
    decision_reason VARCHAR(255) NULL COMMENT 'Reason for superseding or rejecting the capture.',
    linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Timestamp when the capture was linked to the result.',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last disposition update timestamp.',
    CONSTRAINT pk_test_result_scans PRIMARY KEY (test_result_scan_id),
    CONSTRAINT uk_test_result_scans_scan UNIQUE (scan_session_id),
    CONSTRAINT uk_test_result_scans_result_scan UNIQUE (test_result_id, scan_session_id),
    CONSTRAINT uk_test_result_scans_one_selected UNIQUE (selected_result_id),
    CONSTRAINT fk_test_result_scans_test_result
        FOREIGN KEY (test_result_id) REFERENCES test_results (test_result_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_test_result_scans_scan_session
        FOREIGN KEY (scan_session_id) REFERENCES scan_sessions (scan_session_id),
    CONSTRAINT fk_test_result_scans_decided_by_user
        FOREIGN KEY (decided_by_user_id) REFERENCES users (user_id),
    INDEX idx_test_result_scans_result_status (test_result_id, link_status)
) COMMENT='Audit-ready history of selected, superseded, and rejected scans for each learner result.';

CREATE TABLE student_answers (
    student_answer_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique verified-answer identifier.',
    test_result_id BIGINT UNSIGNED NOT NULL COMMENT 'Overall learner attempt.',
    question_id BIGINT UNSIGNED NOT NULL COMMENT 'Question answered.',
    verified_by_user_id BIGINT UNSIGNED NOT NULL COMMENT 'Teacher who confirmed or corrected the answer.',
    answer_uuid CHAR(36) NOT NULL COMMENT 'Offline-generated answer idempotency identifier.',
    capture_source ENUM('omr', 'teacher_correction', 'manual') NOT NULL COMMENT 'Source of the final verified answer.',
    verified_at TIMESTAMP NOT NULL COMMENT 'Answer-verification timestamp.',
    selected_option CHAR(1) NULL COMMENT 'Final option confirmed by the teacher; null for blank or invalid answers.',
    answer_status ENUM('answered', 'blank', 'multiple', 'invalid') NOT NULL COMMENT 'Final verified answer condition.',
    is_correct BOOLEAN NOT NULL COMMENT 'Whether the selected option matched the answer key.',
    points_earned DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT 'Points awarded for the question.',
    correction_reason VARCHAR(255) NULL COMMENT 'Reason supplied when the teacher corrected the detected answer.',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last answer update timestamp.',
    CONSTRAINT pk_student_answers PRIMARY KEY (student_answer_id),
    CONSTRAINT uk_student_answers_uuid UNIQUE (answer_uuid),
    CONSTRAINT uk_student_answers_result_question UNIQUE (test_result_id, question_id),
    CONSTRAINT fk_student_answers_test_result
        FOREIGN KEY (test_result_id) REFERENCES test_results (test_result_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_student_answers_question
        FOREIGN KEY (question_id) REFERENCES questions (question_id),
    CONSTRAINT fk_student_answers_verified_by_user
        FOREIGN KEY (verified_by_user_id) REFERENCES users (user_id),
    CONSTRAINT chk_student_answers_option CHECK (selected_option IS NULL OR selected_option IN ('A', 'B', 'C', 'D', 'E')),
    CONSTRAINT chk_student_answers_points CHECK (points_earned >= 0)
) COMMENT='Final teacher-verified answer for every evaluated question.';

CREATE TABLE intervention_results (
    intervention_result_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique generated recommendation identifier.',
    test_result_id BIGINT UNSIGNED NOT NULL COMMENT 'Learner result receiving the recommendation.',
    intervention_id BIGINT UNSIGNED NOT NULL COMMENT 'Recommended intervention.',
    CONSTRAINT pk_intervention_results PRIMARY KEY (intervention_result_id),
    CONSTRAINT uk_intervention_results_result_intervention UNIQUE (test_result_id, intervention_id),
    CONSTRAINT fk_intervention_results_test_result
        FOREIGN KEY (test_result_id) REFERENCES test_results (test_result_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_intervention_results_intervention
        FOREIGN KEY (intervention_id) REFERENCES interventions (intervention_id)
) COMMENT='Interventions generated from a learner assessment result.';

CREATE TABLE syncs (
    sync_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Central synchronization-batch identifier.',
    sync_uuid CHAR(36) NOT NULL COMMENT 'Offline-generated idempotency identifier for the batch.',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'Teacher or user who initiated the synchronization.',
    test_id BIGINT UNSIGNED NULL COMMENT 'Assessment involved when the batch is assessment-specific.',
    device_identifier VARCHAR(100) NULL COMMENT 'Mobile installation or device identifier.',
    direction ENUM('download', 'upload') NOT NULL COMMENT 'Direction of data transfer.',
    sync_status ENUM('pending', 'in_progress', 'partial_success', 'success', 'failed') NOT NULL DEFAULT 'pending' COMMENT 'Overall synchronization-batch status.',
    payload_hash CHAR(64) NULL COMMENT 'Hash used for duplicate-payload detection.',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Actual synchronization start time.',
    completed_at TIMESTAMP NULL COMMENT 'Synchronization completion time.',
    error_message TEXT NULL COMMENT 'Batch-level failure details.',
    CONSTRAINT pk_syncs PRIMARY KEY (sync_id),
    CONSTRAINT uk_syncs_uuid UNIQUE (sync_uuid),
    CONSTRAINT fk_syncs_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_syncs_test
        FOREIGN KEY (test_id) REFERENCES tests (test_id),
    INDEX idx_syncs_user_started (user_id, started_at),
    INDEX idx_syncs_status_started (sync_status, started_at)
) COMMENT='One mobile synchronization batch containing zero or more result items.';

CREATE TABLE sync_items (
    sync_item_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique item identifier within a synchronization batch.',
    sync_id BIGINT UNSIGNED NOT NULL COMMENT 'Parent synchronization batch.',
    result_uuid CHAR(36) NOT NULL COMMENT 'Offline result identifier available before central insertion.',
    test_result_id BIGINT UNSIGNED NULL COMMENT 'Central result identifier after successful insertion or matching.',
    sync_action ENUM('create', 'update') NOT NULL COMMENT 'Requested result operation.',
    sync_status ENUM('pending', 'success', 'failed', 'skipped') NOT NULL DEFAULT 'pending' COMMENT 'Individual result-processing status.',
    error_code VARCHAR(50) NULL COMMENT 'Machine-readable failure code.',
    error_message TEXT NULL COMMENT 'Detailed item-level failure message.',
    synced_at TIMESTAMP NULL COMMENT 'Successful item-processing timestamp.',
    CONSTRAINT pk_sync_items PRIMARY KEY (sync_item_id),
    CONSTRAINT uk_sync_items_batch_result_uuid UNIQUE (sync_id, result_uuid),
    CONSTRAINT fk_sync_items_sync
        FOREIGN KEY (sync_id) REFERENCES syncs (sync_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_sync_items_test_result
        FOREIGN KEY (test_result_id) REFERENCES test_results (test_result_id),
    INDEX idx_sync_items_result_uuid (result_uuid),
    INDEX idx_sync_items_status (sync_id, sync_status)
) COMMENT='Individual learner results processed inside one synchronization batch.';

CREATE TABLE omr_detections (
    omr_detection_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique raw OMR-detection identifier.',
    scan_session_id BIGINT UNSIGNED NOT NULL COMMENT 'Parent scanned sheet.',
    question_id BIGINT UNSIGNED NOT NULL COMMENT 'Question represented by the bubble position.',
    detected_option CHAR(1) NULL COMMENT 'Scanner-detected option; null for blank or ambiguous marks.',
    confidence_score DECIMAL(5,4) NOT NULL COMMENT 'Detection confidence from 0.0000 through 1.0000.',
    detection_status ENUM('detected', 'blank', 'multiple_marks', 'uncertain') NOT NULL COMMENT 'Raw scanner interpretation.',
    verification_status ENUM('pending', 'confirmed', 'corrected') NOT NULL DEFAULT 'pending' COMMENT 'Teacher-verification status.',
    raw_mark TEXT NOT NULL COMMENT 'Structured darkness or confidence measurements for each option.',
    detected_at TIMESTAMP NOT NULL COMMENT 'Detection timestamp.',
    CONSTRAINT pk_omr_detections PRIMARY KEY (omr_detection_id),
    CONSTRAINT uk_omr_detections_scan_question UNIQUE (scan_session_id, question_id),
    CONSTRAINT fk_omr_detections_scan_session
        FOREIGN KEY (scan_session_id) REFERENCES scan_sessions (scan_session_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_omr_detections_question
        FOREIGN KEY (question_id) REFERENCES questions (question_id),
    CONSTRAINT chk_omr_detections_option CHECK (detected_option IS NULL OR detected_option IN ('A', 'B', 'C', 'D', 'E')),
    CONSTRAINT chk_omr_detections_confidence CHECK (confidence_score >= 0 AND confidence_score <= 1)
) COMMENT='Raw OMR detections retained separately from final teacher-verified answers.';

CREATE TABLE auth_sessions (
    auth_session_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Central authenticated-session identifier.',
    session_uuid CHAR(36) NOT NULL COMMENT 'Public unique session identifier.',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'User who owns the session.',
    refresh_token_hash CHAR(64) NOT NULL COMMENT 'SHA-256 refresh-token hash; never stores the raw token.',
    device_identifier VARCHAR(100) NULL COMMENT 'Browser or mobile-device identifier.',
    ip_address VARCHAR(45) NULL COMMENT 'IPv4 or IPv6 address at session creation.',
    user_agent TEXT NULL COMMENT 'Browser, operating-system, or application information.',
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Session creation timestamp.',
    expires_at TIMESTAMP NOT NULL COMMENT 'Session expiration timestamp.',
    last_used_at TIMESTAMP NULL COMMENT 'Most recent refresh-token use timestamp.',
    revoked_at TIMESTAMP NULL COMMENT 'Logout or security-revocation timestamp.',
    CONSTRAINT pk_auth_sessions PRIMARY KEY (auth_session_id),
    CONSTRAINT uk_auth_sessions_uuid UNIQUE (session_uuid),
    CONSTRAINT uk_auth_sessions_refresh_token_hash UNIQUE (refresh_token_hash),
    CONSTRAINT fk_auth_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    INDEX idx_auth_sessions_user_expiry (user_id, expires_at),
    INDEX idx_auth_sessions_user_revoked (user_id, revoked_at)
) COMMENT='Secure login sessions and refresh-token lifecycle records.';

CREATE TABLE login_attempts (
    login_attempt_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique login-attempt identifier.',
    user_id BIGINT UNSIGNED NULL COMMENT 'Matched account; nullable when the email is unknown.',
    attempted_email VARCHAR(120) NOT NULL COMMENT 'Email supplied during the login attempt.',
    ip_address VARCHAR(45) NULL COMMENT 'IPv4 or IPv6 source address.',
    device_identifier VARCHAR(100) NULL COMMENT 'Browser or mobile-device identifier.',
    user_agent TEXT NULL COMMENT 'Browser, operating-system, or application information.',
    was_successful BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Whether authentication succeeded.',
    failure_reason ENUM('invalid_credentials', 'pending_approval', 'rejected_account', 'inactive_account', 'locked_account', 'email_not_verified', 'rate_limited', 'other') NULL COMMENT 'Controlled failure reason; null for successful attempts.',
    attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Login-attempt timestamp.',
    CONSTRAINT pk_login_attempts PRIMARY KEY (login_attempt_id),
    CONSTRAINT fk_login_attempts_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON DELETE SET NULL,
    INDEX idx_login_attempts_user_time (user_id, attempted_at),
    INDEX idx_login_attempts_email_time (attempted_email, attempted_at),
    INDEX idx_login_attempts_ip_time (ip_address, attempted_at)
) COMMENT='Successful and failed login attempts used for security monitoring and lockout.';

CREATE TABLE audit_logs (
    audit_log_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Central audit-event identifier.',
    audit_uuid CHAR(36) NOT NULL COMMENT 'Public unique audit-event identifier.',
    user_id BIGINT UNSIGNED NULL COMMENT 'User who performed the action; null for anonymous or system events.',
    action VARCHAR(100) NOT NULL COMMENT 'Action such as LOGIN, IMPORT_SF1, or VERIFY_OMR.',
    entity_type VARCHAR(100) NULL COMMENT 'Affected entity or table name.',
    entity_id VARCHAR(100) NULL COMMENT 'Affected numeric identifier or UUID.',
    outcome ENUM('success', 'failed', 'denied') NOT NULL COMMENT 'Result of the attempted action.',
    ip_address VARCHAR(45) NULL COMMENT 'IPv4 or IPv6 source address.',
    device_identifier VARCHAR(100) NULL COMMENT 'Browser or mobile-device identifier.',
    user_agent TEXT NULL COMMENT 'Browser, operating-system, or application information.',
    details JSON NULL COMMENT 'Structured audit details without passwords or raw tokens.',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Audit-event timestamp.',
    CONSTRAINT pk_audit_logs PRIMARY KEY (audit_log_id),
    CONSTRAINT uk_audit_logs_uuid UNIQUE (audit_uuid),
    CONSTRAINT fk_audit_logs_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON DELETE SET NULL,
    INDEX idx_audit_logs_user_time (user_id, created_at),
    INDEX idx_audit_logs_entity (entity_type, entity_id),
    INDEX idx_audit_logs_action_time (action, created_at),
    INDEX idx_audit_logs_outcome_time (outcome, created_at)
) COMMENT='Append-only audit trail for security-sensitive and business-critical actions.';
