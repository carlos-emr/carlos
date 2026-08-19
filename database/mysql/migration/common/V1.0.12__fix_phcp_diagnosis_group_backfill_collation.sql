-- Re-run the V1.0.7 dxphcpgroup backfill with a collation-safe comparison.
--
-- V1.0.7's legacy-expansion join compares the utf8mb4_general_ci dxcode column
-- against a bare CAST(... AS CHAR). That cast takes the SESSION's default
-- utf8mb4 collation, so on servers whose session collation for utf8mb4 is not
-- in the general_ci family — for example MariaDB 11.4+ images that ship
-- character_set_collations = utf8mb4=uca1400_ai_ci, reached through a utf8mb4
-- client session — the comparison fails with ERROR 1267 (illegal mix of
-- collations) and V1.0.7 aborts after its DDL but before either backfill
-- INSERT. JDBC/Flyway sessions negotiate a compatible collation, which is why
-- managed migrations never hit this; migrations applied through the
-- mysql/mariadb CLI (the carlos-podman deployment path) do.
--
-- V1.0.7 itself is left byte-identical to preserve its recorded Flyway
-- checksum. This forward-only migration repeats both backfill INSERTs with the
-- cast pinned to CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci (matching
-- the table), so it works under any session collation. Both INSERTs keep
-- their existence guards, so this is a no-op on databases where V1.0.7
-- completed and fills in the missing rows on databases where it aborted.

-- Expand adopted legacy integer-keyed mappings to every exact diagnostic-code
-- spelling that normalizes to the same integer (see V1.0.7 for the rationale).
INSERT INTO dxphcpgroup (dxcode, level1, level2, lastUpdateUser, lastUpdateDate)
SELECT codes.dxcode,
       legacy.level1,
       legacy.level2,
       legacy.lastUpdateUser,
       legacy.lastUpdateDate
FROM (
  SELECT diagnostic_code AS dxcode,
         CAST(diagnostic_code AS UNSIGNED) AS numeric_code
  FROM diagnosticcode
  WHERE diagnostic_code REGEXP '^[0-9]{1,5}$'
  GROUP BY diagnostic_code
) codes
JOIN dxphcpgroup legacy
  ON legacy.dxcode REGEXP '^[0-9]{1,5}$'
  -- Pin the cast's charset AND collation: a bare CAST(... AS CHAR) inherits
  -- the session collation and can clash with the table's utf8mb4_general_ci.
  AND legacy.dxcode = CAST(CAST(legacy.dxcode AS UNSIGNED) AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_general_ci
  AND CAST(legacy.dxcode AS UNSIGNED) = codes.numeric_code
LEFT JOIN dxphcpgroup exact_mapping
  ON exact_mapping.dxcode = codes.dxcode
WHERE exact_mapping.dxcode IS NULL;

-- Seed the remaining numeric billing diagnoses with the ICD-9 chapter
-- taxonomy (identical to V1.0.7's second INSERT; guarded, so a completed
-- V1.0.7 makes this a no-op).
INSERT INTO dxphcpgroup (dxcode, level1, level2, lastUpdateUser, lastUpdateDate)
SELECT codes.dxcode,
       CASE
         WHEN codes.category_code <= 139 THEN '01 Infectious and parasitic diseases'
         WHEN codes.category_code <= 239 THEN '02 Neoplasms'
         WHEN codes.category_code <= 279 THEN '03 Endocrine, nutritional, metabolic, and immunity disorders'
         WHEN codes.category_code <= 289 THEN '04 Diseases of blood and blood-forming organs'
         WHEN codes.category_code <= 319 THEN '05 Mental disorders'
         WHEN codes.category_code <= 389 THEN '06 Diseases of the nervous system and sense organs'
         WHEN codes.category_code <= 459 THEN '07 Diseases of the circulatory system'
         WHEN codes.category_code <= 519 THEN '08 Diseases of the respiratory system'
         WHEN codes.category_code <= 579 THEN '09 Diseases of the digestive system'
         WHEN codes.category_code <= 629 THEN '10 Diseases of the genitourinary system'
         WHEN codes.category_code <= 679 THEN '11 Complications of pregnancy, childbirth, and the puerperium'
         WHEN codes.category_code <= 709 THEN '12 Diseases of the skin and subcutaneous tissue'
         WHEN codes.category_code <= 739 THEN '13 Diseases of the musculoskeletal system and connective tissue'
         WHEN codes.category_code <= 759 THEN '14 Congenital anomalies'
         WHEN codes.category_code <= 779 THEN '15 Conditions originating in the perinatal period'
         WHEN codes.category_code <= 799 THEN '16 Symptoms, signs, and ill-defined conditions'
         ELSE '17 Injury and poisoning'
       END,
       CASE
         WHEN codes.category_code <= 139 THEN 'ICD-9 001-139'
         WHEN codes.category_code <= 239 THEN 'ICD-9 140-239'
         WHEN codes.category_code <= 279 THEN 'ICD-9 240-279'
         WHEN codes.category_code <= 289 THEN 'ICD-9 280-289'
         WHEN codes.category_code <= 319 THEN 'ICD-9 290-319'
         WHEN codes.category_code <= 389 THEN 'ICD-9 320-389'
         WHEN codes.category_code <= 459 THEN 'ICD-9 390-459'
         WHEN codes.category_code <= 519 THEN 'ICD-9 460-519'
         WHEN codes.category_code <= 579 THEN 'ICD-9 520-579'
         WHEN codes.category_code <= 629 THEN 'ICD-9 580-629'
         WHEN codes.category_code <= 679 THEN 'ICD-9 630-679'
         WHEN codes.category_code <= 709 THEN 'ICD-9 680-709'
         WHEN codes.category_code <= 739 THEN 'ICD-9 710-739'
         WHEN codes.category_code <= 759 THEN 'ICD-9 740-759'
         WHEN codes.category_code <= 779 THEN 'ICD-9 760-779'
         WHEN codes.category_code <= 799 THEN 'ICD-9 780-799'
         ELSE 'ICD-9 800-999'
       END,
       'migration',
       CURRENT_TIMESTAMP
FROM (
  SELECT diagnostic_code AS dxcode,
         CAST(LEFT(diagnostic_code, 3) AS UNSIGNED) AS category_code
  FROM diagnosticcode
  WHERE diagnostic_code REGEXP '^[0-9]{1,5}$'
  GROUP BY diagnostic_code
) codes
WHERE NOT EXISTS (
  SELECT 1
  FROM dxphcpgroup existing
  WHERE existing.dxcode = codes.dxcode
);
