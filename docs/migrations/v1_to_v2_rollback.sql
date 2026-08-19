-- V2 rollback guide.
-- The preferred rollback is switching the backend connection profile back to V1.
-- This file performs verification only. The cleanup block is intentionally commented.

SELECT DATABASE() AS connected_database;

SELECT table_name, table_rows
FROM information_schema.tables
WHERE table_schema = 'performance_assessment_v2_db'
ORDER BY table_name;

SELECT 'v2_operational_counts' AS report,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.users) AS users,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.students) AS students,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.tests) AS tests,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.test_results) AS results,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.student_answers) AS answers,
       (SELECT COUNT(*) FROM performance_assessment_v2_db.syncs) AS sync_batches;

-- DESTRUCTIVE TARGET-ONLY CLEANUP FOR A CONFIRMED DISPOSABLE V2 DATABASE.
-- Do not uncomment against V1 or production TiDB.
-- SET FOREIGN_KEY_CHECKS = 0;
-- TRUNCATE TABLE performance_assessment_v2_db.audit_logs;
-- TRUNCATE TABLE performance_assessment_v2_db.login_attempts;
-- TRUNCATE TABLE performance_assessment_v2_db.auth_sessions;
-- TRUNCATE TABLE performance_assessment_v2_db.omr_detections;
-- TRUNCATE TABLE performance_assessment_v2_db.sync_items;
-- TRUNCATE TABLE performance_assessment_v2_db.syncs;
-- TRUNCATE TABLE performance_assessment_v2_db.intervention_results;
-- TRUNCATE TABLE performance_assessment_v2_db.student_answers;
-- TRUNCATE TABLE performance_assessment_v2_db.test_results;
-- TRUNCATE TABLE performance_assessment_v2_db.scan_sessions;
-- TRUNCATE TABLE performance_assessment_v2_db.mappings;
-- TRUNCATE TABLE performance_assessment_v2_db.answer_keys;
-- TRUNCATE TABLE performance_assessment_v2_db.questions;
-- TRUNCATE TABLE performance_assessment_v2_db.test_parts;
-- TRUNCATE TABLE performance_assessment_v2_db.tests;
-- TRUNCATE TABLE performance_assessment_v2_db.interventions;
-- TRUNCATE TABLE performance_assessment_v2_db.skills;
-- TRUNCATE TABLE performance_assessment_v2_db.competency_tags;
-- TRUNCATE TABLE performance_assessment_v2_db.root_tags;
-- TRUNCATE TABLE performance_assessment_v2_db.class_lists;
-- TRUNCATE TABLE performance_assessment_v2_db.class_assignments;
-- TRUNCATE TABLE performance_assessment_v2_db.classes;
-- TRUNCATE TABLE performance_assessment_v2_db.students;
-- TRUNCATE TABLE performance_assessment_v2_db.sections;
-- TRUNCATE TABLE performance_assessment_v2_db.term_periods;
-- TRUNCATE TABLE performance_assessment_v2_db.academic_years;
-- TRUNCATE TABLE performance_assessment_v2_db.users;
-- TRUNCATE TABLE performance_assessment_v2_db.school_profiles;
-- TRUNCATE TABLE performance_assessment_v2_db.addresses;
-- SET FOREIGN_KEY_CHECKS = 1;

-- After rollback, verify that the backend profile points to V1 and rerun the V1 smoke tests.
