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

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Gate of the document description template page: the clinic-wide list belongs to administration,
 * a provider's own list to their preferences (issue #5057).
 */
@Tag("unit")
@Tag("security")
@DisplayName("DisplayDocumentDescriptionTemplate2Action")
class DisplayDocumentDescriptionTemplate2ActionUnitTest {

    private MockHttpServletRequest request;
    private SecurityInfoManager security;
    private LoggedInInfo user;
    private MockedStatic<ServletActionContext> servletContext;
    private MockedStatic<LoggedInInfo> loggedInInfoStatic;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/carlos/admin/DisplayDocumentDescriptionTemplate");
        security = mock(SecurityInfoManager.class);
        user = mock(LoggedInInfo.class);
        servletContext = mockStatic(ServletActionContext.class);
        servletContext.when(ServletActionContext::getRequest).thenReturn(request);
        loggedInInfoStatic = mockStatic(LoggedInInfo.class);
        loggedInInfoStatic.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoStatic.close();
        servletContext.close();
    }

    private void grant(String object, String right) {
        when(security.hasPrivilege(eq(user), eq(object), eq(right), isNull())).thenReturn(true);
    }

    private String open() {
        return new DisplayDocumentDescriptionTemplate2Action(security).execute();
    }

    @Test
    @DisplayName("should open a provider's own list with preference write and no administration right")
    void shouldOpenOwnList_forProviderWithPreferenceWrite() {
        grant("_pref", "w");

        assertThat(open()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should refuse a provider's own list without preference write, even to an administrator")
    void shouldRefuseOwnList_withoutPreferenceWrite() {
        grant("_admin", "r");
        grant("_admin", "w");
        grant("_pref", "r");

        assertThatThrownBy(this::open)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_pref)");
    }

    @ParameterizedTest(name = "{0} read")
    @ValueSource(strings = {"_admin", "_admin.document"})
    @DisplayName("should open the clinic-wide list for an administration reader")
    void shouldOpenClinicList_forAdministrationReader(String object) {
        request.setParameter("setDefault", "true");
        grant(object, "r");

        assertThat(open()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should refuse the clinic-wide list to a provider who only holds preferences")
    void shouldRefuseClinicList_forProviderWithoutAdministration() {
        request.setParameter("setDefault", "true");
        grant("_pref", "w");
        grant("_edoc", "r");

        assertThatThrownBy(this::open)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin | _admin.document)");
    }

    @ParameterizedTest(name = "setDefault={0}")
    @ValueSource(strings = {"false", "TRUE", "1", ""})
    @DisplayName("should treat any setDefault but the exact value true as the provider's own list")
    void shouldOpenOwnList_whenSetDefaultIsNotTrue(String value) {
        request.setParameter("setDefault", value);
        grant("_pref", "w");

        assertThat(open()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should refuse both lists without a session")
    void shouldRefusePage_withoutSession() {
        loggedInInfoStatic.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        assertThatThrownBy(this::open).isInstanceOf(SecurityException.class);
        request.setParameter("setDefault", "true");
        assertThatThrownBy(this::open).isInstanceOf(SecurityException.class);
    }
}
