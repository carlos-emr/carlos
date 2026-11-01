-- OSCAR 19 update-2008-12-16 widens this field to VARCHAR(255).
-- The BCAR 2007 UI and print configuration already read it. Keeping it
-- native also avoids adding an unused archived twin to this very wide form.
SET @bcar_previous_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_AUTO_VALUE_ON_ZERO';

ALTER TABLE `formBCAR2007`
    ADD COLUMN IF NOT EXISTS `ar2_urineCStxt` VARCHAR(255)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL;

-- Also upgrade installations that already carry the earlier 50-character
-- source definition. Strict conversion protects unexpected local values.
ALTER TABLE `formBCAR2007`
    MODIFY COLUMN `ar2_urineCStxt` VARCHAR(255)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL;

SET SESSION sql_mode = @bcar_previous_sql_mode;
