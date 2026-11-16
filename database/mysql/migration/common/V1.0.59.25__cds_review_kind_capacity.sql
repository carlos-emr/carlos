-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- APPROVE_UNWRITTEN_REVISION is a 26-character reviewed control operation.
-- Widen only: retain any larger operator-approved type and all existing metadata/data.
-- Published review-history migrations are immutable; existing installations upgrade here.
SET @cds_review_kind_ddl = (
    SELECT IF(character_maximum_length >= 32, 'SELECT 1',
        CONCAT('ALTER TABLE `cds_import_review` MODIFY COLUMN `kind` VARCHAR(32)',
            ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='cds_import_review' AND column_name='kind'
);
PREPARE cds_review_kind_statement FROM @cds_review_kind_ddl;
EXECUTE cds_review_kind_statement;
DEALLOCATE PREPARE cds_review_kind_statement;
