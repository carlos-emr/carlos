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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.util;

import java.math.BigDecimal;

/**
 * Normalises A1C (glycated haemoglobin) values to percent for flowsheet flagging and graphing.
 *
 * <p>Some labs report A1C as a fraction (0.071) while most report percent (7.1). The unit is
 * not stored with the measurement, so both can sit under the one {@code A1C} type. An A1C
 * below 1% is incompatible with life, so a value in (0, 1) is read as a fraction and
 * multiplied by 100 (clinical direction on issue #4614). The stored value is never changed;
 * this applies only when a value is compared or plotted.</p>
 */
public final class A1cPercent {

    /** Measurement type code the normalisation applies to. */
    public static final String TYPE = "A1C";

    private A1cPercent() {
    }

    /**
     * @param measurementType the measurement type code, may be null
     * @return true when values of this type are A1C and need normalising
     */
    public static boolean appliesTo(String measurementType) {
        return TYPE.equals(measurementType);
    }

    /**
     * @param value an A1C value
     * @return the value as percent: values strictly between 0 and 1 are multiplied by 100
     */
    public static double toPercent(double value) {
        if (value > 0 && value < 1) {
            // BigDecimal keeps 0.095 -> 9.5 exact (0.095 * 100 in binary is 9.499999999999998).
            return BigDecimal.valueOf(value).movePointRight(2).doubleValue();
        }
        return value;
    }

    /**
     * Text form of {@link #toPercent(double)} for callers that hold the raw value string.
     *
     * @param value the raw measurement text, may be null
     * @return the normalised text, or the input unchanged when it is null or not a number
     */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            double percent = toPercent(parsed);
            return percent == parsed ? value : BigDecimal.valueOf(percent).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return value;
        }
    }
}
