-- Run with the MariaDB client from the repository root, selecting a NEW DISPOSABLE
-- database. This fixture owns hl7TextInfo and must never run in a clinic database.
-- Example: mariadb disposable_migration_check < scripts/lab-label-migration-check.sql
SET NAMES utf8mb4;
SET SESSION sql_mode = CONCAT_WS(',', @@sql_mode, 'STRICT_ALL_TABLES');
CREATE TABLE hl7TextInfo (id INT PRIMARY KEY, label VARCHAR(255) NULL) CHARACTER SET utf8mb4;
INSERT INTO hl7TextInfo VALUES (1,NULL),(2,''),(3,REPEAT('x',255)),(4,'Étiquette | résultat');
CREATE TEMPORARY TABLE expected_labels (id INT PRIMARY KEY, label TEXT NULL) CHARACTER SET utf8mb4;
INSERT INTO expected_labels SELECT id, label FROM hl7TextInfo;
SOURCE database/mysql/migration/common/V1.0.40__widen_lab_labels.sql;
DELIMITER //
CREATE PROCEDURE assert_labels_preserved()
BEGIN
    IF (SELECT COUNT(*) FROM hl7TextInfo) <> (SELECT COUNT(*) FROM expected_labels)
       OR EXISTS (SELECT 1 FROM expected_labels e LEFT JOIN hl7TextInfo h USING(id)
                  WHERE h.id IS NULL OR NOT (BINARY h.label <=> BINARY e.label)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Lab label migration changed existing values';
    END IF;
END//
DELIMITER ;
CALL assert_labels_preserved();
INSERT INTO hl7TextInfo VALUES (5,REPEAT('panel ',100)),(6,REPEAT('résultat ',100));
INSERT INTO expected_labels SELECT id,label FROM hl7TextInfo WHERE id IN (5,6);
SOURCE database/mysql/migration/common/V1.0.40__widen_lab_labels.sql;
CALL assert_labels_preserved();
SELECT 'PASS: original/null/empty/unicode/full-length labels preserved, widening repeatable' AS result;
DROP PROCEDURE assert_labels_preserved;
DROP TEMPORARY TABLE expected_labels;
DROP TABLE hl7TextInfo;
