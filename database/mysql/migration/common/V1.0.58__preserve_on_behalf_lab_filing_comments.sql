-- Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
-- Preserve the entire clinical comment when adding the server-generated filing attribution.
-- The old 255-character limit could reject a legitimate filing or silently truncate its history.
ALTER TABLE providerLabRouting MODIFY COLUMN comment TEXT DEFAULT '';
