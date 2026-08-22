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
import java.util.List;
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
    private static final Path UPLOAD_PARTIAL_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/eform/partials/upload.jsp");
    private static final Path IMPORT_PARTIAL_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/eform/partials/import.jsp");
    private static final Path EFM_FORM_MANAGER_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/eform/efmformmanager.jsp");
    private static final Path EFM_TOP_NAV_JSPF =
            Path.of("src/main/webapp/WEB-INF/jsp/eform/efmTopNav.jspf");
    private static final Path EFM_FORM_MANAGER_EDIT_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/eform/efmformmanageredit.jsp");
    private static final Path STRUTS_EFORM_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts-eform.xml");
    private static final Path STRUTS_FORM_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts-form.xml");
    private static final Path STRUTS_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts.xml");
    private static final Path RTL_ATTACHMENT_ROUTE_FIX_SQL =
            Path.of("database/mysql/updates/update-2026-06-29-rtl-attachment-route-fix.sql");
    private static final Path WEB_XML =
            Path.of("src/main/webapp/WEB-INF/web.xml");
    private static final Path EFORM_FAX_MISSING_CONTENT_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/fax/EFormMissingContent.jsp");
    private static final Pattern STRUTS_ACTION_EXCLUDE_PATTERN = Pattern.compile(
            "<constant name=\"struts\\.action\\.excludePattern\" value=\"([^\"]+)\"\\s*/>");

    @Test
    @DisplayName("should keep the legacy render URL mapped to the renamed browser render page servlet")
    void shouldKeepLegacyRenderUrl_mappedToBrowserRenderPageServlet() throws IOException {
        // The retained /EFormViewForPdfGenerationServlet URL is what the loopback render
        // navigation, the LoginFilter/CSRF exclusions, and the Struts exclude pattern all key on;
        // each of those is tested individually, but only this pin closes the loop on the
        // servlet-name -> renamed-class mapping itself.
        String webXml = Files.readString(WEB_XML, StandardCharsets.UTF_8);

        assertThat(webXml).containsSubsequence(
                "<servlet-name>EFormViewForPdfGenerationServlet</servlet-name>",
                "<servlet-class>io.github.carlos_emr.carlos.eform.util.EFormBrowserRenderPageServlet</servlet-class>");
        assertThat(webXml).containsSubsequence(
                "<servlet-name>EFormViewForPdfGenerationServlet</servlet-name>",
                "<url-pattern>/EFormViewForPdfGenerationServlet</url-pattern>");
    }

    @Test
    @DisplayName("incomplete eForm fax approval should show progress and prevent duplicate submission")
    void shouldPreventDuplicateSubmission_whenApprovingIncompleteEFormFax() throws IOException {
        String jsp = Files.readString(EFORM_FAX_MISSING_CONTENT_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("id=\"approve-incomplete-eform-fax\"")
                .contains("form.dataset.submitting === \"true\"")
                .contains("submit.disabled = true")
                .contains("spinner-border-sm")
                .contains("<output id=\"approve-incomplete-eform-fax-status\"")
                .contains("fax.eformMissingContent.btnPreparingFax")
                .contains("aria-live=\"polite\"")
                .contains("id=\"cancel-incomplete-eform-fax\"")
                .contains("name=\"method\" value=\"cancelStagedEFormFax\"")
                .doesNotContainPattern("(?s)\\b(?:window\\s*\\.\\s*)?history\\s*\\.\\s*back\\s*\\(");
    }

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
                "(?s)eForm = new EForm\\(fdid\\);\\s*"
                        + "eForm\\.setContextPath\\(request\\.getContextPath\\(\\)\\);\\s*"
                        + "eForm\\.setOscarOPEN\\(request\\.getRequestURI\\(\\)\\);\\s*"
                        + "eForm\\.setImagePath\\(request\\.getContextPath\\(\\)\\);");
    }

    @Test
    @DisplayName("consultation request eForm links should keep using the shared saved-form route")
    void shouldUseSharedShowFormRoute_whenConsultationRequestLinksSavedEforms() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/encounter/oscarConsultationRequest/ConsultationFormRequest.jsp"),
                StandardCharsets.UTF_8);

        assertThat(jsp).contains("/eform/efmshowform_data?fdid=");
    }

    @Test
    @DisplayName("eForm admin nav should use a Bootstrap button dropdown for Create eForm")
    void shouldUseBootstrapButtonDropdown_whenRenderingEFormTopNav() throws IOException {
        String jsp = Files.readString(EFM_TOP_NAV_JSPF, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("<button type=\"button\"")
                .contains("data-bs-toggle=\"dropdown\"")
                .contains("aria-haspopup=\"true\"")
                .contains("aria-expanded=\"false\"")
                .contains("<fmt:message key=\"eform.create\"/>")
                .doesNotContain("Create eForm")
                .doesNotContain("<a href=\"javascript:void(0);\" class=\"dropdown-toggle\"");
    }

    @Test
    @DisplayName("eForm editor save should return the popup to the library without navigating the main CARLOS window")
    void shouldRedirectCurrentWindow_whenAdminEditorSaveSucceeds() throws IOException {
        String jsp = Files.readString(EFM_FORM_MANAGER_EDIT_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("window.location.href = '<%=request.getContextPath()%>/eform/efmformmanager';")
                .doesNotContain("window.opener.location.href = '<%=request.getContextPath()%>/administration?show=Forms';");
    }

    @Test
    @DisplayName("eForm uploads and imports should preserve explicit schedule navigation")
    void shouldPreserveScheduleNavigation_throughEFormUploadAndImport() throws IOException {
        String manager = Files.readString(EFM_FORM_MANAGER_JSP, StandardCharsets.UTF_8);
        String upload = Files.readString(UPLOAD_PARTIAL_JSP, StandardCharsets.UTF_8);
        String importJsp = Files.readString(IMPORT_PARTIAL_JSP, StandardCharsets.UTF_8);

        assertThat(manager)
                .contains("/eform/partials/upload${param.scheduleNav eq '1' ? '?scheduleNav=1' : ''}")
                .contains("/eform/partials/import${param.scheduleNav eq '1' ? '?scheduleNav=1' : ''}");

        for (String partial : List.of(upload, importJsp)) {
            assertThat(partial)
                    .contains("<c:if test=\"${param.scheduleNav eq '1'}\">")
                    .contains("<input type=\"hidden\" name=\"scheduleNav\" value=\"1\"")
                    .contains("/administration?show=Forms${param.scheduleNav eq '1' ? '&scheduleNav=1' : ''}")
                    .doesNotContain("/administration?show=Forms&scheduleNav=1\"");
        }
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
    @DisplayName("struts global config should let rendering servlet routes reach web.xml mappings")
    void shouldExcludeRenderingServletRoutes_whenReadingStrutsGlobalConfig() throws IOException {
        String globalStruts = Files.readString(STRUTS_XML, StandardCharsets.UTF_8);
        Matcher matcher = STRUTS_ACTION_EXCLUDE_PATTERN.matcher(globalStruts);

        assertThat(matcher.find()).isTrue();

        Pattern excludePattern = Pattern.compile(matcher.group(1));
        assertThat(excludePattern.matcher("/imageRenderingServlet").matches()).isTrue();
        assertThat(excludePattern.matcher("/carlos/imageRenderingServlet").matches()).isTrue();
        assertThat(excludePattern.matcher("/EFormSignatureViewForPdfGenerationServlet").matches()).isTrue();
        assertThat(excludePattern.matcher("/carlos/EFormSignatureViewForPdfGenerationServlet").matches()).isTrue();
        assertThat(excludePattern.matcher("/contentRenderingServlet/document/1").matches()).isTrue();
        assertThat(excludePattern.matcher("/carlos/contentRenderingServlet/document/1").matches()).isTrue();

        // The signature-control library page must reach Struts (its compatibility-alias action),
        // not be excluded as a static .jsp — otherwise legacy eForms 404 loading the signature pad.
        assertThat(excludePattern.matcher("/library/eforms/signatureControl.jsp").matches()).isFalse();
        assertThat(excludePattern.matcher("/carlos/library/eforms/signatureControl.jsp").matches()).isFalse();
        // A different library .jsp stays excluded.
        assertThat(excludePattern.matcher("/library/eforms/other.jsp").matches()).isTrue();
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
    @DisplayName("Rich Text Letter attachment migration should use gated routes and saved hidden inputs")
    void shouldUseGatedAttachmentRoutesAndSavedHiddenInputs_whenUpdatingRichTextLetterTemplate()
            throws IOException {
        String sql = Files.readString(RTL_ATTACHMENT_ROUTE_FIX_SQL, StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("../eform/attachEform.jsp")
                .contains("../eform/attachEform")
                .contains("../eform/displayAttachedFiles.jsp")
                .contains("../eform/displayAttachedFiles")
                .contains("document.getElementById(\"fdid\")")
                .contains("document.getElementById(\"demographicNo\")")
                .contains("gup(\"fid\")")
                .contains("gup(\"demographic_no\")");
    }

    @Test
    @DisplayName("attachment popup checkboxes should expose accessible names through aria-labelledby")
    void shouldExposeAccessibleAttachmentCheckboxNames_whenRenderingAttachPopup() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/eform/attachEform.jsp"), StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("SafeEncode.forHtmlAttribute(labLabelId + \" \" + labDateId)")
                .contains("SafeEncode.forHtmlAttribute(labVersionLabelId + \" \" + labVersionDateId)")
                .contains("SafeEncode.forHtmlAttribute(hrmLabelId + \" \" + hrmDateId)")
                .contains("SafeEncode.forHtmlAttribute(eformLabelId)")
                .contains("SafeEncode.forHtmlAttribute(formLabelId + \" \" + formDateId)");
    }

    @Test
    @DisplayName("attachment popup should preserve already-attached older encounter form revisions")
    void shouldPreserveAlreadyAttachedOlderEncounterForms_whenRenderingAttachPopup() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/eform/attachEform.jsp"), StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("getEncounterFormsbyDemographicNumber(loggedInInfo, demographicNo, false, true)")
                .contains("getEncounterFormsbyDemographicNumber(loggedInInfo, demographicNo, true, true)")
                .contains("List<EctFormData.PatientForm> attachedOlderForms = new ArrayList<>()")
                .contains("!currentFormIds.contains(attachedFormId)")
                .contains("allForms.isEmpty() && attachedOlderForms.isEmpty()")
                .contains("Earlier version");
    }

    @Test
    @DisplayName("attachment popup should HTML-attribute encode generated ids and values")
    void shouldEncodeAttachmentCheckboxAttributes_whenRenderingAttachPopup() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/eform/attachEform.jsp"), StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("SafeEncode.forHtmlAttribute(documentCheckboxId)")
                .contains("SafeEncode.forHtmlAttribute(documentId)")
                .contains("SafeEncode.forHtmlAttribute(labCheckboxId)")
                .contains("SafeEncode.forHtmlAttribute(labVersionCheckboxId)")
                .contains("SafeEncode.forHtmlAttribute(hrmCheckboxId)")
                .contains("SafeEncode.forHtmlAttribute(eformCheckboxId)")
                .contains("SafeEncode.forHtmlAttribute(formCheckboxId)");
    }

    @Test
    @DisplayName("attached file sidebar should gate category lookups by category privileges")
    void shouldGateAttachedSidebarLookupsByCategoryPrivilege_whenRenderingDisplayAttachedFiles() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/eform/displayAttachedFiles.jsp"), StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("hasPrivilege(loggedInInfo, \"_edoc\", \"r\", null)")
                .contains("hasPrivilege(loggedInInfo, \"_lab\", \"r\", null)")
                .contains("hasPrivilege(loggedInInfo, \"_hrm\", \"r\", null)")
                .contains("hasPrivilege(loggedInInfo, \"_eform\", \"r\", null)")
                .contains("hasPrivilege(loggedInInfo, \"_form\", \"r\", null)")
                .contains("Collections.emptyList()");
    }

    @Test
    @DisplayName("admin nav Create eForm dropdown should use a button element with aria-expanded and proper Bootstrap 5 nav-item structure")
    void shouldUseButtonToggle_forCreateEFormDropdown() throws IOException {
        String nav = Files.readString(EFM_TOP_NAV_JSPF, StandardCharsets.UTF_8);

        assertThat(nav)
            .containsSubsequence(
                "<li class=\"nav-item dropdown\">",
                "<button type=\"button\"",
                "class=\"contentLink nav-link dropdown-toggle\"",
                "data-bs-toggle=\"dropdown\"",
                "aria-haspopup=\"true\"",
                "aria-expanded=\"false\"",
                "<fmt:message key=\"eform.create\"/>")
            .doesNotContain("javascript:void(0)")
            .doesNotContain("Create eForm");
    }

    @Test
    @DisplayName("eForm editor should navigate current window to eForm library after save, not the opener window")
    void shouldNavigateCurrentWindow_afterSave() throws IOException {
        String jsp = Files.readString(EFM_FORM_MANAGER_EDIT_JSP, StandardCharsets.UTF_8);

        // window.opener.location navigates the main CARLOS window (opener of the admin popup),
        // which would replace Schedule/Search/Inbox with the admin page — "losing the Carlos menu"
        assertThat(jsp)
            .doesNotContain("window.opener.location.href")
            .doesNotContain("window.opener.location")
            .contains("window.location.href");
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

    @Test
    @DisplayName("upload partial JS strings containing localized messages should use carlos:forJavaScript so neither apostrophes nor double quotes in any locale break JavaScript")
    void shouldUseJavaScriptEncoding_forLocalizedMessagesInCheckFormAndDisable() throws IOException {
        // Two locales expose two different failure modes if messages are placed raw in JS strings:
        //   Polish (msgFileMissing): kliknąć przycisk "Prześlij" — double quotes break a double-quoted string
        //   French (msgFileMissing): Veuillez d'abord... — apostrophe breaks a single-quoted string
        // The safe solution for both is to capture the message into a JSTL var and encode it with
        // ${carlos:forJavaScript(var)}, which escapes backslashes, quotes, and control characters.
        String jsp = Files.readString(UPLOAD_PARTIAL_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
            .doesNotContain("alert(\"<fmt:message")
            .doesNotContain("alert('<fmt:message")
            .doesNotContain(".subm.value = \"<fmt:message")
            .doesNotContain(".subm.value = '<fmt:message")
            .contains("<fmt:message key=\"eform.uploadhtml.msgFileMissing\" var=")
            .contains("<fmt:message key=\"eform.uploadimages.processing\" var=")
            .containsPattern("alert\\(\"\\$\\{carlos:forJavaScript\\([^)]+\\)\\}\"\\)")
            .containsPattern("\\.subm\\.value = \"\\$\\{carlos:forJavaScript\\([^)]+\\)\\}\"");
    }

    @Test
    @DisplayName("eForm host pages should load DOMPurify ahead of jQuery so the editor's sanitize gate works")
    void shouldLoadDomPurifyBeforeJquery_onEformHostPages() throws IOException {
        // editControl2.js routes every innerHTML write through sanitizeHtml(), which returns null
        // when DOMPurify is absent and falls back to textContent. The clinician then sees their
        // saved letter as escaped markup, and the NEXT save stores it double-encoded — silent
        // corruption of stored clinical content.
        //
        // Position is load-bearing and counter-intuitive: addHeadJavascript PREPENDS ("For
        // Javascript: First is last"), so the call listed AFTER jQuery is emitted BEFORE it.
        // Sorting this block alphabetically would reintroduce the corruption, so pin the order.
        for (Path hostPage : List.of(
                Path.of("src/main/webapp/WEB-INF/jsp/eform/efmshowform_data.jsp"),
                Path.of("src/main/webapp/WEB-INF/jsp/eform/efmformadd_data.jsp"))) {
            String jsp = Files.readString(hostPage, StandardCharsets.UTF_8);

            assertThat(jsp)
                    .as("%s must load DOMPurify", hostPage)
                    .contains("/library/dompurify/purify.min.js");
            assertThat(jsp.indexOf("/library/jquery/jquery-3.7.1.min.js"))
                    .as("%s: DOMPurify must be registered after jQuery, so it is emitted before it",
                            hostPage)
                    .isLessThan(jsp.indexOf("/library/dompurify/purify.min.js"));
        }
    }
}
