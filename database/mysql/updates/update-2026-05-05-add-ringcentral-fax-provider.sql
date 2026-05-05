-- Add RingCentral provider-specific fax configuration fields.
-- This script is idempotent and can be run multiple times safely.

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'fax_config'
  AND COLUMN_NAME = 'rc_client_id';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `fax_config` ADD COLUMN `rc_client_id` varchar(128) DEFAULT NULL AFTER `providerType`',
    'SELECT ''Column rc_client_id already exists in fax_config'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'fax_config'
  AND COLUMN_NAME = 'rc_client_secret';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `fax_config` ADD COLUMN `rc_client_secret` text DEFAULT NULL AFTER `rc_client_id`',
    'SELECT ''Column rc_client_secret already exists in fax_config'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'fax_config'
  AND COLUMN_NAME = 'rc_jwt_token';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `fax_config` ADD COLUMN `rc_jwt_token` text DEFAULT NULL AFTER `rc_client_secret`',
    'SELECT ''Column rc_jwt_token already exists in fax_config'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'fax_config'
  AND COLUMN_NAME = 'rc_account_id';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `fax_config` ADD COLUMN `rc_account_id` varchar(64) DEFAULT NULL AFTER `rc_jwt_token`',
    'SELECT ''Column rc_account_id already exists in fax_config'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'fax_config'
  AND COLUMN_NAME = 'rc_extension_id';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `fax_config` ADD COLUMN `rc_extension_id` varchar(64) DEFAULT NULL AFTER `rc_account_id`',
    'SELECT ''Column rc_extension_id already exists in fax_config'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
