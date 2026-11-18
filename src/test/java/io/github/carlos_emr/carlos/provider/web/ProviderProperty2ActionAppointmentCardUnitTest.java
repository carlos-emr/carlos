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

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Set Appointment Card Preferences shows what the card prints (issue #5134): the saved value, else
 * the older APPT_CARD_* value an upgraded install holds, so the next save carries it over.
 *
 * @since 2026-10-21
 */
@Tag("unit")
@Tag("provider")
class ProviderProperty2ActionAppointmentCardUnitTest extends CarlosUnitTestBase {

    private static final String PROVIDER = "999998";

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final UserPropertyDAO userProperties = mock(UserPropertyDAO.class);
    private MockedStatic<ServletActionContext> servlet;
    private ProviderProperty2Action action;

    @BeforeEach
    void setUp() {
        registerMock(UserPropertyDAO.class, userProperties);
        registerMock(ProviderDao.class, mock(ProviderDao.class));
        registerMock(ProviderManager2.class, mock(ProviderManager2.class));
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        request.getSession().setAttribute("user", PROVIDER);
        when(userProperties.getProp(anyString(), anyString())).thenReturn(null);
        action = new ProviderProperty2Action();
    }

    @AfterEach
    void tearDown() {
        servlet.close();
    }

    private UserProperty saved(String key, String value) {
        UserProperty property = new UserProperty();
        property.setProviderNo(PROVIDER);
        property.setName(key);
        property.setValue(value);
        when(userProperties.getProp(PROVIDER, key)).thenReturn(property);
        return property;
    }

    private String shown(String attribute) {
        return ((UserProperty) request.getAttribute(attribute)).getValue();
    }

    @Test
    void shouldShowTheOlderValues_whenOnlyTheyAreSaved() {
        saved("APPT_CARD_NAME", "FAKE Old Clinic");
        saved("APPT_CARD_PHONE", "613-555-0122");
        saved("APPT_CARD_FAX", "613-555-0123");

        action.viewAppointmentCardPrefs();

        assertThat(shown("name")).isEqualTo("FAKE Old Clinic");
        assertThat(shown("phone")).isEqualTo("613-555-0122");
        assertThat(shown("fax")).isEqualTo("613-555-0123");
    }

    @Test
    void shouldShowTheSavedValue_overTheOlderOne() {
        saved("appointmentCardName", "FAKE New Clinic");
        saved("APPT_CARD_NAME", "FAKE Old Clinic");

        action.viewAppointmentCardPrefs();

        assertThat(shown("name")).isEqualTo("FAKE New Clinic");
        assertThat(action.getAppointmentCardName().getName()).isEqualTo("appointmentCardName");
    }

    @Test
    void shouldShowEmptyBoxes_whenNothingIsSaved() {
        action.viewAppointmentCardPrefs();

        assertThat(shown("name")).isEmpty();
        assertThat(shown("phone")).isEmpty();
        assertThat(shown("fax")).isEmpty();
    }

    @Test
    void shouldLeaveTheLoadedRowsUnchanged_whenShowingTheOlderValue() {
        UserProperty blank = saved("appointmentCardName", "");
        saved("APPT_CARD_NAME", "FAKE Old Clinic");

        action.viewAppointmentCardPrefs();

        // The page shows a copy; the loaded row keeps its own value and nothing is written.
        assertThat(shown("name")).isEqualTo("FAKE Old Clinic");
        assertThat(blank.getValue()).isEmpty();
        verify(userProperties, never()).saveProp(anyString(), anyString(), anyString());
    }
}
