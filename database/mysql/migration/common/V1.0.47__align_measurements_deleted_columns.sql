-- Align measurementsDeleted with measurements so a row can always be archived.
-- measurements.type is varchar(50), and comments and dateObserved accept NULL there;
-- measurementsDeleted kept varchar(4) and NOT NULL. Archiving a measurement whose type
-- is longer than four characters, or that has no comment or observation date, failed
-- when a newer version of a lab replaced its measurements or a measurement was deleted.
-- Widening only; existing rows are unchanged. Re-running is safe.
ALTER TABLE measurementsDeleted
    MODIFY COLUMN `type` varchar(50) NOT NULL,
    MODIFY COLUMN `comments` varchar(255) DEFAULT NULL,
    MODIFY COLUMN `dateObserved` datetime DEFAULT NULL;
