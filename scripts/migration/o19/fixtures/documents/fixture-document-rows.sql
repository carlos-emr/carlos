-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 CARLOS Contributors
--
-- Fixture `document` / ctl_document / eform rows matching the files that
-- fixtures/documents/make-documents-tar.sh generates (see manifest.json).
-- Loaded into the O19 fixture database AFTER demo.sql by
-- build-o19-fixture.sh. The demo_missing_scan.pdf row deliberately has NO
-- file in the tar so the P5 reconciliation gate has a real failure to catch.

INSERT INTO `document`
  (`doctype`, `docdesc`, `docxml`, `docfilename`, `doccreator`,
   `responsible`, `source`, `program_id`, `updatedatetime`, `status`,
   `contenttype`, `public1`, `observationdate`, `reviewer`, `reviewdatetime`)
VALUES
  ('consult', 'Demo referral note', '', 'demo_referral_note.pdf', '999998',
   '999998', '', 10016, '2019-06-01 10:00:00', 'A',
   'application/pdf', 0, '2019-06-01', NULL, NULL),
  ('lab', 'Demo lab scan', '', 'demo_lab_scan.pdf', '999998',
   '999998', '', 10016, '2019-07-15 09:30:00', 'A',
   'application/pdf', 0, '2019-07-15', NULL, NULL),
  ('consult', 'Demo missing scan', '', 'demo_missing_scan.pdf', '999998',
   '999998', '', 10016, '2019-08-20 14:00:00', 'A',
   'application/pdf', 0, '2019-08-20', NULL, NULL);

-- Link the three documents to the two demo.sql patients (demographic_no 1
-- and 2 are the first rows demo.sql inserts into an empty database).
INSERT INTO `ctl_document` (`module`, `module_id`, `document_no`, `status`)
SELECT 'demographic', 1, document_no, 'A' FROM `document`
  WHERE docfilename = 'demo_referral_note.pdf';
INSERT INTO `ctl_document` (`module`, `module_id`, `document_no`, `status`)
SELECT 'demographic', 2, document_no, 'A' FROM `document`
  WHERE docfilename = 'demo_lab_scan.pdf';
INSERT INTO `ctl_document` (`module`, `module_id`, `document_no`, `status`)
SELECT 'demographic', 1, document_no, 'A' FROM `document`
  WHERE docfilename = 'demo_missing_scan.pdf';

-- eForm whose HTML references an on-disk image asset via oscar_image_path.
INSERT INTO `eform`
  (`form_name`, `file_name`, `subject`, `form_date`, `form_time`,
   `form_creator`, `status`, `form_html`, `showLatestFormOnly`,
   `patient_independent`, `roleType`)
VALUES
  ('Demo Letterhead', 'demo_letterhead.html', 'Demo letterhead form',
   '2019-05-01', '08:00:00', 'oscardoc', 1,
   '<html><body><img src="${oscar_image_path}demo_clinic_logo.png"/><p>Demo clinic letterhead</p></body></html>',
   0, 0, NULL);
