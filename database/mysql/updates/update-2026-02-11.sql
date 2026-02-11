-- Add provider transport selection for fax configuration.
ALTER TABLE `fax_config`
ADD COLUMN `providerType` varchar(25) DEFAULT 'MIDDLEWARE';

-- Backfill existing rows to preserve current middleware behavior.
UPDATE `fax_config`
SET `providerType` = 'MIDDLEWARE'
WHERE `providerType` IS NULL OR `providerType` = '';
