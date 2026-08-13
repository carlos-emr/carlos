/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the client-side service-selection contract on the consultation request form.
 *
 * <p>The posted service is the hidden {@code #service} input, not the visible
 * {@code #serviceInput} autocomplete the clinician actually reads. Keeping those two in step is
 * what makes the blank-service submit guard trustworthy, so the wiring is asserted here rather
 * than left to a browser-only check. See issue #2241.</p>
 *
 * @since 2026-08-13
 */
@DisplayName("Consultation form service selection JSP regressions")
@Tag("unit")
@Tag("consultation")
class ConsultationFormServiceSelectionJspRegressionTest {

    private static final int MAX_PARENT_SEARCH_DEPTH = 5;
    private static final Path CONSULT_JSP_RELATIVE = Path.of(
            "src/main/webapp/WEB-INF/jsp/encounter/oscarConsultationRequest/ConsultationFormRequest.jsp");
    private static final Path CONSULT_JSP = resolveProjectPath(CONSULT_JSP_RELATIVE);

    @Test
    @DisplayName("Editing the visible service text should re-resolve the hidden service id")
    void shouldResyncHiddenServiceId_whenVisibleServiceTextChanges() throws Exception {
        String jsp = Files.readString(CONSULT_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("jQuery('#serviceInput').on('input', function() {")
                .contains("var typed = jQuery(this).val().trim().toLowerCase();")
                .contains("var description = (allServicesData[i].serviceDesc || '').trim().toLowerCase();")
                .contains("if (description === typed) {")
                .contains("matchedId = allServicesData[i].serviceId;")
                .contains("jQuery('#service').val(matchedId);");
    }

    @Test
    @DisplayName("The normal interactive form should block a blank posted service value")
    void shouldBlockSubmission_whenPostedServiceValueIsBlank() throws Exception {
        String jsp = Files.readString(CONSULT_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("var serviceElement = document.EctConsultationFormRequest2Form.service;")
                .contains("if (serviceElement && !isEReferral) {")
                .contains("if (serviceValue === '') {")
                .doesNotContain("if (serviceOptionsElement && serviceOptionsElement.selectedIndex == 0) {");
    }

    @Test
    @DisplayName("An eReferral should allow its service metadata to be blank")
    void shouldAllowBlankService_whenConsultationIsEReferral() throws Exception {
        String jsp = Files.readString(CONSULT_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("var isEReferral = document.getElementById('isOceanEReferral') !== null;")
                .contains("if (!isEReferral && typeof checkFormHCT === \"function\") {")
                .contains("if (serviceElement && !isEReferral) {");
    }

    @Test
    @DisplayName("Typing a different exact service should clear consultant-dependent fields")
    void shouldClearSpecialistFields_whenTypedServiceResolvesToDifferentId() throws Exception {
        String jsp = Files.readString(CONSULT_JSP, StandardCharsets.UTF_8);

        int handlerStart = jsp.indexOf("jQuery('#serviceInput').on('input', function() {");
        assertThat(handlerStart).as("the input handler must exist").isGreaterThan(-1);

        String handlerBody = jsp.substring(handlerStart, jsp.indexOf("});", handlerStart));

        // Partial/unknown text leaves matchedId blank, so dependent fields survive intermediate
        // keystrokes. A different exact service is a real selection change and must clear them.
        assertThat(handlerBody)
                .contains("var previousServiceId = lastResolvedServiceId;")
                .contains("if (matchedId !== '') {")
                .contains("if (String(matchedId) !== String(previousServiceId || '')) {")
                .contains("onServiceSelected(matchedId);")
                .doesNotContain("lastResolvedServiceId = String(matchedId);");

        assertThat(jsp)
                .contains("function onServiceSelected(serviceId) {")
                .contains("lastResolvedServiceId = String(serviceId || '');");
    }

    /**
     * Resolves a project fixture from the compiled test location. This remains stable when an IDE
     * or a direct Surefire invocation uses a working directory outside the repository.
     */
    private static Path resolveProjectPath(Path relativePath) {
        try {
            Path location = Path.of(ConsultationFormServiceSelectionJspRegressionTest.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            Path current = Files.isRegularFile(location) ? location.getParent() : location;
            for (int depth = 0; depth <= MAX_PARENT_SEARCH_DEPTH && current != null; depth++) {
                Path candidate = current.resolve(relativePath);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
                current = current.getParent();
            }
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException("Unable to resolve consultation test class location", e);
        }
        throw new IllegalStateException("Unable to locate project file: " + relativePath);
    }
}
