-- Appointment types and the appointments created from them store the selected
-- site name. Keep all three columns aligned with the 255-character site.name
-- column rather than silently truncating valid multisite selections.
ALTER TABLE appointment
    MODIFY COLUMN location varchar(255) DEFAULT NULL;

ALTER TABLE appointmentArchive
    MODIFY COLUMN location varchar(255) DEFAULT NULL;

ALTER TABLE appointmentType
    MODIFY COLUMN location varchar(255) DEFAULT NULL;
