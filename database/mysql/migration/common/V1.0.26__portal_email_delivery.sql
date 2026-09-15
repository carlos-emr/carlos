-- Track the portal password lifecycle independently of transport acceptance.
-- No password is stored in these columns. Existing email behavior stays disabled by default.
ALTER TABLE emailLog
    ADD COLUMN portalDeliveryState VARCHAR(32) NULL,
    ADD COLUMN portalSourceReference VARCHAR(64) NULL,
    ADD COLUMN portalSecretId BIGINT NULL,
    ADD COLUMN portalOrigin VARCHAR(512) NULL,
    ADD COLUMN portalClinicId VARCHAR(64) NULL,
    ADD UNIQUE INDEX uq_email_portal_source (portalSourceReference);
