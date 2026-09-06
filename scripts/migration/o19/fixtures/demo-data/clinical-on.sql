-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 CARLOS Contributors
--
-- ONTARIO billing rows for the rehearsal fixture. Synthetic; see
-- fixtures/PROVENANCE.md.
--
-- Split out of clinical.sql when the fixture became province-aware:
-- billing_on_cheader1 and billing_on_item exist only in an Ontario
-- OSCAR 19 database, so loading them into a BC fixture fails at the
-- first statement. The BC twin is clinical-bc.sql.
--
-- These are what P7's billing-totals aggregate reads on an Ontario
-- host (o19map_schema.BILLING_TOTALS_TABLE['on']), so a regression in
-- the money check shows up in the rehearsal rather than at a clinic.
--
-- The demographics are the ones clinical.sql created; re-derived here
-- because each file is loaded as its own client session and the @vars
-- do not survive.

SET @clin1 = (SELECT demographic_no FROM demographic
              WHERE last_name = 'PATIENT' LIMIT 1);
SET @clin3 = (SELECT demographic_no FROM demographic
              WHERE chart_no = '101' LIMIT 1);

-- ---------------------------------------------------------------------
-- Ontario billing: the P7 verification aggregates these by fiscal year
-- ---------------------------------------------------------------------
INSERT INTO `billing_on_cheader1`
  (`header_id`, `hin`, `ver`, `dob`, `demographic_no`, `provider_no`,
   `demographic_name`, `sex`, `province`, `billing_date`, `billing_time`,
   `total`, `paid`, `status`, `visittype`, `creator`, `clinic`)
VALUES
  (1, '2222222222', 'AZ', '19670701', @clin1, '999998', 'PATIENT, TEST',
   '2', 'ON', '2014-03-04', '09:20:00', 33.70, 0.00, 'O', '00',
   '999998', 'Main Clinic'),
  (2, '1111111119', 'AB', '19740317', @clin3, '999998',
   'CÔTÉ, GENEVIÈVE', '2', 'ON', '2014-05-06', '11:05:00', 77.20, 77.20,
   'S', '00', '999998', 'Main Clinic');

INSERT INTO `billing_on_item`
  (`ch1_id`, `service_code`, `fee`, `ser_num`, `service_date`, `dx`,
   `status`)
SELECT id, 'A007A', '33.70', '1', billing_date, '250', 'S'
  FROM billing_on_cheader1 WHERE header_id = 1;
INSERT INTO `billing_on_item`
  (`ch1_id`, `service_code`, `fee`, `ser_num`, `service_date`, `dx`,
   `status`)
SELECT id, 'A003A', '77.20', '1', billing_date, '401', 'S'
  FROM billing_on_cheader1 WHERE header_id = 2;
