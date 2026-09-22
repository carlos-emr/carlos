-- Ocean's clinic-wide opaque settings. The fixed primary key and CHECK enforce
-- one row even for concurrent first saves. Application writes use an atomic upsert.
CREATE TABLE OceanSetting (
  id INT NOT NULL,
  settings MEDIUMTEXT NULL,
  lastUpdateUser VARCHAR(100) NOT NULL,
  lastUpdateDate DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT chk_ocean_setting_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
