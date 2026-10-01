-- Email footer (issue #3981).
--
-- emailLog.footer keeps the footer that was sent below the message, so Manage Emails can copy
-- it into a resend and the log shows exactly what the patient received. It is stored apart from
-- body because body is what the chart note is built from, and the footer is never charted.
-- Base64 BLOB, the same as body. Rows written before this change read as an empty footer.
--
-- emailConfig.defaultFooter is the footer the compose screen fills in for that sending account
-- when an eForm does not supply one. It is a plain column, not part of configDetails, which
-- holds the credentials. There is no edit screen yet; it is set by SQL.
--
-- IF NOT EXISTS keeps the migration safe to re-run.
--
-- Renumber before merge: take the next free version above the highest in BOTH develop and release/2026.08 at merge time.
ALTER TABLE emailLog ADD COLUMN IF NOT EXISTS footer BLOB DEFAULT NULL AFTER body;
ALTER TABLE emailConfig ADD COLUMN IF NOT EXISTS defaultFooter TEXT DEFAULT NULL;
