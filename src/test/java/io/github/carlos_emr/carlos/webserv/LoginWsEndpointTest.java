/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
package io.github.carlos_emr.carlos.webserv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.test.base.CarlosSoapTestBase;
import io.github.carlos_emr.carlos.utility.NotAuthorisedException;
import io.github.carlos_emr.carlos.webserv.transfer_objects.LoginResultTransfer2;

/**
 * SOAP endpoint tests for {@link LoginWs} using CXF local transport.
 *
 * <p>LoginWs uses {@code WsUtils.checkAuthenticationAndSetLoggedInInfo()} and
 * {@code WsUtils.generateSecurityToken()}, both static methods that are mocked
 * in these tests.</p>
 *
 * @since 2026-03-31
 * @see CarlosSoapTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("soap")
@DisplayName("LoginWs SOAP endpoint tests")
class LoginWsEndpointTest extends CarlosSoapTestBase {

    @Mock
    private SecurityDao mockSecurityDao;

    @Mock
    private ProviderDao mockProviderDao;

    private MockedStatic<WsUtils> mockedWsUtils;

    @Override
    protected Object getServiceBean() {
        LoginWs ws = new LoginWs();
        injectDependency(ws, "securityDao", mockSecurityDao);
        injectDependency(ws, "providerDao", mockProviderDao);
        return ws;
    }

    @Override
    protected Class<?> getServiceInterface() {
        return LoginWs.class;
    }

    @BeforeEach
    void setUpWsUtils() {
        mockedWsUtils = mockStatic(WsUtils.class);
    }

    @AfterEach
    void tearDownWsUtils() {
        if (mockedWsUtils != null) {
            mockedWsUtils.close();
        }
    }

    @Test
    @Disabled("TODO: WsUtils static initializer calls SpringUtils.getBean(ProviderDao.class), cannot be instrumented by Mockito")
    @DisplayName("should return login result with security token on valid login")
    void shouldReturnLoginResult_whenCredentialsValid() throws NotAuthorisedException {
        Security security = new Security();
        security.setSecurityNo(1);
        security.setProviderNo("999998");
        when(mockSecurityDao.findByUserName("testuser")).thenReturn(List.of(security));

        Provider provider = new Provider();
        provider.setProviderNo("999998");
        provider.setFirstName("Test");
        provider.setLastName("Doctor");
        when(mockProviderDao.getProvider("999998")).thenReturn(provider);

        mockedWsUtils.when(() -> WsUtils.checkAuthenticationAndSetLoggedInInfo(
            any(HttpServletRequest.class), eq(security), eq("testpass")))
            .thenReturn(true);
        mockedWsUtils.when(() -> WsUtils.generateSecurityToken(security))
            .thenReturn("mock-token-123");

        LoginWs proxy = createClient(LoginWs.class);
        LoginResultTransfer2 result = proxy.login2("testuser", "testpass");

        assertThat(result).isNotNull();
        assertThat(result.getSecurityTokenKey()).isEqualTo("mock-token-123");
    }
}
