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
package io.github.carlos_emr.carlos.admin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

import io.github.carlos_emr.carlos.lab.service.ProviderLinkingRulesService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

@Tag("unit")
@Tag("security")
@DisplayName("Provider Linking Rules admin actions")
class ProviderLinkingRules2ActionUnitTest {

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private ProviderLinkingRulesService rules;
    private LoggedInInfo loggedInInfo;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        response = new MockHttpServletResponse();
        security = mock(SecurityInfoManager.class);
        rules = mock(ProviderLinkingRulesService.class);
        loggedInInfo = mock(LoggedInInfo.class);
        servlet = mockStatic(ServletActionContext.class);
        login = mockStatic(LoggedInInfo.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        login.close();
        servlet.close();
    }

    private ProviderLinkingRules2Action view() {
        return new ProviderLinkingRules2Action(security, rules);
    }

    private SaveProviderLinkingRules2Action save() {
        return new SaveProviderLinkingRules2Action(security, rules);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("view should require _admin read before reading the setting")
    void shouldThrowSecurityException_whenViewerLacksAdminRead(String method) {
        request.setMethod(method);
        assertThatThrownBy(() -> view().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin)");
        verifyNoInteractions(rules);
    }

    @Test
    @DisplayName("view should publish the current state, write access and saved flag")
    void shouldPublishState_whenAdminReads() {
        request.setMethod("GET");
        request.setParameter("saved", "true");
        when(security.hasPrivilege(loggedInInfo, "_admin", "r", null)).thenReturn(true);
        when(security.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(false);
        when(rules.isEnabled()).thenReturn(true);

        assertThat(view().execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute("providerLinkingRulesEnabled")).isEqualTo(true);
        assertThat(request.getAttribute("providerLinkingRulesCanWrite")).isEqualTo(false);
        assertThat(request.getAttribute("providerLinkingRulesSaved")).isEqualTo(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    @DisplayName("view should answer 405 to anything but GET and HEAD")
    void shouldRejectUnsafeMethod_onViewRoute(String method) {
        request.setMethod(method);
        assertThat(view().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("GET, HEAD");
        verifyNoInteractions(security, rules);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT"})
    @DisplayName("save should answer 405 before authorization or any write")
    void shouldRejectNonPost_beforeAnyWrite(String method) throws Exception {
        request.setMethod(method);
        request.setParameter("enabled", "true");

        assertThat(save().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(security, rules);
    }

    @Test
    @DisplayName("save should require _admin write, not just lab rights")
    void shouldThrowSecurityException_whenSaverLacksAdminWrite() {
        request.setMethod("POST");
        request.setParameter("enabled", "true");
        when(security.hasPrivilege(loggedInInfo, "_lab", "w", null)).thenReturn(true);

        assertThatThrownBy(() -> save().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin)");
        verifyNoInteractions(rules);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("save should store the switch and redirect back to the view")
    void shouldSaveSwitchAndRedirect_whenAdminPosts(boolean enabled) throws Exception {
        request.setMethod("POST");
        if (enabled) {
            request.setParameter("enabled", "true");
        }
        when(security.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);

        assertThat(save().execute()).isEqualTo(ActionSupport.NONE);

        verify(rules).setEnabled(loggedInInfo, enabled);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/admin/providerLinkingRules?saved=true");
    }

    @Test
    @DisplayName("save should treat any value but true as off")
    void shouldSaveOff_whenEnabledValueIsNotTrue() throws Exception {
        request.setMethod("POST");
        request.setParameter("enabled", "on");
        when(security.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);

        save().execute();

        verify(rules).setEnabled(loggedInInfo, false);
    }
}
