-- Preserve the page III and IV immunization notes offered by Rourke 2020.
-- Existing records have no recoverable value for these previously discarded fields.
ALTER TABLE formRourke2020
    ADD COLUMN IF NOT EXISTS p3_immunization TEXT NULL,
    ADD COLUMN IF NOT EXISTS p4_immunization TEXT NULL;
