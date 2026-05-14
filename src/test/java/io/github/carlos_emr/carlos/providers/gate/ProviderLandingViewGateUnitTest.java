/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.providers.gate;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("Provider landing view gates")
class ProviderLandingViewGateUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("GET");
        request.getSession(true).setAttribute("user", "999998");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "r", null))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (mockitoCloseable != null) {
            mockitoCloseable.close();
        }
    }

    @Test
    @DisplayName("should allow provider control rendering after privilege check")
    void shouldAllowProviderControlRenderingAfterPrivilegeCheck() throws Exception {
        String result = new ViewProviderControl2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getForwardedUrl()).isNull();
    }

    @Test
    @DisplayName("should allow appointment day rendering after privilege check")
    void shouldAllowAppointmentDayRenderingAfterPrivilegeCheck() throws Exception {
        String result = new ViewAppointmentAdminDay2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getForwardedUrl()).isNull();
    }

    @Test
    @DisplayName("should not render when appointment privilege is missing")
    void shouldNotRenderWhenAppointmentPrivilegeIsMissing() {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_appointment"), eq("r"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> new ViewAppointmentAdminDay2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_appointment");
        assertThat(response.getForwardedUrl()).isNull();
    }

    @Test
    @DisplayName("should redirect unauthenticated provider view requests before privilege check")
    void shouldRedirectUnauthenticatedProviderViewRequestsBeforePrivilegeCheck() throws Exception {
        request.getSession(false).invalidate();

        String result = new ViewProviderControl2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/logoutPage");
    }
}
