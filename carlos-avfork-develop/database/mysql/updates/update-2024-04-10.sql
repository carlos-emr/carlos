-- this index is designed to increase performance when retrieving the consent status for a particular demographic by avoiding a full table scan
-- note, this create index if not exists only works in mariadb

CREATE INDEX IF NOT EXISTS `Consent_demographic_no_IDX` ON `Consent` (`demographic_no`);