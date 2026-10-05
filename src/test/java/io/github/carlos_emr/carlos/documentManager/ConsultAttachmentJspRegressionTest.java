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
package io.github.carlos_emr.carlos.documentManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Consult attachment JSP regressions")
@Tag("unit")
@Tag("documentManager")
class ConsultAttachmentJspRegressionTest {
    private static final Path ATTACH_DOCUMENT_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "documentManager", "attachDocument.jsp");
    private static final Path CONSULT_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "encounter", "oscarConsultationRequest", "ConsultationFormRequest.jsp");
    private static final Path EFORM_FLOATING_TOOLBAR_JS =
            Path.of("src", "main", "webapp", "eform", "eformFloatingToolbar", "eform_floating_toolbar.js");

    @Test
    @DisplayName("Attachment selector should render with consult read access and disable selection without write access")
    void shouldRenderAttachmentSelectorReadOnly_withoutConsultWriteAccess() throws Exception {
        String jsp = normalizeWhitespace(Files.readString(ATTACH_DOCUMENT_JSP, StandardCharsets.UTF_8));

        assertThat(jsp)
                .contains("String attachmentSecurityObject = \"_eform\".equals(attachmentSecurityObjectRequest) ? \"_eform\" : \"_con\";")
                .contains("objectName=\"<%=attachmentSecurityObject%>\" rights=\"r\"")
                .contains("<c:set var=\"attachmentSelectionDisabled\" value=\"${canManageAttachments ne true}\"/>")
                .contains("<c:if test=\"${attachmentSelectionDisabled}\">disabled=\"disabled\"</c:if>");
    }

    @Test
    @DisplayName("Attachment selector should include demographic context on preview requests")
    void shouldIncludeDemographicContext_onPreviewRequests() throws Exception {
        String jsp = normalizeWhitespace(Files.readString(ATTACH_DOCUMENT_JSP, StandardCharsets.UTF_8));

        assertThat(jsp)
                .contains("eFormPreviewParameters")
                .contains("eFormId=${carlos:forUriComponent(eForm.id)}")
                .contains("documentPreviewParameters")
                .contains("eDocId=${carlos:forUriComponent(document.docId)}")
                .contains("labPreviewParameters")
                .contains("segmentId=${carlos:forUriComponent(lab.segmentID)}")
                .contains("labVersionPreviewParameters")
                .contains("segmentId=${carlos:forUriComponent(version.key)}")
                .contains("hrmPreviewParameters")
                .contains("hrmId=${carlos:forUriComponent(hrm['id'])}")
                .contains("formPreviewParameters")
                .contains("formName=${carlos:forUriComponent(form.formName)}")
                .contains("onclick=\"${carlos:forHtmlAttribute(eFormPreviewOnclick)}\"")
                .contains("onclick=\"${carlos:forHtmlAttribute(documentPreviewOnclick)}\"")
                .contains("onclick=\"${carlos:forHtmlAttribute(labPreviewOnclick)}\"")
                .contains("onclick=\"${carlos:forHtmlAttribute(labVersionPreviewOnclick)}\"")
                .contains("onclick=\"${carlos:forHtmlAttribute(hrmPreviewOnclick)}\"")
                .contains("onclick=\"${carlos:forHtmlAttribute(formPreviewOnclick)}\"")
                .doesNotContain("onclick=\"getPdf('EFORM'")
                .doesNotContain("onclick=\"getPdf('DOC'")
                .doesNotContain("onclick=\"getPdf('LAB'")
                .doesNotContain("onclick=\"getPdf('HRM'");
    }

    @Test
    @DisplayName("Attachment preview URLs should encode dynamic form parameters")
    void shouldEncodeDynamicParameters_inFormPreviewUrls() throws Exception {
        String consultJsp = normalizeWhitespace(Files.readString(CONSULT_JSP, StandardCharsets.UTF_8));
        String floatingToolbarJs = normalizeWhitespace(Files.readString(EFORM_FLOATING_TOOLBAR_JS, StandardCharsets.UTF_8));

        assertThat(consultJsp)
                .contains("'&formId=' + encodeURIComponent(formValue)")
                .contains("'&formName=' + encodeURIComponent(formName)")
                .contains("'&demographicNo=' + encodeURIComponent(demographicNo)");
        assertThat(floatingToolbarJs)
                .contains("'&formId=' + encodeURIComponent(formValue)")
                .contains("'&formName=' + encodeURIComponent(formName)")
                .contains("'&demographicNo=' + encodeURIComponent(demographicNo)");
    }

    @Test
    @DisplayName("Consult page should expose attachment panel without gating it on consult write")
    void shouldExposeAttachmentPanel_withConsultReadAccess() throws Exception {
        String jsp = normalizeWhitespace(Files.readString(CONSULT_JSP, StandardCharsets.UTF_8));

        assertThat(jsp)
                .contains("String consultSecurityTarget = StringUtils.isNullOrEmpty(demo) ? null : demo;")
                .contains("hasPrivilege(loggedInInfo, \"_con\", SecurityInfoManager.READ, consultSecurityTarget)")
                .contains("if (thisForm.iseReferral())")
                .contains("id=\"attachDocumentPanelBtn\"")
                .doesNotContain("if (canWriteConsult) { if (thisForm.iseReferral())")
                .doesNotContain("<security:oscarSec roleName");
    }

    @Test
    @DisplayName("Consult page should authorize the resolved request demographic before loading patient data")
    void shouldAuthorizeResolvedRequestDemographic_beforeLoadingPatientData() throws Exception {
        String jsp = normalizeWhitespace(Files.readString(CONSULT_JSP, StandardCharsets.UTF_8));
        String earlyReadCheck = "if (requestId != null && consultSecurityTarget != null && !securityInfoManager.hasPrivilege(loggedInInfo, \"_con\", SecurityInfoManager.READ, consultSecurityTarget))";
        String requestLoad = "consultUtil.estRequestFromId(loggedInInfo, requestId);";
        String canonicalizeDemo = "if (!StringUtils.isNullOrEmpty(consultUtil.demoNo)) { demo = consultUtil.demoNo; }";
        String finalReadCheck = "boolean consultReadAlreadyVerified = consultSecurityTarget != null && consultSecurityTarget.equals(verifiedConsultSecurityTarget); if (!consultReadAlreadyVerified && !securityInfoManager.hasPrivilege(loggedInInfo, \"_con\", SecurityInfoManager.READ, consultSecurityTarget))";
        String patientLoad = "demographic = demoData.getDemographic(loggedInInfo, demo);";

        int earlyReadCheckIndex = jsp.indexOf(earlyReadCheck);
        int requestLoadIndex = jsp.indexOf(requestLoad);
        int canonicalizeDemoIndex = jsp.indexOf(canonicalizeDemo);
        int finalReadCheckIndex = jsp.indexOf(finalReadCheck);
        int patientLoadIndex = jsp.indexOf(patientLoad);

        assertThat(earlyReadCheckIndex).isGreaterThanOrEqualTo(0);
        assertThat(requestLoadIndex).isGreaterThanOrEqualTo(0);
        assertThat(canonicalizeDemoIndex).isGreaterThanOrEqualTo(0);
        assertThat(finalReadCheckIndex).isGreaterThanOrEqualTo(0);
        assertThat(patientLoadIndex).isGreaterThanOrEqualTo(0);
        assertThat(earlyReadCheckIndex).isLessThan(requestLoadIndex);
        assertThat(canonicalizeDemoIndex).isGreaterThan(requestLoadIndex);
        assertThat(canonicalizeDemoIndex).isLessThan(finalReadCheckIndex);
        assertThat(finalReadCheckIndex).isLessThan(patientLoadIndex);
    }

    @Test
    @DisplayName("Consult page should not expose write-only actions in read-only mode")
    void shouldGateWriteOnlyConsultActions_withConsultWriteAccess() throws Exception {
        String jsp = normalizeWhitespace(Files.readString(CONSULT_JSP, StandardCharsets.UTF_8));

        assertThat(jsp)
                .contains("if (canWriteConsult && thisForm.geteReferralId() == null)")
                .contains("if (<%= canWriteConsult ? \"true\" : \"false\" %> && isOceanEReferral !== null")
                .contains("attachOceanAttachments();");
    }

    @Test
    @DisplayName("Consult page should disable editable fields without consult write access")
    void shouldDisableEditableConsultFields_withoutConsultWriteAccess() throws Exception {
        String jsp = normalizeWhitespace(Files.readString(CONSULT_JSP, StandardCharsets.UTF_8));

        assertThat(jsp)
                .contains("var readOnlyConsult = <%= canWriteConsult ? \"false\" : \"true\" %>;")
                .contains("var disableFields = readOnlyConsult;")
                .contains("if (readOnlyConsult) { return; }")
                .contains("if (canWriteConsult && requestId == null && demo != null")
                .contains("if (!readOnlyConsult && form.providerNo)")
                .contains("disableIfExists(document.getElementById('providerNoSelect'), disableFields);");
    }

    private static String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
