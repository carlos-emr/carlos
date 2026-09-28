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
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("NoteBrowser document mutation action unit tests")
@Tag("unit")
@Tag("caseManagement")
class NoteBrowserDocumentMutationUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    static class TestDeleteAction extends NoteBrowserDocumentDelete2Action {
        final List<String> deleted = new ArrayList<>();
        boolean fail;
        @Override protected void deleteDocument(String docNo) throws DocumentMutationException {
            if (fail) {
                throw new DocumentMutationException(new IllegalStateException("simulated delete failure"));
            }
            deleted.add(docNo);
        }
    }

    static class TestUndeleteAction extends NoteBrowserDocumentUndelete2Action {
        final List<String> undeleted = new ArrayList<>();
        boolean fail;
        @Override protected void undeleteDocument(String docNo) throws DocumentMutationException {
            if (fail) {
                throw new DocumentMutationException(new IllegalStateException("simulated undelete failure"));
            }
            undeleted.add(docNo);
        }
    }

    static class TestRefileAction extends NoteBrowserDocumentRefile2Action {
        final List<String[]> refiles = new ArrayList<>();
        boolean fail;
        @Override protected void refileDocument(String docNo, String queue) throws DocumentMutationException {
            if (fail) {
                throw new DocumentMutationException(new IllegalStateException("simulated refile failure"));
            }
            refiles.add(new String[]{docNo, queue});
        }
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("POST");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(jakarta.servlet.http.HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eChart"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    @Test
    @DisplayName("should return methodNotAllowed on GET before mutating")
    void shouldReturnMethodNotAllowed_whenRequestIsGet() throws Exception {
        TestDeleteAction action = new TestDeleteAction();
        mockRequest.setMethod("GET");
        action.setDelDocumentNo("42");

        assertThat(action.execute()).isEqualTo("methodNotAllowed");
        assertThat(action.deleted).isEmpty();
    }

    @Test
    @DisplayName("should require logged in session")
    void shouldThrow_whenUserSessionMissing() {
        TestDeleteAction action = new TestDeleteAction();
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(jakarta.servlet.http.HttpServletRequest.class)))
                .thenReturn(null);
        action.setDelDocumentNo("42");

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_eChart w");
        assertThat(action.deleted).isEmpty();
    }

    @Test
    @DisplayName("should require eChart write privilege")
    void shouldThrow_whenEChartWritePrivilegeMissing() {
        TestDeleteAction action = new TestDeleteAction();
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eChart"), eq("w"), isNull()))
                .thenReturn(false);
        action.setDelDocumentNo("42");

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_eChart w");
        assertThat(action.deleted).isEmpty();
    }

    @Test
    @DisplayName("should reject zero document number")
    void shouldRejectDelete_whenDocumentNoIsZero() throws Exception {
        TestDeleteAction action = new TestDeleteAction();
        action.setDelDocumentNo("0");

        assertThat(action.execute()).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        assertThat(action.deleted).isEmpty();
    }

    @Test
    @DisplayName("should reject missing delete document number")
    void shouldRejectDelete_whenDocumentNoIsMissing() throws Exception {
        TestDeleteAction action = new TestDeleteAction();

        assertThat(action.execute()).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        assertThat(action.deleted).isEmpty();
    }

    @Test
    @DisplayName("should reject empty delete document number")
    void shouldRejectDelete_whenDocumentNoIsEmpty() throws Exception {
        TestDeleteAction action = new TestDeleteAction();
        action.setDelDocumentNo("");

        assertThat(action.execute()).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        assertThat(action.deleted).isEmpty();
    }

    @Test
    @DisplayName("should delete and return success")
    void shouldDeleteAndReturnSuccess_whenRequestIsValid() throws Exception {
        TestDeleteAction action = new TestDeleteAction();
        action.setDelDocumentNo("42");
        action.setDemographicNo("1001");
        action.setView("all");
        action.setViewstatus("active");
        action.setSortorder("Content");

        String result = action.execute();

        assertThat(result).isEqualTo("success");
        assertThat(action.deleted).containsExactly("42");
        assertThat(action.getDemographicNo()).isEqualTo("1001");
        assertThat(action.getView()).isEqualTo("all");
        assertThat(action.getViewstatus()).isEqualTo("active");
        assertThat(action.getSortorder()).isEqualTo("Content");
        assertThat(action.getMutationErrorMessage()).isEmpty();
        assertThat(mockResponse.getRedirectedUrl()).isNull();
        verify(mockSecurityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_eChart"), eq("w"), isNull());
        verify(mockSecurityInfoManager, never()).hasPrivilege(any(LoggedInInfo.class), eq("_eChart"), eq("r"), isNull());
    }

    @Test
    @DisplayName("should set generic error message when delete fails")
    void shouldSetErrorMessage_whenDeleteFails() throws Exception {
        TestDeleteAction action = new TestDeleteAction();
        action.fail = true;
        action.setDelDocumentNo("42");

        assertThat(action.execute()).isEqualTo("success");

        assertThat(action.deleted).isEmpty();
        assertThat(action.getMutationErrorMessage()).isEqualTo("Document update failed.");
    }

    @Test
    @DisplayName("should reject nonnumeric undelete document number")
    void shouldRejectUndelete_whenDocumentNoIsNonNumeric() throws Exception {
        TestUndeleteAction action = new TestUndeleteAction();
        action.setUndelDocumentNo("42x");

        assertThat(action.execute()).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        assertThat(action.undeleted).isEmpty();
    }

    @Test
    @DisplayName("should reject missing undelete document number")
    void shouldRejectUndelete_whenDocumentNoIsMissing() throws Exception {
        TestUndeleteAction action = new TestUndeleteAction();

        assertThat(action.execute()).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        assertThat(action.undeleted).isEmpty();
    }

    @Test
    @DisplayName("should reject empty undelete document number")
    void shouldRejectUndelete_whenDocumentNoIsEmpty() throws Exception {
        TestUndeleteAction action = new TestUndeleteAction();
        action.setUndelDocumentNo("");

        assertThat(action.execute()).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        assertThat(action.undeleted).isEmpty();
    }

    @Test
    @DisplayName("should undelete and return success")
    void shouldUndeleteAndReturnSuccess_whenRequestIsValid() throws Exception {
        TestUndeleteAction action = new TestUndeleteAction();
        action.setUndelDocumentNo("42");

        assertThat(action.execute()).isEqualTo("success");
        assertThat(action.undeleted).containsExactly("42");
        assertThat(action.getMutationErrorMessage()).isEmpty();
        assertThat(mockResponse.getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("should set generic error message when undelete fails")
    void shouldSetErrorMessage_whenUndeleteFails() throws Exception {
        TestUndeleteAction action = new TestUndeleteAction();
        action.fail = true;
        action.setUndelDocumentNo("42");

        assertThat(action.execute()).isEqualTo("success");

        assertThat(action.undeleted).isEmpty();
        assertThat(action.getMutationErrorMessage()).isEqualTo("Document update failed.");
    }

    @ParameterizedTest
    @CsvSource({
            "42, queue-1",
            "42x, 7",
            "42, 0",
            "'', 7",
            "42, ''"
    })
    @DisplayName("should reject invalid refile parameters")
    void shouldRejectRefile_whenParametersAreInvalid(String documentNo, String queueId) throws Exception {
        TestRefileAction action = new TestRefileAction();
        action.setRefileDocumentNo(documentNo);
        action.setQueueId(queueId);

        assertThat(action.execute()).isEqualTo("none");

        assertThat(mockResponse.getStatus()).isEqualTo(400);
        assertThat(action.refiles).isEmpty();
    }

    @Test
    @DisplayName("should reject missing refile document number")
    void shouldRejectRefile_whenDocumentNoIsMissing() throws Exception {
        TestRefileAction action = new TestRefileAction();
        action.setQueueId("7");

        assertThat(action.execute()).isEqualTo("none");

        assertThat(mockResponse.getStatus()).isEqualTo(400);
        assertThat(action.refiles).isEmpty();
    }

    @Test
    @DisplayName("should reject missing queueId")
    void shouldRejectRefile_whenQueueIdIsMissing() throws Exception {
        TestRefileAction action = new TestRefileAction();
        action.setRefileDocumentNo("42");

        assertThat(action.execute()).isEqualTo("none");

        assertThat(mockResponse.getStatus()).isEqualTo(400);
        assertThat(action.refiles).isEmpty();
    }

    @Test
    @DisplayName("should refile with validated numeric queueId")
    void shouldRefile_whenDocumentAndQueueAreNumeric() throws Exception {
        TestRefileAction action = new TestRefileAction();
        action.setRefileDocumentNo("42");
        action.setQueueId("7");

        assertThat(action.execute()).isEqualTo("success");

        assertThat(action.refiles).hasSize(1);
        assertThat(action.refiles.get(0)).containsExactly("42", "7");
        assertThat(action.getMutationErrorMessage()).isEmpty();
        assertThat(mockResponse.getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("should set generic error message when refile fails")
    void shouldSetErrorMessage_whenRefileFails() throws Exception {
        TestRefileAction action = new TestRefileAction();
        action.fail = true;
        action.setRefileDocumentNo("42");
        action.setQueueId("7");

        assertThat(action.execute()).isEqualTo("success");

        assertThat(action.refiles).isEmpty();
        assertThat(action.getMutationErrorMessage()).isEqualTo("Document update failed.");
    }

    @Test
    @DisplayName("should expose parameters for Struts redirect result")
    void shouldExposeParameters_forStrutsRedirectResult() {
        TestDeleteAction deleteAction = new TestDeleteAction();
        deleteAction.setDemographicNo("1001");
        deleteAction.setView("all");
        deleteAction.setViewstatus("active");
        deleteAction.setSortorder("Content");
        deleteAction.setDelDocumentNo("42");

        TestUndeleteAction undeleteAction = new TestUndeleteAction();
        undeleteAction.setUndelDocumentNo("43");

        TestRefileAction refileAction = new TestRefileAction();
        refileAction.setRefileDocumentNo("44");
        refileAction.setQueueId("7");

        assertThat(deleteAction.getDemographicNo()).isEqualTo("1001");
        assertThat(deleteAction.getView()).isEqualTo("all");
        assertThat(deleteAction.getViewstatus()).isEqualTo("active");
        assertThat(deleteAction.getSortorder()).isEqualTo("Content");
        assertThat(deleteAction.getDelDocumentNo()).isEqualTo("42");
        assertThat(undeleteAction.getUndelDocumentNo()).isEqualTo("43");
        assertThat(refileAction.getRefileDocumentNo()).isEqualTo("44");
        assertThat(refileAction.getQueueId()).isEqualTo("7");
    }
}
