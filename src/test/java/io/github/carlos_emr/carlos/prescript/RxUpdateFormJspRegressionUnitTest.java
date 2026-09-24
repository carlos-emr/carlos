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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the drug-form update page to a CSRFGuard-compatible POST target and a POST-only,
 * write-privileged mutation (ported from CARLOS PR #2509).
 *
 * @since 2026-09-24
 */
@DisplayName("Prescription update form JSP regressions")
@Tag("unit")
@Tag("prescript")
@Tag("security")
class RxUpdateFormJspRegressionUnitTest {

    private static final Path UPDATE_FORM_JSP = Path.of("src/main/webapp/WEB-INF/jsp/rx/updateForm.jsp");

    @Test
    @DisplayName("should post to a concrete Struts URL so CSRFGuard injects its token")
    void shouldPostToConcreteStrutsUrl_forCsrfTokenInjection() throws IOException {
        String jsp = Files.readString(UPDATE_FORM_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("<form action=\"<%= request.getContextPath() %>/rx/ViewUpdateForm\" method=\"post\">");
        assertThat(jsp).doesNotContain("<form action=\"\" method=\"post\">");
    }

    @Test
    @DisplayName("should keep the drug id on the update POST and leave loading and authorising to the gate")
    void shouldPostDrugId_andRenderOnlyGateAttributes() throws IOException {
        String jsp = Files.readString(UPDATE_FORM_JSP, StandardCharsets.UTF_8);

        // The form posts to a concrete URL without a query string, so the drug id travels as a
        // hidden input; without it the POST had no id and the update failed (#3908).
        assertThat(jsp).contains("<input type=\"hidden\" name=\"id\" value=\"<carlos:encode value='<%= id %>' context=\"htmlAttribute\"/>\"/>");
        assertThat(jsp).contains("<input type=\"hidden\" name=\"action\" value=\"update\"/>");
        // The drug is loaded, authorised against its own patient and changed in ViewUpdateForm2Action;
        // the page never reads the drug by a request id itself.
        assertThat(jsp).doesNotContain("drugDao").doesNotContain("DrugDao").doesNotContain("request.getParameter(\"id\")");
        assertThat(jsp).contains("request.getAttribute(\"drugId\")").contains("request.getAttribute(\"drugForm\")");
    }
}
