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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.DemographicArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicContactDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentCommentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for demographic export request method handling.
 *
 * @since 2026-05-03
 */
@Tag("unit")
@Tag("demographic")
@DisplayName("DemographicExportAction42Action request method handling")
class DemographicExportAction42ActionRequestMethodTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager securityInfoManager;
    @Mock
    private LoggedInInfo loggedInInfo;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private DemographicArchiveDao demographicArchiveDao;
    @Mock
    private DemographicContactDao demographicContactDao;
    @Mock
    private PartialDateDao partialDateDao;
    @Mock
    private HRMDocumentToDemographicDao hrmDocumentToDemographicDao;
    @Mock
    private HRMDocumentDao hrmDocumentDao;
    @Mock
    private HRMDocumentCommentDao hrmDocumentCommentDao;
    @Mock
    private CaseManagementManager caseManagementManager;
    @Mock
    private Hl7TextInfoDao hl7TextInfoDao;
    @Mock
    private Hl7TextMessageDao hl7TextMessageDao;
    @Mock
    private DemographicExtDao demographicExtDao;

    private DemographicExportAction42Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        registerMock(DemographicArchiveDao.class, demographicArchiveDao);
        registerMock(DemographicContactDao.class, demographicContactDao);
        registerMock(PartialDateDao.class, partialDateDao);
        registerMock(HRMDocumentToDemographicDao.class, hrmDocumentToDemographicDao);
        registerMock(HRMDocumentDao.class, hrmDocumentDao);
        registerMock(HRMDocumentCommentDao.class, hrmDocumentCommentDao);
        registerMock(CaseManagementManager.class, caseManagementManager);
        registerMock(Hl7TextInfoDao.class, hl7TextInfoDao);
        registerMock(Hl7TextMessageDao.class, hl7TextMessageDao);
        registerMock(DemographicExtDao.class, demographicExtDao);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), isNull()))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographicExport"), eq("r"), isNull()))
                .thenReturn(true);

        action = new DemographicExportAction42Action(
                demographicArchiveDao,
                demographicContactDao,
                partialDateDao,
                hrmDocumentToDemographicDao,
                hrmDocumentDao,
                hrmDocumentCommentDao,
                caseManagementManager,
                hl7TextInfoDao,
                hl7TextMessageDao,
                demographicExtDao,
                securityInfoManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("should display export UI for GET requests")
    void shouldReturnSuccess_whenRequestMethodIsGet() throws Exception {
        when(request.getMethod()).thenReturn("GET");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(securityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), isNull());
        verify(securityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_demographicExport"), eq("r"), isNull());
        verifyNoInteractions(response);
    }

    @Test
    @DisplayName("should audit unsupported template for POST requests")
    void shouldAuditExportAttempt_whenPostingUnsupportedTemplate() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        action.setDemographicNo("123");
        action.setTemplate(String.valueOf(DemographicExportAction42Action.E2E));

        String result = action.execute();

        assertThat(result).isNotEqualTo(ActionSupport.SUCCESS);
        ArgumentCaptor<OscarLog> auditLogCaptor = ArgumentCaptor.forClass(OscarLog.class);
        logActionMock.verify(() -> LogAction.addLogSynchronous(auditLogCaptor.capture()));
        OscarLog auditLog = auditLogCaptor.getValue();
        assertThat(auditLog.getAction()).isEqualTo(LogConst.EXPORT);
        assertThat(auditLog.getContent()).isEqualTo(LogConst.CON_DEMOGRAPHIC);
        assertThat(auditLog.getData()).contains("Exported 1 records", "outcome=fail", "ids=123");
        verify(securityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), isNull());
        verify(securityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_demographicExport"), eq("r"), isNull());
    }
}
