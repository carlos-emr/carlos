/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.service;

import io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao.DateSearchType;
import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import org.springframework.stereotype.Service;

/**
 * Resolves the inbox date-search mode from the {@code inboxDateSearchType} system preference.
 *
 * <p>The inbox counts used to read this preference inside {@code InboxCategoryDao}, once per
 * category count, which made the DAO inject {@link SystemPreferencesDao} (issue #5772). Callers now
 * resolve the mode once per inbox load and carry it in the
 * {@link io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao.Filter}, so every category of one
 * load uses the same mode. A read failure propagates: the inbox must not fall back to a mode the
 * administrator did not choose.</p>
 *
 * @since 2026-11-19
 */
@Service
public class InboxDateSearchTypeResolver {
    private final SystemPreferencesDao preferences;

    /**
     * Creates the resolver.
     * @param preferences SystemPreferencesDao that stores the inbox display preferences
     */
    public InboxDateSearchTypeResolver(SystemPreferencesDao preferences) {
        this.preferences = preferences;
    }

    /**
     * Reads the configured mode. A missing, blank or unrecognised preference selects
     * {@link DateSearchType#SERVICE_OBSERVATION}, as it always has.
     *
     * @return DateSearchType the mode the inbox counts and lists apply now
     */
    public DateSearchType resolve() {
        SystemPreferences preference = preferences.findPreferenceByName(
                SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.inboxDateSearchType);
        return DateSearchType.fromPreference(preference == null ? null : preference.getValue());
    }
}
