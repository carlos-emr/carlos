-- SPDX-License-Identifier: GPL-2.0-or-later
-- The historical roster candidate query filters by provider and roster start.
CREATE INDEX IF NOT EXISTS idx_demographicArchive_provider_roster_date
    ON demographicArchive (provider_no, roster_date);
