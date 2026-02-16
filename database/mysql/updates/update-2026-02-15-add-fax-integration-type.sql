-- Add integration_type column to support dual-mode fax (legacy gateway vs SRFax)
-- This enables per-account selection of fax transmission provider

ALTER TABLE fax_config
ADD COLUMN integration_type VARCHAR(50) DEFAULT ''
COMMENT 'Fax provider type: empty/LEGACY_GATEWAY or SRFAX';
