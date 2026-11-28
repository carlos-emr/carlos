-- SPDX-License-Identifier: GPL-2.0-or-later
-- A clinic index can occupy the name used by 1.0.59.50.1 without serving the
-- provider/date lookup. Keep any such index, including its uniqueness rules.
DROP PROCEDURE IF EXISTS carlos_ensure_historical_roster_lookup;
DELIMITER $$
CREATE PROCEDURE carlos_ensure_historical_roster_lookup()
SQL SECURITY INVOKER
ensure_lookup: BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.statistics AS provider_part
          JOIN information_schema.statistics AS date_part
            USING (table_schema, table_name, index_name)
         WHERE provider_part.table_schema = DATABASE()
           AND provider_part.table_name = 'demographicArchive'
           AND provider_part.index_type = 'BTREE' AND provider_part.ignored = 'NO'
           AND provider_part.seq_in_index = 1 AND provider_part.column_name = 'provider_no'
           AND provider_part.sub_part IS NULL
           AND date_part.seq_in_index = 2 AND date_part.column_name = 'roster_date'
           AND date_part.sub_part IS NULL
    ) THEN
        LEAVE ensure_lookup;
    END IF;

    BEGIN
        -- Index-count and maximum-key-length limits can apply to customized
        -- tables. The report remains functional there, and Flyway reports why
        -- the optional performance index could not be created.
        DECLARE EXIT HANDLER FOR 1069, 1071, 1709
            SIGNAL SQLSTATE '01000' SET MESSAGE_TEXT = 'Roster lookup index exceeds local index limits; review demographicArchive indexes';

        IF EXISTS (SELECT 1 FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name = 'demographicArchive'
                      AND index_name = 'idx_demographicArchive_provider_roster_date') THEN
            -- MariaDB chooses an unused name, including when provider_no is taken.
            ALTER TABLE demographicArchive ADD INDEX (provider_no, roster_date);
        ELSE
            ALTER TABLE demographicArchive ADD INDEX idx_demographicArchive_provider_roster_date (provider_no, roster_date);
        END IF;
    END;
END$$
DELIMITER ;
CALL carlos_ensure_historical_roster_lookup();
DROP PROCEDURE carlos_ensure_historical_roster_lookup;
