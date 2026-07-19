-- Updates to tighten up table column widths.
-- Many column widths are far too large for the intended contents.
-- These should be set appropriately in order to reduce the database footprint
-- and mitigate injection attacks.
-- Data that is too large for the column width is truncated

ALTER TABLE `demographic`
    DROP INDEX `myOscarUserName`;
ALTER TABLE `demographic`
    MODIFY COLUMN `myOscarUserName` varchar(1) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `email`,
    MODIFY COLUMN `provider_no` varchar(11) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `spoken_lang`,
    MODIFY COLUMN `previousAddress` varchar(60) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `alias`,
    MODIFY COLUMN `children` varchar(1) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `previousAddress`,
    MODIFY COLUMN `sourceOfIncome` varchar(1) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `children`,
    MODIFY COLUMN `newsletter` varchar(1) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `country_of_origin`;
ALTER TABLE `admission`
    MODIFY COLUMN `admission_notes` varchar(4) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `admission_from_transfer`,
    MODIFY COLUMN `discharge_notes` varchar(4) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `discharge_from_transfer`;
ALTER TABLE `pharmacyInfo`
    MODIFY COLUMN `address` varchar(255) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `name`,
    MODIFY COLUMN `city` varchar(60) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `address`,
    MODIFY COLUMN `province` varchar(60) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `city`,
    MODIFY COLUMN `notes` varchar(25) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `email`,
    MODIFY COLUMN `serviceLocationIdentifier` varchar(25) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `status`;