-- Remove the orphaned admin Traceability permission after the report, routes,
-- and UI entry points were removed.
DELETE FROM `secObjPrivilege`
WHERE `objectName` = '_admin.traceability';

DELETE FROM `secObjectName`
WHERE `objectName` = '_admin.traceability';
