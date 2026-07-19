CREATE TABLE IF NOT EXISTS SystemPreferences
(
  id         INT AUTO_INCREMENT PRIMARY KEY,
  name       VARCHAR(40) NULL,
  value      VARCHAR(40) NULL,
  updateDate DATETIME    NULL
);