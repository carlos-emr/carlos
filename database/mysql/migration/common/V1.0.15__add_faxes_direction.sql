-- Persist inbound/outbound direction on fax jobs so the Manage Faxes queue can label rows
-- reliably instead of inferring the type from filename substrings. Backfill existing rows:
-- RECEIVED faxes are inbound; anything else with a destination is outbound.
ALTER TABLE faxes
    ADD COLUMN IF NOT EXISTS direction varchar(3) DEFAULT NULL;

UPDATE faxes SET direction = 'IN' WHERE status = 'RECEIVED' AND direction IS NULL;
UPDATE faxes SET direction = 'OUT' WHERE direction IS NULL AND destination IS NOT NULL AND destination <> '';
