-- Backs the Ocean Toolbar's own getSettings/saveSettings REST contract
-- (GET/POST /ws/rs/ocean/{getSettings,saveSettings}). CARLOS stores only the
-- opaque settings blob the toolbar itself manages (site number, encrypted
-- secret key, etc.); it never inspects or generates the blob's contents.
-- Single-row table by design, matching the toolbar's one-settings-per-instance model.
CREATE TABLE OceanSetting (
  id INT NOT NULL AUTO_INCREMENT,
  settings TEXT NULL,
  updateDate DATETIME NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
