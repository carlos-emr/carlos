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
    @DisplayName("should encode Rx JSP scriptlet output in the correct contexts")
    @Tag("security")
    void shouldEncodeRxJspScriptletOutput_inCorrectContexts() throws Exception {
        String writeScriptJsp = readJsp("rx/WriteScript.jsp");
        String viewScriptJsp = readJsp("rx/ViewScript2.jsp");
        String displayRxRecordJsp = readJsp("rx/DisplayRxRecord.jsp");
        String printDrugProfileJsp = readJsp("rx/PrintDrugProfile2.jsp");
        String previewJsp = readJsp("rx/Preview2.jsp");
        String sideLinksJsp = readJsp("rx/SideLinksNoEditFavorites2.jsp");
        String interactionDisplayJsp = readJsp("rx/InteractionDisplay.jsp");
        String chooseAllergyJsp = readJsp("rx/ChooseAllergy2.jsp");
        String showPreviousPrintsJsp = readJsp("rx/ShowPreviousPrints.jsp");
        String selectReasonJsp = readJsp("rx/SelectReason.jsp");

        assertThat(writeScriptJsp)
                .contains("for <carlos:encode value='<%= patient.getFirstName() %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= thisForm.getBrandName() %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= spec[i] %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= freq[i].getFreqCode() %>' context=\"html\"/>")
                .doesNotContain("for <%= patient.getFirstName() %> <%= patient.getSurname() %>")
                .doesNotContain("<%= spec[i] %>")
                .doesNotContain("<%= freq[i].getFreqCode() %>");

        assertThat(viewScriptJsp)
                .contains("SafeEncode.forJavaScript(pharmacy.getFax())")
                .contains("href=\"javascript:ShowDrugInfo('<carlos:encode value='<%= rx.getGenericName() %>' context=\"javaScriptAttribute\"/>');\"")
                .contains("><carlos:encode value='<%= te %>' context=\"html\"/>")
                .doesNotContain("'<%= pharmacy!=null?pharmacy.getFax():\"\"%>'")
                .doesNotContain("href=\"javascript:ShowDrugInfo('<%= rx.getGenericName() %>');\">");

        assertThat(displayRxRecordJsp)
                .contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>")
                .contains("<td><carlos:encode value='<%= drug.getSpecialInstruction() %>' context=\"html\"/>")
                .contains("${carlos:forHtml(pharmacy.name)}")
                .doesNotContain("<td><%= drug.getSpecialInstruction()%>");

        assertThat(printDrugProfileJsp)
                .contains("<carlos:encode value='<%= surname %>' context=\"html\"/>")
                .contains("${carlos:forHtml(patient.hin)}")
                .doesNotContain("<%=surname%>, <%=firstName%>")
                .doesNotContain("${patient.hin}");

        assertThat(previewJsp)
                .contains("value=\"<carlos:encode value='<%= pharmaFax %>' context=\"htmlAttribute\"/>\"")
                .contains("${carlos:forHtml(infirmaryView_programAddress)}")
                .contains("SafeEncode.forHtml(io.github.carlos_emr.CarlosProperties.getInstance().getProperty(\"RX_FOOTER\"))")
                .contains("SafeEncode.forHtml(io.github.carlos_emr.CarlosProperties.getInstance().getProperty(\"FORMS_PROMOTEXT\"))")
                .doesNotContain("value=\"<%=pharmaFax%>\"")
                .doesNotContain("out.write(io.github.carlos_emr.CarlosProperties.getInstance().getProperty(\"RX_FOOTER\"));");

        assertThat(sideLinksJsp)
                .contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>")
                .contains("title=\"<carlos:encode value='<%= allergies[j].getDescription() %>' context=\"htmlAttribute\"/> -")
                .contains("title=\"<carlos:encode value='<%= favorites[j].getFavoriteName() %>' context=\"htmlAttribute\"/>\"")
                .doesNotContain("title=\"<%= allergies[j].getDescription() %> - <%= allergies[j].getReaction() %>\"")
                .doesNotContain("title=\"<%= favorites[j].getFavoriteName() %>\"");

        assertThat(interactionDisplayJsp)
                .contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>")
                .contains("<carlos:encode value='<%= interactions[i].comment %>' context=\"html\"/>")
                .doesNotContain("<%=interactions[i].comment%>");

        assertThat(chooseAllergyJsp)
                .contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>")
                .contains("${carlos:forHtml(allergy.value.description)}")
                .contains("${carlos:forHtml(drugClass[1])}")
                .doesNotContain("${allergy.value.description}")
                .doesNotContain("${allergy.description}");

        assertThat(showPreviousPrintsJsp)
                .contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>")
                .contains("<carlos:encode value='<%= providerName %>' context=\"html\"/>")
                .doesNotContain("<%=providerDao.getProvider(originalProviderNo).getFormattedName() %>")
                .doesNotContain("<%=providerName %>");

        assertThat(selectReasonJsp)
                .contains("<carlos:encode value='<%= request.getAttribute(\"message\") %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= drugReason.getComments() %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= drugReason.getDateCoded() %>' context=\"html\"/>")
                .doesNotContain("<%=request.getAttribute(\"message\") %>")
                .doesNotContain("<%=drugReason.getComments() %>");
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
