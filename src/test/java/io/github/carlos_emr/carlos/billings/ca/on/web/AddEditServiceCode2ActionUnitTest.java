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

import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingPercLimitDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CSSStylesDAO;
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
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AddEditServiceCode2Action} covering the
 * mutation-intent gating contract:
 * <ul>
 *   <li>plain GET (no {@code action} param) renders the form</li>
 *   <li>GET + {@code action=delete} returns 405 with {@code Allow: POST}</li>
 *   <li>POST + {@code action=delete} returns SUCCESS (passes the gate)</li>
 *   <li>missing session throws {@code SecurityException} before any
 *       privilege call (avoids the noisy internal ERROR from
 *       {@code SecurityInfoManagerImpl.hasPrivilege(null, ...)})</li>
 * </ul>
 *
 * @since 2026-04-25
 */
@DisplayName("AddEditServiceCode2Action")
@Tag("unit")
@Tag("billing")
class AddEditServiceCode2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("GET");

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        // AddEditServiceCodeViewModelAssembler's no-arg ctor resolves three DAOs
        // via SpringUtils.getBean — empty mocks suffice for gate testing.
        registerMock(BillingServiceDao.class, org.mockito.Mockito.mock(BillingServiceDao.class));
        registerMock(BillingPercLimitDao.class, org.mockito.Mockito.mock(BillingPercLimitDao.class));
        registerMock(CSSStylesDAO.class, org.mockito.Mockito.mock(CSSStylesDAO.class));

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldReturnSuccess_whenAuthorizedGetWithoutMutationIntent() throws Exception {
        AddEditServiceCode2Action action = new AddEditServiceCode2Action(mockSecurityInfoManager, org.mockito.Mockito.mock(io.github.carlos_emr.carlos.billings.ca.on.assembler.AddEditServiceCodeViewModelAssembler.class));
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    void shouldReturnSuccess_whenAuthorizedPostWithMutationIntent() throws Exception {
        mockRequest.setMethod("POST");
        mockRequest.setParameter("action", "delete");

        AddEditServiceCode2Action action = new AddEditServiceCode2Action(mockSecurityInfoManager, org.mockito.Mockito.mock(io.github.carlos_emr.carlos.billings.ca.on.assembler.AddEditServiceCodeViewModelAssembler.class));
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    void shouldReturn405WithAllowHeader_whenGetWithMutationIntent() throws Exception {
        // RFC 7231 §6.5.5 — a 405 response MUST include an Allow header.
        // Sibling 2Actions in this PR set Allow on their 405 paths; this
        // action lacked it before round-11.
        mockRequest.setParameter("action", "delete");

        AddEditServiceCode2Action action = new AddEditServiceCode2Action(mockSecurityInfoManager, org.mockito.Mockito.mock(io.github.carlos_emr.carlos.billings.ca.on.assembler.AddEditServiceCodeViewModelAssembler.class));
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    void shouldThrowSecurityException_whenSessionMissing() {
        // hasPrivilege(null, ...) reaches SecurityInfoManagerImpl and emits
        // a noisy internal ERROR before returning false; an explicit guard
        // converts that into a clean SecurityException at the gate.
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        AddEditServiceCode2Action action = new AddEditServiceCode2Action(mockSecurityInfoManager, org.mockito.Mockito.mock(io.github.carlos_emr.carlos.billings.ca.on.assembler.AddEditServiceCodeViewModelAssembler.class));
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing session");
    }

    @Test
    void shouldThrowSecurityException_whenLackingPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(false);

        AddEditServiceCode2Action action = new AddEditServiceCode2Action(mockSecurityInfoManager, org.mockito.Mockito.mock(io.github.carlos_emr.carlos.billings.ca.on.assembler.AddEditServiceCodeViewModelAssembler.class));
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.billing");
    }
}
