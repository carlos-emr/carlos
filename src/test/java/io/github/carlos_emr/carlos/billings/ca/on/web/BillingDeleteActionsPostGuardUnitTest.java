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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionRecordService;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Billing delete action POST guards")
@Tag("unit")
@Tag("billing")
class BillingDeleteActionsPostGuardUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private BillingDao mockBillingDao;
    @Mock private AppointmentArchiveDao mockAppointmentArchiveDao;
    @Mock private OscarAppointmentDao mockAppointmentDao;
    @Mock private BillingCorrectionRecordService mockCorrectionRecordService;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void billingDeleteNoApptShouldRejectNonPostMethods_beforeDeleting(String method) {
        mockRequest.setMethod(method);
        mockRequest.setParameter("billCode", "A");
        mockRequest.setParameter("billing_no", "123");

        String result = new BillingDeleteNoAppt2Action(
                mockSecurityInfoManager, mockBillingDao, mockCorrectionRecordService).execute();

        assertMethodNotAllowed(result);
        verifyNoInteractions(mockBillingDao, mockCorrectionRecordService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void billingDeleteWithBillNoShouldRejectNonPostMethods_beforeDeleting(String method) {
        mockRequest.setMethod(method);
        mockRequest.setParameter("appointment_no", "");
        mockRequest.setParameter("billNo_old", "123");
        mockRequest.setParameter("billStatus_old", "O");

        String result = new BillingDeleteWithBillNo2Action(
                mockSecurityInfoManager, mockBillingDao, mockCorrectionRecordService).execute();

        assertMethodNotAllowed(result);
        verifyNoInteractions(mockBillingDao, mockCorrectionRecordService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void billingDeleteWithoutNoShouldRejectNonPostMethods_beforeDeleting(String method) {
        mockRequest.setMethod(method);
        mockRequest.setParameter("appointment_no", "123");
        mockRequest.setParameter("status", "A");

        String result = new BillingDeleteWithoutNo2Action(
                mockSecurityInfoManager,
                mockBillingDao,
                mockAppointmentArchiveDao,
                mockAppointmentDao,
                mockCorrectionRecordService).execute();

        assertMethodNotAllowed(result);
        verifyNoInteractions(mockBillingDao, mockAppointmentArchiveDao, mockAppointmentDao, mockCorrectionRecordService);
    }

    private void assertMethodNotAllowed(String result) {
        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
    }
}
