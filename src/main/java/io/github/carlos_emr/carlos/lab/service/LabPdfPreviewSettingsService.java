/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 *
 */
package io.github.carlos_emr.carlos.lab.service;

import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and saves {@link LabPdfPreviewSettings} in {@code SystemPreferences}.
 *
 * <p>No schema change or seed rows are needed: a missing row means the default (preview on,
 * 10 MiB), so an upgraded install behaves the same as a new one until an administrator changes
 * a value on <em>Administration &gt; Labs/Inbox &gt; Lab Display Settings</em>.</p>
 *
 * @since 2026-09-30
 */
@Service
public class LabPdfPreviewSettingsService {

    private final SystemPreferencesDao systemPreferencesDao;

    public LabPdfPreviewSettingsService(SystemPreferencesDao systemPreferencesDao) {
        this.systemPreferencesDao = systemPreferencesDao;
    }

    /**
     * Loads the current settings.
     *
     * @return the settings, with defaults for missing or invalid values; never {@code null}
     */
    @Transactional(readOnly = true)
    public LabPdfPreviewSettings load() {
        return LabPdfPreviewSettings.fromPreferences(
                valueOf(LAB_DISPLAY_PREFERENCE_KEYS.lab_pdf_inline_preview),
                valueOf(LAB_DISPLAY_PREFERENCE_KEYS.lab_pdf_max_size));
    }

    /**
     * Saves both settings, creating the preference rows when they do not exist yet.
     *
     * <p>Runs at REPEATABLE READ explicitly, whatever the server default: the race-free upsert
     * relies on InnoDB's next-key locks, which READ COMMITTED does not take (see
     * {@link SystemPreferencesDao#upsertPreference}). Call it outside any existing transaction,
     * where a joined transaction would keep the outer isolation, and retry a lock conflict from
     * outside it.</p>
     *
     * @param settings the settings to store; {@code maxBytes} is stored as a plain byte count
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void save(LabPdfPreviewSettings settings) {
        upsert(LAB_DISPLAY_PREFERENCE_KEYS.lab_pdf_inline_preview, Boolean.toString(settings.inlinePreviewEnabled()));
        upsert(LAB_DISPLAY_PREFERENCE_KEYS.lab_pdf_max_size, Long.toString(settings.maxBytes()));
    }

    private String valueOf(LAB_DISPLAY_PREFERENCE_KEYS key) {
        SystemPreferences preference = systemPreferencesDao.findPreferenceByName(key);
        // getValue() maps a NULL column to "", which is treated like a missing row.
        return preference == null || preference.getValue().isBlank() ? null : preference.getValue();
    }

    private void upsert(LAB_DISPLAY_PREFERENCE_KEYS key, String value) {
        // Not find-then-insert: that duplicates rows when two first saves race (see upsertPreference).
        systemPreferencesDao.upsertPreference(key, value);
    }
}
