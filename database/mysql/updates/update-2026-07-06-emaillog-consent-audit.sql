ALTER TABLE emailLog
    ADD COLUMN consentStatus varchar(32) NULL,
    ADD COLUMN consentId int NULL,
    ADD COLUMN consentLastUpdateDate datetime NULL,
    ADD COLUMN consentOverride tinyint(1) NOT NULL DEFAULT 0,
    ADD COLUMN consentOverrideReason varchar(255) NULL;
