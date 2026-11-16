-- Both release/2026.08 (V1.0.59.30) and develop (V1.0.77) establish the same live
-- consent key. The release migration creates its projection as a visible column;
-- develop's ADD COLUMN IF NOT EXISTS cannot hide a column that already exists.
-- Preserve the generated expression and unique key, and apply develop's invisible
-- projection contract after either upgrade history. No consent rows are changed.
ALTER TABLE `Consent`
    MODIFY COLUMN `live_demographic_no` INT(10)
        AS (IF(`deleted` = 0, `demographic_no`, NULL)) PERSISTENT INVISIBLE
        COMMENT 'demographic_no while the row is live, NULL once deleted; backs uq_consent_live_type';
