-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 CARLOS Contributors
--
-- roles.sql — synthetic role/privilege and legacy-data cases for the OSCAR 19
-- migration rehearsal (loaded by build-o19-fixture.sh after demo.sql). Every
-- value is clearly fake; no real clinic, person or patient. Exercises the M8
-- roles post-step: a clinic-custom role, an assignment with activeyn NULL, an
-- expired login, a document-queue object, a patient-scoped lockout, a clinic
-- override of a stock grant, a legacy prevention type code and a removed-module
-- property key.

-- a clinic-custom role resembling the stock nurse role (its CARLOS-era grants
-- come from that template), with one deviation on _rx; the copied nurse set
-- includes _pmm objects, which CARLOS still checks and the merge carries.
-- Timestamps are fixed so the fixture dump is reproducible.
INSERT INTO `secRole` (`role_name`, `description`)
  VALUES ('Triage Nurse', 'Triage Nurse (clinic-custom fixture role)');
INSERT INTO `secObjPrivilege` (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
  SELECT 'Triage Nurse', `objectName`, `privilege`, `priority`, `provider_no`
    FROM `secObjPrivilege` WHERE `roleUserGroup` = 'nurse' AND `objectName` <> '_rx';
INSERT INTO `secObjPrivilege` (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
  VALUES ('Triage Nurse', '_rx', 'r', 0, '999998');

-- providers: a Triage Nurse (active), a doctor whose role row has activeyn
-- NULL (import activates it), a doctor whose login is expired (advisory)
INSERT INTO `provider` (`provider_no`, `last_name`, `first_name`, `provider_type`, `specialty`, `status`, `lastUpdateUser`, `lastUpdateDate`) VALUES
  ('999901', 'FIXTURE', 'TRIAGE', 'nurse', 'Triage', '1', '999998', '2019-06-01 09:00:00'),
  ('999902', 'FIXTURE', 'NULLROLE', 'doctor', 'Family Practice', '1', '999998', '2019-06-01 09:00:00'),
  ('999903', 'FIXTURE', 'EXPIRED', 'doctor', 'Family Practice', '1', '999998', '2019-06-01 09:00:00');
INSERT INTO `security` (`user_name`, `password`, `provider_no`, `pin`, `b_ExpireSet`, `date_ExpireDate`, `forcePasswordReset`, `storageVersion`) VALUES
  ('fixture.triage',   '-51-282443-97-5-9410489-60-1021-45-127-12435464-32', '999901', '1111', 1, '2100-01-01', 0, 1),
  ('fixture.nullrole', '-51-282443-97-5-9410489-60-1021-45-127-12435464-32', '999902', '1111', 1, '2100-01-01', 0, 1),
  ('fixture.expired',  '-51-282443-97-5-9410489-60-1021-45-127-12435464-32', '999903', '1111', 1, '2020-01-01', 0, 1);
INSERT INTO `secUserRole` (`provider_no`, `role_name`, `orgcd`, `activeyn`, `lastUpdateDate`) VALUES
  ('999901', 'Triage Nurse', 'R0000001', 1,    '2019-06-01 09:00:00'),
  ('999902', 'doctor',       'R0000001', NULL, '2019-06-01 09:00:00'),
  ('999903', 'doctor',       'R0000001', 1,    '2019-06-01 09:00:00');

-- a second document queue with its privilege object, stored as CARLOS and
-- O19 store it: the role in roleUserGroup, `_queue.<id>` as the objectName
-- (OscarRoleObjectPrivilege.getPrivilegeProp("_queue." + qid)); rides in
-- through the merge, the queue row itself is replace_seed
INSERT INTO `queue` VALUES (2, 'Triage');
INSERT INTO `secObjPrivilege` (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
  VALUES ('Triage Nurse', '_queue.2', 'x', 0, '999998');

-- a grant on an object no CARLOS code checks (the merge leaves it behind
-- and privilege-diff.txt lists it)
INSERT INTO `secObjPrivilege` (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
  VALUES ('Triage Nurse', '_admin.traceability', 'x', 0, '999998');

-- a patient-scoped lockout as DemographicMerged writes it
INSERT INTO `secObjPrivilege` (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
  SELECT '_all', CONCAT('_eChart$', MIN(`demographic_no`)), '|or|', 0, '0' FROM `demographic`;

-- a clinic override of a stock grant: CARLOS's seed wins and the diff
-- report must name it
UPDATE `secObjPrivilege` SET `privilege` = 'r'
  WHERE `roleUserGroup` = 'doctor' AND `objectName` = '_billing';

-- legacy prevention type codes (Flu -> Inf, dTaP -> Tdap under Health Canada
-- naming) next to the VALID pediatric code DTaP, which differs from the
-- legacy dTaP only by case and must survive the normalisation untouched
INSERT INTO `preventions` (`demographic_no`, `creation_date`, `prevention_date`, `provider_no`, `prevention_type`, `deleted`, `refused`, `never`, `creator`, `lastUpdateDate`)
  SELECT MIN(`demographic_no`), '2019-06-01 09:00:00', '2019-11-01 00:00:00', '999998', 'Flu', '0', '0', '0', 999998, '2019-06-01 09:00:00' FROM `demographic`;
INSERT INTO `preventions` (`demographic_no`, `creation_date`, `prevention_date`, `provider_no`, `prevention_type`, `deleted`, `refused`, `never`, `creator`, `lastUpdateDate`)
  SELECT MIN(`demographic_no`), '2019-06-01 09:00:00', '2019-08-01 00:00:00', '999998', 'dTaP', '0', '0', '0', 999998, '2019-06-01 09:00:00' FROM `demographic`;
INSERT INTO `preventions` (`demographic_no`, `creation_date`, `prevention_date`, `provider_no`, `prevention_type`, `deleted`, `refused`, `never`, `creator`, `lastUpdateDate`)
  SELECT MIN(`demographic_no`), '2019-06-01 09:00:00', '2010-02-01 00:00:00', '999998', 'DTaP', '0', '0', '0', 999998, '2019-06-01 09:00:00' FROM `demographic`;

-- a removed-module key in the property table
INSERT INTO `property` (`name`, `value`, `provider_no`)
  VALUES ('INTEGRATOR_fixture_sync', 'true', NULL);
