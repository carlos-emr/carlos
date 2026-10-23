// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.admin.web;

import io.github.carlos_emr.carlos.chartspace.ChartSpaceLayout;
import io.github.carlos_emr.carlos.chartspace.ChartSpaceLayoutService;
import io.github.carlos_emr.carlos.commn.dao.OceanSettingDao;
import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ServletActionContext;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class EchartDisplaySettings2ActionUnitTest {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private OceanSettingDao ocean;
    private SystemPreferencesDao preferences;
    private ChartSpaceLayoutService layoutService;
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
        layoutService = mock(ChartSpaceLayoutService.class);
        loggedInInfo = mock(LoggedInInfo.class);
        servlet = mockStatic(ServletActionContext.class);
        spring = mockStatic(SpringUtils.class);
        login = mockStatic(LoggedInInfo.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
        spring.when(() -> SpringUtils.getBean(OceanSettingDao.class)).thenReturn(ocean);
        spring.when(() -> SpringUtils.getBean(SystemPreferencesDao.class)).thenReturn(preferences);
        spring.when(() -> SpringUtils.getBean(ChartSpaceLayoutService.class)).thenReturn(layoutService);
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
        verifyNoInteractions(ocean, preferences, layoutService);
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
        verifyNoInteractions(security, ocean, preferences, layoutService);
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
        verifyNoInteractions(ocean, preferences, security, layoutService);
    }

    @Test
    void shouldDisplayPersistedPreference_whenDisabled() {
        when(security.hasPrivilege(loggedInInfo, "_admin", "r", null)).thenReturn(true);
        when(preferences.findPreferenceByName(SystemPreferences.ECHART_PREFERENCE_KEYS.echart_show_ocean))
                .thenReturn(new SystemPreferences("echart_show_ocean", "false"));
        new EchartDisplaySettings2Action().execute();
        assertThat(request.getAttribute("displayOceanUI")).isEqualTo(false);
    }

    private static final String LAYOUT_PARAM =
            SystemPreferences.ECHART_PREFERENCE_KEYS.chartspace_default_layout.name();

    private void authorizeAs(String privilege) {
        when(security.hasPrivilege(loggedInInfo, "_admin", privilege, null)).thenReturn(true);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("1001");
    }

    private void postSave() {
        request.setMethod("POST");
        request.setParameter("dboperation", "Save");
    }

    @Test
    void shouldShowFamilyPractice_whenNoPreferenceStored() {
        authorizeAs("r");
        when(layoutService.loadClinicDefault()).thenReturn(ChartSpaceLayout.DEFAULT);
        new EchartDisplaySettings2Action().execute();
        assertThat(request.getAttribute("chartSpaceLayout")).isEqualTo(ChartSpaceLayout.FAMILY_PRACTICE);
        assertThat(request.getAttribute("chartSpaceLayouts")).isEqualTo(java.util.List.of(ChartSpaceLayout.values()));
        assertThat(request.getAttribute("layoutInvalid")).isEqualTo(false);
    }

    @Test
    void shouldShowStoredLayout_whenValid() {
        authorizeAs("r");
        when(layoutService.loadClinicDefault()).thenReturn(ChartSpaceLayout.EYE_CARE);
        new EchartDisplaySettings2Action().execute();
        assertThat(request.getAttribute("chartSpaceLayout")).isEqualTo(ChartSpaceLayout.EYE_CARE);
    }

    @Test
    void shouldPropagateError_whenLayoutReadFails() {
        authorizeAs("r");
        PersistenceException failure = new PersistenceException("FAKE-db-down");
        when(layoutService.loadClinicDefault()).thenThrow(failure);
        assertThatThrownBy(() -> new EchartDisplaySettings2Action().execute()).isSameAs(failure);
    }

    @Test
    void shouldSaveLayout_onPostWithAdminWrite() {
        postSave();
        request.setParameter(LAYOUT_PARAM, "eyeCare");
        authorizeAs("w");
        assertThat(new EchartDisplaySettings2Action().execute()).isEqualTo("success");
        verify(layoutService).saveClinicDefault(ChartSpaceLayout.EYE_CARE);
        assertThat(request.getAttribute("chartSpaceLayout")).isEqualTo(ChartSpaceLayout.EYE_CARE);
        assertThat(request.getAttribute("saved")).isEqualTo(true);
        assertThat(request.getAttribute("layoutInvalid")).isEqualTo(false);
    }

    @Test
    void shouldSaveOceanBeforeLayout_whenBothValid() {
        postSave();
        request.setParameter(LAYOUT_PARAM, "eyeCare");
        request.setParameter("echart_show_ocean", "true");
        authorizeAs("w");
        new EchartDisplaySettings2Action().execute();
        InOrder order = inOrder(ocean, layoutService);
        order.verify(ocean).saveDisplayPreference(true, "1001");
        order.verify(layoutService).saveClinicDefault(ChartSpaceLayout.EYE_CARE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"foo", "EyeCare", " eyeCare", ""})
    void shouldSaveNothing_whenLayoutInvalid(String submitted) {
        postSave();
        request.setParameter(LAYOUT_PARAM, submitted);
        request.setParameter("echart_show_ocean", "true");
        authorizeAs("w");
        when(layoutService.loadClinicDefault()).thenReturn(ChartSpaceLayout.EYE_CARE);
        when(preferences.findPreferenceByName(SystemPreferences.ECHART_PREFERENCE_KEYS.echart_show_ocean))
                .thenReturn(new SystemPreferences("echart_show_ocean", "false"));
        assertThat(new EchartDisplaySettings2Action().execute()).isEqualTo("success");
        verify(layoutService, never()).saveClinicDefault(any());
        verifyNoInteractions(ocean);
        assertThat(request.getAttribute("saved")).isEqualTo(false);
        assertThat(request.getAttribute("layoutInvalid")).isEqualTo(true);
        assertThat(request.getAttribute("chartSpaceLayout")).isEqualTo(ChartSpaceLayout.EYE_CARE);
        // The Ocean checkbox shows the STORED value, not the submitted "true".
        assertThat(request.getAttribute("displayOceanUI")).isEqualTo(false);
    }

    @Test
    void shouldNotExposeSubmittedValue_whenLayoutInvalid() {
        String submitted = "FAKE-<script>alert(1)</script>";
        postSave();
        request.setParameter(LAYOUT_PARAM, submitted);
        authorizeAs("w");
        when(layoutService.loadClinicDefault()).thenReturn(ChartSpaceLayout.DEFAULT);
        new EchartDisplaySettings2Action().execute();
        for (String name : Collections.list(request.getAttributeNames())) {
            assertThat(String.valueOf(request.getAttribute(name))).as(name).doesNotContain("FAKE-<script>");
        }
    }

    @Test
    void shouldSaveOceanOnly_whenLayoutAbsent() {
        postSave();
        authorizeAs("w");
        new EchartDisplaySettings2Action().execute();
        verify(ocean).saveDisplayPreference(false, "1001");
        verify(layoutService, never()).saveClinicDefault(any());
        assertThat(request.getAttribute("saved")).isEqualTo(true);
    }

    @Test
    void shouldKeepOceanBehaviour_whenLayoutAlsoSubmitted() {
        postSave();
        request.setParameter(LAYOUT_PARAM, "familyPractice");
        request.setParameter("echart_show_ocean", "true");
        authorizeAs("w");
        assertThat(new EchartDisplaySettings2Action().execute()).isEqualTo("success");
        verify(ocean).saveDisplayPreference(true, "1001");
        assertThat(request.getAttribute("displayOceanUI")).isEqualTo(true);
        assertThat(request.getAttribute("saved")).isEqualTo(true);
    }
}
