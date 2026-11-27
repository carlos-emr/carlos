-- Component identity and strength are captured with the order or favourite.
-- A NULL value means the historical record supplies only its original dosage text.
ALTER TABLE drugs ADD COLUMN IF NOT EXISTS medication_components MEDIUMTEXT NULL;
ALTER TABLE favorites ADD COLUMN IF NOT EXISTS medication_components MEDIUMTEXT NULL;
ALTER TABLE formBPMH ADD COLUMN IF NOT EXISTS otc_reviewed BOOLEAN NULL;
