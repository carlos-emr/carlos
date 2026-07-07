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
package io.github.carlos_emr.carlos.provider.web;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("ProviderPropertyAction schedule navigation mode")
class ProviderPropertyActionScheduleNavigationModeTest extends CarlosUnitTestBase {

    private static final String PROVIDER_NO = "999998";

    @Test
    @DisplayName("focused mode does not enable legacy encounter tabs")
    void shouldPersistScheduleNavigationMode_whenFocusedSelected() throws Exception {
        UserPropertyDAO dao = mock(UserPropertyDAO.class);
        MockHttpServletRequest request = requestWithMode(UserProperty.SCHEDULE_NAVIGATION_MODE_FOCUSED);

        invokeSaveScheduleNavigationMode(request, dao);

        verify(dao).saveProp(PROVIDER_NO, UserProperty.SCHEDULE_NAVIGATION_MODE,
                UserProperty.SCHEDULE_NAVIGATION_MODE_FOCUSED);
        verify(dao).saveProp(PROVIDER_NO, UserProperty.ENCOUNTER_OPEN_IN_TAB, "no");
    }

    @Test
    @DisplayName("tab mode keeps legacy encounter tab behavior enabled")
    void shouldPersistScheduleNavigationMode_whenTabSelected() throws Exception {
        UserPropertyDAO dao = mock(UserPropertyDAO.class);
        MockHttpServletRequest request = requestWithMode(UserProperty.SCHEDULE_NAVIGATION_MODE_TAB);

        invokeSaveScheduleNavigationMode(request, dao);

        verify(dao).saveProp(PROVIDER_NO, UserProperty.SCHEDULE_NAVIGATION_MODE,
                UserProperty.SCHEDULE_NAVIGATION_MODE_TAB);
        verify(dao).saveProp(PROVIDER_NO, UserProperty.ENCOUNTER_OPEN_IN_TAB, "yes");
    }

    @Test
    @DisplayName("invalid mode falls back to popups")
    void shouldFallbackToPopup_whenModeInvalid() throws Exception {
        UserPropertyDAO dao = mock(UserPropertyDAO.class);
        MockHttpServletRequest request = requestWithMode("unexpected");

        invokeSaveScheduleNavigationMode(request, dao);

        verify(dao).saveProp(PROVIDER_NO, UserProperty.SCHEDULE_NAVIGATION_MODE,
                UserProperty.SCHEDULE_NAVIGATION_MODE_POPUP);
        verify(dao).saveProp(PROVIDER_NO, UserProperty.ENCOUNTER_OPEN_IN_TAB, "no");
    }

    @Test
    @DisplayName("missing mode parameter leaves existing navigation settings untouched")
    void shouldSkipPersistence_whenModeMissing() throws Exception {
        UserPropertyDAO dao = mock(UserPropertyDAO.class);
        MockHttpServletRequest request = new MockHttpServletRequest();

        invokeSaveScheduleNavigationMode(request, dao);

        // Partial preference posts and older forms must not write these keys with any value.
        verify(dao, never()).saveProp(eq(PROVIDER_NO), eq(UserProperty.SCHEDULE_NAVIGATION_MODE), anyString());
        verify(dao, never()).saveProp(eq(PROVIDER_NO), eq(UserProperty.ENCOUNTER_OPEN_IN_TAB), anyString());
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
