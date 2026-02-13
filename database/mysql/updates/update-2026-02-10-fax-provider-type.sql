-- Add provider transport selection for fax configuration.
-- This script is idempotent and can be run multiple times safely.

-- Check if column exists before adding
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists 
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 'fax_config' 
  AND COLUMN_NAME = 'providerType';

-- Add column only if it doesn't exist (positioned after 'download' column for schema consistency)
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `fax_config` ADD COLUMN `providerType` varchar(25) DEFAULT ''MIDDLEWARE'' AFTER `download`',
    'SELECT ''Column providerType already exists in fax_config'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Backfill existing rows to preserve current middleware behavior.
-- This is safe to run multiple times
UPDATE `fax_config`
SET `providerType` = 'MIDDLEWARE'
WHERE `providerType` IS NULL OR `providerType` = '';
