-- V3_027: SET-5 school-year lifecycle (final-defense revisions, October 2026).
-- Completed (the year ended, a one-month grace period for late work) then Archived (principal
-- confirms; hidden from normal lists). Ready-to-archive is computed from the dates, not stored.
-- academic_years.status cannot change on TiDB (the generated active_school_id depends on it),
-- so archiving is recorded in archived_at; the API reports such a year as status "archived".
-- No new table and no count change: the startup guard stays 80 / 202 / 119.

ALTER TABLE academic_years ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP NULL DEFAULT NULL AFTER status;

SHOW COLUMNS FROM academic_years LIKE 'archived_at';
