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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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

    private static final Path ADD_REACTION_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "rx", "AddReaction2.jsp");

    @Test
    @DisplayName("AJAX-rendered allergy form should include its CSRF token and rendered patient context")
    void shouldRenderCsrfTokenAndPatientContext_whenAddReactionJspIsRendered() throws IOException {
        String jsp = Files.readString(ADD_REACTION_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("<%@ taglib uri=\"https://owasp.org/www-project-csrfguard/Owasp.CsrfGuard.tld\" prefix=\"csrf\" %>");
        assertThat(jsp).contains("name=\"<csrf:tokenname/>\" value=\"<csrf:tokenvalue/>\"");
        assertThat(jsp).contains("name=\"formDemographicNo\"");
        assertThat(jsp).contains("patient.getDemographicNo()");
    }

    @Test
    @DisplayName("AJAX-rendered allergy form should fail closed when the session patient is missing")
    void shouldFailClosed_whenSessionPatientIsMissing() throws IOException {
        String jsp = Files.readString(ADD_REACTION_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("if (patient == null) {");
        assertThat(jsp).contains("response.sendError(HttpServletResponse.SC_FORBIDDEN);");
        assertThat(jsp).contains("return;");
    }

    @Test
    @DisplayName("AJAX-rendered allergy form should declare only one server-rendered CSRF field")
    void shouldDeclareOnlyOneServerRenderedCsrfField_whenAddReactionJspIsRendered() throws IOException {
        String jsp = Files.readString(ADD_REACTION_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).containsOnlyOnce("name=\"<csrf:tokenname/>\" value=\"<csrf:tokenvalue/>\"");
    }
}
