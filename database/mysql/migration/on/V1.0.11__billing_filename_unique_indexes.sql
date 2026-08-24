-- Ontario-only billing filenames must be unique so claim and HTML output files
-- cannot collide. This replaces the legacy dated update script for both fresh
-- installs and Flyway-adopted upgrades.
--
-- Existing CARLOS installations may already have these exact indexes from
-- update-2026-05-03-billing-disk-filename-unique.sql. IF NOT EXISTS keeps
-- Flyway adoption idempotent for those databases. If an installation has
-- duplicate non-NULL filenames and no index yet, the CREATE fails rather than
-- silently discarding or rewriting billing data.

CREATE UNIQUE INDEX IF NOT EXISTS billing_on_diskname_ohipfilename_uq
  ON billing_on_diskname (ohipfilename);

CREATE UNIQUE INDEX IF NOT EXISTS billing_on_filename_htmlfilename_uq
  ON billing_on_filename (htmlfilename);
