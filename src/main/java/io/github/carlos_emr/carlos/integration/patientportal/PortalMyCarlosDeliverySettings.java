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
package io.github.carlos_emr.carlos.integration.patientportal;

import java.util.function.Function;

/** The separately enabled myCarlos channel; existing portal booking calls do not read this switch. */
final class PortalMyCarlosDeliverySettings {
    static final String ENABLED_KEY = "patient_portal.mycarlos_delivery.enabled";

    private PortalMyCarlosDeliverySettings() {}

    static boolean isEnabled() {
        return isEnabled(PatientPortalSettings::rawProperty);
    }

    static boolean isEnabled(Function<String, String> lookup) {
        String value = lookup.apply(ENABLED_KEY);
        if (value == null || value.isBlank() || "false".equals(value.strip())) {
            return false;
        }
        if ("true".equals(value.strip())) {
            return true;
        }
        throw new PatientPortalConfigurationException(ENABLED_KEY + " must be true or false");
    }
}
