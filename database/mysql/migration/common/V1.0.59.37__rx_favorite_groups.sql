-- Atomic clinical writes require transactional engines, including adopted OSCAR schemas.
SET @rx_engine_sql = IFNULL((SELECT IF(ENGINE = 'InnoDB', 'DO 0', 'ALTER TABLE `drugs` ENGINE=InnoDB') FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='drugs'), 'DO 0');
PREPARE rx_engine_stmt FROM @rx_engine_sql;
EXECUTE rx_engine_stmt;
DEALLOCATE PREPARE rx_engine_stmt;
SET @rx_engine_sql = IFNULL((SELECT IF(ENGINE = 'InnoDB', 'DO 0', 'ALTER TABLE `prescription` ENGINE=InnoDB') FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='prescription'), 'DO 0');
PREPARE rx_engine_stmt FROM @rx_engine_sql;
EXECUTE rx_engine_stmt;
DEALLOCATE PREPARE rx_engine_stmt;
SET @rx_engine_sql = IFNULL((SELECT IF(ENGINE = 'InnoDB', 'DO 0', 'ALTER TABLE `log` ENGINE=InnoDB') FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='log'), 'DO 0');
PREPARE rx_engine_stmt FROM @rx_engine_sql;
EXECUTE rx_engine_stmt;
DEALLOCATE PREPARE rx_engine_stmt;
SET @rx_engine_sql = IFNULL((SELECT IF(ENGINE = 'InnoDB', 'DO 0', 'ALTER TABLE `favorites` ENGINE=InnoDB') FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='favorites'), 'DO 0');
PREPARE rx_engine_stmt FROM @rx_engine_sql;
EXECUTE rx_engine_stmt;
DEALLOCATE PREPARE rx_engine_stmt;

-- Provider-owned presentation metadata. Existing favourite templates remain unchanged and ungrouped.
CREATE TABLE IF NOT EXISTS rx_favorite_group (
    id INT NOT NULL AUTO_INCREMENT,
    provider_no VARCHAR(6) NOT NULL,
    name VARCHAR(80) NOT NULL,
    position INT NOT NULL,
    lastUpdateUser VARCHAR(100) NOT NULL DEFAULT 'system',
    lastUpdateDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY rx_favorite_group_owner_name (provider_no, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS rx_favorite_group_member (
    group_id INT NOT NULL,
    favorite_id INT NOT NULL,
    lastUpdateUser VARCHAR(100) NOT NULL DEFAULT 'system',
    lastUpdateDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, favorite_id),
    KEY rx_favorite_group_member_favorite (favorite_id),
    CONSTRAINT rx_favorite_group_member_group_fk FOREIGN KEY (group_id)
        REFERENCES rx_favorite_group(id) ON DELETE CASCADE,
    CONSTRAINT rx_favorite_group_member_favorite_fk FOREIGN KEY (favorite_id)
        REFERENCES favorites(favoriteid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
