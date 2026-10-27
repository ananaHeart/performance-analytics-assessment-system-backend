-- V3_026: REG-3 / CLS-9 teacher_subjects (final-defense revisions, October 2026).
-- The one new table: a teacher's optional additional majors and subjects taught before.
-- The main major stays in users.major_id (majors and subjects share the same IDs).
-- Same as PART B of deploy/tidb-v3/03_final_defense_revisions.sql.
-- Ships together with the startup guard 80 tables / 202 foreign keys / 119 unique constraints.

CREATE TABLE IF NOT EXISTS teacher_subjects (
    teacher_subject_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    subject_id INT UNSIGNED NOT NULL,
    relation ENUM('additional_major', 'taught_before') NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (teacher_subject_id),
    UNIQUE KEY uk_teacher_subjects_user_subject_relation (user_id, subject_id, relation),
    KEY idx_teacher_subjects_subject_relation (subject_id, relation),
    CONSTRAINT fk_teacher_subjects_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_teacher_subjects_subject FOREIGN KEY (subject_id) REFERENCES subjects (subject_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

-- Verify: expect 80 tables, 202 foreign keys, 119 unique constraints.
SELECT
    (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE') AS tables_total,
    (SELECT COUNT(*) FROM information_schema.table_constraints
      WHERE constraint_schema = DATABASE() AND constraint_type = 'FOREIGN KEY') AS foreign_keys_total,
    (SELECT COUNT(*) FROM information_schema.table_constraints
      WHERE constraint_schema = DATABASE() AND constraint_type = 'UNIQUE') AS unique_total;
