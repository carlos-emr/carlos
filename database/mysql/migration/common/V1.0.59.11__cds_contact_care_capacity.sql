-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- CDS Contact/Care additive capacity and explicitly typed source values.
-- Preserve actual column defaults, nullability, comments, charset/collation and any wider storage.
-- No source author, consent, exact observation time or normal exam result is fabricated.

-- Contact.firstName: widening only; wider operator-approved storage is retained.
SET @cds_care_ddl = (
    SELECT IF(data_type IN ('text','mediumtext','longtext') OR character_maximum_length >= 101, 'SELECT 1',
        CONCAT('ALTER TABLE `Contact` MODIFY COLUMN `firstName` VARCHAR(101)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='Contact' AND column_name='firstName'
);
PREPARE cds_care_statement FROM @cds_care_ddl;
EXECUTE cds_care_statement;
DEALLOCATE PREPARE cds_care_statement;

-- Contact.residencePhone: widening only; wider operator-approved storage is retained.
SET @cds_care_ddl = (
    SELECT IF(data_type IN ('text','mediumtext','longtext') OR character_maximum_length >= 40, 'SELECT 1',
        CONCAT('ALTER TABLE `Contact` MODIFY COLUMN `residencePhone` VARCHAR(40)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='Contact' AND column_name='residencePhone'
);
PREPARE cds_care_statement FROM @cds_care_ddl;
EXECUTE cds_care_statement;
DEALLOCATE PREPARE cds_care_statement;

-- Contact.cellPhone: widening only; wider operator-approved storage is retained.
SET @cds_care_ddl = (
    SELECT IF(data_type IN ('text','mediumtext','longtext') OR character_maximum_length >= 40, 'SELECT 1',
        CONCAT('ALTER TABLE `Contact` MODIFY COLUMN `cellPhone` VARCHAR(40)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='Contact' AND column_name='cellPhone'
);
PREPARE cds_care_statement FROM @cds_care_ddl;
EXECUTE cds_care_statement;
DEALLOCATE PREPARE cds_care_statement;

-- measurements.dataField: widening only; wider operator-approved storage is retained.
SET @cds_care_ddl = (
    SELECT IF(data_type IN ('mediumtext','longtext'), 'SELECT 1',
        CONCAT('ALTER TABLE `measurements` MODIFY COLUMN `dataField` MEDIUMTEXT',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='measurements' AND column_name='dataField'
);
PREPARE cds_care_statement FROM @cds_care_ddl;
EXECUTE cds_care_statement;
DEALLOCATE PREPARE cds_care_statement;

-- The existing CGSD type is Yes/No and FTE/FTLS can mean Normal. They cannot represent arbitrary
-- goals or an exam performed without a reported result. Use distinct types with no numeric inference.
-- Resolve the validation ID; adopted databases do not necessarily use the CARLOS seed IDs.
INSERT INTO `validations` (`name`, `regularExp`, `minValue`, `maxValue1`, `minLength`, `maxLength`, `isNumeric`, `isTrue`, `isDate`)
SELECT 'CDS source text (no numeric inference)', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM `validations` WHERE `name`='CDS source text (no numeric inference)'
      AND `regularExp` IS NULL AND `minValue` IS NULL AND `maxValue1` IS NULL
      AND `minLength` IS NULL AND `maxLength` IS NULL
      AND `isNumeric` IS NULL AND `isTrue` IS NULL AND `isDate` IS NULL
);
SET @cds_care_text_rule = (
    SELECT MIN(`id`) FROM `validations` WHERE `name`='CDS source text (no numeric inference)'
      AND `regularExp` IS NULL AND `minValue` IS NULL AND `maxValue1` IS NULL
      AND `minLength` IS NULL AND `maxLength` IS NULL
      AND `isNumeric` IS NULL AND `isTrue` IS NULL AND `isDate` IS NULL
);

-- Preserve any pre-existing colliding code. Runtime admission requires the exact safe rule/instruction.
INSERT INTO `measurementType` (`type`, `typeDisplayName`, `typeDescription`, `measuringInstruction`, `validation`, `createDate`)
SELECT 'CDS_GOALS', 'Imported collaborative goals', 'Imported collaborative goals', 'Source documented goals', CAST(@cds_care_text_rule AS CHAR), '2026-08-01 00:00:00'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `measurementType` WHERE `type`='CDS_GOALS');

-- Preserve any pre-existing colliding code. Runtime admission requires the exact safe rule/instruction.
INSERT INTO `measurementType` (`type`, `typeDisplayName`, `typeDescription`, `measuringInstruction`, `validation`, `createDate`)
SELECT 'CDS_EXAM', 'Imported examination performed', 'Imported examination performed', 'Source exam performed; result not supplied', CAST(@cds_care_text_rule AS CHAR), '2026-08-01 00:00:00'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `measurementType` WHERE `type`='CDS_EXAM');

-- Preserve any pre-existing colliding code. Runtime admission requires the exact safe rule/instruction.
INSERT INTO `measurementType` (`type`, `typeDisplayName`, `typeDescription`, `measuringInstruction`, `validation`, `createDate`)
SELECT 'CDS_CARE_VALUE', 'Imported uninterpreted care value', 'Imported uninterpreted care value', 'Uninterpreted source care value', CAST(@cds_care_text_rule AS CHAR), '2026-08-01 00:00:00'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `measurementType` WHERE `type`='CDS_CARE_VALUE');

SET @cds_care_text_rule = NULL;
SET @cds_care_ddl = NULL;
