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
package io.github.carlos_emr.carlos.integration.patientportal.booking;

import io.github.carlos_emr.CarlosProperties;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Clinic settings for offering open times through the portal (#3850), read from the CARLOS
 * properties.
 *
 * <p>Only free time inside the schedule template codes the clinic chose ({@code offerable_codes},
 * Cortico style) is ever offered; nothing off the doctor's real template. With no codes chosen,
 * nothing is offered at all, so the feature is off until a clinic decides which codes patients
 * may book.
 */
public record PortalBookingSettings(
        Set<Character> offerableCodes, int leadHours, String visitMode, String locationCode,
        boolean syncEnabled, String syncProviderNo, long syncIntervalSeconds) {

    static final String PREFIX = "patient_portal.booking.";
    static final int DEFAULT_LEAD_HOURS = 24;
    static final long DEFAULT_SYNC_INTERVAL_SECONDS = 60;

    public PortalBookingSettings {
        offerableCodes = Collections.unmodifiableSet(new LinkedHashSet<>(offerableCodes));
    }

    public static PortalBookingSettings fromCarlosProperties() {
        CarlosProperties properties = CarlosProperties.getInstance();
        return fromProperties(properties::getProperty);
    }

    /** Bad values fall back to the safe default (nothing offered, sync off) rather than guessing. */
    static PortalBookingSettings fromProperties(Function<String, String> lookup) {
        Set<Character> codes = new LinkedHashSet<>();
        String configured = lookup.apply(PREFIX + "offerable_codes");
        if (configured != null) {
            for (String code : configured.split("[,\\s]+")) {
                // One template code is one character; '_' marks an empty slot and is never offerable.
                if (code.length() == 1 && code.charAt(0) != '_' && !Character.isWhitespace(code.charAt(0))) {
                    codes.add(code.charAt(0));
                }
            }
        }
        String mode = trimmed(lookup.apply(PREFIX + "visit_mode"));
        if (mode == null || !Set.of("in_person", "phone", "video").contains(mode)) {
            mode = "in_person";
        }
        String location = trimmed(lookup.apply(PREFIX + "location_code"));
        if (location != null && !location.matches("[a-z0-9_-]{1,32}")) {
            location = null;
        }
        String syncProvider = trimmed(lookup.apply(PREFIX + "sync.provider_no"));
        return new PortalBookingSettings(codes,
                (int) bounded(lookup.apply(PREFIX + "offer_lead_hours"), DEFAULT_LEAD_HOURS, 0, 24 * 30),
                mode, location,
                "true".equals(trimmed(lookup.apply(PREFIX + "sync.enabled"))) && syncProvider != null,
                syncProvider,
                bounded(lookup.apply(PREFIX + "sync.interval_seconds"), DEFAULT_SYNC_INTERVAL_SECONDS, 15, 3600));
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static long bounded(String value, long fallback, long min, long max) {
        try {
            long parsed = value == null ? fallback : Long.parseLong(value.strip());
            return parsed < min || parsed > max ? fallback : parsed;
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }
}
