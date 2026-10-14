-- AES-GCM envelopes and Base64 expand transport credentials beyond their plaintext size.
-- Existing configurations can already use all 1,000 characters of the legacy column.
-- Match EmailConfig's TEXT mapping; preserve existing values and NULL semantics.
ALTER TABLE emailConfig MODIFY COLUMN configDetails TEXT DEFAULT NULL;
