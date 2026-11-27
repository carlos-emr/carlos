-- Medication lifecycle writes capture the previous state in the same transaction.
-- A NULL flag means no transition has been captured for this row.
ALTER TABLE drugs
    ADD COLUMN archived_before_lifecycle TINYINT(1) NULL,
    ADD COLUMN archived_date_before_lifecycle DATETIME NULL,
    ADD COLUMN archived_reason_before_lifecycle VARCHAR(100) NULL;
