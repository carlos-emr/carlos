-- Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
-- Durable replay protection. Clinical prose remains in the clinician-approved target record.
CREATE TABLE IF NOT EXISTS clinical_chart_update_receipt (
    proposal_key CHAR(64) NOT NULL,
    demographic_no INT NOT NULL,
    document_no INT NOT NULL,
    provider_no VARCHAR(20) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    source_hash CHAR(64) NOT NULL,
    accepted_at DATETIME NOT NULL,
    PRIMARY KEY (proposal_key),
    KEY chart_update_patient (demographic_no, accepted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
