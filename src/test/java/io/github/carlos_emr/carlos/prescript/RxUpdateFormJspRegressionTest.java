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
 * Locks prescription update-form markup to a CSRFGuard-compatible POST target.
 *
 * @since 2026-05-30
 */
@DisplayName("Prescription update form JSP regressions")
@Tag("unit")
@Tag("prescript")
@Tag("security")
class RxUpdateFormJspRegressionTest {

    private static final Path UPDATE_FORM_JSP =
        Path.of("src/main/webapp/WEB-INF/jsp/rx/updateForm.jsp");

    @Test
    @DisplayName("update form should POST to a concrete Struts URL for CSRF token injection")
    void shouldPostToConcreteStrutsUrl_forCsrfTokenInjection() throws IOException {
        String jsp = Files.readString(UPDATE_FORM_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("request.getContextPath()");
        assertThat(jsp).contains("/rx/ViewUpdateForm");
        assertThat(jsp).contains("method=\"post\"");
        assertThat(jsp).doesNotContain("<form action=\"\" method=\"post\">");
    }
}
