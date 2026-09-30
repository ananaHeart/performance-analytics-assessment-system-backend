-- SMART Assessment System - canonical V3 central database schema.
-- Generated from a clean, locally validated MariaDB 10.4 V3 migration run.
-- Safety: this file creates and replaces tables only in performance_assessment_v3_db.
-- It does not alter performance_assessment_v2_db, V1, TiDB, frontend, or mobile SQLite.

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `performance_assessment_v3_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */;

USE `performance_assessment_v3_db`;
DROP TABLE IF EXISTS `academic_years`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `academic_years` (
  `academic_year_id` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique academic-year identifier.',
  `curriculum_id` int(10) unsigned NOT NULL COMMENT 'Curriculum used during the academic year.',
  `year_name` varchar(15) NOT NULL COMMENT 'Display value such as 2026-2027.',
  `start_date` date NOT NULL COMMENT 'Official opening date.',
  `end_date` date NOT NULL COMMENT 'Official closing date.',
  `status` enum('planned','active','completed') NOT NULL DEFAULT 'planned' COMMENT 'Academic-year lifecycle status.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Record creation timestamp.',
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Last update timestamp.',
  PRIMARY KEY (`academic_year_id`),
  UNIQUE KEY `uk_academic_years_name` (`year_name`),
  KEY `fk_academic_years_curriculum` (`curriculum_id`),
  CONSTRAINT `fk_academic_years_curriculum` FOREIGN KEY (`curriculum_id`) REFERENCES `curriculums` (`curriculum_id`),
  CONSTRAINT `chk_academic_years_dates` CHECK (`end_date` >= `start_date`)
) ENGINE=InnoDB AUTO_INCREMENT=910004 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='School academic years and their curriculum version.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `accepted_answers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Accepted text answers and matching rules for identification and enumeration.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `addresses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `addresses` (
  `address_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique address identifier.',
  `country_code` char(2) NOT NULL DEFAULT 'PH' COMMENT 'ISO country code.',
  `region_code` varchar(20) DEFAULT NULL COMMENT 'Region code returned by the address API.',
  `region_name` varchar(100) DEFAULT NULL COMMENT 'Region display name.',
  `province_code` varchar(20) DEFAULT NULL COMMENT 'Province code returned by the address API.',
  `province_name` varchar(100) DEFAULT NULL COMMENT 'Province display name.',
  `city_municipality_code` varchar(20) DEFAULT NULL COMMENT 'City or municipality code returned by the address API.',
  `city_municipality_name` varchar(120) DEFAULT NULL COMMENT 'City or municipality display name.',
  `barangay_code` varchar(20) DEFAULT NULL COMMENT 'Barangay code returned by the address API.',
  `barangay_name` varchar(120) DEFAULT NULL COMMENT 'Barangay display name.',
  `address_line` varchar(255) DEFAULT NULL COMMENT 'House, building, subdivision, or street information.',
  `postal_code` varchar(10) DEFAULT NULL COMMENT 'Postal or ZIP code.',
  `address_source` enum('api','manual','sf1_import') NOT NULL DEFAULT 'manual' COMMENT 'Source used to capture the address.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Record creation timestamp.',
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Last update timestamp.',
  PRIMARY KEY (`address_id`),
  KEY `idx_addresses_location` (`region_code`,`province_code`,`city_municipality_code`,`barangay_code`)
) ENGINE=InnoDB AUTO_INCREMENT=2000055 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Normalized address records for schools, users, and students.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `answer_attachments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `answer_attachments` (
  `answer_attachment_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `attachment_uuid` char(36) NOT NULL,
  `student_answer_id` bigint(20) unsigned DEFAULT NULL,
  `scan_session_id` bigint(20) unsigned DEFAULT NULL,
  `attachment_type` enum('full_sheet','answer_crop','teacher_evidence') NOT NULL,
  `storage_provider` varchar(40) NOT NULL,
  `storage_key` varchar(500) NOT NULL,
  `mime_type` varchar(100) NOT NULL,
  `file_size_bytes` bigint(20) unsigned NOT NULL,
  `content_hash` char(64) NOT NULL,
  `crop_coordinates` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`crop_coordinates`)),
  `captured_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`answer_attachment_id`),
  UNIQUE KEY `uk_answer_attachments_uuid` (`attachment_uuid`),
  KEY `idx_answer_attachments_scan_type` (`scan_session_id`,`attachment_type`),
  KEY `idx_answer_attachments_answer_type` (`student_answer_id`,`attachment_type`),
  CONSTRAINT `fk_answer_attachments_scan_session` FOREIGN KEY (`scan_session_id`) REFERENCES `scan_sessions` (`scan_session_id`),
  CONSTRAINT `fk_answer_attachments_student_answer` FOREIGN KEY (`student_answer_id`) REFERENCES `student_answers` (`student_answer_id`),
  CONSTRAINT `chk_answer_attachments_owner` CHECK (`student_answer_id` is not null or `scan_session_id` is not null),
  CONSTRAINT `chk_answer_attachments_file_size` CHECK (`file_size_bytes` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Private full-sheet and item-crop evidence for scan and non-objective response review.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `answer_keys`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `answer_keys` (
  `answer_key_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique answer-key identifier.',
  `question_id` bigint(20) unsigned NOT NULL COMMENT 'Question answered by this key.',
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
  CONSTRAINT `fk_answer_keys_correct_question_option` FOREIGN KEY (`correct_question_option_id`) REFERENCES `question_options` (`question_option_id`),
  CONSTRAINT `fk_answer_keys_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`question_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_answer_keys_rubric` FOREIGN KEY (`rubric_id`) REFERENCES `rubrics` (`rubric_id`),
  CONSTRAINT `chk_answer_keys_strategy` CHECK (`answer_key_type` = 'option' and `correct_question_option_id` is not null and `rubric_id` is null or `answer_key_type` = 'accepted_text' and `correct_question_option_id` is null and `rubric_id` is null or `answer_key_type` = 'rubric' and `correct_question_option_id` is null and `rubric_id` is not null or `answer_key_type` = 'manual' and `correct_question_option_id` is null)
) ENGINE=InnoDB AUTO_INCREMENT=700076 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='One correct answer for each assessment question.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `answer_rubric_scores`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `answer_rubric_scores` (
  `answer_rubric_score_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `student_answer_id` bigint(20) unsigned NOT NULL,
  `rubric_criterion_id` bigint(20) unsigned NOT NULL,
  `answer_verification_id` bigint(20) unsigned DEFAULT NULL,
  `scored_by_user_id` bigint(20) unsigned NOT NULL,
  `score_version` int(10) unsigned NOT NULL DEFAULT 1,
  `points_awarded` decimal(8,2) NOT NULL,
  `criterion_feedback` text DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`answer_rubric_score_id`),
  UNIQUE KEY `uk_answer_rubric_scores_version` (`student_answer_id`,`rubric_criterion_id`,`score_version`),
  KEY `fk_answer_rubric_scores_criterion` (`rubric_criterion_id`),
  KEY `fk_answer_rubric_scores_verification` (`answer_verification_id`),
  KEY `fk_answer_rubric_scores_scored_by_user` (`scored_by_user_id`),
  KEY `idx_answer_rubric_scores_answer_version` (`student_answer_id`,`score_version`),
  CONSTRAINT `fk_answer_rubric_scores_criterion` FOREIGN KEY (`rubric_criterion_id`) REFERENCES `rubric_criteria` (`rubric_criterion_id`),
  CONSTRAINT `fk_answer_rubric_scores_scored_by_user` FOREIGN KEY (`scored_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_answer_rubric_scores_student_answer` FOREIGN KEY (`student_answer_id`) REFERENCES `student_answers` (`student_answer_id`),
  CONSTRAINT `fk_answer_rubric_scores_verification` FOREIGN KEY (`answer_verification_id`) REFERENCES `answer_verifications` (`answer_verification_id`),
  CONSTRAINT `chk_answer_rubric_scores_points` CHECK (`points_awarded` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Criterion-level, versioned rubric scores for essay and other manually scored responses.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `answer_verifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
  CONSTRAINT `fk_answer_verifications_evidence_attachment` FOREIGN KEY (`evidence_attachment_id`) REFERENCES `answer_attachments` (`answer_attachment_id`),
  CONSTRAINT `fk_answer_verifications_student_answer` FOREIGN KEY (`student_answer_id`) REFERENCES `student_answers` (`student_answer_id`),
  CONSTRAINT `fk_answer_verifications_verified_by_user` FOREIGN KEY (`verified_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_answer_verifications_points` CHECK (`new_points` >= 0),
  CONSTRAINT `chk_answer_verifications_reason` CHECK (`verification_action` not in ('ocr_corrected','reopened') or `reason_code` is not null)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Append-only OCR, transcription, manual-score, and correction history for non-objective answers.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `audit_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `audit_logs` (
  `audit_log_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Central audit-event identifier.',
  `audit_uuid` char(36) NOT NULL COMMENT 'Public unique audit-event identifier.',
  `user_id` bigint(20) unsigned DEFAULT NULL COMMENT 'User who performed the action; null for anonymous or system events.',
  `action` varchar(100) NOT NULL COMMENT 'Action such as LOGIN, IMPORT_SF1, or VERIFY_OMR.',
  `entity_type` varchar(100) DEFAULT NULL COMMENT 'Affected entity or table name.',
  `entity_id` varchar(100) DEFAULT NULL COMMENT 'Affected numeric identifier or UUID.',
  `outcome` enum('success','failed','denied') NOT NULL COMMENT 'Result of the attempted action.',
  `ip_address` varchar(45) DEFAULT NULL COMMENT 'IPv4 or IPv6 source address.',
  `device_identifier` varchar(100) DEFAULT NULL COMMENT 'Browser or mobile-device identifier.',
  `user_agent` text DEFAULT NULL COMMENT 'Browser, operating-system, or application information.',
  `details` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'Structured audit details without passwords or raw tokens.' CHECK (json_valid(`details`)),
  `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Audit-event timestamp.',
  PRIMARY KEY (`audit_log_id`),
  UNIQUE KEY `uk_audit_logs_uuid` (`audit_uuid`),
  KEY `idx_audit_logs_user_time` (`user_id`,`created_at`),
  KEY `idx_audit_logs_entity` (`entity_type`,`entity_id`),
  KEY `idx_audit_logs_action_time` (`action`,`created_at`),
  KEY `idx_audit_logs_outcome_time` (`outcome`,`created_at`),
  CONSTRAINT `fk_audit_logs_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=1303 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Append-only audit trail for security-sensitive and business-critical actions.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `auth_sessions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `auth_sessions` (
  `auth_session_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Central authenticated-session identifier.',
  `session_uuid` char(36) NOT NULL COMMENT 'Public unique session identifier.',
  `user_id` bigint(20) unsigned NOT NULL COMMENT 'User who owns the session.',
  `authentication_level` enum('password','mfa') NOT NULL DEFAULT 'password',
  `user_mfa_factor_id` bigint(20) unsigned DEFAULT NULL,
  `mfa_verified_at` timestamp NULL DEFAULT NULL,
  `refresh_token_hash` char(64) NOT NULL COMMENT 'SHA-256 refresh-token hash; never stores the raw token.',
  `device_identifier` varchar(100) DEFAULT NULL COMMENT 'Browser or mobile-device identifier.',
  `ip_address` varchar(45) DEFAULT NULL COMMENT 'IPv4 or IPv6 address at session creation.',
  `user_agent` text DEFAULT NULL COMMENT 'Browser, operating-system, or application information.',
  `issued_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Session creation timestamp.',
  `expires_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Session expiration timestamp.',
  `last_used_at` timestamp NULL DEFAULT NULL COMMENT 'Most recent refresh-token use timestamp.',
  `revoked_at` timestamp NULL DEFAULT NULL COMMENT 'Logout or security-revocation timestamp.',
  PRIMARY KEY (`auth_session_id`),
  UNIQUE KEY `uk_auth_sessions_uuid` (`session_uuid`),
  UNIQUE KEY `uk_auth_sessions_refresh_token_hash` (`refresh_token_hash`),
  KEY `idx_auth_sessions_user_expiry` (`user_id`,`expires_at`),
  KEY `idx_auth_sessions_user_revoked` (`user_id`,`revoked_at`),
  KEY `fk_auth_sessions_mfa_factor` (`user_mfa_factor_id`),
  CONSTRAINT `fk_auth_sessions_mfa_factor` FOREIGN KEY (`user_mfa_factor_id`) REFERENCES `user_mfa_factors` (`user_mfa_factor_id`),
  CONSTRAINT `fk_auth_sessions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_auth_sessions_mfa_state` CHECK (`authentication_level` = 'password' or `user_mfa_factor_id` is not null and `mfa_verified_at` is not null)
) ENGINE=InnoDB AUTO_INCREMENT=1162 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Secure login sessions and refresh-token lifecycle records.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `class_assignments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `class_assignments` (
  `class_assignment_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique teacher-subject assignment identifier.',
  `class_id` bigint(20) unsigned NOT NULL COMMENT 'Assigned section-and-year cohort.',
  `user_id` bigint(20) unsigned NOT NULL COMMENT 'Assigned teacher.',
  `subject_id` int(10) unsigned NOT NULL COMMENT 'Assigned subject.',
  `assignment_role` enum('primary','co_teacher') NOT NULL DEFAULT 'primary' COMMENT 'Teacher role in the class and subject.',
  `assigned_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Assignment timestamp.',
  `ended_at` timestamp NULL DEFAULT NULL,
  `status` enum('active','completed','archived') NOT NULL DEFAULT 'active' COMMENT 'Assignment lifecycle status.',
  `status_changed_by_user_id` bigint(20) unsigned DEFAULT NULL,
  `status_reason` varchar(255) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Record creation timestamp.',
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Last update timestamp.',
  PRIMARY KEY (`class_assignment_id`),
  UNIQUE KEY `uk_class_assignments_teacher_subject` (`class_id`,`user_id`,`subject_id`),
  KEY `fk_class_assignments_user` (`user_id`),
  KEY `fk_class_assignments_subject` (`subject_id`),
  KEY `idx_class_assignments_class_subject` (`class_id`,`subject_id`,`status`),
  KEY `fk_class_assignments_status_changed_by_user` (`status_changed_by_user_id`),
  CONSTRAINT `fk_class_assignments_class` FOREIGN KEY (`class_id`) REFERENCES `classes` (`class_id`),
  CONSTRAINT `fk_class_assignments_status_changed_by_user` FOREIGN KEY (`status_changed_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_class_assignments_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`subject_id`),
  CONSTRAINT `fk_class_assignments_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=910015 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Teacher and subject assignments for a class cohort.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `class_lists`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `class_lists` (
  `class_list_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique learner-membership identifier.',
  `membership_uuid` char(36) NOT NULL,
  `class_id` bigint(20) unsigned NOT NULL COMMENT 'Class cohort containing the learner.',
  `student_id` bigint(20) unsigned NOT NULL COMMENT 'Learner in the class cohort.',
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
  CONSTRAINT `fk_class_lists_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`academic_year_id`),
  CONSTRAINT `fk_class_lists_class` FOREIGN KEY (`class_id`) REFERENCES `classes` (`class_id`),
  CONSTRAINT `fk_class_lists_previous_membership` FOREIGN KEY (`previous_class_list_id`) REFERENCES `class_lists` (`class_list_id`),
  CONSTRAINT `fk_class_lists_source_sf1_import` FOREIGN KEY (`source_sf1_import_id`) REFERENCES `sf1_imports` (`sf1_import_id`),
  CONSTRAINT `fk_class_lists_status_changed_by_user` FOREIGN KEY (`status_changed_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_class_lists_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`student_id`),
  CONSTRAINT `chk_class_lists_end_state` CHECK (`enrollment_status` = 'enrolled' and `ended_at` is null or `enrollment_status` <> 'enrolled' and `ended_at` is not null)
) ENGINE=InnoDB AUTO_INCREMENT=1029 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Normalized learner membership for each class cohort.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `classes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `classes` (
  `class_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique section-and-academic-year cohort identifier.',
  `academic_year_id` int(10) unsigned NOT NULL COMMENT 'Academic year of the cohort.',
  `section_id` int(10) unsigned NOT NULL COMMENT 'Section represented by the cohort.',
  `status` enum('active','completed','archived') NOT NULL DEFAULT 'active' COMMENT 'Class lifecycle status.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Record creation timestamp.',
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Last update timestamp.',
  PRIMARY KEY (`class_id`),
  UNIQUE KEY `uk_classes_year_section` (`academic_year_id`,`section_id`),
  KEY `fk_classes_section` (`section_id`),
  CONSTRAINT `fk_classes_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`academic_year_id`),
  CONSTRAINT `fk_classes_section` FOREIGN KEY (`section_id`) REFERENCES `sections` (`section_id`)
) ENGINE=InnoDB AUTO_INCREMENT=910007 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Section cohorts for a specific academic year.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `competency_tags`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `competency_tags` (
  `competency_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique competency identifier.',
  `root_tag_id` int(10) unsigned NOT NULL COMMENT 'Broad root category of the competency.',
  `competency_name` varchar(255) NOT NULL COMMENT 'Official competency statement or label.',
  PRIMARY KEY (`competency_id`),
  UNIQUE KEY `uk_competency_tags_root_name` (`root_tag_id`,`competency_name`),
  CONSTRAINT `fk_competency_tags_root_tag` FOREIGN KEY (`root_tag_id`) REFERENCES `root_tags` (`root_tag_id`)
) ENGINE=InnoDB AUTO_INCREMENT=910143 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Competency statements under curriculum root categories.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `curriculums`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `curriculums` (
  `curriculum_id` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique curriculum identifier.',
  `curriculum_name` varchar(80) NOT NULL COMMENT 'Curriculum name such as MATATAG Curriculum.',
  `version` varchar(30) NOT NULL COMMENT 'Curriculum version or release label.',
  `description` text DEFAULT NULL COMMENT 'Curriculum description.',
  `status` enum('active','inactive','archived') NOT NULL DEFAULT 'active' COMMENT 'Curriculum lifecycle status.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Record creation timestamp.',
  PRIMARY KEY (`curriculum_id`),
  UNIQUE KEY `uk_curriculums_name_version` (`curriculum_name`,`version`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Curriculum versions used by the school.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `educational_attainments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `educational_attainments` (
  `educational_attainment_id` tinyint(3) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique educational-attainment identifier.',
  `attainment_name` varchar(100) NOT NULL COMMENT 'Educational-attainment display name.',
  `attainment_order` tinyint(3) unsigned NOT NULL COMMENT 'Display and ranking order.',
  `description` varchar(255) DEFAULT NULL COMMENT 'Additional information about the attainment.',
  `is_active` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Whether the attainment may be assigned.',
  PRIMARY KEY (`educational_attainment_id`),
  UNIQUE KEY `uk_educational_attainments_name` (`attainment_name`),
  UNIQUE KEY `uk_educational_attainments_order` (`attainment_order`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Normalized highest educational-attainment choices for users.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `genders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `genders` (
  `gender_id` tinyint(3) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique gender identifier.',
  `gender_name` varchar(20) NOT NULL COMMENT 'Gender display value.',
  `description` varchar(255) DEFAULT NULL COMMENT 'Optional explanation of the gender value.',
  PRIMARY KEY (`gender_id`),
  UNIQUE KEY `uk_genders_name` (`gender_name`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Reference values used for user and student gender.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `grade_levels`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `grade_levels` (
  `grade_level_id` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique grade-level identifier.',
  `grade_level_name` varchar(20) NOT NULL COMMENT 'Grade-level display name such as Grade 7.',
  PRIMARY KEY (`grade_level_id`),
  UNIQUE KEY `uk_grade_levels_name` (`grade_level_name`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Supported school grade levels.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `intervention_results`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `intervention_results` (
  `intervention_result_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique generated recommendation identifier.',
  `test_result_id` bigint(20) unsigned NOT NULL COMMENT 'Learner result receiving the recommendation.',
  `intervention_id` bigint(20) unsigned NOT NULL COMMENT 'Recommended intervention.',
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
  CONSTRAINT `fk_intervention_results_acknowledged_by_user` FOREIGN KEY (`acknowledged_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_intervention_results_intervention` FOREIGN KEY (`intervention_id`) REFERENCES `interventions` (`intervention_id`),
  CONSTRAINT `fk_intervention_results_performance_rule_set` FOREIGN KEY (`performance_rule_set_id`) REFERENCES `performance_rule_sets` (`performance_rule_set_id`),
  CONSTRAINT `fk_intervention_results_test_result` FOREIGN KEY (`test_result_id`) REFERENCES `test_results` (`test_result_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_intervention_results_mastery` CHECK (`mastery_rate_snapshot` between 0 and 100)
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Interventions generated from a learner assessment result.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `interventions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `interventions` (
  `intervention_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique intervention identifier.',
  `skill_id` bigint(20) unsigned NOT NULL COMMENT 'Skill addressed by the intervention.',
  `intervention_type` enum('remediation','review','practice','enrichment','other') NOT NULL COMMENT 'Type of instructional support.',
  `description` text NOT NULL COMMENT 'Teacher-facing intervention guidance.',
  PRIMARY KEY (`intervention_id`),
  UNIQUE KEY `uk_interventions_skill_type` (`skill_id`,`intervention_type`),
  CONSTRAINT `fk_interventions_skill` FOREIGN KEY (`skill_id`) REFERENCES `skills` (`skill_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1005 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Master intervention recommendations for specific skills.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `login_attempts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `login_attempts` (
  `login_attempt_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique login-attempt identifier.',
  `user_id` bigint(20) unsigned DEFAULT NULL COMMENT 'Matched account; nullable when the email is unknown.',
  `attempted_email` varchar(120) NOT NULL COMMENT 'Email supplied during the login attempt.',
  `ip_address` varchar(45) DEFAULT NULL COMMENT 'IPv4 or IPv6 source address.',
  `device_identifier` varchar(100) DEFAULT NULL COMMENT 'Browser or mobile-device identifier.',
  `user_agent` text DEFAULT NULL COMMENT 'Browser, operating-system, or application information.',
  `was_successful` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Whether authentication succeeded.',
  `failure_reason` enum('invalid_credentials','pending_email_verification','pending_approval','rejected_account','inactive_account','locked_account','email_not_verified','mfa_required','invalid_mfa_code','mfa_locked','mfa_not_enrolled','rate_limited','other') DEFAULT NULL,
  `attempted_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Login-attempt timestamp.',
  PRIMARY KEY (`login_attempt_id`),
  KEY `idx_login_attempts_user_time` (`user_id`,`attempted_at`),
  KEY `idx_login_attempts_email_time` (`attempted_email`,`attempted_at`),
  KEY `idx_login_attempts_ip_time` (`ip_address`,`attempted_at`),
  CONSTRAINT `fk_login_attempts_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=1173 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Successful and failed login attempts used for security monitoring and lockout.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `majors`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `majors` (
  `major_id` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique major identifier.',
  `major_name` varchar(100) NOT NULL COMMENT 'Teacher major or specialization name.',
  PRIMARY KEY (`major_id`),
  UNIQUE KEY `uk_majors_name` (`major_name`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Teacher major and specialization reference values.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mfa_authentication_challenges`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
  KEY `fk_mfa_authentication_challenges_factor` (`user_mfa_factor_id`),
  KEY `idx_mfa_authentication_challenges_user_status` (`user_id`,`challenge_status`,`expires_at`),
  CONSTRAINT `fk_mfa_authentication_challenges_factor` FOREIGN KEY (`user_mfa_factor_id`) REFERENCES `user_mfa_factors` (`user_mfa_factor_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_mfa_authentication_challenges_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_mfa_authentication_challenges_attempts` CHECK (`maximum_attempt_count` > 0 and `attempt_count` <= `maximum_attempt_count`),
  CONSTRAINT `chk_mfa_authentication_challenges_expiry` CHECK (`expires_at` > `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Short-lived login challenges for password-plus-authenticator authentication.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mfa_recovery_codes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='One-time hashed recovery codes issued during authenticator enrollment.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `notifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `notifications` (
  `notification_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Internal notification identifier.',
  `notification_uuid` char(36) NOT NULL COMMENT 'Public notification identifier.',
  `recipient_user_id` bigint(20) unsigned NOT NULL COMMENT 'User who may view and read this notification.',
  `notification_type` varchar(50) NOT NULL COMMENT 'Machine-readable event type.',
  `title` varchar(120) NOT NULL COMMENT 'Short user-facing notification title.',
  `message` varchar(500) NOT NULL COMMENT 'User-facing notification message.',
  `reference_type` varchar(50) DEFAULT NULL COMMENT 'Related entity type, such as users or syncs.',
  `reference_id` varchar(100) DEFAULT NULL COMMENT 'Related entity identifier.',
  `event_key` varchar(150) NOT NULL COMMENT 'Idempotency key preventing duplicate event notifications per recipient.',
  `read_at` timestamp NULL DEFAULT NULL COMMENT 'Timestamp when the recipient marked the notification as read.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Timestamp when the notification was created.',
  PRIMARY KEY (`notification_id`),
  UNIQUE KEY `uk_notifications_uuid` (`notification_uuid`),
  UNIQUE KEY `uk_notifications_recipient_event` (`recipient_user_id`,`event_key`),
  KEY `idx_notifications_recipient_read_created` (`recipient_user_id`,`read_at`,`created_at`),
  KEY `idx_notifications_reference` (`reference_type`,`reference_id`),
  CONSTRAINT `fk_notifications_recipient` FOREIGN KEY (`recipient_user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Persistent role-scoped notifications and unread state for authenticated users.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `omr_detections`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `omr_detections` (
  `omr_detection_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique raw OMR-detection identifier.',
  `detection_uuid` char(36) NOT NULL,
  `scan_session_id` bigint(20) unsigned NOT NULL COMMENT 'Parent scanned sheet.',
  `question_id` bigint(20) unsigned NOT NULL COMMENT 'Question represented by the bubble position.',
  `detected_option` char(1) DEFAULT NULL COMMENT 'Scanner-detected option; null for blank or ambiguous marks.',
  `confidence_score` decimal(5,4) NOT NULL COMMENT 'Detection confidence from 0.0000 through 1.0000.',
  `detection_status` enum('detected','blank','multiple_marks','uncertain') NOT NULL COMMENT 'Raw scanner interpretation.',
  `raw_mark` text NOT NULL COMMENT 'Structured darkness or confidence measurements for each option.',
  `detected_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Detection timestamp.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`omr_detection_id`),
  UNIQUE KEY `uk_omr_detections_scan_question` (`scan_session_id`,`question_id`),
  UNIQUE KEY `uk_omr_detections_uuid` (`detection_uuid`),
  KEY `fk_omr_detections_question` (`question_id`),
  CONSTRAINT `fk_omr_detections_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`question_id`),
  CONSTRAINT `fk_omr_detections_scan_session` FOREIGN KEY (`scan_session_id`) REFERENCES `scan_sessions` (`scan_session_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_omr_detections_confidence` CHECK (`confidence_score` >= 0 and `confidence_score` <= 1),
  CONSTRAINT `chk_omr_detections_option` CHECK (`detected_option` is null or `detected_option` in ('A','B','C','D'))
) ENGINE=InnoDB AUTO_INCREMENT=1093 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Raw OMR detections retained separately from final teacher-verified answers.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `omr_templates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `omr_templates` (
  `omr_template_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `template_code` varchar(80) NOT NULL,
  `template_name` varchar(120) NOT NULL,
  `question_type_id` smallint(5) unsigned NOT NULL,
  `page_size` varchar(20) NOT NULL DEFAULT 'A4',
  `page_orientation` enum('portrait','landscape') NOT NULL DEFAULT 'portrait',
  `minimum_item_count` smallint(5) unsigned NOT NULL,
  `maximum_item_count` smallint(5) unsigned NOT NULL,
  `option_count` tinyint(3) unsigned NOT NULL,
  `geometry_definition` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`geometry_definition`)),
  `qr_payload_version` smallint(5) unsigned NOT NULL,
  `minimum_scanner_version` varchar(50) NOT NULL,
  `template_status` enum('draft','active','retired') NOT NULL DEFAULT 'draft',
  `created_by_user_id` bigint(20) unsigned DEFAULT NULL,
  `activated_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`omr_template_id`),
  UNIQUE KEY `uk_omr_templates_code` (`template_code`),
  KEY `fk_omr_templates_created_by_user` (`created_by_user_id`),
  KEY `idx_omr_templates_capability` (`question_type_id`,`template_status`,`minimum_item_count`,`maximum_item_count`),
  CONSTRAINT `fk_omr_templates_created_by_user` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_omr_templates_question_type` FOREIGN KEY (`question_type_id`) REFERENCES `question_types` (`question_type_id`),
  CONSTRAINT `chk_omr_templates_item_count` CHECK (`minimum_item_count` >= 1 and `maximum_item_count` >= `minimum_item_count`),
  CONSTRAINT `chk_omr_templates_option_count` CHECK (`option_count` between 2 and 4)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Immutable, versioned paper-template geometry shared by backend printing and mobile scanning.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `part_skill_mappings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
  CONSTRAINT `fk_part_skill_mappings_skill` FOREIGN KEY (`skill_id`) REFERENCES `skills` (`skill_id`),
  CONSTRAINT `fk_part_skill_mappings_test_part` FOREIGN KEY (`test_part_id`) REFERENCES `test_parts` (`test_part_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_part_skill_mappings_range` CHECK (`start_item_number` >= 1 and `end_item_number` >= `start_item_number` and `item_count` = `end_item_number` - `start_item_number` + 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Range-based test-part mappings used for competency mastery analytics.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `performance_rule_sets`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
  CONSTRAINT `fk_performance_rule_sets_approved_by_user` FOREIGN KEY (`approved_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_performance_rule_sets_school` FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`),
  CONSTRAINT `chk_performance_rule_sets_dates` CHECK (`effective_until_at` is null or `effective_from_at` is null or `effective_until_at` > `effective_from_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Versioned and auditable scoring, mastery, and intervention thresholds.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `question_options`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Normalized options for Multiple Choice and True/False questions.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `question_types`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Supported response formats and their capture, scoring, and verification capabilities.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `questions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `questions` (
  `question_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique question identifier.',
  `question_uuid` char(36) NOT NULL,
  `test_part_id` bigint(20) unsigned NOT NULL COMMENT 'Test part containing the question.',
  `question_type_id` smallint(5) unsigned NOT NULL,
  `item_number` int(10) unsigned NOT NULL COMMENT 'Question number within the part.',
  `question_text` text NOT NULL COMMENT 'Actual assessment question.',
  `maximum_points` decimal(8,2) NOT NULL DEFAULT 1.00,
  `rubric_id` bigint(20) unsigned DEFAULT NULL,
  `response_instructions` text DEFAULT NULL,
  `answer_order_required` tinyint(1) NOT NULL DEFAULT 0,
  `maximum_response_length` int(10) unsigned DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`question_id`),
  UNIQUE KEY `uk_questions_part_item` (`test_part_id`,`item_number`),
  UNIQUE KEY `uk_questions_uuid` (`question_uuid`),
  KEY `fk_questions_question_type` (`question_type_id`),
  KEY `fk_questions_rubric` (`rubric_id`),
  CONSTRAINT `fk_questions_question_type` FOREIGN KEY (`question_type_id`) REFERENCES `question_types` (`question_type_id`),
  CONSTRAINT `fk_questions_rubric` FOREIGN KEY (`rubric_id`) REFERENCES `rubrics` (`rubric_id`),
  CONSTRAINT `fk_questions_test_part` FOREIGN KEY (`test_part_id`) REFERENCES `test_parts` (`test_part_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_questions_maximum_points` CHECK (`maximum_points` > 0),
  CONSTRAINT `chk_questions_maximum_response_length` CHECK (`maximum_response_length` is null or `maximum_response_length` > 0)
) ENGINE=InnoDB AUTO_INCREMENT=700076 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Individual multiple-choice questions used by answer keys and OMR.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `roles` (
  `role_id` tinyint(3) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique authorization-role identifier.',
  `role_name` varchar(30) NOT NULL COMMENT 'Role name such as principal or teacher.',
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `uk_roles_name` (`role_name`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='System authorization roles.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `root_tags`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `root_tags` (
  `root_tag_id` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique root-tag identifier.',
  `curriculum_id` int(10) unsigned NOT NULL COMMENT 'Curriculum that defines the broad topic.',
  `root_tag_name` varchar(100) NOT NULL COMMENT 'Broad curriculum topic name.',
  `description` text DEFAULT NULL COMMENT 'Definition or scope of the broad topic.',
  `status` enum('active','inactive') NOT NULL DEFAULT 'active' COMMENT 'Root-tag availability status.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Record creation timestamp.',
  PRIMARY KEY (`root_tag_id`),
  UNIQUE KEY `uk_root_tags_curriculum_name` (`curriculum_id`,`root_tag_name`),
  CONSTRAINT `fk_root_tags_curriculum` FOREIGN KEY (`curriculum_id`) REFERENCES `curriculums` (`curriculum_id`)
) ENGINE=InnoDB AUTO_INCREMENT=910022 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Curriculum-specific root competency categories.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rubric_criteria`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Ordered scoring criteria and point limits within a rubric.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rubrics`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
  CONSTRAINT `fk_rubrics_created_by_user` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_rubrics_school` FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`),
  CONSTRAINT `chk_rubrics_total_points` CHECK (`total_points` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Reusable school-owned rubrics for manual and essay scoring.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `scan_sessions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `scan_sessions` (
  `scan_session_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Central scan-session identifier.',
  `scan_uuid` char(36) NOT NULL COMMENT 'Offline-generated idempotency identifier.',
  `omr_template_id` bigint(20) unsigned NOT NULL,
  `test_assignment_id` bigint(20) unsigned NOT NULL,
  `class_list_id` bigint(20) unsigned NOT NULL,
  `scanned_by_user_id` bigint(20) unsigned NOT NULL COMMENT 'Teacher operating the scanner.',
  `verified_by_user_id` bigint(20) unsigned DEFAULT NULL COMMENT 'Teacher who verified the OMR detections.',
  `device_identifier` varchar(100) DEFAULT NULL COMMENT 'Mobile installation or device identifier.',
  `template_version` varchar(50) NOT NULL COMMENT 'OMR template version used.',
  `scanner_version` varchar(50) NOT NULL COMMENT 'Scanner or OpenCV version used.',
  `image_hash` char(64) DEFAULT NULL COMMENT 'SHA-256 image hash for duplicate detection and audit.',
  `supersedes_scan_session_id` bigint(20) unsigned DEFAULT NULL,
  `scan_status` enum('captured','processing','needs_verification','accepted','rescan_requested','rejected','superseded','failed') NOT NULL DEFAULT 'captured',
  `failure_code` varchar(50) DEFAULT NULL,
  `failure_detail` varchar(500) DEFAULT NULL,
  `scanned_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Image-capture timestamp.',
  `verified_at` timestamp NULL DEFAULT NULL COMMENT 'Teacher-verification completion timestamp.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Record creation timestamp.',
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Last update timestamp.',
  PRIMARY KEY (`scan_session_id`),
  UNIQUE KEY `uk_scan_sessions_uuid` (`scan_uuid`),
  KEY `fk_scan_sessions_verified_by_user` (`verified_by_user_id`),
  KEY `idx_scan_sessions_user_status` (`scanned_by_user_id`,`scan_status`,`scanned_at`),
  KEY `fk_scan_sessions_omr_template` (`omr_template_id`),
  KEY `fk_scan_sessions_class_list` (`class_list_id`),
  KEY `idx_scan_sessions_assignment_student` (`test_assignment_id`,`class_list_id`,`scan_status`,`scanned_at`),
  KEY `idx_scan_sessions_rescan_lineage` (`supersedes_scan_session_id`),
  CONSTRAINT `fk_scan_sessions_class_list` FOREIGN KEY (`class_list_id`) REFERENCES `class_lists` (`class_list_id`),
  CONSTRAINT `fk_scan_sessions_omr_template` FOREIGN KEY (`omr_template_id`) REFERENCES `omr_templates` (`omr_template_id`),
  CONSTRAINT `fk_scan_sessions_scanned_by_user` FOREIGN KEY (`scanned_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_scan_sessions_supersedes_scan_session` FOREIGN KEY (`supersedes_scan_session_id`) REFERENCES `scan_sessions` (`scan_session_id`),
  CONSTRAINT `fk_scan_sessions_test_assignment` FOREIGN KEY (`test_assignment_id`) REFERENCES `test_assignments` (`test_assignment_id`),
  CONSTRAINT `fk_scan_sessions_verified_by_user` FOREIGN KEY (`verified_by_user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1013 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Central audit record for each uploaded OMR scan.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `scan_verifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `scan_verifications` (
  `scan_verification_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `verification_uuid` char(36) NOT NULL,
  `scan_session_id` bigint(20) unsigned NOT NULL,
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
  CONSTRAINT `fk_scan_verifications_scan_session` FOREIGN KEY (`scan_session_id`) REFERENCES `scan_sessions` (`scan_session_id`),
  CONSTRAINT `fk_scan_verifications_verified_by_user` FOREIGN KEY (`verified_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_scan_verifications_reason` CHECK (`verification_action` = 'accepted' or `reason_code` is not null)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Append-only whole-scan decisions; never stores or replaces an objective learner answer.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `school_profiles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `school_profiles` (
  `school_id` varchar(20) NOT NULL COMMENT 'Official DepEd school identifier.',
  `address_id` bigint(20) unsigned NOT NULL COMMENT 'Address of the school.',
  `school_name` varchar(120) NOT NULL COMMENT 'Official school name.',
  `contact_number` varchar(20) DEFAULT NULL COMMENT 'Official school contact number.',
  `email` varchar(120) DEFAULT NULL COMMENT 'Official school email address.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Record creation timestamp.',
  PRIMARY KEY (`school_id`),
  UNIQUE KEY `uk_school_profiles_email` (`email`),
  KEY `fk_school_profiles_address` (`address_id`),
  CONSTRAINT `fk_school_profiles_address` FOREIGN KEY (`address_id`) REFERENCES `addresses` (`address_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='School identity and contact information.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sections`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sections` (
  `section_id` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique section identifier.',
  `school_id` varchar(20) NOT NULL COMMENT 'School that owns this reusable grade-level section.',
  `grade_level_id` int(10) unsigned NOT NULL COMMENT 'Grade level containing the section.',
  `section_name` varchar(50) NOT NULL COMMENT 'Official section name such as Rizal.',
  PRIMARY KEY (`section_id`),
  KEY `idx_sections_grade_level` (`grade_level_id`),
  UNIQUE KEY `uk_sections_school_grade_name` (`school_id`,`grade_level_id`,`section_name`),
  CONSTRAINT `fk_sections_school` FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`),
  CONSTRAINT `fk_sections_grade_level` FOREIGN KEY (`grade_level_id`) REFERENCES `grade_levels` (`grade_level_id`)
) ENGINE=InnoDB AUTO_INCREMENT=910007 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='School-owned section master records independent of a specific academic year.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sf1_import_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
  CONSTRAINT `fk_sf1_import_items_class_list` FOREIGN KEY (`class_list_id`) REFERENCES `class_lists` (`class_list_id`),
  CONSTRAINT `fk_sf1_import_items_import` FOREIGN KEY (`sf1_import_id`) REFERENCES `sf1_imports` (`sf1_import_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_sf1_import_items_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Per-row incremental SF1 outcomes, warnings, and enrollment conflicts.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sf1_imports`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
  CONSTRAINT `fk_sf1_imports_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`academic_year_id`),
  CONSTRAINT `fk_sf1_imports_school` FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`),
  CONSTRAINT `fk_sf1_imports_target_class` FOREIGN KEY (`target_class_id`) REFERENCES `classes` (`class_id`),
  CONSTRAINT `fk_sf1_imports_uploaded_by_user` FOREIGN KEY (`uploaded_by_user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Auditable SF1 import operation; repeated file hashes warn but do not block incremental processing.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `skills`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `skills` (
  `skill_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique contextualized-skill identifier.',
  `competency_id` bigint(20) unsigned NOT NULL COMMENT 'Competency represented by the skill.',
  `term_period_id` int(10) unsigned NOT NULL COMMENT 'Term in which the skill is taught or assessed.',
  `grade_level_id` int(10) unsigned NOT NULL COMMENT 'Target grade level.',
  `subject_id` int(10) unsigned NOT NULL COMMENT 'Target subject.',
  PRIMARY KEY (`skill_id`),
  UNIQUE KEY `uk_skills_context` (`competency_id`,`term_period_id`,`grade_level_id`,`subject_id`),
  KEY `fk_skills_term_period` (`term_period_id`),
  KEY `fk_skills_grade_level` (`grade_level_id`),
  KEY `fk_skills_subject` (`subject_id`),
  CONSTRAINT `fk_skills_competency` FOREIGN KEY (`competency_id`) REFERENCES `competency_tags` (`competency_id`),
  CONSTRAINT `fk_skills_grade_level` FOREIGN KEY (`grade_level_id`) REFERENCES `grade_levels` (`grade_level_id`),
  CONSTRAINT `fk_skills_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`subject_id`),
  CONSTRAINT `fk_skills_term_period` FOREIGN KEY (`term_period_id`) REFERENCES `term_periods` (`term_period_id`)
) ENGINE=InnoDB AUTO_INCREMENT=910170 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Competencies contextualized by term, grade level, and subject.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `statuses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `statuses` (
  `status_id` tinyint(3) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique account-status identifier.',
  `status_name` varchar(30) NOT NULL COMMENT 'Account status such as pending, active, rejected, or inactive.',
  `is_active` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Whether the status may be assigned.',
  PRIMARY KEY (`status_id`),
  UNIQUE KEY `uk_statuses_name` (`status_name`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='User-account lifecycle statuses.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `student_answers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `student_answers` (
  `student_answer_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique verified-answer identifier.',
  `test_result_id` bigint(20) unsigned NOT NULL COMMENT 'Overall learner attempt.',
  `question_id` bigint(20) unsigned NOT NULL COMMENT 'Question answered.',
  `verified_by_user_id` bigint(20) unsigned DEFAULT NULL,
  `answer_uuid` char(36) NOT NULL COMMENT 'Offline-generated answer idempotency identifier.',
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
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Last answer update timestamp.',
  PRIMARY KEY (`student_answer_id`),
  UNIQUE KEY `uk_student_answers_uuid` (`answer_uuid`),
  UNIQUE KEY `uk_student_answers_result_question` (`test_result_id`,`question_id`),
  KEY `fk_student_answers_question` (`question_id`),
  KEY `fk_student_answers_verified_by_user` (`verified_by_user_id`),
  KEY `fk_student_answers_selected_question_option` (`selected_question_option_id`),
  CONSTRAINT `fk_student_answers_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`question_id`),
  CONSTRAINT `fk_student_answers_selected_question_option` FOREIGN KEY (`selected_question_option_id`) REFERENCES `question_options` (`question_option_id`),
  CONSTRAINT `fk_student_answers_test_result` FOREIGN KEY (`test_result_id`) REFERENCES `test_results` (`test_result_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_student_answers_verified_by_user` FOREIGN KEY (`verified_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_student_answers_points` CHECK (`points_earned` >= 0),
  CONSTRAINT `chk_student_answers_response` CHECK (`selected_question_option_id` is not null or `response_text` is not null or `answer_status` in ('blank','multiple','uncertain','invalid','pending_manual'))
) ENGINE=InnoDB AUTO_INCREMENT=1093 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Final teacher-verified answer for every evaluated question.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `student_intervention_cases`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
  CONSTRAINT `fk_student_intervention_cases_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`academic_year_id`),
  CONSTRAINT `fk_student_intervention_cases_assigned_to_user` FOREIGN KEY (`assigned_to_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_student_intervention_cases_class_list` FOREIGN KEY (`class_list_id`) REFERENCES `class_lists` (`class_list_id`),
  CONSTRAINT `fk_student_intervention_cases_opened_by_user` FOREIGN KEY (`opened_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_student_intervention_cases_rule_set` FOREIGN KEY (`performance_rule_set_id`) REFERENCES `performance_rule_sets` (`performance_rule_set_id`),
  CONSTRAINT `fk_student_intervention_cases_school` FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`),
  CONSTRAINT `fk_student_intervention_cases_skill` FOREIGN KEY (`skill_id`) REFERENCES `skills` (`skill_id`),
  CONSTRAINT `fk_student_intervention_cases_source_result` FOREIGN KEY (`source_test_result_id`) REFERENCES `test_results` (`test_result_id`),
  CONSTRAINT `fk_student_intervention_cases_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`student_id`),
  CONSTRAINT `fk_student_intervention_cases_term_period` FOREIGN KEY (`term_period_id`) REFERENCES `term_periods` (`term_period_id`),
  CONSTRAINT `chk_student_intervention_cases_dates` CHECK (`resolved_at` is null or `resolved_at` >= `opened_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Teacher-facing learner intervention cases with traceable analytics evidence.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `student_intervention_updates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
  CONSTRAINT `chk_student_intervention_updates_status_change` CHECK (`update_type` <> 'status_change' or `previous_status` is not null and `new_status` is not null)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Append-only intervention notes, actions, status changes, and learner outcomes.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `students`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `students` (
  `student_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Internal learner identifier.',
  `school_id` varchar(20) NOT NULL COMMENT 'School that owns the learner record.',
  `address_id` bigint(20) unsigned NOT NULL COMMENT 'Residential address of the learner.',
  `gender_id` tinyint(3) unsigned NOT NULL COMMENT 'Gender reference.',
  `student_lrn` varchar(12) NOT NULL COMMENT 'Official 12-digit Learner Reference Number.',
  `first_name` varchar(50) NOT NULL COMMENT 'First name.',
  `middle_name` varchar(50) DEFAULT NULL COMMENT 'Middle name.',
  `last_name` varchar(50) NOT NULL COMMENT 'Last name.',
  `suffix_id` tinyint(3) unsigned DEFAULT NULL,
  `birth_date` date DEFAULT NULL COMMENT 'Birth date.',
  `status` enum('active','inactive','transferred','graduated') NOT NULL DEFAULT 'active' COMMENT 'Learner lifecycle status.',
  `status_reason` varchar(255) DEFAULT NULL,
  `status_effective_at` timestamp NULL DEFAULT NULL,
  `status_changed_by_user_id` bigint(20) unsigned DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Record creation timestamp.',
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`student_id`),
  UNIQUE KEY `uk_students_lrn` (`student_lrn`),
  KEY `fk_students_address` (`address_id`),
  KEY `fk_students_gender` (`gender_id`),
  KEY `idx_students_school_name` (`school_id`,`last_name`,`first_name`),
  KEY `fk_students_suffix` (`suffix_id`),
  KEY `fk_students_status_changed_by_user` (`status_changed_by_user_id`),
  CONSTRAINT `fk_students_address` FOREIGN KEY (`address_id`) REFERENCES `addresses` (`address_id`),
  CONSTRAINT `fk_students_gender` FOREIGN KEY (`gender_id`) REFERENCES `genders` (`gender_id`),
  CONSTRAINT `fk_students_school` FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`),
  CONSTRAINT `fk_students_status_changed_by_user` FOREIGN KEY (`status_changed_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_students_suffix` FOREIGN KEY (`suffix_id`) REFERENCES `suffixes` (`suffix_id`),
  CONSTRAINT `chk_students_lrn` CHECK (`student_lrn` regexp '^[0-9]{12}$')
) ENGINE=InnoDB AUTO_INCREMENT=1015 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='School learner master records.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `subjects`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `subjects` (
  `subject_id` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique subject identifier.',
  `subject_code` varchar(10) DEFAULT NULL COMMENT 'Official or internal subject code.',
  `subject_name` varchar(100) NOT NULL COMMENT 'Official subject display name such as Technology and Livelihood Education.',
  PRIMARY KEY (`subject_id`),
  UNIQUE KEY `uk_subjects_name` (`subject_name`),
  UNIQUE KEY `uk_subjects_code` (`subject_code`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Subject master records.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `suffixes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `suffixes` (
  `suffix_id` tinyint(3) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique suffix identifier.',
  `suffix_name` varchar(10) NOT NULL COMMENT 'Name suffix display value such as Jr. or III.',
  `display_order` tinyint(3) unsigned NOT NULL COMMENT 'Dropdown display order.',
  `is_active` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Whether the suffix may be selected.',
  PRIMARY KEY (`suffix_id`),
  UNIQUE KEY `uk_suffixes_name` (`suffix_name`),
  UNIQUE KEY `uk_suffixes_order` (`display_order`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Optional teacher and learner name suffix reference values.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sync_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sync_items` (
  `sync_item_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique item identifier within a synchronization batch.',
  `sync_id` bigint(20) unsigned NOT NULL COMMENT 'Parent synchronization batch.',
  `result_uuid` char(36) NOT NULL COMMENT 'Offline result identifier available before central insertion.',
  `test_result_id` bigint(20) unsigned DEFAULT NULL COMMENT 'Central result identifier after successful insertion or matching.',
  `sync_action` enum('upsert') NOT NULL DEFAULT 'upsert',
  `sync_status` enum('pending','success','failed','skipped') NOT NULL DEFAULT 'pending' COMMENT 'Individual result-processing status.',
  `attempt_count` smallint(5) unsigned NOT NULL DEFAULT 0,
  `last_attempt_at` timestamp NULL DEFAULT NULL,
  `processed_at` timestamp NULL DEFAULT NULL,
  `error_code` varchar(50) DEFAULT NULL COMMENT 'Machine-readable failure code.',
  `error_message` text DEFAULT NULL COMMENT 'Detailed item-level failure message.',
  `synced_at` timestamp NULL DEFAULT NULL COMMENT 'Successful item-processing timestamp.',
  PRIMARY KEY (`sync_item_id`),
  UNIQUE KEY `uk_sync_items_batch_result_uuid` (`sync_id`,`result_uuid`),
  KEY `fk_sync_items_test_result` (`test_result_id`),
  KEY `idx_sync_items_result_uuid` (`result_uuid`),
  KEY `idx_sync_items_status` (`sync_id`,`sync_status`),
  CONSTRAINT `fk_sync_items_sync` FOREIGN KEY (`sync_id`) REFERENCES `syncs` (`sync_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_sync_items_test_result` FOREIGN KEY (`test_result_id`) REFERENCES `test_results` (`test_result_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1035 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Individual learner results processed inside one synchronization batch.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `syncs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `syncs` (
  `sync_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Central synchronization-batch identifier.',
  `sync_uuid` char(36) NOT NULL COMMENT 'Offline-generated idempotency identifier for the batch.',
  `user_id` bigint(20) unsigned NOT NULL COMMENT 'Teacher or user who initiated the synchronization.',
  `test_assignment_id` bigint(20) unsigned DEFAULT NULL,
  `device_identifier` varchar(100) DEFAULT NULL COMMENT 'Mobile installation or device identifier.',
  `direction` enum('download','upload') NOT NULL COMMENT 'Direction of data transfer.',
  `sync_status` enum('pending','in_progress','partial_success','success','failed') NOT NULL DEFAULT 'pending' COMMENT 'Overall synchronization-batch status.',
  `payload_hash` char(64) DEFAULT NULL COMMENT 'Hash used for duplicate-payload detection.',
  `retry_count` smallint(5) unsigned NOT NULL DEFAULT 0,
  `last_retry_at` timestamp NULL DEFAULT NULL,
  `request_item_count` int(10) unsigned NOT NULL DEFAULT 0,
  `idempotency_version` smallint(5) unsigned NOT NULL DEFAULT 1,
  `started_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Actual synchronization start time.',
  `completed_at` timestamp NULL DEFAULT NULL COMMENT 'Synchronization completion time.',
  `error_message` text DEFAULT NULL COMMENT 'Batch-level failure details.',
  PRIMARY KEY (`sync_id`),
  UNIQUE KEY `uk_syncs_uuid` (`sync_uuid`),
  KEY `idx_syncs_user_started` (`user_id`,`started_at`),
  KEY `idx_syncs_status_started` (`sync_status`,`started_at`),
  KEY `idx_syncs_assignment_started` (`test_assignment_id`,`started_at`),
  CONSTRAINT `fk_syncs_test_assignment` FOREIGN KEY (`test_assignment_id`) REFERENCES `test_assignments` (`test_assignment_id`),
  CONSTRAINT `fk_syncs_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_syncs_request_item_count` CHECK (`request_item_count` >= 0)
) ENGINE=InnoDB AUTO_INCREMENT=1004 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='One mobile synchronization batch containing zero or more result items.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `term_periods`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `term_periods` (
  `term_period_id` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique term-period identifier.',
  `academic_year_id` int(10) unsigned NOT NULL COMMENT 'Academic year containing the term.',
  `term_name` varchar(50) NOT NULL COMMENT 'Display name such as First Quarter.',
  `term_order` tinyint(3) unsigned NOT NULL COMMENT 'Chronological order within the academic year.',
  `start_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `end_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `status` enum('planned','active','completed') NOT NULL DEFAULT 'planned' COMMENT 'Term lifecycle status.',
  `activation_mode` enum('automatic','manual') NOT NULL DEFAULT 'automatic',
  `activated_at` timestamp NULL DEFAULT NULL,
  `completed_at` timestamp NULL DEFAULT NULL,
  `overridden_by_user_id` bigint(20) unsigned DEFAULT NULL,
  `override_reason` varchar(255) DEFAULT NULL,
  `overridden_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`term_period_id`),
  UNIQUE KEY `uk_term_periods_order` (`academic_year_id`,`term_order`),
  UNIQUE KEY `uk_term_periods_name` (`academic_year_id`,`term_name`),
  KEY `fk_term_periods_overridden_by_user` (`overridden_by_user_id`),
  CONSTRAINT `fk_term_periods_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`academic_year_id`),
  CONSTRAINT `fk_term_periods_overridden_by_user` FOREIGN KEY (`overridden_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_term_periods_dates` CHECK (`end_at` > `start_at`),
  CONSTRAINT `chk_term_periods_override` CHECK (`overridden_by_user_id` is null or `override_reason` is not null and `overridden_at` is not null)
) ENGINE=InnoDB AUTO_INCREMENT=910006 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Academic-year terms or grading periods.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `test_assignments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
  `assigned_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`test_assignment_id`),
  UNIQUE KEY `uk_test_assignments_uuid` (`assignment_uuid`),
  UNIQUE KEY `uk_test_assignments_test_class` (`test_id`,`class_assignment_id`),
  KEY `fk_test_assignments_assigned_by_user` (`assigned_by_user_id`),
  KEY `idx_test_assignments_class_status` (`class_assignment_id`,`assignment_status`,`open_at`,`close_at`),
  CONSTRAINT `fk_test_assignments_assigned_by_user` FOREIGN KEY (`assigned_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_test_assignments_class_assignment` FOREIGN KEY (`class_assignment_id`) REFERENCES `class_assignments` (`class_assignment_id`),
  CONSTRAINT `fk_test_assignments_test` FOREIGN KEY (`test_id`) REFERENCES `tests` (`test_id`),
  CONSTRAINT `chk_test_assignments_dates` CHECK (`close_at` is null or `open_at` is null or `close_at` > `open_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Delivery of reusable assessment content to a specific teacher, class, and subject assignment.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `test_parts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `test_parts` (
  `test_part_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique assessment-part identifier.',
  `test_id` bigint(20) unsigned NOT NULL COMMENT 'Parent assessment.',
  `part_order` smallint(5) unsigned NOT NULL COMMENT 'Display and processing order.',
  `part_name` varchar(80) NOT NULL COMMENT 'Part label such as Part I.',
  `question_type_id` smallint(5) unsigned NOT NULL,
  `number_of_items` smallint(5) unsigned NOT NULL COMMENT 'Validated snapshot of questions in the part.',
  `points_per_item` decimal(5,2) NOT NULL DEFAULT 1.00 COMMENT 'Default points for each correct answer.',
  `part_instructions` text DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`test_part_id`),
  UNIQUE KEY `uk_test_parts_order` (`test_id`,`part_order`),
  KEY `fk_test_parts_question_type` (`question_type_id`),
  CONSTRAINT `fk_test_parts_question_type` FOREIGN KEY (`question_type_id`) REFERENCES `question_types` (`question_type_id`),
  CONSTRAINT `fk_test_parts_test` FOREIGN KEY (`test_id`) REFERENCES `tests` (`test_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_test_parts_number_of_items` CHECK (`number_of_items` > 0),
  CONSTRAINT `chk_test_parts_points_per_item` CHECK (`points_per_item` > 0)
) ENGINE=InnoDB AUTO_INCREMENT=1015 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Ordered groups of questions inside an assessment.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `test_result_scans`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `test_result_scans` (
  `test_result_scan_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique result-to-scan audit-link identifier.',
  `test_result_id` bigint(20) unsigned NOT NULL COMMENT 'Verified learner result associated with the capture.',
  `scan_session_id` bigint(20) unsigned NOT NULL COMMENT 'Captured OMR scan retained for the result audit trail.',
  `link_status` enum('selected','superseded','rejected') NOT NULL COMMENT 'Whether this capture is authoritative, replaced, or rejected.',
  `selected_result_id` bigint(20) unsigned GENERATED ALWAYS AS (case when `link_status` = 'selected' then `test_result_id` else NULL end) VIRTUAL COMMENT 'Generated key used to enforce one selected scan per result.',
  `decided_by_user_id` bigint(20) unsigned NOT NULL COMMENT 'Teacher who selected, superseded, or rejected the capture.',
  `decision_reason` varchar(255) DEFAULT NULL COMMENT 'Reason for superseding or rejecting the capture.',
  `linked_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Timestamp when the capture was linked to the result.',
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Last disposition update timestamp.',
  PRIMARY KEY (`test_result_scan_id`),
  UNIQUE KEY `uk_test_result_scans_scan` (`scan_session_id`),
  UNIQUE KEY `uk_test_result_scans_result_scan` (`test_result_id`,`scan_session_id`),
  UNIQUE KEY `uk_test_result_scans_one_selected` (`selected_result_id`),
  KEY `fk_test_result_scans_decided_by_user` (`decided_by_user_id`),
  KEY `idx_test_result_scans_result_status` (`test_result_id`,`link_status`),
  CONSTRAINT `fk_test_result_scans_decided_by_user` FOREIGN KEY (`decided_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_test_result_scans_scan_session` FOREIGN KEY (`scan_session_id`) REFERENCES `scan_sessions` (`scan_session_id`),
  CONSTRAINT `fk_test_result_scans_test_result` FOREIGN KEY (`test_result_id`) REFERENCES `test_results` (`test_result_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1013 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Audit-ready history of selected, superseded, and rejected scans for each learner result.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `test_results`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `test_results` (
  `test_result_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Central verified-result identifier.',
  `result_uuid` char(36) NOT NULL COMMENT 'Offline-generated idempotency identifier.',
  `test_assignment_id` bigint(20) unsigned NOT NULL,
  `class_list_id` bigint(20) unsigned NOT NULL COMMENT 'Learner membership that took the assessment.',
  `attempt_number` smallint(5) unsigned NOT NULL DEFAULT 1 COMMENT 'Attempt sequence for the learner and assessment.',
  `total_score` decimal(8,2) NOT NULL COMMENT 'Sum of verified points earned.',
  `max_score` decimal(8,2) NOT NULL COMMENT 'Maximum possible points snapshot.',
  `items_evaluated` int(10) unsigned NOT NULL COMMENT 'Number of evaluated questions.',
  `result_status` enum('draft','pending_verification','finalized','superseded') NOT NULL DEFAULT 'draft',
  `submitted_at` timestamp NULL DEFAULT NULL,
  `verification_completed_at` timestamp NULL DEFAULT NULL,
  `scored_at` timestamp NULL DEFAULT NULL,
  `finalized_at` timestamp NULL DEFAULT NULL,
  `percentage_snapshot` decimal(7,4) DEFAULT NULL,
  `performance_status` varchar(50) DEFAULT NULL,
  `performance_rule_set_id` bigint(20) unsigned DEFAULT NULL,
  `score_version` int(10) unsigned NOT NULL DEFAULT 1,
  `checked_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Teacher verification or checking completion timestamp.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Record creation timestamp.',
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Last update timestamp.',
  PRIMARY KEY (`test_result_id`),
  UNIQUE KEY `uk_test_results_uuid` (`result_uuid`),
  UNIQUE KEY `uk_test_results_attempt` (`test_assignment_id`,`class_list_id`,`attempt_number`),
  KEY `fk_test_results_class_list` (`class_list_id`),
  KEY `fk_test_results_performance_rule_set` (`performance_rule_set_id`),
  KEY `idx_test_results_assignment_status` (`test_assignment_id`,`result_status`,`finalized_at`),
  CONSTRAINT `fk_test_results_class_list` FOREIGN KEY (`class_list_id`) REFERENCES `class_lists` (`class_list_id`),
  CONSTRAINT `fk_test_results_performance_rule_set` FOREIGN KEY (`performance_rule_set_id`) REFERENCES `performance_rule_sets` (`performance_rule_set_id`),
  CONSTRAINT `fk_test_results_test_assignment` FOREIGN KEY (`test_assignment_id`) REFERENCES `test_assignments` (`test_assignment_id`),
  CONSTRAINT `chk_test_results_scores` CHECK (`total_score` >= 0 and `max_score` >= 0 and `total_score` <= `max_score`),
  CONSTRAINT `chk_test_results_percentage` CHECK (`percentage_snapshot` is null or `percentage_snapshot` between 0 and 100)
) ENGINE=InnoDB AUTO_INCREMENT=1011 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Verified overall learner attempts from OMR or approved manual capture.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tests` (
  `test_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique assessment identifier.',
  `test_uuid` char(36) NOT NULL,
  `school_id` varchar(20) NOT NULL,
  `created_by_user_id` bigint(20) unsigned NOT NULL,
  `version_number` int(10) unsigned NOT NULL DEFAULT 1,
  `source_test_id` bigint(20) unsigned DEFAULT NULL,
  `term_period_id` int(10) unsigned NOT NULL COMMENT 'Term period of the assessment.',
  `test_name` varchar(120) NOT NULL COMMENT 'Assessment title.',
  `test_type` enum('quiz','exam','diagnostic','long_test','other') NOT NULL COMMENT 'Assessment category.',
  `instructions` text DEFAULT NULL COMMENT 'General assessment instructions.',
  `total_items` int(10) unsigned NOT NULL DEFAULT 0 COMMENT 'Validated snapshot of the total question count.',
  `status` enum('draft','active','completed','archived') NOT NULL DEFAULT 'draft' COMMENT 'Assessment lifecycle status.',
  `published_at` timestamp NULL DEFAULT NULL,
  `content_locked_at` timestamp NULL DEFAULT NULL,
  `completed_at` timestamp NULL DEFAULT NULL,
  `archived_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Record creation timestamp.',
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Last update timestamp.',
  PRIMARY KEY (`test_id`),
  UNIQUE KEY `uk_tests_uuid` (`test_uuid`),
  KEY `fk_tests_term_period` (`term_period_id`),
  KEY `fk_tests_created_by_user` (`created_by_user_id`),
  KEY `fk_tests_source_test` (`source_test_id`),
  KEY `idx_tests_owner_term_status` (`school_id`,`created_by_user_id`,`term_period_id`,`status`),
  CONSTRAINT `fk_tests_created_by_user` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_tests_school` FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`),
  CONSTRAINT `fk_tests_source_test` FOREIGN KEY (`source_test_id`) REFERENCES `tests` (`test_id`),
  CONSTRAINT `fk_tests_term_period` FOREIGN KEY (`term_period_id`) REFERENCES `term_periods` (`term_period_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1010 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Assessments owned by a teacher-class-subject assignment.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_mfa_factors`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
  CONSTRAINT `chk_user_mfa_factors_totp` CHECK (`totp_digits` in (6,8) and `totp_period_seconds` between 15 and 120)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Encrypted TOTP authenticator factors. Secrets must never be stored as plain text.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `users` (
  `user_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique user identifier.',
  `school_id` varchar(20) NOT NULL COMMENT 'School that owns the account.',
  `address_id` bigint(20) unsigned NOT NULL COMMENT 'Residential address of the user.',
  `gender_id` tinyint(3) unsigned NOT NULL COMMENT 'Gender reference.',
  `major_id` int(10) unsigned DEFAULT NULL COMMENT 'Teacher major or specialization; optional for principals.',
  `educational_attainment_id` tinyint(3) unsigned DEFAULT NULL COMMENT 'Highest educational attainment.',
  `role_id` tinyint(3) unsigned NOT NULL COMMENT 'Authorization role.',
  `status_id` tinyint(3) unsigned NOT NULL COMMENT 'Account lifecycle status.',
  `first_name` varchar(50) NOT NULL COMMENT 'First name.',
  `middle_name` varchar(50) DEFAULT NULL COMMENT 'Middle name.',
  `last_name` varchar(50) NOT NULL COMMENT 'Last name.',
  `suffix_id` tinyint(3) unsigned DEFAULT NULL,
  `birth_date` date DEFAULT NULL COMMENT 'Birth date used to derive age.',
  `teaching_start_month` tinyint(3) unsigned DEFAULT NULL,
  `teaching_start_year` smallint(5) unsigned DEFAULT NULL,
  `email` varchar(120) NOT NULL COMMENT 'Unique login email.',
  `contact_number` varchar(20) NOT NULL COMMENT 'Unique primary contact number.',
  `password_hash` varchar(255) NOT NULL COMMENT 'Secure password hash; never stores a plain-text password.',
  `failed_login_count` smallint(5) unsigned NOT NULL DEFAULT 0 COMMENT 'Consecutive failed-login counter.',
  `locked_until_at` timestamp NULL DEFAULT NULL COMMENT 'Account lock expiration timestamp.',
  `last_login_at` timestamp NULL DEFAULT NULL COMMENT 'Most recent successful-login timestamp.',
  `password_changed_at` timestamp NULL DEFAULT NULL COMMENT 'Most recent password-change timestamp.',
  `email_verified_at` timestamp NULL DEFAULT NULL COMMENT 'Email-verification timestamp.',
  `contact_verified_at` timestamp NULL DEFAULT NULL COMMENT 'Contact-number verification timestamp.',
  `mfa_required` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Account creation timestamp.',
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Last account update timestamp.',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_users_email` (`email`),
  UNIQUE KEY `uk_users_contact_number` (`contact_number`),
  KEY `fk_users_address` (`address_id`),
  KEY `fk_users_gender` (`gender_id`),
  KEY `fk_users_major` (`major_id`),
  KEY `fk_users_educational_attainment` (`educational_attainment_id`),
  KEY `fk_users_role` (`role_id`),
  KEY `fk_users_status` (`status_id`),
  KEY `idx_users_school_role_status` (`school_id`,`role_id`,`status_id`),
  KEY `fk_users_suffix` (`suffix_id`),
  CONSTRAINT `fk_users_address` FOREIGN KEY (`address_id`) REFERENCES `addresses` (`address_id`),
  CONSTRAINT `fk_users_educational_attainment` FOREIGN KEY (`educational_attainment_id`) REFERENCES `educational_attainments` (`educational_attainment_id`),
  CONSTRAINT `fk_users_gender` FOREIGN KEY (`gender_id`) REFERENCES `genders` (`gender_id`),
  CONSTRAINT `fk_users_major` FOREIGN KEY (`major_id`) REFERENCES `majors` (`major_id`),
  CONSTRAINT `fk_users_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`role_id`),
  CONSTRAINT `fk_users_school` FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`),
  CONSTRAINT `fk_users_status` FOREIGN KEY (`status_id`) REFERENCES `statuses` (`status_id`),
  CONSTRAINT `fk_users_suffix` FOREIGN KEY (`suffix_id`) REFERENCES `suffixes` (`suffix_id`),
  CONSTRAINT `chk_users_teaching_start_month` CHECK (`teaching_start_month` is null or `teaching_start_month` between 1 and 12),
  CONSTRAINT `chk_users_teaching_start_year` CHECK (`teaching_start_year` is null or `teaching_start_year` between 1900 and 2200)
) ENGINE=InnoDB AUTO_INCREMENT=910025 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Principal and teacher accounts, identity, security, and profile information.';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `verification_challenges`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
  CONSTRAINT `chk_verification_challenges_attempts` CHECK (`maximum_attempt_count` > 0 and `attempt_count` <= `maximum_attempt_count`),
  CONSTRAINT `chk_verification_challenges_expiry` CHECK (`expires_at` > `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Hashed, expiring, rate-limited email and SMS verification challenges.';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
