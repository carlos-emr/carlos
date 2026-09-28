/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.pageUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class FileLabs2ActionUnitTest extends CarlosUnitTestBase {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final LoggedInInfo info = mock(LoggedInInfo.class);
    MockedStatic<ServletActionContext> servlet;
    MockedStatic<LoggedInInfo> sessions;
    MockedStatic<CommonLabResultData> filing;
    FileLabs2Action action;

    @BeforeEach void fixture() {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(PatientLabRoutingDao.class, mock(PatientLabRoutingDao.class));
        registerMock(ProviderLabRoutingDao.class, mock(ProviderLabRoutingDao.class));
        registerMock(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class));
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        sessions = mockStatic(LoggedInInfo.class);
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
        filing = mockStatic(CommonLabResultData.class);
        request.setMethod("POST"); request.setParameter("flaggedLabId", "42"); request.setParameter("labType", "DOC");
        action = new FileLabs2Action();
    }
    @AfterEach void release() {
        if (filing != null) filing.close(); if (sessions != null) sessions.close(); if (servlet != null) servlet.close();
    }
    JsonNode result() throws Exception { return new ObjectMapper().readTree(response.getContentAsString()); }

    @Test void docAjaxReportsExactConfirmedIdentityAndUsesAuthenticatedOverload() throws Exception {
        filing.when(() -> CommonLabResultData.fileLabs(any(), eq(info))).thenAnswer(call -> {
            ArrayList<String[]> selected = call.getArgument(0);
            assertThat(selected).hasSize(1); assertThat(selected.get(0)).containsExactly("42", "DOC"); return true;
        });
        action.fileLabAjax();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(result().path("success").asBoolean()).isTrue(); assertThat(result().path("accepted").asBoolean()).isTrue();
        assertThat(result().path("document").asInt()).isEqualTo(42); assertThat(result().path("retryable").asBoolean()).isFalse();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        filing.verify(() -> CommonLabResultData.fileLabs(any(), anyString()), never());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rejectedOrUnknownDocOutcomeNeverReportsSuccessOrPermitsReplay(boolean accepted) throws Exception {
        filing.when(() -> CommonLabResultData.fileLabs(any(), eq(info))).thenThrow(
                new CommonLabResultData.FilingFailure(new SecurityException("PRIVATE denial detail"), accepted));
        action.fileLabAjax();
        assertThat(response.getStatus()).isEqualTo(accepted ? 500 : 403);
        assertThat(result().path("success").asBoolean()).isFalse(); assertThat(result().path("accepted").asBoolean()).isEqualTo(accepted);
        assertThat(result().path("document").asInt()).isEqualTo(42); assertThat(result().path("retryable").asBoolean()).isFalse();
        assertThat(response.getContentAsString()).doesNotContain("PRIVATE");
    }

    @Test void falseServiceResultIsVisibleAndConservativelyAccepted() throws Exception {
        filing.when(() -> CommonLabResultData.fileLabs(any(), eq(info))).thenReturn(false);
        action.fileLabAjax();
        assertThat(response.getStatus()).isEqualTo(500); assertThat(result().path("success").asBoolean()).isFalse();
        assertThat(result().path("accepted").asBoolean()).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"GET", "PUT", "DELETE"})
    void bothEntryPointsRejectNonPostBeforeAnyFiling(String method) throws Exception {
        request.setMethod(method); action.fileLabAjax(); action.execute();
        assertThat(response.getStatus()).isEqualTo(405); assertThat(response.getHeader("Allow")).isEqualTo("POST");
        filing.verifyNoInteractions();
    }

    @Test void duplicateAjaxSelectionRejectsWithoutCallingService() throws Exception {
        request.setParameter("flaggedLabId", new String[]{"42", "43"}); action.fileLabAjax();
        assertThat(response.getStatus()).isEqualTo(400); assertThat(result().path("accepted").asBoolean()).isFalse();
        filing.verifyNoInteractions();
    }

    @Test void malformedBatchIsNotAnEmptySuccessfulFiling() throws Exception {
        request.setParameter("flaggedLabs", "{not-json"); action.execute();
        assertThat(response.getStatus()).isEqualTo(400); assertThat(result().path("success").asBoolean()).isFalse();
        filing.verifyNoInteractions();
    }

    @Test void batchPartialCommitDoesNotReturnSuccessfulFiles() throws Exception {
        request.setParameter("flaggedLabs", "{\"files\":[\"42:DOC\",\"43:DOC\"]}");
        filing.when(() -> CommonLabResultData.fileLabs(any(), eq(info))).thenThrow(
                new CommonLabResultData.FilingFailure(new IllegalStateException("later item failed"), true));
        action.execute();
        assertThat(response.getStatus()).isEqualTo(500); assertThat(result().path("accepted").asBoolean()).isTrue();
        assertThat(result().path("success").asBoolean()).isFalse(); assertThat(result().has("files")).isFalse();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void responseFailureAfterCommitCannotBeReclassifiedAsUnaccepted(boolean batch) throws Exception {
        var broken = mock(jakarta.servlet.http.HttpServletResponse.class);
        when(broken.getOutputStream()).thenThrow(new IllegalStateException("Response channel already selected"));
        action.response = broken;
        request.setParameter("flaggedLabs", "{\"files\":[\"42:DOC\"]}");
        filing.when(() -> CommonLabResultData.fileLabs(any(), eq(info))).thenReturn(true);
        assertThatThrownBy(() -> {if (batch) action.execute(); else action.fileLabAjax();}).isInstanceOf(IllegalStateException.class);
        filing.verify(() -> CommonLabResultData.fileLabs(any(), eq(info)), times(1));
        verify(broken).setStatus(200); verify(broken, never()).setStatus(500);
        verify(broken, times(1)).getOutputStream();
    }
}
