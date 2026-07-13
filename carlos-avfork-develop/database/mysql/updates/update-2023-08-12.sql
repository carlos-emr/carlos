-- A large production database was used to identify the following adjustments

-- this change is made because this column holds strings up to 10 characters long ("Electronic")
-- this reverses a change made in update-2023-05-03.sql where it was set to varchar(1)
ALTER TABLE `demographic`
    MODIFY COLUMN `newsletter` varchar(10);
	
-- this change is made because this is a textbox in the GUI and examples of it holding up to 800 characters
-- this reverses a change made in update-2023-05-03.sql where it was set to varchar(25)
ALTER TABLE `pharmacyInfo`
    MODIFY COLUMN `notes` tinytext;

-- this change is made because examples of it holding more than 25 characters
-- this reverses a change made in update-2023-05-03.sql where it was set to varchar(25)
ALTER TABLE `pharmacyInfo`
    MODIFY COLUMN `serviceLocationIdentifier` varchar(255);

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