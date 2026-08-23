-- Portable follow-up for the billing filename indexes introduced by the
-- published V1.0.11 migration. V1.0.11 must remain byte-for-byte unchanged
-- because Flyway records its checksum in databases upgraded through alpha1.
--
-- MariaDB installations may already have these exact indexes from V1.0.11 or
-- update-2026-05-03-billing-disk-filename-unique.sql. These guarded statements
-- are valid on both MySQL and MariaDB. They skip creation only when the exact
-- name and single-column unique shape are already present. If duplicate
-- non-NULL filenames exist and an index is absent, creation fails rather than
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
