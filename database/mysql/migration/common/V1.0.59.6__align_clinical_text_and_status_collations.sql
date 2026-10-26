-- Explicit per-column comparison policy. UTF-8 storage is unchanged.
-- Human names/free text use Unicode UCA14. Case-sensitive appointment codes
-- retain distinct C/c and H/h meanings in both tables. Technical identifiers,
-- lookup/merge keys and archive originals retain their existing policy.
-- Stop all application writers and take a verified backup before upgrading.
-- Every schema and uniqueness guard runs before the first persistent ALTER.
-- Change all affected columns of a table together so composite unique keys
-- never see an intermediate mixture of old and proposed comparisons.
-- Existing widths, nullability, defaults and comments are retained.

SET @carlos_text_shape_ok = (
    SELECT COUNT(*) = 1 AND MIN(character_set_name) = 'utf8mb4'
        AND MIN(data_type) IN ('char','varchar','tinytext','text','mediumtext','longtext')
        AND COALESCE(MIN(generation_expression), '') = ''
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'demographic' AND column_name = 'first_name'
);
SET @carlos_text_guard = IF(@carlos_text_shape_ok, 'SELECT 1',
    'SELECT resolve_demographic_first_name_shape_before_collation_upgrade FROM `demographic`');
PREPARE carlos_text_statement FROM @carlos_text_guard;
EXECUTE carlos_text_statement;
DEALLOCATE PREPARE carlos_text_statement;

SET @carlos_text_shape_ok = (
    SELECT COUNT(*) = 1 AND MIN(character_set_name) = 'utf8mb4'
        AND MIN(data_type) IN ('char','varchar','tinytext','text','mediumtext','longtext')
        AND COALESCE(MIN(generation_expression), '') = ''
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'demographic' AND column_name = 'last_name'
);
SET @carlos_text_guard = IF(@carlos_text_shape_ok, 'SELECT 1',
    'SELECT resolve_demographic_last_name_shape_before_collation_upgrade FROM `demographic`');
PREPARE carlos_text_statement FROM @carlos_text_guard;
EXECUTE carlos_text_statement;
DEALLOCATE PREPARE carlos_text_statement;

SET @carlos_text_shape_ok = (
    SELECT COUNT(*) = 1 AND MIN(character_set_name) = 'utf8mb4'
        AND MIN(data_type) IN ('char','varchar','tinytext','text','mediumtext','longtext')
        AND COALESCE(MIN(generation_expression), '') = ''
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'provider' AND column_name = 'first_name'
);
SET @carlos_text_guard = IF(@carlos_text_shape_ok, 'SELECT 1',
    'SELECT resolve_provider_first_name_shape_before_collation_upgrade FROM `provider`');
PREPARE carlos_text_statement FROM @carlos_text_guard;
EXECUTE carlos_text_statement;
DEALLOCATE PREPARE carlos_text_statement;

SET @carlos_text_shape_ok = (
    SELECT COUNT(*) = 1 AND MIN(character_set_name) = 'utf8mb4'
        AND MIN(data_type) IN ('char','varchar','tinytext','text','mediumtext','longtext')
        AND COALESCE(MIN(generation_expression), '') = ''
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'provider' AND column_name = 'last_name'
);
SET @carlos_text_guard = IF(@carlos_text_shape_ok, 'SELECT 1',
    'SELECT resolve_provider_last_name_shape_before_collation_upgrade FROM `provider`');
PREPARE carlos_text_statement FROM @carlos_text_guard;
EXECUTE carlos_text_statement;
DEALLOCATE PREPARE carlos_text_statement;

SET @carlos_text_shape_ok = (
    SELECT COUNT(*) = 1 AND MIN(character_set_name) = 'utf8mb4'
        AND MIN(data_type) IN ('char','varchar','tinytext','text','mediumtext','longtext')
        AND COALESCE(MIN(generation_expression), '') = ''
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'casemgmt_note' AND column_name = 'note'
);
SET @carlos_text_guard = IF(@carlos_text_shape_ok, 'SELECT 1',
    'SELECT resolve_casemgmt_note_note_shape_before_collation_upgrade FROM `casemgmt_note`');
PREPARE carlos_text_statement FROM @carlos_text_guard;
EXECUTE carlos_text_statement;
DEALLOCATE PREPARE carlos_text_statement;

SET @carlos_text_shape_ok = (
    SELECT COUNT(*) = 1 AND MIN(character_set_name) = 'utf8mb4'
        AND MIN(data_type) IN ('char','varchar','tinytext','text','mediumtext','longtext')
        AND COALESCE(MIN(generation_expression), '') = ''
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'document' AND column_name = 'docdesc'
);
SET @carlos_text_guard = IF(@carlos_text_shape_ok, 'SELECT 1',
    'SELECT resolve_document_docdesc_shape_before_collation_upgrade FROM `document`');
PREPARE carlos_text_statement FROM @carlos_text_guard;
EXECUTE carlos_text_statement;
DEALLOCATE PREPARE carlos_text_statement;

SET @carlos_text_shape_ok = (
    SELECT COUNT(*) = 1 AND MIN(character_set_name) = 'utf8mb4'
        AND MIN(data_type) IN ('char','varchar','tinytext','text','mediumtext','longtext')
        AND COALESCE(MIN(generation_expression), '') = ''
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'drugs' AND column_name = 'special'
);
SET @carlos_text_guard = IF(@carlos_text_shape_ok, 'SELECT 1',
    'SELECT resolve_drugs_special_shape_before_collation_upgrade FROM `drugs`');
PREPARE carlos_text_statement FROM @carlos_text_guard;
EXECUTE carlos_text_statement;
DEALLOCATE PREPARE carlos_text_statement;

SET @carlos_text_shape_ok = (
    SELECT COUNT(*) = 1 AND MIN(character_set_name) = 'utf8mb4'
        AND MIN(data_type) IN ('char','varchar','tinytext','text','mediumtext','longtext')
        AND COALESCE(MIN(generation_expression), '') = ''
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tickler' AND column_name = 'message'
);
SET @carlos_text_guard = IF(@carlos_text_shape_ok, 'SELECT 1',
    'SELECT resolve_tickler_message_shape_before_collation_upgrade FROM `tickler`');
PREPARE carlos_text_statement FROM @carlos_text_guard;
EXECUTE carlos_text_statement;
DEALLOCATE PREPARE carlos_text_statement;

SET @carlos_text_shape_ok = (
    SELECT COUNT(*) = 1 AND MIN(character_set_name) = 'utf8mb4'
        AND MIN(data_type) IN ('char','varchar','tinytext','text','mediumtext','longtext')
        AND COALESCE(MIN(generation_expression), '') = ''
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'appointment' AND column_name = 'status'
);
SET @carlos_text_guard = IF(@carlos_text_shape_ok, 'SELECT 1',
    'SELECT resolve_appointment_status_shape_before_collation_upgrade FROM `appointment`');
PREPARE carlos_text_statement FROM @carlos_text_guard;
EXECUTE carlos_text_statement;
DEALLOCATE PREPARE carlos_text_statement;

SET @carlos_text_shape_ok = (
    SELECT COUNT(*) = 1 AND MIN(character_set_name) = 'utf8mb4'
        AND MIN(data_type) IN ('char','varchar','tinytext','text','mediumtext','longtext')
        AND COALESCE(MIN(generation_expression), '') = ''
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'appointment_status' AND column_name = 'status'
);
SET @carlos_text_guard = IF(@carlos_text_shape_ok, 'SELECT 1',
    'SELECT resolve_appointment_status_status_shape_before_collation_upgrade FROM `appointment_status`');
PREPARE carlos_text_statement FROM @carlos_text_guard;
EXECUTE carlos_text_statement;
DEALLOCATE PREPARE carlos_text_statement;

-- Check complete composite/prefix unique keys using their proposed comparison.
-- NULL-containing keys remain exempt, as MariaDB unique indexes permit them.
SET @carlos_old_group_concat_max_len = @@SESSION.group_concat_max_len;
SET SESSION group_concat_max_len = 16777216;
SET @carlos_collision_queries = (
    SELECT GROUP_CONCAT(CONCAT('SELECT 1 FROM `', REPLACE(table_name, '`', '``'),
        '` WHERE ', non_nulls, ' GROUP BY ', expressions, ' HAVING COUNT(*) > 1') SEPARATOR ' UNION ALL ')
    FROM (
        SELECT s.table_name, s.index_name,
            GROUP_CONCAT(CONCAT('`', REPLACE(s.column_name, '`', '``'), '` IS NOT NULL')
                         ORDER BY s.seq_in_index SEPARATOR ' AND ') AS non_nulls,
            GROUP_CONCAT(CONCAT(
                IF(s.sub_part IS NULL, CONCAT('`', REPLACE(s.column_name, '`', '``'), '`'),
                   CONCAT('LEFT(`', REPLACE(s.column_name, '`', '``'), '`,', s.sub_part, ')')),
                IF((s.table_name, s.column_name) IN (('demographic','first_name'), ('demographic','last_name'), ('provider','first_name'), ('provider','last_name'), ('casemgmt_note','note'), ('document','docdesc'), ('drugs','special'), ('tickler','message')),
                   ' COLLATE utf8mb4_uca1400_ai_ci', ''))
                ORDER BY s.seq_in_index SEPARATOR ', ') AS expressions
        FROM information_schema.statistics s
        WHERE s.table_schema = DATABASE() AND s.non_unique = 0
          AND EXISTS (
              SELECT 1 FROM information_schema.statistics touched
              WHERE touched.table_schema = s.table_schema AND touched.table_name = s.table_name
                AND touched.index_name = s.index_name
                AND (touched.table_name, touched.column_name) IN (('demographic','first_name'), ('demographic','last_name'), ('provider','first_name'), ('provider','last_name'), ('casemgmt_note','note'), ('document','docdesc'), ('drugs','special'), ('tickler','message'))
          )
        GROUP BY s.table_name, s.index_name
    ) affected_unique_keys
);
SET @carlos_collision_sql = IF(@carlos_collision_queries IS NULL,
    'SELECT 0 INTO @carlos_collation_conflicts',
    CONCAT('SELECT COUNT(*) INTO @carlos_collation_conflicts FROM (',
           @carlos_collision_queries, ') collation_conflicts'));
PREPARE carlos_text_statement FROM @carlos_collision_sql;
EXECUTE carlos_text_statement;
DEALLOCATE PREPARE carlos_text_statement;
SET @carlos_text_guard = IF(@carlos_collation_conflicts = 0, 'SELECT 1',
    'SELECT resolve_unique_key_collisions_before_collation_upgrade FROM demographic');
PREPARE carlos_text_statement FROM @carlos_text_guard;
EXECUTE carlos_text_statement;
DEALLOCATE PREPARE carlos_text_statement;

SET @carlos_text_ddl = (
    SELECT COALESCE(CONCAT('ALTER TABLE `demographic` ', GROUP_CONCAT(
        IF(collation_name = 'utf8mb4_uca1400_ai_ci', NULL,
            CONCAT('MODIFY COLUMN `', REPLACE(column_name, '`', '``'), '` ', column_type,
                   ' CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci',
                   IF(is_nullable = 'YES', ' NULL', ' NOT NULL'),
                   IF(column_default IS NULL, '', CONCAT(' DEFAULT ', column_default)),
                   ' COMMENT ', QUOTE(column_comment),
                   IF(extra = 'INVISIBLE', ' INVISIBLE', '')))
        ORDER BY column_name SEPARATOR ', ')), 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'demographic' AND column_name IN ('first_name', 'last_name')
);
PREPARE carlos_text_statement FROM @carlos_text_ddl;
EXECUTE carlos_text_statement;
DEALLOCATE PREPARE carlos_text_statement;

SET @carlos_text_ddl = (
    SELECT COALESCE(CONCAT('ALTER TABLE `provider` ', GROUP_CONCAT(
        IF(collation_name = 'utf8mb4_uca1400_ai_ci', NULL,
            CONCAT('MODIFY COLUMN `', REPLACE(column_name, '`', '``'), '` ', column_type,
                   ' CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci',
                   IF(is_nullable = 'YES', ' NULL', ' NOT NULL'),
                   IF(column_default IS NULL, '', CONCAT(' DEFAULT ', column_default)),
                   ' COMMENT ', QUOTE(column_comment),
                   IF(extra = 'INVISIBLE', ' INVISIBLE', '')))
        ORDER BY column_name SEPARATOR ', ')), 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'provider' AND column_name IN ('first_name', 'last_name')
);
PREPARE carlos_text_statement FROM @carlos_text_ddl;
EXECUTE carlos_text_statement;
DEALLOCATE PREPARE carlos_text_statement;

SET @carlos_text_ddl = (
    SELECT COALESCE(CONCAT('ALTER TABLE `casemgmt_note` ', GROUP_CONCAT(
        IF(collation_name = 'utf8mb4_uca1400_ai_ci', NULL,
            CONCAT('MODIFY COLUMN `', REPLACE(column_name, '`', '``'), '` ', column_type,
                   ' CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci',
                   IF(is_nullable = 'YES', ' NULL', ' NOT NULL'),
                   IF(column_default IS NULL, '', CONCAT(' DEFAULT ', column_default)),
                   ' COMMENT ', QUOTE(column_comment),
                   IF(extra = 'INVISIBLE', ' INVISIBLE', '')))
        ORDER BY column_name SEPARATOR ', ')), 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'casemgmt_note' AND column_name IN ('note')
);
PREPARE carlos_text_statement FROM @carlos_text_ddl;
EXECUTE carlos_text_statement;
DEALLOCATE PREPARE carlos_text_statement;

SET @carlos_text_ddl = (
    SELECT COALESCE(CONCAT('ALTER TABLE `document` ', GROUP_CONCAT(
        IF(collation_name = 'utf8mb4_uca1400_ai_ci', NULL,
            CONCAT('MODIFY COLUMN `', REPLACE(column_name, '`', '``'), '` ', column_type,
                   ' CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci',
                   IF(is_nullable = 'YES', ' NULL', ' NOT NULL'),
                   IF(column_default IS NULL, '', CONCAT(' DEFAULT ', column_default)),
                   ' COMMENT ', QUOTE(column_comment),
                   IF(extra = 'INVISIBLE', ' INVISIBLE', '')))
        ORDER BY column_name SEPARATOR ', ')), 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'document' AND column_name IN ('docdesc')
);
PREPARE carlos_text_statement FROM @carlos_text_ddl;
EXECUTE carlos_text_statement;
DEALLOCATE PREPARE carlos_text_statement;

SET @carlos_text_ddl = (
    SELECT COALESCE(CONCAT('ALTER TABLE `drugs` ', GROUP_CONCAT(
        IF(collation_name = 'utf8mb4_uca1400_ai_ci', NULL,
            CONCAT('MODIFY COLUMN `', REPLACE(column_name, '`', '``'), '` ', column_type,
                   ' CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci',
                   IF(is_nullable = 'YES', ' NULL', ' NOT NULL'),
                   IF(column_default IS NULL, '', CONCAT(' DEFAULT ', column_default)),
                   ' COMMENT ', QUOTE(column_comment),
                   IF(extra = 'INVISIBLE', ' INVISIBLE', '')))
        ORDER BY column_name SEPARATOR ', ')), 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'drugs' AND column_name IN ('special')
);
PREPARE carlos_text_statement FROM @carlos_text_ddl;
EXECUTE carlos_text_statement;
DEALLOCATE PREPARE carlos_text_statement;

SET @carlos_text_ddl = (
    SELECT COALESCE(CONCAT('ALTER TABLE `tickler` ', GROUP_CONCAT(
        IF(collation_name = 'utf8mb4_uca1400_ai_ci', NULL,
            CONCAT('MODIFY COLUMN `', REPLACE(column_name, '`', '``'), '` ', column_type,
                   ' CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci',
                   IF(is_nullable = 'YES', ' NULL', ' NOT NULL'),
                   IF(column_default IS NULL, '', CONCAT(' DEFAULT ', column_default)),
                   ' COMMENT ', QUOTE(column_comment),
                   IF(extra = 'INVISIBLE', ' INVISIBLE', '')))
        ORDER BY column_name SEPARATOR ', ')), 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tickler' AND column_name IN ('message')
);
PREPARE carlos_text_statement FROM @carlos_text_ddl;
EXECUTE carlos_text_statement;
DEALLOCATE PREPARE carlos_text_statement;

SET @carlos_text_ddl = (
    SELECT COALESCE(CONCAT('ALTER TABLE `appointment` ', GROUP_CONCAT(
        IF(collation_name = 'utf8mb4_bin', NULL,
            CONCAT('MODIFY COLUMN `', REPLACE(column_name, '`', '``'), '` ', column_type,
                   ' CHARACTER SET utf8mb4 COLLATE utf8mb4_bin',
                   IF(is_nullable = 'YES', ' NULL', ' NOT NULL'),
                   IF(column_default IS NULL, '', CONCAT(' DEFAULT ', column_default)),
                   ' COMMENT ', QUOTE(column_comment),
                   IF(extra = 'INVISIBLE', ' INVISIBLE', '')))
        ORDER BY column_name SEPARATOR ', ')), 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'appointment' AND column_name IN ('status')
);
PREPARE carlos_text_statement FROM @carlos_text_ddl;
EXECUTE carlos_text_statement;
DEALLOCATE PREPARE carlos_text_statement;

SET @carlos_text_ddl = (
    SELECT COALESCE(CONCAT('ALTER TABLE `appointment_status` ', GROUP_CONCAT(
        IF(collation_name = 'utf8mb4_bin', NULL,
            CONCAT('MODIFY COLUMN `', REPLACE(column_name, '`', '``'), '` ', column_type,
                   ' CHARACTER SET utf8mb4 COLLATE utf8mb4_bin',
                   IF(is_nullable = 'YES', ' NULL', ' NOT NULL'),
                   IF(column_default IS NULL, '', CONCAT(' DEFAULT ', column_default)),
                   ' COMMENT ', QUOTE(column_comment),
                   IF(extra = 'INVISIBLE', ' INVISIBLE', '')))
        ORDER BY column_name SEPARATOR ', ')), 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'appointment_status' AND column_name IN ('status')
);
PREPARE carlos_text_statement FROM @carlos_text_ddl;
EXECUTE carlos_text_statement;
DEALLOCATE PREPARE carlos_text_statement;

SET SESSION group_concat_max_len = @carlos_old_group_concat_max_len;
