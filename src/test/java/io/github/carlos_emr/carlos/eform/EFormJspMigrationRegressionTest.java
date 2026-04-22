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
package io.github.carlos_emr.carlos.eform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the migrated eForm callers onto Struts-backed endpoints and prevents
 * regressions back to dead or publicly reachable JSP routes.
 *
 * @since 2026-04-15
 */
@DisplayName("EForm JSP migration regressions")
@Tag("unit")
@Tag("eform")
@Tag("security")
class EFormJspMigrationRegressionTest {

    private static final Path PATIENT_FORM_LIST_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/eform/efmpatientformlist.jsp");
    private static final Path STRUTS_EFORM_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts-eform.xml");
    private static final Path STRUTS_FORM_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts-form.xml");

    @Test
    @DisplayName("patient eForm list should not reference the missing PHR action and should keep live view/delete actions")
    void patientEFormListShouldNotReferenceMissingPhrAction() throws IOException {
        String jsp = Files.readString(PATIENT_FORM_LIST_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).doesNotContain("efmpatientformlistSendPhrAction.jsp");
        assertThat(jsp).doesNotContain("id=\"sendToPhrForm\"");
        assertThat(jsp).contains("efmshowform_data?fdid=");
        assertThat(jsp).contains("/eform/removeEForm");
    }

    @Test
    @DisplayName("struts eForm config should forward only to internal WEB-INF views, not invented WEB-INF .do routes")
    void strutsEFormConfigShouldNotForwardToWebInfDoRoutes() throws IOException {
        String struts = Files.readString(STRUTS_EFORM_XML, StandardCharsets.UTF_8);

        assertThat(struts).doesNotContainPattern("/WEB-INF/jsp/eform/[^<\"]+\\.do");
        assertThat(struts).contains("<action name=\"eform/efmshowform_data\"");
        assertThat(struts).contains("<action name=\"eform/efmformadd_data\"");
    }

    @Test
    @DisplayName("struts eForm config should keep both extensionless and legacy displayImage routes")
    void shouldKeepDisplayImageCompatibilityRoutes_whenReadingStrutsEFormConfig() throws IOException {
        String struts = Files.readString(STRUTS_EFORM_XML, StandardCharsets.UTF_8);

        assertThat(struts).contains("<action name=\"eform/displayImage\"");
        assertThat(struts).contains("<action name=\"eform/displayImage.do\"");
    }

    @Test
    @DisplayName("struts form config should forward only to internal WEB-INF views, not invented WEB-INF .do routes")
    void strutsFormConfigShouldNotForwardToWebInfDoRoutes() throws IOException {
        String struts = Files.readString(STRUTS_FORM_XML, StandardCharsets.UTF_8);

        assertThat(struts).doesNotContainPattern("/WEB-INF/jsp/form/[^<\"]+\\.do");
        assertThat(struts).contains("<action name=\"form/xmlUpload\"");
        assertThat(struts).contains("<action name=\"form/formname\"");
    }
}
