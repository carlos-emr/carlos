/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

-- Restore current baseline privileges after development.sql replaces
-- secObjPrivilege with its older demo snapshot. This local-only seed is also
-- safe to run against an existing development database (seed_data.sh re-runs it
-- on every devcontainer rebuild). The default carlosdoc account is intentionally
-- a full administrator in development, so remove its production/demo schedule
-- group-creation override below.
--
-- scripts/demo-dataset-contract.test.js replays this file and fails when a
-- privilege that a Playwright check names differs from the Flyway seeds.

INSERT IGNORE INTO `secObjectName`
    (`objectName`, `description`, `orgapplicable`)
VALUES
    ('_admin.flowsheet', 'Manage Flowsheets', 0),
    ('_admin.invoices', 'Restrict invoice admin to current provider', 0),
    ('_admin.schedule.groupCreate', 'Create schedule provider groups', 0);

INSERT INTO `secObjPrivilege`
    (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
VALUES
    ('admin', '_admin.auditLogPurge', 'x', 0, '999998'),
    ('admin', '_admin.flowsheet', 'x', 0, '999998'),
    ('admin', '_admin.invoices', 'r', 0, '999998'),
    ('admin', '_admin.misc', 'x', 0, '999998'),
    ('admin', '_admin.schedule', 'x', 0, '999998'),
    ('admin', '_admin.schedule.groupCreate', 'x', 0, '999998'),
    ('admin', '_site_access_privacy', 'x', 0, '999998')
ON DUPLICATE KEY UPDATE
    `privilege` = VALUES(`privilege`),
    `priority` = VALUES(`priority`),
    `provider_no` = VALUES(`provider_no`);

-- Doctor-role rows the 2023 snapshot carries with values that differ from the
-- Flyway seeds (on/bc V1.0.2), which every packaged install gets (issue #4566).
-- Without them a check passes on the deb and fails here, or the reverse.
--   _eform w: the snapshot has x. Flyway gives doctors eForm edit and export
--     (w) and leaves eForm deletion to _admin.eform, which
--     authz-read-admin-objects asserts.
--   _admin.flowsheet r: the snapshot has no doctor row. admin-role-management
--     relies on it for the day-sheet Administration link.
--   _episode and _newCasemgmt.episode o: the snapshot has x, a hand edit on the
--     demo server (its recyclebin rows 5 and 7, dated 2023-10-06, record the
--     earlier o for each).
--     The shipped default hides the encounter Episode panel from doctors;
--     granting it is a product change for a migration, not for this file.
INSERT INTO `secObjPrivilege`
    (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
VALUES
    ('doctor', '_admin.flowsheet', 'r', 0, '999998'),
    ('doctor', '_eform', 'w', 0, '999998'),
    ('doctor', '_episode', 'o', 0, '999998'),
    ('doctor', '_newCasemgmt.episode', 'o', 0, '999998')
ON DUPLICATE KEY UPDATE
    `privilege` = VALUES(`privilege`),
    `priority` = VALUES(`priority`),
    `provider_no` = VALUES(`provider_no`);

-- carlosdoc is the local test administrator and should inherit the admin role
-- grant rather than a higher-priority provider-specific denial.
DELETE FROM `secObjPrivilege`
WHERE `roleUserGroup` = '999998'
  AND `objectName` = '_admin.schedule.groupCreate'
  AND `privilege` = 'o'
  AND `provider_no` = '999998';

-- Keep the development snapshot aligned with the current baseline cleanup.
DELETE FROM `secObjPrivilege`
WHERE `objectName` = '_admin.traceability';

DELETE FROM `secObjectName`
WHERE `objectName` = '_admin.traceability';
