-- Written in the same transaction as prescription/drugs and authoritative audits.
-- Deliberately retained across session expiry and application restarts; no clinical text is stored here.
CREATE TABLE IF NOT EXISTS rx_submission_receipt (
    submission_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_no VARCHAR(6) NOT NULL,
    demographic_no INT NOT NULL,
    prescription_id INT DEFAULT NULL,
    outcome VARCHAR(7) NOT NULL,
    attempt_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    CONSTRAINT rx_submission_receipt_outcome CHECK (
        (outcome = 'SAVED' AND prescription_id IS NOT NULL) OR
        (outcome IN ('PENDING','FAILED') AND prescription_id IS NULL)),
    lastUpdateUser VARCHAR(100) NOT NULL DEFAULT 'system',
    lastUpdateDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (submission_hash, provider_no, demographic_no),
    KEY rx_submission_receipt_prescription (prescription_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
