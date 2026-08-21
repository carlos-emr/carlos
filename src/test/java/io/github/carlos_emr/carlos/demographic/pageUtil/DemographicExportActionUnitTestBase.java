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
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentCommentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Shared unit-test fixture for {@link DemographicExportAction42Action}.
 *
 * <p>The action resolves a dozen collaborators through {@code SpringUtils} and reads the servlet
 * request/response from {@code ServletActionContext} statics, so every unit test of it needs the
 * same substantial setup. Centralising it here keeps the per-behaviour test classes focused and
 * stops the two suites from drifting apart.</p>
 *
 * <p>Both export privileges are granted by default; a test that exercises authorization should
 * re-stub {@link #securityInfoManager} for the case it needs.</p>
 *
 * @since 2026-08-11
 */
abstract class DemographicExportActionUnitTestBase extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    protected SecurityInfoManager securityInfoManager;
    @Mock
    protected LoggedInInfo loggedInInfo;
    @Mock
    protected HttpServletRequest request;
    @Mock
    protected HttpServletResponse response;
    @Mock
    protected DemographicArchiveDao demographicArchiveDao;
    @Mock
    protected DemographicContactDao demographicContactDao;
    @Mock
    protected PartialDateDao partialDateDao;
    @Mock
    protected HRMDocumentToDemographicDao hrmDocumentToDemographicDao;
    @Mock
    protected HRMDocumentDao hrmDocumentDao;
    @Mock
    protected HRMDocumentCommentDao hrmDocumentCommentDao;
    @Mock
    protected CaseManagementManager caseManagementManager;
    @Mock
    protected Hl7TextInfoDao hl7TextInfoDao;
    @Mock
    protected Hl7TextMessageDao hl7TextMessageDao;
    @Mock
    protected DemographicExtDao demographicExtDao;

    /** Action under test, rebuilt for each test method. */
    protected DemographicExportAction42Action action;

    @BeforeEach
    void setUpExportAction() {
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

        action = new DemographicExportAction42Action();
    }

    @AfterEach
    void tearDownExportAction() throws Exception {
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
}
