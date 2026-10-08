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

import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * Request-parameter parsing for the read-only ChartSpace page.
 *
 * <p>Kept separate from the Struts action so the strict {@code demographicNo}
 * contract (1 to 10 ASCII digits, positive, fits in {@code int}) is unit
 * testable and reusable. Anything else is treated as absent rather than
 * normalised, so a malformed id never reaches a privilege check or a DAO.</p>
 *
 * @since 2026-10-08
 */
public final class ChartSpaceParams {

    private static final Pattern DIGITS = Pattern.compile("^\\d{1,10}$");

    private ChartSpaceParams() {
    }

    /**
     * Parses a raw {@code demographicNo} request parameter.
     *
     * @param raw the raw parameter value, may be {@code null}
     * @return the positive id, or empty if {@code raw} is null, blank, not
     *         1-10 digits, zero, or greater than {@link Integer#MAX_VALUE}
     */
    public static OptionalInt parseDemographicNo(String raw) {
        if (raw == null || !DIGITS.matcher(raw).matches()) {
            return OptionalInt.empty();
        }
        // 10 digits can exceed int range, so parse as long before narrowing.
        long value = Long.parseLong(raw);
        if (value < 1 || value > Integer.MAX_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) value);
    }
}
