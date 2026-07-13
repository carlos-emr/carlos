-- Expand billingmaster.oin_postalcode from varchar(6) to varchar(7).
-- Canadian postal codes use the format "A1B 2C3" (with a space), which is
-- 7 characters. The previous varchar(6) limit caused truncation errors when
-- creating BC private billing for patients whose postal code was stored with
-- the standard space separator.
-- Reference: OSCAR 19 commits 3fd4e7f066, 5f889292ca, ec606eda68.
-- Idempotent: safe to run multiple times (no-op on non-BC installs that lack
-- the billingmaster table, and no-op on re-runs after column is already varchar(7)).

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'billingmaster'
  AND COLUMN_NAME = 'oin_postalcode';

SET @sql = IF(@col_exists > 0,
    'ALTER TABLE billingmaster MODIFY oin_postalcode varchar(7) DEFAULT ''''',
    'SELECT ''billingmaster.oin_postalcode not found, skipping'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
