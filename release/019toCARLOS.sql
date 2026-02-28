
-- This is a file that should be run once but can be run repeatedly to convert OSCAR 19 final schema 
-- ... and some earlier OSCAR 19's
-- To the CARLOS schema
-- Care to be taken to ensure that it doesn't break anything for either CARLOS or OSCAR 19
-- so that, in theory, either WAR could run on the same schema

-- WARNING tested for ONTARIO schema only
-- You will need to consider uncommenting multiple lines for BC 

-- PHC modifications as CARLOS does not currently support bar and table graphs
UPDATE indicatorTemplate SET template = REPLACE(template, '<graphType>bar</graphType>', '<graphType>pie</graphType>');
UPDATE indicatorTemplate SET template = REPLACE(template, '<graphType>table</graphType>', '<graphType>pie</graphType>');
UPDATE indicatorTemplate SET template = REPLACE(template, '<graphType>pie</graphType>', '');

DELIMITER $$
DROP PROCEDURE IF EXISTS add_column $$
CREATE PROCEDURE add_column
(
    given_table    VARCHAR(64),
    given_column   VARCHAR(64),
    given_defin    VARCHAR(64)
)

theStart:BEGIN

    DECLARE TableIsThere INTEGER;
    DECLARE ColumnIsThere INTEGER;

    SELECT COUNT(1) INTO TableIsThere
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE table_schema = DATABASE()
    AND   table_name   = given_table;

    IF TableIsThere = 0 THEN
        SELECT CONCAT(DATABASE(),'.',given_table, 
	' does not exist.  Unable to add ', given_column) add_columnMessage;
	LEAVE theStart;
    ELSE
        SET ColumnIsThere = (  SELECT COUNT(*) 
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE   TABLE_SCHEMA = DATABASE() AND 
                            TABLE_NAME = given_table AND 
                            COLUMN_NAME = given_column );
        IF ColumnIsThere = 0 THEN
 		    SET @sqlstmt = CONCAT('ALTER TABLE ',DATABASE(),'.',given_table,' ADD ',given_column,' ', given_defin);
		    PREPARE st FROM @sqlstmt;
		    EXECUTE st;
		    DEALLOCATE PREPARE st;
	    ELSE
		    SELECT CONCAT('Column ',given_column,' Already Exists ON Table ',
		    DATABASE(),'.',given_table) add_columnMessage;
	    END IF;
	 END IF;
END $$
DELIMITER ;

/*
-- update-2019-11-04.sql see below for appointment_status
-- update-2019-11-04.sql
-- USE WITH CAUTION ON AN EXISTING appointment_status TABLE
-- Modifying the appointment_status table requires a TOMCAT RESTART
-- this update also corrects all appointment status` misplaced after the last BILLING status.
-- backup appointment_status table
CREATE TABLE IF NOT EXISTS appointment_status_old LIKE appointment_status;
INSERT INTO appointment_status_old SELECT * FROM appointment_status;
-- drop old edit tables and create a new one.
DROP TABLE IF EXISTS appointment_status_edit;
-- copy the appointment_status table
CREATE TABLE appointment_status_edit LIKE appointment_status;
INSERT INTO appointment_status_edit SELECT * FROM appointment_status;
-- determine the row id for the BILLING status.  This needs to be last with the highest id
SELECT ao.`id` INTO @BILLING_STATUS_ROW FROM appointment_status_edit ao WHERE BINARY ao.`status` = 'B';
-- Calculate the number of new rows needed to move from the end of the list plus 1 row for the new confirmed status
SELECT (count(id) + 1) INTO @NUMBER_ROWS FROM appointment_status_edit WHERE id > @BILLING_STATUS_ROW;
-- remove all "ilegal rows" inserted after the BILLING status
DELETE FROM appointment_status_edit WHERE id > @BILLING_STATUS_ROW;
-- remove any potential for duplicate "h" confirmed status types
DELETE FROM appointment_status_edit WHERE `status` = 'h';
-- Calculate the begining INSERT_ID by subtracting the needed number of rows minus mandatory last 3 rows
SET @INSERT_ID := @BILLING_STATUS_ROW - 2;
-- increment the last 3 mandatory rows to make room for the new @NUMBER_ROWS

UPDATE appointment_status_edit SET id = (id + @NUMBER_ROWS) WHERE id >= @INSERT_ID ORDER BY id DESC;
-- insert the new "confirmed" status into the new row "INSERT_ID"
INSERT INTO appointment_status_edit (`id`, `status`, `description`, `color`, `icon`, `active`, `editable`, short_letter_colour, short_letters) values (@INSERT_ID, 'h', 'Confirmed', '#2fcccf', 'thumb.png', true, true, 0, 'CONF');
-- Add back in remaining status' found entered after the BILLING status.
-- Exclude the "confirmed" status if it already exists in the status table.
-- Increment the @INSERT_ID by 1 to accommodate the new confirmed status previously inserted
INSERT INTO appointment_status_edit
SELECT @ROW := @ROW + 1 AS id, a.`status`, a.description, a.color, a.icon, a.active, a.editable, a.short_letter_colour, a.short_letters FROM appointment_status a LEFT JOIN appointment_status_edit ao ON (a.`status` = ao.`status` AND a.`status` != 'h') CROSS JOIN (SELECT @ROW := (@INSERT_ID + 1)) r WHERE a.`status` != 'h' AND ao.id IS NULL ORDER BY a.`status`;
-- set missing short_color_letter values
UPDATE appointment_status_edit SET short_letter_colour = 0 WHERE short_letter_colour IS NULL;

-- Rebuild the appointment_status table
START TRANSACTION;
DROP TABLE IF EXISTS appointment_status;
CREATE TABLE appointment_status LIKE appointment_status_edit;
INSERT INTO appointment_status SELECT * FROM appointment_status_edit;
-- drop the edit table
DROP TABLE IF EXISTS appointment_status_edit;
COMMIT;
*/
/*
DROP TABLE IF EXISTS appointment_status;

CREATE TABLE IF NOT EXISTS `appointment_status` (
  `id` int(11) NOT NULL auto_increment,
  `status` char(2) NOT NULL,
  `description` char(30) NOT NULL default 'no description',
  `color` char(7) NOT NULL default '#cccccc',
  `icon` char(30) NOT NULL default '''''',
  `active` int(1) NOT NULL default '1',
  `editable` int(1) NOT NULL default '0',
  `short_letter_colour` INT(11) NULL COMMENT 'The colour of the short letters in the system', 
  `short_letters` VARCHAR(5) NULL COMMENT 'The short letter representation of the appointment status',
  PRIMARY KEY  (`id`)
);
INSERT INTO `appointment_status` VALUES 
(1,'t','To Do','#FDFEC7','starbill.gif',1,0,NULL,NULL),
(2,'T','Daysheet Printed','#FDFEC7','todo.gif',1,0,NULL,NULL),
(3,'H','Here','#00ee00','here.gif',1,1,NULL,NULL),
(4,'P','Picked','#FFBBFF','picked.gif',1,1,NULL,NULL),
(5,'E','Empty Room','#FFFF33','empty.gif',1,1,NULL,NULL),
(6,'a','Customized 1','#897DF8','1.gif',1,1,NULL,NULL),
(7,'b','Customized 2','#897DF8','2.gif',1,1,NULL,NULL),
(8,'c','Customized 3','#897DF8','3.gif',0,1,NULL,NULL),
(9,'d','Customized 4','#897DF8','4.gif',1,1,NULL,NULL),
(10,'e','emailed','#897DF8','5.gif',1,1,NULL,NULL),
(11,'f','Confirmed', '#897DF8','thumb.png',1,1,NULL,NULL),
(12,'x', 'Extra', '#2fcccf', 'at.gif', 1,1,NULL,NULL),
(13,'N','No Show','#cccccc','noshow.gif',1,0,NULL,NULL),
(14,'C','Cancelled','#999999','cancel.gif',1,0,NULL,NULL),
(15,'B','Billed','#3ea4e1','billed.gif',1,0,NULL,NULL);
*/

-- update-2017-01-25.sql
--ALTER TABLE hl7TextInfo MODIFY report_status VARCHAR(10);
-- update-2020-04-13.sql part in patch removed
CALL add_column('demographicPharmacy', 'consentToContact', 'TINYINT(1)');
CALL add_column('consultationRequests', 'demographicContactId', 'INT(10)');
-- update-2020-04-24.sql
CALL add_column('FaxClientLog', 'transactionType', 'VARCHAR(25)');
CALL add_column('document', 'fileSignature', 'VARCHAR(255)');
ALTER TABLE FaxClientLog modify column requestId int(10);
ALTER TABLE FaxClientLog modify column faxId int(10);
-- update-2020-06-26.sql
CALL add_column('DemographicContact', 'programNo', 'INT(11)');
CALL add_column('professionalSpecialists', 'deleted', 'TINYINT(1) NOT NULL DEFAULT 0');


-- update-2021-06-03.sql
CALL add_column('allergies', 'atc', 'VARCHAR(55)');
CALL add_column('allergies', 'reaction_type', 'VARCHAR(20)');
-- update-2021-09-03.sql
CALL add_column('HRMDocumentToProvider', 'filed', "TINYINT(1) NULL AFTER `viewed`");
-- update-2022-01-03.sql
-- for document upload, review.
CALL add_column('document', 'report_media', 'INT');
CALL add_column('document', 'sent_date', 'DATETIME');
-- update-2022-01-30.sql
CALL add_column('fax_config', 'accountName', 'VARCHAR(55)');
-- update-2022-05-13.sql
CALL add_column('drugs', 'demographic_contact_id', 'INT(10)');
-- update-2023-02-20.sql
--
DROP TABLE `ProviderPreferenceAppointmentScreenEForm`;
CREATE TABLE IF NOT EXISTS `ProviderPreferenceAppointmentScreenEForm` (
      `providerNo` varchar(6) NOT NULL,
      `appointmentScreenEForm` int(11) NOT NULL,
      `eFormName` varchar(255)
);
--CALL add_column('ProviderPreferenceAppointmentScreenEForm', 'eFormName', 'VARCHAR(255)');
-- update-2023-03-30.sql
CALL add_column('tickler', 'creation_date', 'TIMESTAMP NOT NULL');
-- update-2023-08-16.sql
CALL add_column('fax_config', 'download', 'TINYINT(1)');
-- update-2023-11-15.sql
CALL add_column('fax_config', 'gatewayName', 'VARCHAR(255)');
CALL add_column('fax_config', 'faxReply', 'VARCHAR(10)');
-- update-2023-12-13.sql
CALL add_column('professionalSpecialists', 'province', 'VARCHAR(55)');
-- update-2024-01-09.sql  added to patch19.sql
--CALL add_column('demographic', 'genderId', 'INT NULL');
--CALL add_column('demographic', 'pronoun', 'VARCHAR(25) NULL');
--CALL add_column('demographic', 'pronounId', 'INT NULL');
--CALL add_column('demographic', 'gender', 'VARCHAR(25) NULL');
--CALL add_column('demographicArchive', 'genderId', 'INT NULL');
--CALL add_column('demographicArchive', 'pronoun', 'VARCHAR(25) NULL');
--CALL add_column('demographicArchive', 'pronounId', 'INT NULL');
--CALL add_column('demographicArchive', 'gender', 'VARCHAR(25) NULL');
-- update-2024-01-24.sql
ALTER TABLE `SystemPreferences` MODIFY `value` varchar(255);
-- update-2024-01-31.sql
CALL add_column('ProviderPreference', 'defaultBillingLocation', 'VARCHAR(4) DEFAULT "no"');
CALL add_column('ProviderPreference', 'defaultSliCode', 'VARCHAR(4) DEFAULT "no"');
-- update-2024-02-02.sql
ALTER TABLE professionalSpecialists MODIFY deleted tinyint(1) NOT NULL default 0;
-- update-2024-04-22.sql
CALL add_column('pharmacyInfo', 'uid', 'INT(10) NOT NULL FIRST');
-- update-2024-04-30.sql
CALL add_column('eform', 'stable', 'TINYINT(1) NOT NULL DEFAULT 1');
CALL add_column('eform', 'errorLog', 'TINYBLOB NULL');
-- update-2024-08-09.sql
ALTER TABLE hl7TextInfo modify report_status varchar(20);
-- update-2024-11-25.sql
CALL add_column('DigitalSignature', 'ModuleType', "ENUM('CONSULTATION', 'E_FORM', 'PRESCRIPTION') NULL");
ALTER TABLE DigitalSignature MODIFY COLUMN signatureImage MEDIUMBLOB;
-- update-2024-12-04.sql
CALL add_column('prescription', 'digital_signature_id', 'INT NULL DEFAULT NULL');
-- update-2025-02-27.sql
CALL add_column('security', 'usingMfa', 'BOOL NOT NULL');
CALL add_column('security', 'mfaSecret', 'VARCHAR(255)');

-- ADDITIONAL
CALL add_column('document', 'sent_date_time', 'datetime DEFAULT NULL');

DELIMITER $$

DROP PROCEDURE IF EXISTS patch_database $$
CREATE PROCEDURE patch_database()
BEGIN

-- update-2020-04-10.sql & update-2020-04-24.sql
IF NOT EXISTS( (SELECT * FROM `secObjPrivilege` WHERE objectName ='_fax') ) THEN
    INSERT INTO `secObjectName`(`objectName`, `description`, `orgapplicable`) VALUES ('_fax', 'Send and Receive Faxes', 0);
    INSERT INTO `secObjPrivilege`(`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`) VALUES ('admin', '_fax', 'x', 0, '999998');
    INSERT INTO `secObjPrivilege`(`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`) VALUES ('doctor', '_fax', 'x', 0, '999998');
    INSERT INTO `secObjPrivilege`(`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`) VALUES ('-1', '_fax', 'x', 0, '999999');
END IF;

-- update-2021-02-07.sql
IF NOT EXISTS( (SELECT * FROM `secObjPrivilege` WHERE `objectName`='_careconnect') ) THEN
    INSERT INTO `secObjectName`(`objectName`, `description`, `orgapplicable`) VALUES ('_careconnect', 'Restrict visibility and access to BC Care Connect', 0);
    INSERT INTO `secObjPrivilege`(`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`) VALUES ('doctor', '_careconnect', 'o', 0, '999998');
    INSERT INTO `secObjPrivilege`(`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`) VALUES ('Pharmacist', '_careconnect', 'o', 0, '999998');
END IF;

-- update-2023-12-05.sql
IF NOT EXISTS( (SELECT * FROM `secObjPrivilege` WHERE `objectName`='_rx.editPharmacy') ) THEN
    INSERT INTO `secObjectName`(`objectName`) VALUES ('_rx.editPharmacy');
    INSERT INTO `secObjPrivilege`(`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`) VALUES ('doctor','_rx.editPharmacy','x',0,'999998');
    INSERT INTO `secObjPrivilege`(`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`) VALUES ('admin','_rx.editPharmacy','x',0,'999998');
END IF;

-- update-2025-01-29.sql
IF NOT EXISTS( (SELECT * FROM secObjPrivilege WHERE objectName='_admin.email') ) THEN
    INSERT INTO `secObjectName`(`objectName`, `description`, `orgapplicable`) VALUES ('_admin.email', 'Configure & Manage Emails', 0);
    INSERT INTO `secObjPrivilege`(`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`) VALUES ('admin','_admin.email','x',0,'999998');
END IF;

IF NOT EXISTS( (SELECT * FROM `secObjPrivilege` WHERE `objectName`='_email') ) THEN
    INSERT INTO `secObjectName`(`objectName`, `description`, `orgapplicable`) VALUES ('_email', 'Send and Receive Emails', 0);
    INSERT INTO `secObjPrivilege`(`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`) VALUES ('-1', '_email', 'x', 0, '999999');
    INSERT INTO `secObjPrivilege`(`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`) VALUES ('admin', '_email', 'x', 0, '999998');
    INSERT INTO `secObjPrivilege`(`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`) VALUES ('doctor', '_email', 'x', 0, '999998');
END IF;

-- update-2024-05-16.sql
IF NOT EXISTS( (SELECT * FROM `secObjPrivilege` WHERE `objectName`='_admin.fax.restart') ) THEN
    INSERT INTO `secObjectName` (`objectName`, `description`, `orgapplicable`) VALUES ('_admin.fax.restart', 'Show status and restart fax scheduler', '0');
    INSERT INTO `secObjPrivilege` VALUES ('admin','_admin.fax.restart','x',0,'999998');
END IF;

-- update-2019-04-06.sql
IF NOT EXISTS( (SELECT * FROM `property` WHERE `name`='aua_valid_duration') ) THEN
    INSERT INTO `property`(`name`, `value`, `provider_no`) VALUES ('aua_valid_duration', '2300-01-01', '999998');
END IF;

IF NOT EXISTS( (SELECT * FROM `property` WHERE `name`='aua_valid_from') ) THEN
    INSERT INTO `property`(`name`, `value`, `provider_no`) VALUES ('aua_valid_from', '2001-01-01', '999998');
END IF;

-- update-2025-01-29.sql
IF NOT EXISTS( (SELECT * FROM `property` WHERE `name`='email_communication') ) THEN
    INSERT INTO `property`(`name`, `value`, `provider_no`) VALUES ('email_communication', 'electronic_communication_consent', NULL);
END IF;

-- update-2024-10-08.sql
IF NOT EXISTS( (SELECT * FROM `property` WHERE `name`='default_ref_prac') ) THEN
    INSERT INTO `property` (`name`, `value`, `provider_no`) VALUES ('default_ref_prac', '1', '999998');
END IF;

IF NOT EXISTS( (SELECT * FROM `property` WHERE `name`='consultation_letterheadname_default') ) THEN
    INSERT INTO `property` (`name`, `value`, `provider_no`) VALUES ('consultation_letterheadname_default', '1', '999998');
END IF;

-- update-2024-02-01.sql
IF NOT EXISTS( (SELECT * FROM `issue` WHERE `code`='ExternalNote') ) THEN
    INSERT INTO `issue` (`code`,`description`,`role`,`update_date`, `type`) VALUES ('ExternalNote','External Note', 'nurse', now(), 'system');
END IF;


END $$

CALL patch_database() $$

DELIMITER ;

-- update-2020-11-09.sql
CREATE TABLE IF NOT EXISTS incomingLabRulesType (
  id int(10) NOT NULL AUTO_INCREMENT,
  forward_rule_id int(10),
  type VARCHAR(10) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT FOREIGN KEY (forward_rule_id) REFERENCES incomingLabRules (id) ON DELETE CASCADE ON UPDATE CASCADE
);

DELIMITER $$
DROP PROCEDURE IF EXISTS insertForwardTypes$$
CREATE PROCEDURE insertForwardTypes()
BEGIN
    DECLARE i INT;
    SET i = 1;
    WHILE i <= (SELECT MAX(id) FROM incomingLabRules) DO
            IF (SELECT id FROM incomingLabRules WHERE id = i) THEN
                INSERT INTO incomingLabRulesType (forward_rule_id, type) VALUES  (i, 'HL7'), (i, 'DOC'), (i, 'HRM');
            END IF;
            SET i = i + 1;
        END WHILE;
END$$
DELIMITER ;
CALL insertForwardTypes();
DROP PROCEDURE IF EXISTS insertForwardTypes;

-- update-2020-12-08.sql
UPDATE document SET abnormal = 0 WHERE abnormal IS NULL;

-- update-2021-05-28.sql
-- update-2021-09-03.sql
create table if not exists `read_lab`
(
    id int null,
    provider_no varchar(11) null,
    lab_type varchar(20) null,
    lab_id int null
);


-- update-2021-10-20.sql formBCAR2020
-- update-2022-01-03.sql
-- for document upload, review.

CREATE TABLE IF NOT EXISTS `document_review` (
        `id` int auto_increment primary key,
        `document_no` int(20) not null,
        `provider_no` varchar(6) not null,
        `date_reviewed` datetime,
        foreign key(document_no) references document(document_no),
        foreign key(provider_no) references provider(provider_no)
    );

INSERT INTO `document_review` (`document_no`, `provider_no`, `date_reviewed`)
SELECT d.document_no, d.reviewer, d.reviewdatetime
FROM `document` d
WHERE d.reviewer IS NOT NULL AND d.reviewer != '' AND d.reviewer != 'null' and d.reviewdatetime IS NOT NULL AND d.reviewer > 100;


-- update-2022-01-14.sql
-- update-2022-01-30.sql
-- update-2022-03-24.sql
-- update-2022-05-13.sql
-- update-2023-02-20.sql
-- update-2023-03-30.sql

UPDATE tickler
    JOIN (
        SELECT max(t.tickler_no) as tickler_no, max(tu.update_date) as update_date
        FROM tickler t
                 JOIN tickler_update tu
                      ON(t.tickler_no = tu.tickler_no)
        GROUP BY t.tickler_no HAVING count(t.tickler_no) > -1
    ) ticklers
    ON (ticklers.tickler_no = tickler.tickler_no)
SET tickler.creation_date = ticklers.update_date;

ALTER TABLE `hl7TextInfo`
    MODIFY COLUMN `report_status` varchar(25) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `first_name`;

-- update-2023-05-03.sql
-- Updates to tighten up table column widths.
-- Many column widths are far too large for the intended contents.
-- These should be set appropriately in order to reduce the database footprint
-- and mitigate injection attacks.
-- Data that is too large for the column width is truncated

ALTER TABLE `demographic`
    DROP INDEX `myOscarUserName`;
ALTER TABLE `demographic`
    MODIFY COLUMN `provider_no` varchar(11) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `spoken_lang`,
    MODIFY COLUMN `previousAddress` varchar(60) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `alias`,
    MODIFY COLUMN `children` varchar(1) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `previousAddress`,
    MODIFY COLUMN `sourceOfIncome` varchar(1) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `children`,
    MODIFY COLUMN `newsletter` varchar(10) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `country_of_origin`;
ALTER TABLE `admission`
    MODIFY COLUMN `admission_notes` varchar(4) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `admission_from_transfer`,
    MODIFY COLUMN `discharge_notes` varchar(4) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `discharge_from_transfer`;
ALTER TABLE `pharmacyInfo`
    MODIFY COLUMN `address` varchar(255) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `name`,
    MODIFY COLUMN `city` varchar(60) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `address`,
    MODIFY COLUMN `province` varchar(60) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `city`,
    MODIFY COLUMN `serviceLocationIdentifier` varchar(25) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `status`;

-- update-2023-05-17.sql
--INSERT INTO `encounterForm`(`form_name`, `form_value`, `form_table`, `hidden`) VALUES ('Rourke2020', '../form/formrourke2020complete.jsp?demographic_no=', 'formRourke2020', 0);

CREATE TABLE IF NOT EXISTS `form_boolean_value` (
    `form_name` varchar(50) NOT NULL,
    `form_id` int(10) NOT NULL,
    `field_name` varchar(50) NOT NULL,
    `value` tinyint(1) DEFAULT NULL,
    PRIMARY KEY (`form_name`,`form_id`,`field_name`)
);

-- update-2023-08-12.sql
-- A large production database was used to identify the following adjustments

-- this change is made because this column holds strings up to 10 characters long ("Electronic")
-- this reverses a change made in update-2023-05-03.sql where it was set to varchar(1)
--ALTER TABLE `demographic`
--    MODIFY COLUMN `newsletter` varchar(10);
	
-- this change is made because this is a textbox in the GUI and examples of it holding up to 800 characters
-- this reverses a change made in update-2023-05-03.sql where it was set to varchar(25)
ALTER TABLE `pharmacyInfo` MODIFY COLUMN `notes` tinytext;

-- this change is made because examples of it holding more than 25 characters
-- this reverses a change made in update-2023-05-03.sql where it was set to varchar(25)
ALTER TABLE `pharmacyInfo` MODIFY COLUMN `serviceLocationIdentifier` varchar(255);

-- this change is made based on realworld performance issues without it
-- note, this create index if not exists only works in mariadb
CREATE INDEX IF NOT EXISTS `provider_noIndex` ON `log` (`provider_no`);

-- this change is made based on realworld performance issues without it
-- note, this create index if not exists only works in mariadb
CREATE INDEX IF NOT EXISTS `archiveId` ON `demographicExtArchive` (`archiveId`);

-- this change is made based on realworld performance issues without it
-- note, this create index if not exists only works in mariadb
CREATE INDEX IF NOT EXISTS `note_idIndex` ON `casemgmt_note_link` (`note_id`);

-- this change is made based on realworld performance issues without it
-- note, this create index if not exists only works in mariadb
CREATE INDEX IF NOT EXISTS `note_idIndex` ON `casemgmt_note_ext` (`note_id`);

-- this change is made based on realworld performance issues without it
-- note, this create index if not exists only works in mariadb
CREATE INDEX IF NOT EXISTS `ticklerno` ON `tickler_comments` (`tickler_no`);

-- update-2023-08-16.sql
update fax_config set download = 1 where download is null;

-- update-2023-08-29.sql
-- this change is made based on realworld performance issues without it
-- note, this create index if not exists only works in mariadb
CREATE INDEX IF NOT EXISTS `service_date_Index` ON `radetail` (`service_date`);

-- update-2023-09-12.sql
-- this index is designed to reduce lab opening time by optimizing this query in labdisplay.jsp
-- CaseManagementNoteLink cml = caseManagementManager.getLatestLinkByTableId(CaseManagementNoteLink.LABTEST,Long.valueOf(segmentID),j+"-"+k);                                       
-- note, this create index if not exists only works in mariadb
CREATE INDEX IF NOT EXISTS `casemgmt_note_link_table_table_name_index` ON `casemgmt_note_link` (`table_name`, `table_id`, `other_id`);

-- update-2023-10-21.sql
-- this index is designed to reduce the time it takes to import a HL7 lab.  The specific line this is optimizing is
-- for (Measurement m : measurementDao.findByValue("lab_no", matchingLabs[k - 1])) {
-- in oscar/oscarLab/ca/all/Hl7textResultsData.java
-- note, this create index if not exists only works in mariadb
CREATE INDEX IF NOT EXISTS `measurements_ext_keyval_val` ON `measurementsExt` (`keyval`, `val`(100));


-- update-2023-11-15.sql
UPDATE `fax_config` SET `gatewayName` = '' WHERE `gatewayName` IS NULL;
UPDATE `fax_config` SET `faxReply` = '' WHERE `faxReply` IS NULL;
UPDATE `fax_config` SET `active` = 0 WHERE `active` IS NULL;
UPDATE `fax_config` SET `download` = 1 WHERE `download` IS NULL;

-- update-2024-01-24.sql
/* This create table is added because not all instances will have this table already in the DB*/
CREATE TABLE IF NOT EXISTS billing_preferences (
  id int(10) unsigned NOT NULL auto_increment,
  referral int(10) unsigned NOT NULL default '0',
  providerNo int(10) unsigned NOT NULL default '0',
  defaultPayeeNo varchar(11) NOT NULL default '0',
  PRIMARY KEY  (id)
) ;

-- update-2024-01-30.sql
-- update-2024-01-31.sql
-- update-2024-02-02.sql
UPDATE professionalSpecialists SET deleted = 0 WHERE deleted IS NULL;

-- update-2024-03-05.sql
CREATE TABLE IF NOT EXISTS erefer_attachment (
    id INT PRIMARY KEY AUTO_INCREMENT,
    demographic_no INT,
    created DATETIME,
    archived BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS erefer_attachment_data (
    erefer_attachment_id INT,
    lab_id INT,
    lab_type VARCHAR(20),
    PRIMARY KEY(erefer_attachment_id, lab_id, lab_type)
);

-- update-2024-04-10.sql
-- this index is designed to increase performance when retrieving the consent status for a particular demographic by avoiding a full table scan
CREATE INDEX IF NOT EXISTS `Consent_demographic_no_IDX` ON `Consent` (`demographic_no`);

-- update-2024-04-23.sql
CREATE TABLE IF NOT EXISTS lst_gender_copy_backup
select * from lst_gender;
truncate table lst_gender;
insert into lst_gender (code,description,isactive,displayorder) values ('M','Male',1,2);
insert into lst_gender (code,description,isactive,displayorder) values ('F','Female',1,1);
insert into lst_gender (code,description,isactive,displayorder) values ('X','Intersex',1,3);
insert into lst_gender (code,description,isactive,displayorder) values ('U','Undisclosed',1,4);

-- update-2024-04-24.sql
CREATE INDEX IF NOT EXISTS idx_hrmDocumentId_hd ON HRMDocumentToDemographic(hrmDocumentId);
CREATE INDEX IF NOT EXISTS idx_demographicNo_hd ON HRMDocumentToDemographic(demographicNo);
CREATE INDEX IF NOT EXISTS idx_hrmDocumentId_hp ON HRMDocumentToProvider(hrmDocumentId);
CREATE INDEX IF NOT EXISTS idx_signedOff_providerNo_hp ON HRMDocumentToProvider(signedOff, providerNo);

-- update-2024-05-28.sql
CREATE TABLE IF NOT EXISTS emailConfig (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    emailType VARCHAR(20),
    emailProvider VARCHAR(20),
    active BOOLEAN DEFAULT FALSE,
    senderFirstName VARCHAR(50),
    senderLastName VARCHAR(50),
    senderEmail VARCHAR(255),
    configDetails VARCHAR(1000)
);

-- update-2024-06-18.sql
-- Example email configurations for Gmail and Outlook.
-- INSERT INTO emailConfig (emailType, emailProvider, active, senderFirstName, senderLastName, senderEmail, configDetails) VALUES ('SMTP', 'GMAIL', true, 'FIRSTNAME', 'LASTNAME', 'example@gmail.com', '{\"host\":\"smtp.gmail.com\",\"port\":\"587\",\"username\":\"example@gmail.com\",\"password\":\"12345\"}');
-- INSERT INTO emailConfig (emailType, emailProvider, active, senderFirstName, senderLastName, senderEmail, configDetails) VALUES ('SMTP', 'OUTLOOK', true, 'FIRSTNAME', 'LASTNAME', 'example@outlook.com', '{\"host\":\"smtp.office365.com\",\"port\":\"587\",\"username\":\"example@outlook.com\",\"password\":\"12345\"}');
CREATE TABLE IF NOT EXISTS emailLog (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    configId BIGINT,
    fromEmail VARCHAR(255),
    toEmail VARCHAR(255),
    subject VARCHAR(1024),
    body BLOB,
    status VARCHAR(20),
    errorMessage VARCHAR(1000),
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    encryptedMessage BLOB DEFAULT FALSE,
    password VARCHAR(50),
    passwordClue VARCHAR(1024),
    isEncrypted BOOLEAN,
    isAttachmentEncrypted BOOLEAN DEFAULT FALSE,
    chartDisplayOption VARCHAR(20),
    internalComment BLOB DEFAULT '',
    transactionType VARCHAR(20),
    demographicNo VARCHAR(6),
    providerNo INT,
    additionalParams VARCHAR(1000),
    FOREIGN KEY (configId) REFERENCES emailConfig (id)
);

-- update-2025-01-29.sql
CREATE TABLE IF NOT EXISTS emailAttachment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    logId BIGINT,
    fileName VARCHAR(100),
    filePath VARCHAR(500),
    documentType VARCHAR(20),
    documentId INT,
    FOREIGN KEY (logId) REFERENCES emailLog (id)
);

-- update-2024-06-16.sql
-- update-2024-10-23.sql
CREATE INDEX IF NOT EXISTS idx_measurements_demographic_date ON measurements (demographicNo, dateObserved);
CREATE INDEX IF NOT EXISTS idx_measurements_type_demographic ON measurements (type, demographicNo);

-- update-2024-11-22.sql
update relationships set deleted = 0 where deleted is null;

-- *** the following are BC table specifics ***

/*
-- update-2021-04-13.sql
DROP TABLE `billingvisit`;
CREATE TABLE `billingvisit` (
  `visittype` varchar(10) DEFAULT '00',
  `visit_desc` varchar(100) DEFAULT '',
  `region` varchar(5) DEFAULT ''
);

INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('A', 'Practitioner\'s Office - In Community', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('B', 'Community Health Centre', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('C', 'Continuing Care facility', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('D', 'Diagnostic Facility', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('E', 'Hospital Emergency Depart. or Diagnostic & Treatment Centre', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('F', 'Private Medical / Surgical Facility', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('G', 'Hospital - Day Care (Surgery) ', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('I', 'Hospital Inpatient', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('J', 'First Nations Primary Health Care Clinic', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('K', 'Hybrid Primary Care Practice (part-time longitudinal practice, part-time walk-in clinic)', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('L', 'Longitudinal Primary Care Practice (e.g. GP family practice or PCN clinic)', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('M', 'Mental Health Centre', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('N', 'Health Care Practitioner Office (non-physician)', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('P', 'Outpatient', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('Q', 'Specialist Physician Office', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('R', 'Patient\'s residence', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('T', 'Practitioner\'s Office - In Publicly Administered Facility', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('U', 'Urgent and Primary Care Centre', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('V', 'Virtual Care Clinic', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('W', 'Walk-In Clinic', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('Z', 'None of the above', 'BC');


-- update-2023-03-23-bc.sql
-- schema updates for new DB LFP billing program
INSERT INTO `billing_msp_servicecode_times`(`billingservice_no`, `timeRange`) VALUES ('98010', 1);
INSERT INTO `billing_msp_servicecode_times`(`billingservice_no`, `timeRange`) VALUES ('98011', 1);
INSERT INTO `billing_msp_servicecode_times`(`billingservice_no`, `timeRange`) VALUES ('98012', 1);

-- update-2020-05-31.sql
-- update-2020-11-05.sql
 CREATE TABLE `bcpEligibleCodes` (
  `Fee Item` varchar(255) DEFAULT NULL,
 `Section` varchar(255) DEFAULT NULL,
  `Fee Item Description` varchar(255) DEFAULT NULL
 );

-- ----------------------------
-- Records of bcpEligibleCodes
-- ----------------------------

BEGIN;
INSERT INTO `bcpEligibleCodes` VALUES ('13701', 'All Sections', 'OFFICE VISIT FOR COVID-19 WITH TEST');
INSERT INTO `bcpEligibleCodes` VALUES ('13702', 'All Sections', 'OFFICE VISIT FOR COVID-19 WITHOUT TEST');
INSERT INTO `bcpEligibleCodes` VALUES ('30007', 'Allergy and Immunology', 'CLINICAL IMMUNOLOGY AND ALLERGY - SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('30010', 'Allergy and Immunology', 'TO INCLUDE A DETAILED HISTORY AND PHYSICAL EXAMINATION WITH REVIEW OF LABORATOR Y INVESTIGATIONS, PLUS APPROPRIATE ALLERGY AND IMMUNOLOGY MANAGEMENT AND ADDITI ONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT');
INSERT INTO `bcpEligibleCodes` VALUES ('30011', 'Allergy and Immunology', 'TO INCLUDE A DETAILED HISTORY AND PHYSICAL EXAMINATION WITH REVIEW OF LABORATOR Y INVESTIGATIONS, PLUS APPROPRIATE ALLEGY AND IMMUNOLOGY MANAGEMENT AND ADDITIO NAL VISITS NECESSARY TO RENDER A WRITTEN REPORT');
INSERT INTO `bcpEligibleCodes` VALUES ('30012', 'Allergy and Immunology', 'REPEAT OR LIMITED CLINICAL IMMUNOLOGY AND ALLERGY CONSULTATION TO APPLY WHERE A CONSULTATION IS REPEATED FOR THE SAME CONDITION WITHIN SIX MON THS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGEMENT OF THE CONSU');
INSERT INTO `bcpEligibleCodes` VALUES ('30070', 'Allergy and Immunology', 'TELEHEALTH CLINICAL IMMUNOLOGY AND ALLERGY CONSULTATION: TO INCLUDE A DETAILED HISTORY AND PHYSICAL EXAMINATION WITH REVIEW OF LABORATORY INVESTIGATIONS, PLUS APPROPRIATE ALLERGY AND IMMUNOLOGY MANAGEMENT AND ADDITIONAL VISITS');
INSERT INTO `bcpEligibleCodes` VALUES ('30071', 'Allergy and Immunology', 'TELEHEALTH PEDIATRIC CLINICAL IMMUNOLOGY AND ALLERGY CONSULTATION: TO INCLUDE A DETAILED HISTORY AND PHYSICAL EXAMINATION WITH REVIEW OF LABORATORY INVESTIGATIONS, PLUS APPROPRIATE ALLERGY AND IMMUNOLOGY MANAGEMENT AND');
INSERT INTO `bcpEligibleCodes` VALUES ('30072', 'Allergy and Immunology', 'TELEHEALTH REPEAT OR LIMITED CLINICAL IMMUNOLOGY AND ALLERGY CONSULTATION: TO APPLY WHERE A CONSULTATION IS REPEATED FOR THE SAME CONDITION WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGEMENT OF THE');
INSERT INTO `bcpEligibleCodes` VALUES ('30077', 'Allergy and Immunology', 'TELEHEALTH CLINICAL IMMUNOLOGY AND ALLERGY SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('1013', 'Anesthesiology', 'CONSULTATION BY A CERTIFIED SPECIALIST IN ANAESTHESIA FOR ASSESSMENT OF THE PATIENT FOR POST OPERATIVE ACUTE PAIN MANAGEMENT, WHEN THE');
INSERT INTO `bcpEligibleCodes` VALUES ('1015', 'Anesthesiology', 'CONSULTATION BY A CERTIFIED SPECIALIST IN ANAESTHESIA:  BECAUSE OF THE COMPLEXITY, OBSCURITY AND/OR SERIOUSNESS OF THE CASE.  INCLUDES');
INSERT INTO `bcpEligibleCodes` VALUES ('1016', 'Anesthesiology', 'CONSULTATION BY A CERTIFIED SPECIALIST IN ANAESTHESIA:  FOR DIAGNOSTIC OPINION AND/OR THERAPEUTIC MANAGEMENT OF COMPLICATED CHRONIC');
INSERT INTO `bcpEligibleCodes` VALUES ('1107', 'Anesthesiology', 'OFFICE VISIT NOTE:  NOT PAID WITH OTHER LISTINGS.');
INSERT INTO `bcpEligibleCodes` VALUES ('1115', 'Anesthesiology', 'REPEAT OR LIMITED CONSULTATION BY A CERTIFIED SPECIALIST IN ANAESTHESIA:  TO APPLY WHERE A CONSULTATION IS REPEATED FOR THE SAME CONDITION/PROBLEM');
INSERT INTO `bcpEligibleCodes` VALUES ('1116', 'Anesthesiology', 'REPEAT OR LIMITED CONSULTATION BY A CERTIFIED SPECIALIST IN ANAESTHESIA: TO APPLY FOR A DIAGNOSTIC OPINION AND/OR THERAPEUTIC PAIN MANAGEMENT WHERE A CONSULTATION IS REPEATED FOR THE SAME CONDITION/PROBLEM WITHIN SIX MONTHS BY');
INSERT INTO `bcpEligibleCodes` VALUES ('1155', 'Anesthesiology', 'TELEHEALTH ANESTHESIOLOGY CONSULTATION: BY A CERTIFIED SPECIALIST IN ANESTHESIOLOGY BECAUSE OF THE COMPLEXITY, OBSCURITY AND/OR SERIOUSNESS OF THE CASE. INCLUDES APPROPRIATE HISTORY AND AN APPROPRIATE PHYSICAL EXAMINATION,');
INSERT INTO `bcpEligibleCodes` VALUES ('7807', 'Cardiac Surgery', 'CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('7810', 'Cardiac Surgery', 'CONSULTATION:  TO INCLUDE COMPLETE HISTORY AND PHYSICAL EXAMINATION, REVIEW OF X-RAY AND LABORATORY FINDINGS, AND A WRITTEN REPORT');
INSERT INTO `bcpEligibleCodes` VALUES ('7812', 'Cardiac Surgery', 'REPEAT OR LIMITED CONSULTATION:  TO APPLY WHERE A CONSULTATION IS REPEATED FOR SAME CONDITION WITHIN SIX MONTHS OF THE LAST VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('7815', 'Cardiac Surgery', 'CARDIAC SURGERY PRE-OPERATIVE ASSESSMENT NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('78007', 'Cardiac Surgery', 'TELEHEALTH CARDIAC SURGERY SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('78010', 'Cardiac Surgery', 'TELEHEALTH CARDIAC SURGERY CONSULTATION: TO INCLUDE COMPLETE HISTORY AND PHYSICAL EXAMINATION, REVIEW OF X RAY AND LABORATORY FINDINGS, AND A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('78012', 'Cardiac Surgery', 'TELEHEALTH CARDIAC SURGERY REPEAT OR LIMITED CONSULTATION: TO APPLY WHERE A CONSULTATION IS REPEATED FOR SAME CONDITION WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE');
INSERT INTO `bcpEligibleCodes` VALUES ('33007', 'Cardiology', 'CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('33010', 'Cardiology', 'CONSULTATION - CARDIOLOGY CONSULTATION: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('33012', 'Cardiology', 'REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEAT ED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGM ENT OF THE CONSULTANT THE CONSULTATIVE SERVICES DO NOT WARRANT A FULL CONSULTAT');
INSERT INTO `bcpEligibleCodes` VALUES ('33013', 'Cardiology', 'COUNSELLING-GROUP-CARDIOLOGY GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS - FIRST FULL HOUR NOTE: START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE');
INSERT INTO `bcpEligibleCodes` VALUES ('33014', 'Cardiology', 'COUNSELLING-PROLONGED VISIT-CARDIOLOGY PROLONGED VISIT FOR COUNSELLING (MAXIMUM, FOUR PER YEAR) NOTE:');
INSERT INTO `bcpEligibleCodes` VALUES ('33015', 'Cardiology', 'COUNSELLING-GROUP-CARDIOLOGY GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS - SECOND HOUR, PER 1/2 HOUR OR MAJOR PORTION THEREOF');
INSERT INTO `bcpEligibleCodes` VALUES ('33107', 'Cardiology', 'TELEHEALTH SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('33110', 'Cardiology', 'TELEHEALTH CONSULTATION: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT');
INSERT INTO `bcpEligibleCodes` VALUES ('33112', 'Cardiology', 'TELEHEALTH REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGEMENT OF THE CONSULTANT THAT CONSULTATIVE SERVICES DO NOT');
INSERT INTO `bcpEligibleCodes` VALUES ('33114', 'Cardiology', 'TELEHEALTH PROLONGED VISIT FOR COUNSELLING (MAXIMUM FOUR PER YEAR) NOTE: I)    SEE PREAMBLE, CLAUSE D. 3. 3.');
INSERT INTO `bcpEligibleCodes` VALUES ('79007', 'Chest Surgery', 'SUBSEQUENT OFFICE VISIT - THORACIC SURGERY');
INSERT INTO `bcpEligibleCodes` VALUES ('79010', 'Chest Surgery', 'CONSULTATION - THORACIC SURGERY TO INCLUDE COMPLETE HISTORY AND PHYSICAL EXAMINATION, REVIEW OF X-RAY AND LABORATORY FINDINGS, IF REQUIRED, AND WRITTEN REPORT');
INSERT INTO `bcpEligibleCodes` VALUES ('79012', 'Chest Surgery', 'REPEAT OR LIMITED CONSULTATION - THORACIC SURGERY TO APPLY WHERE A CONSULTATION IS REPEATED FOR THE SAME CONDITION WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE');
INSERT INTO `bcpEligibleCodes` VALUES ('79207', 'Chest Surgery', 'TELEHEALTH SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('79210', 'Chest Surgery', 'TELEHEALTH CONSULTATION: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('79212', 'Chest Surgery', 'TELEHEALTH REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT, THE CONSULTATIVE SERVICES DOES NOT');
INSERT INTO `bcpEligibleCodes` VALUES ('1400', 'Critical Care', 'CONSULTATION: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT ( NOT FOR ICU PATIENTS)');
INSERT INTO `bcpEligibleCodes` VALUES ('1402', 'Critical Care', 'REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE CONSULTATIVE SERVICES DO NOT WARRANT A FULL');
INSERT INTO `bcpEligibleCodes` VALUES ('1470', 'Critical Care', 'TELEHEALTH CONSULTATION: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT (NOT FOR ICU PATIENTS)');
INSERT INTO `bcpEligibleCodes` VALUES ('1472', 'Critical Care', 'TELEHEALTH REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE CONSULTATIVE SERVICES DO NOT');
INSERT INTO `bcpEligibleCodes` VALUES ('206', 'Dermatology', 'SPECIAL EXAMINATION: FOR PRIMARY SYSTEMIC DISEASES WITH CUTANEOUS MANIFESTATIONS, TO INCLUDE COMPLETE HISTORY AND PHYSICAL EXAMINATION, REVIEW OF');
INSERT INTO `bcpEligibleCodes` VALUES ('207', 'Dermatology', 'CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('210', 'Dermatology', 'CONSULTATION: TO INCLUDE HISTORY AND DERMATOLOGICAL  EXAMINATION, WITH REVIEW OF ANY PREVIOUS X-RAY AND LABORATORY FINDINGS');
INSERT INTO `bcpEligibleCodes` VALUES ('214', 'Dermatology', 'REPEAT OR LIMITED CONSULTATION: TO APPLY WHERE A CONSULTATION IS REPEATED FOR SAME CONDITION WITHIN SIX MONTHS OF THE LAST VISIT BY THE');
INSERT INTO `bcpEligibleCodes` VALUES ('20207', 'Dermatology', 'TELEHEALTH SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('20210', 'Dermatology', 'TELEHEALTH CONSULTATION: TO INCLUDE HISTORY AND DERMATOLOGICAL EXAMINATION, WITH REVIEW OF ANY PREVIOUS X-RAY AND LABORATORY FINDINGS AND WRITTEN REPORT');
INSERT INTO `bcpEligibleCodes` VALUES ('20214', 'Dermatology', 'TELEHEALTH REPEAT OR LIMITED CONSULTATION: TO APPLY WHERE A CONSULTATION IS REPEATED FOR SAME CONDITION WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE CONSULTATIVE SERVICE');
INSERT INTO `bcpEligibleCodes` VALUES ('33207', 'Endocrinology and Metabolism', 'CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('33210', 'Endocrinology and Metabolism', 'CONSULTATION - ENDOCRINOLOGY CONSULTATION: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('33212', 'Endocrinology and Metabolism', 'REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEAT ED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGM ENT OF THE CONSULTANT THE CONSULTATIVE SERVICES DO NOT WARRANT A FULL CONSULTAT');
INSERT INTO `bcpEligibleCodes` VALUES ('33213', 'Endocrinology and Metabolism', 'COUNSELLING-GROUP-ENDOCRINOLOGY GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS - FIRST FULL HOUR NOTE: START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE');
INSERT INTO `bcpEligibleCodes` VALUES ('33214', 'Endocrinology and Metabolism', 'COUNSELLING - PROLONGED VISIT-ENDOCRINOLOGY PROLONGED VISIT FOR COUNSELLING (MAXIMUM, FOUR PER YEAR) NOTE:');
INSERT INTO `bcpEligibleCodes` VALUES ('33215', 'Endocrinology and Metabolism', 'COUNSELLING-GROUP-ENDOCRINOLOGY GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS-SECOND HOUR, PER 1/2 HOUR OR MAJOR PORTION THEREOF');
INSERT INTO `bcpEligibleCodes` VALUES ('33260', 'Endocrinology and Metabolism', 'INITIAL VIRUTAL ASSESSMENT, WITH PATIENT OR REP');
INSERT INTO `bcpEligibleCodes` VALUES ('33262', 'Endocrinology and Metabolism', 'REPEAT OR LIMITED VIRTUAL ASSESSMENT ');
INSERT INTO `bcpEligibleCodes` VALUES ('33267', 'Endocrinology and Metabolism', 'SUBSEQUENT VIRTUAL OFFICE VISIT, REQUIRING A WRITTEN INDIVIDUALIZED REPORT TO THE GP NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('33270', 'Endocrinology and Metabolism', 'TELEHEALTH ENDOCRINOLOGY CONSULTATION: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('33272', 'Endocrinology and Metabolism', 'TELEHEALTH ENDOCRINOLOGY REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE CONSULTATIVE');
INSERT INTO `bcpEligibleCodes` VALUES ('33277', 'Endocrinology and Metabolism', 'TELEHEALTH ENDOCRINOLOGY SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('33307', 'Gastroenterology', 'CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('33310', 'Gastroenterology', 'CONSULTATION - GASTROENTEROLOGY CONSULTATION: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('33312', 'Gastroenterology', 'REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEAT ED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGM JUDGMENT OF THE CONSULTANT THE CONSULTATIVE SERVICES DO NOT WARRANT A FULL');
INSERT INTO `bcpEligibleCodes` VALUES ('33313', 'Gastroenterology', 'COUNSELLING-GROUP-GASTROENTEROLOGY GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS - FIRST FULL HOUR NOTE: START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE');
INSERT INTO `bcpEligibleCodes` VALUES ('33314', 'Gastroenterology', 'COUNSELLING-PROLONGED VISIT-GASTROENTEROLOGY PROLONGED VISIT FOR COUNSELLING (MAXIMUM, FOUR PER YEAR) NOTE:');
INSERT INTO `bcpEligibleCodes` VALUES ('33315', 'Gastroenterology', 'COUNSELLING-GROUP-GASTROENTEROLOGY GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS-SECOND H0UR, PER 1/2 HOUR OR MAJOR PORTION THEREOF');
INSERT INTO `bcpEligibleCodes` VALUES ('33360', 'Gastroenterology', 'TELEHEALTH GASTROENTEROLOGY CONSULTATION: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('33362', 'Gastroenterology', 'TELEHEALTH GASTROENTEROLOGY REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE');
INSERT INTO `bcpEligibleCodes` VALUES ('33367', 'Gastroenterology', 'TELEHEALTH GASTROENTEROLOGY SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('311', 'General Internal Medicine', 'GIM - COMPLEX CONSULTATION - 3 MEDICAL CONDITIONS NOTES: I) PAYABLE ONLY FOR GENERAL INTERNAL MEDICINE SPECIALISTS WHO HAVE COMPLETED 3');
INSERT INTO `bcpEligibleCodes` VALUES ('32210', 'General Internal Medicine', 'CONSULTATION, GENERAL INTERNAL MEDICINE CONSULTATION:  TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('32212', 'General Internal Medicine', 'CONSULTATION, REPEAT/LIMITED, GENERAL INTERNAL MEDICINE REPEAT OR LIMITED CONSULTATION:  WHERE A CONSULTATION FOR SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTATION, OR WHERE');
INSERT INTO `bcpEligibleCodes` VALUES ('32271', 'General Internal Medicine', 'TELEHEALTH INTERNAL MEDICINE COMPLEX CONSULTATION NOTES: I)  PAYABLE ONLY FOR GENERAL INTERNAL MEDICINE SPECIALISTS WHO HAVE COMPLETED 3');
INSERT INTO `bcpEligibleCodes` VALUES ('32307', 'General Internal Medicine', 'SUBSEQUENT FOLLOW UP OFFICE VISIT COMPLEX PATIENT - 3 MEDICAL CONDITIONS NOTES: I)  PAYABLE ONLY FOR GENERAL INTERNAL MEDICINE SPECIALISTS WHO HAVE COMPLETED');
INSERT INTO `bcpEligibleCodes` VALUES ('32370', 'General Internal Medicine', 'TELEHEALTH CONSULTATION, GENERAL INTERNAL MEDICINE TELEHEALTH CONSULTATION:  TO CONSIST OF EXAMINIATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A');
INSERT INTO `bcpEligibleCodes` VALUES ('32372', 'General Internal Medicine', 'TELEHEALTH REPEAT/LIMITED CONSULT, GENERAL INTERNAL MEDICINE TELEHEALTH REPEAT OR LIMITED CONSULTATION:  WHERE A CONSULTATION FOR SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTATION');
INSERT INTO `bcpEligibleCodes` VALUES ('62', 'General Practice', 'ADOPTION EXAMINATION');
INSERT INTO `bcpEligibleCodes` VALUES ('64', 'General Practice', 'ADOPTION - SUBSEQUENT EXAM');
INSERT INTO `bcpEligibleCodes` VALUES ('100', 'General Practice', 'VISIT IN OFFICE: AGE 2 - 49 FOR ANY CONDITION(S) REQUIRING PARTIAL OR REGIONAL EXAMINATION AND HISTORY - INCLUDES BOTH INITIAL AND SUBSEQUENT EXAMINATION FOR SAME OR RELATED');
INSERT INTO `bcpEligibleCodes` VALUES ('101', 'General Practice', 'COMPLETE EXAMINATION IN OFFICE: AGE 2 - 49 FOR ANY CONDITION SEEN REQUIRING A COMPLETE PHYSICAL EXAMINATION AND DETAILED HISTORY (TO INCLUDE TONOMETRY AND BIOMICROSCOPY WHEN PERFORMED)');
INSERT INTO `bcpEligibleCodes` VALUES ('110', 'General Practice', 'CONSULTATION IN OFFICE: AGE 2 - 49 TO INCLUDE A HISTORY AND PHYSICAL EXAMINATION, REVIEW OF X-RAYS AND LABORATORY FINDINGS AND A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('120', 'General Practice', 'FOR A PROLONGED VISIT FOR COUNSELLING (MINIMUM TIME PER VISIT - 20 MINUTES) NOTES: I)   MSP WILL PAY FOR UP TO FOUR (4) INDIVIDUAL COUNSELLING VISITS (ANY');
INSERT INTO `bcpEligibleCodes` VALUES ('121', 'General Practice', 'COUNSELLING - FOR GROUPS OF TWO OR MORE PATIENTS - FIRST FULL HOUR NOTE: START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE PATIENT\'S CHART');
INSERT INTO `bcpEligibleCodes` VALUES ('122', 'General Practice', 'COUNSELLING - FOR GROUPS OF TWO OR MORE PATIENTS - SECOND HOUR, PER 1/2 HOUR OR MAJOR PORTION THEREOF NOTE: START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE');
INSERT INTO `bcpEligibleCodes` VALUES ('12100', 'General Practice', 'VISIT IN OFFICE (AGE 0-1)');
INSERT INTO `bcpEligibleCodes` VALUES ('12101', 'General Practice', 'COMPLETE EXAMINATION IN OFFICE (AGE 0-1)');
INSERT INTO `bcpEligibleCodes` VALUES ('12110', 'General Practice', 'CONSULTATION IN OFFICE:(AGE 0-1)');
INSERT INTO `bcpEligibleCodes` VALUES ('12120', 'General Practice', 'FOR A PROLONGED VISIT FOR COUNSELLING(MINIMUM TIME PER VISIT - 20 MINUTES) NOTES: I)   MSP WILL PAY FOR UP TO FOUR (4) INDIVIDUAL COUNSELLING VISITS (ANY');
INSERT INTO `bcpEligibleCodes` VALUES ('13013', 'General Practice', 'ASSESSMENT FOR INDUCTION OF OPIOID AGONIST TREATMENT (OAT) FOR OPIOID USE DIS- ORDER INITIAL ASSESSMENT REQUIRES COMPLETE MEDICAL HISTORY, SUBSTANCE USE HIST- ORY AND APPROPRIATE TARGETED PHYSICAL EXAMINATION.  IF ASSESSMENT AND INDUCTION');
INSERT INTO `bcpEligibleCodes` VALUES ('13014', 'General Practice', 'MANAGEMENT OF OAT INDUCTION FOR OPIOID USE DISORDER THIS FEE IN PAYABLE FOR INDIVIDUAL INTERACTIONS WITH THE PATIENT DURING THE FIRST THREE DAYS OF OAT INDUCTION FOR OPIOID USE DISORDER WITHIN THE LIMITS');
INSERT INTO `bcpEligibleCodes` VALUES ('13015', 'General Practice', 'HIV/AIDS PRIMARY CARE MANAGEMENT - IN OR OUT OF OFFICE - PER HALF HOUR OR MAJOR PORTION THEREOF NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('13041', 'General Practice', 'TELEHEALTH GP IN-OFFICE GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS FIRST FULL HOUR');
INSERT INTO `bcpEligibleCodes` VALUES ('13042', 'General Practice', 'TELEHEALTH GP IN-OFFICE GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS SECOND HOUR, PER 1/2 HOUR OR MAJOR PORTION THEREOF');
INSERT INTO `bcpEligibleCodes` VALUES ('13070', 'General Practice', 'IN OFFICE ASSESSMENT IN ASSOC WITH A WSBC SERVICE');
INSERT INTO `bcpEligibleCodes` VALUES ('13075', 'General Practice', 'IN OFFICE ASSESSMENT IN ASSOC WITH A ICBC SERVICE');
INSERT INTO `bcpEligibleCodes` VALUES ('13236', 'General Practice', 'TELEHEALTH GP CONSULTATION (AGE 0-1)');
INSERT INTO `bcpEligibleCodes` VALUES ('13237', 'General Practice', 'TELEHEALTH GP VISIT (AGE 0-1)');
INSERT INTO `bcpEligibleCodes` VALUES ('13238', 'General Practice', 'TELEHEALTH GP INDIVIDUAL COUNSELLING FOR A PROLONGED VISIT FOR COUNSELLING (MINIMUM TIME PER VISIT - 20 MINUTES) (AGE 0-1)');
INSERT INTO `bcpEligibleCodes` VALUES ('13436', 'General Practice', 'TELEHEALTH GP CONSULTATION (AGE 2-49) ');
INSERT INTO `bcpEligibleCodes` VALUES ('13437', 'General Practice', 'TELEHEALTH GP VISIT (AGE 2-49)');
INSERT INTO `bcpEligibleCodes` VALUES ('13438', 'General Practice', 'TELEHEALTH GP INDIVIDUAL COUNSELLING FOR A PROLONGED VISIT FOR COUNSELLING (MINIMUM TIME PER VISIT - 20 MINUTES) (AGE 2-49)');
INSERT INTO `bcpEligibleCodes` VALUES ('13501', 'General Practice', 'MAID ASSESSMENT FEE - ASSESSOR PRESCRIBER INCLUDES ALL REQUIREMENTS OF A MAID ASSESSMENT, INCLUDING REVIEW OF MEDICAL RECORDS, PATIENT ENCOUNTER AND COMPLETION OF THE MAID ASSESSMENT RECORD');
INSERT INTO `bcpEligibleCodes` VALUES ('13502', 'General Practice', 'MAID ASSESSMENT FEE - ASSESSOR INCLUDES ALL REQUIREMENTS OF A MAID ASSESSMENT, INCLUDING REVIEW OF MEDICAL RECORDS, PATIENT ENCOUNTER AND COMPLETION OF THE MAID ASSESSMENT RECORD');
INSERT INTO `bcpEligibleCodes` VALUES ('13503', 'General Practice', 'PHYSICIAN WITNESS TO VIDEO CONFERENCE MAID ASSESSMENT - PATIENT ENCOUNTER PHYSICIAN MUST BE IN PERSONAL ATTENDANCE WITH THE PATIENT FOR THE DURATION OF THE PATIENT ENCOUNTER WITH THE ASSESSOR OR ASSESSOR PRESCRIBER.  BILLABLE ONLY');
INSERT INTO `bcpEligibleCodes` VALUES ('13536', 'General Practice', 'TELEHEALTH GP CONSULTATION (AGE 50-59)');
INSERT INTO `bcpEligibleCodes` VALUES ('13537', 'General Practice', 'TELEHEALTH GP VISIT (AGE 50-59)');
INSERT INTO `bcpEligibleCodes` VALUES ('13538', 'General Practice', 'TELEHEALTH GP INDIVIDUAL COUNSELLING FOR A PROLONGED VISIT FOR COUNSELLING (MINIMUM TIME PER VISIT - 20 MINUTES) (AGE 50-59)');
INSERT INTO `bcpEligibleCodes` VALUES ('13636', 'General Practice', 'TELEHEALTH GP CONSULTATION (AGE 60-69)');
INSERT INTO `bcpEligibleCodes` VALUES ('13637', 'General Practice', 'TELEHEALTH GP VISIT (AGE 60-69)');
INSERT INTO `bcpEligibleCodes` VALUES ('13638', 'General Practice', 'TELEHEALTH GP INDIVIDUAL COUNSELLING FOR A PROLONGED VISIT FOR COUNSELLING (MINIMUM TIME PER VISIT - 20 MINUTES) (AGE 60-69)');
INSERT INTO `bcpEligibleCodes` VALUES ('13736', 'General Practice', 'TELEHEALTH GP CONSULTATION (AGE 70-79)');
INSERT INTO `bcpEligibleCodes` VALUES ('13737', 'General Practice', 'TELEHEALTH GP VISIT (AGE 70-79)');
INSERT INTO `bcpEligibleCodes` VALUES ('13738', 'General Practice', 'TELEHEALTH GP INDIVIDUAL COUNSELLING FOR A PROLONGED VISIT FOR COUNSELLING (MINIMUM TIME PER VISIT - 20 MINUTES) (AGE 70-79)');
INSERT INTO `bcpEligibleCodes` VALUES ('13763', 'General Practice', 'GENERAL PRACTICE GROUP MEDICAL VISITS FEE PER PATIENT, PER 1/2 HOUR OR MAJOR PORTION THEREOF: THREE PATIENTS');
INSERT INTO `bcpEligibleCodes` VALUES ('13764', 'General Practice', 'GENERAL PRACTICE GROUP MEDICAL VISITS FEE PER PATIENT, PER 1/2 HOUR OR MAJOR PORTION THEREOF: FOUR PATIENTS');
INSERT INTO `bcpEligibleCodes` VALUES ('13765', 'General Practice', 'GENERAL PRACTICE GROUP MEDICAL VISITS FEE PER PATIENT, PER 1/2 HOUR OR MAJOR PORTION THEREOF: FIVE PATIENTS');
INSERT INTO `bcpEligibleCodes` VALUES ('13766', 'General Practice', 'GENERAL PRACTICE GROUP MEDICAL VISITS FEE PER PATIENT, PER 1/2 HOUR OR MAJOR PORTION THEREOF: SIX PATIENTS');
INSERT INTO `bcpEligibleCodes` VALUES ('13767', 'General Practice', 'GENERAL PRACTICE GROUP MEDICAL VISITS FEE PER PATIENT, PER 1/2 HOUR OR MAJOR PORTION THEREOF: SEVEN PATIENTS');
INSERT INTO `bcpEligibleCodes` VALUES ('13768', 'General Practice', 'GENERAL PRACTICE GROUP MEDICAL VISITS FEE PER PATIENT, PER 1/2 HOUR OR MAJOR PORTION THEREOF: EIGHT PATIENTS');
INSERT INTO `bcpEligibleCodes` VALUES ('13769', 'General Practice', 'GENERAL PRACTICE GROUP MEDICAL VISITS FEE PER PATIENT, PER 1/2 HOUR OR MAJOR PORTION THEREOF: NINE PATIENTS');
INSERT INTO `bcpEligibleCodes` VALUES ('13770', 'General Practice', 'GENERAL PRACTICE GROUP MEDICAL VISITS FEE PER PATIENT, PER 1/2 HOUR OR MAJOR PORTION THEREOF: TEN PATIENTS');
INSERT INTO `bcpEligibleCodes` VALUES ('13771', 'General Practice', 'GENERAL PRACTICE GROUP MEDICAL VISITS FEE PER PATIENT, PER 1/2 HOUR OR MAJOR PORTION THEREOF: ELEVEN PATIENTS');
INSERT INTO `bcpEligibleCodes` VALUES ('13772', 'General Practice', 'GENERAL PRACTICE GROUP MEDICAL VISITS FEE PER PATIENT, PER 1/2 HOUR OR MAJOR PORTION THEREOF: TWELVE PATIENTS');
INSERT INTO `bcpEligibleCodes` VALUES ('13773', 'General Practice', 'GENERAL PRACTICE GROUP MEDICAL VISITS FEE PER PATIENT, PER 1/2 HOUR OR MAJOR PORTION THEREOF: THIRTEEN PATIENTS');
INSERT INTO `bcpEligibleCodes` VALUES ('13774', 'General Practice', 'GENERAL PRACTICE GROUP MEDICAL VISITS FEE PER PATIENT, PER 1/2 HOUR OR MAJOR PORTION THEREOF: FOURTEEN PATIENTS');
INSERT INTO `bcpEligibleCodes` VALUES ('13775', 'General Practice', 'GENERAL PRACTICE GROUP MEDICAL VISITS FEE PER PATIENT, PER 1/2 HOUR OR MAJOR PORTION THEREOF: FIFTEEN PATIENTS');
INSERT INTO `bcpEligibleCodes` VALUES ('13776', 'General Practice', 'GENERAL PRACTICE GROUP MEDICAL VISITS FEE PER PATIENT, PER 1/2 HOUR OR MAJOR PORTION THEREOF: SIXTEEN PATIENTS');
INSERT INTO `bcpEligibleCodes` VALUES ('13777', 'General Practice', 'GENERAL PRACTICE GROUP MEDICAL VISITS FEE PER PATIENT, PER 1/2 HOUR OR MAJOR PORTION THEREOF: SEVENTEEN PATIENTS');
INSERT INTO `bcpEligibleCodes` VALUES ('13778', 'General Practice', 'GENERAL PRACTICE GROUP MEDICAL VISITS FEE PER PATIENT, PER 1/2 HOUR OR MAJOR PORTION THEREOF: EIGHTEEN PATIENTS');
INSERT INTO `bcpEligibleCodes` VALUES ('13779', 'General Practice', 'GENERAL PRACTICE GROUP MEDICAL VISITS FEE PER PATIENT, PER 1/2 HOUR OR MAJOR PORTION THEREOF: NINETEEN PATIENTS');
INSERT INTO `bcpEligibleCodes` VALUES ('13780', 'General Practice', 'GENERAL PRACTICE GROUP MEDICAL VISITS FEE PER PATIENT, PER 1/2 HOUR OR MAJOR PORTION THEREOF: TWENTY PATIENTS');
INSERT INTO `bcpEligibleCodes` VALUES ('13781', 'General Practice', 'GENERAL PRACTICE GROUP MEDICAL VISITS FEE PER PATIENT, PER 1/2 HOUR OR MAJOR PORTION THEREOF: GREATER THAN TWENTY (PER PATIENT)');
INSERT INTO `bcpEligibleCodes` VALUES ('13836', 'General Practice', 'TELEHEALTH GP CONSULTATION (AGE 80+)');
INSERT INTO `bcpEligibleCodes` VALUES ('13837', 'General Practice', 'TELEHEALTH GP VISIT (AGE 80+)');
INSERT INTO `bcpEligibleCodes` VALUES ('13838', 'General Practice', 'TELEHEALTH GP INDIVIDUAL COUNSELLING FOR A PROLONGED VISIT FOR COUNSELLING (MINIMUM TIME PER VISIT - 20 MINUTES) (AGE 80+)');
INSERT INTO `bcpEligibleCodes` VALUES ('14090', 'General Practice', 'PRENATAL VISIT - COMPLETE EXAMINATION');
INSERT INTO `bcpEligibleCodes` VALUES ('14091', 'General Practice', 'PRENATAL VISIT - SUBSEQUENT EXAMINATION NOTES: I)  UNCOMPLICATED PRE-NATAL CARE USUALLY INCLUDES A COMPLETE EXAMINATION');
INSERT INTO `bcpEligibleCodes` VALUES ('14094', 'General Practice', 'POSTNATAL OFFICE VISIT NOTES I) P14094 MAY BE BILLED IN THE SIX WEEKS FOLLOWING DELIVERY(VAGINAL OR');
INSERT INTO `bcpEligibleCodes` VALUES ('14545', 'General Practice', 'MEDICAL ABORTION');
INSERT INTO `bcpEligibleCodes` VALUES ('14560', 'General Practice', 'ROUTINE PELVIC EXAMINATION INCLUDING PAPANICOLAOU SMEAR');
INSERT INTO `bcpEligibleCodes` VALUES ('15300', 'General Practice', 'VISIT IN OFFICE (AGE 50-59)');
INSERT INTO `bcpEligibleCodes` VALUES ('15301', 'General Practice', 'COMPLETE EXAMINATION IN OFFICE (AGE 50-59)');
INSERT INTO `bcpEligibleCodes` VALUES ('15310', 'General Practice', 'CONSULTATION IN OFFICE (AGE 50-59)');
INSERT INTO `bcpEligibleCodes` VALUES ('15320', 'General Practice', 'FOR A PROLONGED VISIT FOR COUNSELLING(MINIMUM TIME PER VISIT - 20 MINUTES) NOTES: I)   MSP WILL PAY FOR UP TO FOUR (4) INDIVIDUAL COUNSELLING VISITS (ANY');
INSERT INTO `bcpEligibleCodes` VALUES ('16100', 'General Practice', 'VISIT IN OFFICE (AGE 60-69)');
INSERT INTO `bcpEligibleCodes` VALUES ('16101', 'General Practice', 'COMPLETE EXAMINATION IN OFFICE (AGE 60-69)');
INSERT INTO `bcpEligibleCodes` VALUES ('16110', 'General Practice', 'CONSULTATION IN OFFICE: (AGE 60-69)');
INSERT INTO `bcpEligibleCodes` VALUES ('16120', 'General Practice', 'FOR A PROLONGED VISIT FOR COUNSELLING(MINIMUM TIME PER VISIT - 20 MINUTES) NOTES: I)   MSP WILL PAY FOR UP TO FOUR (4) INDIVIDUAL COUNSELLING VISITS (ANY');
INSERT INTO `bcpEligibleCodes` VALUES ('17100', 'General Practice', 'VISIT IN OFFICE (AGE 70-79)');
INSERT INTO `bcpEligibleCodes` VALUES ('17101', 'General Practice', 'COMPLETE EXAMINATION IN OFFICE (AGE 70-79)');
INSERT INTO `bcpEligibleCodes` VALUES ('17110', 'General Practice', 'CONSULTATION IN OFFICE: (AGE 70-79)');
INSERT INTO `bcpEligibleCodes` VALUES ('17120', 'General Practice', 'FOR A PROLONGED VISIT FOR COUNSELLING(MINIMUM TIME PER VISIT - 20 MINUTES) NOTES: I)   MSP WILL PAY FOR UP TO FOUR (4) INDIVIDUAL COUNSELLING VISITS (ANY');
INSERT INTO `bcpEligibleCodes` VALUES ('18100', 'General Practice', 'VISIT IN OFFICE (AGE 80+)');
INSERT INTO `bcpEligibleCodes` VALUES ('18101', 'General Practice', 'COMPLETE EXAMINATION IN OFFICE (AGE 80+)');
INSERT INTO `bcpEligibleCodes` VALUES ('18110', 'General Practice', 'CONSULTATION IN OFFICE: (AGE 80+)');
INSERT INTO `bcpEligibleCodes` VALUES ('18120', 'General Practice', 'FOR A PROLONGED VISIT FOR COUNSELLING(MINIMUM TIME PER VISIT - 20 MINUTES) NOTES: I)   MSP WILL PAY FOR UP TO FOUR (4) INDIVIDUAL COUNSELLING VISITS (ANY');
INSERT INTO `bcpEligibleCodes` VALUES ('14044', 'General Practice Services Committee', 'GP MENTAL HEALTH MANAGEMENT FEE AGE 2-49 THESE FEES ARE PAYABLE FOR PROLONGED COUNCELLING VISITS (MINIMUM TIME 20 MINS) WITH PATIENT ON WHOM A MENTAL HEALTH PLANNING FEE 14043 HAS BEEN SUCCESSFULLY');
INSERT INTO `bcpEligibleCodes` VALUES ('14045', 'General Practice Services Committee', 'GP MENTAL HEALTH MANAGEMENT FEE AGE 50-59 THESE FEES ARE PAYABLE FOR PROLONGED COUNSELLING VISITS (MINIMUM TIME 20 MINUTES)WITH PATIENT ON WHOM A MENTAL HEALTH PLANNING FEE G14043 HAS BEEN SUCC');
INSERT INTO `bcpEligibleCodes` VALUES ('14046', 'General Practice Services Committee', 'GP MENTAL HEALTH MANAGEMENT FEE AGE 60-69 THESE FEES ARE PAYABLE FOR PROLONGED COUNSELLING VISITS (MINIMUM TIME 20 MINUTES)WITH PATIENT ON WHOM A MENTAL HEALTH PLANNING FEE G14043 HAS BEEN');
INSERT INTO `bcpEligibleCodes` VALUES ('14047', 'General Practice Services Committee', 'GP MENTAL HEALTH MANAGEMENT FEE AGE 70-79 THESE FEES ARE PAYABLE FOR PROLONGED COUNSELLING VISITS (MINIMUM TIME 20 MINUTES WITH PATIENT ON WHOM A MENTAL HEALTH PLANNING FEE 14043 HAS BEEN SUCCES');
INSERT INTO `bcpEligibleCodes` VALUES ('14048', 'General Practice Services Committee', 'GP MENTAL HEALTH MANAGEMENT FEE AGE 80+ THESE FEES ARE PAYABLE FOR PROLONGED COUNSELLING VISITS (MINIMUM TIME 20 MINS) WITH PATIENTS ON WHOM A MENTAL HEALTH PLANNING FEE 14043 HAS BEEN SUCCESSFULLY');
INSERT INTO `bcpEligibleCodes` VALUES ('7007', 'General Surgery', 'CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('7010', 'General Surgery', 'CONSULTATION: TO INCLUDE COMPLETE HISTORY AND PHYSICAL EXAMINATION, REVIEW OF X-RAY AND LABORATORY FINDINGS, IF REQUIRED, AND WRITTEN REPORT');
INSERT INTO `bcpEligibleCodes` VALUES ('7012', 'General Surgery', 'REPEAT OR LIMITED CONSULTATION: TO APPLY WHERE A CONSULTATION IS REPEATED FOR THE SAME CONDITION WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE');
INSERT INTO `bcpEligibleCodes` VALUES ('70070', 'General Surgery', 'TELEHEALTH GENERAL SURGERY CONSULTATION: TO INCLUDE COMPLETE HISTORY AND PHYSICAL EXAMINATION, REVIEW OF X RAY AND LABORATORY FINDINGS, IF REQUIRED, AND WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('70072', 'General Surgery', 'TELEHEALTH GENERAL SURGERY REPEAT OR LIMITED CONSULTATION: TO APPLY WHERE A CONSULTATION IS REPEATED FOR THE SAME CONDITION WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE');
INSERT INTO `bcpEligibleCodes` VALUES ('70077', 'General Surgery', 'TELEHEALTH GENERAL SURGERY SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('70080', 'General Surgery', 'TELEHEALTH COMPLEX CONSULTATION FOR MANAGEMENT OF MALIGNANCY');
INSERT INTO `bcpEligibleCodes` VALUES ('70087', 'General Surgery', 'TELEHEALTH SPECIAL OFFICE VISIT FOR NEW DIAGNOSIS OR RECURRENT MALIGNANCY NOTES: 1)  PAYABLE ONLY TO THE GENERAL SURGEON WHO IS THE MOST RESPONSIBLE');
INSERT INTO `bcpEligibleCodes` VALUES ('71010', 'General Surgery', 'COMPLEX CONSULTATION FOR MANAGEMENT OF MALIGNANCY');
INSERT INTO `bcpEligibleCodes` VALUES ('71015', 'General Surgery', 'GENERAL SURGERY PRE-OPERATIVE ASSESSMENT NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('71017', 'General Surgery', 'SPECIAL OFFICE VISIT FOR NEW DIAGNOSIS OR RECURRENT MALIGNANCY NOTES: I)   PAYABLE ONLY TO THE GENERAL SURGEON WHO IS THE MOST RESPONSIBLE PHYSICIAN');
INSERT INTO `bcpEligibleCodes` VALUES ('33401', 'Geriatric Medicine', 'COMPREHENSIVE GERIATRIC CONSULTATION LIMITED TO PATIENTS AGED 65 YEARS AND OVER . TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND  ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT WHICH REFLECTS THE NECE');
INSERT INTO `bcpEligibleCodes` VALUES ('33402', 'Geriatric Medicine', 'GERIATRIC REASSESSMENT SUBSEQUENT TO COMPREHENSIVE CONSULTATION-LIMITED TO PATIENTS AGED 65 YEARS AND OVER NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('33403', 'Geriatric Medicine', 'COMPREHENSIVE COGNITIVE CONSULTATION-FOR DEMENTIA OR COGNITIVE PROBLEMS: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONSL VISITS NECESSARY TO RENDER A WRITTEN REPORT WHICH REFLECTS THE');
INSERT INTO `bcpEligibleCodes` VALUES ('33404', 'Geriatric Medicine', 'REPEAT OR LIMITED COMPREHENSIVE COGNITIVE ASSESSMENT-FOR DEMENTIA OR COGNITIVE PROBLEMS. NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('33407', 'Geriatric Medicine', 'CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('33410', 'Geriatric Medicine', 'CONSULTATION - GERIATRIC MEDICINE CONSULTATION: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT');
INSERT INTO `bcpEligibleCodes` VALUES ('33412', 'Geriatric Medicine', 'REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEAT ED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGM ENT OF THE CONSULTANT THE CONSULTATIVE SERVICES DO NOT WARRANT A FULL CONSULTAT');
INSERT INTO `bcpEligibleCodes` VALUES ('33413', 'Geriatric Medicine', 'COUNSELLING-GROUP-GERIATRIC MEDICINE GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS - FIRST FULL HOUR NOTE:   START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND');
INSERT INTO `bcpEligibleCodes` VALUES ('33414', 'Geriatric Medicine', 'COUNSELLING-PROLONGED VISIT-GERIATRIC MEDICINE PROLONGED VISIT FOR COUNSELLING (MAXIMUM, FOUR PER YEAR) NOTE:');
INSERT INTO `bcpEligibleCodes` VALUES ('33415', 'Geriatric Medicine', 'COUNSELLING-GROUP-GERIATRIC MEDICINE GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS - SECOND HOUR, PER 1/2 HOUR OR MAJOR PORTION THEREOF');
INSERT INTO `bcpEligibleCodes` VALUES ('33421', 'Geriatric Medicine', 'TELEHEALTH COMPREHENSIVE GERIATRIC CONSULTATION - LIMITED TO PATIENTS AGED 65 YEARS AND OVER: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS AND');
INSERT INTO `bcpEligibleCodes` VALUES ('33422', 'Geriatric Medicine', 'TELEHEALTH GERIATRIC REASSESSMENT - SUBSEQUENT TO COMPREHENSIVE CONSULTATION - LIMITED TO PATIENTS AGED 65 YEARS AND OVER. NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('33423', 'Geriatric Medicine', 'TELEHEALTH COMPLEX CONSULTATION - FOR 2 OR MORE CONDITIONS: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT WHICH REFLECTS THE NECESSARY');
INSERT INTO `bcpEligibleCodes` VALUES ('33424', 'Geriatric Medicine', 'TELEHEALTH COMPLEX REPEAT OR LIMITED COMPLEX CONSULTATION - FOR 2 CONDITIONS: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGEMENT OF THE CONSULTANT THE');
INSERT INTO `bcpEligibleCodes` VALUES ('33427', 'Geriatric Medicine', 'TELEHEALTH COMPREHENSIVE OR COMPLEX SUBSEQUENT OFICE VISIT NOTES: I) PAYABLE ONLY FOR GERIATRIC MEDICINE SPECIALISTS.');
INSERT INTO `bcpEligibleCodes` VALUES ('33440', 'Geriatric Medicine', 'COMPLEX CONSULTATION -  FOR 2 OR MORE CONDITIONS: TO CONSIST OF EXAMINATION REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT WHICH REFLECTS THE NECESSARY COMPONENTS AND COMPLEXI');
INSERT INTO `bcpEligibleCodes` VALUES ('33442', 'Geriatric Medicine', 'COMPLEX REPEAT OR LIMITED COMPLEX CONSULT - FOR 2 CONDITIONS: WHERE A CONSULT- ATION FOR SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGEMENT OF THE CONSULTANT THE CONSULTATIVE');
INSERT INTO `bcpEligibleCodes` VALUES ('33447', 'Geriatric Medicine', 'COMPREHENSIVE OR COMPLEX SUBSEQUENT OFFICE VISIT NOTES: I) PAYABLE ONLY FOR GERIATRIC MEDICINE SPECIALISTS.');
INSERT INTO `bcpEligibleCodes` VALUES ('33470', 'Geriatric Medicine', 'TELEHEALTH GERIATRIC CONSULTATION: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('33472', 'Geriatric Medicine', 'TELEHEALTH GERIATRIC REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE CONSULTATIVE SERVICES DO NOT');
INSERT INTO `bcpEligibleCodes` VALUES ('33473', 'Geriatric Medicine', 'TELEHEALTH COMPREHENSIVE COGNITIVE CONSULTATION - FOR DEMENTIA OR COGNITIVE PROBLEMS: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDI NGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT WHICH REFLECTS');
INSERT INTO `bcpEligibleCodes` VALUES ('33474', 'Geriatric Medicine', 'TELEHEALTH REPEAT OR LIMITED COMPREHENSIVE COGNITIVE ASSESSMENT - FOR DEMENTIA OR COGNITIVE PROBLEMS NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('33477', 'Geriatric Medicine', 'TELEHEALTH GERIATRIC MEDICINE SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('33507', 'Hematology/Medical Oncology', 'CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('33510', 'Hematology/Medical Oncology', 'CONSULTATION - HEMATOLOGY/ONCOLOGY CONSULTATION: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, XRAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('33512', 'Hematology/Medical Oncology', 'REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEAT ED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGM ENT OF THE CONSULTANT THE CONSULTATIVE SERVICES DO NOT WARRANT A FULL CONSULTAT');
INSERT INTO `bcpEligibleCodes` VALUES ('33513', 'Hematology/Medical Oncology', 'COUNSELLING-GROUP-HEMATOLOGY/ONCOLOGY GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS - FIRST FULL HOUR');
INSERT INTO `bcpEligibleCodes` VALUES ('33514', 'Hematology/Medical Oncology', 'COUNSELLING-PROLONGED VISIT-HEMATOLOGY/ONCOLOGY PROLONGED VISIT FOR COUNSELLING (MAXIMUM, FOUR PER YEAR) NOTE:');
INSERT INTO `bcpEligibleCodes` VALUES ('33515', 'Hematology/Medical Oncology', 'COUNSELLING-GROUP-HEMATOLOGY/ONCOLOGY GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS-SECOND HOUR, PER 1/2 HOUR OR MAJOR PORTION THEREOF');
INSERT INTO `bcpEligibleCodes` VALUES ('33520', 'Hematology/Medical Oncology', 'COMPLEX CONSULTATION: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT FOR COMPLEX PATIENT');
INSERT INTO `bcpEligibleCodes` VALUES ('33522', 'Hematology/Medical Oncology', 'REPEAT OR LIMITED CONSULTATION, COMPLEX PATIENT:  WHERE A CONSULTATION FOR SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTATION, OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE CONSULTATIVE');
INSERT INTO `bcpEligibleCodes` VALUES ('33527', 'Hematology/Medical Oncology', 'SUBSEQUENT OFFICE VISIT, COMPLEX PATIENT NOTES: I)   RESTRICTED TO HEMATOLOGY AND ONCOLOGY');
INSERT INTO `bcpEligibleCodes` VALUES ('33570', 'Hematology/Medical Oncology', 'TELEHEALTH CONSULTATION (HEMATOLOGY AND ONCOLOGY): TO CONSIST OF EXAINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS,AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('33572', 'Hematology/Medical Oncology', 'TELEHEALTH REPEAT OR LIMITED CONSULTATION (HEMATOLOGY AND ONCOLOGY): WHERE A CONSULTATION FOR SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE CONSULTATIVE');
INSERT INTO `bcpEligibleCodes` VALUES ('33577', 'Hematology/Medical Oncology', 'TELEHEALTH SUBSEQUENT OFFICE VISIT (HEMATOLOGY AND ONCOLOGY)');
INSERT INTO `bcpEligibleCodes` VALUES ('33607', 'Infectious Diseases', 'CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('33610', 'Infectious Diseases', 'CONSULTATION - INFECTIOUS DISEASES CONSULTATION: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('33612', 'Infectious Diseases', 'REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEAT ED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGM ENT OF THE CONSULTANT THE CONSULTATIVE SERVICES DO NOT WARRANT A FULL CONSULTAT');
INSERT INTO `bcpEligibleCodes` VALUES ('33613', 'Infectious Diseases', 'COUNSELLING - GROUP - INFECTIOUS DISEASES GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS - FIRST FULL HOUR NOTE: START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE');
INSERT INTO `bcpEligibleCodes` VALUES ('33614', 'Infectious Diseases', 'COUNSELLING-PROLONGED VISIT-INFECTIOUS DISEASES PROLONGED VISIT FOR COUNSELLING (MAXIMUM, FOUR PER YEAR) NOTE:');
INSERT INTO `bcpEligibleCodes` VALUES ('33615', 'Infectious Diseases', 'COUNSELLING - GROUP INFECTIOUS DISEASES GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS - SECOND HOUR, PER 1/2 HOUR OR MAJOR PORTION THEREOF');
INSERT INTO `bcpEligibleCodes` VALUES ('33620', 'Infectious Diseases', 'INFECTIONS DISEASE EXTENDED CONSULTATION FOR COMPLEX INFECTIOUS DISEASE ISSUES (ANTIBIOTIC RESISTANT ORGANISMS, OUTBREAK MANAGEMENT/INFECTION CONTROL, TROPICA L DISEAE MANAGEMENT), WHEN REQUESTED BY ANOTHER INFECTIOUS DISEASES SPECIALIST,');
INSERT INTO `bcpEligibleCodes` VALUES ('33630', 'Infectious Diseases', 'TELEHEALTH CONSULTATION: SHALL INCLUDE A DETAILED HISTORY AND PHYSICAL EXAMINATION, REVIEW OF PREVIOUS MEDICAL RECORDS, DISCUSSION WITH FAMILY, FRIENDS OR WITNESSES, EVALUATION OF APPROPRIATE LABORATORY, X-RAY AND ECG');
INSERT INTO `bcpEligibleCodes` VALUES ('33632', 'Infectious Diseases', 'TELEHEALTH REPEAT OR LIMITED CONSULTATION: TO APPLY WHERE A CONSULTATION IS REPEATED FOR THE SAME CONDITION WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE CONSULTATIVE');
INSERT INTO `bcpEligibleCodes` VALUES ('33637', 'Infectious Diseases', 'TELEHEALTH SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('33645', 'Infectious Diseases', 'INFECTIOUS DISEASE CARE MANAGEMENT OF HIV/AIDS-PER HALF HOUR - PER HALF HOUR OR MAJOR PORTION THEREOF NOTES');
INSERT INTO `bcpEligibleCodes` VALUES ('307', 'Internal Medicine', 'CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('310', 'Internal Medicine', 'CONSULTATION:  TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A');
INSERT INTO `bcpEligibleCodes` VALUES ('312', 'Internal Medicine', 'REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE CONSULTATIVE SERVICES DO NOT WARRANT A FULL');
INSERT INTO `bcpEligibleCodes` VALUES ('313', 'Internal Medicine', 'GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS - FIRST FULL HOUR NOTE: START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE PATIENT\'S CHART');
INSERT INTO `bcpEligibleCodes` VALUES ('314', 'Internal Medicine', 'PROLONGED VISIT FOR COUNSELLING (MAXIMUM, FOUR PER YEAR) NOTES: I)  SEE PREAMBLE, CLAUSE D.3.3.');
INSERT INTO `bcpEligibleCodes` VALUES ('315', 'Internal Medicine', 'GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS- SECOND HOUR, PER 1/2 HOUR OR MAJOR PORTION THEREOF NOTE:');
INSERT INTO `bcpEligibleCodes` VALUES ('32270', 'Internal Medicine', 'TELEHEALTH INTERNAL MEDICINE CONSULTATION:  TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('32272', 'Internal Medicine', 'TELEHEALTH INTERNAL MEDICINE REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE CONSULTATIVE');
INSERT INTO `bcpEligibleCodes` VALUES ('32277', 'Internal Medicine', 'TELEHEALTH INTERNAL MEDICINE SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('94007', 'Laboratory Medicine', 'LABORATORY MEDICINE, SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('94010', 'Laboratory Medicine', 'CONSULTATION:  TO CONSIST OF EXAMINATION, REVIEW OF HISTORY AND LABORATORY FINDINGS WITH A WRITTEN REPORT');
INSERT INTO `bcpEligibleCodes` VALUES ('94012', 'Laboratory Medicine', 'REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEATED WITHIN SIX (6) MONTHS OF THE LAST VISIT BY');
INSERT INTO `bcpEligibleCodes` VALUES ('94070', 'Laboratory Medicine', 'TELEHEALTH LABORATORY MEDICINE CONSULTATION: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY AND LABORATORY FINDINGS WITH A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('94072', 'Laboratory Medicine', 'TELEHEALTH LABORATORY MEDICINE REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEATED WITHIN SIX (6) MONTHS OF THE LAST VISIT BY THE CONSULTANT OR WHERE, IN THE JUDGMENT OF THE CONSULTANT, THE');
INSERT INTO `bcpEligibleCodes` VALUES ('94077', 'Laboratory Medicine', 'TELEHEALTH LABORATORY MEDICINE SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('33707', 'Nephrology', 'CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('33710', 'Nephrology', 'CONSULTATION - NEPHROLOGY CONSULTATION: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('33712', 'Nephrology', 'REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEAT ED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGM ENT OF THE CONSULTANT THE CONSULTATIVE SERVICES DO NOT WARRANT A FULL CONSULTAT');
INSERT INTO `bcpEligibleCodes` VALUES ('33713', 'Nephrology', 'COUNSELLING-GROUP-NEPHROLOGY GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS - FIRST FULL HOUR NOTE:   START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE');
INSERT INTO `bcpEligibleCodes` VALUES ('33714', 'Nephrology', 'COUNSELLING-PROLONGED VISIT-NEPHROLOGY PROLONGED VISIT FOR COUNSELLING (MAXIMUM, FOUR PER YEAR) NOTE:');
INSERT INTO `bcpEligibleCodes` VALUES ('33715', 'Nephrology', 'COUNSELLING-GROUP-NEPHROLOGY GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS-SECOND HOUR, PER 1/2 HOUR OR MAJOR PORTION THEREOF');
INSERT INTO `bcpEligibleCodes` VALUES ('33730', 'Nephrology', 'TELEHEALTH CONSULTATION:  SHALL INCLUDE A DETAILED HISTORY AND PHYSICAL EXAMINATION, REVIEW OF PREVIOUS MEDICAL RECORDS, DISCUSSION WITH FAMILY, FRIENDS OR WITNESSES, EVALUATION OF APPROPRIATE LABORATORY, X-RAY AND ECG');
INSERT INTO `bcpEligibleCodes` VALUES ('33732', 'Nephrology', 'TELEHEALTH REPEAT OR LIMITED CONSULTATION: TO APPLY WHERE A CONSULTATION IS REPEATED FOR THE SAME CONDITION WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE CONSULTATIVE SERVICE');
INSERT INTO `bcpEligibleCodes` VALUES ('33737', 'Nephrology', 'TELEHEALTH SUBSEQUENT HOSPITAL VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('407', 'Neurology', 'CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('410', 'Neurology', 'CONSULTATION:  TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO');
INSERT INTO `bcpEligibleCodes` VALUES ('411', 'Neurology', 'REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR THE SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST SERVICE');
INSERT INTO `bcpEligibleCodes` VALUES ('450', 'Neurology', 'NEUROLOGY COMPLEX CARE-EXTENDED CONSULTATION - PER 15 MINUTES OR MAJOR PORTION THEREOF NOTES');
INSERT INTO `bcpEligibleCodes` VALUES ('457', 'Neurology', 'COMPLEX CARE - EXTENDED VISIT- PER 15 MINUTES OR MAJOR PORTION THEREOF NOTES: I) PAID IN ADDITION TO 00406, 00407, 00408, 00409, 00476, 00477 OR 00478 AFTER');
INSERT INTO `bcpEligibleCodes` VALUES ('460', 'Neurology', 'TRANSFER OF CARE FROM PEDIATRICS - EXTENDED CONSULTATION: TO CONSIST OF AN EXAMINATION, REVIEW OF HISTORY, PREVIOUS LABORATORY & X-RAY FINDINGS, AND WRITTEN REPORT ON A PATIENT WITH A COMPLEX AND CHRONIC NEUROLOGIC');
INSERT INTO `bcpEligibleCodes` VALUES ('470', 'Neurology', 'TELEHEALTH CONSULTATION, TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATO RY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('471', 'Neurology', 'TELEHEALTH REPEAT / LIMITED CONSULTATION NEUROLOGY: WHERE A CONSULTATION FOR THE SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST SERVICE BY THE CONSU LTANT, OR WHERE IN THE JUDGEMENT OF THE CONSULTANT THE CONSULTATIVE SERVICE DOE');
INSERT INTO `bcpEligibleCodes` VALUES ('477', 'Neurology', 'TELEHEALTH SUBSEQUENT OFFICE VISIT, NERUOLOGY');
INSERT INTO `bcpEligibleCodes` VALUES ('485', 'Neurology', 'FACE TO FACE ASSESSMENT FOR ACUTE DETERIORATION IN STATUS OF AN MS PATIENT- 1ST FULL HALF HOUR.  TO CONSIST OF ACUTE ASSESSMENT, EXAMINATION INCLUDING EDSS REVIEW OF HISTORY, LABORATORY TESTING AND DIAGNOSTIC IMAGING, AND THE RENDERING');
INSERT INTO `bcpEligibleCodes` VALUES ('486', 'Neurology', 'FACE TO FACE ASSESSMENT FOR ACUTE DETERIORATION IN STATUS OF AN MS PATIENT- EACH ADDITIONAL HALF HOUR OR MAJOR PORTION THEREOF. NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('487', 'Neurology', 'DETAILED COGNITIVE ASSESSMENT BY BEHAVIORAL NEUROLOGIST NOTES: I)   RESTRICTED TO PRACTITIONERS WITH A SUBSPECIALTY IN BEHAVIORAL NEUROLOGY.');
INSERT INTO `bcpEligibleCodes` VALUES ('488', 'Neurology', 'DETAILED COGNITIVE ASSESSMENT- EXTRA NOTES: I)   RESTRICTED TO NEUROLOGISTS.');
INSERT INTO `bcpEligibleCodes` VALUES ('491', 'Neurology', 'DETAILED PARKINSON\'S DISEASE QUANTITATIVE REVIEW FOR NEUROLOGISTS WITH A MOVE- MENT DISORDER (MD) FELLOWSHIP - EXTRA NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('492', 'Neurology', 'DETAILED PARKINSON\'S DISEASE QUANTITATIVE REVIEW - EXTRA NOTES: I)   RESTRICTED TO NEUROLOGISTS.');
INSERT INTO `bcpEligibleCodes` VALUES ('3007', 'Neurosurgery', 'CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('3010', 'Neurosurgery', 'CONSULTATION:  TO INCLUDE COMPLETE HISTORY AND PHYSICAL EXAMINATION, REVIEW OF X-RAY AND LABORATORY FINDINGS, AND A WRITTEN REPORT');
INSERT INTO `bcpEligibleCodes` VALUES ('3011', 'Neurosurgery', 'REPEAT OR LIMITED CONSULTATION:  TO APPLY WHERE A CONSULTATION IS REPEATED FOR SAME CONDITION WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT,');
INSERT INTO `bcpEligibleCodes` VALUES ('3310', 'Neurosurgery', 'TELEHEALTH CONSULTATION: TO INCLUDE COMPLETE HISTORY AND PHYSICAL EXAMINATION, REVIEW OF X-RAY AND LABORATORY FINDINGS, AND A WRITTEN REPORT');
INSERT INTO `bcpEligibleCodes` VALUES ('3312', 'Neurosurgery', 'TELEHEALTH REPEAT OR LIMITED CONSULTATION: TO APPLY WHERE A CONSULTATION IS REPEATED FOR SAME CONDITION WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULT- ANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE CONSULTATIVE SERVICE DOES');
INSERT INTO `bcpEligibleCodes` VALUES ('3315', 'Neurosurgery', 'NEUROSURGERY PRE-OPERATIVE ASSESSMENT NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('3317', 'Neurosurgery', 'TELEHEALTH SUBSEQUENT OFICE VISIT - NEUROSURGERY');
INSERT INTO `bcpEligibleCodes` VALUES ('4007', 'Obstetrics and Gynecology', 'SUBSEQUENT OFFICE VISIT (FOR GYNECOLOGY VISITS ONLY, ALL PREGNANT PATIENTS AND ROUTINE PRE-NATAL PATIENTS BILLED UNDER FEE ITEM 14091)');
INSERT INTO `bcpEligibleCodes` VALUES ('4010', 'Obstetrics and Gynecology', 'CONSULTATION:  TO INCLUDE COMPLETE HISTORY AND GYNAECOLOGICAL EXAMINATION, REVIEW OF X-RAY AND LABORATORY FINDINGS, IF REQUIRED,');
INSERT INTO `bcpEligibleCodes` VALUES ('4012', 'Obstetrics and Gynecology', 'REPEAT OR LIMITED CONSULTATION:  TO APPLY WHERE A CONSULTATION IS REPEATED FOR SAME CONDITION WITHIN SIX MONTHS OF THE LAST VISIT BY');
INSERT INTO `bcpEligibleCodes` VALUES ('4070', 'Obstetrics and Gynecology', 'TELEHEALTH OBSTETRICS AND GYNECOLOGY CONSULTATION: TO INCLUDE COMPLETE HISTORY AND GYNECOLOGICAL EXAMINATION, REVIEW OF X RAY AND LABORATORY FINDINGS, IF REQUIRED, AND A WRITTEN REPORT OR CONSULTATION DURING LABOUR.');
INSERT INTO `bcpEligibleCodes` VALUES ('4072', 'Obstetrics and Gynecology', 'TELEHEALTH OBSTETRICS AND GYNECOLOGY REPEAT OR LIMITED CONSULTATION: TO APPLY WHERE A CONSULTATION IS REPEATED FOR SAME CONDITION WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE');
INSERT INTO `bcpEligibleCodes` VALUES ('4077', 'Obstetrics and Gynecology', 'TELEHEALTH SUBSEQUENT OFFICE VISIT (FOR GYNECOLOGY VISITS ONLY)');
INSERT INTO `bcpEligibleCodes` VALUES ('4717', 'Obstetrics and Gynecology', 'PRENATAL OFFICE VISIST FOR COMPLEX OBSTETRICAL PATIENT NOTES: I)  PAID ONLY FOR THE FOLLOWING DIAGNOSES:');
INSERT INTO `bcpEligibleCodes` VALUES ('33907', 'Occupational Medicine', 'VISIT-OFFICE-OCCUPATIONAL MEDICINE CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('33910', 'Occupational Medicine', 'CONSULTATION-OCCUPATIONAL MEDICINE CONSULTATION: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('33912', 'Occupational Medicine', 'REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEAT ED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGM ENT OF THE CONSULTANT THE CONSULTATIVE SERVICES DO NOT WARRANT A FULL CONSULTAT');
INSERT INTO `bcpEligibleCodes` VALUES ('2007', 'Ophthalomology', 'CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('2010', 'Ophthalomology', 'CONSULTATION: TO INCLUDE HISTORY, EYE EXAMINATION, REVIEW OF X-RAYS AND LABORATORY FINDINGS AND IN ADDITION WHERE INDICATED AN NECESSARY, ANY OR ALL OF MEASUREMENT FOR REFRACTIVE ERROR, OPHTHALMOSCOPY, BIOMICROSCOPY, TONOMETRY,');
INSERT INTO `bcpEligibleCodes` VALUES ('2011', 'Ophthalomology', 'REPEAT OR LIMITED CONSULTATION: TO APPLY WHERE A CONSULTATION IS REPEATED FOR SAME CONDITION WITHIN  SIX MONTHS OF THE LAST VISIT TO THE');
INSERT INTO `bcpEligibleCodes` VALUES ('2012', 'Ophthalomology', 'SPECIAL CONSULTATION: TO APPLY WHEN A OPHTHALMOLOGIST, NEUROLOGIST, PEDIATRIC NEUROLOGIST OR A NEUROSURGEON REFERS A PATIENT TO AN OPHTHALMOLOGIST FOR SPECIAL EXAMINATION, OR WHEN AN OPHTHALMOLOGIST REFERS A PATIENT TO ANOTHER');
INSERT INTO `bcpEligibleCodes` VALUES ('22007', 'Ophthalomology', 'TELEHEALTH SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('22010', 'Ophthalomology', 'TELEHEALTH CONSULTATION: TO INCLUDE HISTORY, EYE EXAMINATION, REVIEW OF X RAYS AND LABORATORY FINDINGS AND ANY OR ALL OF MEASUREMENT FOR');
INSERT INTO `bcpEligibleCodes` VALUES ('22011', 'Ophthalomology', 'TELEHEALTH REPEAT OR LIMITED CONSULTATION: TO APPLY WHERE A CONSULTATION IS REPEATED FOR SAME CONDITION WITHIN SIX MONTHS OF THE LAST VISIT TO THE');
INSERT INTO `bcpEligibleCodes` VALUES ('22118', 'Ophthalomology', 'LASER FOLLOW-UP VISIT NOTE: I) CAN BE BILLED ONCE ONLY DURING SIX WEEKS FOLLOWING LASER TREATMENT.');
INSERT INTO `bcpEligibleCodes` VALUES ('51005', 'Orthopedics', 'ORTHOPEDICS PRE-OPERATIVE ASSESSMENT NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('51007', 'Orthopedics', 'ORTHOPAEDIC OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('51010', 'Orthopedics', 'CONSULTATION:  (IN OFFICE OR HOSPITAL) TO INCLUDE A HISTORY AND PHYSICAL EXAMINATION, REVIEW OF X-RAY AND LABORATORY FINDINGS, AND A WRITTEN REPORT');
INSERT INTO `bcpEligibleCodes` VALUES ('51012', 'Orthopedics', 'REPEAT OR LIMITED CONSULTATION:  TO APPLY WHERE A CONSULTATION IS REPEATED FOR SAME CONDITION WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE, IN THE JUDGMENT OF THE CONSULTANT, THE CONSULTATIVE SERVICE DOES NOT');
INSERT INTO `bcpEligibleCodes` VALUES ('51015', 'Orthopedics', 'ORTHOPAEDIC SPECIAL CONSULTATION: EXTENDED CONSULT FOR COMPLEX PROBLEMS (I.E. ONCOLOGY, COMPLEX TRAUMA, ADULT CEREBRAL PALSY, ETC.), WHEN REQUESTED BY ANOTHER ORTHOPAEDIC SURGEON, NEUROSURGEON, PLASTIC SURGEON OR REHABILITATION');
INSERT INTO `bcpEligibleCodes` VALUES ('2215', 'Otolaryngology', 'OTOLARYNGOLOGY PRE-OPERATIVE ASSESSMENT NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('2507', 'Otolaryngology', 'CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('2510', 'Otolaryngology', 'CONSULTATION: TO INCLUDE HISTORY, DETAILED EXAMINATION OF THE EAR, NOSE, AND THROAT, REVIEW OF X-RAY AND LABORATORY FINDINGS,');
INSERT INTO `bcpEligibleCodes` VALUES ('2511', 'Otolaryngology', 'CONSULTATION WITH PURE TONE AUDIOGRAM');
INSERT INTO `bcpEligibleCodes` VALUES ('2512', 'Otolaryngology', 'SPECIAL CONSULTATION FOR DIZZINESS: TO APPLY WHERE A PATIENT HAS BEEN REFERRED BY AN OTOLARYNGOLOGIST OR A NEUROLOGIST OR A');
INSERT INTO `bcpEligibleCodes` VALUES ('2513', 'Otolaryngology', 'CONSULTATION FOR MANAGEMENT OF MALIGNANCY NOTES: I)   PAYABLE TO THE SURGEON IN CHARGE.');
INSERT INTO `bcpEligibleCodes` VALUES ('2514', 'Otolaryngology', 'REPEAT OR LIMITED CONSULTATION: TO APPLY WHERE A CONSULTATION IS REPEATED FOR SAME CONDITION WITHIN SIX MONTHS OF THE LAST VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('2515', 'Otolaryngology', 'OTOLARYNGIC ALLERGY CONSULTATION TO INCLUDE A DETAILED HISTORY AND PHYSICAL EXA M WITH REVIEW OF LABORATORY AND OTHER RELEVANT INVESTIGATIONS, PLUS APPROPRIATE  OTOLARYNGIC ALLERGY MANAGEMENT AND ADDITIONAL VISITS NECESSARY TO RENDER A WRI');
INSERT INTO `bcpEligibleCodes` VALUES ('2517', 'Otolaryngology', 'CONSULTATION FOR MANAGEMENT OF COMPLEX LARYNGEAL DISORDER NOTES: I)  TO APPLY WHERE A PATIENT HAS BEEN REFERRED BY ANOTHER OTOLARYNGOLOGIST,');
INSERT INTO `bcpEligibleCodes` VALUES ('2519', 'Otolaryngology', 'COMPLEX LARYNGEAL DISORDER CONFERENCE FEE-PER 15 MINUTES OR GREATER PORTION THEREOF NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('507', 'Pediatrics', 'CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('510', 'Pediatrics', 'CONSULTATION:  TO CONSIST OF AN EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A');
INSERT INTO `bcpEligibleCodes` VALUES ('511', 'Pediatrics', 'CONSULTATION FOR COMPLEX BEHAVIOURAL, DEVELOPMENTAL OR PSYCHIATRIC CONDITION IN A CHILD: TO CONSIST OF A PHYSICAL AND NEUROLOGICAL EXAMINATION, REVIEW OF HI STORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A');
INSERT INTO `bcpEligibleCodes` VALUES ('512', 'Pediatrics', 'REPEAT OR LIMITED CONSULTATION:  WHERE A FORMAL CONSULTATION FOR THE SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY');
INSERT INTO `bcpEligibleCodes` VALUES ('513', 'Pediatrics', 'GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS - FIRST FULL HOUR NOTE:  START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING        CLAIMS AND THE PATIENT\'S CHART.');
INSERT INTO `bcpEligibleCodes` VALUES ('514', 'Pediatrics', 'PROLONGED VISIT FOR COUNSELLING NOTE: I)   THE PLAN WILL PAY UP TO FOUR SUCH VISITS PER YEAR.');
INSERT INTO `bcpEligibleCodes` VALUES ('515', 'Pediatrics', 'GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS- SECOND HOUR, PER 1/2 HOUR OR MAJOR PORTION THEREOF NOTE:  START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND');
INSERT INTO `bcpEligibleCodes` VALUES ('550', 'Pediatrics', 'EXTENDED CONSULTATION - EXCEEDING 53 MINUTES (ACTUAL TIME SPENT WITH PATIENT): TO CONSIST OF AN EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT');
INSERT INTO `bcpEligibleCodes` VALUES ('551', 'Pediatrics', 'EXTENDED CONSULTATION - EXCEEDING 68 MINUTES (ACTUAL TIME SPENT WITH PATIENT): TO CONSIST OF AN EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT');
INSERT INTO `bcpEligibleCodes` VALUES ('552', 'Pediatrics', 'COMPLEX SUBSEQUENT OFFICE VISIT - EXCEEDING 12 MINUTES (AT LEAST 10 MINUTES SPENT WITH PATIENT). NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('553', 'Pediatrics', 'EXTENDED SUBSEQUENT OFFICE VISIT - EXCEEDING 23 MINUTES (AT LEAST 20 MINUTES SPENT WITH PATIENT) NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('554', 'Pediatrics', 'EXTENDED SUBSEQUENT OFFICE VISIT - EXCEEDING 38 MINUTES (AT LEAST 30 MINUTES SPENT WITH PATIENT). NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('590', 'Pediatrics', 'ANTENATAL CONSULTATION TO CONSIST OF AN APPROPRIATE EXAMINATION, REVIEW OF HISTORY, LABORATORY IMAGING STUDIES, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT');
INSERT INTO `bcpEligibleCodes` VALUES ('597', 'Pediatrics', 'ANTENATAL FOLLOW-UP VISIT NOTE: PAYABLE IN CASES OF PREMATURITY OR FETAL ANOMALY.');
INSERT INTO `bcpEligibleCodes` VALUES ('50507', 'Pediatrics', 'TELEHEALTH SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('50510', 'Pediatrics', 'TELEHEALTH CONSULTATION: TO CONSIST OF AN EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A');
INSERT INTO `bcpEligibleCodes` VALUES ('50511', 'Pediatrics', 'TELEHEALTH CONSULTATION FOR COMPLEX BEHAVIOURAL, DEVELOPMENTAL OR PSYCHIATRIC CONDITION IN A CHILD: TO CONSIST OF A PHYSICAL AND NEUROLOGICAL EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY');
INSERT INTO `bcpEligibleCodes` VALUES ('50512', 'Pediatrics', 'TELEHEALTH REPEAT OR LIMITED CONSULTATION: WHERE A FORMAL CONSULTATION FOR THE SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY');
INSERT INTO `bcpEligibleCodes` VALUES ('50514', 'Pediatrics', 'TELEHEALTH PROLONGED VISIT FOR COUNSELLING NOTE: I)    THE PLAN WILL PAY UP TO FOUR SUCH VISITS PER YEAR.');
INSERT INTO `bcpEligibleCodes` VALUES ('50515', 'Pediatrics', 'TELEHEALTH EXTENDED CONSULTATION - EXCEEDING 53 MINUTES   (ACTUAL TIME SPENT WITH PATIENT): TO CONSIST OF AN EXAMINATION, REVIEW OF    HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO');
INSERT INTO `bcpEligibleCodes` VALUES ('50516', 'Pediatrics', 'TELEHEALTH EXTENDED CONSULTATION- EXCEEDING 68 MINUTES (ACTUAL TIME SPENT WITH PATIENT): TO CONSIST OF AN EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER');
INSERT INTO `bcpEligibleCodes` VALUES ('50517', 'Pediatrics', 'TELEHEALTH COMPLEX SUBSEQUENT OFFICE VISIT - EXCEEDING 12 MIN (AT LEAST 10 MIN.  SPENT WITH PATIENT). NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('50518', 'Pediatrics', 'TELEHEALTH-EXTENDED SUBSEQUENT OFFICE VISIT - EXCEEDING 23 MINUTES (AT LEAST 20 MINS SPENT WITH PATIENT). NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('50519', 'Pediatrics', 'TELEHEALTH EXTENDED SUBSEQUENT OFFICE VISIT - EXCEEDING 38 MINS (AT LEAST 30 MINUTES SPENT WITH PATIENT) NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('1770', 'Physical Medicine', 'TELEHEALTH PHYSICAL MEDICINE FORMAL CONSULTATION: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X RAY FINDINGS, FUNCTIONAL, SOCIAL, AND VOCATIONAL APPRAISAL, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN');
INSERT INTO `bcpEligibleCodes` VALUES ('1772', 'Physical Medicine', 'TELEHEALTH PHYSICAL MEDICINE REPEAT OR LIMITED CONSULTATION: WHERE A FORMAL CONSULTATION FOR THE SAME ILLNESS IS REPEATED AT AN INTERVAL WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT.');
INSERT INTO `bcpEligibleCodes` VALUES ('1777', 'Physical Medicine', 'TELEHEALTH OFFICE VISIT - PHYSICAL MEDICINE');
INSERT INTO `bcpEligibleCodes` VALUES ('1707', 'Physical Medicine & Rehab', 'CONTINUING CARE BY CONSULTANT: OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('1710', 'Physical Medicine & Rehab', 'FORMAL CONSULTATION:  TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, FUNCTIONAL, SOCIAL, AND');
INSERT INTO `bcpEligibleCodes` VALUES ('1712', 'Physical Medicine & Rehab', 'REPEAT OR LIMITED CONSULTATION:  WHERE A FORMAL CONSULTATION FOR THE SAME ILLNESS IS REPEATED AT AN INTERVAL WITHIN SIX MONTHS OF');
INSERT INTO `bcpEligibleCodes` VALUES ('1713', 'Physical Medicine & Rehab', 'GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS: FIRST FULL HOUR NOTE:   START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE PATIENT\'S CHART.');
INSERT INTO `bcpEligibleCodes` VALUES ('1714', 'Physical Medicine & Rehab', 'PROLONGED VISIT FOR COUNSELLING (UP TO FOUR ANNUALLY. SEE PREAMBLE, B.4.C.) NOTE:   START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE');
INSERT INTO `bcpEligibleCodes` VALUES ('1715', 'Physical Medicine & Rehab', 'GROUP COUNSELLING FOR GROUPS OF TWO OR MORE PATIENTS: SECOND HOUR, PER 1/2 HOUR (OR MAJOR PORTION THEREOF) NOTE:   START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE');
INSERT INTO `bcpEligibleCodes` VALUES ('6007', 'Plastic Surgery', 'CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('6010', 'Plastic Surgery', 'MAJOR CONSULTATION:  TO INCLUDE COMPLETE HISTORY AND PHYSICAL EXAMINATION, REVIEW OF X-RAY AND LABORATORY FINDINGS, IF REQUIRED, AND A WRITTEN');
INSERT INTO `bcpEligibleCodes` VALUES ('6012', 'Plastic Surgery', 'REPEAT OR LIMITED CONSULTATION:  TO APPLY WHERE A CONSULTATION IS REPEATED FOR SAME CONDITION WITHIN SIX (6) MONTHS OF LAST VISIT BY THE');
INSERT INTO `bcpEligibleCodes` VALUES ('66007', 'Plastic Surgery', 'TELEHEALTH SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('66010', 'Plastic Surgery', 'TELEHEALTH MAJOR CONSULTATION:  TO INCLUDE COMPLETE HISTORY AND PHYSICAL EXAMINATION, REVIEW OF X-RAY AND LABORATORY FINDINGS, IF REQUIRED, AND A WRITTEN');
INSERT INTO `bcpEligibleCodes` VALUES ('66012', 'Plastic Surgery', 'TELEHEALTH REPEAT OR LIMITED CONSULTATION: TO APPLY WHERE A CONSULTATION IS REPEATED FOR SAME CONDITION WITHIN SIX (6) MONTHS OF LAST VISIT BY THE');
INSERT INTO `bcpEligibleCodes` VALUES ('66015', 'Plastic Surgery', 'PLASTIC SURGERY PRE-OPERATIVE ASSESSMENT NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('607', 'Psychiatry', 'OFFICE VISIT TO INCLUDE SERVICES SUCH AS CHEMOTHERAPY MANAGEMENT AND /OR MINIMAL PSYCHOTHERAPY');
INSERT INTO `bcpEligibleCodes` VALUES ('610', 'Psychiatry', 'FULL CONSULTATION - INDIVIDUAL:  DIAGNOSTIC INTERVIEW OR EXAMINATION, INCLUDING HISTORY, MENTAL STATUS EXAM AND TREATMENT RECOMMENDATION, WITH WRITTEN REPORT. PRIVATE OFFICE OR HOSPITAL OUT-PATIENT');
INSERT INTO `bcpEligibleCodes` VALUES ('611', 'Psychiatry', 'EXTENDED ADULT PSYCHIATRY CONSULTATION > 68 MINUTES NOTE:   PAYABLE ONLY TO PATIENTS 18 YEARS OF AGE AND OLDER NOTE:   START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE');
INSERT INTO `bcpEligibleCodes` VALUES ('613', 'Psychiatry', 'FULL CONSULTATION - INDIVIDUAL:  DIAGNOSTIC INTERVIEW OR EXAMINATION, INCLUDING HISTORY, MENTAL STATUS EXAM AND TREATMENT RECOMMENDATION, WITH WRITTEN REPORT. GERIATRIC CONSULTATION (PATIENTS 75 YEARS OR OLDER)');
INSERT INTO `bcpEligibleCodes` VALUES ('614', 'Psychiatry', 'GERIATRIC (SEE 00613) REPEAT OR LIMITED CONSULTATION - WHERE A FORMAL CONSULTATION FOR THE SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR');
INSERT INTO `bcpEligibleCodes` VALUES ('622', 'Psychiatry', 'FULL CONSULTATION - EMOTIONALLY DISTURBED CHILD: DIAGNOSTIC INTERVIEW OR EXAMINATION, INCLUDING MENTAL STATUS AND TREATMENT');
INSERT INTO `bcpEligibleCodes` VALUES ('623', 'Psychiatry', 'FULL CONSULTATION - MULTIPLE DISTURBED FAMILY (THREE OR MORE MEMBERS): SIMULTANEOUS DIAGNOSTIC INTERVIEWS OR EXAMINATION, INCLUDING MENTAL STATUS OF THE MEMBERS, THEIR INTERACTIONS, AND WRITTEN REPORT');
INSERT INTO `bcpEligibleCodes` VALUES ('625', 'Psychiatry', 'INDIVIDUAL (SEE 00610 AND 00615) REPEAT OR LIMITED CONSULTATION - WHERE A FORMAL CONSULTATION FOR THE SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR');
INSERT INTO `bcpEligibleCodes` VALUES ('626', 'Psychiatry', 'EMOTIONALLY DISTURBED CHILD (SEE 00622) REPEAT OR LIMITED CONSULTATION - WHERE A FORMAL CONSULTATION FOR THE SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR');
INSERT INTO `bcpEligibleCodes` VALUES ('627', 'Psychiatry', 'MULTIPLE DISTURBED FAMILY (SEE 00623) REPEAT OR LIMITED CONSULTATION - WHERE A FORMAL CONSULTATION FOR THE SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR');
INSERT INTO `bcpEligibleCodes` VALUES ('630', 'Psychiatry', 'PSYCHIATRIC TREATMENT - INDIVIDUAL (OFFICE OR HOSPITAL OUT-PATIENT) - PER 1/2 HOUR NOTE:  START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS');
INSERT INTO `bcpEligibleCodes` VALUES ('631', 'Psychiatry', 'PSYCHIATRIC TREATMENT - INDIVIDUAL (OFFICE OR HOSPITAL OUT-PATIENT) - PER 3/4 HOUR NOTE:  START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE');
INSERT INTO `bcpEligibleCodes` VALUES ('632', 'Psychiatry', 'PSYCHIATRIC TREATMENT - INDIVIDUAL (OFFICE OR HOSPITAL OUT-PATIENT) - PER 1 HOUR NOTE:  START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING');
INSERT INTO `bcpEligibleCodes` VALUES ('633', 'Psychiatry', 'PSYCHIATRIC TREATMENT - FAMILY/CONJOINT THERAPY - (TWO OR MORE FAMILY MEMBERS) - PER 1/2 HOUR NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('635', 'Psychiatry', 'PSYCHIATRIC TREATMENT - FAMILY/CONJOINT THERAPY - (TWO OR MORE FAMILY MEMBERS) - PER 3/4 HOUR NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('636', 'Psychiatry', 'PSYCHIATRIC TREATMENT - FAMILY/CONJOINT THERAPY - (TWO OR MORE FAMILY MEMBERS) - PER 1 HOUR NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('638', 'Psychiatry', 'PSYCHIATRIC TREATMENT - FAMILY/CONJOINT THERAPY - (TWO OR MORE FAMILY MEMBERS) -PER 1 1/4 HOUR NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('639', 'Psychiatry', 'PSYCHIATRIC TREATMENT-FAMILY/CONJOINT THERAPY -(TWO OR MORE FAMILY MEMBERS) -PER 1 1/2 HOUR NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('663', 'Psychiatry', 'GROUP PSYCHOTHERAPY FEE PER PATIENT, PER 1/2 HOUR:  THREE PATIENTS NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('664', 'Psychiatry', 'GROUP PSYCHOTHERAPY FEE PER PATIENT, PER 1/2 HOUR:  FOUR PATIENTS NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('665', 'Psychiatry', 'GROUP PSYCHOTHERAPY FEE PER PATIENT, PER 1/2 HOUR:  FIVE PATIENTS NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('666', 'Psychiatry', 'GROUP PSYCHOTHERAPY FEE PER PATIENT, PER 1/2 HOUR:  SIX PATIENTS NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('667', 'Psychiatry', 'GROUP PSYCHOTHERAPY FEE PER PATIENT, PER 1/2 HOUR:  SEVEN PATIENTS NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('668', 'Psychiatry', 'GROUP PSYCHOTHERAPY FEE PER PATIENT, PER 1/2 HOUR:  EIGHT PATIENTS NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('669', 'Psychiatry', 'GROUP PSYCHOTHERAPY FEE PER PATIENT, PER 1/2 HOUR:  NINE PATIENTS NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('670', 'Psychiatry', 'GROUP PSYCHOTHERAPY FEE PER PATIENT, PER 1/2 HOUR:  TEN PATIENTS NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('671', 'Psychiatry', 'GROUP PSYCHOTHERAPY FEE PER PATIENT, PER 1/2 HOUR:  ELEVEN PATIENTS NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('672', 'Psychiatry', 'GROUP PSYCHOTHERAPY FEE PER PATIENT, PER 1/2 HOUR:  TWELVE PATIENTS NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('673', 'Psychiatry', 'GROUP PSYCHOTHERAPY FEE PER PATIENT, PER 1/2 HOUR:  THIRTEEN PATIENTS NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('674', 'Psychiatry', 'GROUP PSYCHOTHERAPY FEE PER PATIENT, PER 1/2 HOUR:  FOURTEEN PATIENTS NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('675', 'Psychiatry', 'GROUP PSYCHOTHERAPY FEE PER PATIENT, PER 1/2 HOUR:  FIFTEEN PATIENTS NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('676', 'Psychiatry', 'GROUP PSYCHOTHERAPY FEE PER PATIENT, PER 1/2 HOUR:  SIXTEEN PATIENTS NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('677', 'Psychiatry', 'GROUP PSYCHOTHERAPY FEE PER PATIENT, PER 1/2 HOUR:  SEVENTEEN PATIENTS NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('678', 'Psychiatry', 'GROUP PSYCHOTHERAPY-FEE PATIENT-PER 1/2 HOUR EIGHTEEN PATIENTS NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('679', 'Psychiatry', 'GROUP PSYCHOTHERAPY-FEE PER PATIENT - PER 1/2 HOUR NINETEEN PATIENTS NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('680', 'Psychiatry', 'GROUP PSYCHOTHERAPY-FEE PER PATIENT-PER 1/2 HOUR TWENTY PATIENTS NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('681', 'Psychiatry', 'GROUP PSYCHOTHERAPY-FEE PER PATIENT-PER 1/2 HOUR GREATER THAN TWENTY PATIENTS NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('60607', 'Psychiatry', 'TELEHEALTH OFFICE VISIT TO INCLUDE SERVICES SUCH AS CHEMOTHERAPY MANAGEMENT AND /OR MINIMAL PSYCHOTHERAPY');
INSERT INTO `bcpEligibleCodes` VALUES ('60610', 'Psychiatry', 'TELEHEALTH INDIVIDUAL FULL CONSULTATION: DIAGNOSTIC INTERVIEW OR EXAMINATION, INCLUDING HISTORY, MENTAL STATUS EXAM AND TREATMENT RECOMMENDATION, WITH WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('60613', 'Psychiatry', 'TELEHEALTH GERIATRIC CONSULT (AGE 75 YRS OR OLDER)');
INSERT INTO `bcpEligibleCodes` VALUES ('60614', 'Psychiatry', 'TELEHEALTH REPEAT OR LIMITED GERIATRIC CONSULTATION: WHERE A FORMAL CONSULTATIO N FOR THE SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE CONSULTATIVE SERVICE');
INSERT INTO `bcpEligibleCodes` VALUES ('60622', 'Psychiatry', 'TELEHEALTH CONSULTATION - EMOTIONALLY DISTURBED CHILD: DIAGNOSTIC INTERVIEW OR EXAMINATION, INCLUDING MENTAL STATUS AND TREATMENT');
INSERT INTO `bcpEligibleCodes` VALUES ('60625', 'Psychiatry', 'TELEHEALTH REPEAT OR LIMITED CONSULTATION - WHERE A FORMAL CONSULTATION FOR THE SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE CONSULTATIVE');
INSERT INTO `bcpEligibleCodes` VALUES ('60626', 'Psychiatry', 'TELEHEALTH REPEAT OR LIMITED CONSULT EMOTIONALLY DISTURBED CHILD: WHERE A FORMAL CONSULTATION FOR THE SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE');
INSERT INTO `bcpEligibleCodes` VALUES ('60630', 'Psychiatry', 'INDIVIDUAL TELEHEALTH PSYCHIATRIC TREATMENT PER 1/2 HR NOTE: START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE PATIENT\'S CHART');
INSERT INTO `bcpEligibleCodes` VALUES ('60631', 'Psychiatry', 'INDIVIDUAL TELEHEALTH PSYCHIATRIC TREATMENT PER 3/4 HR NOTE: START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE  PATIENT\'S CHART');
INSERT INTO `bcpEligibleCodes` VALUES ('60632', 'Psychiatry', 'INDIVIDUAL TELEHEALTH PSYCHIATRIC TREATMENT PER 1 HR NOTE: START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE  PATIENT\'S CHART');
INSERT INTO `bcpEligibleCodes` VALUES ('60633', 'Psychiatry', 'FAMILY/CONJOINT TELEHEALTH THERAPY (TWO OR MORE FAMILY MEMBERS) - PER 1/2 HR NOTE: I)START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE');
INSERT INTO `bcpEligibleCodes` VALUES ('60635', 'Psychiatry', 'FAMILY/CONJOINT TELEHEALTH THERAPY (TWO OR MORE FAMILY MEMBERS) - PER 3/4 HR NOTES: I) START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE');
INSERT INTO `bcpEligibleCodes` VALUES ('60636', 'Psychiatry', 'FAMILY/CONJOINT TELEHEALTH THERAPY (TWO OR MORE FAMILY MEMBERS) - PER 1 HR NOTES: I)  START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE');
INSERT INTO `bcpEligibleCodes` VALUES ('60638', 'Psychiatry', 'FAMILY/CONJOINT TELEHEALTH THEREAPY(TWO OR MORE FAMILY MEMBERS)-PER 1 1/4 HR NOTES: I)START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE PATIEN');
INSERT INTO `bcpEligibleCodes` VALUES ('60639', 'Psychiatry', 'FAMILY/CONJOINT TELEHEALTH THERAPY(TWO OR MORE FAMILY MEMBERS)-PER 1 1/2 HR NOTES: I)  START AND END TIMES MUST BE ENTERED IN BOTH THE BILLING CLAIMS AND THE');
INSERT INTO `bcpEligibleCodes` VALUES ('83000', 'Radiology', 'INTERVENTIONAL RADIOLOGY CONSULTATION - TO INCLUDE PERTINENT PATIENT HISTORY, REGIONAL PHYSICAL EXAMINATION, REVIEW OF LABORATORY AND RADIOLOGICAL FINDINGS AND GENERATION OF A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('83070', 'Radiology', 'TELEHEALTH INTERVENTIONAL RADIOLOGY CONSULTATION: TO INCLUDE PERTINENT PATIENT HISTORY, REGIONAL PHYSICAL EXAMINATION, REVIEW OF LABORATORY AND RADIOLOGICAL FINDINGS AND GENERATION OF A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('32007', 'Respirology', 'RESPIROLOGY - CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('32010', 'Respirology', 'RESPIROLOGY - CONSULTATION: TO CONSIST OF AN EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT');
INSERT INTO `bcpEligibleCodes` VALUES ('32012', 'Respirology', 'RESPIROLOGY - REPEAT OR LIMITED CONSULTATION:  WHERE A FORMAL CONSULTATION FOR THE SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE CONSULTATIVE SERVICE');
INSERT INTO `bcpEligibleCodes` VALUES ('32014', 'Respirology', 'RESPIROLOGY - PROLONGED VISIT FOR COUNSELLING (MAXIMUM FOUR PER YEAR) NOTE: I)    SEE PREAMBLE, CLAUSE D. 3. 3.');
INSERT INTO `bcpEligibleCodes` VALUES ('32107', 'Respirology', 'RESPIROLOGY-TELEHEALTH-CONTINUING CARE BY CONSULTANT-SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('32110', 'Respirology', 'TELEHEALTH CONSULTATION-RESPIROLOGY TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('32112', 'Respirology', 'RESPIROLOGY-TELEHEALTH REPEATED OR LIMITED CONSULTATION WHERE A CONSULTATION FOR SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE');
INSERT INTO `bcpEligibleCodes` VALUES ('32114', 'Respirology', 'TELEHEALTH-PROLONGED VISIT/COUNSELLING-RESPIROLOGY');
INSERT INTO `bcpEligibleCodes` VALUES ('31007', 'Rheumatology', 'CONTINUING CARE BY A CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('31010', 'Rheumatology', 'CONSULTATION: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('31012', 'Rheumatology', 'REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT, THE CONSULTATIVE SERVICES DO NOT WARRANT A FULL');
INSERT INTO `bcpEligibleCodes` VALUES ('31014', 'Rheumatology', 'PROLONGED VISIT FOR COUNSELLING (MAXIMUM, FOUR PER YEAR) NOTE: I)SEE PREAMBLE, CLAUSE D.3.3');
INSERT INTO `bcpEligibleCodes` VALUES ('31050', 'Rheumatology', 'EXTENDED CONSULTATION-EXCEEDING 53 MINUTES (ACTUAL TIME SPENT WITH PATIENT). TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, NECESSARY TO INITIATE CARE');
INSERT INTO `bcpEligibleCodes` VALUES ('31060', 'Rheumatology', 'MULTIDISCIPLINARY CONFERENCE FOR COMMUNITY PATIENT');
INSERT INTO `bcpEligibleCodes` VALUES ('31107', 'Rheumatology', 'TELEHEALTH SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('31110', 'Rheumatology', 'TELEHEALTH CONSULTATION: TO CONSIST OF EXAMINATION, REVIEW OF HISTORY, LABORATORY, X-RAY FINDINGS, AND ADDITIONAL VISITS NECESSARY TO RENDER A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('31112', 'Rheumatology', 'TELEHEALTH REPEAT OR LIMITED CONSULTATION: WHERE A CONSULTATION FOR SAME ILLNESS IS REPEATED WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT, THE CONSULTATIVE SERVICES DO NOT');
INSERT INTO `bcpEligibleCodes` VALUES ('78763', 'Specialist Services Committee', 'SPECIALIST GROUP MEDICAL VISITS FEE PER PATIENT PER 1/2 HOUR: 3 PATIENTS A GROUP MEDICAL VISIT (GMV) PROVIDES MEDICAL CARE IN A GROUP SETTING.  A');
INSERT INTO `bcpEligibleCodes` VALUES ('78764', 'Specialist Services Committee', 'SPECIALIST GROUP MEDICAL VISITS FEE PER PATIENT PER 1/2 HOUR:  FOUR PATIENTS A GROUP MEDICAL VISIT (GMV) PROVIDES MEDICAL CARE IN A GROUP SETTING.  A');
INSERT INTO `bcpEligibleCodes` VALUES ('78765', 'Specialist Services Committee', 'SPECIALIST GROUP MEDICAL VISITS FEE PER PATIENT PER 1/2 HOUR:  FIVE PATIENTS A GROUP MEDICAL VISIT (GMV) PROVIDES MEDICAL CARE IN A GROUP SETTING.  A');
INSERT INTO `bcpEligibleCodes` VALUES ('78766', 'Specialist Services Committee', 'SPECIALIST GROUP MEDICAL VISITS FEE PER PATIENT PER 1/2 HOUR:  SIX PATIENTS A GROUP MEDICAL VISIT (GMV) PROVIDES MEDICAL CARE IN A GROUP SETTING.  A');
INSERT INTO `bcpEligibleCodes` VALUES ('78767', 'Specialist Services Committee', 'SPECIALIST GROUP MEDICAL VISITS FEE PER PATIENT PER 1/2 HOUR:  SEVEN PATIENTS A GROUP MEDICAL VISIT (GMV) PROVIDES MEDICAL CARE IN A GROUP SETTING.  A');
INSERT INTO `bcpEligibleCodes` VALUES ('78768', 'Specialist Services Committee', 'SPECIALIST GROUP MEDICAL VISITS FEE PER PATIENT PER 1/2 HOUR:   EIGHT PATIENTS A GROUP MEDICAL VISIT (GMV) PROVIDES MEDICAL CARE IN A GROUP SETTING.  A');
INSERT INTO `bcpEligibleCodes` VALUES ('78769', 'Specialist Services Committee', 'SPECIALIST GROUP MEDICAL VISITS FEE PER PATIENT PER 1/2 HOUR:  NINE PATIENTS A GROUP MEDICAL VISIT (GMV) PROVIDES MEDICAL CARE IN A GROUP SETTING.  A');
INSERT INTO `bcpEligibleCodes` VALUES ('78770', 'Specialist Services Committee', 'SPECIALIST GROUP MEDICAL VISITS FEE PER PATIENT PER 1/2 HOUR:  TEN PATIENTS A GROUP MEDICAL VISIT (GMV) PROVIDES MEDICAL CARE IN A GROUP SETTING.  A');
INSERT INTO `bcpEligibleCodes` VALUES ('78771', 'Specialist Services Committee', 'SPECIALIST GROUP MEDICAL VISITS FEE PER PATIENT PER 1/2 HOUR:  ELEVEN PATIENTS A GROUP MEDICAL VISIT (GMV) PROVIDES MEDICAL CARE IN A GROUP SETTING.  A');
INSERT INTO `bcpEligibleCodes` VALUES ('78772', 'Specialist Services Committee', 'SPECIALIST GROUP MEDICAL VISITS FEE PER PATIENT PER 1/2 HOUR:  TWELVE PATIENTS A GROUP MEDICAL VISIT (GMV) PROVIDES MEDICAL CARE IN A GROUP SETTING.  A');
INSERT INTO `bcpEligibleCodes` VALUES ('78773', 'Specialist Services Committee', 'SPECIALIST GROUP MEDICAL VISITS FEE PER PATIENT PER 1/2 HOUR:  THIRTEEN PATIENTS A GROUP MEDICAL VISIT (GMV) PROVIDES MEDICAL CARE IN A GROUP SETTING.  A');
INSERT INTO `bcpEligibleCodes` VALUES ('78774', 'Specialist Services Committee', 'SPECIALIST GROUP MEDICAL VISITS FEE PER PATIENT PER 1/2 HOUR:  FOURTEEN-PATIENTS A GROUP MEDICAL VISIT (GMV) PROVIDES MEDICAL CARE IN A GROUP SETTING.  A');
INSERT INTO `bcpEligibleCodes` VALUES ('78775', 'Specialist Services Committee', 'SPECIALIST GROUP MEDICAL VISITS FEE PER PATIENT PER 1/2 HOUR:  FIFTEEN PATIENTS A GROUP MEDICAL VISIT (GMV) PROVIDES MEDICAL CARE IN A GROUP SETTING.  A');
INSERT INTO `bcpEligibleCodes` VALUES ('78776', 'Specialist Services Committee', 'SPECIALIST GROUP MEDICAL VISITS FEE PER PATIENT PER 1/2 HOUR:  SIXTEEN PATIENTS A GROUP MEDICAL VISIT (GMV) PROVIDES MEDICAL CARE IN A GROUP SETTING.  A');
INSERT INTO `bcpEligibleCodes` VALUES ('78777', 'Specialist Services Committee', 'SPECIALIST GROUP MEDICAL VISITS FEE PER PATIENT PER 1/2 HOUR:  SEVENTEEN PATIENTS A GROUP MEDICAL VISIT (GMV) PROVIDES MEDICAL CARE IN A GROUP SETTING.  A');
INSERT INTO `bcpEligibleCodes` VALUES ('78778', 'Specialist Services Committee', 'SPECIALIST GROUP MEDICAL VISITS FEE PER PATIENT PER 1/2 HOUR:  EIGHTEEN PATIENTS A GROUP MEDICAL VISIT (GMV) PROVIDES MEDICAL CARE IN A GROUP SETTING.  A');
INSERT INTO `bcpEligibleCodes` VALUES ('78779', 'Specialist Services Committee', 'SPECIALIST GROUP MEDICAL VISITS FEE PER PATIENT PER 1/2 HOUR:  NINETEEN PATIENTS A GROUP MEDICAL VISIT (GMV) PROVIDES MEDICAL CARE IN A GROUP SETTING.  A');
INSERT INTO `bcpEligibleCodes` VALUES ('78780', 'Specialist Services Committee', 'SPECIALIST GROUP MEDICAL VISITS FEE PER PATIENT PER 1/2 HOUR:  TWENTY PATIENTS A GROUP MEDICAL VISIT (GMV) PROVIDES MEDICAL CARE IN A GROUP SETTING.  A');
INSERT INTO `bcpEligibleCodes` VALUES ('78781', 'Specialist Services Committee', 'SPECIALIST GROUP MEDICAL VISITS FEE PER PATIENT PER 1/2 HOUR:  GREATER THAN TWENTY PATIENTS A GROUP MEDICAL VISIT (GMV) PROVIDES MEDICAL CARE IN A GROUP SETTING.  A');
INSERT INTO `bcpEligibleCodes` VALUES ('8007', 'Urology', 'CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('8010', 'Urology', 'CONSULTATION:  TO INCLUDE COMPLETE HISTORY AND PHYSICAL EXAMINATION, REVIEW OF X-RAY AND LABORATORY FINDINGS, IF REQUIRED, AND A WRITTEN');
INSERT INTO `bcpEligibleCodes` VALUES ('8012', 'Urology', 'REPEAT OR LIMITED CONSULTATION:  TO APPLY WHERE A CONSULTATION IS REPEATED FOR THE SAME CONDITION WITHIN SIX MONTHS OF THE LAST VISIT BY');
INSERT INTO `bcpEligibleCodes` VALUES ('8070', 'Urology', 'TELEHEALTH UROLOGY CONSULTATION: TO INCLUDE COMPLETE HISTORY AND PHYSICAL EXAMINATION, REVIEW OF X RAY AND LABORATORY FINDINGS, IF REQUIRED, AND A WRITTEN REPORT.');
INSERT INTO `bcpEligibleCodes` VALUES ('8072', 'Urology', 'TELEHEALTH UROLOGY REPEAT OR LIMITED CONSULTATION: TO APPLY WHERE A CONSULTATION IS REPEATED FOR THE SAME CONDITION WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE CONSULTANT THE');
INSERT INTO `bcpEligibleCodes` VALUES ('8077', 'Urology', 'TELEHEALTH UROLOGY SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('77007', 'Vascular Surgery', 'CONTINUING CARE BY CONSULTANT: SUBSEQUENT OFFICE VISIT');
INSERT INTO `bcpEligibleCodes` VALUES ('77010', 'Vascular Surgery', 'CONSULTATION - VASCULAR SURGERY TO INCLUDE COMPLETE HISTORY AND PHYSICAL EXAMINATION, REVIEW OF X-RAY AND LABORATORY FINDINGS, IF REQUIRED, AND WRITTEN REPORT');
INSERT INTO `bcpEligibleCodes` VALUES ('77012', 'Vascular Surgery', 'REPEAT OR LIMITED CONSULTATION - VASCULAR SURGERY TO APPLY WHERE A CONSULTATION IS REPEATED FOR THE SAME CONDITION WITHIN SIX MONTHS OF THE LAST VISIT BY THE CONSULTANT, OR WHERE IN THE JUDGMENT OF THE');
INSERT INTO `bcpEligibleCodes` VALUES ('77015', 'Vascular Surgery', 'VASCULAR SURGERY PRE-OPERATIVE ASSESSMENT NOTES:');
INSERT INTO `bcpEligibleCodes` VALUES ('77707', 'Vascular Surgery', 'TELEHEALTH SUBSEQUENT OFFICE VISIT-VASCULAR SURGERY');
INSERT INTO `bcpEligibleCodes` VALUES ('77710', 'Vascular Surgery', 'TELEHEALTH CONSULTATION - VASCULAR SURGERY: TO INCLUDE COMPLETE HISTORY AND PHYSICAL EXAMINATION, REVIEW OF X-RAY AND LABORATORY FINDINGS, IF REQUIRED, AND A WRITTEN REPORT');
INSERT INTO `bcpEligibleCodes` VALUES ('77712', 'Vascular Surgery', 'TELEHEALTH REPEAT OR LIMITED CONSULTATION - VASCULAR SURGERY: TO APPLY WHERE A CONSULTATION IS REPEATED FOR THE SAME CONDITION WITHIN 6 MONTHS OF THE LAST VISIT BY THE CONSULTANT, ');
COMMIT;
*/

-- database/mysql/updates/update-2025-12-16-provider-module-singular.sql
-- -----------------------------------------
-- Standardize ctl_doctype module name from 'providers' (plural) to 'provider' (singular)
-- to match the naming convention used by the 'demographic' module and to match the development.sql INSERTS
UPDATE ctl_doctype SET module = 'provider' WHERE module = 'providers';

-- database/mysql/updates/update-2026-01-02-add-flowsheet-admin-privilege.sql
-- -----------------------------------------
-- Add new security object for flowsheet administration
-- This allows granting doctors access to "Manage Flowsheets" page
-- without giving them full _admin.misc privileges
-- INSERT IGNORE syntax allows for a quiet fail if there is a duplicate key

INSERT IGNORE INTO secObjectName (objectName, description, orgapplicable)
VALUES ('_admin.flowsheet', 'Manage Flowsheets', 0);

-- Grant read access to doctor role by default
-- Priority 1 ensures this is checked before other _admin.* entries with priority 0
-- that have 'o' (no rights) which would otherwise override this privilege
INSERT IGNORE INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no)
VALUES ('doctor', '_admin.flowsheet', 'r', 1, '999998');

-- Grant full access to admin role
INSERT IGNORE INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no)
VALUES ('admin', '_admin.flowsheet', 'x', 0, '999998');

-- Remove conflicting 'o' (no rights) entries for doctor role that would override
-- the _admin.flowsheet privilege due to OscarRoleObjectPrivilege bug where
-- Properties entries get overwritten. These 'o' entries are redundant anyway
-- (no access is the default behavior).
DELETE FROM secObjPrivilege
WHERE roleUserGroup = 'doctor'
  AND privilege = 'o'
  AND objectName IN ('_admin.reporting');

-- database/mysql/updates/update-2026-01-26-tickler-indexes.sql
-- Performance indexes for tickler queries
-- These indexes support common filter operations and batch fetching

-- Index for batch fetching tickler links
CREATE INDEX IF NOT EXISTS `idx_tickler_link_tickler_no` ON `tickler_link` (`tickler_no`);

-- Index for composite key
CREATE INDEX IF NOT EXISTS `idx_tickler_status_service_date` ON `tickler` (`status`, `service_date`);

-- Index for "assigned to" filter queries
CREATE INDEX IF NOT EXISTS `idx_tickler_task_assigned_to` ON `tickler` (`task_assigned_to`);

-- Index for "created by" filter queries
CREATE INDEX IF NOT EXISTS `idx_tickler_creator` ON `tickler` (`creator`);

-- database/mysql/updates/update-2026-02-10-fax-provider-type.sql
-- -----------------------------------------
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
WHERE `providerType` IS NULL OR `providerType` = ''

-- PHC additional missed changes
-- -----------------------------------------
CALL add_column('faxes', 'recipient', 'VARCHAR(255)');
CALL add_column('faxes', 'sender', 'VARCHAR(255)');
