-- MariaDB dump 10.19  Distrib 10.4.32-MariaDB, for Win64 (AMD64)
--
-- Host: 127.0.0.1    Database: performance_assessment_v2_db
-- ------------------------------------------------------
-- Server version	10.4.32-MariaDB

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

--
-- Table structure for table `academic_years`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='School academic years and their curriculum version.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `academic_years`
--

LOCK TABLES `academic_years` WRITE;
/*!40000 ALTER TABLE `academic_years` DISABLE KEYS */;
/*!40000 ALTER TABLE `academic_years` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `addresses`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=2000019 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Normalized address records for schools, users, and students.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `addresses`
--

LOCK TABLES `addresses` WRITE;
/*!40000 ALTER TABLE `addresses` DISABLE KEYS */;
/*!40000 ALTER TABLE `addresses` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `answer_keys`
--

DROP TABLE IF EXISTS `answer_keys`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `answer_keys` (
  `answer_key_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique answer-key identifier.',
  `question_id` bigint(20) unsigned NOT NULL COMMENT 'Question answered by this key.',
  `correct_option` char(1) NOT NULL COMMENT 'Correct option from A through E.',
  PRIMARY KEY (`answer_key_id`),
  UNIQUE KEY `uk_answer_keys_question` (`question_id`),
  CONSTRAINT `fk_answer_keys_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`question_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_answer_keys_option` CHECK (`correct_option` in ('A','B','C','D','E'))
) ENGINE=InnoDB AUTO_INCREMENT=700011 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='One correct answer for each assessment question.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `answer_keys`
--

LOCK TABLES `answer_keys` WRITE;
/*!40000 ALTER TABLE `answer_keys` DISABLE KEYS */;
/*!40000 ALTER TABLE `answer_keys` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `audit_logs`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Append-only audit trail for security-sensitive and business-critical actions.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_logs`
--

LOCK TABLES `audit_logs` WRITE;
/*!40000 ALTER TABLE `audit_logs` DISABLE KEYS */;
/*!40000 ALTER TABLE `audit_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `auth_sessions`
--

DROP TABLE IF EXISTS `auth_sessions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `auth_sessions` (
  `auth_session_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Central authenticated-session identifier.',
  `session_uuid` char(36) NOT NULL COMMENT 'Public unique session identifier.',
  `user_id` bigint(20) unsigned NOT NULL COMMENT 'User who owns the session.',
  `refresh_token_hash` char(64) NOT NULL COMMENT 'SHA-256 refresh-token hash; never stores the raw token.',
  `device_identifier` varchar(100) DEFAULT NULL COMMENT 'Browser or mobile-device identifier.',
  `ip_address` varchar(45) DEFAULT NULL COMMENT 'IPv4 or IPv6 address at session creation.',
  `user_agent` text DEFAULT NULL COMMENT 'Browser, operating-system, or application information.',
  `issued_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Session creation timestamp.',
  `expires_at` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00' COMMENT 'Session expiration timestamp.',
  `last_used_at` timestamp NULL DEFAULT NULL COMMENT 'Most recent refresh-token use timestamp.',
  `revoked_at` timestamp NULL DEFAULT NULL COMMENT 'Logout or security-revocation timestamp.',
  PRIMARY KEY (`auth_session_id`),
  UNIQUE KEY `uk_auth_sessions_uuid` (`session_uuid`),
  UNIQUE KEY `uk_auth_sessions_refresh_token_hash` (`refresh_token_hash`),
  KEY `idx_auth_sessions_user_expiry` (`user_id`,`expires_at`),
  KEY `idx_auth_sessions_user_revoked` (`user_id`,`revoked_at`),
  CONSTRAINT `fk_auth_sessions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Secure login sessions and refresh-token lifecycle records.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auth_sessions`
--

LOCK TABLES `auth_sessions` WRITE;
/*!40000 ALTER TABLE `auth_sessions` DISABLE KEYS */;
/*!40000 ALTER TABLE `auth_sessions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `class_assignments`
--

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
  `status` enum('active','completed','archived') NOT NULL DEFAULT 'active' COMMENT 'Assignment lifecycle status.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Record creation timestamp.',
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Last update timestamp.',
  PRIMARY KEY (`class_assignment_id`),
  UNIQUE KEY `uk_class_assignments_teacher_subject` (`class_id`,`user_id`,`subject_id`),
  KEY `fk_class_assignments_user` (`user_id`),
  KEY `fk_class_assignments_subject` (`subject_id`),
  KEY `idx_class_assignments_class_subject` (`class_id`,`subject_id`,`status`),
  CONSTRAINT `fk_class_assignments_class` FOREIGN KEY (`class_id`) REFERENCES `classes` (`class_id`),
  CONSTRAINT `fk_class_assignments_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`subject_id`),
  CONSTRAINT `fk_class_assignments_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Teacher and subject assignments for a class cohort.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `class_assignments`
--

LOCK TABLES `class_assignments` WRITE;
/*!40000 ALTER TABLE `class_assignments` DISABLE KEYS */;
/*!40000 ALTER TABLE `class_assignments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `class_lists`
--

DROP TABLE IF EXISTS `class_lists`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `class_lists` (
  `class_list_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique learner-membership identifier.',
  `class_id` bigint(20) unsigned NOT NULL COMMENT 'Class cohort containing the learner.',
  `student_id` bigint(20) unsigned NOT NULL COMMENT 'Learner in the class cohort.',
  PRIMARY KEY (`class_list_id`),
  UNIQUE KEY `uk_class_lists_class_student` (`class_id`,`student_id`),
  KEY `idx_class_lists_student` (`student_id`),
  CONSTRAINT `fk_class_lists_class` FOREIGN KEY (`class_id`) REFERENCES `classes` (`class_id`),
  CONSTRAINT `fk_class_lists_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`student_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Normalized learner membership for each class cohort.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `class_lists`
--

LOCK TABLES `class_lists` WRITE;
/*!40000 ALTER TABLE `class_lists` DISABLE KEYS */;
/*!40000 ALTER TABLE `class_lists` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `classes`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Section cohorts for a specific academic year.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `classes`
--

LOCK TABLES `classes` WRITE;
/*!40000 ALTER TABLE `classes` DISABLE KEYS */;
/*!40000 ALTER TABLE `classes` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `competency_tags`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Competency statements under curriculum root categories.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `competency_tags`
--

LOCK TABLES `competency_tags` WRITE;
/*!40000 ALTER TABLE `competency_tags` DISABLE KEYS */;
/*!40000 ALTER TABLE `competency_tags` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `curriculums`
--

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

--
-- Dumping data for table `curriculums`
--

LOCK TABLES `curriculums` WRITE;
/*!40000 ALTER TABLE `curriculums` DISABLE KEYS */;
INSERT INTO `curriculums` VALUES (1,'MATATAG Curriculum','2024','Initial curriculum master record for V2 configuration. Confirm the version with the school before production use.','active','2026-08-08 13:39:58');
/*!40000 ALTER TABLE `curriculums` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `educational_attainments`
--

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

--
-- Dumping data for table `educational_attainments`
--

LOCK TABLES `educational_attainments` WRITE;
/*!40000 ALTER TABLE `educational_attainments` DISABLE KEYS */;
INSERT INTO `educational_attainments` VALUES (1,'Bachelor\'s Degree',1,'Completed bachelor\'s degree.',1),(2,'Master\'s Units',2,'Completed graduate-level master\'s units.',1),(3,'Master\'s Degree',3,'Completed master\'s degree.',1),(4,'Doctoral Units',4,'Completed graduate-level doctoral units.',1),(5,'Doctorate Degree',5,'Completed doctorate degree.',1);
/*!40000 ALTER TABLE `educational_attainments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `genders`
--

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

--
-- Dumping data for table `genders`
--

LOCK TABLES `genders` WRITE;
/*!40000 ALTER TABLE `genders` DISABLE KEYS */;
INSERT INTO `genders` VALUES (1,'Male','Male learner or user.'),(2,'Female','Female learner or user.');
/*!40000 ALTER TABLE `genders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `grade_levels`
--

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

--
-- Dumping data for table `grade_levels`
--

LOCK TABLES `grade_levels` WRITE;
/*!40000 ALTER TABLE `grade_levels` DISABLE KEYS */;
INSERT INTO `grade_levels` VALUES (4,'Grade 10'),(1,'Grade 7'),(2,'Grade 8'),(3,'Grade 9');
/*!40000 ALTER TABLE `grade_levels` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `intervention_results`
--

DROP TABLE IF EXISTS `intervention_results`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `intervention_results` (
  `intervention_result_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique generated recommendation identifier.',
  `test_result_id` bigint(20) unsigned NOT NULL COMMENT 'Learner result receiving the recommendation.',
  `intervention_id` bigint(20) unsigned NOT NULL COMMENT 'Recommended intervention.',
  PRIMARY KEY (`intervention_result_id`),
  UNIQUE KEY `uk_intervention_results_result_intervention` (`test_result_id`,`intervention_id`),
  KEY `fk_intervention_results_intervention` (`intervention_id`),
  CONSTRAINT `fk_intervention_results_intervention` FOREIGN KEY (`intervention_id`) REFERENCES `interventions` (`intervention_id`),
  CONSTRAINT `fk_intervention_results_test_result` FOREIGN KEY (`test_result_id`) REFERENCES `test_results` (`test_result_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Interventions generated from a learner assessment result.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `intervention_results`
--

LOCK TABLES `intervention_results` WRITE;
/*!40000 ALTER TABLE `intervention_results` DISABLE KEYS */;
/*!40000 ALTER TABLE `intervention_results` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `interventions`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Master intervention recommendations for specific skills.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `interventions`
--

LOCK TABLES `interventions` WRITE;
/*!40000 ALTER TABLE `interventions` DISABLE KEYS */;
/*!40000 ALTER TABLE `interventions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `login_attempts`
--

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
  `failure_reason` enum('invalid_credentials','pending_approval','rejected_account','inactive_account','locked_account','email_not_verified','rate_limited','other') DEFAULT NULL COMMENT 'Controlled failure reason; null for successful attempts.',
  `attempted_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Login-attempt timestamp.',
  PRIMARY KEY (`login_attempt_id`),
  KEY `idx_login_attempts_user_time` (`user_id`,`attempted_at`),
  KEY `idx_login_attempts_email_time` (`attempted_email`,`attempted_at`),
  KEY `idx_login_attempts_ip_time` (`ip_address`,`attempted_at`),
  CONSTRAINT `fk_login_attempts_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Successful and failed login attempts used for security monitoring and lockout.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `login_attempts`
--

LOCK TABLES `login_attempts` WRITE;
/*!40000 ALTER TABLE `login_attempts` DISABLE KEYS */;
/*!40000 ALTER TABLE `login_attempts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `majors`
--

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

--
-- Dumping data for table `majors`
--

LOCK TABLES `majors` WRITE;
/*!40000 ALTER TABLE `majors` DISABLE KEYS */;
INSERT INTO `majors` VALUES (5,'Araling Panlipunan'),(1,'English'),(4,'Filipino'),(9,'General Education'),(6,'MAPEH'),(2,'Mathematics'),(3,'Science'),(7,'Technology and Livelihood Education'),(8,'Values Education');
/*!40000 ALTER TABLE `majors` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mappings`
--

DROP TABLE IF EXISTS `mappings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mappings` (
  `mapping_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique question-to-skill mapping identifier.',
  `question_id` bigint(20) unsigned NOT NULL COMMENT 'Question being classified.',
  `skill_id` bigint(20) unsigned NOT NULL COMMENT 'Skill measured by the question.',
  PRIMARY KEY (`mapping_id`),
  UNIQUE KEY `uk_mappings_question_skill` (`question_id`,`skill_id`),
  KEY `fk_mappings_skill` (`skill_id`),
  CONSTRAINT `fk_mappings_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`question_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_mappings_skill` FOREIGN KEY (`skill_id`) REFERENCES `skills` (`skill_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1130 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Normalized many-to-many mapping between questions and measured skills.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mappings`
--

LOCK TABLES `mappings` WRITE;
/*!40000 ALTER TABLE `mappings` DISABLE KEYS */;
/*!40000 ALTER TABLE `mappings` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `omr_detections`
--

DROP TABLE IF EXISTS `omr_detections`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `omr_detections` (
  `omr_detection_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique raw OMR-detection identifier.',
  `scan_session_id` bigint(20) unsigned NOT NULL COMMENT 'Parent scanned sheet.',
  `question_id` bigint(20) unsigned NOT NULL COMMENT 'Question represented by the bubble position.',
  `detected_option` char(1) DEFAULT NULL COMMENT 'Scanner-detected option; null for blank or ambiguous marks.',
  `confidence_score` decimal(5,4) NOT NULL COMMENT 'Detection confidence from 0.0000 through 1.0000.',
  `detection_status` enum('detected','blank','multiple_marks','uncertain') NOT NULL COMMENT 'Raw scanner interpretation.',
  `verification_status` enum('pending','confirmed','corrected') NOT NULL DEFAULT 'pending' COMMENT 'Teacher-verification status.',
  `raw_mark` text NOT NULL COMMENT 'Structured darkness or confidence measurements for each option.',
  `detected_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Detection timestamp.',
  PRIMARY KEY (`omr_detection_id`),
  UNIQUE KEY `uk_omr_detections_scan_question` (`scan_session_id`,`question_id`),
  KEY `fk_omr_detections_question` (`question_id`),
  CONSTRAINT `fk_omr_detections_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`question_id`),
  CONSTRAINT `fk_omr_detections_scan_session` FOREIGN KEY (`scan_session_id`) REFERENCES `scan_sessions` (`scan_session_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_omr_detections_option` CHECK (`detected_option` is null or `detected_option` in ('A','B','C','D','E')),
  CONSTRAINT `chk_omr_detections_confidence` CHECK (`confidence_score` >= 0 and `confidence_score` <= 1)
) ENGINE=InnoDB AUTO_INCREMENT=1003 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Raw OMR detections retained separately from final teacher-verified answers.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `omr_detections`
--

LOCK TABLES `omr_detections` WRITE;
/*!40000 ALTER TABLE `omr_detections` DISABLE KEYS */;
/*!40000 ALTER TABLE `omr_detections` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `questions`
--

DROP TABLE IF EXISTS `questions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `questions` (
  `question_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique question identifier.',
  `test_part_id` bigint(20) unsigned NOT NULL COMMENT 'Test part containing the question.',
  `item_number` int(10) unsigned NOT NULL COMMENT 'Question number within the part.',
  `question_text` text NOT NULL COMMENT 'Actual assessment question.',
  `option_a` varchar(255) NOT NULL COMMENT 'Text for option A.',
  `option_b` varchar(255) NOT NULL COMMENT 'Text for option B.',
  `option_c` varchar(255) NOT NULL COMMENT 'Text for option C.',
  `option_d` varchar(255) NOT NULL COMMENT 'Text for option D.',
  `option_e` varchar(255) DEFAULT NULL COMMENT 'Optional text for option E.',
  PRIMARY KEY (`question_id`),
  UNIQUE KEY `uk_questions_part_item` (`test_part_id`,`item_number`),
  CONSTRAINT `fk_questions_test_part` FOREIGN KEY (`test_part_id`) REFERENCES `test_parts` (`test_part_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=700011 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Individual multiple-choice questions used by answer keys and OMR.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `questions`
--

LOCK TABLES `questions` WRITE;
/*!40000 ALTER TABLE `questions` DISABLE KEYS */;
/*!40000 ALTER TABLE `questions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `roles`
--

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

--
-- Dumping data for table `roles`
--

LOCK TABLES `roles` WRITE;
/*!40000 ALTER TABLE `roles` DISABLE KEYS */;
INSERT INTO `roles` VALUES (1,'principal'),(2,'teacher');
/*!40000 ALTER TABLE `roles` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `root_tags`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Curriculum-specific root competency categories.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `root_tags`
--

LOCK TABLES `root_tags` WRITE;
/*!40000 ALTER TABLE `root_tags` DISABLE KEYS */;
/*!40000 ALTER TABLE `root_tags` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `scan_sessions`
--

DROP TABLE IF EXISTS `scan_sessions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `scan_sessions` (
  `scan_session_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Central scan-session identifier.',
  `scan_uuid` char(36) NOT NULL COMMENT 'Offline-generated idempotency identifier.',
  `scanned_by_user_id` bigint(20) unsigned NOT NULL COMMENT 'Teacher operating the scanner.',
  `verified_by_user_id` bigint(20) unsigned DEFAULT NULL COMMENT 'Teacher who verified the OMR detections.',
  `device_identifier` varchar(100) DEFAULT NULL COMMENT 'Mobile installation or device identifier.',
  `template_version` varchar(50) NOT NULL COMMENT 'OMR template version used.',
  `scanner_version` varchar(50) NOT NULL COMMENT 'Scanner or OpenCV version used.',
  `image_hash` char(64) DEFAULT NULL COMMENT 'SHA-256 image hash for duplicate detection and audit.',
  `scan_status` enum('captured','processing','needs_verification','verified','failed') NOT NULL DEFAULT 'captured' COMMENT 'Current scan workflow status.',
  `scanned_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Image-capture timestamp.',
  `verified_at` timestamp NULL DEFAULT NULL COMMENT 'Teacher-verification completion timestamp.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Record creation timestamp.',
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Last update timestamp.',
  PRIMARY KEY (`scan_session_id`),
  UNIQUE KEY `uk_scan_sessions_uuid` (`scan_uuid`),
  KEY `fk_scan_sessions_verified_by_user` (`verified_by_user_id`),
  KEY `idx_scan_sessions_user_status` (`scanned_by_user_id`,`scan_status`,`scanned_at`),
  CONSTRAINT `fk_scan_sessions_scanned_by_user` FOREIGN KEY (`scanned_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_scan_sessions_verified_by_user` FOREIGN KEY (`verified_by_user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Central audit record for each uploaded OMR scan.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `scan_sessions`
--

LOCK TABLES `scan_sessions` WRITE;
/*!40000 ALTER TABLE `scan_sessions` DISABLE KEYS */;
/*!40000 ALTER TABLE `scan_sessions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `school_profiles`
--

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

--
-- Dumping data for table `school_profiles`
--

LOCK TABLES `school_profiles` WRITE;
/*!40000 ALTER TABLE `school_profiles` DISABLE KEYS */;
/*!40000 ALTER TABLE `school_profiles` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sections`
--

DROP TABLE IF EXISTS `sections`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sections` (
  `section_id` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique section identifier.',
  `grade_level_id` int(10) unsigned NOT NULL COMMENT 'Grade level containing the section.',
  `section_name` varchar(50) NOT NULL COMMENT 'Official section name such as Rizal.',
  PRIMARY KEY (`section_id`),
  UNIQUE KEY `uk_sections_grade_name` (`grade_level_id`,`section_name`),
  CONSTRAINT `fk_sections_grade_level` FOREIGN KEY (`grade_level_id`) REFERENCES `grade_levels` (`grade_level_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Section master records independent of a specific academic year.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sections`
--

LOCK TABLES `sections` WRITE;
/*!40000 ALTER TABLE `sections` DISABLE KEYS */;
/*!40000 ALTER TABLE `sections` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `skills`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Competencies contextualized by term, grade level, and subject.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `skills`
--

LOCK TABLES `skills` WRITE;
/*!40000 ALTER TABLE `skills` DISABLE KEYS */;
/*!40000 ALTER TABLE `skills` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `statuses`
--

DROP TABLE IF EXISTS `statuses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `statuses` (
  `status_id` tinyint(3) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique account-status identifier.',
  `status_name` varchar(30) NOT NULL COMMENT 'Account status such as pending, active, rejected, or inactive.',
  `is_active` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Whether the status may be assigned.',
  PRIMARY KEY (`status_id`),
  UNIQUE KEY `uk_statuses_name` (`status_name`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='User-account lifecycle statuses.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `statuses`
--

LOCK TABLES `statuses` WRITE;
/*!40000 ALTER TABLE `statuses` DISABLE KEYS */;
INSERT INTO `statuses` VALUES (1,'pending',1),(2,'active',1),(3,'rejected',1),(4,'inactive',1),(5,'locked',1);
/*!40000 ALTER TABLE `statuses` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `student_answers`
--

DROP TABLE IF EXISTS `student_answers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `student_answers` (
  `student_answer_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique verified-answer identifier.',
  `test_result_id` bigint(20) unsigned NOT NULL COMMENT 'Overall learner attempt.',
  `question_id` bigint(20) unsigned NOT NULL COMMENT 'Question answered.',
  `verified_by_user_id` bigint(20) unsigned NOT NULL COMMENT 'Teacher who confirmed or corrected the answer.',
  `answer_uuid` char(36) NOT NULL COMMENT 'Offline-generated answer idempotency identifier.',
  `capture_source` enum('omr','teacher_correction','manual') NOT NULL COMMENT 'Source of the final verified answer.',
  `verified_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Answer-verification timestamp.',
  `selected_option` char(1) DEFAULT NULL COMMENT 'Final option confirmed by the teacher; null for blank or invalid answers.',
  `answer_status` enum('answered','blank','multiple','invalid') NOT NULL COMMENT 'Final verified answer condition.',
  `is_correct` tinyint(1) NOT NULL COMMENT 'Whether the selected option matched the answer key.',
  `points_earned` decimal(5,2) NOT NULL DEFAULT 0.00 COMMENT 'Points awarded for the question.',
  `correction_reason` varchar(255) DEFAULT NULL COMMENT 'Reason supplied when the teacher corrected the detected answer.',
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Last answer update timestamp.',
  PRIMARY KEY (`student_answer_id`),
  UNIQUE KEY `uk_student_answers_uuid` (`answer_uuid`),
  UNIQUE KEY `uk_student_answers_result_question` (`test_result_id`,`question_id`),
  KEY `fk_student_answers_question` (`question_id`),
  KEY `fk_student_answers_verified_by_user` (`verified_by_user_id`),
  CONSTRAINT `fk_student_answers_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`question_id`),
  CONSTRAINT `fk_student_answers_test_result` FOREIGN KEY (`test_result_id`) REFERENCES `test_results` (`test_result_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_student_answers_verified_by_user` FOREIGN KEY (`verified_by_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_student_answers_option` CHECK (`selected_option` is null or `selected_option` in ('A','B','C','D','E')),
  CONSTRAINT `chk_student_answers_points` CHECK (`points_earned` >= 0)
) ENGINE=InnoDB AUTO_INCREMENT=1003 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Final teacher-verified answer for every evaluated question.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `student_answers`
--

LOCK TABLES `student_answers` WRITE;
/*!40000 ALTER TABLE `student_answers` DISABLE KEYS */;
/*!40000 ALTER TABLE `student_answers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `students`
--

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
  `suffix` varchar(10) DEFAULT NULL COMMENT 'Optional name suffix.',
  `birth_date` date DEFAULT NULL COMMENT 'Birth date.',
  `status` enum('active','inactive','transferred','graduated') NOT NULL DEFAULT 'active' COMMENT 'Learner lifecycle status.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Record creation timestamp.',
  PRIMARY KEY (`student_id`),
  UNIQUE KEY `uk_students_lrn` (`student_lrn`),
  KEY `fk_students_address` (`address_id`),
  KEY `fk_students_gender` (`gender_id`),
  KEY `idx_students_school_name` (`school_id`,`last_name`,`first_name`),
  CONSTRAINT `fk_students_address` FOREIGN KEY (`address_id`) REFERENCES `addresses` (`address_id`),
  CONSTRAINT `fk_students_gender` FOREIGN KEY (`gender_id`) REFERENCES `genders` (`gender_id`),
  CONSTRAINT `fk_students_school` FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='School learner master records.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `students`
--

LOCK TABLES `students` WRITE;
/*!40000 ALTER TABLE `students` DISABLE KEYS */;
/*!40000 ALTER TABLE `students` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `subjects`
--

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

--
-- Dumping data for table `subjects`
--

LOCK TABLES `subjects` WRITE;
/*!40000 ALTER TABLE `subjects` DISABLE KEYS */;
INSERT INTO `subjects` VALUES (1,'ENG','English'),(2,'MATH','Mathematics'),(3,'SCI','Science'),(4,'FIL','Filipino'),(5,'AP','Araling Panlipunan'),(6,'MAPEH','MAPEH'),(7,'TLE','Technology and Livelihood Education'),(8,'VE','Values Education');
/*!40000 ALTER TABLE `subjects` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sync_items`
--

DROP TABLE IF EXISTS `sync_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sync_items` (
  `sync_item_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique item identifier within a synchronization batch.',
  `sync_id` bigint(20) unsigned NOT NULL COMMENT 'Parent synchronization batch.',
  `result_uuid` char(36) NOT NULL COMMENT 'Offline result identifier available before central insertion.',
  `test_result_id` bigint(20) unsigned DEFAULT NULL COMMENT 'Central result identifier after successful insertion or matching.',
  `sync_action` enum('create','update') NOT NULL COMMENT 'Requested result operation.',
  `sync_status` enum('pending','success','failed','skipped') NOT NULL DEFAULT 'pending' COMMENT 'Individual result-processing status.',
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
) ENGINE=InnoDB AUTO_INCREMENT=1033 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Individual learner results processed inside one synchronization batch.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sync_items`
--

LOCK TABLES `sync_items` WRITE;
/*!40000 ALTER TABLE `sync_items` DISABLE KEYS */;
/*!40000 ALTER TABLE `sync_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `syncs`
--

DROP TABLE IF EXISTS `syncs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `syncs` (
  `sync_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Central synchronization-batch identifier.',
  `sync_uuid` char(36) NOT NULL COMMENT 'Offline-generated idempotency identifier for the batch.',
  `user_id` bigint(20) unsigned NOT NULL COMMENT 'Teacher or user who initiated the synchronization.',
  `test_id` bigint(20) unsigned DEFAULT NULL COMMENT 'Assessment involved when the batch is assessment-specific.',
  `device_identifier` varchar(100) DEFAULT NULL COMMENT 'Mobile installation or device identifier.',
  `direction` enum('download','upload') NOT NULL COMMENT 'Direction of data transfer.',
  `sync_status` enum('pending','in_progress','partial_success','success','failed') NOT NULL DEFAULT 'pending' COMMENT 'Overall synchronization-batch status.',
  `payload_hash` char(64) DEFAULT NULL COMMENT 'Hash used for duplicate-payload detection.',
  `started_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Actual synchronization start time.',
  `completed_at` timestamp NULL DEFAULT NULL COMMENT 'Synchronization completion time.',
  `error_message` text DEFAULT NULL COMMENT 'Batch-level failure details.',
  PRIMARY KEY (`sync_id`),
  UNIQUE KEY `uk_syncs_uuid` (`sync_uuid`),
  KEY `fk_syncs_test` (`test_id`),
  KEY `idx_syncs_user_started` (`user_id`,`started_at`),
  KEY `idx_syncs_status_started` (`sync_status`,`started_at`),
  CONSTRAINT `fk_syncs_test` FOREIGN KEY (`test_id`) REFERENCES `tests` (`test_id`),
  CONSTRAINT `fk_syncs_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='One mobile synchronization batch containing zero or more result items.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `syncs`
--

LOCK TABLES `syncs` WRITE;
/*!40000 ALTER TABLE `syncs` DISABLE KEYS */;
/*!40000 ALTER TABLE `syncs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `term_periods`
--

DROP TABLE IF EXISTS `term_periods`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `term_periods` (
  `term_period_id` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique term-period identifier.',
  `academic_year_id` int(10) unsigned NOT NULL COMMENT 'Academic year containing the term.',
  `term_name` varchar(50) NOT NULL COMMENT 'Display name such as First Quarter.',
  `term_order` tinyint(3) unsigned NOT NULL COMMENT 'Chronological order within the academic year.',
  `status` enum('planned','active','completed') NOT NULL DEFAULT 'planned' COMMENT 'Term lifecycle status.',
  PRIMARY KEY (`term_period_id`),
  UNIQUE KEY `uk_term_periods_order` (`academic_year_id`,`term_order`),
  UNIQUE KEY `uk_term_periods_name` (`academic_year_id`,`term_name`),
  CONSTRAINT `fk_term_periods_academic_year` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_years` (`academic_year_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Academic-year terms or grading periods.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `term_periods`
--

LOCK TABLES `term_periods` WRITE;
/*!40000 ALTER TABLE `term_periods` DISABLE KEYS */;
/*!40000 ALTER TABLE `term_periods` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `test_parts`
--

DROP TABLE IF EXISTS `test_parts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `test_parts` (
  `test_part_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique assessment-part identifier.',
  `test_id` bigint(20) unsigned NOT NULL COMMENT 'Parent assessment.',
  `part_order` smallint(5) unsigned NOT NULL COMMENT 'Display and processing order.',
  `part_name` varchar(80) NOT NULL COMMENT 'Part label such as Part I.',
  `part_type` varchar(30) NOT NULL DEFAULT 'multiple_choice' COMMENT 'Question format; the initial OMR scope uses multiple choice.',
  `number_of_items` smallint(5) unsigned NOT NULL COMMENT 'Validated snapshot of questions in the part.',
  `points_per_item` decimal(5,2) NOT NULL DEFAULT 1.00 COMMENT 'Default points for each correct answer.',
  PRIMARY KEY (`test_part_id`),
  UNIQUE KEY `uk_test_parts_order` (`test_id`,`part_order`),
  CONSTRAINT `fk_test_parts_test` FOREIGN KEY (`test_id`) REFERENCES `tests` (`test_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_test_parts_number_of_items` CHECK (`number_of_items` > 0),
  CONSTRAINT `chk_test_parts_points_per_item` CHECK (`points_per_item` > 0)
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Ordered groups of questions inside an assessment.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `test_parts`
--

LOCK TABLES `test_parts` WRITE;
/*!40000 ALTER TABLE `test_parts` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_parts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `test_results`
--

DROP TABLE IF EXISTS `test_results`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `test_results` (
  `test_result_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Central verified-result identifier.',
  `result_uuid` char(36) NOT NULL COMMENT 'Offline-generated idempotency identifier.',
  `test_id` bigint(20) unsigned NOT NULL COMMENT 'Assessment taken.',
  `class_list_id` bigint(20) unsigned NOT NULL COMMENT 'Learner membership that took the assessment.',
  `scan_session_id` bigint(20) unsigned DEFAULT NULL COMMENT 'Verified OMR source; nullable for approved manual entry.',
  `attempt_number` smallint(5) unsigned NOT NULL DEFAULT 1 COMMENT 'Attempt sequence for the learner and assessment.',
  `total_score` decimal(8,2) NOT NULL COMMENT 'Sum of verified points earned.',
  `max_score` decimal(8,2) NOT NULL COMMENT 'Maximum possible points snapshot.',
  `items_evaluated` int(10) unsigned NOT NULL COMMENT 'Number of evaluated questions.',
  `checked_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Teacher verification or checking completion timestamp.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Record creation timestamp.',
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Last update timestamp.',
  PRIMARY KEY (`test_result_id`),
  UNIQUE KEY `uk_test_results_uuid` (`result_uuid`),
  UNIQUE KEY `uk_test_results_attempt` (`test_id`,`class_list_id`,`attempt_number`),
  UNIQUE KEY `uk_test_results_scan_session` (`scan_session_id`),
  KEY `fk_test_results_class_list` (`class_list_id`),
  CONSTRAINT `fk_test_results_class_list` FOREIGN KEY (`class_list_id`) REFERENCES `class_lists` (`class_list_id`),
  CONSTRAINT `fk_test_results_scan_session` FOREIGN KEY (`scan_session_id`) REFERENCES `scan_sessions` (`scan_session_id`),
  CONSTRAINT `fk_test_results_test` FOREIGN KEY (`test_id`) REFERENCES `tests` (`test_id`),
  CONSTRAINT `chk_test_results_scores` CHECK (`total_score` >= 0 and `max_score` >= 0 and `total_score` <= `max_score`)
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Verified overall learner attempts from OMR or approved manual capture.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `test_results`
--

LOCK TABLES `test_results` WRITE;
/*!40000 ALTER TABLE `test_results` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_results` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tests`
--

DROP TABLE IF EXISTS `tests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tests` (
  `test_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Unique assessment identifier.',
  `class_assignment_id` bigint(20) unsigned NOT NULL COMMENT 'Teacher, subject, and class assignment that owns the assessment.',
  `term_period_id` int(10) unsigned NOT NULL COMMENT 'Term period of the assessment.',
  `test_name` varchar(120) NOT NULL COMMENT 'Assessment title.',
  `test_type` enum('quiz','exam','diagnostic','long_test','other') NOT NULL COMMENT 'Assessment category.',
  `test_date` date NOT NULL COMMENT 'Scheduled or administered date.',
  `instructions` text DEFAULT NULL COMMENT 'General assessment instructions.',
  `total_items` int(10) unsigned NOT NULL DEFAULT 0 COMMENT 'Validated snapshot of the total question count.',
  `status` enum('draft','active','completed','archived') NOT NULL DEFAULT 'draft' COMMENT 'Assessment lifecycle status.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Record creation timestamp.',
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'Last update timestamp.',
  PRIMARY KEY (`test_id`),
  KEY `fk_tests_term_period` (`term_period_id`),
  KEY `idx_tests_assignment_term` (`class_assignment_id`,`term_period_id`,`status`),
  CONSTRAINT `fk_tests_class_assignment` FOREIGN KEY (`class_assignment_id`) REFERENCES `class_assignments` (`class_assignment_id`),
  CONSTRAINT `fk_tests_term_period` FOREIGN KEY (`term_period_id`) REFERENCES `term_periods` (`term_period_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Assessments owned by a teacher-class-subject assignment.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tests`
--

LOCK TABLES `tests` WRITE;
/*!40000 ALTER TABLE `tests` DISABLE KEYS */;
/*!40000 ALTER TABLE `tests` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

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
  `suffix` varchar(10) DEFAULT NULL COMMENT 'Optional suffix such as Jr. or III.',
  `birth_date` date DEFAULT NULL COMMENT 'Birth date used to derive age.',
  `teaching_start_date` date DEFAULT NULL COMMENT 'Date used to derive years of teaching experience.',
  `email` varchar(120) NOT NULL COMMENT 'Unique login email.',
  `contact_number` varchar(20) NOT NULL COMMENT 'Unique primary contact number.',
  `password_hash` varchar(255) NOT NULL COMMENT 'Secure password hash; never stores a plain-text password.',
  `failed_login_count` smallint(5) unsigned NOT NULL DEFAULT 0 COMMENT 'Consecutive failed-login counter.',
  `locked_until_at` timestamp NULL DEFAULT NULL COMMENT 'Account lock expiration timestamp.',
  `last_login_at` timestamp NULL DEFAULT NULL COMMENT 'Most recent successful-login timestamp.',
  `password_changed_at` timestamp NULL DEFAULT NULL COMMENT 'Most recent password-change timestamp.',
  `email_verified_at` timestamp NULL DEFAULT NULL COMMENT 'Email-verification timestamp.',
  `contact_verified_at` timestamp NULL DEFAULT NULL COMMENT 'Contact-number verification timestamp.',
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
  CONSTRAINT `fk_users_address` FOREIGN KEY (`address_id`) REFERENCES `addresses` (`address_id`),
  CONSTRAINT `fk_users_educational_attainment` FOREIGN KEY (`educational_attainment_id`) REFERENCES `educational_attainments` (`educational_attainment_id`),
  CONSTRAINT `fk_users_gender` FOREIGN KEY (`gender_id`) REFERENCES `genders` (`gender_id`),
  CONSTRAINT `fk_users_major` FOREIGN KEY (`major_id`) REFERENCES `majors` (`major_id`),
  CONSTRAINT `fk_users_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`role_id`),
  CONSTRAINT `fk_users_school` FOREIGN KEY (`school_id`) REFERENCES `school_profiles` (`school_id`),
  CONSTRAINT `fk_users_status` FOREIGN KEY (`status_id`) REFERENCES `statuses` (`status_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1003 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Principal and teacher accounts, identity, security, and profile information.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-09 11:35:25
