-- Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
-- Durable replay protection. Clinical prose remains in the clinician-approved target record.
-- Pin the repo's utf8mb4_general_ci: left unstated, newer MariaDB (e.g. 11.8) picks utf8mb4_uca1400_ai_ci,
-- and joining provider_no to other tables then fails with "Illegal mix of collations" (1267).
-- The two SHA-256 hex columns are exact-match identifiers, so they compare case-sensitively.
CREATE TABLE IF NOT EXISTS clinical_chart_update_receipt (
    proposal_key CHAR(64) COLLATE utf8mb4_bin NOT NULL,
    demographic_no INT NOT NULL,
    document_no INT NOT NULL,
    provider_no VARCHAR(20) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    source_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL,
    accepted_at DATETIME NOT NULL,
    PRIMARY KEY (proposal_key),
    KEY chart_update_patient (demographic_no, accepted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
