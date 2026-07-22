-- update-2026-05-21-carlosdoc-schedule-group-privilege.sql
--
-- Keep the default carlosdoc provider in the admin role while separating
-- schedule provider-group creation from the broader `_admin.schedule`
-- privilege. Admin users retain the new group-creation object by default,
-- but provider_no 999998 (carlosdoc) receives a higher-priority explicit
-- no-rights row so the development/demo account cannot create Schedule groups.
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
    ('admin', '_admin.schedule.groupCreate', 'x', 0, '999998'),
    ('999998', '_admin.schedule.groupCreate', 'o', 1, '999998');
