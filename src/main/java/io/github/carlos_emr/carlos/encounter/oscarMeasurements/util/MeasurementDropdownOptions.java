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

import io.github.carlos_emr.carlos.commn.model.Validations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Derives the dropdown choices the Add Measurement page offers for a pattern-style validation
 * rule, and recognizes stored values that are no longer one of those choices.
 *
 * <p>The option list comes from the rule name when it is slash-separated
 * ({@code "Provided/Revised/Reviewed"} gives three options) and otherwise from the
 * pipe-separated regular expression ({@code "Yes|No"}). Using the name keeps the list short for
 * rules whose expression accepts several spellings of the same answer
 * ({@code "YES|yes|Yes|Y|..."}).</p>
 *
 * <p>When a measurement type moves to a different rule, values recorded under the old rule
 * remain in the {@code measurements} table. For example, the Asthma Action Plan (AACP) moved from
 * Yes/No/NA to Provided/Revised/Reviewed for OntarioMD conformance (issue #3893), so older AACP
 * rows hold {@code "Yes"} or {@code "No"}. {@link #isLegacyValue(List, String)} lets the page keep
 * showing such a value instead of silently rendering an empty selection.</p>
 *
 * <p>The Health Tracker only has the rule <em>name</em> at hand
 * ({@code EctMeasurementTypesBean.getValidationName()}), so {@link #forValidationName(String)}
 * exposes the name-only half of the derivation for it. Both pages therefore offer the same
 * choices for a slash-named rule.</p>
 *
 * @since 2026-09-24
 */
public final class MeasurementDropdownOptions {

    private MeasurementDropdownOptions() {
    }

    /**
     * Returns the choices to render for a pattern-style validation rule.
     *
     * @param validation the measurement type's validation rule; may be {@code null}
     * @return the options in rule order, or an empty list when the rule has neither a
     *         slash-separated name nor a regular expression
     */
    public static List<String> forValidation(Validations validation) {
        if (validation == null) {
            return Collections.emptyList();
        }
        List<String> fromName = forValidationName(validation.getName());
        if (!fromName.isEmpty()) {
            return fromName;
        }
        String regularExp = validation.getRegularExp();
        if (regularExp == null || regularExp.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(regularExp.split("\\|"));
    }

    /**
     * Returns the choices a slash-separated validation rule name offers
     * ({@code "Provided/Revised/Reviewed"} gives three), or an empty list when the name has no
     * slash and so does not enumerate its own options.
     *
     * @param validationName the validation rule name; may be {@code null}
     * @return the options in name order, or an empty list
     */
    public static List<String> forValidationName(String validationName) {
        if (validationName == null || !validationName.contains("/")) {
            return Collections.emptyList();
        }
        return List.of(validationName.split("/"));
    }

    /**
     * Reports whether a stored value is present but matches none of the current options, which
     * happens for values recorded before the measurement type's validation rule changed.
     *
     * <p>The comparison is exact (case-sensitive), matching how the page pre-selects an option.</p>
     *
     * @param options the options returned by {@link #forValidation(Validations)}
     * @param storedValue the recorded value being displayed; may be {@code null}
     * @return {@code true} when the value is non-empty and not one of {@code options}
     */
    public static boolean isLegacyValue(List<String> options, String storedValue) {
        if (storedValue == null || storedValue.isEmpty()) {
            return false;
        }
        return options == null || !options.contains(storedValue);
    }
}
