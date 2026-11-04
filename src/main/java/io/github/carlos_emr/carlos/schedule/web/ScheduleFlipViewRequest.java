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
package io.github.carlos_emr.carlos.schedule.web;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Checks the request values the provider availability view works from, so the rules can be unit-tested
 * instead of living in the JSP.
 *
 * @since 2026-09-28
 */
public final class ScheduleFlipViewRequest {
    /** Slot length used when the provider's preference holds none, or an unusable one. */
    public static final int DEFAULT_SLOT_MINUTES = 15;

    private static final Pattern PROVIDER_NO = Pattern.compile("[a-zA-Z0-9._-]+");
    private static final Pattern START_DATE = Pattern.compile("[0-9]{4}-[0-9]{1,2}-[0-9]{1,2}");

    private ScheduleFlipViewRequest() {
    }

    /**
     * Picks the provider to show: the requested one, else the session preference's, else the logged-in
     * provider. A blank value counts as absent.
     *
     * @return the provider number, or empty when none is given or the chosen one has characters outside
     *         {@code [A-Za-z0-9._-]}; the page answers 400 for empty
     */
    public static Optional<String> resolveProviderNo(String requested, String preferenceProviderNo,
                                                     String loggedInProviderNo) {
        for (String candidate : new String[]{requested, preferenceProviderNo, loggedInProviderNo}) {
            if (candidate != null && !candidate.isBlank()) {
                return PROVIDER_NO.matcher(candidate).matches() ? Optional.of(candidate) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * @param requested the {@code startDate} parameter: {@code yyyy-M-d} with or without zero padding,
     *                  {@code today}, blank or {@code null}
     * @param today     the date to use when none is requested
     * @return the first date to show
     * @throws IllegalArgumentException when {@code requested} is not a real calendar date in that form
     */
    public static Date resolveStartDate(String requested, Date today) {
        if (requested == null || requested.isBlank() || "today".equals(requested)) {
            return today;
        }
        if (!START_DATE.matcher(requested).matches()) {
            throw new IllegalArgumentException("Invalid startDate");
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date parsed = format.parse(requested, position);
        if (parsed == null || position.getIndex() != requested.length()) {
            throw new IllegalArgumentException("Invalid startDate");
        }
        return parsed;
    }

    /**
     * @param everyMin the slot length from the provider's preference
     * @return {@code everyMin}, or {@link #DEFAULT_SLOT_MINUTES} when it is zero or negative, which would
     *         otherwise divide by zero or loop without end while the grid is built
     */
    public static int slotMinutes(int everyMin) {
        return everyMin > 0 ? everyMin : DEFAULT_SLOT_MINUTES;
    }
}
