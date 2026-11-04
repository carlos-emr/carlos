-- Preserve the largest supported OSCAR 19 text declarations (#5503).
-- Source: oscaremr/oscar stable b425be066fa17f7dff0a4e0ee58e41bf89b83d81.
-- VARCHAR is measured in characters; TEXT tiers also allow full latin1-to-utf8mb4 expansion.
-- Existing values, defaults, nullability and column attributes are retained.
-- Strict conversion refuses incompatible local drift instead of truncating it.
SET @o19_previous_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_AUTO_VALUE_ON_ZERO';

ALTER TABLE `billingnote`
    MODIFY COLUMN `note` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL;

ALTER TABLE `formBCAR`
    MODIFY COLUMN `ar2_investigation` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
    MODIFY COLUMN `pg1_summary` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL;

ALTER TABLE `formBCAR2007`
    MODIFY COLUMN `pg1_summary` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
    MODIFY COLUMN `pg2_investigation` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
    MODIFY COLUMN `pg3_investigation` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL;

ALTER TABLE `formBCAR2012`
    MODIFY COLUMN `pg1_summary` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
    MODIFY COLUMN `pg2_investigation` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
    MODIFY COLUMN `pg3_investigation` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL;

ALTER TABLE `formBCBirthSumMo`
    MODIFY COLUMN `comLabBirth` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL;

ALTER TABLE `formBCBirthSumMo2008`
    MODIFY COLUMN `comLabBirth` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

ALTER TABLE `formRourke2009`
    MODIFY COLUMN `c_birthRemarks` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `c_famHistory` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `c_riskFactors` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p1_development1m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p1_development1w` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p1_development2w` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p1_immunization1m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p1_immunization1w` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p1_immunization2w` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p1_pConcern1m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p1_pConcern1w` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p1_pConcern2w` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p1_pEducation` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p1_pNutrition1m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p1_pNutrition1w` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p1_pNutrition2w` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p1_pPhysical1m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p1_pPhysical1w` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p1_pPhysical2w` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p1_problems1m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p1_problems1w` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p1_problems2w` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p2_development2m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p2_development4m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p2_development6m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p2_education` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p2_immunization6m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p2_nutrition2m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p2_nutrition4m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p2_nutrition6m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p2_pConcern2m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p2_pConcern4m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p2_pConcern6m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p2_physical2m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p2_physical4m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p2_physical6m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p2_problems2m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p2_problems4m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p2_problems6m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p3_development12m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p3_development15m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p3_development9m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p3_education` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p3_nutrition12m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p3_nutrition15m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p3_nutrition9m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p3_pConcern12m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p3_pConcern15m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p3_pConcern9m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p3_physical12m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p3_physical15m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p3_physical9m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p3_problems12m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p3_problems15m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p3_problems9m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p4_development18m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p4_development24m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p4_development36m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p4_development48m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p4_development60m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p4_education18m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p4_education48m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p4_nippisingattained` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p4_nutrition18m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p4_nutrition24m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p4_nutrition48m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p4_pConcern18m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p4_pConcern24m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p4_pConcern48m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p4_physical18m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p4_physical24m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p4_physical48m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p4_problems18m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p4_problems24m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `p4_problems48m` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

ALTER TABLE `formWCB`
    MODIFY COLUMN `w_clinicinfo` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `w_problem` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

ALTER TABLE `hl7_message`
    MODIFY COLUMN `notes` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

ALTER TABLE `hl7_obr`
    MODIFY COLUMN `note` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `parent_result` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `reason_for_study` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `relevant_clinical_info` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `specimen_source` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

ALTER TABLE `hl7_obx`
    MODIFY COLUMN `note` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
    MODIFY COLUMN `observation_identifier` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    MODIFY COLUMN `observation_results` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL;

ALTER TABLE `hl7_pid`
    MODIFY COLUMN `note` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

ALTER TABLE `wcb`
    MODIFY COLUMN `w_clinicinfo` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

SET SESSION sql_mode = @o19_previous_sql_mode;
