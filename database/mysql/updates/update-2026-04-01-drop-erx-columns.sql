-- Deprecate eRx External Prescriber. This is safe to rerun.
-- This must be run or you will be unable to add new users with the old schema
-- as the insert would fail from adding a null to not null columns

ALTER TABLE ProviderPreference
  DROP COLUMN IF EXISTS eRxEnabled,
  DROP COLUMN IF EXISTS eRx_SSO_URL,
  DROP COLUMN IF EXISTS eRxUsername,
  DROP COLUMN IF EXISTS eRxPassword,
  DROP COLUMN IF EXISTS eRxFacility,
  DROP COLUMN IF EXISTS eRxTrainingMode,
  DROP COLUMN IF EXISTS encryptedMyOscarPassword;
