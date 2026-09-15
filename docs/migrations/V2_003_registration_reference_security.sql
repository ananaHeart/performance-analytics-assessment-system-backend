-- V2_003 registration reference security
-- Adds preloaded suffix reference values for teacher registration dropdowns.

USE performance_assessment_v2_db;

CREATE TABLE IF NOT EXISTS suffixes (
    suffix_id TINYINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Unique suffix identifier.',
    suffix_name VARCHAR(10) NOT NULL COMMENT 'Name suffix display value such as Jr. or III.',
    display_order TINYINT UNSIGNED NOT NULL COMMENT 'Dropdown display order.',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Whether the suffix may be selected.',
    CONSTRAINT pk_suffixes PRIMARY KEY (suffix_id),
    CONSTRAINT uk_suffixes_name UNIQUE (suffix_name),
    CONSTRAINT uk_suffixes_order UNIQUE (display_order)
) COMMENT='Optional teacher and learner name suffix reference values.';

INSERT INTO suffixes (
    suffix_id,
    suffix_name,
    display_order,
    is_active
) VALUES
    (1, 'Jr.', 1, TRUE),
    (2, 'Sr.', 2, TRUE),
    (3, 'II', 3, TRUE),
    (4, 'III', 4, TRUE),
    (5, 'IV', 5, TRUE)
ON DUPLICATE KEY UPDATE
    suffix_name = VALUES(suffix_name),
    display_order = VALUES(display_order),
    is_active = VALUES(is_active);
