create table if not exists `read_lab`
(
    id int null,
    provider_no varchar(11) null,
    lab_type varchar(20) null,
    lab_id int null
);

ALTER TABLE `HRMDocumentToProvider` ADD COLUMN `filed` tinyint(1) NULL AFTER `viewed`;