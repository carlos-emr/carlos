-- Downgrade the default doctor role's _eform privilege from "x" (full access)
-- to "w" (write). Doctors retain read, upload, and edit operations but lose
-- the implied delete capability. eForm template deletion is admin-only,
-- enforced via DelEForm2Action which checks the _admin.eform write privilege.
UPDATE secObjPrivilege
   SET privilege = 'w'
 WHERE roleUserGroup = 'doctor'
   AND objectName = '_eform'
   AND privilege = 'x';