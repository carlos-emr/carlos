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
package io.github.carlos_emr.carlos.form.gate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The chart's Forms menu opens BC-WCB through {@link FormViewRoutes#resolveActionPath}: the BC seed registers the form
 * under its old {@code .do} address, and the action is extensionless now (#4965).
 *
 * @since 2026-10-18
 */
@DisplayName("FormViewRoutes BC-WCB link")
@Tag("unit")
@Tag("form")
class FormViewRoutesWcbUnitTest {

    @Test
    @DisplayName("should resolve the BC seed's BC-WCB link to the extensionless route")
    void shouldResolveBcSeededWcbLink_toExtensionlessRoute() {
        // The BC seed's encounterForm row for BC-WCB; the chart appends the patient, formId=0 and provNo.
        assertThat(FormViewRoutes.resolveActionPath("../billing/CA/BC/viewformwcb.do?demographic_no="))
                .isEqualTo("/billing/CA/BC/viewformwcb?demographic_no=");
        assertThat(FormViewRoutes.resolveActionPath("/billing/CA/BC/viewformwcb.do"))
                .isEqualTo("/billing/CA/BC/viewformwcb");
    }

    @Test
    @DisplayName("should leave other billing addresses alone")
    void shouldReturnNull_forOtherBillingPaths() {
        // Only the seeded form value is translated; the WCB form list and other billing pages are not forms.
        assertThat(FormViewRoutes.resolveActionPath("../billing/CA/BC/ViewWcbForms.do?demographic_no=")).isNull();
        assertThat(FormViewRoutes.resolveActionPath("/billing/CA/BC/viewformwcb.jsp")).isNull();
    }
}
