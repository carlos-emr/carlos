/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.action;

import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.EmailFooterLogo;
import io.github.carlos_emr.carlos.email.core.EmailFooterLogoService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Serving the clinic's email footer logo (issue #3981): who may see it, which methods are
 * answered, and the headers that keep the browser from treating it as anything but a picture.
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
@DisplayName("Clinic email logo")
class ClinicEmailLogo2ActionUnitTest {

    private final SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
    private final EmailFooterLogoService logoService = mock(EmailFooterLogoService.class);
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servletActionContext;

    @BeforeEach
    void setUp() {
        response = new MockHttpServletResponse();
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        servletActionContext.close();
    }

    @Test
    @DisplayName("should send the logo with its type, nosniff and no shared caching")
    void shouldServeLogo_whenUserCanSendEmail() throws Exception {
        allow("_email");
        when(logoService.currentLogo()).thenReturn(logo());

        assertThat(run("GET")).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getContentType()).isEqualTo("image/png");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-cache");
        assertThat(response.getContentAsByteArray()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("should let an administrator without email rights see it, and send no body for HEAD")
    void shouldAnswerHead_whenUserIsAdministrator() throws Exception {
        allow("_admin");
        when(logoService.currentLogo()).thenReturn(logo());

        run("HEAD");

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getContentLength()).isEqualTo(3);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should answer 404 when the clinic has no logo")
    void shouldAnswerNotFound_whenNoLogo() throws Exception {
        allow("_email");

        run("GET");

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    @DisplayName("should refuse a user with neither email nor administration rights")
    void shouldRefuse_whenUserLacksRights() {
        assertThatThrownBy(() -> run("GET"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_email)");
        verifyNoInteractions(logoService);
    }

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    @DisplayName("should answer only GET and HEAD")
    void shouldRefuseOtherMethods_withMethodNotAllowed(String method) throws Exception {
        allow("_email");

        assertThat(run(method)).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("GET, HEAD");
        verifyNoInteractions(logoService);
    }

    private void allow(String objectName) {
        when(securityInfoManager.hasPrivilege(any(), eq(objectName), eq(SecurityInfoManager.READ), isNull(String.class)))
                .thenReturn(true);
    }

    private String run(String method) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/email/clinicEmailLogo");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        return new ClinicEmailLogo2Action(securityInfoManager, logoService).execute();
    }

    private static EmailFooterLogo logo() {
        EmailFooterLogo logo = new EmailFooterLogo();
        logo.setContentType("image/png");
        logo.setImageData(new byte[] {1, 2, 3});
        return logo;
    }
}
