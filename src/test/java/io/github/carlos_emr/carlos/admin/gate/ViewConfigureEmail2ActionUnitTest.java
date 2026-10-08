/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.admin.gate;

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
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

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
 * The Configure Email page gate: {@code _admin} read, and the clinic email footer logo's state for
 * the page (issue #3981).
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
@DisplayName("Configure Email page gate")
class ViewConfigureEmail2ActionUnitTest {

    private final SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
    private final EmailFooterLogoService logoService = mock(EmailFooterLogoService.class);
    private MockHttpServletRequest request;
    private MockedStatic<ServletActionContext> servletActionContext;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/admin/ViewConfigureEmail");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
    }

    @AfterEach
    void tearDown() {
        servletActionContext.close();
    }

    @Test
    @DisplayName("should give the page the logo's size when the clinic has one")
    void shouldExposeLogoSize_whenLogoSet() throws Exception {
        allowAdminRead();
        EmailFooterLogo logo = new EmailFooterLogo();
        logo.setWidth(320);
        logo.setHeight(80);
        when(logoService.currentLogo()).thenReturn(logo);

        assertThat(action().execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute("clinicLogoSet")).isEqualTo(true);
        assertThat(request.getAttribute("clinicLogoWidth")).isEqualTo(320);
        assertThat(request.getAttribute("clinicLogoHeight")).isEqualTo(80);
    }

    @Test
    @DisplayName("should tell the page there is no logo")
    void shouldMarkNoLogo_whenNoneSet() throws Exception {
        allowAdminRead();

        action().execute();

        assertThat(request.getAttribute("clinicLogoSet")).isEqualTo(false);
        assertThat(request.getAttribute("clinicLogoWidth")).isNull();
    }

    @Test
    @DisplayName("should refuse a user without _admin read before reading the logo")
    void shouldRefuse_whenAdminReadMissing() {
        assertThatThrownBy(() -> action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin)");
        verifyNoInteractions(logoService);
    }

    private void allowAdminRead() {
        when(securityInfoManager.hasPrivilege(any(), eq("_admin"), eq("r"), isNull(String.class))).thenReturn(true);
    }

    private ViewConfigureEmail2Action action() {
        return new ViewConfigureEmail2Action(securityInfoManager, logoService);
    }
}
