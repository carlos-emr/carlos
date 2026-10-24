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
package io.github.carlos_emr.carlos.appointment;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;

/**
 * The contact lines a provider's appointment card prints: the name line, phone and fax (issue #5134).
 *
 * <p>Preferences (the Save All page and the Set Appointment Card Preferences page) saves them as
 * {@code appointmentCardName/Phone/Fax}. Before OpenO a8d3e3d324 (June 2025) that page saved
 * {@code APPT_CARD_NAME/PHONE/FAX}, and Print Card kept reading those, so the values saved in
 * Preferences never reached the card. Each line is now the saved value, else the older value an
 * upgraded install may still hold, else the card's own default.</p>
 *
 * <p>A blank saved value counts as not set: Save All writes {@code ""} for every empty box, so an
 * upgraded provider who saved Preferences already has blank new rows beside the older values the
 * card printed.</p>
 *
 * @param name  the name line
 * @param phone the phone line
 * @param fax   the fax line
 * @since 2026-10-21
 */
public record AppointmentCardContact(String name, String phone, String fax) {

    /** One card line: the key Preferences saves, and the key older installs saved. */
    public enum Field {
        NAME("appointmentCardName", "APPT_CARD_NAME"),
        PHONE("appointmentCardPhone", "APPT_CARD_PHONE"),
        FAX("appointmentCardFax", "APPT_CARD_FAX");

        private final String key;
        private final String legacyKey;

        Field(String key, String legacyKey) {
            this.key = key;
            this.legacyKey = legacyKey;
        }

        /** The user property Preferences saves this line under. */
        public String key() {
            return key;
        }

        /** The user property older installs saved this line under. */
        public String legacyKey() {
            return legacyKey;
        }
    }

    /**
     * Resolves the card lines for a provider.
     *
     * @param userProperties the provider's user properties
     * @param providerNo     the appointment's provider
     * @param defaultName    the name line when nothing is saved ("Dr. First Last")
     * @param defaultPhone   the phone when nothing is saved (the clinic's)
     * @param defaultFax     the fax when nothing is saved (the clinic's)
     * @return the lines to print; never {@code null} fields unless a default is {@code null}
     */
    public static AppointmentCardContact resolve(UserPropertyDAO userProperties, String providerNo,
                                                 String defaultName, String defaultPhone, String defaultFax) {
        return new AppointmentCardContact(
                orDefault(saved(userProperties, providerNo, Field.NAME), defaultName),
                orDefault(saved(userProperties, providerNo, Field.PHONE), defaultPhone),
                orDefault(saved(userProperties, providerNo, Field.FAX), defaultFax));
    }

    /**
     * What a Preferences page shows in a card box, from the provider's properties as a map: the
     * saved value, else the older one, else {@code ""}. Showing the older value means the next save
     * carries it into the new key instead of hiding what the card prints.
     */
    public static String shownValue(Map<String, String> providerProperties, Field field) {
        return preferred(providerProperties.get(field.key()), providerProperties.get(field.legacyKey()));
    }

    /** As {@link #shownValue(Map, Field)}, read from the DAO. */
    public static String shownValue(UserPropertyDAO userProperties, String providerNo, Field field) {
        return saved(userProperties, providerNo, field);
    }

    // The older key is read only when the saved value is blank, so a card whose lines are all
    // saved costs one lookup per line, as it did before.
    private static String saved(UserPropertyDAO userProperties, String providerNo, Field field) {
        String saved = value(userProperties.getProp(providerNo, field.key()));
        if (StringUtils.isNotBlank(saved)) {
            return saved;
        }
        return preferred(saved, value(userProperties.getProp(providerNo, field.legacyKey())));
    }

    // The first non-blank of the saved and the older value, or "".
    private static String preferred(String saved, String legacy) {
        if (StringUtils.isNotBlank(saved)) {
            return saved;
        }
        return StringUtils.isNotBlank(legacy) ? legacy : "";
    }

    private static String value(UserProperty property) {
        return property == null ? null : property.getValue();
    }

    private static String orDefault(String value, String fallback) {
        return value.isEmpty() ? fallback : value;
    }
}
