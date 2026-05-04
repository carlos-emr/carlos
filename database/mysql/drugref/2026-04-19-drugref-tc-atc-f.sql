-- Adds the tc_atc_f column to cd_therapeutic_class to match the CdTherapeuticClass entity
-- in drugref2026 (field tcAtcf). Without this column Hibernate schema-validation fails
-- on drugref2 startup and the webapp never becomes healthy.
--
-- Run against the drugref2 database (not oscar).

ALTER TABLE cd_therapeutic_class
    ADD COLUMN IF NOT EXISTS tc_atc_f VARCHAR(255) DEFAULT NULL;
