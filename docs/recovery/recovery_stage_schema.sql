-- STAGING schema for recovery_stage database.
-- Purpose: cfg-less IMPORT TABLESPACE requires the destination table to have
-- NO secondary indexes (confirmed empirically via the `roles` test: error 1815
-- "Drop all secondary indexes before importing table ... when .cfg file is
-- missing"). Rather than dismantling the final database's real tables, every
-- table here is a throwaway PK-only shell in a SEPARATE database used only to
-- receive the raw .ibd data, which is then logically transferred (INSERT ...
-- SELECT) into the real, fully-structured table in performance_assessment_v3_db.
--
-- Every column, column order, type, length, unsigned/nullability, default,
-- and generated-column expression below is copied verbatim from
-- docs/recovery/performance_assessment_v3_schema_recovery.sql. Only these are
-- removed, per instruction, since none of them are required for tablespace
-- import: secondary UNIQUE/KEY indexes (including ones on generated/virtual
-- columns), FOREIGN KEY constraints, and CHECK constraints. Only the PRIMARY
-- KEY remains on every table.
--
-- NOT EXECUTED. Awaiting approval.

CREATE DATABASE IF NOT EXISTS `recovery_stage` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `recovery_stage`;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `addresses` (
  `address_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `country_code` char(2) NOT NULL DEFAULT 'PH',
  `region_code` varchar(20) DEFAULT NULL,
  `region_name` varchar(100) DEFAULT NULL,
  `province_code` varchar(20) DEFAULT NULL,
  `province_name` varchar(100) DEFAULT NULL,
  `city_municipality_code` varchar(20) DEFAULT NULL,
  `city_municipality_name` varchar(120) DEFAULT NULL,
  `barangay_code` varchar(20) DEFAULT NULL,
  `barangay_name` varchar(120) DEFAULT NULL,
  `address_line` varchar(255) DEFAULT NULL,
  `postal_code` varchar(10) DEFAULT NULL,
  `address_source` enum('api','manual','sf1_import') NOT NULL DEFAULT 'manual',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`address_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `genders` (
  `gender_id` tinyint(3) unsigned NOT NULL AUTO_INCREMENT,
  `gender_name` varchar(20) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`gender_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `grade_levels` (
  `grade_level_id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `grade_level_name` varchar(20) NOT NULL,
  PRIMARY KEY (`grade_level_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `educational_attainments` (
  `educational_attainment_id` tinyint(3) unsigned NOT NULL AUTO_INCREMENT,
  `attainment_name` varchar(100) NOT NULL,
  `attainment_order` tinyint(3) unsigned NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`educational_attainment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `majors` (
  `major_id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `major_name` varchar(100) NOT NULL,
  PRIMARY KEY (`major_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `suffixes` (
  `suffix_id` tinyint(3) unsigned NOT NULL AUTO_INCREMENT,
  `suffix_name` varchar(10) NOT NULL,
  `display_order` tinyint(3) unsigned NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`suffix_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `statuses` (
  `status_id` tinyint(3) unsigned NOT NULL AUTO_INCREMENT,
  `status_name` varchar(30) NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`status_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `subjects` (
  `subject_id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `subject_code` varchar(10) DEFAULT NULL,
  `subject_name` varchar(100) NOT NULL,
  PRIMARY KEY (`subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `curriculums` (
  `curriculum_id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `curriculum_name` varchar(80) NOT NULL,
  `version` varchar(30) NOT NULL,
  `description` text DEFAULT NULL,
  `status` enum('active','inactive','archived') NOT NULL DEFAULT 'active',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`curriculum_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `question_types` (
  `question_type_id` smallint(5) unsigned NOT NULL AUTO_INCREMENT,
  `question_type_code` varchar(40) NOT NULL,
  `question_type_name` varchar(80) NOT NULL,
  `capture_mode` enum('omr','ocr','manual','hybrid') NOT NULL,
  `scoring_mode` enum('automatic','manual','hybrid') NOT NULL,
  `supports_omr` tinyint(1) NOT NULL DEFAULT 0,
  `supports_ocr` tinyint(1) NOT NULL DEFAULT 0,
  `supports_multiple_response` tinyint(1) NOT NULL DEFAULT 0,
  `requires_attachment` tinyint(1) NOT NULL DEFAULT 0,
  `requires_teacher_verification` tinyint(1) NOT NULL DEFAULT 1,
  `allows_teacher_answer_edit` tinyint(1) NOT NULL DEFAULT 0,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`question_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `paper_sizes` (
  `paper_size_id` smallint(5) unsigned NOT NULL AUTO_INCREMENT,
  `paper_size_code` varchar(20) NOT NULL,
  `paper_size_name` varchar(60) NOT NULL,
  `width_points` decimal(9,3) NOT NULL,
  `height_points` decimal(9,3) NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`paper_size_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `root_tags` (
  `root_tag_id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `curriculum_id` int(10) unsigned NOT NULL,
  `root_tag_name` varchar(100) NOT NULL,
  `description` text DEFAULT NULL,
  `status` enum('active','inactive') NOT NULL DEFAULT 'active',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`root_tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `school_profiles` (
  `school_id` varchar(20) NOT NULL,
  `address_id` bigint(20) unsigned NOT NULL,
  `school_name` varchar(120) NOT NULL,
  `contact_number` varchar(20) DEFAULT NULL,
  `email` varchar(120) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`school_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `competency_tags` (
  `competency_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `root_tag_id` int(10) unsigned NOT NULL,
  `competency_name` varchar(255) NOT NULL,
  PRIMARY KEY (`competency_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- RISK: unique index on virtual generated column `active_school_id` dropped below.
CREATE TABLE `academic_years` (
  `academic_year_id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `school_id` varchar(20) NOT NULL,
  `curriculum_id` int(10) unsigned NOT NULL,
  `year_name` varchar(15) NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `status` enum('planned','active','completed') NOT NULL DEFAULT 'planned',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `active_school_id` varchar(20) GENERATED ALWAYS AS (case when `status` = 'active' then `school_id` else NULL end) VIRTUAL,
  PRIMARY KEY (`academic_year_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `sections` (
  `section_id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `school_id` varchar(20) NOT NULL,
  `grade_level_id` int(10) unsigned NOT NULL,
  `section_name` varchar(50) NOT NULL,
  PRIMARY KEY (`section_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `classes` (
  `class_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `academic_year_id` int(10) unsigned NOT NULL,
  `section_id` int(10) unsigned NOT NULL,
  `status` enum('active','completed','archived') NOT NULL DEFAULT 'active',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`class_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `users` (
  `user_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `school_id` varchar(20) NOT NULL,
  `address_id` bigint(20) unsigned NOT NULL,
  `gender_id` tinyint(3) unsigned NOT NULL,
  `major_id` int(10) unsigned DEFAULT NULL,
  `educational_attainment_id` tinyint(3) unsigned DEFAULT NULL,
  `role_id` tinyint(3) unsigned NOT NULL,
  `status_id` tinyint(3) unsigned NOT NULL,
  `first_name` varchar(50) NOT NULL,
  `middle_name` varchar(50) DEFAULT NULL,
  `last_name` varchar(50) NOT NULL,
  `suffix_id` tinyint(3) unsigned DEFAULT NULL,
  `birth_date` date DEFAULT NULL,
  `teaching_start_month` tinyint(3) unsigned DEFAULT NULL,
  `teaching_start_year` smallint(5) unsigned DEFAULT NULL,
  `email` varchar(120) NOT NULL,
  `contact_number` varchar(20) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `failed_login_count` smallint(5) unsigned NOT NULL DEFAULT 0,
  `locked_until_at` timestamp NULL DEFAULT NULL,
  `last_login_at` timestamp NULL DEFAULT NULL,
  `password_changed_at` timestamp NULL DEFAULT NULL,
  `email_verified_at` timestamp NULL DEFAULT NULL,
  `contact_verified_at` timestamp NULL DEFAULT NULL,
  `mfa_required` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- NOTE: `details` keeps its non-default column collation (utf8mb4_bin) - this
-- is a physical column attribute, not an index, and must be preserved exactly.
CREATE TABLE `audit_logs` (
  `audit_log_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `audit_uuid` char(36) NOT NULL,
  `user_id` bigint(20) unsigned DEFAULT NULL,
  `action` varchar(100) NOT NULL,
  `entity_type` varchar(100) DEFAULT NULL,
  `entity_id` varchar(100) DEFAULT NULL,
  `outcome` enum('success','failed','denied') NOT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `device_identifier` varchar(100) DEFAULT NULL,
  `user_agent` text DEFAULT NULL,
  `details` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`audit_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `login_attempts` (
  `login_attempt_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) unsigned DEFAULT NULL,
  `attempted_email` varchar(120) NOT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `device_identifier` varchar(100) DEFAULT NULL,
  `user_agent` text DEFAULT NULL,
  `was_successful` tinyint(1) NOT NULL DEFAULT 0,
  `failure_reason` enum('invalid_credentials','pending_email_verification','pending_approval','rejected_account','inactive_account','locked_account','email_not_verified','mfa_required','invalid_mfa_code','mfa_locked','mfa_not_enrolled','rate_limited','other') DEFAULT NULL,
  `attempted_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`login_attempt_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `notifications` (
  `notification_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `notification_uuid` char(36) NOT NULL,
  `recipient_user_id` bigint(20) unsigned NOT NULL,
  `notification_type` varchar(50) NOT NULL,
  `title` varchar(120) NOT NULL,
  `message` varchar(500) NOT NULL,
  `reference_type` varchar(50) DEFAULT NULL,
  `reference_id` varchar(100) DEFAULT NULL,
  `event_key` varchar(150) NOT NULL,
  `read_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`notification_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `verification_challenges` (
  `verification_challenge_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `challenge_uuid` char(36) NOT NULL,
  `user_id` bigint(20) unsigned NOT NULL,
  `verification_purpose` enum('teacher_registration','email_verification','contact_verification','sensitive_action') NOT NULL,
  `delivery_channel` enum('email','sms') NOT NULL,
  `destination_masked` varchar(160) NOT NULL,
  `code_hash` varchar(255) NOT NULL,
  `challenge_status` enum('pending','verified','expired','locked','cancelled') NOT NULL DEFAULT 'pending',
  `delivery_status` enum('queued','sent','failed') NOT NULL DEFAULT 'queued',
  `delivery_provider` varchar(50) DEFAULT NULL,
  `provider_message_id` varchar(120) DEFAULT NULL,
  `attempt_count` smallint(5) unsigned NOT NULL DEFAULT 0,
  `maximum_attempt_count` smallint(5) unsigned NOT NULL DEFAULT 5,
  `resend_count` smallint(5) unsigned NOT NULL DEFAULT 0,
  `last_sent_at` timestamp NULL DEFAULT NULL,
  `next_resend_at` timestamp NULL DEFAULT NULL,
  `expires_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `verified_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`verification_challenge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- RISK: unique index on virtual generated column `active_user_id` dropped below.
CREATE TABLE `user_mfa_factors` (
  `user_mfa_factor_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `factor_uuid` char(36) NOT NULL,
  `user_id` bigint(20) unsigned NOT NULL,
  `factor_type` enum('totp') NOT NULL DEFAULT 'totp',
  `factor_name` varchar(80) NOT NULL DEFAULT 'Authenticator app',
  `secret_ciphertext` varbinary(1024) NOT NULL,
  `secret_key_version` smallint(5) unsigned NOT NULL DEFAULT 1,
  `totp_algorithm` enum('SHA1','SHA256','SHA512') NOT NULL DEFAULT 'SHA1',
  `totp_digits` tinyint(3) unsigned NOT NULL DEFAULT 6,
  `totp_period_seconds` smallint(5) unsigned NOT NULL DEFAULT 30,
  `factor_status` enum('pending','active','disabled','revoked') NOT NULL DEFAULT 'pending',
  `active_user_id` bigint(20) unsigned GENERATED ALWAYS AS (case when `factor_status` = 'active' then `user_id` else NULL end) VIRTUAL,
  `enrolled_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `verified_at` timestamp NULL DEFAULT NULL,
  `last_used_at` timestamp NULL DEFAULT NULL,
  `disabled_at` timestamp NULL DEFAULT NULL,
  `revoked_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`user_mfa_factor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `auth_sessions` (
  `auth_session_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `session_uuid` char(36) NOT NULL,
  `user_id` bigint(20) unsigned NOT NULL,
  `authentication_level` enum('password','mfa') NOT NULL DEFAULT 'password',
  `user_mfa_factor_id` bigint(20) unsigned DEFAULT NULL,
  `mfa_verified_at` timestamp NULL DEFAULT NULL,
  `refresh_token_hash` char(64) NOT NULL,
  `device_identifier` varchar(100) DEFAULT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `user_agent` text DEFAULT NULL,
  `issued_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `expires_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `last_used_at` timestamp NULL DEFAULT NULL,
  `revoked_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`auth_session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `mfa_recovery_codes` (
  `mfa_recovery_code_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `user_mfa_factor_id` bigint(20) unsigned NOT NULL,
  `recovery_batch_uuid` char(36) NOT NULL,
  `code_hash` char(64) NOT NULL,
  `used_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`mfa_recovery_code_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `mfa_authentication_challenges` (
  `mfa_authentication_challenge_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `challenge_uuid` char(36) NOT NULL,
  `user_id` bigint(20) unsigned NOT NULL,
  `user_mfa_factor_id` bigint(20) unsigned NOT NULL,
  `challenge_status` enum('pending','verified','expired','locked','cancelled') NOT NULL DEFAULT 'pending',
  `attempt_count` smallint(5) unsigned NOT NULL DEFAULT 0,
  `maximum_attempt_count` smallint(5) unsigned NOT NULL DEFAULT 5,
  `expires_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `verified_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`mfa_authentication_challenge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `students` (
  `student_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `school_id` varchar(20) NOT NULL,
  `address_id` bigint(20) unsigned NOT NULL,
  `gender_id` tinyint(3) unsigned NOT NULL,
  `student_lrn` varchar(12) NOT NULL,
  `first_name` varchar(50) NOT NULL,
  `middle_name` varchar(50) DEFAULT NULL,
  `last_name` varchar(50) NOT NULL,
  `suffix_id` tinyint(3) unsigned DEFAULT NULL,
  `birth_date` date DEFAULT NULL,
  `status` enum('active','inactive','transferred','graduated') NOT NULL DEFAULT 'active',
  `status_reason` varchar(255) DEFAULT NULL,
  `status_effective_at` timestamp NULL DEFAULT NULL,
  `status_changed_by_user_id` bigint(20) unsigned DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `class_assignments` (
  `class_assignment_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `class_id` bigint(20) unsigned NOT NULL,
  `user_id` bigint(20) unsigned NOT NULL,
  `subject_id` int(10) unsigned NOT NULL,
  `assignment_role` enum('primary','co_teacher') NOT NULL DEFAULT 'primary',
  `assigned_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `ended_at` timestamp NULL DEFAULT NULL,
  `status` enum('active','completed','archived') NOT NULL DEFAULT 'active',
  `status_changed_by_user_id` bigint(20) unsigned DEFAULT NULL,
  `status_reason` varchar(255) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`class_assignment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `class_assignment_schedules` (
  `class_assignment_schedule_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `schedule_uuid` char(36) NOT NULL,
  `class_assignment_id` bigint(20) unsigned NOT NULL,
  `day_of_week` tinyint(3) unsigned NOT NULL,
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
  PRIMARY KEY (`class_assignment_schedule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- RISK: unique index on virtual generated column `active_academic_year_id` dropped below.
CREATE TABLE `term_periods` (
  `term_period_id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `academic_year_id` int(10) unsigned NOT NULL,
  `term_name` varchar(50) NOT NULL,
  `term_order` tinyint(3) unsigned NOT NULL,
  `start_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `end_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `status` enum('planned','active','completed') NOT NULL DEFAULT 'planned',
  `activation_mode` enum('automatic','manual') NOT NULL DEFAULT 'automatic',
  `activated_at` timestamp NULL DEFAULT NULL,
  `completed_at` timestamp NULL DEFAULT NULL,
  `overridden_by_user_id` bigint(20) unsigned DEFAULT NULL,
  `override_reason` varchar(255) DEFAULT NULL,
  `overridden_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `active_academic_year_id` int(10) unsigned GENERATED ALWAYS AS (case when `status` = 'active' then `academic_year_id` else NULL end) VIRTUAL,
  PRIMARY KEY (`term_period_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `skills` (
  `skill_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `competency_id` bigint(20) unsigned NOT NULL,
  `term_period_id` int(10) unsigned NOT NULL,
  `grade_level_id` int(10) unsigned NOT NULL,
  `subject_id` int(10) unsigned NOT NULL,
  PRIMARY KEY (`skill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `interventions` (
  `intervention_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `skill_id` bigint(20) unsigned NOT NULL,
  `intervention_type` enum('remediation','review','practice','enrichment','other') NOT NULL,
  `description` text NOT NULL,
  PRIMARY KEY (`intervention_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `sf1_imports` (
  `sf1_import_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `import_uuid` char(36) NOT NULL,
  `school_id` varchar(20) NOT NULL,
  `academic_year_id` int(10) unsigned NOT NULL,
  `target_class_id` bigint(20) unsigned DEFAULT NULL,
  `uploaded_by_user_id` bigint(20) unsigned NOT NULL,
  `source_file_name` varchar(255) NOT NULL,
  `source_file_hash` char(64) NOT NULL,
  `detected_school_year` varchar(20) DEFAULT NULL,
  `detected_grade_level` varchar(30) DEFAULT NULL,
  `detected_section_name` varchar(100) DEFAULT NULL,
  `import_status` enum('previewed','processing','completed','partial_success','failed','cancelled') NOT NULL DEFAULT 'previewed',
  `total_row_count` int(10) unsigned NOT NULL DEFAULT 0,
  `created_student_count` int(10) unsigned NOT NULL DEFAULT 0,
  `updated_student_count` int(10) unsigned NOT NULL DEFAULT 0,
  `unchanged_student_count` int(10) unsigned NOT NULL DEFAULT 0,
  `conflict_row_count` int(10) unsigned NOT NULL DEFAULT 0,
  `invalid_row_count` int(10) unsigned NOT NULL DEFAULT 0,
  `started_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `completed_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`sf1_import_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- RISK: self-referencing FK (previous_class_list_id) dropped; unique index on
-- virtual generated column `active_academic_year_id` dropped below.
CREATE TABLE `class_lists` (
  `class_list_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `membership_uuid` char(36) NOT NULL,
  `class_id` bigint(20) unsigned NOT NULL,
  `student_id` bigint(20) unsigned NOT NULL,
  `academic_year_id` int(10) unsigned NOT NULL,
  `enrollment_status` enum('enrolled','transferred','dropped','completed') NOT NULL DEFAULT 'enrolled',
  `active_academic_year_id` int(10) unsigned GENERATED ALWAYS AS (case when `enrollment_status` = 'enrolled' then `academic_year_id` else NULL end) VIRTUAL,
  `enrollment_source` enum('sf1','manual','transfer') NOT NULL DEFAULT 'manual',
  `source_sf1_import_id` bigint(20) unsigned DEFAULT NULL,
  `previous_class_list_id` bigint(20) unsigned DEFAULT NULL,
  `enrolled_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `ended_at` timestamp NULL DEFAULT NULL,
  `status_reason` varchar(255) DEFAULT NULL,
  `status_changed_by_user_id` bigint(20) unsigned DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`class_list_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `sf1_import_items` (
  `sf1_import_item_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `sf1_import_id` bigint(20) unsigned NOT NULL,
  `row_number` int(10) unsigned NOT NULL,
  `student_lrn_snapshot` varchar(20) NOT NULL,
  `source_row_hash` char(64) NOT NULL,
  `student_id` bigint(20) unsigned DEFAULT NULL,
  `class_list_id` bigint(20) unsigned DEFAULT NULL,
  `outcome_status` enum('created','updated','existing_unchanged','already_enrolled','enrollment_conflict','invalid') NOT NULL,
  `warning_code` varchar(50) DEFAULT NULL,
  `outcome_message` varchar(255) DEFAULT NULL,
  `processed_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`sf1_import_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `performance_rule_sets` (
  `performance_rule_set_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `rule_set_uuid` char(36) NOT NULL,
  `school_id` varchar(20) DEFAULT NULL,
  `rule_set_name` varchar(120) NOT NULL,
  `rule_version` varchar(30) NOT NULL,
  `metric_scope` enum('student_score','skill_mastery','class_mastery','intervention') NOT NULL,
  `rule_definition` json NOT NULL,
  `rule_status` enum('draft','active','retired') NOT NULL DEFAULT 'draft',
  `approved_by_user_id` bigint(20) unsigned DEFAULT NULL,
  `effective_from_at` timestamp NULL DEFAULT NULL,
  `effective_until_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`performance_rule_set_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `rubrics` (
  `rubric_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `rubric_uuid` char(36) NOT NULL,
  `school_id` varchar(20) NOT NULL,
  `created_by_user_id` bigint(20) unsigned NOT NULL,
  `rubric_name` varchar(120) NOT NULL,
  `description` text DEFAULT NULL,
  `total_points` decimal(8,2) NOT NULL,
  `rubric_status` enum('draft','active','archived') NOT NULL DEFAULT 'draft',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`rubric_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `rubric_criteria` (
  `rubric_criterion_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `rubric_id` bigint(20) unsigned NOT NULL,
  `criterion_order` smallint(5) unsigned NOT NULL,
  `criterion_name` varchar(120) NOT NULL,
  `criterion_description` text NOT NULL,
  `maximum_points` decimal(8,2) NOT NULL,
  `level_definition` json DEFAULT NULL,
  `is_required` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`rubric_criterion_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- RISK: self-referencing FK (source_test_id) dropped below.
CREATE TABLE `tests` (
  `test_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `test_uuid` char(36) NOT NULL,
  `school_id` varchar(20) NOT NULL,
  `created_by_user_id` bigint(20) unsigned NOT NULL,
  `version_number` int(10) unsigned NOT NULL DEFAULT 1,
  `source_test_id` bigint(20) unsigned DEFAULT NULL,
  `term_period_id` int(10) unsigned NOT NULL,
  `test_name` varchar(120) NOT NULL,
  `test_type` enum('quiz','exam','diagnostic','long_test','other') NOT NULL,
  `instructions` text DEFAULT NULL,
  `total_items` int(10) unsigned NOT NULL DEFAULT 0,
  `status` enum('draft','active','completed','archived') NOT NULL DEFAULT 'draft',
  `published_at` timestamp NULL DEFAULT NULL,
  `content_locked_at` timestamp NULL DEFAULT NULL,
  `completed_at` timestamp NULL DEFAULT NULL,
  `archived_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`test_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `test_assignments` (
  `test_assignment_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `assignment_uuid` char(36) NOT NULL,
  `test_id` bigint(20) unsigned NOT NULL,
  `class_assignment_id` bigint(20) unsigned NOT NULL,
  `assigned_by_user_id` bigint(20) unsigned NOT NULL,
  `open_at` timestamp NULL DEFAULT NULL,
  `close_at` timestamp NULL DEFAULT NULL,
  `assignment_status` enum('planned','open','closed','archived') NOT NULL DEFAULT 'planned',
  `allow_late_capture` tinyint(1) NOT NULL DEFAULT 0,
  `outside_schedule_confirmed` tinyint(1) NOT NULL DEFAULT 0,
  `outside_schedule_reason` varchar(255) DEFAULT NULL,
  `outside_schedule_confirmed_by_user_id` bigint(20) unsigned DEFAULT NULL,
  `outside_schedule_confirmed_at` timestamp NULL DEFAULT NULL,
  `assigned_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`test_assignment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `test_parts` (
  `test_part_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `test_id` bigint(20) unsigned NOT NULL,
  `part_order` smallint(5) unsigned NOT NULL,
  `part_name` varchar(80) NOT NULL,
  `question_type_id` smallint(5) unsigned NOT NULL,
  `number_of_items` smallint(5) unsigned NOT NULL,
  `points_per_item` decimal(5,2) NOT NULL DEFAULT 1.00,
  `part_instructions` text DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`test_part_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `questions` (
  `question_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `question_uuid` char(36) NOT NULL,
  `test_part_id` bigint(20) unsigned NOT NULL,
  `question_type_id` smallint(5) unsigned NOT NULL,
  `item_number` int(10) unsigned NOT NULL,
  `question_text` text NOT NULL,
  `maximum_points` decimal(8,2) NOT NULL DEFAULT 1.00,
  `rubric_id` bigint(20) unsigned DEFAULT NULL,
  `response_instructions` text DEFAULT NULL,
  `answer_order_required` tinyint(1) NOT NULL DEFAULT 0,
  `maximum_response_length` int(10) unsigned DEFAULT NULL,
  `expected_response_count` smallint(5) unsigned DEFAULT NULL,
  `response_region_size` enum('none','short','medium','long','full_page') NOT NULL DEFAULT 'none',
  `force_page_break_before` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `question_options` (
  `question_option_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `question_id` bigint(20) unsigned NOT NULL,
  `option_key` varchar(10) NOT NULL,
  `option_text` text NOT NULL,
  `option_order` smallint(5) unsigned NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`question_option_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `accepted_answers` (
  `accepted_answer_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `question_id` bigint(20) unsigned NOT NULL,
  `answer_order` smallint(5) unsigned DEFAULT NULL,
  `accepted_text` varchar(500) NOT NULL,
  `normalized_text` varchar(500) NOT NULL,
  `matching_mode` enum('exact','normalized') NOT NULL DEFAULT 'normalized',
  `is_case_sensitive` tinyint(1) NOT NULL DEFAULT 0,
  `points` decimal(8,2) NOT NULL,
  `is_primary` tinyint(1) NOT NULL DEFAULT 0,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`accepted_answer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `answer_keys` (
  `answer_key_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `question_id` bigint(20) unsigned NOT NULL,
  `answer_key_type` enum('option','accepted_text','rubric','manual') NOT NULL,
  `correct_question_option_id` bigint(20) unsigned DEFAULT NULL,
  `scoring_method` enum('exact','normalized','rubric','manual') NOT NULL,
  `rubric_id` bigint(20) unsigned DEFAULT NULL,
  `answer_explanation` text DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`answer_key_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `part_skill_mappings` (
  `part_skill_mapping_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `test_part_id` bigint(20) unsigned NOT NULL,
  `skill_id` bigint(20) unsigned NOT NULL,
  `start_item_number` int(10) unsigned NOT NULL,
  `end_item_number` int(10) unsigned NOT NULL,
  `item_count` int(10) unsigned NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`part_skill_mapping_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `omr_templates` (
  `omr_template_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `template_code` varchar(80) NOT NULL,
  `template_name` varchar(120) NOT NULL,
  `template_version` varchar(50) NOT NULL,
  `question_type_id` smallint(5) unsigned DEFAULT NULL,
  `paper_size_id` smallint(5) unsigned NOT NULL,
  `page_size` varchar(20) NOT NULL DEFAULT 'A4',
  `page_orientation` enum('portrait','landscape') NOT NULL DEFAULT 'portrait',
  `coordinate_origin` enum('pdf_bottom_left') NOT NULL DEFAULT 'pdf_bottom_left',
  `required_print_scale_percent` decimal(5,2) NOT NULL DEFAULT 100.00,
  `minimum_item_count` smallint(5) unsigned DEFAULT NULL,
  `maximum_item_count` smallint(5) unsigned DEFAULT NULL,
  `option_count` tinyint(3) unsigned DEFAULT NULL,
  `geometry_definition` json NOT NULL,
  `geometry_hash` char(64) NOT NULL,
  `qr_payload_version` smallint(5) unsigned NOT NULL,
  `minimum_scanner_version` varchar(50) NOT NULL,
  `template_status` enum('draft','active','retired') NOT NULL DEFAULT 'draft',
  `created_by_user_id` bigint(20) unsigned DEFAULT NULL,
  `activated_at` timestamp NULL DEFAULT NULL,
  `retired_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`omr_template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `omr_template_regions` (
  `omr_template_region_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `region_uuid` char(36) NOT NULL,
  `omr_template_id` bigint(20) unsigned NOT NULL,
  `region_code` varchar(80) NOT NULL,
  `region_order` smallint(5) unsigned NOT NULL,
  `region_type` enum('objective_bubbles','written_response','page_identity','registration_marker') NOT NULL,
  `question_type_id` smallint(5) unsigned DEFAULT NULL,
  `layout_variant` varchar(40) DEFAULT NULL,
  `response_region_size` enum('none','short','medium','long','full_page') DEFAULT NULL,
  `x_points` decimal(9,3) NOT NULL,
  `y_points` decimal(9,3) NOT NULL,
  `width_points` decimal(9,3) NOT NULL,
  `height_points` decimal(9,3) NOT NULL,
  `geometry_definition` json NOT NULL,
  `geometry_hash` char(64) NOT NULL,
  `is_required` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`omr_template_region_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- RISK: self-referencing FK (source_answer_sheet_version_id) dropped below.
CREATE TABLE `answer_sheet_versions` (
  `answer_sheet_version_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `answer_sheet_uuid` char(36) NOT NULL,
  `test_assignment_id` bigint(20) unsigned NOT NULL,
  `paper_size_id` smallint(5) unsigned NOT NULL,
  `generation_number` int(10) unsigned NOT NULL DEFAULT 1,
  `test_version_number` int(10) unsigned NOT NULL,
  `total_questions` int(10) unsigned NOT NULL,
  `total_pages` smallint(5) unsigned NOT NULL,
  `manifest_version` smallint(5) unsigned NOT NULL DEFAULT 1,
  `manifest_hash` char(64) NOT NULL,
  `pdf_storage_key` varchar(500) DEFAULT NULL,
  `pdf_content_hash` char(64) DEFAULT NULL,
  `pdf_file_size_bytes` bigint(20) unsigned DEFAULT NULL,
  `generation_status` enum('generating','ready','retired','failed') NOT NULL DEFAULT 'generating',
  `source_answer_sheet_version_id` bigint(20) unsigned DEFAULT NULL,
  `generated_by_user_id` bigint(20) unsigned NOT NULL,
  `generated_at` timestamp NULL DEFAULT NULL,
  `retired_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`answer_sheet_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `answer_sheet_pages` (
  `answer_sheet_page_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `page_uuid` char(36) NOT NULL,
  `answer_sheet_version_id` bigint(20) unsigned NOT NULL,
  `omr_template_id` bigint(20) unsigned NOT NULL,
  `page_number` smallint(5) unsigned NOT NULL,
  `total_pages` smallint(5) unsigned NOT NULL,
  `qr_payload` varchar(1000) NOT NULL,
  `qr_payload_hash` char(64) NOT NULL,
  `page_geometry_hash` char(64) NOT NULL,
  `page_status` enum('ready','retired') NOT NULL DEFAULT 'ready',
  `retired_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`answer_sheet_page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `answer_sheet_regions` (
  `answer_sheet_region_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `region_uuid` char(36) NOT NULL,
  `answer_sheet_version_id` bigint(20) unsigned NOT NULL,
  `answer_sheet_page_id` bigint(20) unsigned NOT NULL,
  `omr_template_region_id` bigint(20) unsigned NOT NULL,
  `question_id` bigint(20) unsigned NOT NULL,
  `test_part_id` bigint(20) unsigned NOT NULL,
  `question_type_id` smallint(5) unsigned NOT NULL,
  `global_item_number` int(10) unsigned NOT NULL,
  `part_item_number` int(10) unsigned NOT NULL,
  `region_sequence` smallint(5) unsigned NOT NULL DEFAULT 1,
  `region_type` enum('objective_bubbles','written_response') NOT NULL,
  `response_region_size` enum('none','short','medium','long','full_page') NOT NULL DEFAULT 'none',
  `expected_response_count_snapshot` smallint(5) unsigned DEFAULT NULL,
  `response_line_count` smallint(5) unsigned DEFAULT NULL,
  `geometry_snapshot` json NOT NULL,
  `geometry_hash` char(64) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`answer_sheet_region_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- RISK: self-referencing FK (supersedes_scan_session_id) dropped below.
CREATE TABLE `scan_sessions` (
  `scan_session_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `scan_uuid` char(36) NOT NULL,
  `answer_sheet_version_id` bigint(20) unsigned DEFAULT NULL,
  `omr_template_id` bigint(20) unsigned DEFAULT NULL,
  `test_assignment_id` bigint(20) unsigned NOT NULL,
  `class_list_id` bigint(20) unsigned NOT NULL,
  `expected_page_count` smallint(5) unsigned NOT NULL DEFAULT 1,
  `captured_page_count` smallint(5) unsigned NOT NULL DEFAULT 0,
  `scanned_by_user_id` bigint(20) unsigned NOT NULL,
  `verified_by_user_id` bigint(20) unsigned DEFAULT NULL,
  `device_identifier` varchar(100) DEFAULT NULL,
  `template_version` varchar(50) DEFAULT NULL,
  `scanner_version` varchar(50) NOT NULL,
  `image_hash` char(64) DEFAULT NULL,
  `supersedes_scan_session_id` bigint(20) unsigned DEFAULT NULL,
  `scan_status` enum('captured','processing','needs_verification','accepted','rescan_requested','rejected','superseded','failed') NOT NULL DEFAULT 'captured',
  `failure_code` varchar(50) DEFAULT NULL,
  `failure_detail` varchar(500) DEFAULT NULL,
  `scanned_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `verified_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`scan_session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- RISK: self-referencing FK (supersedes_scan_page_id) dropped; virtual generated
-- column `current_page_number` kept as a column, its 2 unique indexes dropped below.
CREATE TABLE `scan_pages` (
  `scan_page_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `scan_page_uuid` char(36) NOT NULL,
  `scan_session_id` bigint(20) unsigned NOT NULL,
  `answer_sheet_page_id` bigint(20) unsigned NOT NULL,
  `omr_template_id` bigint(20) unsigned NOT NULL,
  `page_number` smallint(5) unsigned NOT NULL,
  `capture_number` smallint(5) unsigned NOT NULL DEFAULT 1,
  `scanner_version` varchar(50) NOT NULL,
  `qr_payload` varchar(1000) NOT NULL,
  `qr_payload_hash` char(64) NOT NULL,
  `image_hash` char(64) NOT NULL,
  `captured_rotation_degrees` smallint(5) unsigned NOT NULL DEFAULT 0,
  `page_status` enum('captured','processing','needs_verification','accepted','rescan_requested','rejected','superseded','failed') NOT NULL DEFAULT 'captured',
  `failure_code` varchar(50) DEFAULT NULL,
  `failure_detail` varchar(500) DEFAULT NULL,
  `supersedes_scan_page_id` bigint(20) unsigned DEFAULT NULL,
  `captured_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `current_page_number` smallint(5) unsigned GENERATED ALWAYS AS (case when `page_status` not in ('rejected','superseded','failed') then `page_number` else NULL end) VIRTUAL,
  PRIMARY KEY (`scan_page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- RISK: virtual generated columns `legacy_scan_session_id`/`legacy_question_id`
-- kept as columns, their unique index dropped below.
CREATE TABLE `omr_detections` (
  `omr_detection_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `detection_uuid` char(36) NOT NULL,
  `scan_session_id` bigint(20) unsigned NOT NULL,
  `scan_page_id` bigint(20) unsigned DEFAULT NULL,
  `answer_sheet_region_id` bigint(20) unsigned DEFAULT NULL,
  `legacy_scan_session_id` bigint(20) unsigned GENERATED ALWAYS AS (case when `scan_page_id` is null then `scan_session_id` else NULL end) VIRTUAL,
  `legacy_question_id` bigint(20) unsigned GENERATED ALWAYS AS (case when `scan_page_id` is null then `question_id` else NULL end) VIRTUAL,
  `question_id` bigint(20) unsigned NOT NULL,
  `detected_option` char(1) DEFAULT NULL,
  `confidence_score` decimal(5,4) NOT NULL,
  `detection_status` enum('detected','blank','multiple_marks','uncertain') NOT NULL,
  `raw_mark` text NOT NULL,
  `detected_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`omr_detection_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `test_results` (
  `test_result_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `result_uuid` char(36) NOT NULL,
  `test_assignment_id` bigint(20) unsigned NOT NULL,
  `class_list_id` bigint(20) unsigned NOT NULL,
  `attempt_number` smallint(5) unsigned NOT NULL DEFAULT 1,
  `total_score` decimal(8,2) NOT NULL,
  `max_score` decimal(8,2) NOT NULL,
  `items_evaluated` int(10) unsigned NOT NULL,
  `result_status` enum('draft','pending_verification','finalized','superseded') NOT NULL DEFAULT 'draft',
  `submitted_at` timestamp NULL DEFAULT NULL,
  `verification_completed_at` timestamp NULL DEFAULT NULL,
  `scored_at` timestamp NULL DEFAULT NULL,
  `finalized_at` timestamp NULL DEFAULT NULL,
  `percentage_snapshot` decimal(7,4) DEFAULT NULL,
  `performance_status` varchar(50) DEFAULT NULL,
  `performance_rule_set_id` bigint(20) unsigned DEFAULT NULL,
  `score_version` int(10) unsigned NOT NULL DEFAULT 1,
  `checked_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`test_result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `intervention_results` (
  `intervention_result_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `test_result_id` bigint(20) unsigned NOT NULL,
  `intervention_id` bigint(20) unsigned NOT NULL,
  `performance_rule_set_id` bigint(20) unsigned NOT NULL,
  `mastery_rate_snapshot` decimal(7,4) NOT NULL,
  `recommendation_status` enum('maintain','review','reteach','priority_intervention') NOT NULL,
  `recommendation_snapshot` varchar(500) NOT NULL,
  `generated_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `acknowledged_by_user_id` bigint(20) unsigned DEFAULT NULL,
  `acknowledged_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`intervention_result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `student_intervention_cases` (
  `student_intervention_case_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `case_uuid` char(36) NOT NULL,
  `school_id` varchar(20) NOT NULL,
  `student_id` bigint(20) unsigned NOT NULL,
  `academic_year_id` int(10) unsigned NOT NULL,
  `term_period_id` int(10) unsigned DEFAULT NULL,
  `class_list_id` bigint(20) unsigned NOT NULL,
  `skill_id` bigint(20) unsigned DEFAULT NULL,
  `source_test_result_id` bigint(20) unsigned DEFAULT NULL,
  `performance_rule_set_id` bigint(20) unsigned DEFAULT NULL,
  `opened_by_user_id` bigint(20) unsigned NOT NULL,
  `assigned_to_user_id` bigint(20) unsigned DEFAULT NULL,
  `case_status` enum('open','in_progress','monitoring','resolved','closed') NOT NULL DEFAULT 'open',
  `priority_level` enum('normal','review','priority') NOT NULL DEFAULT 'normal',
  `recommendation_snapshot` varchar(500) NOT NULL,
  `opened_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `due_at` timestamp NULL DEFAULT NULL,
  `resolved_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`student_intervention_case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `student_intervention_updates` (
  `student_intervention_update_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `student_intervention_case_id` bigint(20) unsigned NOT NULL,
  `updated_by_user_id` bigint(20) unsigned NOT NULL,
  `update_type` enum('note','action','status_change','follow_up','outcome') NOT NULL,
  `previous_status` varchar(30) DEFAULT NULL,
  `new_status` varchar(30) DEFAULT NULL,
  `action_taken` varchar(255) DEFAULT NULL,
  `update_notes` text DEFAULT NULL,
  `outcome_status` enum('pending','improved','no_change','regressed') DEFAULT NULL,
  `follow_up_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`student_intervention_update_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- RISK: virtual generated column `selected_result_id` kept as a column, its
-- unique index dropped below.
CREATE TABLE `test_result_scans` (
  `test_result_scan_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `test_result_id` bigint(20) unsigned NOT NULL,
  `scan_session_id` bigint(20) unsigned NOT NULL,
  `link_status` enum('selected','superseded','rejected') NOT NULL,
  `selected_result_id` bigint(20) unsigned GENERATED ALWAYS AS (case when `link_status` = 'selected' then `test_result_id` else NULL end) VIRTUAL,
  `decided_by_user_id` bigint(20) unsigned NOT NULL,
  `decision_reason` varchar(255) DEFAULT NULL,
  `linked_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`test_result_scan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `student_answers` (
  `student_answer_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `test_result_id` bigint(20) unsigned NOT NULL,
  `question_id` bigint(20) unsigned NOT NULL,
  `verified_by_user_id` bigint(20) unsigned DEFAULT NULL,
  `answer_uuid` char(36) NOT NULL,
  `selected_question_option_id` bigint(20) unsigned DEFAULT NULL,
  `response_text` text DEFAULT NULL,
  `capture_source` enum('omr','ocr','manual') NOT NULL,
  `verified_at` timestamp NULL DEFAULT NULL,
  `answer_status` enum('answered','blank','multiple','uncertain','invalid','pending_manual') NOT NULL,
  `evaluation_status` enum('pending_verification','needs_manual_scoring','scored','finalized') NOT NULL DEFAULT 'pending_verification',
  `is_correct` tinyint(1) DEFAULT NULL,
  `points_earned` decimal(8,2) NOT NULL DEFAULT 0.00,
  `teacher_feedback` text DEFAULT NULL,
  `finalized_at` timestamp NULL DEFAULT NULL,
  `reopened_at` timestamp NULL DEFAULT NULL,
  `score_version` int(10) unsigned NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`student_answer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `answer_attachments` (
  `answer_attachment_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `attachment_uuid` char(36) NOT NULL,
  `student_answer_id` bigint(20) unsigned DEFAULT NULL,
  `scan_session_id` bigint(20) unsigned DEFAULT NULL,
  `scan_page_id` bigint(20) unsigned DEFAULT NULL,
  `answer_sheet_region_id` bigint(20) unsigned DEFAULT NULL,
  `source_answer_attachment_id` bigint(20) unsigned DEFAULT NULL,
  `attachment_type` enum('full_sheet','page_image','original_page','normalized_page','answer_crop','teacher_evidence') NOT NULL,
  `storage_provider` varchar(40) NOT NULL,
  `storage_key` varchar(500) NOT NULL,
  `mime_type` varchar(100) NOT NULL,
  `file_size_bytes` bigint(20) unsigned NOT NULL,
  `content_hash` char(64) NOT NULL,
  `retention_policy_code` varchar(60) NOT NULL DEFAULT 'CENTRAL_PRIVATE_365D_V1',
  `retention_until` timestamp NULL DEFAULT NULL,
  `retention_hold` tinyint(1) NOT NULL DEFAULT 0,
  `retention_hold_reason` varchar(500) DEFAULT NULL,
  `retention_hold_set_by_user_id` bigint(20) unsigned DEFAULT NULL,
  `retention_hold_set_at` timestamp NULL DEFAULT NULL,
  `purge_status` enum('retained','purged','purge_failed') NOT NULL DEFAULT 'retained',
  `last_purge_attempt_at` timestamp NULL DEFAULT NULL,
  `purged_at` timestamp NULL DEFAULT NULL,
  `purged_by_user_id` bigint(20) unsigned DEFAULT NULL,
  `purge_reason` varchar(500) DEFAULT NULL,
  `last_purge_error` varchar(1000) DEFAULT NULL,
  `crop_coordinates` json DEFAULT NULL,
  `captured_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`answer_attachment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `scan_verifications` (
  `scan_verification_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `verification_uuid` char(36) NOT NULL,
  `scan_session_id` bigint(20) unsigned NOT NULL,
  `scan_page_id` bigint(20) unsigned DEFAULT NULL,
  `verified_by_user_id` bigint(20) unsigned NOT NULL,
  `verification_action` enum('accepted','rescan_requested','rejected','superseded') NOT NULL,
  `reason_code` varchar(50) DEFAULT NULL,
  `reason_detail` varchar(500) DEFAULT NULL,
  `decided_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`scan_verification_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `answer_verifications` (
  `answer_verification_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `verification_uuid` char(36) NOT NULL,
  `student_answer_id` bigint(20) unsigned NOT NULL,
  `verified_by_user_id` bigint(20) unsigned NOT NULL,
  `verification_action` enum('transcribed','ocr_confirmed','ocr_corrected','manual_scored','reopened','finalized') NOT NULL,
  `previous_answer_status` varchar(30) DEFAULT NULL,
  `previous_answer_value` text DEFAULT NULL,
  `new_answer_status` varchar(30) NOT NULL,
  `new_answer_value` text DEFAULT NULL,
  `previous_points` decimal(8,2) DEFAULT NULL,
  `new_points` decimal(8,2) NOT NULL,
  `reason_code` varchar(50) DEFAULT NULL,
  `reason_detail` varchar(500) DEFAULT NULL,
  `evidence_attachment_id` bigint(20) unsigned DEFAULT NULL,
  `verified_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`answer_verification_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `answer_rubric_scores` (
  `answer_rubric_score_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `student_answer_id` bigint(20) unsigned NOT NULL,
  `rubric_criterion_id` bigint(20) unsigned NOT NULL,
  `answer_verification_id` bigint(20) unsigned DEFAULT NULL,
  `scored_by_user_id` bigint(20) unsigned NOT NULL,
  `score_version` int(10) unsigned NOT NULL DEFAULT 1,
  `points_awarded` decimal(8,2) NOT NULL,
  `maximum_points_snapshot` decimal(8,2) DEFAULT NULL,
  `criterion_feedback` text DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`answer_rubric_score_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `syncs` (
  `sync_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `sync_uuid` char(36) NOT NULL,
  `user_id` bigint(20) unsigned NOT NULL,
  `test_assignment_id` bigint(20) unsigned DEFAULT NULL,
  `device_identifier` varchar(100) DEFAULT NULL,
  `direction` enum('download','upload') NOT NULL,
  `sync_status` enum('pending','in_progress','partial_success','success','failed') NOT NULL DEFAULT 'pending',
  `payload_hash` char(64) DEFAULT NULL,
  `retry_count` smallint(5) unsigned NOT NULL DEFAULT 0,
  `last_retry_at` timestamp NULL DEFAULT NULL,
  `request_item_count` int(10) unsigned NOT NULL DEFAULT 0,
  `idempotency_version` smallint(5) unsigned NOT NULL DEFAULT 1,
  `started_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `completed_at` timestamp NULL DEFAULT NULL,
  `error_message` text DEFAULT NULL,
  PRIMARY KEY (`sync_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `sync_items` (
  `sync_item_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `sync_id` bigint(20) unsigned NOT NULL,
  `result_uuid` char(36) NOT NULL,
  `test_result_id` bigint(20) unsigned DEFAULT NULL,
  `sync_action` enum('upsert') NOT NULL DEFAULT 'upsert',
  `sync_status` enum('pending','success','failed','skipped') NOT NULL DEFAULT 'pending',
  `attempt_count` smallint(5) unsigned NOT NULL DEFAULT 0,
  `last_attempt_at` timestamp NULL DEFAULT NULL,
  `processed_at` timestamp NULL DEFAULT NULL,
  `error_code` varchar(50) DEFAULT NULL,
  `error_message` text DEFAULT NULL,
  `synced_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`sync_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

SET FOREIGN_KEY_CHECKS = 1;
