-- Local V2 demo setup seed.
-- Purpose:
--   Add the minimum operational data needed for a teacher to create V2 assessments:
--   active academic year, term periods, section/class, students, class list,
--   teacher class assignment, competencies, skills, and intervention templates.
--
-- Scope:
--   performance_assessment_v2_db only. This is not a schema migration.
--   It does not modify V1, TiDB, assessment/results/sync/OMR runtime tables, or passwords.
--
-- Default teacher:
--   teacher.manual.local@example.com

USE performance_assessment_v2_db;

SET @school_id = 'V2-LOCAL-TEST';
SET @teacher_email = 'teacher.manual.local@example.com';
SET @academic_year_name = '2026-2027';
SET @academic_year_start_date = '2026-06-01';
SET @academic_year_end_date = '2027-03-31';
SET @grade_level_name = 'Grade 7';
SET @section_name = 'Rizal';
SET @subject_code = 'ENG';

DELIMITER //

DROP PROCEDURE IF EXISTS seed_v2_teacher_class_competencies//

CREATE PROCEDURE seed_v2_teacher_class_competencies()
BEGIN
    DECLARE v_school_count INT DEFAULT 0;
    DECLARE v_teacher_user_id BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_teacher_status VARCHAR(30);
    DECLARE v_curriculum_id INT UNSIGNED DEFAULT 0;
    DECLARE v_grade_level_id INT UNSIGNED DEFAULT 0;
    DECLARE v_subject_id INT UNSIGNED DEFAULT 0;
    DECLARE v_academic_year_id INT UNSIGNED DEFAULT 0;
    DECLARE v_first_term_period_id INT UNSIGNED DEFAULT 0;
    DECLARE v_section_id INT UNSIGNED DEFAULT 0;
    DECLARE v_class_id BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_class_assignment_id BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_root_tag_id INT UNSIGNED DEFAULT 0;
    DECLARE v_competency_grammar BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_competency_main_idea BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_competency_context BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_skill_grammar BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_skill_main_idea BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_skill_context BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_address_id BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_student_id BIGINT UNSIGNED DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    SELECT COUNT(*)
      INTO v_school_count
      FROM school_profiles
     WHERE school_id = @school_id;
    IF v_school_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Missing @school_id in school_profiles.';
    END IF;

    SELECT COALESCE((
        SELECT u.user_id
          FROM users u
          JOIN roles r ON r.role_id = u.role_id
          JOIN statuses s ON s.status_id = u.status_id
         WHERE LOWER(u.email) = LOWER(@teacher_email)
           AND u.school_id = @school_id
           AND r.role_name = 'teacher'
         LIMIT 1
    ), 0)
      INTO v_teacher_user_id;
    IF v_teacher_user_id = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Missing teacher user. Check @teacher_email and @school_id.';
    END IF;

    SELECT s.status_name
      INTO v_teacher_status
      FROM users u
      JOIN statuses s ON s.status_id = u.status_id
     WHERE u.user_id = v_teacher_user_id;
    IF v_teacher_status <> 'active' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Teacher account exists but is not active.';
    END IF;

    SELECT COALESCE((
        SELECT curriculum_id
          FROM curriculums
         WHERE status = 'active'
         ORDER BY curriculum_id
         LIMIT 1
    ), 0)
      INTO v_curriculum_id;
    IF v_curriculum_id = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Missing active curriculum.';
    END IF;

    SELECT COALESCE((
        SELECT grade_level_id
          FROM grade_levels
         WHERE grade_level_name = @grade_level_name
         LIMIT 1
    ), 0)
      INTO v_grade_level_id;
    IF v_grade_level_id = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Missing grade level reference.';
    END IF;

    SELECT COALESCE((
        SELECT subject_id
          FROM subjects
         WHERE subject_code = @subject_code
         LIMIT 1
    ), 0)
      INTO v_subject_id;
    IF v_subject_id = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Missing subject reference.';
    END IF;

    INSERT INTO academic_years (
        curriculum_id,
        year_name,
        start_date,
        end_date,
        status
    ) VALUES (
        v_curriculum_id,
        @academic_year_name,
        @academic_year_start_date,
        @academic_year_end_date,
        'active'
    )
    ON DUPLICATE KEY UPDATE
        curriculum_id = VALUES(curriculum_id),
        start_date = VALUES(start_date),
        end_date = VALUES(end_date),
        status = VALUES(status);

    SELECT academic_year_id
      INTO v_academic_year_id
      FROM academic_years
     WHERE year_name = @academic_year_name
     LIMIT 1;

    INSERT INTO term_periods (
        academic_year_id,
        term_name,
        term_order,
        status
    ) VALUES
        (v_academic_year_id, 'First Quarter', 1, 'active'),
        (v_academic_year_id, 'Second Quarter', 2, 'planned'),
        (v_academic_year_id, 'Third Quarter', 3, 'planned'),
        (v_academic_year_id, 'Fourth Quarter', 4, 'planned')
    ON DUPLICATE KEY UPDATE
        term_name = VALUES(term_name),
        status = VALUES(status);

    SELECT term_period_id
      INTO v_first_term_period_id
      FROM term_periods
     WHERE academic_year_id = v_academic_year_id
       AND term_order = 1
     LIMIT 1;

    INSERT INTO sections (
        grade_level_id,
        section_name
    ) VALUES (
        v_grade_level_id,
        @section_name
    )
    ON DUPLICATE KEY UPDATE
        section_name = VALUES(section_name);

    SELECT section_id
      INTO v_section_id
      FROM sections
     WHERE grade_level_id = v_grade_level_id
       AND section_name = @section_name
     LIMIT 1;

    INSERT INTO classes (
        academic_year_id,
        section_id,
        status
    ) VALUES (
        v_academic_year_id,
        v_section_id,
        'active'
    )
    ON DUPLICATE KEY UPDATE
        status = 'active';

    SELECT class_id
      INTO v_class_id
      FROM classes
     WHERE academic_year_id = v_academic_year_id
       AND section_id = v_section_id
     LIMIT 1;

    INSERT INTO class_assignments (
        class_id,
        user_id,
        subject_id,
        assignment_role,
        status
    ) VALUES (
        v_class_id,
        v_teacher_user_id,
        v_subject_id,
        'primary',
        'active'
    )
    ON DUPLICATE KEY UPDATE
        assignment_role = VALUES(assignment_role),
        status = VALUES(status);

    SELECT class_assignment_id
      INTO v_class_assignment_id
      FROM class_assignments
     WHERE class_id = v_class_id
       AND user_id = v_teacher_user_id
       AND subject_id = v_subject_id
     LIMIT 1;

    IF NOT EXISTS (SELECT 1 FROM students WHERE student_lrn = '123456789001') THEN
        INSERT INTO addresses (
            country_code,
            region_name,
            province_name,
            city_municipality_name,
            barangay_name,
            address_line,
            postal_code,
            address_source
        ) VALUES (
            'PH',
            'Region VI',
            'Iloilo',
            'Iloilo City',
            'City Proper',
            'Demo learner address 1',
            '5000',
            'sf1_import'
        );
        SET v_address_id = LAST_INSERT_ID();

        INSERT INTO students (
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
        ) VALUES (
            @school_id,
            v_address_id,
            1,
            '123456789001',
            'Juan',
            'Santos',
            'Dela Cruz',
            NULL,
            '2013-04-12',
            'active'
        );
    ELSE
        UPDATE students
           SET school_id = @school_id,
               gender_id = 1,
               first_name = 'Juan',
               middle_name = 'Santos',
               last_name = 'Dela Cruz',
               suffix = NULL,
               birth_date = '2013-04-12',
               status = 'active'
         WHERE student_lrn = '123456789001';
    END IF;

    SELECT student_id
      INTO v_student_id
      FROM students
     WHERE student_lrn = '123456789001'
     LIMIT 1;

    INSERT INTO class_lists (
        class_id,
        student_id
    ) VALUES (
        v_class_id,
        v_student_id
    )
    ON DUPLICATE KEY UPDATE
        student_id = VALUES(student_id);

    IF NOT EXISTS (SELECT 1 FROM students WHERE student_lrn = '123456789002') THEN
        INSERT INTO addresses (
            country_code,
            region_name,
            province_name,
            city_municipality_name,
            barangay_name,
            address_line,
            postal_code,
            address_source
        ) VALUES (
            'PH',
            'Region VI',
            'Iloilo',
            'Iloilo City',
            'Molo',
            'Demo learner address 2',
            '5000',
            'sf1_import'
        );
        SET v_address_id = LAST_INSERT_ID();

        INSERT INTO students (
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
        ) VALUES (
            @school_id,
            v_address_id,
            2,
            '123456789002',
            'Maria',
            'Reyes',
            'Santos',
            NULL,
            '2013-07-18',
            'active'
        );
    ELSE
        UPDATE students
           SET school_id = @school_id,
               gender_id = 2,
               first_name = 'Maria',
               middle_name = 'Reyes',
               last_name = 'Santos',
               suffix = NULL,
               birth_date = '2013-07-18',
               status = 'active'
         WHERE student_lrn = '123456789002';
    END IF;

    SELECT student_id
      INTO v_student_id
      FROM students
     WHERE student_lrn = '123456789002'
     LIMIT 1;

    INSERT INTO class_lists (
        class_id,
        student_id
    ) VALUES (
        v_class_id,
        v_student_id
    )
    ON DUPLICATE KEY UPDATE
        student_id = VALUES(student_id);

    IF NOT EXISTS (SELECT 1 FROM students WHERE student_lrn = '123456789003') THEN
        INSERT INTO addresses (
            country_code,
            region_name,
            province_name,
            city_municipality_name,
            barangay_name,
            address_line,
            postal_code,
            address_source
        ) VALUES (
            'PH',
            'Region VI',
            'Iloilo',
            'Iloilo City',
            'Jaro',
            'Demo learner address 3',
            '5000',
            'sf1_import'
        );
        SET v_address_id = LAST_INSERT_ID();

        INSERT INTO students (
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
        ) VALUES (
            @school_id,
            v_address_id,
            1,
            '123456789003',
            'Carlos',
            'Lopez',
            'Garcia',
            NULL,
            '2012-11-06',
            'active'
        );
    ELSE
        UPDATE students
           SET school_id = @school_id,
               gender_id = 1,
               first_name = 'Carlos',
               middle_name = 'Lopez',
               last_name = 'Garcia',
               suffix = NULL,
               birth_date = '2012-11-06',
               status = 'active'
         WHERE student_lrn = '123456789003';
    END IF;

    SELECT student_id
      INTO v_student_id
      FROM students
     WHERE student_lrn = '123456789003'
     LIMIT 1;

    INSERT INTO class_lists (
        class_id,
        student_id
    ) VALUES (
        v_class_id,
        v_student_id
    )
    ON DUPLICATE KEY UPDATE
        student_id = VALUES(student_id);

    INSERT INTO root_tags (
        curriculum_id,
        root_tag_name,
        description,
        status
    ) VALUES (
        v_curriculum_id,
        'English Language Competencies',
        'Demo Grade 7 English competency group for local V2 assessment testing.',
        'active'
    )
    ON DUPLICATE KEY UPDATE
        description = VALUES(description),
        status = VALUES(status);

    SELECT root_tag_id
      INTO v_root_tag_id
      FROM root_tags
     WHERE curriculum_id = v_curriculum_id
       AND root_tag_name = 'English Language Competencies'
     LIMIT 1;

    INSERT INTO competency_tags (
        root_tag_id,
        competency_name
    ) VALUES
        (v_root_tag_id, 'Use correct subject-verb agreement in sentences.'),
        (v_root_tag_id, 'Identify the main idea and supporting details in a text.'),
        (v_root_tag_id, 'Infer meaning from context clues.')
    ON DUPLICATE KEY UPDATE
        competency_name = VALUES(competency_name);

    SELECT competency_id
      INTO v_competency_grammar
      FROM competency_tags
     WHERE root_tag_id = v_root_tag_id
       AND competency_name = 'Use correct subject-verb agreement in sentences.'
     LIMIT 1;

    SELECT competency_id
      INTO v_competency_main_idea
      FROM competency_tags
     WHERE root_tag_id = v_root_tag_id
       AND competency_name = 'Identify the main idea and supporting details in a text.'
     LIMIT 1;

    SELECT competency_id
      INTO v_competency_context
      FROM competency_tags
     WHERE root_tag_id = v_root_tag_id
       AND competency_name = 'Infer meaning from context clues.'
     LIMIT 1;

    INSERT INTO skills (
        competency_id,
        term_period_id,
        grade_level_id,
        subject_id
    ) VALUES
        (v_competency_grammar, v_first_term_period_id, v_grade_level_id, v_subject_id),
        (v_competency_main_idea, v_first_term_period_id, v_grade_level_id, v_subject_id),
        (v_competency_context, v_first_term_period_id, v_grade_level_id, v_subject_id)
    ON DUPLICATE KEY UPDATE
        subject_id = VALUES(subject_id);

    SELECT skill_id
      INTO v_skill_grammar
      FROM skills
     WHERE competency_id = v_competency_grammar
       AND term_period_id = v_first_term_period_id
       AND grade_level_id = v_grade_level_id
       AND subject_id = v_subject_id
     LIMIT 1;

    SELECT skill_id
      INTO v_skill_main_idea
      FROM skills
     WHERE competency_id = v_competency_main_idea
       AND term_period_id = v_first_term_period_id
       AND grade_level_id = v_grade_level_id
       AND subject_id = v_subject_id
     LIMIT 1;

    SELECT skill_id
      INTO v_skill_context
      FROM skills
     WHERE competency_id = v_competency_context
       AND term_period_id = v_first_term_period_id
       AND grade_level_id = v_grade_level_id
       AND subject_id = v_subject_id
     LIMIT 1;

    INSERT INTO interventions (
        skill_id,
        intervention_type,
        description
    ) VALUES
        (
            v_skill_grammar,
            'review',
            'Review subject-verb agreement with short guided examples before the next assessment.'
        ),
        (
            v_skill_main_idea,
            'practice',
            'Give a short reading passage and ask learners to mark the main idea and two supporting details.'
        ),
        (
            v_skill_context,
            'remediation',
            'Reteach context clues using sentence-level examples and quick oral checking.'
        )
    ON DUPLICATE KEY UPDATE
        description = VALUES(description);

    INSERT INTO audit_logs (
        audit_uuid,
        user_id,
        action,
        entity_type,
        entity_id,
        outcome,
        details,
        created_at
    ) VALUES (
        UUID(),
        v_teacher_user_id,
        'LOCAL_DEMO_SEED_CLASS_COMPETENCIES',
        'class_assignments',
        CAST(v_class_assignment_id AS CHAR),
        'success',
        JSON_OBJECT(
            'schoolId', @school_id,
            'teacherEmail', @teacher_email,
            'academicYear', @academic_year_name,
            'section', @section_name,
            'subjectCode', @subject_code,
            'script', 'docs/demo_seed_v2_teacher_class_competencies.sql'
        ),
        CURRENT_TIMESTAMP
    );

    COMMIT;

    SELECT
        @school_id AS school_id,
        v_teacher_user_id AS teacher_user_id,
        v_academic_year_id AS academic_year_id,
        v_first_term_period_id AS first_term_period_id,
        v_class_id AS class_id,
        v_class_assignment_id AS class_assignment_id,
        v_root_tag_id AS root_tag_id,
        v_skill_grammar AS grammar_skill_id,
        v_skill_main_idea AS main_idea_skill_id,
        v_skill_context AS context_skill_id;
END//

CALL seed_v2_teacher_class_competencies()//

DROP PROCEDURE IF EXISTS seed_v2_teacher_class_competencies//

DELIMITER ;
