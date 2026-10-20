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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.managers.MeasurementManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A measurement group's decision support is added or removed only by the Add and Remove pages' POST forms, with the
 * measurement admin write right, for the group the measurement group pages put in the session, and an add takes only
 * a decision support file the Add page lists.
 *
 * @since 2026-10-20
 */
@Tag("unit")
@Tag("measurement")
@DisplayName("Measurement group decision support add and remove")
class EctMeasurementGroupDecisionSupport2ActionUnitTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final MeasurementManager measurements = mock(MeasurementManager.class);
    private final LoggedInInfo login = mock(LoggedInInfo.class);
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;

    @BeforeEach
    void setUp() {
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(login);
        request.getSession().setAttribute("groupName", "FAKE group");
        when(measurements.findGroupId("FAKE group")).thenReturn("14");
    }

    @AfterEach
    void tearDown() {
        loggedIn.close();
        servlet.close();
    }

    private EctMeasurementGroupDecisionSupport2Action action() {
        return new EctMeasurementGroupDecisionSupport2Action(security, measurements, () -> List.of("FAKE.html", "FAKE2.html"));
    }

    private void grantMeasurementAdmin() {
        when(security.hasPrivilege(login, "_admin.measurements", "w", null)).thenReturn(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should answer 405 and change nothing for anything but POST")
    void shouldChangeNothing_whenNotPosted(String method) throws Exception {
        request.setMethod(method);
        request.setParameter("add", "Add");
        request.setParameter("htmlName", "FAKE.html");

        assertThat(action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(measurements, security);
    }

    @Test
    @DisplayName("should refuse a login without the measurement admin write right")
    void shouldRefuseChange_withoutMeasurementAdminWrite() {
        request.setMethod("POST");
        request.setParameter("add", "Add");
        request.setParameter("htmlName", "FAKE.html");

        assertThatThrownBy(() -> action().execute()).isInstanceOf(SecurityException.class);

        verifyNoInteractions(measurements);
    }

    @Test
    @DisplayName("should add the chosen decision support to the session's group")
    void shouldAddDecisionSupport_whenAddIsPosted() throws Exception {
        grantMeasurementAdmin();
        request.setMethod("POST");
        request.setParameter("add", "Add");
        request.setParameter("htmlName", "FAKE.html");

        assertThat(action().execute()).isEqualTo(ActionSupport.SUCCESS);

        verify(measurements).addMeasurementGroupDS("FAKE group", "FAKE.html");
        assertThat(request.getAttribute("dsState")).isEqualTo("added");
    }

    @Test
    @DisplayName("should remove the session's group decision support")
    void shouldRemoveDecisionSupport_whenRemoveIsPosted() throws Exception {
        when(security.hasPrivilege(login, "_admin", "w", null)).thenReturn(true);
        request.setMethod("POST");
        request.setParameter("remove", "Remove");

        assertThat(action().execute()).isEqualTo(ActionSupport.SUCCESS);

        verify(measurements).removeMeasurementGroupDS("mgroup.ds.html.14");
        assertThat(request.getAttribute("dsState")).isEqualTo("removed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", " ", "FAKE-not-listed.html", "../FAKE.html"})
    @DisplayName("should answer 400 and add nothing unless a listed decision support was chosen")
    void shouldAddNothing_unlessListedFileWasChosen(String htmlName) throws Exception {
        grantMeasurementAdmin();
        request.setMethod("POST");
        request.setParameter("add", "Add");
        request.setParameter("htmlName", htmlName);

        assertThat(action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        verify(measurements, never()).addMeasurementGroupDS(any(), any());
    }

    @Test
    @DisplayName("should answer 400 and change nothing when no group is open")
    void shouldChangeNothing_whenNoGroupIsOpen() throws Exception {
        grantMeasurementAdmin();
        request.getSession().removeAttribute("groupName");
        request.setMethod("POST");
        request.setParameter("remove", "Remove");

        assertThat(action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(measurements);
    }
}
