-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- The released importer created its independent security object without a role
-- grant. Seed it for existing full administrators without rewriting that migration.
-- Preserve every existing explicit grant or denial. provider_no is row metadata,
-- not a restriction evaluated by the role privilege service.
INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority)
SELECT 'admin','_cds_import','x',0
WHERE EXISTS (
    SELECT 1 FROM secObjPrivilege
    WHERE roleUserGroup='admin' AND objectName='_admin' AND privilege IN ('x','w')
)
AND NOT EXISTS (
    SELECT 1 FROM secObjPrivilege WHERE roleUserGroup='admin' AND objectName='_cds_import'
);
