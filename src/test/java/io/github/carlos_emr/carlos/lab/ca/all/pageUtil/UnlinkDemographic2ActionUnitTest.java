/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.logging.LoggerLevelOverride;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("UnlinkDemographic2Action debug log sanitization")
@Tag("unit")
@Tag("web")
@Tag("security")
class UnlinkDemographic2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private SecurityInfoManager securityInfoManager;
    private PatientLabRoutingDao patientLabRoutingDao;
    private ProviderLabRoutingDao providerLabRoutingDao;
    private OscarLogDao oscarLogDao;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        patientLabRoutingDao = mock(PatientLabRoutingDao.class);
        providerLabRoutingDao = mock(ProviderLabRoutingDao.class);
        oscarLogDao = mock(OscarLogDao.class);
        loggedInInfo = mock(LoggedInInfo.class);

        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(PatientLabRoutingDao.class, patientLabRoutingDao);
        registerMock(ProviderLabRoutingDao.class, providerLabRoutingDao);
        registerMock(OscarLogDao.class, oscarLogDao);

        request = new MockHttpServletRequest();
        request.setParameter("labNo", "456");
        request.setParameter("reason", "incorrect match");
        request.setRemoteAddr("127.0.0.1");
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_lab"), eq("u"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should sanitize lab and demographic identifiers in unlink debug log")
    void shouldSanitizeIdentifiers_inUnlinkDebugLog() throws Exception {
        PatientLabRouting routing = new PatientLabRouting(456, ProviderLabRoutingDao.LAB_TYPE.HL7.name(), 123);
        when(patientLabRoutingDao.findByLabNoAndLabType(456, ProviderLabRoutingDao.LAB_TYPE.HL7.name()))
                .thenReturn(List.of(routing));
        when(providerLabRoutingDao.findAllLabRoutingByIdandType(456, ProviderLabRoutingDao.LAB_TYPE.HL7.name()))
                .thenReturn(List.of());

        try (LogCapture capture = LogCapture.forLogger(UnlinkDemographic2Action.class)) {
            new UnlinkDemographic2Action().execute();

            assertThat(capture.messages())
                    .contains("Unlinked lab segmentID=456 from demographic=123");
        }
        assertThat(response.getContentAsString()).contains("\"success\":true");
        verify(patientLabRoutingDao).merge(routing);
    }

    @Test
    @DisplayName("should skip unlink debug log when debug is disabled")
    void shouldSkipDebugLog_whenDebugIsDisabled() throws Exception {
        PatientLabRouting routing = new PatientLabRouting(456, ProviderLabRoutingDao.LAB_TYPE.HL7.name(), 123);
        when(patientLabRoutingDao.findByLabNoAndLabType(456, ProviderLabRoutingDao.LAB_TYPE.HL7.name()))
                .thenReturn(List.of(routing));
        when(providerLabRoutingDao.findAllLabRoutingByIdandType(456, ProviderLabRoutingDao.LAB_TYPE.HL7.name()))
                .thenReturn(List.of());

        try (LogCapture capture = LogCapture.forLogger(UnlinkDemographic2Action.class);
                LoggerLevelOverride ignored = LoggerLevelOverride.disableDebug(UnlinkDemographic2Action.class)) {
            new UnlinkDemographic2Action().execute();

            assertThat(capture.messages()).isEmpty();
        }
        assertThat(response.getContentAsString()).contains("\"success\":true");
        verify(patientLabRoutingDao).merge(routing);
    }

}
