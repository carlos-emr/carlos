-- Dashboard exclusions are provider/indicator-specific and retain expired rows.
-- The baseline's UNIQUE(demographic_no,key_val) prevented those records from
-- coexisting. Keep that uniqueness for every other extension key, while allowing
-- exclusion rows to retain their history. The DAO serializes each patient's
-- current-exclusion lookup and insertion with a demographic row lock.
--
-- Replace equivalent unique indexes regardless of their adopted-site name or
-- column order. A legacy table without this uniqueness needs no alteration:
-- do not introduce a new constraint on its existing extension rows.
-- The generated helper is invisible so legacy positional INSERT and SELECT * retain
-- their original seven-column shape.
-- Existing values, IDs, provider attribution and dates are not rewritten.
-- Run with application nodes stopped, as for other schema upgrades.
SET @dashboard_ext_indexes = (
    SELECT GROUP_CONCAT(CONCAT('DROP INDEX `', REPLACE(index_name, '`', '``'), '`')
                        ORDER BY index_name SEPARATOR ', ')
    FROM (
        SELECT index_name FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'demographicExt'
        GROUP BY index_name
        HAVING COUNT(*) = 2 AND MIN(non_unique) = 0
            AND SUM(sub_part IS NOT NULL) = 0
            AND SUM(column_name = 'demographic_no') = 1
            AND SUM(column_name = 'key_val') = 1
    ) extension_identity_indexes
);
-- Match the original key's character set, length and collation, including
-- case/accent equivalence for all the non-exclusion keys.
SET @dashboard_ext_type = (
    SELECT CONCAT('VARCHAR(', character_maximum_length, ') CHARACTER SET ',
                  character_set_name, ' COLLATE ', collation_name)
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'demographicExt'
        AND column_name = 'key_val'
);
SET @dashboard_ext_ddl = IF(@dashboard_ext_indexes IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE demographicExt ', @dashboard_ext_indexes,
           ', ADD COLUMN dashboard_single_value_key ', @dashboard_ext_type,
           ' GENERATED ALWAYS AS (CASE WHEN key_val = ''excludeIndicator'' THEN NULL ELSE key_val END) VIRTUAL INVISIBLE',
           ', ADD UNIQUE INDEX uk_demo_ext_single_value (demographic_no, dashboard_single_value_key)',
           ', ALGORITHM=COPY'));
PREPARE dashboard_ext_statement FROM @dashboard_ext_ddl;
EXECUTE dashboard_ext_statement;
DEALLOCATE PREPARE dashboard_ext_statement;
