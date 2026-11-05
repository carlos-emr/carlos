-- OSCAR 19 update-2008-12-16 widens this field to VARCHAR(255).
-- The BCAR 2007 UI and print configuration already read it. Keeping it
-- native also avoids adding an unused archived twin to this very wide form.
SET @bcar_previous_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_AUTO_VALUE_ON_ZERO';

ALTER TABLE `formBCAR2007`
    ADD COLUMN IF NOT EXISTS `ar2_urineCStxt` VARCHAR(255)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL;

-- Adopted installations may already carry this field. Keep compatible wider
-- definitions unchanged; when widening, retain their charset, collation,
-- nullability, default and comment. Reject incompatible definitions explicitly.
SET @bcar_urine_ddl = (
    SELECT CASE
        WHEN extra <> '' OR data_type NOT IN ('varchar','tinytext','text','mediumtext','longtext')
            THEN 'SELECT bcar2007_urine_culture_definition_requires_review FROM formBCAR2007'
        WHEN data_type IN ('text','mediumtext','longtext')
             OR (data_type = 'varchar' AND character_maximum_length >= 255)
            THEN 'SELECT 1'
        ELSE CONCAT('ALTER TABLE `formBCAR2007` MODIFY COLUMN `ar2_urineCStxt` VARCHAR(255)',
                    ' CHARACTER SET ', character_set_name, ' COLLATE ', collation_name,
                    IF(is_nullable = 'YES', ' NULL', ' NOT NULL'),
                    IF(column_default IS NULL, '', CONCAT(' DEFAULT ', column_default)),
                    ' COMMENT ', QUOTE(column_comment))
    END
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'formBCAR2007'
      AND column_name = 'ar2_urineCStxt'
);
PREPARE bcar_urine_statement FROM @bcar_urine_ddl;
EXECUTE bcar_urine_statement;
DEALLOCATE PREPARE bcar_urine_statement;

SET SESSION sql_mode = @bcar_previous_sql_mode;
