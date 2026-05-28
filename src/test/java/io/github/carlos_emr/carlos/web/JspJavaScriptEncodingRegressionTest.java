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
    private static final Path WEBAPP_ROOT = resolveProjectPath(Path.of("src/main/webapp"));

    @Test
    void shouldContainEncodedSessionValues_inJavaScriptStrings() throws Exception {
        String sentJsp = readJsp("mcedt/mailbox/sent.jsp");
        String autoDownloadJsp = readJsp("mcedt/mailbox/autoDownload.jsp");
        String forcePasswordResetJsp = readJsp("login/forcepasswordreset.jsp");
        String checkPasswordJsJsp = readWebAsset("js/checkPassword.js.jsp");

        assertThat(sentJsp)
                .doesNotContain("'<%= session.getAttribute(\"info\") %>'")
                .contains("SafeEncode.forJavaScript(session.getAttribute(\"info\") == null ? null : session.getAttribute(\"info\").toString())");
        assertThat(autoDownloadJsp)
                .doesNotContain("'<%= session.getAttribute(\"resourceID\") %>'")
                .contains("SafeEncode.forJavaScript(")
                .contains("session.getAttribute(\"resourceID\")");
        assertThat(forcePasswordResetJsp)
                .doesNotContain("JavaScriptUtils.javaScriptEscape(")
                .contains("SafeEncode.forJavaScript(op.getProperty(\"password_group_lower_chars\"))")
                .contains("SafeEncode.forJavaScript(op.getProperty(\"password_group_upper_chars\"))")
                .contains("SafeEncode.forJavaScript(op.getProperty(\"password_group_digits\"))")
                .contains("SafeEncode.forJavaScript(op.getProperty(\"password_group_special\"))");
        assertThat(checkPasswordJsJsp)
                .doesNotContain("JavaScriptUtils.javaScriptEscape(")
                .contains("SafeEncode.forJavaScript(op.getProperty(\"password_group_lower_chars\"))")
                .contains("SafeEncode.forJavaScript(op.getProperty(\"password_group_upper_chars\"))")
                .contains("SafeEncode.forJavaScript(op.getProperty(\"password_group_digits\"))")
                .contains("SafeEncode.forJavaScript(op.getProperty(\"password_group_special\"))");
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

    private static String readJsp(String relativePath) throws Exception {
        return Files.readString(JSP_ROOT.resolve(relativePath));
    }

    private static String readWebAsset(String relativePath) throws Exception {
        return Files.readString(WEBAPP_ROOT.resolve(relativePath));
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
