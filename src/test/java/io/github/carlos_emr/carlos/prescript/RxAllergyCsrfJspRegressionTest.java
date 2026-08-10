/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.carlos_emr.carlos.prescript;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures the allergy form remains submit-safe when it is rendered as an
 * AJAX fragment after the page-level CSRFGuard script has already initialized.
 *
 * @since 2026-08-07
 */
@DisplayName("Rx allergy CSRF JSP regression")
@Tag("unit")
@Tag("rx")
@Tag("security")
class RxAllergyCsrfJspRegressionTest {

    private static final Path ADD_REACTION_JSP = projectRoot()
            .resolve(Path.of("src", "main", "webapp", "WEB-INF", "jsp", "rx", "AddReaction2.jsp"));
    private static final String PATIENT_LOOKUP = "RxPatientData.Patient patient = "
            + "(RxPatientData.Patient) request.getSession().getAttribute(\"Patient\");";
    private static final String MISSING_PATIENT_GUARD = "if (patient == null) { "
            + "response.sendError(HttpServletResponse.SC_FORBIDDEN); return; }";

    @Test
    @DisplayName("AJAX-rendered allergy form should include its CSRF token and rendered patient context")
    void shouldRenderCsrfTokenAndPatientContext_whenAddReactionJspIsRendered() throws IOException {
        Element form = addAllergyForm();
        Elements hiddenInputs = form.select("input[type=hidden]");

        assertThat(hiddenInputs)
                .filteredOn(input -> "<csrf:tokenname/>".equals(input.attr("name")))
                .singleElement()
                .satisfies(input -> assertThat(input.attr("value")).isEqualTo("<csrf:tokenvalue/>"));
        assertThat(hiddenInputs)
                .filteredOn(input -> "formDemographicNo".equals(input.attr("name")))
                .singleElement();
    }

    @Test
    @DisplayName("AJAX-rendered allergy form should fail closed when the session patient is missing")
    void shouldFailClosed_whenSessionPatientIsMissing() throws IOException {
        String jsp = normalizeWhitespace(readAddReactionJsp());
        int patientLookup = indexOfRequired(jsp, PATIENT_LOOKUP);
        int missingPatientGuard = indexOfRequired(jsp, MISSING_PATIENT_GUARD);
        int allergyForm = indexOfRequired(jsp, "id=\"RxAddAllergyForm\"");

        assertThat(missingPatientGuard)
                .as("missing-patient guard must run immediately after loading the session patient")
                .isGreaterThan(patientLookup)
                .isLessThan(allergyForm);
    }

    @Test
    @DisplayName("AJAX-rendered allergy form should declare only one server-rendered CSRF field")
    void shouldDeclareOnlyOneServerRenderedCsrfField_whenAddReactionJspIsRendered() throws IOException {
        Elements hiddenInputs = addAllergyForm().select("input[type=hidden]");

        assertThat(hiddenInputs)
                .filteredOn(input -> "<csrf:tokenname/>".equals(input.attr("name")))
                .singleElement();
    }

    private static Element addAllergyForm() throws IOException {
        Document document = Jsoup.parse(readAddReactionJsp());
        Element form = document.selectFirst("form#RxAddAllergyForm");

        assertThat(form).as("AJAX add-allergy form").isNotNull();
        assertThat(form.attr("action")).endsWith("/rx/addAllergy2");
        assertThat(form.attr("method")).isEqualTo("post");
        return form;
    }

    private static String readAddReactionJsp() throws IOException {
        return Files.readString(ADD_REACTION_JSP, StandardCharsets.UTF_8);
    }

    private static String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static int indexOfRequired(String jsp, String token) {
        int index = jsp.indexOf(token);
        assertThat(index).as("required JSP contract: %s", token).isGreaterThanOrEqualTo(0);
        return index;
    }

    private static Path projectRoot() {
        try {
            Path location = Path.of(RxAllergyCsrfJspRegressionTest.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            Path current = Files.isRegularFile(location) ? location.getParent() : location;
            while (current != null) {
                if (Files.isRegularFile(current.resolve("src/main/webapp/WEB-INF/jsp/rx/AddReaction2.jsp"))) {
                    return current;
                }
                current = current.getParent();
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unable to resolve test class location", e);
        }
        throw new IllegalStateException("Unable to locate CARLOS EMR project root from test classpath");
    }
}
