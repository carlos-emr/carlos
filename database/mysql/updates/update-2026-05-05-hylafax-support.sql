-- Add HylaFax on-premise fax server configuration fields.
-- This script is idempotent and can be run multiple times safely.

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'fax_config'
  AND COLUMN_NAME = 'hf_host';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `fax_config` ADD COLUMN `hf_host` varchar(255) DEFAULT '''' AFTER `providerType`',
    'SELECT ''Column hf_host already exists in fax_config'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'fax_config'
  AND COLUMN_NAME = 'hf_port';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `fax_config` ADD COLUMN `hf_port` int DEFAULT 4559 AFTER `hf_host`',
    'SELECT ''Column hf_port already exists in fax_config'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'fax_config'
  AND COLUMN_NAME = 'hf_username';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `fax_config` ADD COLUMN `hf_username` varchar(64) DEFAULT '''' AFTER `hf_port`',
    'SELECT ''Column hf_username already exists in fax_config'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'fax_config'
  AND COLUMN_NAME = 'hf_password';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `fax_config` ADD COLUMN `hf_password` varchar(255) DEFAULT '''' AFTER `hf_username`',
    'SELECT ''Column hf_password already exists in fax_config'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'fax_config'
  AND COLUMN_NAME = 'hf_modem';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `fax_config` ADD COLUMN `hf_modem` varchar(32) DEFAULT '''' AFTER `hf_password`',
    'SELECT ''Column hf_modem already exists in fax_config'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'fax_config'
  AND COLUMN_NAME = 'hf_use_ssh';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `fax_config` ADD COLUMN `hf_use_ssh` tinyint(1) DEFAULT 0 AFTER `hf_modem`',
    'SELECT ''Column hf_use_ssh already exists in fax_config'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'fax_config'
  AND COLUMN_NAME = 'hf_ssh_key';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `fax_config` ADD COLUMN `hf_ssh_key` text AFTER `hf_use_ssh`',
    'SELECT ''Column hf_ssh_key already exists in fax_config'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'fax_config'
  AND COLUMN_NAME = 'hf_recvq_path';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `fax_config` ADD COLUMN `hf_recvq_path` varchar(255) DEFAULT '''' AFTER `hf_ssh_key`',
    'SELECT ''Column hf_recvq_path already exists in fax_config'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `fax_config`
SET `hf_port` = 4559
WHERE `hf_port` IS NULL OR `hf_port` = 0;

-- HylaFax SSH mode uses pre-provisioned server-side SSH config/agent credentials.
-- Clear unused legacy credential columns so unnecessary secret material is not retained.
UPDATE `fax_config`
SET `hf_password` = '',
    `hf_ssh_key` = ''
WHERE `hf_password` IS NOT NULL
   OR `hf_ssh_key` IS NOT NULL;
