/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.tickler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When {@code ForwardDemographicTickler2Action} drops a patient the forwarding document is not
 * theirs (#4839), {@code ticklerAdd.jsp} must not fill the form from the {@code demographic_no}
 * parameter anyway, and must say plainly that a patient has to be chosen.
 */
@DisplayName("ticklerAdd.jsp dropped document patient regressions")
@Tag("unit")
@Tag("tickler")
class TicklerAddDocumentPatientJspRegressionTest {

    private static final Path TICKLER_ADD_JSP = Path.of("src/main/webapp/WEB-INF/jsp/tickler/ticklerAdd.jsp");
    private static final String KEY = "tickler.ticklerAdd.msgChoosePatientForDocument";

    @Test
    @DisplayName("should read the patient parameter only through the dropped-patient guard")
    void shouldReadPatientParameter_onlyThroughDroppedPatientGuard() throws IOException {
        String source = Files.readString(TICKLER_ADD_JSP, StandardCharsets.UTF_8);

        assertThat(source).contains("request.getAttribute(ForwardDemographicTickler2Action.PATIENT_DROPPED_FOR_DOCUMENT)");
        // A dropped document patient is never read; otherwise the patient comes only from the
        // patient-lock check (#5067), which reads demographic_no / demographicNo itself.
        assertThat(source).containsPattern("String requestDemographicNo = null;\\s*if \\(!patientDroppedForDocument\\) \\{"
                + "[^}]*requestDemographicNo = PatientReadAccess\\.authorizedOptionalPatient\\(request,");
        // No raw read of the parameter remains: the patient lookup, the hidden demographic_no
        // input and the refresh broadcast all use the guarded value.
        assertThat(source).doesNotContain("request.getParameter(\"demographic_no\")");
        assertThat(source).contains("demographicDao.getDemographic(requestDemographicNo)");
        assertThat(source).contains("String demoNoParam = requestDemographicNo;");
        assertThat(source).contains("requestDemographicNo != null ? requestDemographicNo : \"0\"");
    }

    @Test
    @DisplayName("should tell the user to choose a patient when the document's patient was dropped")
    void shouldShowChoosePatientMessage_whenPatientWasDropped() throws IOException {
        String source = Files.readString(TICKLER_ADD_JSP, StandardCharsets.UTF_8);

        assertThat(source).containsPattern("<% if \\(patientDroppedForDocument\\) \\{ %>\\s*<tr>\\s*<td colspan=\"2\">\\s*"
                + "<div id=\"choosePatientForDocument\" class=\"alert alert-warning\" role=\"alert\">"
                + "<fmt:message key=\"" + KEY.replace(".", "\\.") + "\"/></div>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr", "es", "pt_BR", "pl"})
    @DisplayName("should translate the choose-patient message in every shipped locale")
    void shouldTranslateChoosePatientMessage_forEveryLocale(String locale) throws IOException {
        String english = bundle("en").getProperty(KEY);
        String value = bundle(locale).getProperty(KEY);

        assertThat(english).isEqualTo("No patient was filled in from this document. Choose the patient this tickler is for.");
        assertThat(value).isNotBlank();
        if (!"en".equals(locale)) {
            assertThat(value).isNotEqualTo(english);
        }
    }

    private static Properties bundle(String locale) throws IOException {
        Properties properties = new Properties();
        // The bundles are plain ASCII with Unicode escapes (OscarResourcesBundleParseTest pins that).
        try (InputStream in = Files.newInputStream(
                Path.of("src/main/resources/oscarResources_" + locale + ".properties"))) {
            properties.load(in);
        }
        return properties;
    }
}
