CREATE TABLE `rbt_groups` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `tid` int(11) DEFAULT NULL,
  `group_name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
);

ALTER TABLE document ADD COLUMN abnormal int(1);
ALTER TABLE document ADD COLUMN receivedDate date;

ALTER TABLE `FaxClientLog` ADD COLUMN `transactionType` varchar(25) NULL AFTER `faxId`;

UPDATE document SET abnormal = 0 WHERE abnormal IS NULL;

INSERT INTO `secObjectName`(`objectName`, `description`, `orgapplicable`) VALUES ('_fax', 'Send and Receive Faxes', 0);
INSERT INTO `secObjPrivilege`(`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`) VALUES ('-1', '_fax', 'x', 0, '999999');
INSERT INTO `secObjPrivilege`(`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`) VALUES ('admin', '_fax', 'x', 0, '999998');
INSERT INTO `secObjPrivilege`(`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`) VALUES ('doctor', '_fax', 'x', 0, '999998');