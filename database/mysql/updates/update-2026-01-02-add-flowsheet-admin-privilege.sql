-- Add new security object for flowsheet administration
-- This allows granting doctors access to "Manage Flowsheets" page
-- without giving them full _admin.misc privileges
INSERT INTO secObjectName (objectName, description, orgapplicable)
VALUES ('_admin.flowsheet', 'Manage Flowsheets', 0);

-- Grant read access to doctor role by default
-- Priority 1 ensures this is checked before other _admin.* entries with priority 0
-- that have 'o' (no rights) which would otherwise override this privilege
INSERT INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no)
VALUES ('doctor', '_admin.flowsheet', 'r', 1, '999998');

-- Grant full access to admin role
INSERT INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no)
VALUES ('admin', '_admin.flowsheet', 'x', 0, '999998');

-- Remove conflicting 'o' (no rights) entries for doctor role that would override
-- the _admin.flowsheet privilege due to OscarRoleObjectPrivilege bug where
-- Properties entries get overwritten. These 'o' entries are redundant anyway
-- (no access is the default behavior).
DELETE FROM secObjPrivilege
WHERE roleUserGroup = 'doctor'
  AND privilege = 'o'
  AND objectName IN ('_admin.reporting');
