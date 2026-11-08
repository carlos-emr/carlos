-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- Export-owned attempt/disclosure lifecycle only; no clinical or importer row changes.
CREATE TABLE cds_export_attempt (
    id VARCHAR(36) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    epoch BIGINT NOT NULL,
    request_digest VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    context_document LONGTEXT NULL,
    context_digest VARCHAR(64) NULL,
    download_token VARCHAR(36) NULL,
    download_until DATETIME(6) NULL,
    download_operator VARCHAR(128) NULL,
    cleanup_token VARCHAR(36) NULL,
    cleanup_until DATETIME(6) NULL,
    safe_code VARCHAR(96) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revision BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cds_export_attempt (run_id, epoch),
    KEY idx_cds_export_attempt_expiry (state, expires_at, id),
    CONSTRAINT fk_cds_export_attempt_run FOREIGN KEY (run_id) REFERENCES cds_export_run(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Earlier metadata-only builds could create fenced staging directories but could not
-- publish qualified clinical packages. Preserve their original run/audit rows and
-- track the last epoch solely for bounded retention; never promote legacy evidence.
INSERT INTO cds_export_attempt
    (id, run_id, epoch, request_digest, state, created_at, updated_at, expires_at, revision)
SELECT UUID(), id, owner_epoch, request_digest, 'LEGACY_UNVERIFIED', created_at, updated_at,
    COALESCE(expires_at, DATE_ADD(updated_at, INTERVAL 7 DAY)), 0
FROM cds_export_run
WHERE owner_epoch > 0;
