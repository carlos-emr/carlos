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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@code _billing/w} privilege on the BC bill-delete-by-billing-no
 * action and verifies the soft-delete is not invoked when the gate denies.
 *
 * @since 2026-04-30
 */
@DisplayName("BillingDeleteNoAppt2Action (BC)")
@Tag("unit")
@Tag("billing")
class BillingDeleteNoAppt2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private BillingDao mockBillingDao;
    @Mock private BillingmasterDAO mockBillingmasterDao;
    @Mock private LoggedInInfo mockLoggedInInfo;
    @Mock private Billing mockBilling;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("POST");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(BillingDao.class, mockBillingDao);
        registerMock(BillingmasterDAO.class, mockBillingmasterDao);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockBillingmasterDao.getBillingMasterByBillingNo(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldThrowSecurityException_whenPrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);
        mockRequest.setParameter("billCode", "A001");
        mockRequest.setParameter("billing_no", "42");

        BillingDeleteNoAppt2Action action = new BillingDeleteNoAppt2Action();

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
        verify(mockBillingDao, never()).merge(any());
    }

    @Test
    void shouldSoftDelete_whenPrivilegeGrantedAndValidParams() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockBillingDao.find(42)).thenReturn(mockBilling);
        mockRequest.setParameter("billCode", "A001");
        mockRequest.setParameter("billing_no", "42");

        BillingDeleteNoAppt2Action action = new BillingDeleteNoAppt2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(mockBilling).setStatus("D");
        verify(mockBillingDao).merge(mockBilling);
    }

    @Test
    void shouldReturnCannotDelete_whenBillCodeStartsWithB() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        // A "B" prefix indicates the bill has already been billed (B for billed)
        // and is therefore not deletable. The legacy contract was a string-prefix
        // check; this pins it so a future refactor that drops the guard
        // surfaces here.
        mockRequest.setParameter("billCode", "B001");
        mockRequest.setParameter("billing_no", "42");

        BillingDeleteNoAppt2Action action = new BillingDeleteNoAppt2Action();

        assertThat(action.execute()).isEqualTo("cannotDelete");
        assertThat(mockRequest.getAttribute("cannotDelete")).isEqualTo(Boolean.TRUE);
        verify(mockBillingDao, never()).merge(any());
    }

    @Test
    void shouldReturnError_whenBillCodeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);

        BillingDeleteNoAppt2Action action = new BillingDeleteNoAppt2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.ERROR);
        verify(mockBillingDao, never()).merge(any());
    }
}
