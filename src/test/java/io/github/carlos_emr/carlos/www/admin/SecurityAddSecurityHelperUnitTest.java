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
package io.github.carlos_emr.carlos.www.admin;

import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.jsp.PageContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("security")
@DisplayName("SecurityAddSecurityHelper")
class SecurityAddSecurityHelperUnitTest extends CarlosUnitTestBase {

    private static final String RAW_PASSWORD = "S3curePassword!";
    private static final String RAW_PIN = "1234";
    private static final String HASHED_PASSWORD = "{bcrypt}password";
    private static final String HASHED_PIN = "{bcrypt}pin";

    private AutoCloseable mockitoCloseable;
    private SecurityAddSecurityHelper helper;

    @Mock private SecurityDao securityDao;
    @Mock private SecurityManager securityManager;
    @Mock private PageContext pageContext;
    @Mock private ServletRequest request;
    @Mock private HttpSession session;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        registerMock(SecurityDao.class, securityDao);
        registerMock(SecurityManager.class, securityManager);
        helper = new SecurityAddSecurityHelper();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        if (mockitoCloseable != null) {
            mockitoCloseable.close();
        }
    }

    @Test
    @DisplayName("should persist hashed PIN and default invalid lock settings when adding provider")
    void shouldPersistHashedPinAndDefaultInvalidLockSettings_whenAddingProvider() {
        when(pageContext.getRequest()).thenReturn(request);
        when(pageContext.getSession()).thenReturn(session);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getParameter("password")).thenReturn(RAW_PASSWORD);
        when(request.getParameter("pin")).thenReturn(RAW_PIN);
        when(request.getParameter("provider_no")).thenReturn("999998");
        when(request.getParameter("user_name")).thenReturn("carlosdoc");
        when(request.getParameter("b_ExpireSet")).thenReturn("invalid");
        when(request.getParameter("b_LocalLockSet")).thenReturn("1");
        when(request.getParameter("b_RemoteLockSet")).thenReturn(null);
        when(request.getParameter("forcePasswordReset")).thenReturn("1");
        when(request.getParameter("enableMfa")).thenReturn(null);
        when(securityManager.encodePassword(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);
        when(securityManager.encodePin(RAW_PIN)).thenReturn(HASHED_PIN);
        when(securityDao.findByProviderNo("999998")).thenReturn(Collections.emptyList());
        when(securityDao.findByUserName("carlosdoc")).thenReturn(Collections.emptyList());
        ArgumentCaptor<Security> securityCaptor = ArgumentCaptor.forClass(Security.class);

        helper.addProvider(pageContext);

        verify(securityDao).persist(securityCaptor.capture());
        Security persisted = securityCaptor.getValue();
        assertThat(persisted.getPassword()).isEqualTo(HASHED_PASSWORD);
        assertThat(persisted.getPin()).isEqualTo(HASHED_PIN);
        assertThat(persisted.getBExpireset()).isZero();
        assertThat(persisted.getBLocallockset()).isOne();
        assertThat(persisted.getBRemotelockset()).isZero();
        assertThat(persisted.getPasswordUpdateDate()).isNotNull();
        assertThat(persisted.getPinUpdateDate()).isNotNull();
        verify(pageContext).setAttribute("message", "admin.securityaddsecurity.msgAdditionSuccess");
    }

    @Test
    @DisplayName("should parse lock setting when value is valid")
    void shouldParseLockSetting_whenValueIsValid() {
        assertThat(SecurityAddSecurityHelper.parseLockSetting("0")).isZero();
        assertThat(SecurityAddSecurityHelper.parseLockSetting("1")).isOne();
    }

    @Test
    @DisplayName("should default lock setting when value is missing or invalid")
    void shouldDefaultLockSetting_whenValueIsMissingOrInvalid() {
        assertThat(SecurityAddSecurityHelper.parseLockSetting(null)).isZero();
        assertThat(SecurityAddSecurityHelper.parseLockSetting("")).isZero();
        assertThat(SecurityAddSecurityHelper.parseLockSetting("invalid")).isZero();
        assertThat(SecurityAddSecurityHelper.parseLockSetting("-1")).isZero();
        assertThat(SecurityAddSecurityHelper.parseLockSetting("2")).isZero();
        assertThat(SecurityAddSecurityHelper.parseLockSetting("1.5")).isZero();
        assertThat(SecurityAddSecurityHelper.parseLockSetting(" 1 ")).isZero();
        assertThat(SecurityAddSecurityHelper.parseLockSetting("2147483648")).isZero();
    }
}
