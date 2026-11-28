-- A reviewed BPMH medication list can exceed the 65,535-byte BLOB limit
-- after instructions, patient-reported use and notes have been serialized.
ALTER TABLE formBPMH MODIFY COLUMN drugs MEDIUMBLOB DEFAULT NULL;
