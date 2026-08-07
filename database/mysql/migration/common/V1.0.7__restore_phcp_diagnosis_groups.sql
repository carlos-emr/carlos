-- Restore the diagnosis grouping lookup required by the legacy PHCP encounter report.
-- The original report shipped without this table or a distributable PHCP-specific data set.
-- Seed the numeric billing diagnoses with the standard ICD-9 chapter taxonomy so every
-- diagnosis the report can parse has a stable, non-misleading category.

CREATE TABLE IF NOT EXISTS dxphcpgroup (
  dxcode int NOT NULL,
  level1 varchar(100) NOT NULL,
  level2 varchar(100) NOT NULL,
  lastUpdateUser varchar(100) NOT NULL DEFAULT 'migration',
  lastUpdateDate timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (dxcode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Preserve clinic-specific legacy groupings while bringing adopted tables up to the
-- audit-column convention required for new CARLOS tables.
ALTER TABLE dxphcpgroup
  ADD COLUMN IF NOT EXISTS lastUpdateUser varchar(100) NOT NULL DEFAULT 'migration',
  ADD COLUMN IF NOT EXISTS lastUpdateDate timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE dxphcpgroup
  ENGINE=InnoDB,
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

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
  SELECT CAST(diagnostic_code AS UNSIGNED) AS dxcode,
         COALESCE(
           MAX(CASE
             WHEN diagnostic_code = CAST(CAST(diagnostic_code AS UNSIGNED) AS CHAR)
               THEN CAST(LEFT(diagnostic_code, 3) AS UNSIGNED)
           END),
           MIN(CAST(LEFT(diagnostic_code, 3) AS UNSIGNED))
         ) AS category_code
  FROM diagnosticcode
  WHERE diagnostic_code REGEXP '^[0-9]{1,5}$'
  GROUP BY CAST(diagnostic_code AS UNSIGNED)
) codes
WHERE NOT EXISTS (
  SELECT 1
  FROM dxphcpgroup existing
  WHERE existing.dxcode = codes.dxcode
);
