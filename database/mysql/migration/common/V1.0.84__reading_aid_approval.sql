-- Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
-- A clinician's recorded approval of one AI summary of one document (carlos#5537), kept so a
-- reading aid can be sent after the review session has ended. approval_id is an opaque random
-- reference, never derived from the reviewer; it is the only part of this row that reaches the
-- patient's device. Times are Unix seconds, as the delivery contract's claims.
-- Pin the repo's utf8mb4_general_ci: left unstated, newer MariaDB (e.g. 11.8) picks
-- utf8mb4_uca1400_ai_ci, and joining provider_no to other tables then fails with "Illegal mix of
-- collations" (1267). The id and SHA-256 hex columns are exact-match identifiers, so they compare
-- case-sensitively.
CREATE TABLE IF NOT EXISTS clinical_reading_aid_approval (
    approval_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    document_no INT NOT NULL,
    demographic_no INT NOT NULL,
    summary_version CHAR(64) COLLATE utf8mb4_bin NOT NULL,
    source_sha256 CHAR(64) COLLATE utf8mb4_bin NOT NULL,
    notice_version VARCHAR(64) NOT NULL,
    language VARCHAR(16) NOT NULL,
    reviewer_provider_no VARCHAR(20) NOT NULL,
    reviewed_at_epoch BIGINT NOT NULL,
    generated_at_epoch BIGINT NOT NULL,
    revised_at_epoch BIGINT NULL,
    PRIMARY KEY (approval_id),
    UNIQUE KEY reading_aid_approval_document_version (document_no, summary_version),
    KEY reading_aid_approval_patient (demographic_no, reviewed_at_epoch)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
