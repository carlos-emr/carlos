-- Ontario-only billing filenames must be unique so claim and HTML output files
-- cannot collide. This replaces the legacy dated update script for both fresh
-- installs and Flyway-adopted upgrades.
--
-- Existing CARLOS installations may already have these exact indexes from
-- update-2026-05-03-billing-disk-filename-unique.sql. The guarded statements
-- keep Flyway adoption idempotent on both MySQL and MariaDB. If an
-- installation has
-- duplicate non-NULL filenames and no index yet, the CREATE fails rather than
-- silently discarding or rewriting billing data.

SET @index_present = (
  SELECT COUNT(*) = 1
    AND MIN(column_name) = 'ohipfilename'
    AND MIN(non_unique) = 0
    AND MIN(seq_in_index) = 1
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'billing_on_diskname'
    AND index_name = 'billing_on_diskname_ohipfilename_uq'
);
SET @ddl = IF(
  @index_present,
  'SELECT 1',
  'CREATE UNIQUE INDEX billing_on_diskname_ohipfilename_uq ON billing_on_diskname (ohipfilename)'
);
PREPARE index_statement FROM @ddl;
EXECUTE index_statement;
DEALLOCATE PREPARE index_statement;

SET @index_present = (
  SELECT COUNT(*) = 1
    AND MIN(column_name) = 'htmlfilename'
    AND MIN(non_unique) = 0
    AND MIN(seq_in_index) = 1
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'billing_on_filename'
    AND index_name = 'billing_on_filename_htmlfilename_uq'
);
SET @ddl = IF(
  @index_present,
  'SELECT 1',
  'CREATE UNIQUE INDEX billing_on_filename_htmlfilename_uq ON billing_on_filename (htmlfilename)'
);
PREPARE index_statement FROM @ddl;
EXECUTE index_statement;
DEALLOCATE PREPARE index_statement;
