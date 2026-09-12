/*
 * Copyright (c) 2026 CARLOS Contributors.
 * Licensed under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.mds.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.dao.TicklerDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerLinkDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("unit")
@DisplayName("Lab mutation HTTP method boundaries")
class LabMutationMethodUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    @DisplayName("should audit an acknowledgement only after the routing transaction succeeds")
    void shouldAuditOnlyAfterSuccessfulRouting_whenAcknowledging(int failureStage) {
        boolean mutationFails = failureStage == 1;
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oscarMDS/UpdateStatus");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setParameter("segmentID", "169");
        request.setParameter("providerNo", "forged-provider");
        request.setParameter("status", "A");
        request.setParameter("labType", "HL7");
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_lab"), eq("w"), isNull())).thenReturn(true);
        createAndRegisterMock(io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao.class);
        createAndRegisterMock(io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao.class);
        createAndRegisterMock(io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao.class);
        io.github.carlos_emr.carlos.utility.LoggedInInfo info = mock(io.github.carlos_emr.carlos.utility.LoggedInInfo.class);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
                MockedStatic<io.github.carlos_emr.carlos.utility.LoggedInInfo> session = mockStatic(io.github.carlos_emr.carlos.utility.LoggedInInfo.class);
                MockedStatic<io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData> data = mockStatic(io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            session.when(() -> io.github.carlos_emr.carlos.utility.LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
            if (failureStage == 2) {
                logActionMock.when(() -> io.github.carlos_emr.carlos.log.LogAction.addLog("999998",
                        io.github.carlos_emr.carlos.log.LogConst.ACK, io.github.carlos_emr.carlos.log.LogConst.CON_HL7_LAB,
                        "169", request.getRemoteAddr(), "")).thenThrow(new IllegalStateException("injected audit failure"));
            }
            data.when(() -> io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData.updateReportStatusWithOlderVersions(
                    169, "999998", 'A', null, "HL7", false, null)).thenAnswer(invocation -> {
                        logActionMock.verifyNoInteractions();
                        if (mutationFails) throw new IllegalStateException("injected routing failure");
                        return 2;
                    });
            assertThat(new ReportStatusUpdate2Action().executemain()).isEqualTo(mutationFails ? "failure" : ActionSupport.SUCCESS);
            if (mutationFails) {
                logActionMock.verifyNoInteractions();
            } else {
                logActionMock.verify(() -> io.github.carlos_emr.carlos.log.LogAction.addLog("999998",
                        io.github.carlos_emr.carlos.log.LogConst.ACK, io.github.carlos_emr.carlos.log.LogConst.CON_HL7_LAB,
                        "169", request.getRemoteAddr(), ""));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE", "PATCH", "OPTIONS", "post"})
    @DisplayName("should reject every non-POST method before lab, macro, or comment side effects")
    void shouldRejectNonPost_beforeDispatch(String method) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/oscarMDS/UpdateStatus");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        TicklerDao ticklers = createAndRegisterMock(TicklerDao.class);
        TicklerLinkDao links = createAndRegisterMock(TicklerLinkDao.class);
        UserPropertyDAO preferences = createAndRegisterMock(UserPropertyDAO.class);
        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            ReportStatusUpdate2Action status = new ReportStatusUpdate2Action();
            assertThat(status.execute()).isEqualTo(ActionSupport.NONE);
            request.setParameter("method", "addComment");
            assertThat(status.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(status.executemain()).isEqualTo(ActionSupport.NONE);
            assertThat(status.addComment()).isEqualTo(ActionSupport.NONE);
            assertThat(new ReportMacro2Action().execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            assertThat(response.getContentAsString()).isEmpty();
            verifyNoInteractions(security, ticklers, links, preferences);
        }
    }
}
