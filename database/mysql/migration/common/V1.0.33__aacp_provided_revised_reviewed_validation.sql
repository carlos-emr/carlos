-- Asthma Action Plan (AACP): Provided/Revised/Reviewed instead of Yes/No/NA (issue #3893).
--
-- OntarioMD asthma conformance (DE16.098) records whether an action plan was provided, revised
-- or reviewed. The genesis seed (on/V1.0.2 and bc/V1.0.2) points AACP at validation 7
-- (Yes/No/NA) with measuring instruction 'Yes/No'. omdAsthmaFlowsheet.xml already declares the
-- Provided/Revised/Reviewed rule for AACP, but the startup flowsheet import only creates missing
-- measurement types, so the existing AACP row never picked it up.
--
-- Location: common/. measurementType and validations are seeded in BOTH province data files.
--
-- Existing AACP readings keep their stored "Yes"/"No" values. AddMeasurementData.jsp shows such a
-- legacy value as a selected, disabled option (MeasurementDropdownOptions.isLegacyValue).
--
-- Idempotent:
--   * the validation row is inserted only when no row with this exact name and pattern exists
--     (validations has no unique key besides the auto-increment id);
--   * the AACP update resolves that row by name + pattern with ORDER BY id LIMIT 1, so it is
--     deterministic even if an administrator created a same-named duplicate;
--   * the measuring instruction is rewritten only while it is still the seeded 'Yes/No', so a
--     site that customized the instruction keeps its text.
--
-- Adapted from MagentaHealth/Open-O 19b7a9798c, 72d980c0cd, 4e72835ce7, 602e6b4712, b2885289d9
-- and 6844968ed1.

INSERT INTO `validations` (`name`, `regularExp`)
SELECT 'Provided/Revised/Reviewed', 'Provided|Revised|Reviewed'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `validations`
    WHERE `name` = 'Provided/Revised/Reviewed'
      AND `regularExp` = 'Provided|Revised|Reviewed'
);

UPDATE `measurementType`
SET `validation` = (
    SELECT `id` FROM `validations`
    WHERE `name` = 'Provided/Revised/Reviewed'
      AND `regularExp` = 'Provided|Revised|Reviewed'
    ORDER BY `id`
    LIMIT 1
)
WHERE `type` = 'AACP';

UPDATE `measurementType`
SET `measuringInstruction` = 'Provided/Revised/Reviewed'
WHERE `type` = 'AACP'
  AND `measuringInstruction` = 'Yes/No';
