ALTER TABLE `fax_config` ADD COLUMN `gatewayName` varchar(255);
ALTER TABLE `fax_config` ADD COLUMN `faxReply` varchar(10);
UPDATE `fax_config` SET `gatewayName` = '' WHERE `gatewayName` IS NULL;
UPDATE `fax_config` SET `faxReply` = '' WHERE `faxReply` IS NULL;
UPDATE `fax_config` SET `active` = 0 WHERE `active` IS NULL;
UPDATE `fax_config` SET `download` = 1 WHERE `download` IS NULL;