-- MariaDB 11.8 maintenance, not a Flyway migration. Run in a dedicated session.
-- Deletes at most 500 SOAP rows, at least 24 hours after their authoritative epoch expiry.
-- Other token purposes and audit history are preserved. Output is only the removed row count.
DELIMITER //
BEGIN NOT ATOMIC
    IF @@in_transaction THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SOAP token cleanup requires a dedicated session without an active transaction';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA=DATABASE() AND BINARY TABLE_NAME='SecurityToken' AND ENGINE='InnoDB') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SOAP token cleanup requires an InnoDB table in the selected database';
    END IF;
    BEGIN
        DECLARE cutoff_ms DECIMAL(20,0);
        DECLARE removed_count INT DEFAULT 0;
        DECLARE EXIT HANDLER FOR SQLEXCEPTION
        BEGIN
            ROLLBACK;
            RESIGNAL;
        END;
        START TRANSACTION;
        SET cutoff_ms = UNIX_TIMESTAMP() * 1000 - 86400000;
        IF EXISTS (
            SELECT 1 FROM SecurityToken
            WHERE token LIKE 'soap1:%'
              AND (NOT (BINARY token REGEXP '^soap1:[0-9a-f]{64}\\z') OR data IS NULL
                OR NOT (BINARY data REGEXP '^soap1:[1-9][0-9]{0,9}:(0|[1-9][0-9]{0,18}):[1-9][0-9]{0,18}:[0-9a-f]{64}\\z'))
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid SOAP token storage; cleanup rolled back';
        END IF;
        IF EXISTS (
            SELECT 1 FROM SecurityToken
            WHERE token LIKE 'soap1:%'
              AND (CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(data, ':', 2), ':', -1) AS DECIMAL(20,0)) > 2147483647
                OR CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(data, ':', 4), ':', -1) AS DECIMAL(20,0)) > 9223372036854775807
                OR CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(data, ':', 4), ':', -1) AS DECIMAL(20,0))
                    <= CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(data, ':', 3), ':', -1) AS DECIMAL(20,0))
                OR CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(data, ':', 4), ':', -1) AS DECIMAL(20,0))
                    - CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(data, ':', 3), ':', -1) AS DECIMAL(20,0)) > 86400000)
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid SOAP token expiry; cleanup rolled back';
        END IF;
        DELETE FROM SecurityToken
        WHERE token LIKE 'soap1:%'
          AND CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(data, ':', 4), ':', -1) AS DECIMAL(20,0)) <= cutoff_ms
        ORDER BY id LIMIT 500;
        SET removed_count = ROW_COUNT();
        COMMIT;
        SELECT removed_count;
    END;
END//
DELIMITER ;
