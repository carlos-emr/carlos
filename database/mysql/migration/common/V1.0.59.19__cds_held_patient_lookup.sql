-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- Every guarded transaction checks for held charts; avoid scanning the imported patient population.
SET @cds_hold_index_shape = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND table_name='cds_import_patient' AND index_name='cds_patient_held_lookup'
);
SET @cds_hold_index_unique = (
    SELECT MIN(non_unique) FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND table_name='cds_import_patient' AND index_name='cds_patient_held_lookup'
);
SET @cds_hold_index_ddl = CASE
    WHEN @cds_hold_index_shape IS NULL THEN 'ALTER TABLE cds_import_patient ADD KEY cds_patient_held_lookup (held,id)'
    WHEN @cds_hold_index_shape='held,id' AND @cds_hold_index_unique=1 THEN 'SELECT 1'
    ELSE 'SELECT cds_import_held_index_shape_requires_review FROM cds_import_patient'
END;
PREPARE cds_hold_index_statement FROM @cds_hold_index_ddl;
EXECUTE cds_hold_index_statement;
DEALLOCATE PREPARE cds_hold_index_statement;
