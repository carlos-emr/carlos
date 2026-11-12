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
package io.github.carlos_emr.carlos.providers.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the schedule tab-count fragment answers each tab only for roles that can see it.
 *
 * <p>Issue #2689: a receptionist's schedule polled the Messenger count, whose tag threw for a
 * role without {@code _msg}, and the poll answered HTTP 500. The gate now validates the tab id
 * and requires the privilege the tab's menu entry is rendered under.</p>
 *
 * <p>The base class mocks SpringUtils and LogAction; this test also mocks the Struts and
 * LoggedInInfo statics, and the action receives its SecurityInfoManager directly.</p>
 */
@Tag("unit")
@Tag("security")
@DisplayName("Schedule tab alert refresh gate")
class ViewTabAlertsRefresh2ActionUnitTest extends CarlosUnitTestBase {

    private final SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
    private final LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
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

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "r", null)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    private String refresh(String tab) throws Exception {
        if (tab != null) {
            request.setParameter("id", tab);
        }
        return new ViewTabAlertsRefresh2Action(securityInfoManager).execute();
    }

    @ParameterizedTest(name = "{0} needs {1}")
    @CsvSource({
            "oscar_new_lab, _appointment.doctorLink",
            "oscar_new_tickler, _tickler",
            "oscar_new_msg, _msg",
            "oscar_aged_consults, _con",
            "oscar_scratch, _appointment"})
    @DisplayName("should render a tab's count when the role holds that tab's privilege")
    void shouldRenderTabCount_whenRoleHoldsTabPrivilege(String tab, String securityObject) throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, securityObject, "r", null)).thenReturn(true);

        assertThat(refresh(tab)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @ParameterizedTest(name = "{0} refused without {1}")
    @CsvSource({
            "oscar_new_lab, _appointment.doctorLink",
            "oscar_new_tickler, _tickler",
            "oscar_new_msg, _msg",
            "oscar_aged_consults, _con"})
    @DisplayName("should refuse a tab whose menu entry the role cannot see")
    void shouldRefuseTab_whenRoleLacksTabPrivilege(String tab, String securityObject) {
        when(securityInfoManager.hasPrivilege(loggedInInfo, securityObject, "r", null)).thenReturn(false);

        assertThatThrownBy(() -> refresh(tab))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (" + securityObject + ")");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"oscar_unknown", "OSCAR_NEW_MSG", "../tabAlertsRefresh"})
    @DisplayName("should answer 400 for a missing or unknown tab id without checking tab privileges")
    void shouldAnswerBadRequest_whenTabIdIsMissingOrUnknown(String tab) throws Exception {
        assertThat(refresh(tab)).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verify(securityInfoManager, never()).hasPrivilege(any(LoggedInInfo.class), eq("_msg"), anyString(), isNull());
    }

    @Test
    @DisplayName("should refuse before reading the tab id when the schedule privilege is missing")
    void shouldRefuseFirst_whenSchedulePrivilegeIsMissing() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "r", null)).thenReturn(false);

        assertThatThrownBy(() -> refresh("bogus"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_appointment)");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("should map exactly the five tab ids the schedule pages render or refresh")
    void shouldMapExactlyTheScheduleTabs_forTabContract() {
        assertThat(ViewTabAlertsRefresh2Action.TAB_SECURITY_OBJECTS).containsOnlyKeys(
                "oscar_new_lab", "oscar_new_tickler", "oscar_new_msg", "oscar_aged_consults", "oscar_scratch");
    }
}
