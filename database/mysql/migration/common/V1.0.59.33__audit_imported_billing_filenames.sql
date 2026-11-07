-- A repeated OSCAR filename is billing history, not a duplicate submission.
-- The importer preserves the original and assigns a unique live name to every
-- member of the group. Shared files remain unassigned until a billing
-- administrator establishes which submission they belong to.
CREATE TABLE billing_filename_import (
  source_table VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_id BIGINT NOT NULL,
  original_filename VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
  imported_filename VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
  group_size BIGINT NOT NULL,
  reason VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  file_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  file_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  reconciled_by VARCHAR(128) NULL,
  reconciled_at DATETIME NULL,
  PRIMARY KEY (source_table, source_id),
  KEY imported_filename_lookup (imported_filename),
  KEY original_filename_lookup (original_filename),
  CONSTRAINT billing_filename_group_size CHECK (group_size >= 1),
  CONSTRAINT billing_filename_reason CHECK (reason IN ('unchanged', 'collision')),
  CONSTRAINT billing_filename_file_status CHECK (file_status IN ('unchanged', 'ambiguous', 'reconciled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
