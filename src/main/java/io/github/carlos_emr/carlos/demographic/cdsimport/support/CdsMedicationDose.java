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
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import java.math.BigDecimal;
import java.util.Optional;

/** Exact bridge to legacy float dose fields. Unrepresentable free text stays authoritative text. */
public final class CdsMedicationDose {
    private CdsMedicationDose() { }
    public record Range(float minimum, float maximum) { }

    // Exact binary-float equality is intentional: valueOf uses a rounded decimal spelling.
    @SuppressWarnings("java:S2111")
    public static Optional<Range> parse(String source) {
        if (source == null || !source.matches("[0-9]++(?:\\.[0-9]++)?(?:-[0-9]++(?:\\.[0-9]++)?)?")) {
            return Optional.empty();
        }
        String[] range = source.split("-", -1);
        BigDecimal minimum = new BigDecimal(range[0]);
        BigDecimal maximum = new BigDecimal(range.length == 1 ? range[0] : range[1]);
        if (minimum.compareTo(maximum) > 0) return Optional.empty();
        float low = minimum.floatValue();
        float high = maximum.floatValue();
        if (!Float.isFinite(low) || !Float.isFinite(high)
                || new BigDecimal((double) low).compareTo(minimum) != 0
                || new BigDecimal((double) high).compareTo(maximum) != 0) {
            return Optional.empty();
        }
        return Optional.of(new Range(low, high));
    }
}
