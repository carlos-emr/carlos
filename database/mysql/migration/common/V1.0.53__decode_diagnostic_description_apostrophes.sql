-- Correct legacy CP1252 apostrophes stored as literal HTML entities in diagnosis reference data.
-- Leave the published seed migrations unchanged; applies equally to upgrades and fresh installs.
UPDATE diagnosticcode
SET description = REPLACE(description, '&#146;', CHAR(39))
WHERE description LIKE '%&#146;%';
