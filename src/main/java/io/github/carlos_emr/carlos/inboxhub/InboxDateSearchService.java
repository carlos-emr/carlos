/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.inboxhub;

import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import org.springframework.stereotype.Service;

/** Resolves the date mode once when an inbox request creates its immutable filters. */
@Service
public class InboxDateSearchService {
    private final SystemPreferencesDao preferences;

    public InboxDateSearchService(SystemPreferencesDao preferences) {
        this.preferences = preferences;
    }

    /** Missing/empty configuration keeps the legacy default; retrieval failures propagate. */
    public String resolve() {
        var preference = preferences.findPreferenceByName(SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.inboxDateSearchType);
        return preference == null || preference.getValue() == null || preference.getValue().isEmpty()
                ? "serviceObservation" : preference.getValue();
    }
}
