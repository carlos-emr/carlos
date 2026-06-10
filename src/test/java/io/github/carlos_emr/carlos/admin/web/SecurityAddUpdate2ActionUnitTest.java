/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.admin.web;

import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Security add/update 2Action gates")
@Tag("unit")
@Tag("admin")
@Tag("security")
class SecurityAddUpdate2ActionUnitTest extends CarlosUnitTestBase {

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private MockedStatic<ServletActionContext> servletActionContext;
    private MockedStatic<LoggedInInfo> loggedInInfoStatic;

    static Stream<Arguments> securityActions() {
        return Stream.of(
                Arguments.of("add", (Function<SecurityInfoManager, ActionSupport>) SecurityAddSecurity2Action::new),
                Arguments.of("update", (Function<SecurityInfoManager, ActionSupport>) SecurityUpdate2Action::new)
        );
    }

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);

        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("securityActions")
    @DisplayName("should allow POST when _admin write privilege is granted")
    void shouldAllowPost_whenAdminWriteGranted(
            String label, Function<SecurityInfoManager, ActionSupport> actionFactory) throws Exception {
        request.setMethod("POST");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);

        String result = actionFactory.apply(securityInfoManager).execute();

        assertThat(result).as("%s action result", label).isEqualTo(ActionSupport.SUCCESS);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_admin", "w", null);
        verify(securityInfoManager, never()).hasPrivilege(
                eq(loggedInInfo), eq("_admin.userAdmin"), eq("w"), nullable(String.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("securityActions")
    @DisplayName("should allow POST when _admin.userAdmin write privilege is granted")
    void shouldAllowPost_whenUserAdminWriteGranted(
            String label, Function<SecurityInfoManager, ActionSupport> actionFactory) throws Exception {
        request.setMethod("POST");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(false);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.userAdmin", "w", null)).thenReturn(true);

        String result = actionFactory.apply(securityInfoManager).execute();

        assertThat(result).as("%s action result", label).isEqualTo(ActionSupport.SUCCESS);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_admin", "w", null);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_admin.userAdmin", "w", null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("securityActions")
    @DisplayName("should reject POST when write privileges are denied")
    void shouldRejectPost_whenWritePrivilegesDenied(
            String label, Function<SecurityInfoManager, ActionSupport> actionFactory) {
        request.setMethod("POST");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(false);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.userAdmin", "w", null)).thenReturn(false);

        ActionSupport action = actionFactory.apply(securityInfoManager);

        assertThatThrownBy(action::execute)
                .as("%s action rejection", label)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("(_admin or _admin.userAdmin)");
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_admin", "w", null);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_admin.userAdmin", "w", null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("securityActions")
    @DisplayName("should reject GET after authorization succeeds")
    void shouldRejectGet_whenAdminWriteGranted(
            String label, Function<SecurityInfoManager, ActionSupport> actionFactory) throws Exception {
        request.setMethod("GET");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);

        String result = actionFactory.apply(securityInfoManager).execute();

        assertThat(result).as("%s action result", label).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).as("%s response status", label)
                .isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).as("%s Allow header", label).isEqualTo("POST");
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_admin", "w", null);
    }
}
