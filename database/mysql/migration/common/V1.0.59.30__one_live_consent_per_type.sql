-- Align the target with the OSCAR importer: keep one deciding live record,
-- retain all history, and never turn an unknown flag into an opt-in.
-- Prior flags are recorded before normalization/retirement. No rows are deleted.
CREATE TABLE IF NOT EXISTS Consent_migration_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    consent_id INT NOT NULL,
    migration VARCHAR(64) NOT NULL,
    reason VARCHAR(32) NOT NULL,
    prior_explicit TINYINT(1) NULL,
    prior_optout TINYINT(1) NULL,
    prior_deleted TINYINT(1) NULL,
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_consent_migration_reason (consent_id, migration, reason)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO Consent_migration_audit
    (consent_id, migration, reason, prior_explicit, prior_optout, prior_deleted)
SELECT c.id, 'one_live_consent_per_type', 'null_flag', c.explicit, c.optout, c.deleted
FROM Consent c
WHERE (c.explicit IS NULL OR c.optout IS NULL OR c.deleted IS NULL)
  AND NOT EXISTS (SELECT 1 FROM Consent_migration_audit a
                  WHERE a.consent_id = c.id AND a.migration = 'one_live_consent_per_type'
                    AND a.reason = 'null_flag');

UPDATE Consent
SET deleted = CASE WHEN deleted IS NULL OR optout IS NULL THEN 1 ELSE deleted END,
    explicit = IFNULL(explicit, 0),
    optout = IFNULL(optout, 1);

INSERT INTO Consent_migration_audit
    (consent_id, migration, reason, prior_explicit, prior_optout, prior_deleted)
SELECT c.id, 'one_live_consent_per_type', 'duplicate_retired', c.explicit, c.optout, c.deleted
FROM Consent c
JOIN (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY demographic_no, consent_type_id
        ORDER BY `optout` DESC, IFNULL(`explicit`, 0) DESC, (NULLIF(`edit_date`, '0000-00-00 00:00:00') IS NULL), NULLIF(`edit_date`, '0000-00-00 00:00:00') DESC, `id` DESC
    ) AS rn
    FROM Consent
    WHERE deleted = 0 AND demographic_no IS NOT NULL AND consent_type_id IS NOT NULL
) ranked ON ranked.id = c.id
WHERE ranked.rn > 1
  AND NOT EXISTS (SELECT 1 FROM Consent_migration_audit a
                  WHERE a.consent_id = c.id AND a.migration = 'one_live_consent_per_type'
                    AND a.reason = 'duplicate_retired');

UPDATE Consent c
JOIN (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY demographic_no, consent_type_id
        ORDER BY `optout` DESC, IFNULL(`explicit`, 0) DESC, (NULLIF(`edit_date`, '0000-00-00 00:00:00') IS NULL), NULLIF(`edit_date`, '0000-00-00 00:00:00') DESC, `id` DESC
    ) AS rn
    FROM Consent
    WHERE deleted = 0 AND demographic_no IS NOT NULL AND consent_type_id IS NOT NULL
) ranked ON ranked.id = c.id
SET c.deleted = 1
WHERE ranked.rn > 1;

ALTER TABLE Consent
    MODIFY COLUMN explicit TINYINT(1) NOT NULL DEFAULT 0,
    MODIFY COLUMN optout TINYINT(1) NOT NULL DEFAULT 1,
    MODIFY COLUMN deleted TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS live_demographic_no INT GENERATED ALWAYS AS (IF(deleted = 0, demographic_no, NULL)) STORED,
    ADD UNIQUE KEY IF NOT EXISTS uq_consent_live_type (live_demographic_no, consent_type_id);
