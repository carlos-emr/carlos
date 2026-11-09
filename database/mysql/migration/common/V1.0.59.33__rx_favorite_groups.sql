-- Provider-owned presentation metadata. Existing favourite templates remain unchanged and ungrouped.
CREATE TABLE IF NOT EXISTS rx_favorite_group (
    id INT NOT NULL AUTO_INCREMENT,
    provider_no VARCHAR(6) NOT NULL,
    name VARCHAR(80) NOT NULL,
    position INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY rx_favorite_group_owner_name (provider_no, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS rx_favorite_group_member (
    group_id INT NOT NULL,
    favorite_id INT NOT NULL,
    PRIMARY KEY (group_id, favorite_id),
    KEY rx_favorite_group_member_favorite (favorite_id),
    CONSTRAINT rx_favorite_group_member_group_fk FOREIGN KEY (group_id)
        REFERENCES rx_favorite_group(id) ON DELETE CASCADE,
    CONSTRAINT rx_favorite_group_member_favorite_fk FOREIGN KEY (favorite_id)
        REFERENCES favorites(favoriteid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
