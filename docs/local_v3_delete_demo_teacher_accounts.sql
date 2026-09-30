-- LOCAL V3 DEMO CLEANUP ONLY
--
-- Purpose:
--   Permanently remove these teacher accounts from performance_assessment_v3_db
--   so their email addresses and contact numbers can be registered again:
--     910003, 910004, 910015, 910023, 910024, 910025
--
-- Important:
--   * The script preserves shared school, class, student, class-list, curriculum,
--     and reference records.
--   * It removes class assignments and assessments owned by the target teachers,
--     including dependent results, scans, answer evidence, and sync records.
--   * Audit and login-attempt rows are retained; their user_id becomes NULL through
--     the database's ON DELETE SET NULL rule.
--   * Run this only against the local performance_assessment_v3_db.
--
-- Safety:
--   The default mode is a complete dry run followed by ROLLBACK.
--   To permanently apply it, change both values below exactly as documented.

SET @cleanup_apply = 0;
SET @cleanup_confirmation = 'NOT_CONFIRMED';

-- PERMANENT APPLY VALUES:
-- SET @cleanup_apply = 1;
-- SET @cleanup_confirmation = 'DELETE_6_LOCAL_V3_DEMO_USERS';

DELIMITER $$

DROP PROCEDURE IF EXISTS local_v3_delete_demo_teacher_accounts$$

CREATE PROCEDURE local_v3_delete_demo_teacher_accounts()
BEGIN
    DECLARE v_target_count INT DEFAULT 0;
    DECLARE v_invalid_target_count INT DEFAULT 0;
    DECLARE v_cleanup_actor_count INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF DATABASE() <> 'performance_assessment_v3_db' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Safety stop: select performance_assessment_v3_db before running this script.';
    END IF;

    IF COALESCE(@cleanup_apply, 0) NOT IN (0, 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Safety stop: @cleanup_apply must be 0 (dry run) or 1 (permanent apply).';
    END IF;

    IF @cleanup_apply = 1
       AND COALESCE(@cleanup_confirmation, '') <> 'DELETE_6_LOCAL_V3_DEMO_USERS' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Safety stop: permanent cleanup confirmation phrase is missing.';
    END IF;

    DROP TEMPORARY TABLE IF EXISTS cleanup_target_users;
    CREATE TEMPORARY TABLE cleanup_target_users (
        user_id BIGINT UNSIGNED PRIMARY KEY,
        address_id BIGINT UNSIGNED NULL,
        email VARCHAR(150) NOT NULL,
        contact_number VARCHAR(20) NOT NULL
    ) ENGINE=MEMORY;

    INSERT INTO cleanup_target_users (user_id, address_id, email, contact_number)
    SELECT user_id, address_id, email, contact_number
      FROM users
     WHERE user_id IN (910003, 910004, 910015, 910023, 910024, 910025);

    SELECT COUNT(*) INTO v_target_count
      FROM cleanup_target_users;

    IF v_target_count <> 6 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Safety stop: one or more expected target users are missing. No cleanup was applied.';
    END IF;

    SELECT COUNT(*) INTO v_invalid_target_count
      FROM users u
      JOIN roles r ON r.role_id = u.role_id
      JOIN cleanup_target_users target ON target.user_id = u.user_id
     WHERE LOWER(r.role_name) <> 'teacher';

    IF v_invalid_target_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Safety stop: the target list contains a non-teacher account.';
    END IF;

    -- Actor used only when a retained historical/business row has a required
    -- non-null user foreign key. It must be the active local principal.
    SELECT COUNT(*) INTO v_cleanup_actor_count
      FROM users u
      JOIN roles r ON r.role_id = u.role_id
      JOIN statuses s ON s.status_id = u.status_id
     WHERE u.user_id = 900001
       AND LOWER(r.role_name) = 'principal'
       AND LOWER(s.status_name) = 'active';

    IF v_cleanup_actor_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Safety stop: active cleanup principal user 900001 was not found.';
    END IF;

    DROP TEMPORARY TABLE IF EXISTS cleanup_class_assignments;
    CREATE TEMPORARY TABLE cleanup_class_assignments (
        class_assignment_id BIGINT UNSIGNED PRIMARY KEY
    ) ENGINE=MEMORY;

    INSERT INTO cleanup_class_assignments
    SELECT class_assignment_id
      FROM class_assignments
     WHERE user_id IN (SELECT user_id FROM cleanup_target_users);

    DROP TEMPORARY TABLE IF EXISTS cleanup_tests;
    CREATE TEMPORARY TABLE cleanup_tests (
        test_id BIGINT UNSIGNED PRIMARY KEY
    ) ENGINE=MEMORY;

    INSERT INTO cleanup_tests
    SELECT test_id
      FROM tests
     WHERE created_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    DROP TEMPORARY TABLE IF EXISTS cleanup_test_assignments;
    CREATE TEMPORARY TABLE cleanup_test_assignments (
        test_assignment_id BIGINT UNSIGNED PRIMARY KEY
    ) ENGINE=MEMORY;

    INSERT IGNORE INTO cleanup_test_assignments
    SELECT test_assignment_id
      FROM test_assignments
     WHERE class_assignment_id IN (
               SELECT class_assignment_id FROM cleanup_class_assignments
           )
        OR test_id IN (SELECT test_id FROM cleanup_tests);

    DROP TEMPORARY TABLE IF EXISTS cleanup_test_results;
    CREATE TEMPORARY TABLE cleanup_test_results (
        test_result_id BIGINT UNSIGNED PRIMARY KEY
    ) ENGINE=MEMORY;

    INSERT INTO cleanup_test_results
    SELECT test_result_id
      FROM test_results
     WHERE test_assignment_id IN (
               SELECT test_assignment_id FROM cleanup_test_assignments
           );

    DROP TEMPORARY TABLE IF EXISTS cleanup_student_answers;
    CREATE TEMPORARY TABLE cleanup_student_answers (
        student_answer_id BIGINT UNSIGNED PRIMARY KEY
    ) ENGINE=MEMORY;

    INSERT IGNORE INTO cleanup_student_answers
    SELECT answer.student_answer_id
      FROM student_answers answer
      LEFT JOIN questions question ON question.question_id = answer.question_id
      LEFT JOIN test_parts part ON part.test_part_id = question.test_part_id
     WHERE answer.test_result_id IN (
               SELECT test_result_id FROM cleanup_test_results
           )
        OR part.test_id IN (SELECT test_id FROM cleanup_tests);

    DROP TEMPORARY TABLE IF EXISTS cleanup_answer_sheet_versions;
    CREATE TEMPORARY TABLE cleanup_answer_sheet_versions (
        answer_sheet_version_id BIGINT UNSIGNED PRIMARY KEY
    ) ENGINE=MEMORY;

    INSERT INTO cleanup_answer_sheet_versions
    SELECT answer_sheet_version_id
      FROM answer_sheet_versions
     WHERE test_assignment_id IN (
               SELECT test_assignment_id FROM cleanup_test_assignments
           );

    DROP TEMPORARY TABLE IF EXISTS cleanup_answer_sheet_pages;
    CREATE TEMPORARY TABLE cleanup_answer_sheet_pages (
        answer_sheet_page_id BIGINT UNSIGNED PRIMARY KEY
    ) ENGINE=MEMORY;

    INSERT INTO cleanup_answer_sheet_pages
    SELECT answer_sheet_page_id
      FROM answer_sheet_pages
     WHERE answer_sheet_version_id IN (
               SELECT answer_sheet_version_id FROM cleanup_answer_sheet_versions
           );

    DROP TEMPORARY TABLE IF EXISTS cleanup_answer_sheet_regions;
    CREATE TEMPORARY TABLE cleanup_answer_sheet_regions (
        answer_sheet_region_id BIGINT UNSIGNED PRIMARY KEY
    ) ENGINE=MEMORY;

    INSERT IGNORE INTO cleanup_answer_sheet_regions
    SELECT region.answer_sheet_region_id
      FROM answer_sheet_regions region
      LEFT JOIN questions question ON question.question_id = region.question_id
      LEFT JOIN test_parts part_from_question
             ON part_from_question.test_part_id = question.test_part_id
      LEFT JOIN test_parts direct_part
             ON direct_part.test_part_id = region.test_part_id
     WHERE region.answer_sheet_version_id IN (
               SELECT answer_sheet_version_id FROM cleanup_answer_sheet_versions
           )
        OR region.answer_sheet_page_id IN (
               SELECT answer_sheet_page_id FROM cleanup_answer_sheet_pages
           )
        OR part_from_question.test_id IN (SELECT test_id FROM cleanup_tests)
        OR direct_part.test_id IN (SELECT test_id FROM cleanup_tests);

    DROP TEMPORARY TABLE IF EXISTS cleanup_scan_sessions;
    CREATE TEMPORARY TABLE cleanup_scan_sessions (
        scan_session_id BIGINT UNSIGNED PRIMARY KEY
    ) ENGINE=MEMORY;

    INSERT IGNORE INTO cleanup_scan_sessions
    SELECT scan_session_id
      FROM scan_sessions
     WHERE test_assignment_id IN (
               SELECT test_assignment_id FROM cleanup_test_assignments
           )
        OR answer_sheet_version_id IN (
               SELECT answer_sheet_version_id FROM cleanup_answer_sheet_versions
           );

    DROP TEMPORARY TABLE IF EXISTS cleanup_scan_pages;
    CREATE TEMPORARY TABLE cleanup_scan_pages (
        scan_page_id BIGINT UNSIGNED PRIMARY KEY
    ) ENGINE=MEMORY;

    INSERT IGNORE INTO cleanup_scan_pages
    SELECT scan_page_id
      FROM scan_pages
     WHERE scan_session_id IN (
               SELECT scan_session_id FROM cleanup_scan_sessions
           )
        OR answer_sheet_page_id IN (
               SELECT answer_sheet_page_id FROM cleanup_answer_sheet_pages
           );

    DROP TEMPORARY TABLE IF EXISTS cleanup_answer_attachments;
    CREATE TEMPORARY TABLE cleanup_answer_attachments (
        answer_attachment_id BIGINT UNSIGNED PRIMARY KEY
    ) ENGINE=MEMORY;

    INSERT IGNORE INTO cleanup_answer_attachments
    SELECT answer_attachment_id
      FROM answer_attachments
     WHERE scan_session_id IN (SELECT scan_session_id FROM cleanup_scan_sessions)
        OR scan_page_id IN (SELECT scan_page_id FROM cleanup_scan_pages)
        OR student_answer_id IN (SELECT student_answer_id FROM cleanup_student_answers)
        OR answer_sheet_region_id IN (
               SELECT answer_sheet_region_id FROM cleanup_answer_sheet_regions
           );

    DROP TEMPORARY TABLE IF EXISTS cleanup_answer_verifications;
    CREATE TEMPORARY TABLE cleanup_answer_verifications (
        answer_verification_id BIGINT UNSIGNED PRIMARY KEY
    ) ENGINE=MEMORY;

    INSERT IGNORE INTO cleanup_answer_verifications
    SELECT answer_verification_id
      FROM answer_verifications
     WHERE student_answer_id IN (SELECT student_answer_id FROM cleanup_student_answers)
        OR evidence_attachment_id IN (
               SELECT answer_attachment_id FROM cleanup_answer_attachments
           );

    DROP TEMPORARY TABLE IF EXISTS cleanup_intervention_cases;
    CREATE TEMPORARY TABLE cleanup_intervention_cases (
        student_intervention_case_id BIGINT UNSIGNED PRIMARY KEY
    ) ENGINE=MEMORY;

    INSERT INTO cleanup_intervention_cases
    SELECT student_intervention_case_id
      FROM student_intervention_cases
     WHERE source_test_result_id IN (
               SELECT test_result_id FROM cleanup_test_results
           );

    DROP TEMPORARY TABLE IF EXISTS cleanup_syncs;
    CREATE TEMPORARY TABLE cleanup_syncs (
        sync_id BIGINT UNSIGNED PRIMARY KEY
    ) ENGINE=MEMORY;

    INSERT INTO cleanup_syncs
    SELECT sync_id
      FROM syncs
     WHERE test_assignment_id IN (
               SELECT test_assignment_id FROM cleanup_test_assignments
           );

    SELECT 'TARGET_ACCOUNT' AS record_type,
           u.user_id AS record_id,
           u.email AS detail,
           status.status_name AS status
      FROM users u
      JOIN statuses status ON status.status_id = u.status_id
     WHERE u.user_id IN (SELECT user_id FROM cleanup_target_users)
     ORDER BY u.user_id;

    SELECT 'class_assignments' AS entity, COUNT(*) AS rows_to_delete
      FROM cleanup_class_assignments
    UNION ALL
    SELECT 'tests', COUNT(*) FROM cleanup_tests
    UNION ALL
    SELECT 'test_assignments', COUNT(*) FROM cleanup_test_assignments
    UNION ALL
    SELECT 'test_results', COUNT(*) FROM cleanup_test_results
    UNION ALL
    SELECT 'student_answers', COUNT(*) FROM cleanup_student_answers
    UNION ALL
    SELECT 'scan_sessions', COUNT(*) FROM cleanup_scan_sessions
    UNION ALL
    SELECT 'answer_attachments', COUNT(*) FROM cleanup_answer_attachments
    UNION ALL
    SELECT 'syncs', COUNT(*) FROM cleanup_syncs;

    START TRANSACTION;

    -- Break nullable self-references before deleting their source rows.
    UPDATE answer_attachments
       SET source_answer_attachment_id = NULL
     WHERE source_answer_attachment_id IN (
               SELECT answer_attachment_id FROM cleanup_answer_attachments
           );

    UPDATE scan_pages
       SET supersedes_scan_page_id = NULL
     WHERE supersedes_scan_page_id IN (
               SELECT scan_page_id FROM cleanup_scan_pages
           );

    UPDATE scan_sessions
       SET supersedes_scan_session_id = NULL
     WHERE supersedes_scan_session_id IN (
               SELECT scan_session_id FROM cleanup_scan_sessions
           );

    UPDATE answer_sheet_versions
       SET source_answer_sheet_version_id = NULL
     WHERE source_answer_sheet_version_id IN (
               SELECT answer_sheet_version_id FROM cleanup_answer_sheet_versions
           );

    UPDATE tests
       SET source_test_id = NULL
     WHERE source_test_id IN (SELECT test_id FROM cleanup_tests);

    -- Delete dependent assessment, scan, result, and synchronization records.
    DELETE FROM student_intervention_updates
     WHERE student_intervention_case_id IN (
               SELECT student_intervention_case_id FROM cleanup_intervention_cases
           );

    DELETE FROM student_intervention_cases
     WHERE student_intervention_case_id IN (
               SELECT student_intervention_case_id FROM cleanup_intervention_cases
           );

    DELETE FROM intervention_results
     WHERE test_result_id IN (SELECT test_result_id FROM cleanup_test_results);

    DELETE FROM answer_rubric_scores
     WHERE student_answer_id IN (SELECT student_answer_id FROM cleanup_student_answers)
        OR answer_verification_id IN (
               SELECT answer_verification_id FROM cleanup_answer_verifications
           );

    DELETE FROM answer_verifications
     WHERE answer_verification_id IN (
               SELECT answer_verification_id FROM cleanup_answer_verifications
           );

    DELETE FROM scan_verifications
     WHERE scan_session_id IN (SELECT scan_session_id FROM cleanup_scan_sessions)
        OR scan_page_id IN (SELECT scan_page_id FROM cleanup_scan_pages);

    DELETE FROM test_result_scans
     WHERE test_result_id IN (SELECT test_result_id FROM cleanup_test_results)
        OR scan_session_id IN (SELECT scan_session_id FROM cleanup_scan_sessions);

    DELETE detection
      FROM omr_detections detection
      LEFT JOIN questions question ON question.question_id = detection.question_id
      LEFT JOIN test_parts part ON part.test_part_id = question.test_part_id
     WHERE detection.scan_session_id IN (
               SELECT scan_session_id FROM cleanup_scan_sessions
           )
        OR detection.scan_page_id IN (SELECT scan_page_id FROM cleanup_scan_pages)
        OR detection.answer_sheet_region_id IN (
               SELECT answer_sheet_region_id FROM cleanup_answer_sheet_regions
           )
        OR part.test_id IN (SELECT test_id FROM cleanup_tests);

    DELETE FROM answer_attachments
     WHERE answer_attachment_id IN (
               SELECT answer_attachment_id FROM cleanup_answer_attachments
           );

    DELETE FROM student_answers
     WHERE student_answer_id IN (SELECT student_answer_id FROM cleanup_student_answers);

    DELETE FROM sync_items
     WHERE sync_id IN (SELECT sync_id FROM cleanup_syncs)
        OR test_result_id IN (SELECT test_result_id FROM cleanup_test_results);

    DELETE FROM syncs
     WHERE sync_id IN (SELECT sync_id FROM cleanup_syncs);

    DELETE FROM test_results
     WHERE test_result_id IN (SELECT test_result_id FROM cleanup_test_results);

    DELETE FROM scan_pages
     WHERE scan_page_id IN (SELECT scan_page_id FROM cleanup_scan_pages);

    DELETE FROM scan_sessions
     WHERE scan_session_id IN (SELECT scan_session_id FROM cleanup_scan_sessions);

    DELETE FROM answer_sheet_regions
     WHERE answer_sheet_region_id IN (
               SELECT answer_sheet_region_id FROM cleanup_answer_sheet_regions
           );

    DELETE FROM answer_sheet_pages
     WHERE answer_sheet_page_id IN (
               SELECT answer_sheet_page_id FROM cleanup_answer_sheet_pages
           );

    DELETE FROM answer_sheet_versions
     WHERE answer_sheet_version_id IN (
               SELECT answer_sheet_version_id FROM cleanup_answer_sheet_versions
           );

    DELETE FROM test_assignments
     WHERE test_assignment_id IN (
               SELECT test_assignment_id FROM cleanup_test_assignments
           );

    DELETE FROM tests
     WHERE test_id IN (SELECT test_id FROM cleanup_tests);

    DELETE FROM class_assignment_schedules
     WHERE class_assignment_id IN (
               SELECT class_assignment_id FROM cleanup_class_assignments
           );

    DELETE FROM class_assignments
     WHERE class_assignment_id IN (
               SELECT class_assignment_id FROM cleanup_class_assignments
           );

    -- Preserve unrelated rows that only name a target teacher as an actor.
    UPDATE answer_attachments
       SET purged_by_user_id = NULL
     WHERE purged_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE answer_attachments
       SET retention_hold_set_by_user_id = NULL
     WHERE retention_hold_set_by_user_id IN (
               SELECT user_id FROM cleanup_target_users
           );

    UPDATE answer_rubric_scores
       SET scored_by_user_id = 900001
     WHERE scored_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE answer_sheet_versions
       SET generated_by_user_id = 900001
     WHERE generated_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE answer_verifications
       SET verified_by_user_id = 900001
     WHERE verified_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE class_assignments
       SET status_changed_by_user_id = NULL
     WHERE status_changed_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE class_assignment_schedules
       SET created_by_user_id = 900001
     WHERE created_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE class_assignment_schedules
       SET status_changed_by_user_id = NULL
     WHERE status_changed_by_user_id IN (
               SELECT user_id FROM cleanup_target_users
           );

    UPDATE class_lists
       SET status_changed_by_user_id = NULL
     WHERE status_changed_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE intervention_results
       SET acknowledged_by_user_id = NULL
     WHERE acknowledged_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE omr_templates
       SET created_by_user_id = NULL
     WHERE created_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE performance_rule_sets
       SET approved_by_user_id = NULL
     WHERE approved_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE rubrics
       SET created_by_user_id = 900001
     WHERE created_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE scan_sessions
       SET scanned_by_user_id = 900001
     WHERE scanned_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE scan_sessions
       SET verified_by_user_id = NULL
     WHERE verified_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE scan_verifications
       SET verified_by_user_id = 900001
     WHERE verified_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE sf1_imports
       SET uploaded_by_user_id = 900001
     WHERE uploaded_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE students
       SET status_changed_by_user_id = NULL
     WHERE status_changed_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE student_answers
       SET verified_by_user_id = NULL
     WHERE verified_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE student_intervention_cases
       SET assigned_to_user_id = NULL
     WHERE assigned_to_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE student_intervention_cases
       SET opened_by_user_id = 900001
     WHERE opened_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE student_intervention_updates
       SET updated_by_user_id = 900001
     WHERE updated_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE syncs
       SET user_id = 900001
     WHERE user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE term_periods
       SET overridden_by_user_id = NULL
     WHERE overridden_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE test_assignments
       SET assigned_by_user_id = 900001
     WHERE assigned_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    UPDATE test_assignments
       SET outside_schedule_confirmed_by_user_id = NULL
     WHERE outside_schedule_confirmed_by_user_id IN (
               SELECT user_id FROM cleanup_target_users
           );

    UPDATE test_result_scans
       SET decided_by_user_id = 900001
     WHERE decided_by_user_id IN (SELECT user_id FROM cleanup_target_users);

    DELETE FROM auth_sessions
     WHERE user_id IN (SELECT user_id FROM cleanup_target_users);

    -- Cascades remove notifications, MFA rows, and verification challenges.
    -- Audit logs and login attempts retain history with a NULL user_id.
    DELETE FROM users
     WHERE user_id IN (SELECT user_id FROM cleanup_target_users);

    -- Remove only account addresses that are no longer referenced anywhere.
    DELETE address
      FROM addresses address
      JOIN (
            SELECT DISTINCT address_id
              FROM cleanup_target_users
             WHERE address_id IS NOT NULL
      ) target_address ON target_address.address_id = address.address_id
     WHERE NOT EXISTS (
               SELECT 1 FROM users u WHERE u.address_id = address.address_id
           )
       AND NOT EXISTS (
               SELECT 1 FROM students student WHERE student.address_id = address.address_id
           )
       AND NOT EXISTS (
               SELECT 1 FROM school_profiles school WHERE school.address_id = address.address_id
           );

    IF @cleanup_apply = 1 THEN
        COMMIT;
        SELECT 'COMMITTED' AS cleanup_mode,
               'The six local V3 demo teacher accounts were permanently deleted.' AS result;
    ELSE
        ROLLBACK;
        SELECT 'DRY_RUN_ROLLED_BACK' AS cleanup_mode,
               'No database changes were committed. Set the documented apply values to delete permanently.' AS result;
    END IF;

    SELECT COUNT(*) AS remaining_target_users
      FROM users
     WHERE user_id IN (910003, 910004, 910015, 910023, 910024, 910025);

    DROP TEMPORARY TABLE IF EXISTS cleanup_syncs;
    DROP TEMPORARY TABLE IF EXISTS cleanup_intervention_cases;
    DROP TEMPORARY TABLE IF EXISTS cleanup_answer_verifications;
    DROP TEMPORARY TABLE IF EXISTS cleanup_answer_attachments;
    DROP TEMPORARY TABLE IF EXISTS cleanup_scan_pages;
    DROP TEMPORARY TABLE IF EXISTS cleanup_scan_sessions;
    DROP TEMPORARY TABLE IF EXISTS cleanup_answer_sheet_regions;
    DROP TEMPORARY TABLE IF EXISTS cleanup_answer_sheet_pages;
    DROP TEMPORARY TABLE IF EXISTS cleanup_answer_sheet_versions;
    DROP TEMPORARY TABLE IF EXISTS cleanup_student_answers;
    DROP TEMPORARY TABLE IF EXISTS cleanup_test_results;
    DROP TEMPORARY TABLE IF EXISTS cleanup_test_assignments;
    DROP TEMPORARY TABLE IF EXISTS cleanup_tests;
    DROP TEMPORARY TABLE IF EXISTS cleanup_class_assignments;
    DROP TEMPORARY TABLE IF EXISTS cleanup_target_users;
END$$

CALL local_v3_delete_demo_teacher_accounts()$$
DROP PROCEDURE IF EXISTS local_v3_delete_demo_teacher_accounts$$

DELIMITER ;

