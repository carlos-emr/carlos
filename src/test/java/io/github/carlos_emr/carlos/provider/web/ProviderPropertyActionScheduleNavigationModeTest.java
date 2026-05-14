/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.provider.web;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("ProviderPropertyAction schedule navigation mode")
class ProviderPropertyActionScheduleNavigationModeTest {

    private static final String PROVIDER_NO = "999998";

    @Test
    @DisplayName("focused mode does not enable legacy encounter tabs")
    void focusedModeDoesNotEnableLegacyEncounterTabs() throws Exception {
        UserPropertyDAO dao = mock(UserPropertyDAO.class);
        MockHttpServletRequest request = requestWithMode(UserProperty.SCHEDULE_NAVIGATION_MODE_FOCUSED);

        invokeSaveScheduleNavigationMode(request, dao);

        verify(dao).saveProp(PROVIDER_NO, UserProperty.SCHEDULE_NAVIGATION_MODE,
                UserProperty.SCHEDULE_NAVIGATION_MODE_FOCUSED);
        verify(dao).saveProp(PROVIDER_NO, UserProperty.ENCOUNTER_OPEN_IN_TAB, "no");
    }

    @Test
    @DisplayName("tab mode keeps legacy encounter tab behavior enabled")
    void tabModeKeepsLegacyEncounterTabBehaviorEnabled() throws Exception {
        UserPropertyDAO dao = mock(UserPropertyDAO.class);
        MockHttpServletRequest request = requestWithMode(UserProperty.SCHEDULE_NAVIGATION_MODE_TAB);

        invokeSaveScheduleNavigationMode(request, dao);

        verify(dao).saveProp(PROVIDER_NO, UserProperty.SCHEDULE_NAVIGATION_MODE,
                UserProperty.SCHEDULE_NAVIGATION_MODE_TAB);
        verify(dao).saveProp(PROVIDER_NO, UserProperty.ENCOUNTER_OPEN_IN_TAB, "yes");
    }

    @Test
    @DisplayName("invalid mode falls back to popups")
    void invalidModeFallsBackToPopups() throws Exception {
        UserPropertyDAO dao = mock(UserPropertyDAO.class);
        MockHttpServletRequest request = requestWithMode("unexpected");

        invokeSaveScheduleNavigationMode(request, dao);

        verify(dao).saveProp(PROVIDER_NO, UserProperty.SCHEDULE_NAVIGATION_MODE,
                UserProperty.SCHEDULE_NAVIGATION_MODE_POPUP);
        verify(dao).saveProp(PROVIDER_NO, UserProperty.ENCOUNTER_OPEN_IN_TAB, "no");
    }

    @Test
    @DisplayName("missing mode parameter leaves existing navigation settings untouched")
    void missingModeParameterLeavesExistingNavigationSettingsUntouched() throws Exception {
        UserPropertyDAO dao = mock(UserPropertyDAO.class);
        MockHttpServletRequest request = new MockHttpServletRequest();

        invokeSaveScheduleNavigationMode(request, dao);

        // Partial preference posts and older forms must not silently rewrite navigation mode.
        verify(dao, never()).saveProp(PROVIDER_NO, UserProperty.SCHEDULE_NAVIGATION_MODE,
                UserProperty.SCHEDULE_NAVIGATION_MODE_POPUP);
        verify(dao, never()).saveProp(PROVIDER_NO, UserProperty.ENCOUNTER_OPEN_IN_TAB, "no");
    }

    private static MockHttpServletRequest requestWithMode(String mode) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(UserProperty.SCHEDULE_NAVIGATION_MODE, mode);
        return request;
    }

    private static void invokeSaveScheduleNavigationMode(MockHttpServletRequest request,
                                                         UserPropertyDAO dao) throws Exception {
        Method method = ProviderPropertyAction.class.getDeclaredMethod(
                "saveScheduleNavigationMode",
                jakarta.servlet.http.HttpServletRequest.class,
                UserPropertyDAO.class,
                String.class);
        method.setAccessible(true);
        method.invoke(null, request, dao, PROVIDER_NO);
    }
}
