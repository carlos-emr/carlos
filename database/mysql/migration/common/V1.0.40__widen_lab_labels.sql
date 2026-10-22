-- Complete PATHL7 panel labels and labels carried across report versions can exceed
-- 255 characters. Preserve existing values and allow the full display label to import.
-- Retrying this widening is safe; no report text or label content is rewritten.
ALTER TABLE hl7TextInfo
    MODIFY COLUMN label TEXT NULL DEFAULT NULL;
