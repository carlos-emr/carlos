-- Fix missing security object privileges that left admin features inaccessible.
--
-- Affects fresh installs created via createdatabase_on.sh (update scripts are not
-- applied by that path, so entries from update-2017-*.sql never landed in the base
-- schema). Also fixes existing installs where admin users encounter SecurityException
-- for _admin.misc, _admin.billing, and _admin.auditLogPurge.
--
-- References: Bug #500 (update-patient-provider---sec-obj)

-- _admin.eformreporttool: backport from update-2017-02-27.sql
INSERT IGNORE INTO `secObjectName` (`objectName`) VALUES ('_admin.eformreporttool');
INSERT IGNORE INTO `secObjPrivilege` (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
    VALUES ('admin', '_admin.eformreporttool', 'x', 0, '999998');
-- _admin.billing: secObjectName existed but no privilege row; hasPrivilege() returned
-- false for all roles, including admin, breaking ViewShareCalendarPopup2Action.
INSERT IGNORE INTO `secObjectName` (`objectName`) VALUES ('_admin.billing');
INSERT IGNORE INTO `secObjPrivilege` (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
    VALUES ('admin', '_admin.billing', 'x', 0, '999998');
-- _admin.misc: same gap; broke UpdateDemographicProvider2Action, AdminSaveMyGroup2Action,
-- AdminNewGroup2Action, ManageFlowsheets2Action, and others.
INSERT IGNORE INTO `secObjectName` (`objectName`) VALUES ('_admin.misc');
INSERT IGNORE INTO `secObjPrivilege` (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
    VALUES ('admin', '_admin.misc', 'x', 0, '999998');
-- _admin.auditLogPurge: secObjectName existed but no privilege row; broke AuditLogPurge2Action.
INSERT IGNORE INTO `secObjectName` (`objectName`) VALUES ('_admin.auditLogPurge');
INSERT IGNORE INTO `secObjPrivilege` (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
    VALUES ('admin', '_admin.auditLogPurge', 'x', 0, '999998')
-- _hrm for doctor role: was 'o' (deny), should be 'x' to allow all doctors to view HRM records.
-- HRM admin functions remain separately controlled by _hrm.administrator (HRMAdmin role).
UPDATE `secObjPrivilege`
    SET `privilege` = 'x'
    WHERE `roleUserGroup` = 'doctor'
      AND `objectName` = '_hrm'
      AND `privilege` = 'o';

