-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- Independent exporter metadata; no importer or clinical row is rewritten.
-- Unreleased version follows the current develop high-water mark V1.0.80.
-- Apply after the importer release has been forward-integrated into the target branch.

CREATE TABLE IF NOT EXISTS cds_export_run (
    id VARCHAR(36) NOT NULL,
    submission_key VARCHAR(128) NOT NULL,
    operator VARCHAR(128) NOT NULL,
    provider_no VARCHAR(16) NOT NULL,
    request_document LONGTEXT NOT NULL,
    request_digest VARCHAR(64) NOT NULL,
    compatibility_digest VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    owner_epoch BIGINT NOT NULL,
    owner_token VARCHAR(36) NULL,
    lease_until DATETIME(6) NULL,
    cancel_requested BOOLEAN NOT NULL,
    expected_patients BIGINT NULL,
    processed_patients BIGINT NOT NULL,
    blocked_patients BIGINT NOT NULL,
    cohort_member VARCHAR(64) NULL,
    cohort_bytes BIGINT NULL,
    cohort_digest VARCHAR(64) NULL,
    selected_at DATETIME(6) NULL,
    artifact_member VARCHAR(64) NULL,
    artifact_digest VARCHAR(64) NULL,
    artifact_bytes BIGINT NULL,
    expires_at DATETIME(6) NULL,
    safe_code VARCHAR(96) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    revision BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cds_export_submission (operator, submission_key),
    KEY idx_cds_export_pending (state, lease_until, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS cds_export_event (
    id VARCHAR(36) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    epoch BIGINT NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    operator VARCHAR(128) NOT NULL,
    safe_code VARCHAR(96) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_cds_export_event_run (run_id, created_at, id),
    CONSTRAINT fk_cds_export_event_run FOREIGN KEY (run_id) REFERENCES cds_export_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Bulk export authority is independent of import and ordinary chart editing.
INSERT INTO secObjectName (objectName,description,orgapplicable)
SELECT '_cds_export','Run approved clinical data exports',0
WHERE NOT EXISTS (SELECT 1 FROM secObjectName WHERE objectName='_cds_export');
