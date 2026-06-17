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
package io.github.carlos_emr.carlos.form;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.carlos_emr.carlos.form.gate.FormViewRoutes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the "Import Form Data" admin entrypoint onto the routed page and
 * prevents regressions back to the removed public JSP path after the forms
 * JSP migration.
 *
 * <p>Covers the acceptance criteria for the XML upload entrypoint fix:
 * admin navigation opens the XML upload page via the routed URL,
 * {@code /form/formXmlUpload.jsp} is no longer referenced from production
 * callers, and the upload form still POSTs to the processing action.</p>
 *
 * @since 2026-04-21
 */
@DisplayName("Form XML upload JSP migration regressions")
@Tag("unit")
@Tag("form")
@Tag("security")
class FormXmlUploadJspMigrationRegressionTest {

    private static final Path LEFT_NAV_JSPF =
            Path.of("src/main/webapp/WEB-INF/jsp/administration/leftNav.jspf");
    private static final Path ADMIN_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/admin/admin.jsp");
    private static final Path FORM_XML_UPLOAD_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/form/formXmlUpload.jsp");
    private static final Path STRUTS_FORM_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts-form.xml");

    @Test
    @DisplayName("admin leftNav should link to the routed /form/formXmlUpload page, not the removed public JSP")
    void shouldLinkToRoutedPage_fromAdminLeftNav() throws IOException {
        String jsp = Files.readString(LEFT_NAV_JSPF, StandardCharsets.UTF_8);

        assertThat(jsp).doesNotContain("/form/formXmlUpload.jsp");
        assertThat(jsp).contains("/form/formXmlUpload");
        assertThat(jsp).contains("objectName=\"_admin,_admin.eform\" rights=\"w\"");
    }

    @Test
    @DisplayName("admin.jsp should link to the routed /form/formXmlUpload page, not the removed public JSP")
    void shouldLinkToRoutedPage_fromAdminJsp() throws IOException {
        String jsp = Files.readString(ADMIN_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).doesNotContain("/form/formXmlUpload.jsp");
        assertThat(jsp).contains("/form/formXmlUpload");
        assertThat(jsp).contains("objectName=\"_admin,_admin.eform\" rights=\"w\"");
    }

    @Test
    @DisplayName("formXmlUpload page should POST uploads to the /form/xmlUpload processing action")
    void shouldPostUploads_toXmlUploadProcessingAction() throws IOException {
        String jsp = Files.readString(FORM_XML_UPLOAD_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("/form/xmlUpload");
        assertThat(jsp).contains("enctype=\"multipart/form-data\"");
        assertThat(jsp).containsPattern("(?is)<form\\b[^>]*\\bmethod\\s*=\\s*['\"]post['\"]");
        assertThat(jsp).contains("objectName=\"_admin,_admin.eform\" rights=\"w\"");
        assertThat(jsp).doesNotContain("objectName=\"_form\" rights=\"r\"");
    }

    @Test
    @DisplayName("FormViewRoutes should map legacy /form/formXmlUpload.jsp onto the routed page")
    void shouldResolveLegacyFormXmlUploadJsp_toRoutedActionPath() {
        assertThat(FormViewRoutes.resolveActionPath("/form/formXmlUpload.jsp"))
                .isEqualTo("/form/formXmlUpload");
    }

    @Test
    @DisplayName("struts form config should keep the explicit xmlUpload processing action with its internal JSP view")
    void shouldKeepXmlUploadProcessingAction_inStrutsConfig() throws IOException {
        String struts = Files.readString(STRUTS_FORM_XML, StandardCharsets.UTF_8);

        assertThat(struts).contains("<action name=\"form/xmlUpload\"");
        assertThat(struts).contains("FrmXmlUpload2Action");
        assertThat(struts).contains("/WEB-INF/jsp/form/formXmlUpload.jsp");
    }
}
