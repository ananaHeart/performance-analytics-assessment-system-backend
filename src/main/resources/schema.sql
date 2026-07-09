CREATE TABLE IF NOT EXISTS school_profile (
    school_id VARCHAR(20) NOT NULL,
    school_name VARCHAR(50) NOT NULL,
    region_division VARCHAR(50) NOT NULL,
    CONSTRAINT pk_school_profile PRIMARY KEY (school_id)
);

CREATE TABLE IF NOT EXISTS `user` (
    user_id INT NOT NULL AUTO_INCREMENT,
    first_name VARCHAR(30) NOT NULL,
    last_name VARCHAR(30) NOT NULL,
    gender ENUM('male', 'female') NOT NULL,
    date_birth DATE NOT NULL,
    email VARCHAR(80) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    role ENUM('principal', 'teacher') NOT NULL,
    status ENUM('pending', 'active', 'rejected') NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user PRIMARY KEY (user_id),
    CONSTRAINT uk_user_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS academic_year (
    academic_year_id INT NOT NULL AUTO_INCREMENT,
    year_name VARCHAR(15) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status ENUM('Active', 'Completed') NOT NULL,
    CONSTRAINT pk_academic_year PRIMARY KEY (academic_year_id),
    CONSTRAINT uk_academic_year_year_name UNIQUE (year_name)
);

CREATE TABLE IF NOT EXISTS grade_level (
    grade_level_id INT NOT NULL AUTO_INCREMENT,
    grade_level_name VARCHAR(20) NOT NULL,
    CONSTRAINT pk_grade_level PRIMARY KEY (grade_level_id),
    CONSTRAINT uk_grade_level_name UNIQUE (grade_level_name)
);

CREATE TABLE IF NOT EXISTS section (
    section_id INT NOT NULL AUTO_INCREMENT,
    grade_level_id INT NOT NULL,
    section_name VARCHAR(50) NOT NULL,
    CONSTRAINT pk_section PRIMARY KEY (section_id),
    CONSTRAINT uk_section_grade_level_name UNIQUE (grade_level_id, section_name),
    CONSTRAINT fk_section_grade_level
        FOREIGN KEY (grade_level_id) REFERENCES grade_level (grade_level_id),
    INDEX idx_section_grade_level_id (grade_level_id)
);

CREATE TABLE IF NOT EXISTS subject (
    subject_id INT NOT NULL AUTO_INCREMENT,
    subject_code VARCHAR(20),
    subject_name VARCHAR(30) NOT NULL,
    CONSTRAINT pk_subject PRIMARY KEY (subject_id),
    CONSTRAINT uk_subject_code UNIQUE (subject_code)
);

CREATE TABLE IF NOT EXISTS student (
    student_id INT NOT NULL AUTO_INCREMENT,
    student_lrn VARCHAR(12) NOT NULL,
    first_name VARCHAR(30) NOT NULL,
    last_name VARCHAR(30) NOT NULL,
    gender ENUM('male', 'female') NOT NULL,
    CONSTRAINT pk_student PRIMARY KEY (student_id),
    CONSTRAINT uk_student_lrn UNIQUE (student_lrn)
);

CREATE TABLE IF NOT EXISTS student_enrollment (
    student_enrollment_id INT NOT NULL AUTO_INCREMENT,
    student_id INT NOT NULL,
    section_id INT NOT NULL,
    academic_year_id INT NOT NULL,
    CONSTRAINT pk_student_enrollment PRIMARY KEY (student_enrollment_id),
    CONSTRAINT uk_student_enrollment UNIQUE (student_id, academic_year_id),
    CONSTRAINT fk_student_enrollment_student
        FOREIGN KEY (student_id) REFERENCES student (student_id),
    CONSTRAINT fk_student_enrollment_section
        FOREIGN KEY (section_id) REFERENCES section (section_id),
    CONSTRAINT fk_student_enrollment_academic_year
        FOREIGN KEY (academic_year_id) REFERENCES academic_year (academic_year_id),
    INDEX idx_student_enrollment_student_id (student_id),
    INDEX idx_student_enrollment_section_id (section_id),
    INDEX idx_student_enrollment_academic_year_id (academic_year_id)
);

CREATE TABLE IF NOT EXISTS `class` (
    class_id INT NOT NULL AUTO_INCREMENT,
    academic_year_id INT NOT NULL,
    user_id INT NOT NULL,
    subject_id INT NOT NULL,
    section_id INT NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_class PRIMARY KEY (class_id),
    CONSTRAINT uk_class_assignment UNIQUE (academic_year_id, user_id, subject_id, section_id),
    CONSTRAINT fk_class_academic_year
        FOREIGN KEY (academic_year_id) REFERENCES academic_year (academic_year_id),
    CONSTRAINT fk_class_user
        FOREIGN KEY (user_id) REFERENCES `user` (user_id),
    CONSTRAINT fk_class_subject
        FOREIGN KEY (subject_id) REFERENCES subject (subject_id),
    CONSTRAINT fk_class_section
        FOREIGN KEY (section_id) REFERENCES section (section_id),
    INDEX idx_class_academic_year_id (academic_year_id),
    INDEX idx_class_user_id (user_id),
    INDEX idx_class_subject_id (subject_id),
    INDEX idx_class_section_id (section_id),
    INDEX idx_class_section_subject_user (section_id, subject_id, user_id)
);

CREATE TABLE IF NOT EXISTS competency_tags (
    competency_id INT NOT NULL AUTO_INCREMENT,
    parent_competency_id INT,
    grade_level_id INT NOT NULL,
    subject_id INT NOT NULL,
    competency_name VARCHAR(50) NOT NULL,
    CONSTRAINT pk_competency_tags PRIMARY KEY (competency_id),
    CONSTRAINT uk_competency_tags UNIQUE (grade_level_id, subject_id, competency_name),
    CONSTRAINT fk_competency_tags_parent
        FOREIGN KEY (parent_competency_id) REFERENCES competency_tags (competency_id),
    CONSTRAINT fk_competency_tags_grade_level
        FOREIGN KEY (grade_level_id) REFERENCES grade_level (grade_level_id),
    CONSTRAINT fk_competency_tags_subject
        FOREIGN KEY (subject_id) REFERENCES subject (subject_id),
    INDEX idx_competency_tags_parent_id (parent_competency_id),
    INDEX idx_competency_tags_grade_level_id (grade_level_id),
    INDEX idx_competency_tags_subject_id (subject_id),
    INDEX idx_competency_tags_grade_level_subject (grade_level_id, subject_id)
);

CREATE TABLE IF NOT EXISTS grading_period (
    grading_period_id INT NOT NULL AUTO_INCREMENT,
    academic_year_id INT NOT NULL,
    period_name VARCHAR(50) NOT NULL,
    period_order INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status ENUM('Active', 'Completed') NOT NULL DEFAULT 'Active',
    CONSTRAINT pk_grading_period PRIMARY KEY (grading_period_id),
    CONSTRAINT uk_grading_period_name UNIQUE (academic_year_id, period_name),
    CONSTRAINT uk_grading_period_order UNIQUE (academic_year_id, period_order),
    CONSTRAINT fk_grading_period_academic_year
        FOREIGN KEY (academic_year_id) REFERENCES academic_year (academic_year_id)
        ON DELETE CASCADE,
    INDEX idx_grading_period_academic_year_id (academic_year_id),
    INDEX idx_grading_period_status (status)
);

CREATE TABLE IF NOT EXISTS `test` (
    test_id INT NOT NULL AUTO_INCREMENT,
    class_id INT NOT NULL,
    test_name VARCHAR(50) NOT NULL,
    test_type ENUM('Quiz', 'Exam', 'Long Test') NOT NULL,
    test_date DATE NOT NULL,
    grading_period_id INT,
    test_status ENUM('Draft', 'Active', 'Completed') NOT NULL,
    CONSTRAINT pk_test PRIMARY KEY (test_id),
    CONSTRAINT fk_test_class
        FOREIGN KEY (class_id) REFERENCES `class` (class_id),
    CONSTRAINT fk_test_grading_period
        FOREIGN KEY (grading_period_id) REFERENCES grading_period (grading_period_id),
    INDEX idx_test_class_id (class_id),
    INDEX idx_test_grading_period_id (grading_period_id)
);

CREATE TABLE IF NOT EXISTS test_part (
    test_part_id INT NOT NULL AUTO_INCREMENT,
    test_id INT NOT NULL,
    competency_id INT NOT NULL,
    part_order VARCHAR(15) NOT NULL,
    part_type TEXT NOT NULL,
    number_of_items INT NOT NULL,
    points_per_item INT NOT NULL,
    answer_key TEXT,
    CONSTRAINT pk_test_part PRIMARY KEY (test_part_id),
    CONSTRAINT uk_test_part_order UNIQUE (test_id, part_order),
    CONSTRAINT fk_test_part_test
        FOREIGN KEY (test_id) REFERENCES `test` (test_id),
    CONSTRAINT fk_test_part_competency
        FOREIGN KEY (competency_id) REFERENCES competency_tags (competency_id),
    INDEX idx_test_part_test_id (test_id),
    INDEX idx_test_part_competency_id (competency_id)
);

CREATE TABLE IF NOT EXISTS test_result (
    test_result_id INT NOT NULL AUTO_INCREMENT,
    test_id INT NOT NULL,
    student_id INT NOT NULL,
    total_score INT NOT NULL,
    raw_answers TEXT,
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_test_result PRIMARY KEY (test_result_id),
    CONSTRAINT uk_test_result_test_student UNIQUE (test_id, student_id),
    CONSTRAINT fk_test_result_test
        FOREIGN KEY (test_id) REFERENCES `test` (test_id),
    CONSTRAINT fk_test_result_student
        FOREIGN KEY (student_id) REFERENCES student (student_id),
    INDEX idx_test_result_test_id (test_id),
    INDEX idx_test_result_student_id (student_id)
);

CREATE TABLE IF NOT EXISTS part_skill_mapping (
    mapping_id INT NOT NULL AUTO_INCREMENT,
    test_part_id INT NOT NULL,
    competency_id INT NOT NULL,
    mapping_mode ENUM('RANGE', 'CUSTOM') NOT NULL DEFAULT 'RANGE',
    item_count INT NOT NULL,
    start_item INT,
    end_item INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_part_skill_mapping PRIMARY KEY (mapping_id),
    CONSTRAINT uk_part_skill_mapping_competency UNIQUE (test_part_id, competency_id),
    CONSTRAINT fk_part_skill_mapping_test_part
        FOREIGN KEY (test_part_id) REFERENCES test_part (test_part_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_part_skill_mapping_competency
        FOREIGN KEY (competency_id) REFERENCES competency_tags (competency_id),
    CONSTRAINT chk_part_skill_mapping_item_count
        CHECK (item_count > 0),
    CONSTRAINT chk_part_skill_mapping_range_values
        CHECK (
            (mapping_mode = 'RANGE' AND start_item IS NOT NULL AND end_item IS NOT NULL AND start_item >= 1 AND end_item >= start_item)
            OR
            (mapping_mode = 'CUSTOM' AND start_item IS NULL AND end_item IS NULL)
        ),
    INDEX idx_part_skill_mapping_test_part_id (test_part_id),
    INDEX idx_part_skill_mapping_competency_id (competency_id),
    INDEX idx_part_skill_mapping_mode (mapping_mode),
    INDEX idx_part_skill_mapping_range (test_part_id, start_item, end_item)
);

CREATE TABLE IF NOT EXISTS skill_item (
    mapping_item_id INT NOT NULL AUTO_INCREMENT,
    mapping_id INT NOT NULL,
    item_number INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_skill_item PRIMARY KEY (mapping_item_id),
    CONSTRAINT uk_skill_item_mapping_item UNIQUE (mapping_id, item_number),
    CONSTRAINT fk_skill_item_mapping
        FOREIGN KEY (mapping_id) REFERENCES part_skill_mapping (mapping_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_skill_item_number
        CHECK (item_number >= 1),
    INDEX idx_skill_item_mapping_id (mapping_id),
    INDEX idx_skill_item_item_number (item_number)
);

CREATE TABLE IF NOT EXISTS test_item_result (
    item_result_id INT NOT NULL AUTO_INCREMENT,
    test_part_id INT NOT NULL,
    test_result_id INT NOT NULL,
    item_number INT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    CONSTRAINT pk_test_item_result PRIMARY KEY (item_result_id),
    CONSTRAINT uk_test_item_result UNIQUE (test_result_id, test_part_id, item_number),
    CONSTRAINT fk_test_item_result_test_part
        FOREIGN KEY (test_part_id) REFERENCES test_part (test_part_id),
    CONSTRAINT fk_test_item_result_test_result
        FOREIGN KEY (test_result_id) REFERENCES test_result (test_result_id),
    INDEX idx_test_item_result_item_number (item_number),
    INDEX idx_test_item_result_test_part_id (test_part_id),
    INDEX idx_test_item_result_test_result_id (test_result_id)
);

CREATE TABLE IF NOT EXISTS sync_log (
    sync_id INT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    test_id INT NOT NULL,
    sync_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sync_status ENUM('Success', 'Failed') NOT NULL,
    CONSTRAINT pk_sync_log PRIMARY KEY (sync_id),
    CONSTRAINT fk_sync_log_user
        FOREIGN KEY (user_id) REFERENCES `user` (user_id),
    CONSTRAINT fk_sync_log_test
        FOREIGN KEY (test_id) REFERENCES `test` (test_id),
    INDEX idx_sync_log_user_id (user_id),
    INDEX idx_sync_log_test_id (test_id),
    INDEX idx_sync_log_user_test (user_id, test_id)
);
