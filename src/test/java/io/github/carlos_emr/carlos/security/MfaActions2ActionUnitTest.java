/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package io.github.carlos_emr.carlos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.MfaManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Authorization + HTTP-method contract for the MFA-secret reset action. Verifies that the privileged
 * reset cannot be triggered by GET (CSRF-style) and requires security-administration write.
 */
@DisplayName("MfaActions2Action authorization")
@Tag("unit")
@Tag("security")
class MfaActions2ActionUnitTest extends CarlosUnitTestBase {

    private SecurityManager securityManager;
    private MfaManager mfaManager;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        securityManager = mock(SecurityManager.class);
        mfaManager = mock(MfaManager.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityManager.class, securityManager);
        registerMock(MfaManager.class, mfaManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setParameter("method", "resetMfa");
        request.setParameter("securityId", "42");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
    }

    @Test
    @DisplayName("should reject GET with 405 and not reset MFA")
    void shouldRejectGet_andNotResetMfa() {
        request.setMethod("GET");
        try (MockedStatic<ServletActionContext> sac = mockStatic(ServletActionContext.class)) {
            sac.when(ServletActionContext::getRequest).thenReturn(request);
            sac.when(ServletActionContext::getResponse).thenReturn(response);

            MfaActions2Action action = new MfaActions2Action();
            action.execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            verify(mfaManager, never()).resetMfaSecret(any(), any());
        }
    }

    @Test
    @DisplayName("should throw SecurityException when caller lacks security-admin write")
    void shouldThrowSecurityException_whenNotAdmin() {
        request.setMethod("POST");
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);
        try (MockedStatic<ServletActionContext> sac = mockStatic(ServletActionContext.class)) {
            sac.when(ServletActionContext::getRequest).thenReturn(request);
            sac.when(ServletActionContext::getResponse).thenReturn(response);

            MfaActions2Action action = new MfaActions2Action();

            assertThatThrownBy(action::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("missing required sec object (_admin or _admin.userAdmin)");
            verify(mfaManager, never()).resetMfaSecret(any(), any());
        }
    }

    @Test
    @DisplayName("should reset MFA when POST and caller has security-admin write")
    void shouldResetMfa_whenPostAndAdmin() {
        request.setMethod("POST");
        Security security = mock(Security.class);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);
        when(securityManager.find(any(), any())).thenReturn(security);
        try (MockedStatic<ServletActionContext> sac = mockStatic(ServletActionContext.class)) {
            sac.when(ServletActionContext::getRequest).thenReturn(request);
            sac.when(ServletActionContext::getResponse).thenReturn(response);

            MfaActions2Action action = new MfaActions2Action();
            action.execute();

            verify(mfaManager).resetMfaSecret(loggedInInfo, security);
        }
    }
}
