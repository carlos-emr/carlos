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
package io.github.carlos_emr.carlos.prevention;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The route and dose-unit codes the Add Prevention form offers, and how a value from the vaccine
 * brand catalogue, a stored record or an import maps onto them.
 *
 * <p>The form's Route and Dose Unit are selects, so a value is kept only when it is one of their
 * options. The shipped catalogue ({@code prevention/vaccine-brands.json}) also spells routes as
 * words or other abbreviations (SQ, Intranasal, Intradermal) and units in capitals (VIAL, DOSE), and
 * an exact match dropped them: the route showed blank and the unit showed mL (#5432). These methods
 * map a value to the form's own code in any letter case. A value with no equivalent comes back
 * unchanged, so the form can still show it as given rather than blank or as another value.</p>
 *
 * @since 2026-11-11
 */
public final class PreventionFormCodes {

    /** Route codes, in the order the form lists them. */
    public static final List<String> ROUTES = List.of("ID", "IM", "IN", "IV", "PO", "SC");

    /** Dose units, in the order the form lists them. */
    public static final List<String> DOSE_UNITS = List.of("mL", "mg", "g", "capsule", "vial", "dose");

    /** Upper-case spelling to the form's route code: each code itself, and words for the same route. */
    private static final Map<String, String> ROUTE_LOOKUP = lookup(ROUTES, Map.of(
            "INTRADERMAL", "ID",
            "INTRAMUSCULAR", "IM",
            "INTRANASAL", "IN",
            "INTRAVENOUS", "IV",
            "ORAL", "PO",
            "SQ", "SC",
            "SUBCUT", "SC",
            "SUBCUTANEOUS", "SC"));

    /** Upper-case spelling to the form's dose unit. */
    private static final Map<String, String> DOSE_UNIT_LOOKUP = lookup(DOSE_UNITS, Map.of());

    private PreventionFormCodes() {
        // Static mapping only.
    }

    /**
     * Returns the form's route code for a catalogue, stored or imported route.
     *
     * @param given the route as written; may be null
     * @return the form's code for the same route in any letter case (SQ and Subcutaneous give SC),
     *         the trimmed value when the form has no equivalent, or an empty string when none is given
     */
    public static String routeCode(String given) {
        return code(ROUTE_LOOKUP, given);
    }

    /**
     * Returns the form's dose unit for a catalogue, stored or imported unit.
     *
     * @param given the unit as written; may be null
     * @return the form's unit in its own letter case (VIAL gives vial, ML gives mL), the trimmed value
     *         when the form has no equivalent, or an empty string when none is given
     */
    public static String doseUnitCode(String given) {
        return code(DOSE_UNIT_LOOKUP, given);
    }

    /** Whether the form's Route select offers this code. */
    public static boolean isOfferedRoute(String code) {
        return code != null && ROUTES.contains(code);
    }

    /** Whether the form's Dose Unit select offers this unit. */
    public static boolean isOfferedDoseUnit(String code) {
        return code != null && DOSE_UNITS.contains(code);
    }

    private static String code(Map<String, String> lookup, String given) {
        if (given == null) return "";
        String value = given.trim();
        String code = lookup.get(value.toUpperCase(Locale.ROOT));
        return code != null ? code : value;
    }

    private static Map<String, String> lookup(List<String> codes, Map<String, String> synonyms) {
        Map<String, String> lookup = new HashMap<>(synonyms);
        for (String code : codes) {
            lookup.put(code.toUpperCase(Locale.ROOT), code);
        }
        return Map.copyOf(lookup);
    }
}
