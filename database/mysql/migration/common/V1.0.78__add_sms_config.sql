-- SMS settings saved from Administration > SMS (#3836), replacing the sms.* properties once saved:
-- the provider, whether sending is on, whether the queue scheduler runs, the sender number, the
-- webhook secret, and the provider's credentials.
--
-- The table holds at most one row, id 1: the fixed primary key and the CHECK constraint make a
-- second row impossible, so two administrators saving for the first time at once cannot leave a
-- second copy of the credentials behind. version lets the application refuse a save that raced
-- another instead of silently overwriting it. No row is seeded: until an administrator saves the
-- form, CARLOS keeps using sms.provider.default, sms.queue.scheduler.enabled and sends as before,
-- so upgrading changes nothing.
--
-- webhook_secret and each value in the credentials JSON are encrypted by the application with
-- EncryptionUtils (the {ENC} prefix), using encryption.util.secret.key, which is kept outside the
-- database. Plain text never reaches these columns.
--
-- Idempotent (CREATE TABLE IF NOT EXISTS), so a re-run is harmless.
--
-- Numbered V1.0.59: above develop (V1.0.57), release/2026.08 (V1.0.56) and #4440 (V1.0.58); see
-- database/mysql/migration/common/README.md. Renumber above the high-water mark at merge if needed.

CREATE TABLE IF NOT EXISTS sms_config (
  id INT NOT NULL,
  version INT NOT NULL DEFAULT 0,
  provider_type VARCHAR(16) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 0,
  scheduler_enabled TINYINT(1) NOT NULL DEFAULT 0,
  sender_number VARCHAR(32) NULL,
  webhook_secret VARCHAR(512) NULL,
  credentials TEXT NULL,
  updated_at DATETIME NOT NULL,
  updated_by VARCHAR(16) NULL,
  PRIMARY KEY (id),
  CONSTRAINT sms_config_single_row CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
