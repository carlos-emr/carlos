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
package io.github.carlos_emr.carlos.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for JSP JavaScript-context encoding regressions.
 *
 * @since 2026-05-19
 */
@DisplayName("JSP JavaScript encoding")
@Tag("unit")
class JspJavaScriptEncodingRegressionTest {
    private static final String BASEDIR_PROPERTY = "basedir";
    private static final Path JSP_ROOT = resolveProjectPath(Path.of("src/main/webapp/WEB-INF/jsp"));
    private static final String SAFE_ENCODE_IMPORT_PATTERN =
            "<%@\\s*page\\s+import\\s*=\\s*\"io\\.github\\.carlos_emr\\.carlos\\.utility\\.SafeEncode\"\\s*%>";
    private static final String SAFE_TEXTAREA_RENDER_PATTERN =
            "out\\.println\\(\\s*SafeEncode\\.forHtml\\(\\s*aline\\s*\\)\\s*\\);";
    private static final String RAW_TEXTAREA_RENDER_PATTERN = "out\\.println\\(\\s*aline\\s*\\);";

    @Test
    void shouldContainEncodedSessionValues_inJavaScriptStrings() throws Exception {
        String sentJsp = readJsp("mcedt/mailbox/sent.jsp");
        String autoDownloadJsp = readJsp("mcedt/mailbox/autoDownload.jsp");

        assertThat(sentJsp)
                .doesNotContain("'<%= session.getAttribute(\"info\") %>'")
                .contains("SafeEncode.forJavaScript(session.getAttribute(\"info\") == null ? null : session.getAttribute(\"info\").toString())");
        assertThat(autoDownloadJsp)
                .doesNotContain("'<%= session.getAttribute(\"resourceID\") %>'")
                .contains("SafeEncode.forJavaScript(")
                .contains("session.getAttribute(\"resourceID\")");
    }

    @Test
    void shouldEncodeCurrentProgram_inDemographicPaperArchiveJavaScriptString() throws Exception {
        String editJsp = readJsp("demographic/edit.jsp");

        assertThat(editJsp)
                .containsPattern(SAFE_ENCODE_IMPORT_PATTERN)
                .contains("jQuery(\"#paper_chart_archived_program\").val('<%=SafeEncode.forJavaScript(currentProgram)%>');")
                .doesNotContain("jQuery(\"#paper_chart_archived_program\").val('<%=currentProgram%>');");
    }

    @Test
    void shouldContainEncodedInlineHandlers_inJavaScriptAttributeContext() throws Exception {
        String chartNotesJsp = readJsp("casemgmt/ChartNotesAjax.jsp");
        String multiPageJsp = readJsp("documentManager/MultiPageDocDisplay.jsp");
        String documentReportJsp = readJsp("documentManager/documentReport.jsp");
        String demographicSwipeJsp = readJsp("demographic/demographicswipe.jsp");

        assertThat(chartNotesJsp)
                .doesNotContain("SafeEncode.forHtml(((NoteDisplayNonNote) note).getLinkInfo())")
                .contains("SafeEncode.forJavaScriptAttribute(((NoteDisplayNonNote) note).getLinkInfo())");
        assertThat(multiPageJsp)
                .doesNotContain("firstPage('<%=docId%>')")
                .doesNotContain("prevPage('<%=docId%>')")
                .doesNotContain("nextPage('<%=docId%>')")
                .doesNotContain("lastPage('<%=docId%>')")
                .doesNotContain("checkSave('<%=docId%>')")
                .contains("context='javaScriptAttribute'");
        assertThat(documentReportJsp)
                .doesNotContain("'<%=url%>'")
                .contains("context='javaScriptAttribute'");
        assertThat(demographicSwipeJsp)
                .contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>")
                .doesNotContain("<td><font size=\"-1\"><%=responseDescription%>")
                .doesNotContain("value=\"<%=lastName%>\"")
                .doesNotContain("onclick=\"javascript:Attach('<%=lastName%>'")
                .doesNotContain("onclick=\"javascript:Attach('<%=firstName%>'")
                .doesNotContain("onclick=\"javascript:Attach('<%=hcMagneticStripe.getHealthNumber()%>'")
                .contains("context=\\\"javaScriptAttribute\\\"")
                .contains("context=\\\"htmlAttribute\\\"")
                .contains("context=\"html\"/>");
    }

    @Test
    void shouldDeriveDocumentReportCurrentUserFromSession_andGuardOpenerRefresh() throws Exception {
        String addDocumentJsp = readJsp("documentManager/addDocument.jsp");
        String documentReportJsp = readJsp("documentManager/documentReport.jsp");

        // Assert intent (whitespace-tolerant patterns), not exact source formatting. The
        // doesNotContain guards are the durable regression net: curUser must not be read from a
        // request parameter, and the opener URL map must not be dereferenced unguarded.
        assertThat(addDocumentJsp)
                .containsPattern("curUser\\s*=\\s*user_no\\s*!=\\s*null")
                .doesNotContain("request.getParameter(\"curUser\")");
        assertThat(documentReportJsp)
                .containsPattern("curUser\\s*=\\s*LoggedInInfo\\.getLoggedInInfoFromSession\\(request\\)\\.getLoggedInProviderNo\\(\\)")
                .containsPattern("hasOwnProperty\\.call\\(\\s*window\\.opener\\.URLs")
                // Gate forwards the validated lowercased function token; the JSP must prefer it so a
                // mixed-case "function" param cannot skip the case-sensitive "demographic" branch.
                .containsPattern("getAttribute\\(\\s*\"normalizedFunction\"\\s*\\)")
                .doesNotContain("var Url = window.opener.URLs;");
    }

    @Test
    void shouldUseGuardedIpAddressVariable_forChartNotesAjax() throws Exception {
        String chartNotesJsp = readJsp("casemgmt/ChartNotesAjax.jsp");
        int declarationStart = chartNotesJsp.indexOf("String noteLockIpAddress");
        int assignmentIndex = chartNotesJsp.indexOf("noteLockIpAddress = casemgmtNoteLock.getIpAddress();");
        int confirmStart = chartNotesJsp.indexOf("var viewEditedNote = confirm(");
        int confirmEnd = chartNotesJsp.indexOf(");", confirmStart);
        assertThat(declarationStart).isGreaterThanOrEqualTo(0);
        assertThat(assignmentIndex).isGreaterThan(declarationStart).isLessThan(confirmStart);
        assertThat(confirmStart).isGreaterThanOrEqualTo(0);
        assertThat(confirmEnd).isGreaterThan(confirmStart);
        String lockPreparationSnippet = chartNotesJsp.substring(declarationStart, confirmStart);
        String confirmSnippet = chartNotesJsp.substring(confirmStart, confirmEnd);

        assertThat(lockPreparationSnippet)
                .containsPattern("String\\s+noteLockIpAddress\\s*=\\s*\"\"\\s*;")
                .containsPattern("if\\s*\\(noteLockedBySameUser\\)\\s*\\{")
                .contains("noteLockIpAddress = casemgmtNoteLock.getIpAddress();");
        assertThat(confirmSnippet)
                .contains("<%= noteLockIpAddress %>")
                .doesNotContain("casemgmtNoteLock.getIpAddress()");
    }

    @Test
    void shouldNotLockInJavaScriptOnlyEncoding_forInnerHtmlAssignments() throws Exception {
        String viewScriptJsp = readJsp("rx/ViewScript2.jsp");

        // Regression guard: the original unsafe raw interpolation must not return.
        // JS-only encoding into innerHTML is a layered concern flagged in review, so
        // we intentionally do not lock in any specific mitigation here.
        assertThat(viewScriptJsp)
                .doesNotContain("innerHTML = \"<%=vecAddress.get(i)%>\";");
    }

    @Test
    void shouldContainEncodedBillingServiceCalls_inJavaScriptAttributeContext() throws Exception {
        String billingBcJsp = readJsp("billing/CA/BC/billingBC.jsp");

        assertThat(billingBcJsp)
                .doesNotContain("\"addSvcCode('\" + billlist1[i].getServiceCode() + \"')\"")
                .doesNotContain("\"addSvcCode('\" + billlist2[i].getServiceCode() + \"')\"")
                .doesNotContain("\"addSvcCode('\" + billlist3[i].getServiceCode() + \"')\"")
                .contains("SafeEncode.forJavaScriptAttribute(billlist1[i].getServiceCode())")
                .contains("SafeEncode.forJavaScriptAttribute(billlist2[i].getServiceCode())")
                .contains("SafeEncode.forJavaScriptAttribute(billlist3[i].getServiceCode())");
    }

    @Test
    @DisplayName("should encode bill status table fields in HTML and JavaScript attribute contexts")
    @Tag("security")
    void shouldContainEncodedBillingStatusNamesAndDescriptions_inSafeContexts() throws Exception {
        String billStatusJsp = readJsp("billing/CA/BC/billStatus.jsp");

        assertThat(billStatusJsp)
                .doesNotContain("<a href=\"javascript: setDemographic('<%=b.demoNo%>');\"><%=b.demoName%>")
                .doesNotContain("<td><%=b.providerLastName%>,<%=b.providerFirstName%>")
                .doesNotContain("<td title=\"<%=msp.getStatusDesc(b.reason)%>\"><%=msp.getStatusDesc(b.reason) == null ? \"&nbsp\" : msp.getStatusDesc(b.reason)%>")
                .doesNotContain("SafeEncode.forJavaScriptAttribute(String.valueOf(b.demoNo))")
                .contains("SafeEncode.forJavaScriptAttribute(b.demoNo)")
                .contains("SafeEncode.forHtml(b.demoName)")
                .contains("SafeEncode.forHtml(b.providerLastName)")
                .contains("SafeEncode.forHtml(b.providerFirstName)")
                .contains("String statusDesc = msp.getStatusDesc(b.reason);")
                .contains("title=\"<%=SafeEncode.forHtmlAttribute(statusDesc)%>\"")
                .contains("statusDesc == null ? \"&nbsp;\" : SafeEncode.forHtml(statusDesc)");
    }

    @Test
    void shouldContainEncodedMeasurementGroupNames_inHtmlBodyContext() throws Exception {
        String addGroupJsp = readJsp("encounter/oscarMeasurements/AddMeasurementGroup.jsp");
        String editGroupJsp = readJsp("encounter/oscarMeasurements/EditMeasurementGroup.jsp");

        assertThat(addGroupJsp)
                .doesNotContain("<%= session.getAttribute(\"groupName\") %>")
                .contains("<carlos:encode value='<%= groupName %>' context=\"html\"/>")
                .doesNotContainPattern(">(?:\\s*)<%=\\s*groupName\\s*%>(?:\\s*)<");
        assertThat(editGroupJsp)
                .doesNotContain("<%= session.getAttribute(\"groupName\") %>")
                .contains("<carlos:encode value='<%= groupName %>' context=\"html\"/>")
                .doesNotContainPattern(">(?:\\s*)<%=\\s*groupName\\s*%>(?:\\s*)<");
    }

    @Test
    @DisplayName("should encode provider values in lab forwarding rules JSP")
    @Tag("security")
    void shouldEncodeProviderValues_inLabForwardingRulesJsp() throws Exception {
        String jsp = readJsp("admin/labforwardingrules.jsp");

        assertThat(jsp)
                .doesNotContain("<option value=\"<%= prov_no %>\"")
                .doesNotContain("removeProvider('<%= (String) ((ArrayList) frwdProviders.get(i)).get(0) %>'")
                .contains("<option value=\"<carlos:encode value='<%= prov_no %>' context=\"htmlAttribute\"/>\"")
                .contains("<option value=\"<carlos:encode value='<%= prov_no %>' context=\"htmlAttribute\"/>\"><carlos:encode "
                        + "value='<%= (String) ((ArrayList) providers.get(i)).get(1) %>' context=\"html\"/>")
                .contains("<td><carlos:encode value='<%= (String) ((ArrayList) frwdProviders.get(i)).get(1) %>' context=\"html\"/> "
                        + "<carlos:encode value='<%= (String) ((ArrayList) frwdProviders.get(i)).get(2) %>' context=\"html\"/>")
                .contains("removeProvider('<carlos:encode value='<%= (String) ((ArrayList) frwdProviders.get(i)).get(0) %>' "
                        + "context=\"javaScriptAttribute\"/>', '<carlos:encode value='<%= (String) ((ArrayList) frwdProviders.get(i)).get(1) %>' "
                        + "context=\"javaScriptAttribute\"/> <carlos:encode value='<%= (String) ((ArrayList) frwdProviders.get(i)).get(2) %>' "
                        + "context=\"javaScriptAttribute\"/>')");
    }

    @Test
    @DisplayName("should encode decision textarea file content in HTML body context")
    @Tag("security")
    void shouldEncodeDecisionTextareaFileContent_inHtmlBodyContext() throws Exception {
        List<String> decisionTextareaEditors = List.of(
                "decision/antenatal/obarriskedit_99_12.jsp",
                "decision/annualreview/riskedit.jsp",
                "decision/annualreview/checklistedit.jsp",
                "provider/obarriskedit_99_12.jsp",
                "provider/obarchecklistedit_99_12.jsp");

        for (String jspPath : decisionTextareaEditors) {
            assertThat(readJsp(jspPath))
                    .as(jspPath)
                    .containsPattern(SAFE_ENCODE_IMPORT_PATTERN)
                    .containsPattern(SAFE_TEXTAREA_RENDER_PATTERN)
                    .doesNotContainPattern(RAW_TEXTAREA_RENDER_PATTERN);
        }
    }

    @Test
    @DisplayName("should encode billing settings custom clinic info textarea in HTML body context")
    @Tag("security")
    void shouldEncodeBillingSettingsCustomClinicInfoTextarea_inHtmlBodyContext() throws Exception {
        String billingSettingsJsp = readJsp("admin/billingSettings.jsp");

        assertThat(billingSettingsJsp)
                .contains("${carlos:forHtmlContent(\"on\" eq dataBean[\"invoice_use_custom_clinic_info\"] ? dataBean[\"invoice_custom_clinic_info\"] : clinicData.label)}")
                .doesNotContain("${\"on\" eq dataBean[\"invoice_use_custom_clinic_info\"] ? dataBean[\"invoice_custom_clinic_info\"] : clinicData.label }");
    }

    @Test
    @DisplayName("should render MOH archive session messages on the MOH files page")
    @Tag("security")
    void shouldRenderMohMessages_onViewMohFilesPage() throws Exception {
        String jsp = readJsp("billing/CA/ON/viewMOHFiles.jsp");

        assertThat(jsp)
                .contains("WebUtils.popErrorAndInfoMessagesAsHtml(session)")
                .doesNotContain("WebUtils.popErrorMessagesAsAlert(session)");
    }

    @Test
    @DisplayName("should encode measurement data cells in HTML body context")
    @Tag("security")
    void shouldEncodeMeasurementData_onDisplayHistoryPage() throws Exception {
        String jsp = readJsp("encounter/oscarMeasurements/DisplayHistory.jsp");

        assertThat(jsp)
                .contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>")
                .contains("${carlos:forHtmlContent(data.dataField)}")
                .contains("${carlos:forHtmlContent(data.comments)}")
                .doesNotContain("${data.dataField}</td>")
                .doesNotContain("${data.comments}</td>")
                .doesNotContain("${carlos:forHtml(data.dataField)}")
                .doesNotContain("${carlos:forHtml(data.comments)}");
    }

    @Test
    @DisplayName("should render Teleplan eligibility lines without storing HTML in Msgs")
    @Tag("security")
    void shouldRenderTeleplanEligibilityLines_onCheckEligibilityPage() throws Exception {
        String jsp = readJsp("billing/CA/BC/checkEligibility.jsp");

        assertThat(jsp)
                .contains("List<String> msgLines")
                .contains("request.getAttribute(\"MsgsLines\")")
                .contains("<span style=\"color:red; font-weight:bold;\">")
                .contains("<carlos:encode value='<%= safeLine %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= msgs %>' context=\"html\"/>");
    }

    private static String readJsp(String relativePath) throws Exception {
        return Files.readString(JSP_ROOT.resolve(relativePath));
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")))
                .toAbsolutePath()
                .normalize();
        for (int checkedParents = 0; current != null && checkedParents < 6; checkedParents++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate) || Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate " + relativePath + " from "
                + System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")));
    }
}
