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
package io.github.carlos_emr.carlos.admin.gate;

import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.admin.web.SecuritySearchResults2Action;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Admin security read 2Action gates")
@Tag("unit")
@Tag("admin")
@Tag("security")
class AdminSecurityReadGate2ActionUnitTest extends CarlosUnitTestBase {

    private MockHttpServletRequest request;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private MockedStatic<ServletActionContext> servletActionContext;
    private MockedStatic<LoggedInInfo> loggedInInfoStatic;

    static Stream<Arguments> readActions() {
        return Stream.of(
                Arguments.of("view add record",
                        (Function<SecurityInfoManager, ActionSupport>) ViewSecurityAddARecord2Action::new),
                Arguments.of("view search records",
                        (Function<SecurityInfoManager, ActionSupport>) ViewSecuritySearchRecordsHtm2Action::new),
                Arguments.of("view update security",
                        (Function<SecurityInfoManager, ActionSupport>) ViewSecurityUpdateSecurity2Action::new),
                Arguments.of("search results",
                        (Function<SecurityInfoManager, ActionSupport>) SecuritySearchResults2Action::new)
        );
    }

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);

        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);

        loggedInInfoStatic = mockStatic(LoggedInInfo.class);
        loggedInInfoStatic.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoStatic != null) {
            loggedInInfoStatic.close();
        }
        if (servletActionContext != null) {
            servletActionContext.close();
        }
    }

    @ParameterizedTest
    @MethodSource("readActions")
    @DisplayName("should allow read when _admin read privilege is granted")
    void shouldAllowRead_whenAdminReadGranted(
            String label,
            Function<SecurityInfoManager, ActionSupport> actionFactory) throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)).thenReturn(true);

        String result = actionFactory.apply(securityInfoManager).execute();

        assertThat(result).as("%s result", label).isEqualTo(ActionSupport.SUCCESS);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_admin", "r", null);
        verify(securityInfoManager, never()).hasPrivilege(loggedInInfo, "_admin.userAdmin", "r", null);
    }

    @ParameterizedTest
    @MethodSource("readActions")
    @DisplayName("should allow read when _admin.userAdmin read privilege is granted")
    void shouldAllowRead_whenUserAdminReadGranted(
            String label,
            Function<SecurityInfoManager, ActionSupport> actionFactory) throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)).thenReturn(false);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.userAdmin", "r", null)).thenReturn(true);

        String result = actionFactory.apply(securityInfoManager).execute();

        assertThat(result).as("%s result", label).isEqualTo(ActionSupport.SUCCESS);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_admin", "r", null);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_admin.userAdmin", "r", null);
    }

    @ParameterizedTest
    @MethodSource("readActions")
    @DisplayName("should reject read when read privileges are denied")
    void shouldRejectRead_whenReadPrivilegesDenied(
            String label,
            Function<SecurityInfoManager, ActionSupport> actionFactory) {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)).thenReturn(false);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.userAdmin", "r", null)).thenReturn(false);

        ActionSupport action = actionFactory.apply(securityInfoManager);

        assertThatThrownBy(action::execute)
                .as("%s denial", label)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin or _admin.userAdmin");
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_admin", "r", null);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_admin.userAdmin", "r", null);
    }
}
