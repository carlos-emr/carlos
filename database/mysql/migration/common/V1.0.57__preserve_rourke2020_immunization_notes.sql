-- Preserve the page III and IV immunization notes offered by Rourke 2020.
-- Existing records have no recoverable value for these previously discarded fields.
-- information_schema guards support both MySQL and MariaDB and allow a retry
-- after either independently committed ALTER TABLE statement.
SET @rourke_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'formRourke2020'
            AND column_name = 'p3_immunization'),
    'SELECT 1',
    'ALTER TABLE formRourke2020 ADD COLUMN p3_immunization TEXT NULL'
);
PREPARE rourke_statement FROM @rourke_ddl;
EXECUTE rourke_statement;
DEALLOCATE PREPARE rourke_statement;

SET @rourke_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'formRourke2020'
            AND column_name = 'p4_immunization'),
    'SELECT 1',
    'ALTER TABLE formRourke2020 ADD COLUMN p4_immunization TEXT NULL'
);
PREPARE rourke_statement FROM @rourke_ddl;
EXECUTE rourke_statement;
DEALLOCATE PREPARE rourke_statement;
