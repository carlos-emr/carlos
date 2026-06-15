-- Downgrade the default doctor role's _eform privilege from "x" (full access)
-- to "w" (write). This makes the independent delete ("d") privilege return false
-- for doctors, so DelEForm2Action can enforce creator-only deletion for providers
-- while preserving all existing read, upload, and edit operations.
-- Admins retain their _eform privilege unchanged.
UPDATE secObjPrivilege
   SET privilege = 'w'
 WHERE roleName = 'doctor'
   AND objectName = '_eform'
   AND privilege = 'x';
