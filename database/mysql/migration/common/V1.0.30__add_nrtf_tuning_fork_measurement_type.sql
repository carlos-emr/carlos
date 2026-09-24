-- Add the NRTF measurement type: "Neurological exam: 128Hz tuning fork D1" (issue #3892).
--
-- OntarioMD diabetes conformance (DE16.066) records the 10g monofilament exam and the 128Hz
-- tuning-fork vibration exam at the first toe (D1) as two separate findings. The diabetes
-- flowsheets (omdDiabetesFlowsheet.xml, diabetesQueensFlowsheet.xml, diabetesFlowsheet.xml) used
-- one FTLS item for "10g monofilament or 128 Hz tuning fork"; FTLS is now labelled as the
-- monofilament exam and the flowsheets gain a separate NRTF item.
--
-- Location: common/. measurementType is seeded in BOTH province data files
-- (on/V1.0.2 and bc/V1.0.2), and both provinces register the diabetes flowsheets.
--
-- NRTF takes the same validation rule as FTLS, so the two neurological findings render the same
-- dropdown. The rule id is resolved, not hard-coded: validations.id is auto-increment, and a
-- database converted from OpenO/oscar19 (baselined at 1.0.2) may number its rules differently
-- from the CARLOS seed. Resolution order: the FTLS row's rule, then the Yes/No/NA rule matched by
-- name and pattern. No id is ever assumed: when neither exists (an adopted database whose FTLS
-- row and Yes/No/NA rule were both removed), the Yes/No/NA rule is recreated first, the same
-- insert-if-missing pattern V1.0.31 uses for its Provided/Revised/Reviewed rule, so the lookup
-- cannot come back NULL and NRTF never binds to whatever rule happens to own id 7.
--
-- Idempotent. measurementType has no unique key on `type` (only an auto-increment id), so an
-- ON DUPLICATE KEY UPDATE insert would never match and would add a duplicate row on every rerun;
-- the insert is guarded with NOT EXISTS instead. The follow-up UPDATE normalizes every field when
-- a row already exists, e.g. one created at startup by the flowsheet measurement import
-- (ImportMeasurementTypes) before this migration ran, which would otherwise carry the XML
-- validation rule and a different validation id.
--
-- Adapted from MagentaHealth/Open-O 18b5d53095, 19f9d2d02d and 3eeacfa4b6.

INSERT INTO `validations` (`name`, `regularExp`)
SELECT 'Yes/No/NA', 'YES|yes|Yes|Y|NO|no|No|N|NotApplicable|NA'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `measurementType` WHERE `type` = 'FTLS' AND `validation` IS NOT NULL AND `validation` <> '')
  AND NOT EXISTS (
    SELECT 1 FROM `validations`
    WHERE `name` = 'Yes/No/NA'
      AND `regularExp` = 'YES|yes|Yes|Y|NO|no|No|N|NotApplicable|NA');

-- Resolved in separate steps so no expression mixes the table collation with the client's
-- connection collation (a manual MariaDB CLI session may default to utf8mb4_uca1400_ai_ci).
SET @carlos_nrtf_validation = (
    SELECT `validation` FROM `measurementType`
    WHERE `type` = 'FTLS' AND `validation` IS NOT NULL AND `validation` <> ''
    ORDER BY `id` LIMIT 1);
SET @carlos_nrtf_validation = IFNULL(@carlos_nrtf_validation, (
    SELECT `id` FROM `validations`
    WHERE `name` = 'Yes/No/NA'
      AND `regularExp` = 'YES|yes|Yes|Y|NO|no|No|N|NotApplicable|NA'
    ORDER BY `id` LIMIT 1));

INSERT INTO `measurementType`
    (`type`, `typeDisplayName`, `typeDescription`, `measuringInstruction`, `validation`, `createDate`)
SELECT 'NRTF',
       'Neurological exam: 128Hz tuning fork D1',
       'Neurological exam: 128Hz tuning fork D1',
       'Normal',
       @carlos_nrtf_validation,
       '2026-03-23 00:00:00'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `measurementType` WHERE `type` = 'NRTF');

UPDATE `measurementType`
SET `typeDisplayName`      = 'Neurological exam: 128Hz tuning fork D1',
    `typeDescription`      = 'Neurological exam: 128Hz tuning fork D1',
    `measuringInstruction` = 'Normal',
    `validation`           = @carlos_nrtf_validation
WHERE `type` = 'NRTF';

SET @carlos_nrtf_validation = NULL;
