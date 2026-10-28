-- V3_027: SET-5 school-year lifecycle (final-defense revisions, October 2026).
-- Completed (the year ended, a one-month grace period for late work) then Archived (principal
-- confirms; hidden from normal lists). Ready-to-archive is computed from the dates, not stored.
-- No new table and no count change: the startup guard stays 80 / 202 / 119.
-- TiDB only allows new ENUM values at the END of the list.

ALTER TABLE academic_years
    MODIFY COLUMN status ENUM('planned', 'active', 'completed', 'archived') NOT NULL DEFAULT 'planned';

SHOW COLUMNS FROM academic_years LIKE 'status';
