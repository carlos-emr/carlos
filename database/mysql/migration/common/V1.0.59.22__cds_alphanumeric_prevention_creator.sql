-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- The native model and provider API use text identifiers, including authorized T099-like actors.
-- Preserve wider adopted storage, null/default/comment, and text charset/collation metadata.
-- Numeric legacy creator values convert to their exact decimal spelling; NULL remains NULL.
SET @cds_prevention_creator_ddl=(
    SELECT IF(data_type IN ('text','mediumtext','longtext') OR character_maximum_length>=16,'SELECT 1',
        CONCAT('ALTER TABLE `preventions` MODIFY COLUMN `creator` VARCHAR(16)',
            ' CHARACTER SET ',COALESCE(character_set_name,'ascii'),' COLLATE ',COALESCE(collation_name,'ascii_bin'),
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='preventions' AND column_name='creator'
);
PREPARE cds_prevention_creator_statement FROM @cds_prevention_creator_ddl;
EXECUTE cds_prevention_creator_statement;
DEALLOCATE PREPARE cds_prevention_creator_statement;
SET @cds_prevention_creator_ddl=NULL;
