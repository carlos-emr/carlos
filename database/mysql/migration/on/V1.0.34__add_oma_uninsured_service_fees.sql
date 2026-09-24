-- OMA uninsured service fees on the Ontario PRIVATE billing form (issue #3894).
--
-- OntarioMD conformance PC13.19 expects the private (uninsured) billing form to offer the OMA
-- uninsured-services fee schedule. The genesis Ontario seed (on/V1.0.2) maps only three
-- placeholder rows to the PRIVATE form: OHIP code A007A under ' Group 1 Name', ' Group 2 Name'
-- and ' Group 3 Name'.
--
-- This migration:
--   1. deactivates (status 'I') those three placeholder mappings, matched on their exact seeded
--      group names so any real PRIVATE mapping of A007A an administrator added stays active.
--      Nothing is deleted;
--   2. upserts 34 private fee codes in billingservice: existing rows for the same code and
--      effective date are updated, missing ones are inserted. Private codes use the CARLOS
--      underscore convention (`_OMA_...`, at most 10 characters for billingservice.service_code)
--      and no region, so they never reach an OHIP claim file;
--   3. maps each code to the PRIVATE form (servicetype 'PRI') in three groups:
--      Group1 = Forms, Group2 = Assessments, Group3 = Procedures.
--
-- Fee amounts and effective dates are the upstream values, kept as-is. The PRIVATE form lists a
-- code only for bill dates on or after its billingservice_date (BillingServiceDao picks the
-- latest fee row dated on or before the bill date).
--
-- Location: on/. billingservice and ctl_billingservice are shared tables, but the PRIVATE
-- (servicetype 'PRI') billing form and its seed rows exist only in the Ontario data.
--
-- Local-customisation policy:
--   * billingservice, rows keyed by (service_code, billingservice_date) for the 34 `_OMA_` codes
--     and the effective dates below: this migration owns the fee-schedule fields and
--     overwrites them on every run: description, value, percentage ('0.00'), region (NULL) and
--     termination_date ('9999-12-31'). A stale description or fee from an earlier load is
--     therefore corrected, not skipped. If duplicate rows share a code and date, all of them are
--     updated, so the result does not depend on which row the fee lookup picks.
--   * Clinic-configured flags on those rows are preserved: gstFlag, sliFlag, displaystyle,
--     specialty, anaesthesia and service_compositecode (for example, a clinic that charges HST on
--     insurance forms keeps its gstFlag). They are set only on insert.
--   * Rows for the same codes with any OTHER effective date are left alone. A clinic that
--     wants its own fee should add a later-dated row (billing uses the latest row dated on or
--     before the bill date) or use its own non-`_OMA_` private code. Both survive a rerun.
--   * Flyway applies this file once per database, so the overwrite affects only rows that existed
--     before it ran (e.g. an earlier manual load of the upstream script). Fee edits an
--     administrator makes later through Service Code admin are never touched.
--   * ctl_billingservice: a form mapping is inserted only when the code is not already mapped to
--     the PRIVATE form in any group or status, so an administrator's regrouping, reordering or
--     deactivation is preserved.
--
-- Idempotent. Neither table has a unique key besides its auto-increment id, so the upsert is an
-- UPDATE of matching rows followed by an existence-guarded INSERT of missing ones, and the form
-- mapping insert is existence-guarded. A rerun rewrites the same values and inserts nothing.
--
-- The staging tables pin utf8mb4_general_ci (the collation of the real tables) so the NOT EXISTS
-- comparisons cannot fail with "Illegal mix of collations" when a manual mariadb CLI session
-- defaults to utf8mb4_uca1400_ai_ci (see the V1.0.7 note in ../README.md).
--
-- Adapted from MagentaHealth/Open-O 9091c94c60, c9f6f63b47 and eb94eabbd4.

UPDATE `ctl_billingservice`
SET `status` = 'I'
WHERE `servicetype` = 'PRI'
  AND `service_code` = 'A007A'
  AND `service_group_name` IN (' Group 1 Name', ' Group 2 Name', ' Group 3 Name');

CREATE TEMPORARY TABLE `carlos_oma_uninsured_fee` (
    `service_code` VARCHAR(10) NOT NULL PRIMARY KEY,
    `description` TEXT NOT NULL,
    `value` VARCHAR(8) NOT NULL,
    `billingservice_date` DATE NOT NULL
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `carlos_oma_uninsured_fee` (`service_code`, `description`, `value`, `billingservice_date`) VALUES
    ('_OMA_A003', 'General Assessment', '253.35', '2026-01-01'),
    ('_OMA_A007', 'Intermediate Assessment', '110.10', '2026-01-01'),
    ('_OMA_A110', 'Periodic oculo-visual assessment (by a General/Family practitioner)', '141.85', '2026-01-01'),
    ('_OMA_C01', 'Patient interview for practice admission', '128.00', '2026-01-01'),
    ('_OMA_F01', 'Form completion for physicals for schools, camps, pre-school, daycare, university/educational institutions', '37.25', '2026-01-01'),
    ('_OMA_F02', 'Form completion for physicals for pre-employment certification of fitness/fitness clubs or hospital/nursing home employee', '49.00', '2026-01-01'),
    ('_OMA_F03', 'Children''s Aid Society (CAS) application for prospective foster parent', '252.00', '2026-01-01'),
    ('_OMA_F04', 'CRA Disability Tax Credit Certificate (form T2201)', '150.00', '2026-01-01'),
    ('_OMA_F05', 'Insurance Certificate OCF- 3 Disability Certificate', '262.00', '2026-01-01'),
    ('_OMA_F06', 'Insurance Certificate OCF-18 Treatment Plan', '278.00', '2026-01-01'),
    ('_OMA_F07', 'Insurance Certificate OCF-23 Treatment Confirmation', '262.00', '2026-01-01'),
    ('_OMA_F08', 'Attending Physician''s Statement', '200.00', '2026-01-01'),
    ('_OMA_F09', 'Insurance Medical Examination (assessment and report)', '200.00', '2026-03-01'),
    ('_OMA_F10', 'Medical Report for a CPP Disability Benefit (SCISP-2519)', '85.00', '2026-03-01'),
    ('_OMA_F11', 'CPP Narrative Medical Report', '150.00', '2026-03-01'),
    ('_OMA_F12', 'Medical certificate employment insurance sickness benefits INS5140', '52.00', '2026-01-01'),
    ('_OMA_F13', 'Travel cancellation insurance form', '164.00', '2026-01-01'),
    ('_OMA_F14', 'Life insurance death certificate', '50.00', '2026-03-01'),
    ('_OMA_F15', 'Insurance Certificate OCF-19 Determination of Catastrophic Impairment', '155.00', '2026-01-01'),
    ('_OMA_F16', 'System-Specific or Disease Specific Questionnaire', '125.00', '2026-01-01'),
    ('_OMA_F17', 'System-Specific Examination', '152.00', '2026-01-01'),
    ('_OMA_F18', 'Assessments: Clarification Report', '300.00', '2026-01-01'),
    ('_OMA_F19', 'Assessments: Full Narrative Report', '350.00', '2026-01-01'),
    ('_OMA_F20', 'Assessments: Independent Medical Examination', '200.00', '2026-01-01'),
    ('_OMA_F21', 'Terminal Illness Medical Attestation for a Disability Benefit (ISP2530B)', '85.00', '2026-01-01'),
    ('_OMA_F22', 'Reassessment Medical Report (ISP2509)', '25.00', '2026-01-01'),
    ('_OMA_F23', 'Scannable Impairment Evaluation (IMPAIR)', '50.00', '2026-01-01'),
    ('_OMA_F24', 'Medical Report – Recurrence of the Same Medical Problem (ISP2525)', '25.00', '2026-01-01'),
    ('_OMA_F25', 'Drivers medical examination (form only)', '77.00', '2026-01-01'),
    ('_OMA_G010', 'Urinalysis – without microscopy', '7.65', '2026-03-01'),
    ('_OMA_N01', 'Sick notes (includes return to work/school notes)', '26.00', '2026-01-01'),
    ('_OMA_N02', 'Fitness to work notes', '50.00', '2026-01-01'),
    ('_OMA_RECOR', 'Electronic Transfer of Records', '30.00', '2026-03-01'),
    ('_OMA_RX', 'Dispensing service fee', '20.75', '2026-01-01');

UPDATE `billingservice` AS bs
INNER JOIN `carlos_oma_uninsured_fee` AS fee
    ON bs.`service_code` = fee.`service_code`
    AND bs.`billingservice_date` = fee.`billingservice_date`
SET bs.`description`      = fee.`description`,
    bs.`value`            = fee.`value`,
    bs.`percentage`       = '0.00',
    bs.`region`           = NULL,
    bs.`termination_date` = '9999-12-31';

INSERT INTO `billingservice`
    (`service_compositecode`, `service_code`, `description`, `value`, `percentage`, `billingservice_date`,
     `specialty`, `region`, `anaesthesia`, `termination_date`, `displaystyle`, `sliFlag`, `gstFlag`)
SELECT '', fee.`service_code`, fee.`description`, fee.`value`, '0.00', fee.`billingservice_date`,
       NULL, NULL, NULL, '9999-12-31', NULL, 0, 0
FROM `carlos_oma_uninsured_fee` AS fee
WHERE NOT EXISTS (
    SELECT 1 FROM `billingservice` AS bs
    WHERE bs.`service_code` = fee.`service_code`
      AND bs.`billingservice_date` = fee.`billingservice_date`
)
ORDER BY fee.`service_code`;

CREATE TEMPORARY TABLE `carlos_oma_private_form_map` (
    `service_code` VARCHAR(10) NOT NULL PRIMARY KEY,
    `service_group_name` VARCHAR(30) NOT NULL,
    `service_group` VARCHAR(30) NOT NULL,
    `service_order` INT NOT NULL
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `carlos_oma_private_form_map` (`service_code`, `service_group_name`, `service_group`, `service_order`) VALUES
    ('_OMA_A003', 'Assessments', 'Group2', 1),
    ('_OMA_A007', 'Assessments', 'Group2', 2),
    ('_OMA_A110', 'Assessments', 'Group2', 3),
    ('_OMA_F01', 'Forms', 'Group1', 2),
    ('_OMA_F02', 'Forms', 'Group1', 3),
    ('_OMA_F03', 'Forms', 'Group1', 4),
    ('_OMA_F04', 'Forms', 'Group1', 5),
    ('_OMA_F05', 'Forms', 'Group1', 6),
    ('_OMA_F06', 'Forms', 'Group1', 7),
    ('_OMA_F07', 'Forms', 'Group1', 8),
    ('_OMA_F08', 'Assessments', 'Group2', 5),
    ('_OMA_F09', 'Assessments', 'Group2', 6),
    ('_OMA_F10', 'Assessments', 'Group2', 7),
    ('_OMA_F11', 'Assessments', 'Group2', 8),
    ('_OMA_F12', 'Assessments', 'Group2', 4),
    ('_OMA_F13', 'Forms', 'Group1', 11),
    ('_OMA_F14', 'Forms', 'Group1', 12),
    ('_OMA_F15', 'Forms', 'Group1', 13),
    ('_OMA_F16', 'Assessments', 'Group2', 10),
    ('_OMA_F17', 'Assessments', 'Group2', 11),
    ('_OMA_F18', 'Assessments', 'Group2', 12),
    ('_OMA_F19', 'Assessments', 'Group2', 13),
    ('_OMA_F20', 'Assessments', 'Group2', 14),
    ('_OMA_F21', 'Forms', 'Group1', 14),
    ('_OMA_F22', 'Assessments', 'Group2', 15),
    ('_OMA_F23', 'Assessments', 'Group2', 16),
    ('_OMA_F24', 'Assessments', 'Group2', 17),
    ('_OMA_F25', 'Assessments', 'Group2', 9),
    ('_OMA_G010', 'Procedures', 'Group3', 2),
    ('_OMA_N01', 'Forms', 'Group1', 9),
    ('_OMA_N02', 'Forms', 'Group1', 10),
    ('_OMA_RECOR', 'Forms', 'Group1', 1),
    ('_OMA_RX', 'Procedures', 'Group3', 1),
    ('_OMA_C01', 'Assessments', 'Group2', 18);

INSERT INTO `ctl_billingservice`
    (`servicetype_name`, `servicetype`, `service_code`, `service_group_name`, `service_group`, `status`, `service_order`)
SELECT 'PRIVATE', 'PRI', map.`service_code`, map.`service_group_name`, map.`service_group`, 'A', map.`service_order`
FROM `carlos_oma_private_form_map` AS map
WHERE NOT EXISTS (
    SELECT 1 FROM `ctl_billingservice` AS ctl
    WHERE ctl.`servicetype` = 'PRI'
      AND ctl.`service_code` = map.`service_code`
)
ORDER BY map.`service_group`, map.`service_order`;

DROP TEMPORARY TABLE `carlos_oma_private_form_map`;
DROP TEMPORARY TABLE `carlos_oma_uninsured_fee`;
