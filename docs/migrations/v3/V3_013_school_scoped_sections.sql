-- V3 school-ownership hardening for sections and their class cohorts.
-- Scope: local performance_assessment_v3_db only.
-- Safety: preflight stops before ALTER TABLE when existing ownership is ambiguous.

USE `performance_assessment_v3_db`;

DELIMITER $$

DROP PROCEDURE IF EXISTS `v3_013_preflight`$$
CREATE PROCEDURE `v3_013_preflight`()
BEGIN
    DECLARE conflicting_section_count INT DEFAULT 0;
    DECLARE unused_section_count INT DEFAULT 0;
    DECLARE school_count INT DEFAULT 0;
    DECLARE school_column_count INT DEFAULT 0;

    SELECT COUNT(*)
      INTO school_column_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'sections'
       AND column_name = 'school_id';

    IF school_column_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V3_013 already appears to be applied: sections.school_id exists.';
    END IF;

    SELECT COUNT(*)
      INTO conflicting_section_count
      FROM (
            SELECT ownership.section_id
              FROM (
                    SELECT class_row.section_id, teacher.school_id
                      FROM classes class_row
                      JOIN class_assignments assignment
                        ON assignment.class_id = class_row.class_id
                      JOIN users teacher
                        ON teacher.user_id = assignment.user_id
                    UNION ALL
                    SELECT class_row.section_id, student.school_id
                      FROM classes class_row
                      JOIN class_lists membership
                        ON membership.class_id = class_row.class_id
                      JOIN students student
                        ON student.student_id = membership.student_id
                   ) ownership
             GROUP BY ownership.section_id
            HAVING COUNT(DISTINCT ownership.school_id) > 1
           ) conflicts;

    IF conflicting_section_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V3_013 blocked: at least one section is referenced by more than one school.';
    END IF;

    SELECT COUNT(*) INTO school_count FROM school_profiles;

    SELECT COUNT(*)
      INTO unused_section_count
      FROM sections section_row
     WHERE NOT EXISTS (
               SELECT 1
                 FROM classes class_row
                 JOIN class_assignments assignment
                   ON assignment.class_id = class_row.class_id
                 JOIN users teacher
                   ON teacher.user_id = assignment.user_id
                WHERE class_row.section_id = section_row.section_id
                  AND teacher.school_id IS NOT NULL
           )
       AND NOT EXISTS (
               SELECT 1
                 FROM classes class_row
                 JOIN class_lists membership
                   ON membership.class_id = class_row.class_id
                 JOIN students student
                   ON student.student_id = membership.student_id
                WHERE class_row.section_id = section_row.section_id
                  AND student.school_id IS NOT NULL
           );

    IF unused_section_count > 0 AND school_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V3_013 blocked: unused sections cannot be assigned safely when the database contains zero or multiple schools.';
    END IF;
END$$

CALL `v3_013_preflight`()$$
DROP PROCEDURE `v3_013_preflight`$$

DELIMITER ;

ALTER TABLE `sections`
    ADD COLUMN `school_id` varchar(20) NULL
        COMMENT 'School that owns this reusable grade-level section.'
        AFTER `section_id`;

UPDATE `sections` section_row
JOIN (
      SELECT ownership.section_id, MIN(ownership.school_id) AS school_id
        FROM (
              SELECT class_row.section_id, teacher.school_id
                FROM classes class_row
                JOIN class_assignments assignment
                  ON assignment.class_id = class_row.class_id
                JOIN users teacher
                  ON teacher.user_id = assignment.user_id
              UNION ALL
              SELECT class_row.section_id, student.school_id
                FROM classes class_row
                JOIN class_lists membership
                  ON membership.class_id = class_row.class_id
                JOIN students student
                  ON student.student_id = membership.student_id
             ) ownership
       WHERE ownership.school_id IS NOT NULL
       GROUP BY ownership.section_id
     ) resolved
  ON resolved.section_id = section_row.section_id
 SET section_row.school_id = resolved.school_id;

UPDATE `sections`
   SET `school_id` = (SELECT MIN(`school_id`) FROM `school_profiles`)
 WHERE `school_id` IS NULL;

ALTER TABLE `sections`
    DROP INDEX `uk_sections_grade_name`,
    MODIFY COLUMN `school_id` varchar(20) NOT NULL
        COMMENT 'School that owns this reusable grade-level section.',
    ADD KEY `idx_sections_grade_level` (`grade_level_id`),
    ADD UNIQUE KEY `uk_sections_school_grade_name`
        (`school_id`, `grade_level_id`, `section_name`),
    ADD CONSTRAINT `fk_sections_school`
        FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`);
