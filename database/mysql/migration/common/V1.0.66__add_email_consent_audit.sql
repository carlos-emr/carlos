-- Record the consent decision used for each provider-to-patient email send.
--
-- IF NOT EXISTS keeps the Flyway cutover safe for installations that already
-- applied the former dated update script before it was retired.
ALTER TABLE emailLog
    ADD COLUMN IF NOT EXISTS consentStatus varchar(32) NULL,
    ADD COLUMN IF NOT EXISTS consentId int NULL,
    ADD COLUMN IF NOT EXISTS consentLastUpdateDate datetime NULL,
    ADD COLUMN IF NOT EXISTS consentOverride tinyint(1) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS consentOverrideReason varchar(255) NULL;
