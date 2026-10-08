-- Email footer (issue #3981; formatting and a clinic logo added by maintainer decision, 8 Oct 2026).
--
-- emailLog.footer keeps the footer that was sent below the message, so Manage Emails can copy
-- it into a resend and the log shows exactly what the patient received. It holds the cleaned,
-- formatted (HTML) footer. It is stored apart from body because body is what the chart note is
-- built from, and the footer is never charted. Base64 BLOB, the same as body. Rows written before
-- this change read as an empty footer.
--
-- property.value grows from VARCHAR(2000) to TEXT: the clinic footer and each user's own footer
-- (#4093) are stored in property, and a formatted footer of 2,000 visible characters can be longer
-- than 2,000 characters of HTML. No index or key covers property.value, so nothing is lost. On a
-- large property table MariaDB rebuilds the table for this change; it takes seconds for typical
-- clinics, during which writes to property wait.
--
-- emailFooterLogo holds the clinic's footer logo (PNG or JPEG, at most 100 KB, re-encoded by
-- CARLOS). The newest row without removedAt is the logo in use; replaced and removed logos keep
-- their row, so what was sent stays traceable alongside the outbound email archive.
--
-- IF NOT EXISTS and MODIFY keep the migration safe to re-run.
--
-- Renumber before merge: take the next free version above the highest in BOTH develop and release/2026.08 at merge time.
ALTER TABLE emailLog ADD COLUMN IF NOT EXISTS footer BLOB DEFAULT NULL AFTER body;

ALTER TABLE property MODIFY COLUMN value TEXT DEFAULT NULL;

CREATE TABLE IF NOT EXISTS emailFooterLogo (
  id INT NOT NULL AUTO_INCREMENT,
  contentType VARCHAR(32) NOT NULL,
  imageData MEDIUMBLOB NOT NULL,
  width INT NOT NULL,
  height INT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  uploadedBy VARCHAR(6) NOT NULL,
  uploadedAt DATETIME NOT NULL,
  removedAt DATETIME DEFAULT NULL,
  removedBy VARCHAR(6) DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
