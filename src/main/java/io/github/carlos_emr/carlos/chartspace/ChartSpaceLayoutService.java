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
 */
package io.github.carlos_emr.carlos.chartspace;

import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences.ECHART_PREFERENCE_KEYS;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and saves the ChartSpace clinic default layout in {@code SystemPreferences}.
 *
 * <p>No schema change or seed row is needed: a missing, blank or invalid value means
 * {@link ChartSpaceLayout#DEFAULT}. Database errors are deliberately not caught; they propagate
 * to the caller, so an administrator never sees (and re-saves) an invented default.</p>
 *
 * @since 2026-10-23
 */
@Service
public class ChartSpaceLayoutService {

    private static final Logger logger = MiscUtils.getLogger();
    private static final ECHART_PREFERENCE_KEYS KEY = ECHART_PREFERENCE_KEYS.chartspace_default_layout;

    private final SystemPreferencesDao systemPreferencesDao;

    public ChartSpaceLayoutService(SystemPreferencesDao systemPreferencesDao) {
        this.systemPreferencesDao = systemPreferencesDao;
    }

    /**
     * Loads the clinic default layout, for the admin screen and for the ChartSpace view.
     *
     * <p>A stored value that is not on the allow-list is read as {@link ChartSpaceLayout#DEFAULT}
     * and logged at WARN with the preference name only, never the stored value. Do not call this
     * from another method of this class: the transaction proxy would not apply.</p>
     *
     * @return the layout; never {@code null}
     */
    @Transactional(readOnly = true)
    public ChartSpaceLayout loadClinicDefault() {
        SystemPreferences preference = systemPreferencesDao.findPreferenceByName(KEY);
        // getValue() maps a NULL column to "", which is treated like a missing row.
        if (preference == null || preference.getValue().isBlank()) {
            return ChartSpaceLayout.DEFAULT;
        }
        return ChartSpaceLayout.fromId(preference.getValue()).orElseGet(() -> {
            logger.warn("Invalid stored value for preference {}; using the default layout", KEY.name());
            return ChartSpaceLayout.DEFAULT;
        });
    }

    /**
     * Saves the clinic default layout, creating the preference row when it does not exist yet.
     *
     * <ol>
     *   <li>Runs at REPEATABLE READ explicitly, whatever the server default: the race-free upsert
     *       relies on InnoDB next-key locks that READ COMMITTED does not take (see
     *       {@link SystemPreferencesDao#upsertPreference}).</li>
     *   <li>Call it outside any existing transaction: a joined transaction would keep the outer
     *       isolation.</li>
     *   <li>There is no retry. The deadlock described by the DAO happens when the
     *       {@code SystemPreferences} table is empty, and the ON/BC seeds leave it empty; but in the
     *       admin flow Ocean first writes {@code echart_show_ocean} in its own committed
     *       transaction, so the table is not empty. If a conflict still happens the error reaches
     *       the administrator, who can save again.</li>
     *   <li>Saving together with the Ocean setting is not atomic: Ocean commits first.</li>
     * </ol>
     *
     * @param layout the layout to store, already validated through {@link ChartSpaceLayout#fromId}
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void saveClinicDefault(ChartSpaceLayout layout) {
        // Not find-then-insert: that duplicates rows when two first saves race (see upsertPreference).
        systemPreferencesDao.upsertPreference(KEY, layout.id());
    }
}
