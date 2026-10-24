-- Track the portal password lifecycle independently of transport acceptance.
-- No password is stored in these columns. Existing email behavior stays disabled by default.
--
-- IF NOT EXISTS makes a rerun after a partial apply a no-op, matching V1.0.24.
ALTER TABLE emailLog
    ADD COLUMN IF NOT EXISTS portalDeliveryState VARCHAR(32) NULL,
    ADD COLUMN IF NOT EXISTS portalSourceReference VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS portalSecretId BIGINT NULL,
    ADD COLUMN IF NOT EXISTS portalOrigin VARCHAR(512) NULL,
    ADD COLUMN IF NOT EXISTS portalClinicId VARCHAR(64) NULL,
    ADD UNIQUE INDEX IF NOT EXISTS uq_email_portal_source (portalSourceReference);
