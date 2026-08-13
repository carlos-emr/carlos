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

    private static final Path CONSULT_JSP = Path.of(
            "src", "main", "webapp", "WEB-INF", "jsp", "encounter", "oscarConsultationRequest",
            "ConsultationFormRequest.jsp");

    @Test
    @DisplayName("Editing the visible service text should re-resolve the hidden service id")
    void shouldResyncHiddenServiceId_whenVisibleServiceTextChanges() throws Exception {
        String jsp = Files.readString(CONSULT_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("jQuery('#serviceInput').on('input', function() {")
                .contains("var typed = jQuery(this).val().trim().toLowerCase();")
                .contains("if (allServicesData[i].serviceDesc.toLowerCase() === typed) {")
                .contains("matchedId = allServicesData[i].serviceId;")
                .contains("jQuery('#service').val(matchedId);");
    }

    @Test
    @DisplayName("The submit guard should read the posted service field and block a blank value")
    void shouldBlockSubmission_whenPostedServiceValueIsBlank() throws Exception {
        String jsp = Files.readString(CONSULT_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("var serviceElement = document.EctConsultationFormRequest2Form.service;")
                .contains("if (serviceValue === '') {")
                .doesNotContain("if (serviceElement.options.selectedIndex");
    }

    @Test
    @DisplayName("Re-resolving the hidden id should not trigger the destructive service-change cascade")
    void shouldNotClearSpecialistFields_whenServiceTextIsEdited() throws Exception {
        String jsp = Files.readString(CONSULT_JSP, StandardCharsets.UTF_8);

        int handlerStart = jsp.indexOf("jQuery('#serviceInput').on('input', function() {");
        assertThat(handlerStart).as("the input handler must exist").isGreaterThan(-1);

        String handlerBody = jsp.substring(handlerStart, jsp.indexOf("});", handlerStart));

        // onServiceSelected() wipes specialist, phone, fax, address and annotation. Calling it
        // per keystroke would destroy data the clinician already entered.
        assertThat(handlerBody).doesNotContain("onServiceSelected(");
    }
}
