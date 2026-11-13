-- NULL preserves every existing independent provider assignment. Only newly created
-- Provider Linking Rules assignments carry a patient id and may be revoked on rematch.
-- Sequence follows #3986 (36) and #3996 (37/38); deploy those migrations first.
ALTER TABLE HRMDocumentToProvider
    ADD COLUMN IF NOT EXISTS mrpDemographicNo INT NULL;
ALTER TABLE providerLabRouting
    ADD COLUMN IF NOT EXISTS mrpDemographicNo INT NULL;
