-- --------------------------------------------------------------------------------
-- CARLOS EMR Database Update
-- Date: 2026-05-05
-- Description: Grant missing security privileges to the default 'doctor' role
-- and the explicit '999998' provider (carlosdoc) to prevent SecurityExceptions.
-- --------------------------------------------------------------------------------

-- Privileges for the modern Dashboard
INSERT IGNORE INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no) VALUES ('doctor', '_dashboardChgUser', 'x', 0, '999998');
INSERT IGNORE INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no) VALUES ('doctor', '_dashboardDisplay', 'x', 0, '999998');
INSERT IGNORE INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no) VALUES ('doctor', '_dashboardDrilldown', 'x', 0, '999998');
INSERT IGNORE INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no) VALUES ('doctor', '_dashboardManager', 'x', 0, '999998');

-- Form Privileges
INSERT IGNORE INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no) VALUES ('doctor', '_formMentalHealth', 'x', 0, '999998');

-- PM Module Privileges
INSERT IGNORE INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no) VALUES ('doctor', '_pmm_management', 'x', 0, '999998');

-- Record Merge Privileges
INSERT IGNORE INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no) VALUES ('doctor', '_merge', 'x', 0, '999998');

-- Rx Research Privileges
INSERT IGNORE INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no) VALUES ('doctor', '_rxresearch', 'x', 0, '999998');
