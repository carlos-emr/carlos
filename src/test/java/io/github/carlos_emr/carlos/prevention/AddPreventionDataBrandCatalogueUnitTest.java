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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The vaccine brand picker prefills the Add Prevention form from {@code prevention/vaccine-brands.json};
 * every route and dose unit the shipped catalogue uses must be one the form's selects offer (#5432).
 */
@Tag("unit")
@Tag("fast")
@DisplayName("Add Prevention form and the shipped vaccine brand catalogue (#5432)")
class AddPreventionDataBrandCatalogueUnitTest {

    private static final Path CATALOGUE = Path.of("src/main/webapp/prevention/vaccine-brands.json");
    private static final Path FORM = Path.of("src/main/webapp/WEB-INF/jsp/prevention/AddPreventionData.jsp");
    private static final Pattern LITERAL_OPTION = Pattern.compile("<option value=\"([A-Za-z]+)\"");

    @Test
    @DisplayName("should offer the code for every route the catalogue prefills")
    void shouldOfferEveryCatalogueRoute_inRouteSelect() throws Exception {
        Set<String> offered = options("<select name=\"route\" id=\"route\"");
        assertThat(offered).containsExactlyInAnyOrderElementsOf(PreventionFormCodes.ROUTES);

        List<String> notOffered = new ArrayList<>();
        for (String route : catalogueValues("route")) {
            if (!offered.contains(PreventionFormCodes.routeCode(route))) notOffered.add(route);
        }
        assertThat(notOffered).as("catalogue routes the form would show blank").isEmpty();
    }

    @Test
    @DisplayName("should offer every dose unit the catalogue prefills")
    void shouldOfferEveryCatalogueUnit_inDoseUnitSelect() throws Exception {
        Set<String> offered = options("<select name=\"doseUnit\"");
        assertThat(offered).containsExactlyInAnyOrderElementsOf(PreventionFormCodes.DOSE_UNITS);

        List<String> notOffered = new ArrayList<>();
        for (String unit : catalogueValues("units")) {
            if (!offered.contains(PreventionFormCodes.doseUnitCode(unit))) notOffered.add(unit);
        }
        assertThat(notOffered).as("catalogue units the form would replace with another").isEmpty();
    }

    @Test
    @DisplayName("should default a new record to mL only when no unit is prefilled")
    void shouldKeepPrefilledUnit_forNewRecord() throws Exception {
        String form = Files.readString(FORM);
        assertThat(form)
                .contains("String d2 = PreventionFormCodes.doseUnitCode(pUnit);")
                .contains("if (\"\".equals(dose) && \"\".equals(d2)) {")
                .contains("PreventionFormCodes.routeCode(storedRoute != null ? storedRoute : pRoute)");
    }

    /** The literal option values of the select that starts with {@code selectStart}. */
    private static Set<String> options(String selectStart) throws Exception {
        String form = Files.readString(FORM);
        int start = form.indexOf(selectStart);
        assertThat(start).as("the form has %s", selectStart).isNotNegative();
        int end = form.indexOf("</select>", start);
        assertThat(end).isGreaterThan(start);
        Set<String> values = new TreeSet<>();
        Matcher matcher = LITERAL_OPTION.matcher(form.substring(start, end));
        while (matcher.find()) values.add(matcher.group(1));
        return values;
    }

    /** Every non-blank value of {@code field} in the shipped catalogue. */
    private static Set<String> catalogueValues(String field) throws Exception {
        JsonNode brands = new ObjectMapper().readTree(CATALOGUE.toFile());
        assertThat(brands.isArray()).isTrue();
        Set<String> values = new TreeSet<>();
        for (JsonNode brand : brands) {
            String value = brand.path(field).asText("").trim();
            if (!value.isEmpty()) values.add(value);
        }
        assertThat(values).as("the catalogue names some %s values", field).isNotEmpty();
        return values;
    }
}
