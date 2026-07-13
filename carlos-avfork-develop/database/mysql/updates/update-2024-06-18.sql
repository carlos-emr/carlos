ALTER TABLE emailConfig ALTER COLUMN active SET DEFAULT FALSE;
UPDATE emailConfig SET active = FALSE WHERE active IS NULL;

ALTER TABLE emailLog ALTER COLUMN isEncrypted SET DEFAULT FALSE;
UPDATE emailLog SET isEncrypted = FALSE WHERE isEncrypted IS NULL;

ALTER TABLE emailLog ALTER COLUMN isAttachmentEncrypted SET DEFAULT FALSE;
UPDATE emailLog SET isAttachmentEncrypted = FALSE WHERE isAttachmentEncrypted IS NULL;