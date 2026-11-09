-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- An archive must retain the complete nine-character billingmaster source amount.
-- Preserve local wider columns, character set, collation, nullability and default.
SET @bc_history_previous_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_AUTO_VALUE_ON_ZERO';
SET @bc_history_widen_sql = (
    SELECT IF(CHARACTER_MAXIMUM_LENGTH >= 9, 'DO 0',
        CONCAT('ALTER TABLE `billing_history` MODIFY COLUMN `amount` VARCHAR(9) CHARACTER SET ',
            CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME,
            IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL'),
            ' DEFAULT ', COALESCE(COLUMN_DEFAULT, 'NULL'), ' COMMENT ', QUOTE(COLUMN_COMMENT)))
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'billing_history' AND COLUMN_NAME = 'amount'
        AND DATA_TYPE IN ('char', 'varchar', 'tinytext', 'text', 'mediumtext', 'longtext') AND EXTRA = ''
);
-- Missing or incompatible local definitions refuse migration rather than losing audit data.
PREPARE bc_history_widen FROM @bc_history_widen_sql;
EXECUTE bc_history_widen;
DEALLOCATE PREPARE bc_history_widen;
SET SESSION sql_mode = @bc_history_previous_sql_mode;
