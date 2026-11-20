-- Imported signatures with demographicId=-1 have no authoritative patient on
-- the signature row. The importer writes a unique source-reference decision
-- here before go-live. Runtime form submissions never update this table.
CREATE TABLE IF NOT EXISTS `o19_imported_signature_owner` (
  `signature_id` INT(11) NOT NULL,
  `demographic_id` INT(10) NOT NULL,
  `module_type` VARCHAR(24) NOT NULL,
  `lastUpdateUser` VARCHAR(100) NOT NULL DEFAULT 'o19-import',
  `lastUpdateDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`signature_id`),
  KEY `o19_signature_owner_demographic` (`demographic_id`),
  CONSTRAINT `o19_signature_owner_signature_fk`
    FOREIGN KEY (`signature_id`) REFERENCES `DigitalSignature` (`id`),
  CONSTRAINT `o19_signature_owner_demographic_fk`
    FOREIGN KEY (`demographic_id`) REFERENCES `demographic` (`demographic_no`),
  CONSTRAINT `o19_signature_owner_module_ck`
    CHECK (`module_type` IN ('CONSULTATION', 'PRESCRIPTION', 'E_FORM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
