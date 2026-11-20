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
package io.github.carlos_emr.carlos.report.reportByTemplate.actions;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.reportByTemplate.ReportManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the write gate of the Report by Template editor (carlos-emr/carlos#4133): add, edit
 * and delete are POST-only and need {@code _report} write, and a refused save hands the
 * submitted document back to the editor.
 */
@DisplayName("ManageTemplates2Action")
@Tag("unit")
@Tag("report")
@Tag("security")
class ManageTemplates2ActionUnitTest extends CarlosUnitTestBase {

    private static final String XML = "<report title=\"FAKE\" description=\"FAKE\"><query>SELECT 1</query></report>";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private SecurityInfoManager securityInfoManager;
    private ReportManager reportManager;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        reportManager = mock(ReportManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        request = new MockHttpServletRequest("POST", "/carlos/oscarReport/reportByTemplate/addEditTemplatesAction");
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.READ, null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.WRITE, null)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    private ManageTemplates2Action newAction() {
        return new ManageTemplates2Action(securityInfoManager, () -> reportManager);
    }

    @ParameterizedTest(name = "action={0}")
    @ValueSource(strings = {"add", "edit", "delete"})
    @DisplayName("should refuse a GET that would change a template, before touching it")
    void shouldReturn405_whenMutatingActionIsGet(String operation) throws Exception {
        request.setMethod("GET");
        request.setParameter("action", operation);
        request.setParameter("templateid", "7");
        request.setParameter("xmltext", XML);

        assertThat(newAction().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(reportManager);
    }

    @ParameterizedTest(name = "action={0}")
    @ValueSource(strings = {"add", "edit", "delete"})
    @DisplayName("should require _report write, not only read, to change a template")
    void shouldThrowSecurityException_whenReportWriteDenied(String operation) {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.WRITE, null)).thenReturn(false);
        request.setParameter("action", operation);
        request.setParameter("templateid", "7");

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_report)");
        verifyNoInteractions(reportManager);
    }

    @Test
    @DisplayName("should save an edited template and return to the configuration page on Done")
    void shouldReturnDone_whenEditSavedWithDone() throws Exception {
        when(reportManager.updateTemplate("u-1", "7", XML, loggedInInfo)).thenReturn("Saved Successfully");
        request.setParameter("action", "edit");
        request.setParameter("templateid", "7");
        request.setParameter("uuid", "u-1");
        request.setParameter("xmltext", XML);
        request.setParameter("done", "Done");

        assertThat(newAction().execute()).isEqualTo("done");
        verify(reportManager).updateTemplate("u-1", "7", XML, loggedInInfo);
        assertThat(request.getAttribute("submittedXml")).isNull();
    }

    @Test
    @DisplayName("should stay on the editor with the submitted XML when the save is refused")
    void shouldReshowSubmittedXml_whenSaveRefused() throws Exception {
        String refused = XML.replace("SELECT 1", "DELETE FROM demographic");
        when(reportManager.updateTemplate(null, "7", refused, loggedInInfo))
                .thenReturn("Error: The <query> was refused: Only SELECT statements are allowed");
        request.setParameter("action", "edit");
        request.setParameter("templateid", "7");
        request.setParameter("xmltext", refused);
        request.setParameter("done", "Done");

        assertThat(newAction().execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("message")).asString().startsWith("Error: The <query> was refused");
        assertThat(request.getAttribute("submittedXml")).isEqualTo(refused);
    }

    @Test
    @DisplayName("should save a new template and stay on the editor with the outcome")
    void shouldStayOnEditor_whenAddSaved() throws Exception {
        when(reportManager.addTemplate(null, XML, loggedInInfo)).thenReturn("Saved Successfully");
        request.setParameter("action", "add");
        request.setParameter("xmltext", XML);

        assertThat(newAction().execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(reportManager).addTemplate(null, XML, loggedInInfo);
        assertThat(request.getAttribute("message")).isEqualTo("Saved Successfully");
        assertThat(request.getAttribute("submittedXml")).isNull();
    }

    @Test
    @DisplayName("should stay on the editor with the message when the delete fails")
    void shouldReportFailure_whenDeleteFails() throws Exception {
        when(reportManager.deleteTemplate("7", loggedInInfo)).thenReturn("Error: Template not found");
        request.setParameter("action", "delete");
        request.setParameter("templateid", "7");

        assertThat(newAction().execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("message")).isEqualTo("Error: Template not found");
    }

    @Test
    @DisplayName("should pass the acting user to delete and land on the template home page")
    void shouldDeleteWithLoggedInInfo_whenPosted() throws Exception {
        when(reportManager.deleteTemplate("7", loggedInInfo)).thenReturn("");
        request.setParameter("action", "delete");
        request.setParameter("templateid", "7");

        assertThat(newAction().execute()).isEqualTo("deleted");
        verify(reportManager).deleteTemplate("7", loggedInInfo);
    }

    @Test
    @DisplayName("should still let a _report reader open the editor without an operation")
    void shouldAllowReadOnlyVisit_whenNoOperation() throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.WRITE, null)).thenReturn(false);
        request.setMethod("GET");

        assertThat(newAction().execute()).isEqualTo(ActionSupport.SUCCESS);
        verifyNoInteractions(reportManager);
    }
}
