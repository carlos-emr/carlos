-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- The released importer created its independent security object without a role
-- grant. Seed it for existing full administrators without rewriting that migration.
-- Preserve every existing explicit grant or denial. A restricted admin role and
-- all other roles continue to require an explicit, auditable migration grant.
INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority)
SELECT 'admin','_cds_import','x',0
WHERE EXISTS (
    SELECT 1 FROM secObjPrivilege
    WHERE roleUserGroup='admin' AND objectName='_admin' AND privilege IN ('x','w')
      AND (provider_no IS NULL OR provider_no='')
)
AND NOT EXISTS (
    SELECT 1 FROM secObjPrivilege WHERE roleUserGroup='admin' AND objectName='_cds_import'
);
