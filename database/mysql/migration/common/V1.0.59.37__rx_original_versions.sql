-- Existing clients remain readable; current editors submit the version they originally displayed.
ALTER TABLE drugs ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE prescription ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;
