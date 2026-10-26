-- Field Note Report is an administration report. Register its existing menu object
-- and grant the administrator role access without replacing explicit local grants/denials.
INSERT INTO secObjectName (objectName, description, orgapplicable)
SELECT '_admin.fieldnote', 'Field Note Report and eForm selection', 0
WHERE NOT EXISTS (SELECT 1 FROM secObjectName WHERE objectName = '_admin.fieldnote');

INSERT INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no)
SELECT 'admin', '_admin.fieldnote', 'x', 0, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM secObjPrivilege WHERE roleUserGroup = 'admin' AND objectName = '_admin.fieldnote'
);
