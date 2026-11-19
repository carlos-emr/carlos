-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- NULL preserves ordinary native document behavior. The marker describes immutable
-- imported-source precision; it does not rewrite explicit later native observations.
-- No dates are backfilled and no legacy timestamp edit constraint is introduced.
ALTER TABLE document ADD COLUMN cds_content_precision
    VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL;
