alter table security add column IF NOT EXISTS usingMfa BOOL not null DEFAULT FALSE;
alter table security add column IF NOT EXISTS mfaSecret VARCHAR(255);
