-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- Native history copies live values verbatim. Retain consultation text and
-- demographic/provider update timestamps without truncating archived values.
SET @consultation_archive_previous_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_AUTO_VALUE_ON_ZERO';

ALTER TABLE `consultationRequestsArchive`
    MODIFY COLUMN `allergies` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
    MODIFY COLUMN `clinicalInfo` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
    MODIFY COLUMN `concurrentProblems` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
    MODIFY COLUMN `currentMeds` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
    MODIFY COLUMN `letterheadAddress` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
    MODIFY COLUMN `reason` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
    MODIFY COLUMN `statusText` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL;

ALTER TABLE `consultationRequestExtArchive`
    MODIFY COLUMN `value` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL;

ALTER TABLE `demographicArchive`
    MODIFY COLUMN `lastUpdateDate` DATETIME DEFAULT NULL,
    MODIFY COLUMN `date_of_birth` VARCHAR(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
    MODIFY COLUMN `month_of_birth` VARCHAR(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL;

ALTER TABLE `providerArchive`
    MODIFY COLUMN `lastUpdateDate` DATETIME DEFAULT NULL;

SET SESSION sql_mode = @consultation_archive_previous_sql_mode;
