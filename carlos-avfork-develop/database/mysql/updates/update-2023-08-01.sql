ALTER TABLE `allergies` ADD COLUMN `atc` varchar(55) NULL AFTER `providerNo`;
ALTER TABLE `allergies` ADD COLUMN `nonDrug` tinyint(1) NULL AFTER `atc`;
ALTER TABLE `allergies` ADD COLUMN `reaction_type` varchar(20) NULL AFTER `nonDrug`;