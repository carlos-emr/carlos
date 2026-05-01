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

import io.github.carlos_emr.carlos.billing.CA.dao.BillingInrDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingInr;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionContext;
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
 * Unit tests for {@link InrBillingCreate2Action}. Pins {@code _admin.billing/w}
 * privilege gate, POST-only contract, and the demoid-required validation.
 *
 * <p>The action wires its dependencies via {@code SpringUtils.getBean} in
 * field initializers; {@link CarlosUnitTestBase} sets up the static mock and
 * exposes {@link CarlosUnitTestBase#registerMock} for the bean registry.
 * Mock beans must be registered <em>before</em> the action is constructed.</p>
 *
 * @since 2026-04-29
 */
@DisplayName("InrBillingCreate2Action")
@Tag("unit")
@Tag("billing")
class InrBillingCreate2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private BillingInrDao mockBillingInrDao;
    @Mock private LoggedInInfo mockLoggedInInfo;

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

        // CarlosUnitTestBase sets up the SpringUtils static mock; we just
        // register the beans the action will pull during construction.
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(BillingInrDao.class, mockBillingInrDao);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        ActionContext.of().bind();
    }

    @AfterEach
    void tearDown() throws Exception {
        ActionContext.clear();
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldPersistAndReturnSuccess_whenPrivilegeGrantedAndDemoIdValid() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
        mockRequest.setParameter("demoid", "1234");

        InrBillingCreate2Action action = new InrBillingCreate2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(mockBillingInrDao).persist(any(BillingInr.class));
        verify(mockSecurityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull());
    }

    @Test
    void shouldThrowSecurityException_whenPrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(false);
        mockRequest.setParameter("demoid", "1234");

        InrBillingCreate2Action action = new InrBillingCreate2Action();

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.billing");
    }

    @Test
    void shouldNotPersist_whenPrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(false);
        mockRequest.setParameter("demoid", "1234");

        InrBillingCreate2Action action = new InrBillingCreate2Action();

        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verify(mockBillingInrDao, never()).persist(any(BillingInr.class));
    }

    @Test
    void shouldReturnError_whenDemoidMissing() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
        // No demoid parameter.

        InrBillingCreate2Action action = new InrBillingCreate2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.ERROR);
        verify(mockBillingInrDao, never()).persist(any(BillingInr.class));
    }

    @Test
    void shouldReturnError_whenDemoidNotNumeric() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
        mockRequest.setParameter("demoid", "not-a-number");

        InrBillingCreate2Action action = new InrBillingCreate2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.ERROR);
        verify(mockBillingInrDao, never()).persist(any(BillingInr.class));
    }

    @Test
    void shouldReturn405_whenNotPost() throws Exception {
        mockRequest.setMethod("GET");

        InrBillingCreate2Action action = new InrBillingCreate2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(405);
        verify(mockBillingInrDao, never()).persist(any(BillingInr.class));
    }
}
