-- Downgrade the default doctor role's _eform privilege from "x" (full access)
-- to "w" (write). This makes the independent delete ("d") privilege return false
-- for doctors, so DelEForm2Action can enforce creator-only deletion for providers
-- while preserving all existing read, upload, and edit operations.
-- Grant admins the direct _eform delete privilege checked by DelEForm2Action.
INSERT INTO secObjPrivilege (roleName, objectName, privilege, orgapplicable, orgcd)
SELECT 'admin', '_eform', 'd', 0, '999998'
WHERE NOT EXISTS (
    SELECT 1
      FROM secObjPrivilege
     WHERE roleName = 'admin'
       AND objectName = '_eform'
       AND privilege IN ('d', 'x')
);

UPDATE secObjPrivilege
   SET privilege = 'w'
 WHERE roleName = 'doctor'
   AND objectName = '_eform'
   AND privilege = 'x';
