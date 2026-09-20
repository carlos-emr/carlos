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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementTypeBeanHandler;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementTypesBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Turns the Health Tracker's flat form-parameter map into typed
 * {@link HealthTrackerEntry} values.
 *
 * <p>The parser is driven by the flowsheet, never by the request: it walks
 * {@link MeasurementFlowSheet#getMeasurementList()} and only looks for parameters
 * whose names it derived itself. A caller cannot therefore smuggle in a
 * measurement type that is not on the flowsheet the request named, which is the
 * property the legacy {@code FormUpdate2Action} relied on too.
 */
public class HealthTrackerSubmissionParser {

    private final EctMeasurementTypeBeanHandler measurementTypes;

    /**
     * @param measurementTypes lookup for {@code measurementType} rows; injected so
     *        tests can supply a stub instead of hitting the database
     */
    public HealthTrackerSubmissionParser(EctMeasurementTypeBeanHandler measurementTypes) {
        this.measurementTypes = measurementTypes;
    }

    public HealthTrackerSubmissionParser() {
        this(new EctMeasurementTypeBeanHandler());
    }

    /**
     * Derives the form-field name the Health Tracker page uses for a flowsheet item.
     *
     * <p>Keyed on the item's measurement type, not its display name. The type is the
     * key {@code MeasurementFlowSheet} orders its items by, so it is unique within a
     * flowsheet; display names are not. Two items may carry the same display name, and
     * two different names can sanitize to one field ({@code A/B} and {@code AB} both
     * become {@code AB}) -- either way one posted parameter would answer for both rows
     * and a value could be written under the wrong measurement type.
     *
     * <p>The transform is injective, which stripping characters is not.
     * {@code EctAddMeasurementType2Action} accepts {@code ^[\\w\\s,.?]*$}, so an
     * administrator can create both {@code FOO BAR} and {@code FOOBAR}; removing the
     * space would give them one field name, one posted parameter, and a value written
     * under whichever type was read last. Every character outside {@code [A-Za-z0-9]}
     * is escaped instead, as {@code _} followed by its four-digit hex code, and a
     * literal {@code _} doubles. An escape is therefore always distinguishable: after
     * a {@code _}, another {@code _} is the character itself and anything else begins
     * a four-digit code. A type made only of letters and digits -- which every type in
     * the shipped reference data is -- passes through unchanged.
     *
     * <p>Kept public and static because the JSP renders the same transform when it
     * emits the inputs; if the two ever drift the form silently stops saving, so
     * both sides call this one method.
     *
     * @param measurementType the flowsheet item's measurement type (its key in
     *        {@code MeasurementFlowSheet#getMeasurementList()})
     * @return a field name safe to use as an HTML name and element id, unique to
     *         this measurement type
     */
    public static String fieldNameFor(String measurementType) {
        if (measurementType == null) {
            return "";
        }
        StringBuilder name = new StringBuilder(measurementType.length());
        for (int i = 0; i < measurementType.length(); i++) {
            char c = measurementType.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                name.append(c);
            } else if (c == '_') {
                name.append("__");
            } else {
                name.append('_').append(String.format("%04X", (int) c));
            }
        }
        return name.toString();
    }

    /**
     * Extracts the entries the clinician actually filled in.
     *
     * @param flowSheet the flowsheet the request named, already merged with the
     *        provider/patient customizations
     * @param parameters accessor over the request parameters; a plain function so
     *        this class stays free of the servlet API
     * @param defaultDateObserved the form's hidden fallback date ({@code yyyy-MM-dd}),
     *        used when a row's own date box came back blank
     * @return one entry per non-blank value, in flowsheet order. Rows whose
     *         measurement type is unknown, or which map to a prevention item rather
     *         than a measurement, are skipped
     */
    public List<HealthTrackerEntry> parse(MeasurementFlowSheet flowSheet,
                                          Function<String, String> parameters,
                                          String defaultDateObserved) {
        List<HealthTrackerEntry> entries = new ArrayList<>();
        if (flowSheet == null) {
            return entries;
        }

        for (String measure : flowSheet.getMeasurementList()) {
            Map<String, String> info = flowSheet.getMeasurementFlowSheetInfo(measure);
            // Prevention items live on the same flowsheet but are added through the
            // prevention module, not this form; they have no measurement_type.
            if (info == null || info.get("measurement_type") == null) {
                continue;
            }

            String displayName = info.get("display_name");
            String fieldName = fieldNameFor(measure);
            String value = trimToEmpty(parameters.apply(fieldName));
            if (value.isEmpty()) {
                continue;
            }

            EctMeasurementTypesBean typeBean = measurementTypes.getMeasurementType(measure);
            if (typeBean == null) {
                continue;
            }

            String date = trimToEmpty(parameters.apply(fieldName + "_date"));
            if (date.isEmpty()) {
                date = defaultDateObserved;
            }

            entries.add(new HealthTrackerEntry(
                    typeBean.getType(),
                    fieldName,
                    displayName,
                    typeBean.getMeasuringInstrc(),
                    value,
                    trimToEmpty(parameters.apply(fieldName + "_comments")),
                    date,
                    !trimToEmpty(parameters.apply(fieldName + "_note")).isEmpty()));
        }
        return entries;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
