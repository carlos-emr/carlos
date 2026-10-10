-- BillingOnRaService stores the 12-character HR4 health-number field followed
-- by its 2-character version. The original 12-character column silently lost
-- the version in permissive SQL mode (and rejects the import in strict mode).
-- Keep any already-wider adopted column and retain its other attributes.
-- MariaDB exposes string defaults as quoted SQL expressions in COLUMN_DEFAULT.
SET @ra_hin_ddl = (
    SELECT IF(character_maximum_length < 14,
        CONCAT('ALTER TABLE radetail MODIFY COLUMN hin VARCHAR(14) CHARACTER SET ',
               character_set_name, ' COLLATE ', collation_name,
               IF(is_nullable = 'YES', ' NULL', ' NOT NULL'),
               IF(column_default IS NULL, '', CONCAT(' DEFAULT ', column_default)),
               ' COMMENT ', QUOTE(column_comment)),
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'radetail' AND column_name = 'hin'
);
PREPARE ra_hin_statement FROM @ra_hin_ddl;
EXECUTE ra_hin_statement;
DEALLOCATE PREPARE ra_hin_statement;
