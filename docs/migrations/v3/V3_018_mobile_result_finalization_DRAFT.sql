-- DRAFT after V3_017. Reviewed deployment only; no automatic startup migration.
-- One first-finalization receipt per result. Reopen/supersede require a future audited history contract.
CREATE TABLE mobile_result_finalizations (
    test_result_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    mobile_revision BIGINT UNSIGNED NOT NULL,
    score_version INT UNSIGNED NOT NULL,
    response_json LONGTEXT NOT NULL,
    finalized_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mobile_result_finalization_result FOREIGN KEY (test_result_id) REFERENCES test_results(test_result_id),
    CONSTRAINT chk_mobile_result_finalization_revision CHECK (mobile_revision BETWEEN 2 AND 9007199254740991),
    CONSTRAINT chk_mobile_result_finalization_version CHECK (score_version >= 1),
    CONSTRAINT chk_mobile_result_finalization_json CHECK (JSON_VALID(response_json))
) ENGINE=InnoDB;
