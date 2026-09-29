-- update-2026-05-21-carlosdoc-schedule-group-privilege.sql
--
-- Keep schedule provider-group creation separate from the broader
-- `_admin.schedule` privilege and grant it to administrators. The default
-- carlosdoc provider inherits this permission through its active admin role;
-- remove the former provider-specific denial that overrode that role grant.
--
-- Idempotent via INSERT IGNORE — safe to re-run on databases that already
-- have any of these rows.
INSERT IGNORE INTO `secObjectName`
    (`objectName`, `description`, `orgapplicable`)
VALUES
    ('_admin.schedule.groupCreate', 'Create schedule provider groups', 0);

INSERT IGNORE INTO `secObjPrivilege`
    (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
VALUES
    ('admin', '_admin.schedule.groupCreate', 'x', 0, '999998');

DELETE FROM `secObjPrivilege`
WHERE `roleUserGroup` = '999998'
  AND `objectName` = '_admin.schedule.groupCreate'
  AND `privilege` = 'o'
  AND `provider_no` = '999998';
