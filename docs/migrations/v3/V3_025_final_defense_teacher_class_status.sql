-- V3_025: final-defense revisions (October 2026) for teacher and class-assignment status.
-- No new table. Take a backup first; apply only to an explicitly selected V3 database.
-- Ships together with app.v3.baseline.expected-hardening-column-count = 18.
--
-- REG-2  users.contract_end_date: last day the teacher's contract is valid (approval + 3 years).
-- TCH-4  users.archived_at: when the principal archived the teacher (TCH-8 counts 10 years from it).
-- TCH-3  statuses 'archived': account status is Active / Inactive / Archived.
-- CLS-7  class_assignments.status 'inactive': a paused assignment. TiDB only allows new ENUM
--        values at the END of the list, so 'inactive' is appended after 'archived'.

ALTER TABLE users ADD COLUMN contract_end_date DATE NULL AFTER contact_verified_at;
ALTER TABLE users ADD COLUMN archived_at TIMESTAMP NULL DEFAULT NULL AFTER contract_end_date;

INSERT INTO statuses (status_name, is_active)
SELECT 'archived', TRUE
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM statuses WHERE status_name = 'archived');

ALTER TABLE class_assignments
    MODIFY COLUMN status ENUM('active', 'completed', 'archived', 'inactive') NOT NULL DEFAULT 'active';

-- REG-2: teachers approved before this change get approval date + 3 years
-- (the account creation date when no approval was recorded).
UPDATE users u
  JOIN roles r ON r.role_id = u.role_id AND r.role_name = 'teacher'
  JOIN statuses s ON s.status_id = u.status_id AND s.status_name IN ('active', 'inactive', 'locked')
   SET u.contract_end_date = DATE_ADD(DATE(COALESCE(
           (SELECT MAX(a.created_at)
              FROM audit_logs a
             WHERE a.action = 'teacher.approve'
               AND a.entity_type = 'users'
               AND a.entity_id = CAST(u.user_id AS CHAR)
               AND a.outcome = 'success'),
           u.created_at)), INTERVAL 3 YEAR)
 WHERE u.contract_end_date IS NULL;

-- Verify: expect 2, 1, the new ENUM, and no approved teacher without a contract end.
SELECT COUNT(*) AS new_user_columns
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'users'
   AND column_name IN ('contract_end_date', 'archived_at');
SELECT COUNT(*) AS archived_status_rows FROM statuses WHERE status_name = 'archived';
SHOW COLUMNS FROM class_assignments LIKE 'status';
SELECT COUNT(*) AS approved_teachers_without_contract_end
  FROM users u
  JOIN roles r ON r.role_id = u.role_id AND r.role_name = 'teacher'
  JOIN statuses s ON s.status_id = u.status_id AND s.status_name IN ('active', 'inactive', 'locked')
 WHERE u.contract_end_date IS NULL;
