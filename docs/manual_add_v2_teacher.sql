-- Manual V2 teacher-account insert helper.
-- Purpose: local/demo fallback when the public teacher registration UI is not ready.
-- Scope: performance_assessment_v2_db only. This is not a schema migration.
--
-- Default behavior:
--   Creates a teacher account with status = pending.
--   The principal can then see it in the teacher list and approve/reject it.
--
-- Demo password for the inserted account:
--   TeacherTest@2026
--
-- If you need the teacher to log in immediately for a demo, change:
--   SET @teacher_status = 'active';
--   SET @verify_email = TRUE;
--   SET @verify_contact = TRUE;

USE performance_assessment_v2_db;

SET @school_id = 'V2-LOCAL-TEST';
SET @teacher_status = 'pending';
SET @verify_email = FALSE;
SET @verify_contact = FALSE;

SET @first_name = 'Manual';
SET @middle_name = NULL;
SET @last_name = 'Teacher';
SET @suffix = NULL;
SET @birth_date = '1990-01-01';
SET @teaching_start_date = '2015-06-01';
SET @email = 'teacher.manual.local@example.com';
SET @contact_number = '+639171112222';
SET @password_hash = '$2y$10$QgZVC7BATuNQIBjBK/huv.JUgfaCQ9Ul0gcPgm1YF/.KX0Sa0hGHW';

SET @gender_id = 2;
SET @major_id = 1;
SET @educational_attainment_id = 1;

SET @country_code = 'PH';
SET @region_code = '06';
SET @region_name = 'Western Visayas';
SET @province_code = '0604';
SET @province_name = 'Iloilo';
SET @city_municipality_code = '063022';
SET @city_municipality_name = 'Iloilo City';
SET @barangay_code = '063022001';
SET @barangay_name = 'City Proper';
SET @address_line = 'Manual demo address';
SET @postal_code = '5000';

DELIMITER //

DROP PROCEDURE IF EXISTS add_v2_manual_teacher//

CREATE PROCEDURE add_v2_manual_teacher()
BEGIN
    DECLARE v_teacher_role_id INT DEFAULT 0;
    DECLARE v_status_id INT DEFAULT 0;
    DECLARE v_address_id BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_user_id BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_reference_count INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    SELECT COUNT(*)
      INTO v_reference_count
      FROM school_profiles
     WHERE school_id = @school_id;
    IF v_reference_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invalid @school_id. Check school_profiles.school_id.';
    END IF;

    SELECT COUNT(*)
      INTO v_reference_count
      FROM users
     WHERE LOWER(email) = LOWER(@email);
    IF v_reference_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Duplicate teacher email. Change @email before rerunning.';
    END IF;

    SELECT COUNT(*)
      INTO v_reference_count
      FROM users
     WHERE contact_number = @contact_number;
    IF v_reference_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Duplicate teacher contact number. Change @contact_number before rerunning.';
    END IF;

    SELECT COALESCE((
        SELECT role_id
          FROM roles
         WHERE role_name = 'teacher'
         LIMIT 1
    ), 0)
      INTO v_teacher_role_id;
    IF v_teacher_role_id = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Missing teacher role reference.';
    END IF;

    SELECT COALESCE((
        SELECT status_id
          FROM statuses
         WHERE status_name = @teacher_status
           AND is_active = TRUE
         LIMIT 1
    ), 0)
      INTO v_status_id;
    IF v_status_id = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invalid @teacher_status. Use pending, active, rejected, inactive, or locked.';
    END IF;

    SELECT COUNT(*)
      INTO v_reference_count
      FROM genders
     WHERE gender_id = @gender_id;
    IF v_reference_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invalid @gender_id.';
    END IF;

    SELECT COUNT(*)
      INTO v_reference_count
      FROM majors
     WHERE major_id = @major_id;
    IF v_reference_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invalid @major_id.';
    END IF;

    SELECT COUNT(*)
      INTO v_reference_count
      FROM educational_attainments
     WHERE educational_attainment_id = @educational_attainment_id
       AND is_active = TRUE;
    IF v_reference_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invalid or inactive @educational_attainment_id.';
    END IF;

    INSERT INTO addresses (
        country_code,
        region_code,
        region_name,
        province_code,
        province_name,
        city_municipality_code,
        city_municipality_name,
        barangay_code,
        barangay_name,
        address_line,
        postal_code,
        address_source
    ) VALUES (
        @country_code,
        @region_code,
        @region_name,
        @province_code,
        @province_name,
        @city_municipality_code,
        @city_municipality_name,
        @barangay_code,
        @barangay_name,
        @address_line,
        @postal_code,
        'manual'
    );

    SET v_address_id = LAST_INSERT_ID();

    INSERT INTO users (
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
        password_changed_at,
        email_verified_at,
        contact_verified_at,
        created_at,
        updated_at
    ) VALUES (
        @school_id,
        v_address_id,
        @gender_id,
        @major_id,
        @educational_attainment_id,
        v_teacher_role_id,
        v_status_id,
        @first_name,
        @middle_name,
        @last_name,
        @suffix,
        @birth_date,
        @teaching_start_date,
        LOWER(@email),
        @contact_number,
        @password_hash,
        CURRENT_TIMESTAMP,
        CASE WHEN @verify_email THEN CURRENT_TIMESTAMP ELSE NULL END,
        CASE WHEN @verify_contact THEN CURRENT_TIMESTAMP ELSE NULL END,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );

    SET v_user_id = LAST_INSERT_ID();

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
        v_user_id,
        'MANUAL_ADD_TEACHER_ACCOUNT',
        'users',
        CAST(v_user_id AS CHAR),
        'success',
        JSON_OBJECT(
            'schoolId', @school_id,
            'status', @teacher_status,
            'script', 'docs/manual_add_v2_teacher.sql'
        ),
        CURRENT_TIMESTAMP
    );

    COMMIT;

    SELECT
        'created' AS result,
        v_user_id AS user_id,
        @school_id AS school_id,
        @email AS email,
        @teacher_status AS status,
        'TeacherTest@2026' AS demo_password;
END//

CALL add_v2_manual_teacher()//

DROP PROCEDURE IF EXISTS add_v2_manual_teacher//

DELIMITER ;
