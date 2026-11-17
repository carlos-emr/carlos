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
import java.sql.ResultSet;
import java.sql.SQLException;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.workflow.RHWorkFlow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * The RH workflow's pages (issue #4992): the WorkFlow queue's link (finding 287), the RH form's Add Injection button
 * (288), the injection editor's Save handler (290) and the injection panel, which shows again once the panel's lookup is
 * fixed (291) and so must encode what it prints and list only the chart's own patient.
 *
 * @since 2026-10-22
 */
@DisplayName("RH workflow pages")
@Tag("unit")
@Tag("form")
class RhWorkflowPagesRegressionUnitTest {

    private static final Path JSPS = Path.of("src/main/webapp/WEB-INF/jsp");

    private static String read(String page) throws IOException {
        return Files.readString(JSPS.resolve(page), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should open the workflow's own RH form from the queue, inside the application")
    void shouldOpenWorkflowRhForm_fromQueueRow() throws Exception {
        // "../form/forwardshortcutname?formname=RH Form" left the application against the queue page's
        // context-root base, and named a Forms-menu shortcut no shipped install registers.
        try (MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            ResultSet bound = rows(55);
            jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet(contains("workflowId = ?"), eq(42), eq(7))).thenReturn(bound);

            assertThat(new RHWorkFlow().getLink("42", "7"))
                    .isEqualTo("/form/formRhImmuneGlobulin?demographic_no=42&formId=55&workflowId=7");
        }

        assertThat(read("oscarWorkflow/WorkFlowList.jsp"))
                .contains("request.getContextPath() + flow.getLink(")
                .doesNotContain("href=\"<%=flow.getLink(");
    }

    @Test
    @DisplayName("should open a new form when no form is bound to the workflow")
    void shouldOpenNewForm_whenNoFormIsBoundToTheWorkflow() throws Exception {
        // The patient's other RH forms may belong to an earlier pregnancy; offering one would let a save copy
        // that pregnancy's values into this workflow, so only a form bound to this workflow is opened.
        try (MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            ResultSet none = rows();
            jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet(contains("workflowId = ?"), eq(42), eq(7))).thenReturn(none);

            assertThat(new RHWorkFlow().getLink("42", "7")).isEqualTo("/form/formRhImmuneGlobulin?demographic_no=42&formId=0&workflowId=7");
            jdbc.verify(() -> LegacyJdbcQuery.getPreparedResultSet(matches("(?s)(?!.*workflowId).*"), eq(42)), never());
        }
    }

    @Test
    @DisplayName("should not query for a row whose ids are not numbers")
    void shouldNotQuery_whenQueueRowIdsAreNotNumbers() {
        try (MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            assertThat(new RHWorkFlow().getLink("x' OR 1=1", "7"))
                    .isEqualTo("/form/formRhImmuneGlobulin?demographic_no=0&formId=0&workflowId=7");
            jdbc.verifyNoInteractions();
        }
    }

    private static ResultSet rows(int... ids) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        if (ids.length == 0) {
            when(rs.next()).thenReturn(false);
        } else {
            when(rs.next()).thenReturn(true, false);
            when(rs.getInt(1)).thenReturn(ids[0]);
        }
        return rs;
    }

    @Test
    @DisplayName("should open the injection editor under form/ from the RH form")
    void shouldOpenInjectionEditorUnderForm_fromRhForm() throws IOException {
        String form = read("form/formRhImmuneGlobulin.jsp");
        assertThat(form).contains("popup(700,600,'form/addRhInjection?demographic_no=");
        assertThat(form).doesNotContain("popup(700,600,'addRhInjection?demographic_no=<carlos");
    }

    @Test
    @DisplayName("should show and save the queue row's workflow when it is one of the patient's active workflows")
    void shouldBindRequestedWorkflow_whenQueueRowNamesIt() throws IOException {
        // Two active RH workflows for one patient: the row's link names its own, and the page must not fall
        // back to the first active one, or the form shown and saved would belong to another pregnancy.
        String form = read("form/formRhImmuneGlobulin.jsp");
        assertThat(form).contains("String requestedWorkflowId = request.getParameter(\"workflowId\");")
                .contains("if (requestedWorkflowId == null) {\n                        h = (Hashtable) currentWorkFlows.get(0);")
                .contains("requestedWorkflowId.trim().equals(((Hashtable) candidate).get(\"ID\"))")
                .doesNotContain("request.setAttribute(\"currentWorkFlow\", currentWorkFlows.get(0));")
                // A named workflow that is no longer active is still posted, so form/RHPrevention refuses the save.
                .contains("<%} else if (requestedWorkflowId != null) {%>")
                .contains("<input type=\"hidden\" name=\"workflowId\" value=\"<carlos:encode value='<%= requestedWorkflowId.trim() %>' context=\"htmlAttribute\"/>\"/>");
    }

    @Test
    @DisplayName("should send the prevention type and date that prevention/AddPrevention requires from the panel's Delete")
    void shouldSendPreventionTypeAndDate_whenPanelDeletesInjection() throws IOException {
        // AddPrevention2Action answers 400 to a post without prevDate or a known prevention type,
        // a delete included, so the RH form's delete form carries both.
        String form = read("form/formRhImmuneGlobulin.jsp");
        assertThat(form).contains("<input type=\"hidden\" name=\"prevention\" value=\"RH\"/>")
                .contains("<input type=\"hidden\" name=\"prevDate\" id=\"deletePrevDate\"/>")
                .contains("function deleteInjection(idval, prevDate) {")
                .contains("document.getElementById('deletePrevDate').value = prevDate;");
    }

    @Test
    @DisplayName("should let the injection editor's Save submit without a script error")
    void shouldSubmitWithoutScriptError_fromInjectionEditor() throws IOException {
        String editor = read("form/addRhInjection.jsp");
        assertThat(editor).doesNotContain("return False").contains("return true;");
        assertThat(editor).contains("for (var i = 0; i < formInject.reason_check.length; i++)");
    }

    @Test
    @DisplayName("should encode the injection panel and list only the chart's patient")
    void shouldEncodePanelAndListOnlyChartPatient_forInjectionPanel() throws IOException {
        String panel = read("form/RhInjectionDisplay.jsp");
        for (String field : new String[] {"location", "lot", "product", "dosage", "reason"}) {
            assertThat(panel).contains("SafeEncode.forHtmlContent(hextended.get(\"" + field + "\"))");
            assertThat(panel).doesNotContain("<%=hextended.get(\"" + field + "\")");
        }
        assertThat(panel).contains("demographicNo.trim().equals(String.valueOf(hdata.get(\"demographicNo\")))");
        assertThat(panel).contains("deleteInjection('<%= SafeEncode.forJavaScriptAttribute(preventionId) %>', "
                + "'<%= SafeEncode.forJavaScriptAttribute((String) hdata.get(\"preventionDate\")) %>')");
        // Delete acts rather than navigates: a button, not an anchor with a javascript: href.
        assertThat(panel).contains("<button type=\"button\" class=\"rh-injection-delete\"").doesNotContain("href=\"javascript:");
    }
}
