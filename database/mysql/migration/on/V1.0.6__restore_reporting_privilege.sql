-- Restore legacy doctor reporting access omitted from the generated Ontario data baseline.
INSERT IGNORE INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no)
VALUES ('doctor', '_admin.reporting', 'o', 0, '999998');
