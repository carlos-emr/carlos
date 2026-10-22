-- SRFax supports explicit international destinations. Preserve the '+' until
-- provider normalization; the legacy 11-character column cannot store these.
-- Widening is lossless for existing domestic numbers and keeps NULL semantics.
ALTER TABLE faxes MODIFY COLUMN destination VARCHAR(32) DEFAULT NULL;
