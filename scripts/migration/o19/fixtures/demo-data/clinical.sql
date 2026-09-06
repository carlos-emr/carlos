-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 CARLOS Contributors
--
-- Clinical rows the vendored demo.sql does not carry, plus the text
-- encodings the import's charset path exists for. Synthetic; see
-- fixtures/PROVENANCE.md.
--
-- The vendored dataset populates demographics, labs, drugs, preventions
-- and messages, and nothing else: no encounter notes, no appointments,
-- no ticklers, no consultations, no billing. That left eight of the
-- fourteen chunked tables, the provincial billing copy paths (now in
-- the clinical-on.sql / clinical-bc.sql siblings), the
-- tickler zero-date value_exprs, the fk_remap indirection through an
-- appended merge parent, and every CHARSET_SCAN column unexercised by
-- the rehearsal -- so a regression in any of them would ship green.
--
-- The database is created LATIN1 (see build-o19-fixture.sh), so the
-- accented values below are stored as latin1 bytes exactly as a real
-- 2014-era clinic stores them. Rows are deliberately of three kinds:
--   1. correctly encoded accents        -> must survive verbatim
--   2. double-encoded text ('Ã©' for 'é') -> the per-row repair fixes it,
--      and it forces --accept charset-repair, which the rehearsal must
--      therefore pass
--   3. plain ASCII                      -> must not be touched by the repair
--
-- Every INSERT names its columns, so a patch-level difference in column
-- order cannot silently shift a value into the wrong field.

SET @clin1 = (SELECT demographic_no FROM demographic
              WHERE last_name = 'PATIENT' LIMIT 1);

-- ---------------------------------------------------------------------
-- a demographic whose name is correctly encoded, and one whose name is
-- double-encoded: demographic.last_name is a CHARSET_SCAN column
-- ---------------------------------------------------------------------
INSERT INTO `demographic`
  (`title`, `last_name`, `first_name`, `address`, `city`, `province`,
   `postal`, `phone`, `year_of_birth`, `month_of_birth`, `date_of_birth`,
   `hin`, `ver`, `roster_status`, `patient_status`, `date_joined`,
   `chart_no`, `official_lang`, `sex`, `end_date`, `eff_date`, `hc_type`,
   `family_doctor`, `lastUpdateUser`, `lastUpdateDate`)
VALUES
  ('Mme', 'CÔTÉ', 'GENEVIÈVE', '12 rue Principale', 'Hawkesbury', 'ON',
   'K6A 1A1', '613-555-0101', '1974', '03', '17', '1111111119', 'AB',
   'RO', 'AC', '2005-04-01', '101', 'French', 'F', '2051-01-05',
   '2005-04-01', 'ON', '<rdohip></rdohip><rd></rd>', '999998',
   '2015-07-19'),
  ('M', 'LEFEBVRE', 'ANDRÃ©', '9 Main St', 'Cornwall', 'ON',
   'K6H 1A1', '613-555-0102', '1968', '11', '02', '1111111127', 'AC',
   'RO', 'AC', '2006-05-02', '102', 'English', 'M', '2051-01-05',
   '2006-05-02', 'ON', '<rdohip></rdohip><rd></rd>', '999998',
   '2015-07-19');
SET @clin3 = (SELECT demographic_no FROM demographic
              WHERE chart_no = '101' LIMIT 1);
SET @clin4 = (SELECT demographic_no FROM demographic
              WHERE chart_no = '102' LIMIT 1);

-- ---------------------------------------------------------------------
-- encounter notes: casemgmt_note is chunked, and note is CHARSET_SCAN
-- ---------------------------------------------------------------------
INSERT INTO `casemgmt_note`
  (`update_date`, `observation_date`, `demographic_no`, `provider_no`,
   `note`, `signed`, `include_issue_innote`, `signing_provider_no`,
   `encounter_type`, `billing_code`, `program_no`, `reporter_caisi_role`,
   `reporter_program_team`, `history`, `archived`, `position`)
VALUES
  ('2014-03-04 09:15:00', '2014-03-04 09:00:00', @clin1, '999998',
   'Follow-up visit. BP 128/82. Continue current therapy.', 1, 0,
   '999998', 'Face to Face Encounter with client', '', '10016', '1', '1',
   '', 0, 0),
  ('2014-05-06 11:00:00', '2014-05-06 10:45:00', @clin3, '999998',
   'Contrôle annuel. Aucun problème signalé; répéter les analyses dans six mois.',
   1, 0, '999998', 'Face to Face Encounter with client', '', '10016',
   '1', '1', '', 0, 0),
  ('2014-06-07 14:30:00', '2014-06-07 14:00:00', @clin4, '999998',
   'Suivi post-opÃ©ratoire. Plaie propre, pas de fiÃ¨vre.', 1, 0,
   '999998', 'Face to Face Encounter with client', '', '10016', '1', '1',
   '', 0, 0);

INSERT INTO `casemgmt_note_ext` (`note_id`, `key_val`, `value`)
SELECT note_id, 'Reason', 'Routine follow-up'
  FROM casemgmt_note WHERE demographic_no = @clin1;

INSERT INTO `casemgmt_issue`
  (`demographic_no`, `issue_id`, `acute`, `certain`, `major`, `resolved`,
   `program_id`, `type`, `update_date`)
SELECT @clin1, MIN(issue_id), 0, 1, 1, 0, 10016, 'system',
       '2014-03-04 09:15:00'
  FROM issue HAVING MIN(issue_id) IS NOT NULL;

-- ---------------------------------------------------------------------
-- appointments: reason is CHARSET_SCAN
-- ---------------------------------------------------------------------
INSERT INTO `appointment`
  (`provider_no`, `appointment_date`, `start_time`, `end_time`, `name`,
   `demographic_no`, `program_id`, `notes`, `reason`, `location`, `type`,
   `style`, `billing`, `status`, `createdatetime`, `updatedatetime`,
   `creator`, `lastupdateuser`, `remarks`)
VALUES
  ('999998', '2014-03-04', '09:00:00', '09:15:00', 'PATIENT, TEST',
   @clin1, 10016, '', 'Follow-up', 'Main Clinic', '', 'bg', '', 'B',
   '2014-02-20 10:00:00', '2014-03-04 09:20:00', 'oscardoc', '999998',
   ''),
  ('999998', '2014-05-06', '10:45:00', '11:00:00', 'CÔTÉ, GENEVIÈVE',
   @clin3, 10016, '', 'Contrôle annuel', 'Main Clinic', '', 'bg', '',
   'B', '2014-04-20 10:00:00', '2014-05-06 11:05:00', 'oscardoc',
   '999998', '');

-- ---------------------------------------------------------------------
-- ticklers: both branches of VALUE_EXPRS['tickler']['creation_date'] --
-- CARLOS's creation_date is a NOT NULL TIMESTAMP fed from these two
-- nullable DATETIMEs, and O19 writes zero dates into them
-- ---------------------------------------------------------------------
INSERT INTO `tickler_category` (`category`, `description`, `active`)
VALUES ('Rappel', 'Clinic-defined tickler category', b'1');
SET @cat = LAST_INSERT_ID();

INSERT INTO `tickler`
  (`demographic_no`, `program_id`, `message`, `status`, `update_date`,
   `service_date`, `creator`, `priority`, `category_id`)
VALUES
  -- ordinary row: update_date carries the creation moment
  (@clin1, 10016, 'Recall for bloodwork', 'A', '2014-03-05 08:00:00',
   '2014-09-05 00:00:00', '999998', 'Normal', @cat),
  -- update_date is the column DEFAULT zero date: the expression must
  -- fall through to service_date
  (@clin3, 10016, 'Rappel: résultats de laboratoire', 'A',
   '0001-01-01 00:00:00', '2014-11-06 00:00:00', '999998', 'High', @cat),
  -- both are zero/NULL: the expression must yield NULL, not a zero
  -- timestamp in a NOT NULL column
  (@clin4, 10016, 'Follow up when convenient', 'A',
   '0001-01-01 00:00:00', NULL, '999998', 'Normal', NULL);

-- ---------------------------------------------------------------------
-- consultations: consultationServices is a merge parent with a surrogate
-- key, and consultationRequests.serviceId is remapped through its id map
-- ---------------------------------------------------------------------
INSERT INTO `consultationServices` (`serviceDesc`, `active`)
VALUES ('Cardiologie (clinic-defined)', '1');
SET @svc = LAST_INSERT_ID();

INSERT INTO `consultationRequests`
  (`referalDate`, `serviceId`, `specId`, `reason`, `clinicalInfo`,
   `currentMeds`, `allergies`, `providerNo`, `demographicNo`, `status`,
   `sendTo`, `urgency`, `lastUpdateDate`)
VALUES
  ('2014-04-01', @svc, NULL, 'Palpitations', 'Intermittent palpitations',
   'none', 'none', '999998', @clin1, '1', '1', '2', '2014-04-01 09:00:00'),
  ('2014-05-10', @svc, NULL, 'Douleur thoracique atypique',
   'Symptômes depuis trois semaines', 'aucun', 'aucune', '999998',
   @clin3, '1', '1', '2', '2014-05-10 09:00:00');

-- ---------------------------------------------------------------------
-- allergies and the custom demographic sheet (demographiccust.content is
-- CHARSET_SCAN)
-- ---------------------------------------------------------------------
INSERT INTO `allergies`
  (`demographic_no`, `entry_date`, `DESCRIPTION`, `TYPECODE`, `reaction`,
   `archived`, `start_date`, `severity_of_reaction`, `position`,
   `lastUpdateDate`, `providerNo`)
VALUES
  (@clin1, '2013-01-15', 'PENICILLIN', 1, 'Rash', '0', '2013-01-15', '2',
   0, '2013-01-15 09:00:00', '999998'),
  (@clin3, '2013-02-16', 'CODEINE', 1, 'Nausée et vomissements', '0',
   '2013-02-16', '2', 0, '2013-02-16 09:00:00', '999998');

INSERT INTO `demographiccust` (`demographic_no`, `content`)
VALUES
  (@clin1, 'Prefers morning appointments.'),
  (@clin3, 'Préfère les rendez-vous en matinée; interprète non requis.'),
  (@clin4, 'Suivi partagÃ© avec la clinique voisine.');
