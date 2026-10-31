-- Imported medication projections cannot be executable while clinically important
-- source detail is missing or represented only in the lossless source category.
-- NULL retains the established behavior of untouched native medication rows.
ALTER TABLE drugs ADD COLUMN cds_projection_incomplete BOOLEAN NULL DEFAULT NULL;
