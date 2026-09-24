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
-- validation '7' is the seeded Yes/No/NA rule (identical id in both province seeds), the same one
-- FTLS uses, so the two neurological findings render the same dropdown.
--
-- Idempotent. measurementType has no unique key on `type` (only an auto-increment id), so an
-- ON DUPLICATE KEY UPDATE insert would never match and would add a duplicate row on every rerun;
-- the insert is guarded with NOT EXISTS instead. The follow-up UPDATE normalizes every field when
-- a row already exists, e.g. one created at startup by the flowsheet measurement import
-- (ImportMeasurementTypes) before this migration ran, which would otherwise carry the XML
-- validation rule and a different validation id.
--
-- Adapted from MagentaHealth/Open-O 18b5d53095, 19f9d2d02d and 3eeacfa4b6.

INSERT INTO `measurementType`
    (`type`, `typeDisplayName`, `typeDescription`, `measuringInstruction`, `validation`, `createDate`)
SELECT 'NRTF',
       'Neurological exam: 128Hz tuning fork D1',
       'Neurological exam: 128Hz tuning fork D1',
       'Normal',
       '7',
       '2026-03-23 00:00:00'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `measurementType` WHERE `type` = 'NRTF');

UPDATE `measurementType`
SET `typeDisplayName`      = 'Neurological exam: 128Hz tuning fork D1',
    `typeDescription`      = 'Neurological exam: 128Hz tuning fork D1',
    `measuringInstruction` = 'Normal',
    `validation`           = '7'
WHERE `type` = 'NRTF';
