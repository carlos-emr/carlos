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

import java.util.EnumMap;
import java.util.Map;

/**
 * One provider's effective eChart display settings: every {@link EchartDisplayPreference} with its
 * stored value, or its default when the provider never saved it. Immutable.
 *
 * @since 2026-10-16
 */
public final class EchartDisplayPreferences {

    private final Map<EchartDisplayPreference, Boolean> values;

    private EchartDisplayPreferences(Map<EchartDisplayPreference, Boolean> values) {
        this.values = values;
    }

    /**
     * Builds the effective settings from stored {@code property} rows.
     *
     * @param storedValues {@code property.name} to {@code property.value}; names that are not eChart
     *                     display settings are ignored, and a missing or unreadable value means default
     * @return the effective settings, never {@code null}
     */
    public static EchartDisplayPreferences fromStoredValues(Map<String, String> storedValues) {
        Map<EchartDisplayPreference, Boolean> resolved = new EnumMap<>(EchartDisplayPreference.class);
        for (EchartDisplayPreference preference : EchartDisplayPreference.values()) {
            String stored = storedValues == null ? null : storedValues.get(preference.propertyName());
            resolved.put(preference, preference.resolve(stored));
        }
        return new EchartDisplayPreferences(resolved);
    }

    /** @return the settings of a provider who has saved nothing */
    public static EchartDisplayPreferences defaults() {
        return fromStoredValues(Map.of());
    }

    /**
     * @param preference the setting
     * @return whether it is on
     */
    public boolean isEnabled(EchartDisplayPreference preference) {
        return values.get(preference);
    }

    public boolean showTimer() {
        return isEnabled(EchartDisplayPreference.SHOW_TIMER);
    }

    public boolean groupDocumentsByType() {
        return isEnabled(EchartDisplayPreference.GROUP_DOCUMENTS_BY_TYPE);
    }

    public boolean showDob() {
        return isEnabled(EchartDisplayPreference.SHOW_DOB);
    }

    public boolean showCell() {
        return isEnabled(EchartDisplayPreference.SHOW_CELL);
    }

    public boolean phoneTelLinks() {
        return isEnabled(EchartDisplayPreference.PHONE_TEL_LINKS);
    }

    public boolean showHin() {
        return isEnabled(EchartDisplayPreference.SHOW_HIN);
    }

    public boolean showEmail() {
        return isEnabled(EchartDisplayPreference.SHOW_EMAIL);
    }

    public boolean showFamilyDoctor() {
        return isEnabled(EchartDisplayPreference.SHOW_FAMILY_DOCTOR);
    }

    public boolean showReferringDoctor() {
        return isEnabled(EchartDisplayPreference.SHOW_REFERRING_DOCTOR);
    }

    public boolean showOcean() {
        return isEnabled(EchartDisplayPreference.SHOW_OCEAN);
    }
}
