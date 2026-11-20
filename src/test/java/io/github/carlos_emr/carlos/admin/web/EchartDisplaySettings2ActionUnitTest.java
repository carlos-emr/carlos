// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.admin.web;

import io.github.carlos_emr.carlos.commn.dao.OceanSettingDao;
import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class EchartDisplaySettings2ActionUnitTest {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private OceanSettingDao ocean;
    private SystemPreferencesDao preferences;
    private LoggedInInfo loggedInInfo;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<SpringUtils> spring;
    private MockedStatic<LoggedInInfo> login;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setMethod("GET");
        response = new MockHttpServletResponse();
        security = mock(SecurityInfoManager.class);
        ocean = mock(OceanSettingDao.class);
        preferences = mock(SystemPreferencesDao.class);
        loggedInInfo = mock(LoggedInInfo.class);
        servlet = mockStatic(ServletActionContext.class);
        spring = mockStatic(SpringUtils.class);
        login = mockStatic(LoggedInInfo.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
        spring.when(() -> SpringUtils.getBean(OceanSettingDao.class)).thenReturn(ocean);
        spring.when(() -> SpringUtils.getBean(SystemPreferencesDao.class)).thenReturn(preferences);
        login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        login.close();
        spring.close();
        servlet.close();
    }

    @ParameterizedTest
    @CsvSource({"GET,r", "HEAD,r", "POST,w"})
    void shouldCheckRequiredPrivilege_beforeRenderingSettings(String method, String privilege) {
        request.setMethod(method);
        var action = new EchartDisplaySettings2Action();
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verifyNoInteractions(ocean, preferences);
        when(security.hasPrivilege(loggedInInfo, "_admin", privilege, null)).thenReturn(true);
        assertThat(action.execute()).isEqualTo("success");
        verify(security, times(2)).hasPrivilege(loggedInInfo, "_admin", privilege, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE", "PATCH", "OPTIONS"})
    void shouldRejectUnsupportedMethods_withoutAccessingSettings(String method) {
        request.setMethod(method);
        assertThat(new EchartDisplaySettings2Action().execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("GET, HEAD, POST");
        verifyNoInteractions(security, ocean, preferences);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldPersistCheckbox_whenAuthorizedPostSaves(boolean enabled) {
        request.setMethod("POST");
        request.setParameter("dboperation", "Save");
        if (enabled) request.setParameter("echart_show_ocean", "true");
        when(security.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("1001");
        assertThat(new EchartDisplaySettings2Action().execute()).isEqualTo("success");
        verify(ocean).saveDisplayPreference(enabled, "1001");
        assertThat(request.getAttribute("displayOceanUI")).isEqualTo(enabled);
        assertThat(request.getAttribute("saved")).isEqualTo(true);
    }

    @Test
    void shouldDefaultVisible_whenViewingWithoutSaveIntent() {
        when(security.hasPrivilege(loggedInInfo, "_admin", "r", null)).thenReturn(true);
        new EchartDisplaySettings2Action().execute();
        assertThat(request.getAttribute("displayOceanUI")).isEqualTo(true);
        assertThat(request.getAttribute("saved")).isEqualTo(false);
        verifyNoInteractions(ocean);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRejectSaveIntent_onReadMethodsWithoutDaoAccess(String method) {
        request.setMethod(method);
        request.setParameter("dboperation", "Save");
        assertThat(new EchartDisplaySettings2Action().execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(ocean, preferences, security);
    }

    @Test
    void shouldDisplayPersistedPreference_whenDisabled() {
        when(security.hasPrivilege(loggedInInfo, "_admin", "r", null)).thenReturn(true);
        when(preferences.findPreferenceByName(SystemPreferences.ECHART_PREFERENCE_KEYS.echart_show_ocean))
                .thenReturn(new SystemPreferences("echart_show_ocean", "false"));
        new EchartDisplaySettings2Action().execute();
        assertThat(request.getAttribute("displayOceanUI")).isEqualTo(false);
    }
}
