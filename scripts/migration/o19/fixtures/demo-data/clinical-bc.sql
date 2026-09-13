-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 CARLOS Contributors
--
-- BRITISH COLUMBIA clinical rows for the rehearsal fixture. Synthetic;
-- see fixtures/PROVENANCE.md.
--
-- The BC twin of clinical-on.sql. Until the BC manifest pass, the
-- fixture built an Ontario OSCAR 19 database and nothing else, so every
-- BC ruling in the manifest was reasoned from the schema and never
-- moved a row. These are the surfaces a BC clinic would notice first:
--
--   billing / billingmaster  the MSP claim and its service lines.
--                            `billing` is what P7 aggregates on a BC
--                            host (BILLING_TOTALS_TABLE['bc']), so
--                            these rows are the money check's input.
--   wcb                      a WorkSafeBC report: BC-only, patient
--                            data, and copy-class.
--   formBCAR                 the BC antenatal record. formBCAR2007 was
--                            ruled archive+patient_data against the
--                            Ontario schema (correct there, where
--                            CARLOS dropped the table) and is a live
--                            copy here -- exactly the ruling the BC
--                            pass corrected, so the fixture carries a
--                            row that would have been moved out of the
--                            application by the old one.
--   billingvisit             one clinic-added MSP visit type, which is
--                            what the merge ruling appends past the
--                            CARLOS seed.
--
-- Money is deliberately not round and not equal between the two claims,
-- so a totals aggregate that dropped a row, double-counted one, or
-- summed the wrong column cannot still match.
--
-- Loaded under the same latin1 client as the rest of the fixture; the
-- accented text here is the correctly-encoded case (the double-encoded
-- one lives in clinical.sql, which every province loads).

SET @clin1 = (SELECT demographic_no FROM demographic
              WHERE last_name = 'PATIENT' LIMIT 1);
SET @clin3 = (SELECT demographic_no FROM demographic
              WHERE chart_no = '101' LIMIT 1);

-- ---------------------------------------------------------------------
-- MSP claims: `billing` is the invoice P7 aggregates by fiscal year,
-- `billingmaster` holds the service lines behind it
-- ---------------------------------------------------------------------
INSERT INTO `billing`
  (`clinic_no`, `demographic_no`, `provider_no`, `demographic_name`,
   `hin`, `update_date`, `update_time`, `billing_date`, `billing_time`,
   `total`, `status`, `dob`, `visitdate`, `visittype`, `creator`,
   `billingtype`)
VALUES
  (1, @clin1, '999998', 'PATIENT, TEST', '9999999998', '2014-03-04',
   '09:20:00', '2014-03-04', '09:20:00', '31.62', 'S', '19670701',
   '2014-03-04', 'A', '999998', 'MSP'),
  (1, @clin3, '999998', 'CÔTÉ, GENEVIÈVE', '9999999997', '2014-05-06',
   '11:05:00', '2014-05-06', '11:05:00', '78.45', 'S', '19740317',
   '2014-05-06', 'A', '999998', 'MSP');

INSERT INTO `billingmaster`
  (`billing_no`, `createdate`, `billingstatus`, `demographic_no`,
   `practitioner_no`, `phn`, `billing_code`, `bill_amount`,
   `service_date`, `dx_code1`)
SELECT billing_no, '2014-03-04 09:20:00', 'S', demographic_no, '99998',
       hin, '00100', '000003162', '20140304', '780'
  FROM billing WHERE demographic_no = @clin1 AND billing_date =
       '2014-03-04';
INSERT INTO `billingmaster`
  (`billing_no`, `createdate`, `billingstatus`, `demographic_no`,
   `practitioner_no`, `phn`, `billing_code`, `bill_amount`,
   `service_date`, `dx_code1`)
SELECT billing_no, '2014-05-06 11:05:00', 'S', demographic_no, '99998',
       hin, '00120', '000007845', '20140506', '401'
  FROM billing WHERE demographic_no = @clin3 AND billing_date =
       '2014-05-06';

-- ---------------------------------------------------------------------
-- WorkSafeBC report: BC-only, copy-class, patient data
-- ---------------------------------------------------------------------
INSERT INTO `wcb`
  (`billing_no`, `demographic_no`, `provider_no`, `formCreated`,
   `formEdited`, `w_reporttype`, `bill_amount`, `w_fname`, `w_lname`,
   `w_gender`, `w_dob`, `w_address`, `w_city`, `w_postal`)
VALUES
  (0, @clin1, 999998, '2014-06-02 10:00:00', '2014-06-02 10:30:00',
   'F', '58.20', 'TEST', 'PATIENT', 'M', '1967-07-01',
   '1 Main Street', 'Victoria', 'V8W1A1');

-- ---------------------------------------------------------------------
-- BC antenatal record: the table whose ruling the BC pass corrected
-- from archive+patient_data to copy
-- ---------------------------------------------------------------------
INSERT INTO `formBCAR`
  (`demographic_no`, `provider_no`, `formCreated`, `c_hospital`,
   `pg1_famPhy`, `pg1_moName`, `pg1_dateOfBirth`)
VALUES
  (@clin3, 999998, '2014-02-10', 'Victoria General',
   'Dr. Test Provider', 'CÔTÉ, GENEVIÈVE', '1974-03-17');

-- ---------------------------------------------------------------------
-- a clinic-added MSP visit type: the row the billingvisit merge has to
-- append past the CARLOS seed's 21
-- ---------------------------------------------------------------------
INSERT INTO `billingvisit` (`visittype`, `visit_desc`, `region`)
VALUES ('Y', 'Clinic-defined outreach visit', 'BC');

-- ---------------------------------------------------------------------
-- One Rourke 2009 record whose BC-only columns hold real answers.
--
-- formRourke2009 is the table whose 288 BC columns cannot have live
-- `import_archived_` twins (ARCHIVE_TWINS_EXEMPT): CARLOS already
-- defines 1227 columns on it and the server refuses the definition. So
-- their values reach o19_archive.formRourke2009__dropped instead, and
-- WITHOUT a row that answers some of them the rehearsal would exercise
-- the ruling's plumbing while preserving nothing -- the capture is
-- non-default rows only, and an empty table has none.
--
-- The columns are tinyint(1) and the capture's predicate is
-- `IS NOT NULL AND <> 0`, so 1 is what makes a row non-default. Three of
-- them, one per page group, so the capture is proved on more than one
-- column and more than one part of the form.
-- ---------------------------------------------------------------------
INSERT INTO `formRourke2009`
  (`demographic_no`, `provider_no`, `formCreated`,
   `p1_stoolUrine1wNo`, `p2_smilesNo`, `p3_showsFearStrangersNo`)
VALUES
  (@clin1, '999998', '2014-07-14', 1, 1, 1);
