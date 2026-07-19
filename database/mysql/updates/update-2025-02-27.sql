alter table security add usingMfa BOOL not null;
alter table security add mfaSecret VARCHAR(255);
