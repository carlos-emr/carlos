-- OSCAR 19 stores unassigned signatures with demographicId = -1. Keep the
-- source-facing column and its exact value; only the FK projection treats the
-- sentinel as absent. An INVISIBLE generated column leaves positional legacy
-- INSERTs and SELECT * with the original seven-column shape.
ALTER TABLE `DigitalSignature`
  DROP FOREIGN KEY `DigitalSignature_ibfk_3`,
  ADD COLUMN `demographicFkId` INT(11)
    GENERATED ALWAYS AS (NULLIF(`demographicId`, -1)) PERSISTENT INVISIBLE,
  ADD KEY `DigitalSignature_demographicFkId` (`demographicFkId`),
  ADD CONSTRAINT `DigitalSignature_demographicFkId_fk`
    FOREIGN KEY (`demographicFkId`) REFERENCES `demographic` (`demographic_no`);
