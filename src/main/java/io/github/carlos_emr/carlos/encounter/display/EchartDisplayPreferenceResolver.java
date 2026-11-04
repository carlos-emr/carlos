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
package io.github.carlos_emr.carlos.encounter.display;

import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Resolves a provider's eChart display settings: the stored value of each
 * {@link EchartDisplayPreference}, falling back to its default.
 *
 * <p>Read on every chart render (header, note panel, left navigation, Documents panel), so it reads
 * just the eChart settings in one query rather than every property the provider owns.</p>
 *
 * @since 2026-10-16
 */
@Service
public class EchartDisplayPreferenceResolver {

    private static final List<String> PROPERTY_NAMES = Arrays.stream(EchartDisplayPreference.values())
            .map(EchartDisplayPreference::propertyName)
            .toList();

    private final UserPropertyDAO userPropertyDao;
    private final SystemPreferencesDao systemPreferencesDao;

    public EchartDisplayPreferenceResolver(UserPropertyDAO userPropertyDao, SystemPreferencesDao systemPreferencesDao) {
        this.userPropertyDao = userPropertyDao;
        this.systemPreferencesDao = systemPreferencesDao;
    }

    /**
     * @param providerNo the logged-in provider; {@code null} (no session) yields the defaults
     * @return the provider's effective settings, never {@code null}
     */
    public EchartDisplayPreferences resolve(String providerNo) {
        if (providerNo == null || providerNo.isBlank()) {
            return EchartDisplayPreferences.defaults();
        }
        return EchartDisplayPreferences.fromStoredValues(
                userPropertyDao.getProviderPropertiesByNames(providerNo, PROPERTY_NAMES));
    }

    /**
     * Whether the Ocean toolbar placeholder belongs on this provider's chart.
     *
     * <p>Two switches must both allow it. The clinic-wide {@code echart_show_ocean} system
     * preference (Administration &gt; eChart Display Settings, on when never saved) lets an
     * administrator turn Ocean off for everyone; the provider's own "Display OCEAN UI" setting (off
     * until they choose it) opts them in. Whether the Ocean script loads at all is decided separately
     * by {@code cme_js=ocean} in {@code carlos.properties}.</p>
     *
     * @param preferences the provider's resolved settings
     * @return {@code true} only when the clinic allows Ocean and the provider has turned it on
     */
    public boolean isOceanShown(EchartDisplayPreferences preferences) {
        if (!preferences.showOcean()) {
            return false;
        }
        SystemPreferences clinicSetting = systemPreferencesDao.findPreferenceByName(
                SystemPreferences.ECHART_PREFERENCE_KEYS.echart_show_ocean);
        return clinicSetting == null || clinicSetting.getValueAsBoolean();
    }
}
