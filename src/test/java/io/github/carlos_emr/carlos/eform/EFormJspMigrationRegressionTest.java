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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Path IMPORT_PARTIAL_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/eform/partials/import.jsp");
    private static final Path STRUTS_EFORM_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts-eform.xml");
    private static final Path STRUTS_FORM_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts-form.xml");
    private static final Path STRUTS_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts.xml");
    private static final Pattern STRUTS_ACTION_EXCLUDE_PATTERN = Pattern.compile(
            "<constant name=\"struts\\.action\\.excludePattern\" value=\"([^\"]+)\"\\s*/>");

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
    @DisplayName("addEForm results should render the internal eForm JSP directly so POST save flows do not hit the GET-only gate")
    void shouldRenderInternalShowFormJsp_whenAddEFormReturnsResults() throws IOException {
        String struts = Files.readString(STRUTS_EFORM_XML, StandardCharsets.UTF_8);
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/eform/efmshowform_data.jsp"), StandardCharsets.UTF_8);

        assertThat(struts)
                .contains("<action name=\"eform/addEForm\" class=\"io.github.carlos_emr.carlos.eform.actions.AddEForm2Action\">")
                .contains("<result name=\"close\">/WEB-INF/jsp/eform/efmshowform_data.jsp</result>")
                .contains("<result name=\"download\">/WEB-INF/jsp/eform/efmshowform_data.jsp</result>")
                .contains("<result name=\"error\">/WEB-INF/jsp/eform/efmshowform_data.jsp</result>");
        assertThat(jsp).contains("request.getParameter(\"error\") != null ? request.getParameter(\"error\") : (String) request.getAttribute(\"error\")");
    }


    @Test
    @DisplayName("admin eForm preview should resolve image placeholders through the active request context")
    void shouldUseRequestContextForImagePath_whenAdminEFormPreviewRenders() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/eform/efmshowform_data.jsp"), StandardCharsets.UTF_8);

        assertThat(jsp).contains("eForm.setImagePath(request.getContextPath());")
                .doesNotContain("eForm.setImagePath();");
    }

    @Test
    @DisplayName("saved eForm previews should resolve image placeholders through the active request context")
    void shouldResolveImagePath_whenSavedEFormPreviewRenders() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/eform/efmshowform_data.jsp"), StandardCharsets.UTF_8);

        assertThat(jsp).containsPattern(
                "(?s)eForm\\s*=\\s*new\\s+EForm\\(\\s*fdid\\s*\\);\\s*"
                        + "eForm\\.setContextPath\\(\\s*request\\.getContextPath\\(\\s*\\)\\s*\\);\\s*"
                        + "eForm\\.setOscarOPEN\\(\\s*request\\.getRequestURI\\(\\s*\\)\\s*\\);\\s*"
                        + "eForm\\.setImagePath\\(\\s*request\\.getContextPath\\(\\s*\\)\\s*\\);");
    }

    @Test
    @DisplayName("consultation request eForm links should keep using the shared saved-form route")
    void shouldUseSharedShowFormRoute_whenConsultationRequestLinksSavedEforms() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/encounter/oscarConsultationRequest/ConsultationFormRequest.jsp"),
                StandardCharsets.UTF_8);

        assertThat(jsp).contains("/eform/efmshowform_data?fdid=");
    }

    @Test
    @DisplayName("struts eForm config should keep both extensionless and legacy displayImage routes")
    void shouldKeepDisplayImageCompatibilityRoutes_whenReadingStrutsEFormConfig() throws IOException {
        String struts = Files.readString(STRUTS_EFORM_XML, StandardCharsets.UTF_8);

        assertThat(struts).contains("<action name=\"eform/displayImage\"");
        assertThat(struts).contains("<action name=\"eform/displayImage.do\"");
    }

    @Test
    @DisplayName("struts eForm config should keep the legacy Rich Text Letter template JSP compatibility route")
    void shouldKeepLegacyRichTextLetterTemplateCompatibilityRoute_whenReadingStrutsConfigs()
            throws IOException {
        String struts = Files.readString(STRUTS_EFORM_XML, StandardCharsets.UTF_8);
        String globalStruts = Files.readString(STRUTS_XML, StandardCharsets.UTF_8);
        Matcher matcher = STRUTS_ACTION_EXCLUDE_PATTERN.matcher(globalStruts);

        assertThat(struts).contains("<action name=\"eform/efmformrtl_templates\"");
        assertThat(struts).contains("<action name=\"eform/efmformrtl_templates.jsp\"");
        assertThat(matcher.find()).isTrue();

        Pattern excludePattern = Pattern.compile(matcher.group(1));
        assertThat(excludePattern.matcher("/eform/efmformrtl_templates.jsp").matches()).isFalse();
        assertThat(excludePattern.matcher("/carlos/eform/efmformrtl_templates.jsp").matches()).isFalse();
        assertThat(excludePattern.matcher("/eform/other.jsp").matches()).isTrue();
    }

    @Test
    @DisplayName("struts form config should forward only to internal WEB-INF views, not invented WEB-INF .do routes")
    void strutsFormConfigShouldNotForwardToWebInfDoRoutes() throws IOException {
        String struts = Files.readString(STRUTS_FORM_XML, StandardCharsets.UTF_8);

        assertThat(struts).doesNotContainPattern("/WEB-INF/jsp/form/[^<\"]+\\.do");
        assertThat(struts).contains("<action name=\"form/xmlUpload\"");
        assertThat(struts).contains("<action name=\"form/formname\"");
    }

    @Test
    @DisplayName("import partial should encode import and action errors")
    void shouldEncodeImportErrors_whenRenderingUploadMetadata() throws IOException {
        String jsp = Files.readString(IMPORT_PARTIAL_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>");
        assertThat(jsp).contains("<carlos:encode value='<%= error %>' context=\"html\"/>");
        assertThat(jsp).contains("<carlos:encode value='<%= importError %>' context=\"html\"/>");
        assertThat(jsp).doesNotContain("<li><%= error %></li>");
        assertThat(jsp).doesNotContain("<%=importError%>");
    }
}
