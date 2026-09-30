-- V3 school calendar and teacher timetable hardening.
-- Scope: local performance_assessment_v3_db only.
-- Apply once after a verified backup. The preflight stops before ALTER TABLE
-- when academic-year ownership is ambiguous or this migration was applied.

USE `performance_assessment_v3_db`;

DELIMITER $$

DROP PROCEDURE IF EXISTS `v3_014_preflight`$$
CREATE PROCEDURE `v3_014_preflight`()
BEGIN
    DECLARE school_column_count INT DEFAULT 0;
    DECLARE schedule_table_count INT DEFAULT 0;
    DECLARE conflicting_year_count INT DEFAULT 0;
    DECLARE unused_year_count INT DEFAULT 0;
    DECLARE school_count INT DEFAULT 0;

    SELECT COUNT(*)
      INTO school_column_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'academic_years'
       AND column_name = 'school_id';

    SELECT COUNT(*)
      INTO schedule_table_count
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = 'class_assignment_schedules'
       AND table_type = 'BASE TABLE';

    IF school_column_count > 0 OR schedule_table_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V3_014 already appears to be applied.';
    END IF;

    SELECT COUNT(*)
      INTO conflicting_year_count
      FROM (
            SELECT ownership.academic_year_id
              FROM (
                    SELECT class_row.academic_year_id, section_row.school_id
                      FROM classes class_row
                      JOIN sections section_row
                        ON section_row.section_id = class_row.section_id
                    UNION ALL
                    SELECT sf1.academic_year_id, sf1.school_id
                      FROM sf1_imports sf1
                    UNION ALL
                    SELECT term.academic_year_id, test.school_id
                      FROM tests test
                      JOIN term_periods term
                        ON term.term_period_id = test.term_period_id
                   ) ownership
             WHERE ownership.school_id IS NOT NULL
             GROUP BY ownership.academic_year_id
            HAVING COUNT(DISTINCT ownership.school_id) > 1
           ) conflicts;

    IF conflicting_year_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V3_014 blocked: an academic year is referenced by more than one school.';
    END IF;

    SELECT COUNT(*) INTO school_count FROM school_profiles;

    SELECT COUNT(*)
      INTO unused_year_count
      FROM academic_years academic_year
     WHERE NOT EXISTS (
               SELECT 1
                 FROM classes class_row
                 JOIN sections section_row
                   ON section_row.section_id = class_row.section_id
                WHERE class_row.academic_year_id = academic_year.academic_year_id
                  AND section_row.school_id IS NOT NULL
           )
       AND NOT EXISTS (
               SELECT 1
                 FROM sf1_imports sf1
                WHERE sf1.academic_year_id = academic_year.academic_year_id
                  AND sf1.school_id IS NOT NULL
           )
       AND NOT EXISTS (
               SELECT 1
                 FROM tests test
                 JOIN term_periods term
                   ON term.term_period_id = test.term_period_id
                WHERE term.academic_year_id = academic_year.academic_year_id
                  AND test.school_id IS NOT NULL
           );

    IF unused_year_count > 0 AND school_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V3_014 blocked: unused academic years cannot be assigned safely when the database contains zero or multiple schools.';
    END IF;
END$$

CALL `v3_014_preflight`()$$
DROP PROCEDURE `v3_014_preflight`$$

DELIMITER ;

ALTER TABLE `academic_years`
    ADD COLUMN `school_id` varchar(20) NULL
        COMMENT 'School that owns this academic calendar.'
        AFTER `academic_year_id`;

UPDATE academic_years academic_year
JOIN (
      SELECT ownership.academic_year_id, MIN(ownership.school_id) AS school_id
        FROM (
              SELECT class_row.academic_year_id, section_row.school_id
                FROM classes class_row
                JOIN sections section_row
                  ON section_row.section_id = class_row.section_id
              UNION ALL
              SELECT sf1.academic_year_id, sf1.school_id
                FROM sf1_imports sf1
              UNION ALL
              SELECT term.academic_year_id, test.school_id
                FROM tests test
                JOIN term_periods term
                  ON term.term_period_id = test.term_period_id
             ) ownership
       WHERE ownership.school_id IS NOT NULL
       GROUP BY ownership.academic_year_id
     ) resolved
  ON resolved.academic_year_id = academic_year.academic_year_id
 SET academic_year.school_id = resolved.school_id;

UPDATE `academic_years`
   SET `school_id` = (SELECT MIN(`school_id`) FROM `school_profiles`)
 WHERE `school_id` IS NULL;

ALTER TABLE `academic_years`
    DROP INDEX `uk_academic_years_name`,
    MODIFY COLUMN `school_id` varchar(20) NOT NULL
        COMMENT 'School that owns this academic calendar.',
    ADD COLUMN `active_school_id` varchar(20)
        GENERATED ALWAYS AS (
            CASE WHEN `status` = 'active' THEN `school_id` ELSE NULL END
        ) VIRTUAL,
    ADD UNIQUE KEY `uk_academic_years_school_name` (`school_id`, `year_name`),
    ADD UNIQUE KEY `uk_academic_years_one_active_school` (`active_school_id`),
    ADD CONSTRAINT `fk_academic_years_school`
        FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`);

ALTER TABLE `term_periods`
    ADD COLUMN `active_academic_year_id` int(10) unsigned
        GENERATED ALWAYS AS (
            CASE WHEN `status` = 'active' THEN `academic_year_id` ELSE NULL END
        ) VIRTUAL,
    ADD UNIQUE KEY `uk_term_periods_one_active_year` (`active_academic_year_id`),
    ADD CONSTRAINT `chk_term_periods_order`
        CHECK (`term_order` BETWEEN 1 AND 4);

CREATE TABLE `class_assignment_schedules` (
  `class_assignment_schedule_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `schedule_uuid` char(36) NOT NULL,
  `class_assignment_id` bigint(20) unsigned NOT NULL,
  `day_of_week` tinyint(3) unsigned NOT NULL
      COMMENT 'ISO weekday: 1 Monday through 7 Sunday.',
  `start_time` time NOT NULL,
  `end_time` time NOT NULL,
  `timezone_name` varchar(64) NOT NULL DEFAULT 'Asia/Manila',
  `effective_from` date NOT NULL,
  `effective_to` date DEFAULT NULL,
  `schedule_status` enum('active','archived') NOT NULL DEFAULT 'active',
  `created_by_user_id` bigint(20) unsigned NOT NULL,
  `status_changed_by_user_id` bigint(20) unsigned DEFAULT NULL,
  `status_reason` varchar(255) DEFAULT NULL,
  `archived_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`class_assignment_schedule_id`),
  UNIQUE KEY `uk_class_assignment_schedules_uuid` (`schedule_uuid`),
  KEY `idx_class_assignment_schedules_lookup`
      (`class_assignment_id`, `schedule_status`, `day_of_week`, `effective_from`, `effective_to`),
  KEY `fk_class_assignment_schedules_created_by_user` (`created_by_user_id`),
  KEY `fk_class_assignment_schedules_status_user` (`status_changed_by_user_id`),
  CONSTRAINT `fk_class_assignment_schedules_assignment`
      FOREIGN KEY (`class_assignment_id`) REFERENCES `class_assignments` (`class_assignment_id`),
  CONSTRAINT `fk_class_assignment_schedules_created_by_user`
      FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_class_assignment_schedules_status_user`
      FOREIGN KEY (`status_changed_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_class_assignment_schedules_day`
      CHECK (`day_of_week` BETWEEN 1 AND 7),
  CONSTRAINT `chk_class_assignment_schedules_time`
      CHECK (`end_time` > `start_time`),
  CONSTRAINT `chk_class_assignment_schedules_dates`
      CHECK (`effective_to` IS NULL OR `effective_to` >= `effective_from`),
  CONSTRAINT `chk_class_assignment_schedules_timezone`
      CHECK (`timezone_name` = 'Asia/Manila'),
  CONSTRAINT `chk_class_assignment_schedules_archive`
      CHECK (
          (`schedule_status` = 'active'
           AND `status_changed_by_user_id` IS NULL
           AND `status_reason` IS NULL
           AND `archived_at` IS NULL)
          OR
          (`schedule_status` = 'archived'
           AND `status_changed_by_user_id` IS NOT NULL
           AND CHAR_LENGTH(TRIM(`status_reason`)) >= 5
           AND `archived_at` IS NOT NULL)
      )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Teacher-maintained meeting times for an assigned class and subject.';

ALTER TABLE `test_assignments`
    ADD COLUMN `outside_schedule_confirmed` tinyint(1) NOT NULL DEFAULT 0
        AFTER `allow_late_capture`,
    ADD COLUMN `outside_schedule_reason` varchar(255) DEFAULT NULL
        AFTER `outside_schedule_confirmed`,
    ADD COLUMN `outside_schedule_confirmed_by_user_id` bigint(20) unsigned DEFAULT NULL
        AFTER `outside_schedule_reason`,
    ADD COLUMN `outside_schedule_confirmed_at` timestamp NULL DEFAULT NULL
        AFTER `outside_schedule_confirmed_by_user_id`,
    ADD KEY `fk_test_assignments_outside_schedule_user`
        (`outside_schedule_confirmed_by_user_id`),
    ADD CONSTRAINT `fk_test_assignments_outside_schedule_user`
        FOREIGN KEY (`outside_schedule_confirmed_by_user_id`) REFERENCES `users` (`user_id`),
    ADD CONSTRAINT `chk_test_assignments_outside_schedule_confirmation`
        CHECK (
            (`outside_schedule_confirmed` = 0
             AND `outside_schedule_reason` IS NULL
             AND `outside_schedule_confirmed_by_user_id` IS NULL
             AND `outside_schedule_confirmed_at` IS NULL)
            OR
            (`outside_schedule_confirmed` = 1
             AND CHAR_LENGTH(TRIM(`outside_schedule_reason`)) >= 5
             AND `outside_schedule_confirmed_by_user_id` IS NOT NULL
             AND `outside_schedule_confirmed_at` IS NOT NULL)
        );
