-- Performance Assessment System V2
-- Migration: audit-ready OMR recapture retention
-- Approved: August 9, 2026
-- Scope: separate local performance_assessment_v2_db only
-- Do not run against V1 or TiDB until the V2 migration gate is approved.

USE performance_assessment_v2_db;

CREATE TABLE test_result_scans (
    test_result_scan_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique result-to-scan audit-link identifier.',
    test_result_id BIGINT UNSIGNED NOT NULL COMMENT 'Verified learner result associated with the capture.',
    scan_session_id BIGINT UNSIGNED NOT NULL COMMENT 'Captured OMR scan retained for the result audit trail.',
    link_status ENUM('selected', 'superseded', 'rejected') NOT NULL COMMENT 'Whether this capture is authoritative, replaced, or rejected.',
    selected_result_id BIGINT UNSIGNED AS (
        CASE WHEN link_status = 'selected' THEN test_result_id ELSE NULL END
    ) VIRTUAL COMMENT 'Generated key used to enforce one selected scan per result.',
    decided_by_user_id BIGINT UNSIGNED NOT NULL COMMENT 'Teacher who selected, superseded, or rejected the capture.',
    decision_reason VARCHAR(255) NULL COMMENT 'Reason for superseding or rejecting the capture.',
    linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Timestamp when the capture was linked to the result.',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last disposition update timestamp.',
    CONSTRAINT pk_test_result_scans PRIMARY KEY (test_result_scan_id),
    CONSTRAINT uk_test_result_scans_scan UNIQUE (scan_session_id),
    CONSTRAINT uk_test_result_scans_result_scan UNIQUE (test_result_id, scan_session_id),
    CONSTRAINT uk_test_result_scans_one_selected UNIQUE (selected_result_id),
    CONSTRAINT fk_test_result_scans_test_result
        FOREIGN KEY (test_result_id) REFERENCES test_results (test_result_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_test_result_scans_scan_session
        FOREIGN KEY (scan_session_id) REFERENCES scan_sessions (scan_session_id),
    CONSTRAINT fk_test_result_scans_decided_by_user
        FOREIGN KEY (decided_by_user_id) REFERENCES users (user_id),
    INDEX idx_test_result_scans_result_status (test_result_id, link_status)
) COMMENT='Audit-ready history of selected, superseded, and rejected scans for each learner result.';

-- Preserve any existing direct scan link as the initially selected capture.
INSERT INTO test_result_scans (
    test_result_id,
    scan_session_id,
    link_status,
    decided_by_user_id,
    decision_reason,
    linked_at
)
SELECT
    tr.test_result_id,
    tr.scan_session_id,
    'selected',
    COALESCE(ss.verified_by_user_id, ss.scanned_by_user_id),
    'Migrated from the former direct test_results.scan_session_id link.',
    COALESCE(ss.verified_at, tr.checked_at)
FROM test_results tr
JOIN scan_sessions ss ON ss.scan_session_id = tr.scan_session_id
WHERE tr.scan_session_id IS NOT NULL;

ALTER TABLE test_results
    DROP FOREIGN KEY fk_test_results_scan_session;

ALTER TABLE test_results
    DROP INDEX uk_test_results_scan_session;

ALTER TABLE test_results
    DROP COLUMN scan_session_id;

-- Expected after migration:
-- 1. test_results has no scan_session_id.
-- 2. Every migrated non-null former link has one selected test_result_scans row.
-- 3. UNIQUE(selected_result_id) permits at most one selected scan per result.
SELECT COUNT(*) AS test_result_scan_rows FROM test_result_scans;
SHOW COLUMNS FROM test_results;
SHOW INDEX FROM test_result_scans;
