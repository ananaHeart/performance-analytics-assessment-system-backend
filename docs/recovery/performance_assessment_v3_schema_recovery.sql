-- SMART Assessment System - reconstructed schema for performance_assessment_v3_db.
--
-- PURPOSE: recovery reference only. Reconstructed by reading, in full, every
-- schema-affecting migration file in docs/migrations/v3/ and merging their
-- cumulative CREATE TABLE / ALTER TABLE / DROP TABLE effects into final
-- CREATE TABLE statements. No database was connected to, queried, or modified
-- to produce this file - it is derived entirely from source control.
--
-- SOURCE MIGRATIONS USED (schema-affecting only; seed/data-only files excluded):
--   V3_000_v2_schema_snapshot.sql            (baseline; note: its own CREATE
--                                              DATABASE/USE statements say
--                                              performance_assessment_v2_db -
--                                              that is a copy-paste artifact of
--                                              how the snapshot was dumped, not
--                                              a real target; V3_001 onward all
--                                              explicitly USE
--                                              performance_assessment_v3_db)
--   V3_001_core_domain.sql
--   V3_002_assessment_capture.sql
--   V3_003_security_sync_intervention.sql
--   V3_006_dynamic_answer_sheet_schema_DRAFT.sql   (filename says DRAFT, but its
--                                              own header states: "Approved and
--                                              applied on 2026-08-30 to the
--                                              isolated local V3 database after
--                                              a verified backup." Confirmed
--                                              load-bearing: the live
--                                              V3AnswerSheetRepository code
--                                              queries paper_sizes,
--                                              answer_sheet_versions,
--                                              answer_sheet_pages,
--                                              answer_sheet_regions directly.
--                                              Included in full.)
--   V3_013_school_scoped_sections.sql        (ALTER only, no new tables)
--   V3_014_academic_calendar_and_class_schedules.sql
--
-- DELIBERATELY EXCLUDED, with reasons:
--   V3_004_reference_seed.sql, V3_005_smoke_test.sql   - no CREATE/ALTER TABLE,
--       seed/test data only.
--   V3_007 through V3_009 (*_DRAFT)   - no CREATE/ALTER TABLE; validation and
--       fixture scripts only, confirmed by grep across the whole file set.
--   V3_010_v2_data_preflight.sql, V3_011_v2_data_migration.sql,
--       V3_012_data_reconciliation.sql   - data migration only, no schema DDL.
--   V3_015 through V3_023 (*_DRAFT, mobile_* tables)   - per
--       docs/V3_PROMPT_18_STAGING_HANDOFF.md these were applied only to a
--       separate synthetic database (v3_scan_validation_full_110003), not to
--       performance_assessment_v3_db. Corroborated by
--       docs/local_v3_delete_demo_teacher_accounts.sql, which enumerates every
--       table that references teacher-owned data across this schema in detail
--       and never mentions any mobile_* table - inconsistent with those tables
--       existing in the real local database.
--   V3_024_dynamic_a4_template.sql   - no CREATE/ALTER TABLE found.
--
-- NOT INCLUDED PER REQUEST: INSERT statements, sample/seed data, DROP DATABASE,
-- DROP TABLE, TRUNCATE, or any migration-execution/session commands. This file
-- has not been run against any database.
--
-- USE THIS FILE WITH CARE: MariaDB's ALTER TABLE ... IMPORT TABLESPACE validates
-- the destination table's structure against the .ibd/.cfg metadata and will
-- normally error out (rather than silently import) on a structural mismatch -
-- but exact column order, types, and lengths still matter for it to succeed at
-- all. See the accompanying report for tables flagged as higher-risk merges.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
SET UNIQUE_CHECKS = 0;
SET SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO';

CREATE DATABASE IF NOT EXISTS `performance_assessment_v3_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `performance_assessment_v3_db`;

-- =====================================================================
-- Reference / lookup tables (V3_000 baseline, unaltered by later migrations)
-- =====================================================================

CREATE TABLE `genders` (
  `gender_id` tinyint(3) unsigned NOT NULL AUTO_INCREMENT,
  `gender_name` varchar(20) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`gender_id`),
  UNIQUE KEY `uk_genders_name` (`gender_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `grade_levels` (
  `grade_level_id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `grade_level_name` varchar(20) NOT NULL,
  PRIMARY KEY (`grade_level_id`),
  UNIQUE KEY `uk_grade_levels_name` (`grade_level_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `educational_attainments` (
  `educational_attainment_id` tinyint(3) unsigned NOT NULL AUTO_INCREMENT,
  `attainment_name` varchar(100) NOT NULL,
  `attainment_order` tinyint(3) unsigned NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`educational_attainment_id`),
  UNIQUE KEY `uk_educational_attainments_name` (`attainment_name`),
  UNIQUE KEY `uk_educational_attainments_order` (`attainment_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `majors` (
  `major_id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `major_name` varchar(100) NOT NULL,
  PRIMARY KEY (`major_id`),
  UNIQUE KEY `uk_majors_name` (`major_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `suffixes` (
  `suffix_id` tinyint(3) unsigned NOT NULL AUTO_INCREMENT,
  `suffix_name` varchar(10) NOT NULL,
  `display_order` tinyint(3) unsigned NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`suffix_id`),
  UNIQUE KEY `uk_suffixes_name` (`suffix_name`),
  UNIQUE KEY `uk_suffixes_order` (`display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `statuses` (
  `status_id` tinyint(3) unsigned NOT NULL AUTO_INCREMENT,
  `status_name` varchar(30) NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`status_id`),
  UNIQUE KEY `uk_statuses_name` (`status_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `roles` (
  `role_id` tinyint(3) unsigned NOT NULL AUTO_INCREMENT,
  `role_name` varchar(30) NOT NULL,
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `uk_roles_name` (`role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `subjects` (
  `subject_id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `subject_code` varchar(10) DEFAULT NULL,
  `subject_name` varchar(100) NOT NULL,
  PRIMARY KEY (`subject_id`),
  UNIQUE KEY `uk_subjects_name` (`subject_name`),
  UNIQUE KEY `uk_subjects_code` (`subject_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `curriculums` (
  `curriculum_id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `curriculum_name` varchar(80) NOT NULL,
  `version` varchar(30) NOT NULL,
  `description` text DEFAULT NULL,
  `status` enum('active','inactive','archived') NOT NULL DEFAULT 'active',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`curriculum_id`),
  UNIQUE KEY `uk_curriculums_name_version` (`curriculum_name`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

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
  PRIMARY KEY (`address_id`),
  KEY `idx_addresses_location` (`region_code`,`province_code`,`city_municipality_code`,`barangay_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `root_tags` (
  `root_tag_id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `curriculum_id` int(10) unsigned NOT NULL,
  `root_tag_name` varchar(100) NOT NULL,
  `description` text DEFAULT NULL,
  `status` enum('active','inactive') NOT NULL DEFAULT 'active',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`root_tag_id`),
  UNIQUE KEY `uk_root_tags_curriculum_name` (`curriculum_id`,`root_tag_name`),
  CONSTRAINT `fk_root_tags_curriculum` FOREIGN KEY (`curriculum_id`) REFERENCES `curriculums` (`curriculum_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `competency_tags` (
  `competency_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `root_tag_id` int(10) unsigned NOT NULL,
  `competency_name` varchar(255) NOT NULL,
  PRIMARY KEY (`competency_id`),
  UNIQUE KEY `uk_competency_tags_root_name` (`root_tag_id`,`competency_name`),
  CONSTRAINT `fk_competency_tags_root_tag` FOREIGN KEY (`root_tag_id`) REFERENCES `root_tags` (`root_tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- =====================================================================
-- School, calendar, and enrollment (V3_000 baseline + V3_013 + V3_014 ALTERs)
-- =====================================================================

CREATE TABLE `school_profiles` (
  `school_id` varchar(20) NOT NULL,
  `address_id` bigint(20) unsigned NOT NULL,
  `school_name` varchar(120) NOT NULL,
  `contact_number` varchar(20) DEFAULT NULL,
  `email` varchar(120) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`school_id`),
  UNIQUE KEY `uk_school_profiles_email` (`email`),
  KEY `fk_school_profiles_address` (`address_id`),
  CONSTRAINT `fk_school_profiles_address` FOREIGN KEY (`address_id`) REFERENCES `addresses` (`address_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- academic_years: V3_000 baseline + V3_014 (school_id, active_school_id, unique keys)
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
  PRIMARY KEY (`academic_year_id`),
  UNIQUE KEY `uk_academic_years_school_name` (`school_id`,`year_name`),
  UNIQUE KEY `uk_academic_years_one_active_school` (`active_school_id`),
  KEY `fk_academic_years_curriculum` (`curriculum_id`),
  CONSTRAINT `fk_academic_years_curriculum` FOREIGN KEY (`curriculum_id`) REFERENCES `curriculums` (`curriculum_id`),
  CONSTRAINT `fk_academic_years_school` FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`),
  CONSTRAINT `chk_academic_years_dates` CHECK (`end_date` >= `start_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- sections: V3_000 baseline + V3_013 (school_id, re-scoped unique key)
CREATE TABLE `sections` (
  `section_id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `school_id` varchar(20) NOT NULL,
  `grade_level_id` int(10) unsigned NOT NULL,
  `section_name` varchar(50) NOT NULL,
  PRIMARY KEY (`section_id`),
  UNIQUE KEY `uk_sections_school_grade_name` (`school_id`,`grade_level_id`,`section_name`),
  KEY `idx_sections_grade_level` (`grade_level_id`),
  CONSTRAINT `fk_sections_grade_level` FOREIGN KEY (`grade_level_id`) REFERENCES `grade_levels` (`grade_level_id`),
  CONSTRAINT `fk_sections_school` FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `classes` (
  `class_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `academic_year_id` int(10) unsigned NOT NULL,
  `section_id` int(10) unsigned NOT NULL,
  `status` enum('active','completed','archived') NOT NULL DEFAULT 'active',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`class_id`),
  UNIQUE KEY `uk_classes_year_section` (`academic_year_id`,`section_id`),
  KEY `fk_classes_section` (`section_id`),
  CONSTRAINT `fk_classes_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`academic_year_id`),
  CONSTRAINT `fk_classes_section` FOREIGN KEY (`section_id`) REFERENCES `sections` (`section_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- =====================================================================
-- Users, identity, and security
-- =====================================================================

-- users: V3_000 baseline + V3_001 (suffix_id, teaching_start_month/year, mfa_required; drop suffix, teaching_start_date)
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
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_users_email` (`email`),
  UNIQUE KEY `uk_users_contact_number` (`contact_number`),
  KEY `fk_users_address` (`address_id`),
  KEY `fk_users_gender` (`gender_id`),
  KEY `fk_users_major` (`major_id`),
  KEY `fk_users_educational_attainment` (`educational_attainment_id`),
  KEY `fk_users_role` (`role_id`),
  KEY `fk_users_status` (`status_id`),
  KEY `fk_users_suffix` (`suffix_id`),
  KEY `idx_users_school_role_status` (`school_id`,`role_id`,`status_id`),
  CONSTRAINT `fk_users_address` FOREIGN KEY (`address_id`) REFERENCES `addresses` (`address_id`),
  CONSTRAINT `fk_users_educational_attainment` FOREIGN KEY (`educational_attainment_id`) REFERENCES `educational_attainments` (`educational_attainment_id`),
  CONSTRAINT `fk_users_gender` FOREIGN KEY (`gender_id`) REFERENCES `genders` (`gender_id`),
  CONSTRAINT `fk_users_major` FOREIGN KEY (`major_id`) REFERENCES `majors` (`major_id`),
  CONSTRAINT `fk_users_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`role_id`),
  CONSTRAINT `fk_users_school` FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`),
  CONSTRAINT `fk_users_status` FOREIGN KEY (`status_id`) REFERENCES `statuses` (`status_id`),
  CONSTRAINT `fk_users_suffix` FOREIGN KEY (`suffix_id`) REFERENCES `suffixes` (`suffix_id`),
  CONSTRAINT `chk_users_teaching_start_month` CHECK (`teaching_start_month` IS NULL OR `teaching_start_month` BETWEEN 1 AND 12),
  CONSTRAINT `chk_users_teaching_start_year` CHECK (`teaching_start_year` IS NULL OR `teaching_start_year` BETWEEN 1900 AND 2200)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

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
  `details` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`details`)),
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`audit_log_id`),
  UNIQUE KEY `uk_audit_logs_uuid` (`audit_uuid`),
  KEY `idx_audit_logs_user_time` (`user_id`,`created_at`),
  KEY `idx_audit_logs_entity` (`entity_type`,`entity_id`),
  KEY `idx_audit_logs_action_time` (`action`,`created_at`),
  KEY `idx_audit_logs_outcome_time` (`outcome`,`created_at`),
  CONSTRAINT `fk_audit_logs_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- auth_sessions: V3_000 baseline + V3_003 (authentication_level, user_mfa_factor_id, mfa_verified_at)
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
  PRIMARY KEY (`auth_session_id`),
  UNIQUE KEY `uk_auth_sessions_uuid` (`session_uuid`),
  UNIQUE KEY `uk_auth_sessions_refresh_token_hash` (`refresh_token_hash`),
  KEY `idx_auth_sessions_user_expiry` (`user_id`,`expires_at`),
  KEY `idx_auth_sessions_user_revoked` (`user_id`,`revoked_at`),
  KEY `fk_auth_sessions_mfa_factor` (`user_mfa_factor_id`),
  CONSTRAINT `fk_auth_sessions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_auth_sessions_mfa_factor` FOREIGN KEY (`user_mfa_factor_id`) REFERENCES `user_mfa_factors` (`user_mfa_factor_id`),
  CONSTRAINT `chk_auth_sessions_mfa_state` CHECK (`authentication_level` = 'password' OR (`user_mfa_factor_id` IS NOT NULL AND `mfa_verified_at` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- login_attempts: V3_000 baseline + V3_003 (widened failure_reason enum)
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
  PRIMARY KEY (`login_attempt_id`),
  KEY `idx_login_attempts_user_time` (`user_id`,`attempted_at`),
  KEY `idx_login_attempts_email_time` (`attempted_email`,`attempted_at`),
  KEY `idx_login_attempts_ip_time` (`ip_address`,`attempted_at`),
  CONSTRAINT `fk_login_attempts_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL
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
  PRIMARY KEY (`notification_id`),
  UNIQUE KEY `uk_notifications_uuid` (`notification_uuid`),
  UNIQUE KEY `uk_notifications_recipient_event` (`recipient_user_id`,`event_key`),
  KEY `idx_notifications_recipient_read_created` (`recipient_user_id`,`read_at`,`created_at`),
  KEY `idx_notifications_reference` (`reference_type`,`reference_id`),
  CONSTRAINT `fk_notifications_recipient` FOREIGN KEY (`recipient_user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- V3_003 new tables: MFA and generalized verification challenges
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
  PRIMARY KEY (`verification_challenge_id`),
  UNIQUE KEY `uk_verification_challenges_uuid` (`challenge_uuid`),
  KEY `idx_verification_challenges_user_status` (`user_id`,`verification_purpose`,`challenge_status`,`expires_at`),
  KEY `idx_verification_challenges_resend` (`user_id`,`delivery_channel`,`next_resend_at`),
  CONSTRAINT `fk_verification_challenges_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_verification_challenges_attempts` CHECK (`maximum_attempt_count` > 0 AND `attempt_count` <= `maximum_attempt_count`),
  CONSTRAINT `chk_verification_challenges_expiry` CHECK (`expires_at` > `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

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
  PRIMARY KEY (`user_mfa_factor_id`),
  UNIQUE KEY `uk_user_mfa_factors_uuid` (`factor_uuid`),
  UNIQUE KEY `uk_user_mfa_factors_one_active` (`active_user_id`,`factor_type`),
  KEY `idx_user_mfa_factors_user_status` (`user_id`,`factor_status`),
  CONSTRAINT `fk_user_mfa_factors_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_user_mfa_factors_totp` CHECK (`totp_digits` IN (6,8) AND `totp_period_seconds` BETWEEN 15 AND 120)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `mfa_recovery_codes` (
  `mfa_recovery_code_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `user_mfa_factor_id` bigint(20) unsigned NOT NULL,
  `recovery_batch_uuid` char(36) NOT NULL,
  `code_hash` char(64) NOT NULL,
  `used_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`mfa_recovery_code_id`),
  UNIQUE KEY `uk_mfa_recovery_codes_hash` (`user_mfa_factor_id`,`code_hash`),
  KEY `idx_mfa_recovery_codes_batch` (`user_mfa_factor_id`,`recovery_batch_uuid`,`used_at`),
  CONSTRAINT `fk_mfa_recovery_codes_factor` FOREIGN KEY (`user_mfa_factor_id`) REFERENCES `user_mfa_factors` (`user_mfa_factor_id`) ON DELETE CASCADE
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
  PRIMARY KEY (`mfa_authentication_challenge_id`),
  UNIQUE KEY `uk_mfa_authentication_challenges_uuid` (`challenge_uuid`),
  KEY `idx_mfa_authentication_challenges_user_status` (`user_id`,`challenge_status`,`expires_at`),
  KEY `fk_mfa_authentication_challenges_factor` (`user_mfa_factor_id`),
  CONSTRAINT `fk_mfa_authentication_challenges_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_mfa_authentication_challenges_factor` FOREIGN KEY (`user_mfa_factor_id`) REFERENCES `user_mfa_factors` (`user_mfa_factor_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_mfa_authentication_challenges_attempts` CHECK (`maximum_attempt_count` > 0 AND `attempt_count` <= `maximum_attempt_count`),
  CONSTRAINT `chk_mfa_authentication_challenges_expiry` CHECK (`expires_at` > `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- =====================================================================
-- Students, enrollment, class assignments, schedules, and skills
-- =====================================================================

-- students: V3_000 baseline + V3_001 (suffix_id, status_reason/effective_at/changed_by, updated_at; drop suffix)
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
  PRIMARY KEY (`student_id`),
  UNIQUE KEY `uk_students_lrn` (`student_lrn`),
  KEY `fk_students_address` (`address_id`),
  KEY `fk_students_gender` (`gender_id`),
  KEY `fk_students_suffix` (`suffix_id`),
  KEY `fk_students_status_changed_by_user` (`status_changed_by_user_id`),
  KEY `idx_students_school_name` (`school_id`,`last_name`,`first_name`),
  CONSTRAINT `fk_students_address` FOREIGN KEY (`address_id`) REFERENCES `addresses` (`address_id`),
  CONSTRAINT `fk_students_gender` FOREIGN KEY (`gender_id`) REFERENCES `genders` (`gender_id`),
  CONSTRAINT `fk_students_school` FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`),
  CONSTRAINT `fk_students_suffix` FOREIGN KEY (`suffix_id`) REFERENCES `suffixes` (`suffix_id`),
  CONSTRAINT `fk_students_status_changed_by_user` FOREIGN KEY (`status_changed_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_students_lrn` CHECK (`student_lrn` REGEXP '^[0-9]{12}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- class_lists: V3_000 baseline + V3_001 (membership_uuid, academic_year_id, enrollment lifecycle columns)
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
  PRIMARY KEY (`class_list_id`),
  UNIQUE KEY `uk_class_lists_class_student` (`class_id`,`student_id`),
  UNIQUE KEY `uk_class_lists_membership_uuid` (`membership_uuid`),
  UNIQUE KEY `uk_class_lists_active_student_year` (`student_id`,`active_academic_year_id`),
  KEY `idx_class_lists_student` (`student_id`),
  KEY `fk_class_lists_academic_year` (`academic_year_id`),
  KEY `fk_class_lists_source_sf1_import` (`source_sf1_import_id`),
  KEY `fk_class_lists_previous_membership` (`previous_class_list_id`),
  KEY `fk_class_lists_status_changed_by_user` (`status_changed_by_user_id`),
  CONSTRAINT `fk_class_lists_class` FOREIGN KEY (`class_id`) REFERENCES `classes` (`class_id`),
  CONSTRAINT `fk_class_lists_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`student_id`),
  CONSTRAINT `fk_class_lists_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`academic_year_id`),
  CONSTRAINT `fk_class_lists_source_sf1_import` FOREIGN KEY (`source_sf1_import_id`) REFERENCES `sf1_imports` (`sf1_import_id`),
  CONSTRAINT `fk_class_lists_previous_membership` FOREIGN KEY (`previous_class_list_id`) REFERENCES `class_lists` (`class_list_id`),
  CONSTRAINT `fk_class_lists_status_changed_by_user` FOREIGN KEY (`status_changed_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_class_lists_end_state` CHECK ((`enrollment_status` = 'enrolled' AND `ended_at` IS NULL) OR (`enrollment_status` <> 'enrolled' AND `ended_at` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- class_assignments: V3_000 baseline + V3_001 (ended_at, status_changed_by_user_id, status_reason)
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
  PRIMARY KEY (`class_assignment_id`),
  UNIQUE KEY `uk_class_assignments_teacher_subject` (`class_id`,`user_id`,`subject_id`),
  KEY `fk_class_assignments_user` (`user_id`),
  KEY `fk_class_assignments_subject` (`subject_id`),
  KEY `fk_class_assignments_status_changed_by_user` (`status_changed_by_user_id`),
  KEY `idx_class_assignments_class_subject` (`class_id`,`subject_id`,`status`),
  CONSTRAINT `fk_class_assignments_class` FOREIGN KEY (`class_id`) REFERENCES `classes` (`class_id`),
  CONSTRAINT `fk_class_assignments_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`subject_id`),
  CONSTRAINT `fk_class_assignments_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_class_assignments_status_changed_by_user` FOREIGN KEY (`status_changed_by_user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- class_assignment_schedules: new in V3_014
CREATE TABLE `class_assignment_schedules` (
  `class_assignment_schedule_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `schedule_uuid` char(36) NOT NULL,
  `class_assignment_id` bigint(20) unsigned NOT NULL,
  `day_of_week` tinyint(3) unsigned NOT NULL COMMENT 'ISO weekday: 1 Monday through 7 Sunday.',
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
  KEY `idx_class_assignment_schedules_lookup` (`class_assignment_id`,`schedule_status`,`day_of_week`,`effective_from`,`effective_to`),
  KEY `fk_class_assignment_schedules_created_by_user` (`created_by_user_id`),
  KEY `fk_class_assignment_schedules_status_user` (`status_changed_by_user_id`),
  CONSTRAINT `fk_class_assignment_schedules_assignment` FOREIGN KEY (`class_assignment_id`) REFERENCES `class_assignments` (`class_assignment_id`),
  CONSTRAINT `fk_class_assignment_schedules_created_by_user` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_class_assignment_schedules_status_user` FOREIGN KEY (`status_changed_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_class_assignment_schedules_day` CHECK (`day_of_week` BETWEEN 1 AND 7),
  CONSTRAINT `chk_class_assignment_schedules_time` CHECK (`end_time` > `start_time`),
  CONSTRAINT `chk_class_assignment_schedules_dates` CHECK (`effective_to` IS NULL OR `effective_to` >= `effective_from`),
  CONSTRAINT `chk_class_assignment_schedules_timezone` CHECK (`timezone_name` = 'Asia/Manila'),
  CONSTRAINT `chk_class_assignment_schedules_archive` CHECK (
      (`schedule_status` = 'active' AND `status_changed_by_user_id` IS NULL AND `status_reason` IS NULL AND `archived_at` IS NULL)
      OR (`schedule_status` = 'archived' AND `status_changed_by_user_id` IS NOT NULL AND CHAR_LENGTH(TRIM(`status_reason`)) >= 5 AND `archived_at` IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- term_periods: V3_000 baseline + V3_001 (heavy expansion) + V3_014 (active_academic_year_id, order check)
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
  PRIMARY KEY (`term_period_id`),
  UNIQUE KEY `uk_term_periods_order` (`academic_year_id`,`term_order`),
  UNIQUE KEY `uk_term_periods_name` (`academic_year_id`,`term_name`),
  UNIQUE KEY `uk_term_periods_one_active_year` (`active_academic_year_id`),
  KEY `fk_term_periods_overridden_by_user` (`overridden_by_user_id`),
  CONSTRAINT `fk_term_periods_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`academic_year_id`),
  CONSTRAINT `fk_term_periods_overridden_by_user` FOREIGN KEY (`overridden_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_term_periods_dates` CHECK (`end_at` > `start_at`),
  CONSTRAINT `chk_term_periods_override` CHECK (`overridden_by_user_id` IS NULL OR (`override_reason` IS NOT NULL AND `overridden_at` IS NOT NULL)),
  CONSTRAINT `chk_term_periods_order` CHECK (`term_order` BETWEEN 1 AND 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `skills` (
  `skill_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `competency_id` bigint(20) unsigned NOT NULL,
  `term_period_id` int(10) unsigned NOT NULL,
  `grade_level_id` int(10) unsigned NOT NULL,
  `subject_id` int(10) unsigned NOT NULL,
  PRIMARY KEY (`skill_id`),
  UNIQUE KEY `uk_skills_context` (`competency_id`,`term_period_id`,`grade_level_id`,`subject_id`),
  KEY `fk_skills_term_period` (`term_period_id`),
  KEY `fk_skills_grade_level` (`grade_level_id`),
  KEY `fk_skills_subject` (`subject_id`),
  CONSTRAINT `fk_skills_competency` FOREIGN KEY (`competency_id`) REFERENCES `competency_tags` (`competency_id`),
  CONSTRAINT `fk_skills_grade_level` FOREIGN KEY (`grade_level_id`) REFERENCES `grade_levels` (`grade_level_id`),
  CONSTRAINT `fk_skills_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`subject_id`),
  CONSTRAINT `fk_skills_term_period` FOREIGN KEY (`term_period_id`) REFERENCES `term_periods` (`term_period_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `interventions` (
  `intervention_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `skill_id` bigint(20) unsigned NOT NULL,
  `intervention_type` enum('remediation','review','practice','enrichment','other') NOT NULL,
  `description` text NOT NULL,
  PRIMARY KEY (`intervention_id`),
  UNIQUE KEY `uk_interventions_skill_type` (`skill_id`,`intervention_type`),
  CONSTRAINT `fk_interventions_skill` FOREIGN KEY (`skill_id`) REFERENCES `skills` (`skill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- intervention_results: V3_000 baseline + V3_003 (performance_rule_set_id and generated-recommendation columns)
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
  PRIMARY KEY (`intervention_result_id`),
  UNIQUE KEY `uk_intervention_results_result_intervention` (`test_result_id`,`intervention_id`),
  KEY `fk_intervention_results_intervention` (`intervention_id`),
  KEY `fk_intervention_results_performance_rule_set` (`performance_rule_set_id`),
  KEY `fk_intervention_results_acknowledged_by_user` (`acknowledged_by_user_id`),
  CONSTRAINT `fk_intervention_results_intervention` FOREIGN KEY (`intervention_id`) REFERENCES `interventions` (`intervention_id`),
  CONSTRAINT `fk_intervention_results_test_result` FOREIGN KEY (`test_result_id`) REFERENCES `test_results` (`test_result_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_intervention_results_performance_rule_set` FOREIGN KEY (`performance_rule_set_id`) REFERENCES `performance_rule_sets` (`performance_rule_set_id`),
  CONSTRAINT `fk_intervention_results_acknowledged_by_user` FOREIGN KEY (`acknowledged_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_intervention_results_mastery` CHECK (`mastery_rate_snapshot` BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- V3_003 new tables: student intervention case management
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
  PRIMARY KEY (`student_intervention_case_id`),
  UNIQUE KEY `uk_student_intervention_cases_uuid` (`case_uuid`),
  KEY `fk_student_intervention_cases_school` (`school_id`),
  KEY `fk_student_intervention_cases_academic_year` (`academic_year_id`),
  KEY `fk_student_intervention_cases_term_period` (`term_period_id`),
  KEY `fk_student_intervention_cases_class_list` (`class_list_id`),
  KEY `fk_student_intervention_cases_skill` (`skill_id`),
  KEY `fk_student_intervention_cases_source_result` (`source_test_result_id`),
  KEY `fk_student_intervention_cases_rule_set` (`performance_rule_set_id`),
  KEY `fk_student_intervention_cases_opened_by_user` (`opened_by_user_id`),
  KEY `idx_student_intervention_cases_student_status` (`student_id`,`academic_year_id`,`case_status`),
  KEY `idx_student_intervention_cases_assignee_status` (`assigned_to_user_id`,`case_status`,`due_at`),
  CONSTRAINT `fk_student_intervention_cases_school` FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`),
  CONSTRAINT `fk_student_intervention_cases_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`student_id`),
  CONSTRAINT `fk_student_intervention_cases_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`academic_year_id`),
  CONSTRAINT `fk_student_intervention_cases_term_period` FOREIGN KEY (`term_period_id`) REFERENCES `term_periods` (`term_period_id`),
  CONSTRAINT `fk_student_intervention_cases_class_list` FOREIGN KEY (`class_list_id`) REFERENCES `class_lists` (`class_list_id`),
  CONSTRAINT `fk_student_intervention_cases_skill` FOREIGN KEY (`skill_id`) REFERENCES `skills` (`skill_id`),
  CONSTRAINT `fk_student_intervention_cases_source_result` FOREIGN KEY (`source_test_result_id`) REFERENCES `test_results` (`test_result_id`),
  CONSTRAINT `fk_student_intervention_cases_rule_set` FOREIGN KEY (`performance_rule_set_id`) REFERENCES `performance_rule_sets` (`performance_rule_set_id`),
  CONSTRAINT `fk_student_intervention_cases_opened_by_user` FOREIGN KEY (`opened_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_student_intervention_cases_assigned_to_user` FOREIGN KEY (`assigned_to_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_student_intervention_cases_dates` CHECK (`resolved_at` IS NULL OR `resolved_at` >= `opened_at`)
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
  PRIMARY KEY (`student_intervention_update_id`),
  KEY `fk_student_intervention_updates_user` (`updated_by_user_id`),
  KEY `idx_student_intervention_updates_case_time` (`student_intervention_case_id`,`created_at`),
  CONSTRAINT `fk_student_intervention_updates_case` FOREIGN KEY (`student_intervention_case_id`) REFERENCES `student_intervention_cases` (`student_intervention_case_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_student_intervention_updates_user` FOREIGN KEY (`updated_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_student_intervention_updates_status_change` CHECK (`update_type` <> 'status_change' OR (`previous_status` IS NOT NULL AND `new_status` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- =====================================================================
-- SF1 import, question types, rubrics, and performance rule sets (V3_001)
-- =====================================================================

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
  PRIMARY KEY (`sf1_import_id`),
  UNIQUE KEY `uk_sf1_imports_uuid` (`import_uuid`),
  KEY `fk_sf1_imports_academic_year` (`academic_year_id`),
  KEY `fk_sf1_imports_target_class` (`target_class_id`),
  KEY `idx_sf1_imports_duplicate_warning` (`school_id`,`academic_year_id`,`source_file_hash`),
  KEY `idx_sf1_imports_uploader_time` (`uploaded_by_user_id`,`created_at`),
  CONSTRAINT `fk_sf1_imports_school` FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`),
  CONSTRAINT `fk_sf1_imports_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`academic_year_id`),
  CONSTRAINT `fk_sf1_imports_target_class` FOREIGN KEY (`target_class_id`) REFERENCES `classes` (`class_id`),
  CONSTRAINT `fk_sf1_imports_uploaded_by_user` FOREIGN KEY (`uploaded_by_user_id`) REFERENCES `users` (`user_id`)
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
  PRIMARY KEY (`sf1_import_item_id`),
  UNIQUE KEY `uk_sf1_import_items_row` (`sf1_import_id`,`row_number`),
  KEY `fk_sf1_import_items_student` (`student_id`),
  KEY `fk_sf1_import_items_class_list` (`class_list_id`),
  KEY `idx_sf1_import_items_lrn` (`student_lrn_snapshot`),
  KEY `idx_sf1_import_items_outcome` (`sf1_import_id`,`outcome_status`),
  CONSTRAINT `fk_sf1_import_items_import` FOREIGN KEY (`sf1_import_id`) REFERENCES `sf1_imports` (`sf1_import_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_sf1_import_items_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`student_id`),
  CONSTRAINT `fk_sf1_import_items_class_list` FOREIGN KEY (`class_list_id`) REFERENCES `class_lists` (`class_list_id`)
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
  PRIMARY KEY (`question_type_id`),
  UNIQUE KEY `uk_question_types_code` (`question_type_code`),
  UNIQUE KEY `uk_question_types_name` (`question_type_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `performance_rule_sets` (
  `performance_rule_set_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `rule_set_uuid` char(36) NOT NULL,
  `school_id` varchar(20) DEFAULT NULL,
  `rule_set_name` varchar(120) NOT NULL,
  `rule_version` varchar(30) NOT NULL,
  `metric_scope` enum('student_score','skill_mastery','class_mastery','intervention') NOT NULL,
  `rule_definition` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`rule_definition`)),
  `rule_status` enum('draft','active','retired') NOT NULL DEFAULT 'draft',
  `approved_by_user_id` bigint(20) unsigned DEFAULT NULL,
  `effective_from_at` timestamp NULL DEFAULT NULL,
  `effective_until_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`performance_rule_set_id`),
  UNIQUE KEY `uk_performance_rule_sets_uuid` (`rule_set_uuid`),
  UNIQUE KEY `uk_performance_rule_sets_version` (`school_id`,`rule_set_name`,`rule_version`,`metric_scope`),
  KEY `fk_performance_rule_sets_approved_by_user` (`approved_by_user_id`),
  KEY `idx_performance_rule_sets_lookup` (`school_id`,`metric_scope`,`rule_status`),
  CONSTRAINT `fk_performance_rule_sets_school` FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`),
  CONSTRAINT `fk_performance_rule_sets_approved_by_user` FOREIGN KEY (`approved_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_performance_rule_sets_dates` CHECK (`effective_until_at` IS NULL OR `effective_from_at` IS NULL OR `effective_until_at` > `effective_from_at`)
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
  PRIMARY KEY (`rubric_id`),
  UNIQUE KEY `uk_rubrics_uuid` (`rubric_uuid`),
  UNIQUE KEY `uk_rubrics_school_name` (`school_id`,`rubric_name`),
  KEY `fk_rubrics_created_by_user` (`created_by_user_id`),
  CONSTRAINT `fk_rubrics_school` FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`),
  CONSTRAINT `fk_rubrics_created_by_user` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_rubrics_total_points` CHECK (`total_points` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `rubric_criteria` (
  `rubric_criterion_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `rubric_id` bigint(20) unsigned NOT NULL,
  `criterion_order` smallint(5) unsigned NOT NULL,
  `criterion_name` varchar(120) NOT NULL,
  `criterion_description` text NOT NULL,
  `maximum_points` decimal(8,2) NOT NULL,
  `level_definition` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`level_definition`)),
  `is_required` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`rubric_criterion_id`),
  UNIQUE KEY `uk_rubric_criteria_order` (`rubric_id`,`criterion_order`),
  CONSTRAINT `fk_rubric_criteria_rubric` FOREIGN KEY (`rubric_id`) REFERENCES `rubrics` (`rubric_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_rubric_criteria_maximum_points` CHECK (`maximum_points` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- =====================================================================
-- Assessment content: tests, parts, assignments, questions, options, answers
-- =====================================================================

-- tests: V3_000 baseline + V3_001 (heavy rework: test_uuid, school_id, versioning, lifecycle timestamps; drop class_assignment_id, test_date)
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
  PRIMARY KEY (`test_id`),
  UNIQUE KEY `uk_tests_uuid` (`test_uuid`),
  KEY `fk_tests_term_period` (`term_period_id`),
  KEY `fk_tests_created_by_user` (`created_by_user_id`),
  KEY `fk_tests_source_test` (`source_test_id`),
  KEY `idx_tests_owner_term_status` (`school_id`,`created_by_user_id`,`term_period_id`,`status`),
  CONSTRAINT `fk_tests_term_period` FOREIGN KEY (`term_period_id`) REFERENCES `term_periods` (`term_period_id`),
  CONSTRAINT `fk_tests_school` FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`),
  CONSTRAINT `fk_tests_created_by_user` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_tests_source_test` FOREIGN KEY (`source_test_id`) REFERENCES `tests` (`test_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- test_assignments: new in V3_001, extended by V3_014 (outside-schedule confirmation columns)
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
  PRIMARY KEY (`test_assignment_id`),
  UNIQUE KEY `uk_test_assignments_uuid` (`assignment_uuid`),
  UNIQUE KEY `uk_test_assignments_test_class` (`test_id`,`class_assignment_id`),
  KEY `fk_test_assignments_assigned_by_user` (`assigned_by_user_id`),
  KEY `fk_test_assignments_outside_schedule_user` (`outside_schedule_confirmed_by_user_id`),
  KEY `idx_test_assignments_class_status` (`class_assignment_id`,`assignment_status`,`open_at`,`close_at`),
  CONSTRAINT `fk_test_assignments_test` FOREIGN KEY (`test_id`) REFERENCES `tests` (`test_id`),
  CONSTRAINT `fk_test_assignments_class_assignment` FOREIGN KEY (`class_assignment_id`) REFERENCES `class_assignments` (`class_assignment_id`),
  CONSTRAINT `fk_test_assignments_assigned_by_user` FOREIGN KEY (`assigned_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_test_assignments_outside_schedule_user` FOREIGN KEY (`outside_schedule_confirmed_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_test_assignments_dates` CHECK (`close_at` IS NULL OR `open_at` IS NULL OR `close_at` > `open_at`),
  CONSTRAINT `chk_test_assignments_outside_schedule_confirmation` CHECK (
      (`outside_schedule_confirmed` = 0 AND `outside_schedule_reason` IS NULL AND `outside_schedule_confirmed_by_user_id` IS NULL AND `outside_schedule_confirmed_at` IS NULL)
      OR (`outside_schedule_confirmed` = 1 AND CHAR_LENGTH(TRIM(`outside_schedule_reason`)) >= 5 AND `outside_schedule_confirmed_by_user_id` IS NOT NULL AND `outside_schedule_confirmed_at` IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- test_parts: V3_000 baseline + V3_001 (question_type_id, part_instructions, timestamps; drop part_type)
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
  PRIMARY KEY (`test_part_id`),
  UNIQUE KEY `uk_test_parts_order` (`test_id`,`part_order`),
  KEY `fk_test_parts_question_type` (`question_type_id`),
  CONSTRAINT `fk_test_parts_test` FOREIGN KEY (`test_id`) REFERENCES `tests` (`test_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_test_parts_question_type` FOREIGN KEY (`question_type_id`) REFERENCES `question_types` (`question_type_id`),
  CONSTRAINT `chk_test_parts_number_of_items` CHECK (`number_of_items` > 0),
  CONSTRAINT `chk_test_parts_points_per_item` CHECK (`points_per_item` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- questions: V3_000 baseline + V3_001 (heavy rework) + V3_006_DRAFT (response_region_size, force_page_break_before)
-- CORRECTED 2026-09-16: added expected_response_count (was missing entirely -
-- introduced by V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql, which
-- my original migration audit missed because it only grepped for CREATE TABLE,
-- not ALTER TABLE. Verified against docs/backups/performance_assessment_v3_db_pre_V3_014_20260903_163000.sql.
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
  PRIMARY KEY (`question_id`),
  UNIQUE KEY `uk_questions_part_item` (`test_part_id`,`item_number`),
  UNIQUE KEY `uk_questions_uuid` (`question_uuid`),
  KEY `fk_questions_question_type` (`question_type_id`),
  KEY `fk_questions_rubric` (`rubric_id`),
  CONSTRAINT `fk_questions_test_part` FOREIGN KEY (`test_part_id`) REFERENCES `test_parts` (`test_part_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_questions_question_type` FOREIGN KEY (`question_type_id`) REFERENCES `question_types` (`question_type_id`),
  CONSTRAINT `fk_questions_rubric` FOREIGN KEY (`rubric_id`) REFERENCES `rubrics` (`rubric_id`),
  CONSTRAINT `chk_questions_maximum_points` CHECK (`maximum_points` > 0),
  CONSTRAINT `chk_questions_maximum_response_length` CHECK (`maximum_response_length` IS NULL OR `maximum_response_length` > 0),
  CONSTRAINT `chk_questions_expected_response_count` CHECK (`expected_response_count` IS NULL OR `expected_response_count` >= 1)
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
  PRIMARY KEY (`question_option_id`),
  UNIQUE KEY `uk_question_options_key` (`question_id`,`option_key`),
  UNIQUE KEY `uk_question_options_order` (`question_id`,`option_order`),
  CONSTRAINT `fk_question_options_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`question_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_question_options_order` CHECK (`option_order` > 0)
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
  PRIMARY KEY (`accepted_answer_id`),
  UNIQUE KEY `uk_accepted_answers_variant` (`question_id`,`answer_order`,`normalized_text`),
  CONSTRAINT `fk_accepted_answers_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`question_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_accepted_answers_points` CHECK (`points` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- answer_keys: V3_000 baseline, fully reworked in V3_001 (answer_key_type/scoring_method/rubric strategy; drop correct_option)
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
  PRIMARY KEY (`answer_key_id`),
  UNIQUE KEY `uk_answer_keys_question` (`question_id`),
  KEY `fk_answer_keys_correct_question_option` (`correct_question_option_id`),
  KEY `fk_answer_keys_rubric` (`rubric_id`),
  CONSTRAINT `fk_answer_keys_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`question_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_answer_keys_correct_question_option` FOREIGN KEY (`correct_question_option_id`) REFERENCES `question_options` (`question_option_id`),
  CONSTRAINT `fk_answer_keys_rubric` FOREIGN KEY (`rubric_id`) REFERENCES `rubrics` (`rubric_id`),
  CONSTRAINT `chk_answer_keys_strategy` CHECK (
      (`answer_key_type` = 'option' AND `correct_question_option_id` IS NOT NULL AND `rubric_id` IS NULL)
      OR (`answer_key_type` = 'accepted_text' AND `correct_question_option_id` IS NULL AND `rubric_id` IS NULL)
      OR (`answer_key_type` = 'rubric' AND `correct_question_option_id` IS NULL AND `rubric_id` IS NOT NULL)
      OR (`answer_key_type` = 'manual' AND `correct_question_option_id` IS NULL)
  )
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
  PRIMARY KEY (`part_skill_mapping_id`),
  UNIQUE KEY `uk_part_skill_mappings_range` (`test_part_id`,`skill_id`,`start_item_number`,`end_item_number`),
  KEY `fk_part_skill_mappings_skill` (`skill_id`),
  KEY `idx_part_skill_mappings_part_range` (`test_part_id`,`start_item_number`,`end_item_number`),
  CONSTRAINT `fk_part_skill_mappings_test_part` FOREIGN KEY (`test_part_id`) REFERENCES `test_parts` (`test_part_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_part_skill_mappings_skill` FOREIGN KEY (`skill_id`) REFERENCES `skills` (`skill_id`),
  CONSTRAINT `chk_part_skill_mappings_range` CHECK (`start_item_number` >= 1 AND `end_item_number` >= `start_item_number` AND `item_count` = `end_item_number` - `start_item_number` + 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- =====================================================================
-- OMR templates and paper geometry (V3_002 baseline + V3_006_DRAFT expansion)
-- Confirmed load-bearing: V3AnswerSheetRepository queries paper_sizes and
-- omr_templates directly for the live fixed-sheet generation path.
-- =====================================================================

CREATE TABLE `paper_sizes` (
  `paper_size_id` smallint(5) unsigned NOT NULL AUTO_INCREMENT,
  `paper_size_code` varchar(20) NOT NULL,
  `paper_size_name` varchar(60) NOT NULL,
  `width_points` decimal(9,3) NOT NULL,
  `height_points` decimal(9,3) NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`paper_size_id`),
  UNIQUE KEY `uk_paper_sizes_code` (`paper_size_code`),
  CONSTRAINT `chk_paper_sizes_dimensions` CHECK (`width_points` > 0 AND `height_points` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- omr_templates: V3_002 baseline, heavily extended by V3_006_DRAFT (paper_size_id,
-- template_version, coordinate_origin, required_print_scale_percent, geometry_hash,
-- retired_at; several columns widened to nullable to support mixed-geometry templates)
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
  `geometry_definition` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`geometry_definition`)),
  `geometry_hash` char(64) NOT NULL,
  `qr_payload_version` smallint(5) unsigned NOT NULL,
  `minimum_scanner_version` varchar(50) NOT NULL,
  `template_status` enum('draft','active','retired') NOT NULL DEFAULT 'draft',
  `created_by_user_id` bigint(20) unsigned DEFAULT NULL,
  `activated_at` timestamp NULL DEFAULT NULL,
  `retired_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`omr_template_id`),
  UNIQUE KEY `uk_omr_templates_code` (`template_code`),
  KEY `fk_omr_templates_created_by_user` (`created_by_user_id`),
  KEY `idx_omr_templates_capability` (`question_type_id`,`template_status`,`minimum_item_count`,`maximum_item_count`),
  KEY `idx_omr_templates_paper_status` (`paper_size_id`,`page_orientation`,`template_status`),
  CONSTRAINT `fk_omr_templates_question_type` FOREIGN KEY (`question_type_id`) REFERENCES `question_types` (`question_type_id`),
  CONSTRAINT `fk_omr_templates_created_by_user` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_omr_templates_paper_size` FOREIGN KEY (`paper_size_id`) REFERENCES `paper_sizes` (`paper_size_id`),
  CONSTRAINT `chk_omr_templates_item_count` CHECK ((`minimum_item_count` IS NULL AND `maximum_item_count` IS NULL) OR (`minimum_item_count` >= 1 AND `maximum_item_count` >= `minimum_item_count`)),
  CONSTRAINT `chk_omr_templates_option_count` CHECK (`option_count` IS NULL OR `option_count` BETWEEN 2 AND 4),
  CONSTRAINT `chk_omr_templates_print_scale` CHECK (`required_print_scale_percent` = 100.00)
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
  `geometry_definition` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`geometry_definition`)),
  `geometry_hash` char(64) NOT NULL,
  `is_required` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`omr_template_region_id`),
  UNIQUE KEY `uk_omr_template_regions_uuid` (`region_uuid`),
  UNIQUE KEY `uk_omr_template_regions_code` (`omr_template_id`,`region_code`),
  UNIQUE KEY `uk_omr_template_regions_order` (`omr_template_id`,`region_order`),
  KEY `fk_omr_template_regions_question_type` (`question_type_id`),
  KEY `idx_omr_template_regions_capability` (`omr_template_id`,`region_type`,`question_type_id`,`response_region_size`),
  CONSTRAINT `fk_omr_template_regions_template` FOREIGN KEY (`omr_template_id`) REFERENCES `omr_templates` (`omr_template_id`),
  CONSTRAINT `fk_omr_template_regions_question_type` FOREIGN KEY (`question_type_id`) REFERENCES `question_types` (`question_type_id`),
  CONSTRAINT `chk_omr_template_regions_bounds` CHECK (`x_points` >= 0 AND `y_points` >= 0 AND `width_points` > 0 AND `height_points` > 0),
  CONSTRAINT `chk_omr_template_regions_question_type` CHECK (
      (`region_type` IN ('objective_bubbles','written_response') AND `question_type_id` IS NOT NULL)
      OR (`region_type` IN ('page_identity','registration_marker') AND `question_type_id` IS NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

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
  PRIMARY KEY (`answer_sheet_version_id`),
  UNIQUE KEY `uk_answer_sheet_versions_uuid` (`answer_sheet_uuid`),
  UNIQUE KEY `uk_answer_sheet_versions_generation` (`test_assignment_id`,`paper_size_id`,`generation_number`),
  UNIQUE KEY `uk_answer_sheet_versions_manifest` (`test_assignment_id`,`paper_size_id`,`manifest_hash`),
  KEY `fk_answer_sheet_versions_paper_size` (`paper_size_id`),
  KEY `fk_answer_sheet_versions_source` (`source_answer_sheet_version_id`),
  KEY `fk_answer_sheet_versions_generated_by_user` (`generated_by_user_id`),
  KEY `idx_answer_sheet_versions_assignment_status` (`test_assignment_id`,`generation_status`,`generated_at`),
  CONSTRAINT `fk_answer_sheet_versions_test_assignment` FOREIGN KEY (`test_assignment_id`) REFERENCES `test_assignments` (`test_assignment_id`),
  CONSTRAINT `fk_answer_sheet_versions_paper_size` FOREIGN KEY (`paper_size_id`) REFERENCES `paper_sizes` (`paper_size_id`),
  CONSTRAINT `fk_answer_sheet_versions_source` FOREIGN KEY (`source_answer_sheet_version_id`) REFERENCES `answer_sheet_versions` (`answer_sheet_version_id`),
  CONSTRAINT `fk_answer_sheet_versions_generated_by_user` FOREIGN KEY (`generated_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_answer_sheet_versions_minimum_questions` CHECK (`total_questions` >= 5),
  CONSTRAINT `chk_answer_sheet_versions_pages` CHECK (`total_pages` >= 1),
  CONSTRAINT `chk_answer_sheet_versions_generation` CHECK (`generation_number` >= 1),
  CONSTRAINT `chk_answer_sheet_versions_content_version` CHECK (`test_version_number` >= 1 AND `manifest_version` >= 1),
  CONSTRAINT `chk_answer_sheet_versions_pdf_size` CHECK (`pdf_file_size_bytes` IS NULL OR `pdf_file_size_bytes` > 0)
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
  PRIMARY KEY (`answer_sheet_page_id`),
  UNIQUE KEY `uk_answer_sheet_pages_uuid` (`page_uuid`),
  UNIQUE KEY `uk_answer_sheet_pages_number` (`answer_sheet_version_id`,`page_number`),
  UNIQUE KEY `uk_answer_sheet_pages_qr_hash` (`qr_payload_hash`),
  KEY `idx_answer_sheet_pages_template` (`omr_template_id`,`page_status`),
  CONSTRAINT `fk_answer_sheet_pages_version` FOREIGN KEY (`answer_sheet_version_id`) REFERENCES `answer_sheet_versions` (`answer_sheet_version_id`),
  CONSTRAINT `fk_answer_sheet_pages_template` FOREIGN KEY (`omr_template_id`) REFERENCES `omr_templates` (`omr_template_id`),
  CONSTRAINT `chk_answer_sheet_pages_number` CHECK (`page_number` >= 1 AND `total_pages` >= `page_number`),
  CONSTRAINT `chk_answer_sheet_pages_qr_payload` CHECK (OCTET_LENGTH(`qr_payload`) BETWEEN 2 AND 256),
  CONSTRAINT `chk_answer_sheet_pages_qr_hash` CHECK (CAST(`qr_payload_hash` AS CHAR CHARACTER SET binary) REGEXP '^[0-9a-f]{64}$' AND `qr_payload_hash` = SHA2(`qr_payload`,256))
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
  `geometry_snapshot` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`geometry_snapshot`)),
  `geometry_hash` char(64) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`answer_sheet_region_id`),
  UNIQUE KEY `uk_answer_sheet_regions_uuid` (`region_uuid`),
  UNIQUE KEY `uk_answer_sheet_regions_question_sequence` (`answer_sheet_version_id`,`question_id`,`region_sequence`),
  UNIQUE KEY `uk_answer_sheet_regions_page_slot` (`answer_sheet_page_id`,`omr_template_region_id`),
  KEY `fk_answer_sheet_regions_template_region` (`omr_template_region_id`),
  KEY `fk_answer_sheet_regions_question` (`question_id`),
  KEY `fk_answer_sheet_regions_test_part` (`test_part_id`),
  KEY `fk_answer_sheet_regions_question_type` (`question_type_id`),
  KEY `idx_answer_sheet_regions_page_order` (`answer_sheet_page_id`,`global_item_number`,`region_sequence`),
  CONSTRAINT `fk_answer_sheet_regions_version` FOREIGN KEY (`answer_sheet_version_id`) REFERENCES `answer_sheet_versions` (`answer_sheet_version_id`),
  CONSTRAINT `fk_answer_sheet_regions_page` FOREIGN KEY (`answer_sheet_page_id`) REFERENCES `answer_sheet_pages` (`answer_sheet_page_id`),
  CONSTRAINT `fk_answer_sheet_regions_template_region` FOREIGN KEY (`omr_template_region_id`) REFERENCES `omr_template_regions` (`omr_template_region_id`),
  CONSTRAINT `fk_answer_sheet_regions_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`question_id`),
  CONSTRAINT `fk_answer_sheet_regions_test_part` FOREIGN KEY (`test_part_id`) REFERENCES `test_parts` (`test_part_id`),
  CONSTRAINT `fk_answer_sheet_regions_question_type` FOREIGN KEY (`question_type_id`) REFERENCES `question_types` (`question_type_id`),
  CONSTRAINT `chk_answer_sheet_regions_item_numbers` CHECK (`global_item_number` >= 1 AND `part_item_number` >= 1 AND `region_sequence` >= 1),
  CONSTRAINT `chk_answer_sheet_regions_expected_count` CHECK (`expected_response_count_snapshot` IS NULL OR `expected_response_count_snapshot` >= 1),
  CONSTRAINT `chk_answer_sheet_regions_line_count` CHECK (`response_line_count` IS NULL OR `response_line_count` >= 1),
  CONSTRAINT `chk_answer_sheet_regions_response_shape` CHECK (
      (`region_type` = 'objective_bubbles' AND `response_region_size` = 'none' AND `expected_response_count_snapshot` IS NULL AND `response_line_count` IS NULL)
      OR (`region_type` = 'written_response' AND `response_region_size` <> 'none' AND (`expected_response_count_snapshot` IS NULL OR `response_line_count` IS NULL OR `response_line_count` >= `expected_response_count_snapshot`))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- =====================================================================
-- Scanning, detection, and results
-- =====================================================================

-- scan_sessions: V3_000 baseline + V3_002 + V3_006_DRAFT (answer_sheet_version_id,
-- expected/captured page counts; omr_template_id/template_version now nullable)
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
  PRIMARY KEY (`scan_session_id`),
  UNIQUE KEY `uk_scan_sessions_uuid` (`scan_uuid`),
  KEY `fk_scan_sessions_verified_by_user` (`verified_by_user_id`),
  KEY `fk_scan_sessions_omr_template` (`omr_template_id`),
  KEY `fk_scan_sessions_class_list` (`class_list_id`),
  KEY `idx_scan_sessions_user_status` (`scanned_by_user_id`,`scan_status`,`scanned_at`),
  KEY `idx_scan_sessions_assignment_student` (`test_assignment_id`,`class_list_id`,`scan_status`,`scanned_at`),
  KEY `idx_scan_sessions_rescan_lineage` (`supersedes_scan_session_id`),
  KEY `idx_scan_sessions_sheet_version` (`answer_sheet_version_id`,`class_list_id`,`scan_status`),
  CONSTRAINT `fk_scan_sessions_scanned_by_user` FOREIGN KEY (`scanned_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_scan_sessions_verified_by_user` FOREIGN KEY (`verified_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_scan_sessions_omr_template` FOREIGN KEY (`omr_template_id`) REFERENCES `omr_templates` (`omr_template_id`),
  CONSTRAINT `fk_scan_sessions_test_assignment` FOREIGN KEY (`test_assignment_id`) REFERENCES `test_assignments` (`test_assignment_id`),
  CONSTRAINT `fk_scan_sessions_class_list` FOREIGN KEY (`class_list_id`) REFERENCES `class_lists` (`class_list_id`),
  CONSTRAINT `fk_scan_sessions_supersedes_scan_session` FOREIGN KEY (`supersedes_scan_session_id`) REFERENCES `scan_sessions` (`scan_session_id`),
  CONSTRAINT `fk_scan_sessions_answer_sheet_version` FOREIGN KEY (`answer_sheet_version_id`) REFERENCES `answer_sheet_versions` (`answer_sheet_version_id`),
  CONSTRAINT `chk_scan_sessions_capture_model` CHECK (`answer_sheet_version_id` IS NOT NULL OR `omr_template_id` IS NOT NULL),
  CONSTRAINT `chk_scan_sessions_page_counts` CHECK (`expected_page_count` >= 1 AND `captured_page_count` <= `expected_page_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- scan_pages: new in V3_006_DRAFT
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
  PRIMARY KEY (`scan_page_id`),
  UNIQUE KEY `uk_scan_pages_uuid` (`scan_page_uuid`),
  UNIQUE KEY `uk_scan_pages_capture` (`scan_session_id`,`page_number`,`capture_number`),
  UNIQUE KEY `uk_scan_pages_current` (`scan_session_id`,`current_page_number`),
  UNIQUE KEY `uk_scan_pages_supersedes` (`supersedes_scan_page_id`),
  KEY `fk_scan_pages_answer_sheet_page` (`answer_sheet_page_id`),
  KEY `fk_scan_pages_template` (`omr_template_id`),
  KEY `idx_scan_pages_session_status` (`scan_session_id`,`page_status`,`page_number`),
  KEY `idx_scan_pages_rescan_lineage` (`supersedes_scan_page_id`),
  CONSTRAINT `fk_scan_pages_session` FOREIGN KEY (`scan_session_id`) REFERENCES `scan_sessions` (`scan_session_id`),
  CONSTRAINT `fk_scan_pages_answer_sheet_page` FOREIGN KEY (`answer_sheet_page_id`) REFERENCES `answer_sheet_pages` (`answer_sheet_page_id`),
  CONSTRAINT `fk_scan_pages_template` FOREIGN KEY (`omr_template_id`) REFERENCES `omr_templates` (`omr_template_id`),
  CONSTRAINT `fk_scan_pages_supersedes` FOREIGN KEY (`supersedes_scan_page_id`) REFERENCES `scan_pages` (`scan_page_id`),
  CONSTRAINT `chk_scan_pages_numbers` CHECK (`page_number` >= 1 AND `capture_number` >= 1),
  CONSTRAINT `chk_scan_pages_rotation` CHECK (`captured_rotation_degrees` IN (0,90,180,270)),
  CONSTRAINT `chk_scan_pages_qr_payload` CHECK (OCTET_LENGTH(`qr_payload`) BETWEEN 2 AND 256),
  CONSTRAINT `chk_scan_pages_qr_hash` CHECK (CAST(`qr_payload_hash` AS CHAR CHARACTER SET binary) REGEXP '^[0-9a-f]{64}$' AND `qr_payload_hash` = SHA2(`qr_payload`,256))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- omr_detections: V3_000 baseline + V3_002 + V3_006_DRAFT (scan_page_id, answer_sheet_region_id, legacy virtual columns)
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
  `detected_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`omr_detection_id`),
  UNIQUE KEY `uk_omr_detections_uuid` (`detection_uuid`),
  UNIQUE KEY `uk_omr_detections_page_region` (`scan_page_id`,`answer_sheet_region_id`),
  UNIQUE KEY `uk_omr_detections_legacy_scan_question` (`legacy_scan_session_id`,`legacy_question_id`),
  KEY `fk_omr_detections_question` (`question_id`),
  KEY `fk_omr_detections_answer_sheet_region` (`answer_sheet_region_id`),
  KEY `idx_omr_detections_session_question` (`scan_session_id`,`question_id`),
  CONSTRAINT `fk_omr_detections_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`question_id`),
  CONSTRAINT `fk_omr_detections_scan_session` FOREIGN KEY (`scan_session_id`) REFERENCES `scan_sessions` (`scan_session_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_omr_detections_scan_page` FOREIGN KEY (`scan_page_id`) REFERENCES `scan_pages` (`scan_page_id`),
  CONSTRAINT `fk_omr_detections_answer_sheet_region` FOREIGN KEY (`answer_sheet_region_id`) REFERENCES `answer_sheet_regions` (`answer_sheet_region_id`),
  CONSTRAINT `chk_omr_detections_option` CHECK (`detected_option` IS NULL OR `detected_option` IN ('A','B','C','D')),
  CONSTRAINT `chk_omr_detections_confidence` CHECK (`confidence_score` >= 0 AND `confidence_score` <= 1),
  CONSTRAINT `chk_omr_detections_page_region` CHECK ((`scan_page_id` IS NULL AND `answer_sheet_region_id` IS NULL) OR (`scan_page_id` IS NOT NULL AND `answer_sheet_region_id` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- test_results: V3_000 baseline, heavily reworked in V3_002 (test_assignment_id
-- replaces test_id; result-lifecycle timestamps, performance/score-version columns)
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
  `checked_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`test_result_id`),
  UNIQUE KEY `uk_test_results_uuid` (`result_uuid`),
  UNIQUE KEY `uk_test_results_attempt` (`test_assignment_id`,`class_list_id`,`attempt_number`),
  KEY `fk_test_results_class_list` (`class_list_id`),
  KEY `fk_test_results_performance_rule_set` (`performance_rule_set_id`),
  KEY `idx_test_results_assignment_status` (`test_assignment_id`,`result_status`,`finalized_at`),
  CONSTRAINT `fk_test_results_class_list` FOREIGN KEY (`class_list_id`) REFERENCES `class_lists` (`class_list_id`),
  CONSTRAINT `fk_test_results_test_assignment` FOREIGN KEY (`test_assignment_id`) REFERENCES `test_assignments` (`test_assignment_id`),
  CONSTRAINT `fk_test_results_performance_rule_set` FOREIGN KEY (`performance_rule_set_id`) REFERENCES `performance_rule_sets` (`performance_rule_set_id`),
  CONSTRAINT `chk_test_results_scores` CHECK (`total_score` >= 0 AND `max_score` >= 0 AND `total_score` <= `max_score`),
  CONSTRAINT `chk_test_results_percentage` CHECK (`percentage_snapshot` IS NULL OR `percentage_snapshot` BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

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
  PRIMARY KEY (`test_result_scan_id`),
  UNIQUE KEY `uk_test_result_scans_scan` (`scan_session_id`),
  UNIQUE KEY `uk_test_result_scans_result_scan` (`test_result_id`,`scan_session_id`),
  UNIQUE KEY `uk_test_result_scans_one_selected` (`selected_result_id`),
  KEY `fk_test_result_scans_decided_by_user` (`decided_by_user_id`),
  KEY `idx_test_result_scans_result_status` (`test_result_id`,`link_status`),
  CONSTRAINT `fk_test_result_scans_decided_by_user` FOREIGN KEY (`decided_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_test_result_scans_scan_session` FOREIGN KEY (`scan_session_id`) REFERENCES `scan_sessions` (`scan_session_id`),
  CONSTRAINT `fk_test_result_scans_test_result` FOREIGN KEY (`test_result_id`) REFERENCES `test_results` (`test_result_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- student_answers: V3_000 baseline, heavily reworked in V3_002 (selected_question_option_id,
-- response_text, evaluation_status, feedback/finalize/reopen/score_version columns)
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
  PRIMARY KEY (`student_answer_id`),
  UNIQUE KEY `uk_student_answers_uuid` (`answer_uuid`),
  UNIQUE KEY `uk_student_answers_result_question` (`test_result_id`,`question_id`),
  KEY `fk_student_answers_question` (`question_id`),
  KEY `fk_student_answers_verified_by_user` (`verified_by_user_id`),
  KEY `fk_student_answers_selected_question_option` (`selected_question_option_id`),
  CONSTRAINT `fk_student_answers_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`question_id`),
  CONSTRAINT `fk_student_answers_test_result` FOREIGN KEY (`test_result_id`) REFERENCES `test_results` (`test_result_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_student_answers_verified_by_user` FOREIGN KEY (`verified_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_student_answers_selected_question_option` FOREIGN KEY (`selected_question_option_id`) REFERENCES `question_options` (`question_option_id`),
  CONSTRAINT `chk_student_answers_points` CHECK (`points_earned` >= 0),
  CONSTRAINT `chk_student_answers_response` CHECK (
      `selected_question_option_id` IS NOT NULL
      OR `response_text` IS NOT NULL
      OR `answer_status` IN ('blank','multiple','uncertain','invalid','pending_manual')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- answer_attachments: V3_002 baseline + V3_006_DRAFT (scan_page_id, answer_sheet_region_id; widened attachment_type)
-- CORRECTED 2026-09-16: added 13 missing retention/purge columns and widened
-- attachment_type (original_page, normalized_page) - all from
-- V3_009_..._contract_hardening_DRAFT.sql, missed by the original migration
-- audit. Verified verbatim against the pre_V3_014 backup dump.
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
  `crop_coordinates` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`crop_coordinates`)),
  `captured_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`answer_attachment_id`),
  UNIQUE KEY `uk_answer_attachments_uuid` (`attachment_uuid`),
  KEY `idx_answer_attachments_scan_type` (`scan_session_id`,`attachment_type`),
  KEY `idx_answer_attachments_answer_type` (`student_answer_id`,`attachment_type`),
  KEY `idx_answer_attachments_page_type` (`scan_page_id`,`attachment_type`),
  KEY `idx_answer_attachments_region_type` (`answer_sheet_region_id`,`attachment_type`),
  KEY `fk_answer_attachments_hold_set_by_user` (`retention_hold_set_by_user_id`),
  KEY `fk_answer_attachments_purged_by_user` (`purged_by_user_id`),
  KEY `idx_answer_attachments_source` (`source_answer_attachment_id`),
  KEY `idx_answer_attachments_retention` (`purge_status`,`retention_hold`,`retention_until`),
  CONSTRAINT `fk_answer_attachments_student_answer` FOREIGN KEY (`student_answer_id`) REFERENCES `student_answers` (`student_answer_id`),
  CONSTRAINT `fk_answer_attachments_scan_session` FOREIGN KEY (`scan_session_id`) REFERENCES `scan_sessions` (`scan_session_id`),
  CONSTRAINT `fk_answer_attachments_scan_page` FOREIGN KEY (`scan_page_id`) REFERENCES `scan_pages` (`scan_page_id`),
  CONSTRAINT `fk_answer_attachments_answer_sheet_region` FOREIGN KEY (`answer_sheet_region_id`) REFERENCES `answer_sheet_regions` (`answer_sheet_region_id`),
  CONSTRAINT `fk_answer_attachments_source` FOREIGN KEY (`source_answer_attachment_id`) REFERENCES `answer_attachments` (`answer_attachment_id`),
  CONSTRAINT `fk_answer_attachments_hold_set_by_user` FOREIGN KEY (`retention_hold_set_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_answer_attachments_purged_by_user` FOREIGN KEY (`purged_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_answer_attachments_owner` CHECK (`student_answer_id` IS NOT NULL OR `scan_session_id` IS NOT NULL OR `scan_page_id` IS NOT NULL),
  CONSTRAINT `chk_answer_attachments_region_page` CHECK (`answer_sheet_region_id` IS NULL OR `scan_page_id` IS NOT NULL),
  CONSTRAINT `chk_answer_attachments_file_size` CHECK (`file_size_bytes` > 0),
  CONSTRAINT `chk_answer_attachments_normalized_source` CHECK (
      (`attachment_type` = 'normalized_page' AND `source_answer_attachment_id` IS NOT NULL)
      OR (`attachment_type` <> 'normalized_page' AND `source_answer_attachment_id` IS NULL)
  ),
  CONSTRAINT `chk_answer_attachments_hold` CHECK (
      (`retention_hold` = 0 AND `retention_hold_reason` IS NULL AND `retention_hold_set_by_user_id` IS NULL AND `retention_hold_set_at` IS NULL)
      OR (`retention_hold` = 1 AND `retention_hold_reason` IS NOT NULL AND `retention_hold_set_by_user_id` IS NOT NULL AND `retention_hold_set_at` IS NOT NULL)
  ),
  CONSTRAINT `chk_answer_attachments_purge_state` CHECK (
      (`purge_status` = 'retained' AND `purged_at` IS NULL AND `purged_by_user_id` IS NULL AND `purge_reason` IS NULL AND `last_purge_error` IS NULL)
      OR (`purge_status` = 'purged' AND `last_purge_attempt_at` IS NOT NULL AND `purged_at` IS NOT NULL AND `purge_reason` IS NOT NULL AND `last_purge_error` IS NULL)
      OR (`purge_status` = 'purge_failed' AND `last_purge_attempt_at` IS NOT NULL AND `purged_at` IS NULL AND `purged_by_user_id` IS NULL AND `purge_reason` IS NOT NULL AND `last_purge_error` IS NOT NULL)
  ),
  CONSTRAINT `chk_answer_attachments_held_not_purged` CHECK (`retention_hold` = 0 OR `purge_status` <> 'purged')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- scan_verifications: V3_002 baseline + V3_006_DRAFT (scan_page_id)
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
  PRIMARY KEY (`scan_verification_id`),
  UNIQUE KEY `uk_scan_verifications_uuid` (`verification_uuid`),
  KEY `fk_scan_verifications_verified_by_user` (`verified_by_user_id`),
  KEY `idx_scan_verifications_session_time` (`scan_session_id`,`decided_at`),
  KEY `idx_scan_verifications_page_time` (`scan_page_id`,`decided_at`),
  CONSTRAINT `fk_scan_verifications_scan_session` FOREIGN KEY (`scan_session_id`) REFERENCES `scan_sessions` (`scan_session_id`),
  CONSTRAINT `fk_scan_verifications_scan_page` FOREIGN KEY (`scan_page_id`) REFERENCES `scan_pages` (`scan_page_id`),
  CONSTRAINT `fk_scan_verifications_verified_by_user` FOREIGN KEY (`verified_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_scan_verifications_reason` CHECK (`verification_action` = 'accepted' OR `reason_code` IS NOT NULL)
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
  PRIMARY KEY (`answer_verification_id`),
  UNIQUE KEY `uk_answer_verifications_uuid` (`verification_uuid`),
  KEY `fk_answer_verifications_verified_by_user` (`verified_by_user_id`),
  KEY `fk_answer_verifications_evidence_attachment` (`evidence_attachment_id`),
  KEY `idx_answer_verifications_answer_time` (`student_answer_id`,`verified_at`),
  CONSTRAINT `fk_answer_verifications_student_answer` FOREIGN KEY (`student_answer_id`) REFERENCES `student_answers` (`student_answer_id`),
  CONSTRAINT `fk_answer_verifications_verified_by_user` FOREIGN KEY (`verified_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_answer_verifications_evidence_attachment` FOREIGN KEY (`evidence_attachment_id`) REFERENCES `answer_attachments` (`answer_attachment_id`),
  CONSTRAINT `chk_answer_verifications_points` CHECK (`new_points` >= 0),
  CONSTRAINT `chk_answer_verifications_reason` CHECK (`verification_action` NOT IN ('ocr_corrected','reopened') OR `reason_code` IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- answer_rubric_scores: V3_002 baseline + V3_006_DRAFT (maximum_points_snapshot)
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
  PRIMARY KEY (`answer_rubric_score_id`),
  UNIQUE KEY `uk_answer_rubric_scores_version` (`student_answer_id`,`rubric_criterion_id`,`score_version`),
  KEY `fk_answer_rubric_scores_criterion` (`rubric_criterion_id`),
  KEY `fk_answer_rubric_scores_verification` (`answer_verification_id`),
  KEY `fk_answer_rubric_scores_scored_by_user` (`scored_by_user_id`),
  KEY `idx_answer_rubric_scores_answer_version` (`student_answer_id`,`score_version`),
  CONSTRAINT `fk_answer_rubric_scores_student_answer` FOREIGN KEY (`student_answer_id`) REFERENCES `student_answers` (`student_answer_id`),
  CONSTRAINT `fk_answer_rubric_scores_criterion` FOREIGN KEY (`rubric_criterion_id`) REFERENCES `rubric_criteria` (`rubric_criterion_id`),
  CONSTRAINT `fk_answer_rubric_scores_verification` FOREIGN KEY (`answer_verification_id`) REFERENCES `answer_verifications` (`answer_verification_id`),
  CONSTRAINT `fk_answer_rubric_scores_scored_by_user` FOREIGN KEY (`scored_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_answer_rubric_scores_points` CHECK (`points_awarded` >= 0),
  CONSTRAINT `chk_answer_rubric_scores_maximum` CHECK (`maximum_points_snapshot` IS NULL OR (`maximum_points_snapshot` > 0 AND `points_awarded` <= `maximum_points_snapshot`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- syncs: V3_000 baseline + V3_003 (test_assignment_id replaces test_id, retry/idempotency columns)
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
  PRIMARY KEY (`sync_id`),
  UNIQUE KEY `uk_syncs_uuid` (`sync_uuid`),
  KEY `idx_syncs_user_started` (`user_id`,`started_at`),
  KEY `idx_syncs_status_started` (`sync_status`,`started_at`),
  KEY `idx_syncs_assignment_started` (`test_assignment_id`,`started_at`),
  CONSTRAINT `fk_syncs_test_assignment` FOREIGN KEY (`test_assignment_id`) REFERENCES `test_assignments` (`test_assignment_id`),
  CONSTRAINT `fk_syncs_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_syncs_request_item_count` CHECK (`request_item_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- sync_items: V3_000 baseline + V3_003 (attempt_count, last_attempt_at, processed_at; sync_action narrowed to upsert)
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
  PRIMARY KEY (`sync_item_id`),
  UNIQUE KEY `uk_sync_items_batch_result_uuid` (`sync_id`,`result_uuid`),
  KEY `fk_sync_items_test_result` (`test_result_id`),
  KEY `idx_sync_items_result_uuid` (`result_uuid`),
  KEY `idx_sync_items_status` (`sync_id`,`sync_status`),
  CONSTRAINT `fk_sync_items_sync` FOREIGN KEY (`sync_id`) REFERENCES `syncs` (`sync_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_sync_items_test_result` FOREIGN KEY (`test_result_id`) REFERENCES `test_results` (`test_result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

SET FOREIGN_KEY_CHECKS = 1;
SET UNIQUE_CHECKS = 1;
