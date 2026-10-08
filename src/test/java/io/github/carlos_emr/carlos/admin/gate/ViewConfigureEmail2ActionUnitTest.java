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

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.model.EmailFooterLogo;
import io.github.carlos_emr.carlos.email.core.EmailFooterLogoService;
import io.github.carlos_emr.carlos.email.core.EmailFooterService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The Configure Email page's clinic footer section (issue #4093, follow-up to #3981): the footer
 * shown, the fingerprint its form sends back, and the clinic-change rule its wording follows; and
 * the clinic email footer logo's state for the page (issue #3981).
 *
 * @since 2026-10-07
 */
@DisplayName("ViewConfigureEmail2Action")
@Tag("unit")
@Tag("fast")
@Tag("admin")
@Tag("email")
@Tag("security")
class ViewConfigureEmail2ActionUnitTest {

    private MockHttpServletRequest request;
    private SecurityInfoManager securityInfoManager;
    private EmailFooterService emailFooterService;
    private EmailFooterLogoService logoService;
    private LoggedInInfo loggedInInfo;
    private MockedStatic<ServletActionContext> servletActionContext;
    private MockedStatic<LoggedInInfo> loggedInInfoStatic;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/admin/ViewConfigureEmail");
        securityInfoManager = mock(SecurityInfoManager.class);
        emailFooterService = mock(EmailFooterService.class);
        logoService = mock(EmailFooterLogoService.class);
        loggedInInfo = mock(LoggedInInfo.class);
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        loggedInInfoStatic = mockStatic(LoggedInInfo.class);
        loggedInInfoStatic.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoStatic.close();
        servletActionContext.close();
    }

    @Test
    @DisplayName("should show the clinic footer with its fingerprint and the clinic-change rule")
    void shouldExposeClinicFooter_forConfigureEmailPage() throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)).thenReturn(true);
        when(emailFooterService.clinicDefault()).thenReturn("Riverside Clinic\nBook online");
        when(emailFooterService.ownFootersReplacedOnClinicChange()).thenReturn(true);

        String result = action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("clinicFooter")).isEqualTo("Riverside Clinic\nBook online");
        assertThat(request.getAttribute("clinicFooterFingerprint"))
                .isEqualTo(EmailFooterService.fingerprint("Riverside Clinic\nBook online"));
        assertThat(request.getAttribute("ownFootersReplacedOnClinicChange")).isEqualTo(true);
    }

    @Test
    @DisplayName("should refuse a user without _admin read before reading the footer")
    void shouldThrowSecurityException_whenAdminReadMissing() {
        assertThatThrownBy(() -> action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin)");
        verifyNoInteractions(emailFooterService, logoService);
    }

    @Test
    @DisplayName("should give the page the logo's size when the clinic has one")
    void shouldExposeLogoSize_whenLogoSet() throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)).thenReturn(true);
        EmailFooterLogo logo = new EmailFooterLogo();
        logo.setWidth(320);
        logo.setHeight(80);
        when(logoService.currentLogo()).thenReturn(logo);

        action().execute();

        assertThat(request.getAttribute("clinicLogoSet")).isEqualTo(true);
        assertThat(request.getAttribute("clinicLogoWidth")).isEqualTo(320);
        assertThat(request.getAttribute("clinicLogoHeight")).isEqualTo(80);
    }

    @Test
    @DisplayName("should tell the page there is no logo")
    void shouldMarkNoLogo_whenNoneSet() throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)).thenReturn(true);

        action().execute();

        assertThat(request.getAttribute("clinicLogoSet")).isEqualTo(false);
        assertThat(request.getAttribute("clinicLogoWidth")).isNull();
    }

    private ViewConfigureEmail2Action action() {
        return new ViewConfigureEmail2Action(securityInfoManager, emailFooterService, logoService);
    }
}
