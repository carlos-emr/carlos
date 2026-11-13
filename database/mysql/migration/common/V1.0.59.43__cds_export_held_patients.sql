-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- Export-owned record of selected patients held back from a package; no clinical row changes.
-- Unreleased forward migration for release/2026.08.
--
-- A patient whose chart this release cannot represent completely is left out of the package
-- and named here with a safe reason code, so authorized export operators can see exactly which
-- charts need attention. The encrypted package ReadMe lists the same patients for the recipient.
CREATE TABLE IF NOT EXISTS cds_export_held_patient (
    id VARCHAR(36) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    epoch BIGINT NOT NULL,
    demographic_no INT NOT NULL,
    safe_code VARCHAR(96) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cds_export_held_patient (run_id, epoch, demographic_no),
    CONSTRAINT fk_cds_export_held_patient_run FOREIGN KEY (run_id) REFERENCES cds_export_run(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
