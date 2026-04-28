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

import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RemovePaymentType2Action}.
 *
 * @since 2026-04-27
 */
@DisplayName("RemovePaymentType2Action")
@Tag("unit")
@Tag("billing")
class RemovePaymentType2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private LoggedInInfo mockLoggedInInfo;
    @Mock private BillingPaymentTypeDao mockTypeDao;
    @Mock private BillingONPaymentDao mockPaymentDao;

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

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldRemoveAndReturnRet0_whenTypeIsUnused() throws Exception {
        mockRequest.setParameter("paymentTypeId", "5");
        when(mockPaymentDao.getCountOfPaymentByPaymentTypeId(5)).thenReturn(0);

        RemovePaymentType2Action action =
                new RemovePaymentType2Action(mockSecurityInfoManager, mockTypeDao, mockPaymentDao);
        assertThat(action.execute()).isNull();

        verify(mockTypeDao, times(1)).remove(5);
        assertThat(mockResponse.getContentAsString()).contains("\"ret\":0");
    }

    @Test
    void shouldRefuseRemoveAndReturnRet1_whenTypeIsInUse() throws Exception {
        mockRequest.setParameter("paymentTypeId", "5");
        when(mockPaymentDao.getCountOfPaymentByPaymentTypeId(5)).thenReturn(3);

        RemovePaymentType2Action action =
                new RemovePaymentType2Action(mockSecurityInfoManager, mockTypeDao, mockPaymentDao);
        action.execute();

        verify(mockTypeDao, never()).remove(any());
        assertThat(mockResponse.getContentAsString()).contains("\"ret\":1");
        assertThat(mockResponse.getContentAsString()).contains("has been used");
    }

    @Test
    void shouldReturnRet1_whenIdParamIsNonNumeric() throws Exception {
        mockRequest.setParameter("paymentTypeId", "not-a-number");

        RemovePaymentType2Action action =
                new RemovePaymentType2Action(mockSecurityInfoManager, mockTypeDao, mockPaymentDao);
        action.execute();

        verify(mockTypeDao, never()).remove(any());
        assertThat(mockResponse.getContentAsString()).contains("\"ret\":1");
    }

    @Test
    void shouldThrowSecurityException_whenLackingBillingWritePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);

        RemovePaymentType2Action action =
                new RemovePaymentType2Action(mockSecurityInfoManager, mockTypeDao, mockPaymentDao);
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }
}
