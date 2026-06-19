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
package io.github.carlos_emr.carlos.form;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Frm2Action redirect construction")
@Tag("unit")
@Tag("security")
class Frm2ActionRedirectUnitTest {

    @Test
    @DisplayName("should encode form_link before redirecting to forwardname")
    void shouldEncodeFormLink_whenBuildingForwardNameRedirect() {
        String redirect = Frm2Action.forwardNameRedirectUrl(
                "/carlos",
                "formannual.jsp&demographic_no=999",
                "save?demographic_no=123&formId=456");

        assertThat(redirect).isEqualTo(
                "/carlos/form/forwardname?form_link=formannual.jsp%26demographic_no%3D999"
                        + "&demographic_no=123&formId=456");
    }

    @Test
    @DisplayName("should reject non-save forward actions")
    void shouldRejectNonSaveForwardActions() {
        assertThatThrownBy(() -> Frm2Action.forwardNameRedirectUrl(
                "/carlos",
                "formannual.jsp",
                "https://evil.example"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
