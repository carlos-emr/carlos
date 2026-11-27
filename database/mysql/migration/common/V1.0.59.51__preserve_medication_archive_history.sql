-- Preserve the first archive state observed by this release. This also covers
-- previously imported rows with no per-row provenance and legacy JDBC writers.
-- The operational state remains readable by applications that use the original columns.
ALTER TABLE drugs
    ADD COLUMN archived_before_lifecycle TINYINT(1) NULL,
    ADD COLUMN archived_date_before_lifecycle DATETIME NULL,
    ADD COLUMN archived_reason_before_lifecycle VARCHAR(100) NULL;

CREATE TRIGGER drugs_preserve_archive_history BEFORE UPDATE ON drugs
FOR EACH ROW SET
    NEW.archived_date_before_lifecycle = IF(
        OLD.archived_before_lifecycle IS NULL AND
        NOT (NEW.archived <=> OLD.archived AND NEW.archived_date <=> OLD.archived_date
             AND BINARY NEW.archived_reason <=> BINARY OLD.archived_reason),
        OLD.archived_date, OLD.archived_date_before_lifecycle),
    NEW.archived_reason_before_lifecycle = IF(
        OLD.archived_before_lifecycle IS NULL AND
        NOT (NEW.archived <=> OLD.archived AND NEW.archived_date <=> OLD.archived_date
             AND BINARY NEW.archived_reason <=> BINARY OLD.archived_reason),
        OLD.archived_reason, OLD.archived_reason_before_lifecycle),
    NEW.archived_before_lifecycle = IF(
        OLD.archived_before_lifecycle IS NULL AND
        NOT (NEW.archived <=> OLD.archived AND NEW.archived_date <=> OLD.archived_date
             AND BINARY NEW.archived_reason <=> BINARY OLD.archived_reason),
        OLD.archived, OLD.archived_before_lifecycle);
