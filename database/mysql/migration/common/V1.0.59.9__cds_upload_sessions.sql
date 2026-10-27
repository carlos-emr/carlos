-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- Uploads are administrative staging, never evidence that clinical import committed.
CREATE TABLE cds_import_upload (
    id VARCHAR(36) NOT NULL,
    operator_key VARCHAR(128) NOT NULL,
    source_name VARCHAR(1000) NOT NULL,
    media_kind VARCHAR(8) NOT NULL,
    expected_bytes BIGINT NOT NULL,
    received_bytes BIGINT NOT NULL DEFAULT 0,
    chunk_count BIGINT NOT NULL DEFAULT 0,
    manifest_digest CHAR(64) NULL,
    snapshot_digest CHAR(64) NULL,
    state VARCHAR(24) NOT NULL,
    submission_key VARCHAR(128) NOT NULL,
    run_id VARCHAR(36) NULL,
    dry_run_id VARCHAR(36) NULL,
    quota_released BIT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_cds_upload_owner_id (operator_key, id),
    KEY idx_cds_upload_expiry (state, updated_at),
    UNIQUE KEY idx_cds_upload_actual_run (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO cds_import_admission (id, owner_run_id, reserved_bytes)
SELECT 'UPLOAD_BUDGET', NULL, 0
WHERE NOT EXISTS (SELECT 1 FROM cds_import_admission WHERE id='UPLOAD_BUDGET');
