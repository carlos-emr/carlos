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

import io.github.carlos_emr.carlos.casemgmt.common.ChartSessionKeys;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Collections;

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
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The day sheet's "remember this appointment" call files the appointment under the patient it is
 * booked for, so a later appointment opened for another patient leaves this patient's current
 * visit alone (issue #4673). It used to set one login-wide {@code cur_appointment_no}, which tied
 * Health Tracker saves and the multisite prescription address in one patient's window to the visit
 * last opened for another patient.
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("security")
@DisplayName("Day-sheet appointment store")
class ViewStoreApptInSession2ActionUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT_A = 101;
    private static final int PATIENT_B = 202;

    private final SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
    private final OscarAppointmentDao appointmentDao = mock(OscarAppointmentDao.class);
    private final LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
    private final MockHttpSession session = new MockHttpSession();
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        session.setAttribute("user", "999998");
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenAnswer(invocation -> request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenAnswer(invocation -> response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "r", null)).thenReturn(true);
        when(appointmentDao.find(11)).thenReturn(appointment(11, PATIENT_A));
        when(appointmentDao.find(22)).thenReturn(appointment(22, PATIENT_B));
        newRequest("POST");
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    @Test
    @DisplayName("should keep each patient's appointment when another patient's is opened next")
    void shouldKeepEachPatientsAppointment_whenAnotherPatientsAppointmentOpenedNext() throws Exception {
        assertThat(store("11")).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
        newRequest("POST");
        store("22");

        assertThat(session.getAttribute(ChartSessionKeys.currentAppointment(Integer.toString(PATIENT_A))))
                .isEqualTo("11");
        assertThat(session.getAttribute(ChartSessionKeys.currentAppointment(Integer.toString(PATIENT_B))))
                .isEqualTo("22");
        assertThat(session.getAttribute("cur_appointment_no")).isNull();
        // What the Health Tracker, tickler and prescription pages read back for each patient.
        assertThat(EncounterPatientContext.currentAppointment(request, Integer.toString(PATIENT_A), appointmentDao))
                .extracting(Appointment::getId).isEqualTo(11);
        assertThat(EncounterPatientContext.currentAppointment(request, Integer.toString(PATIENT_B), appointmentDao))
                .extracting(Appointment::getId).isEqualTo(22);
    }

    @Test
    @DisplayName("should read back no appointment for a patient whose stored appointment was moved to another patient")
    void shouldReadBackNoAppointment_whenStoredAppointmentMovedToAnotherPatient() throws Exception {
        store("11");
        when(appointmentDao.find(11)).thenReturn(appointment(11, PATIENT_B));

        assertThat(EncounterPatientContext.currentAppointment(request, Integer.toString(PATIENT_A), appointmentDao))
                .isNull();
    }

    @Test
    @DisplayName("should store nothing for an appointment booked without a patient")
    void shouldStoreNothing_whenAppointmentHasNoPatient() throws Exception {
        when(appointmentDao.find(33)).thenReturn(appointment(33, 0));

        assertThat(store("33")).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
        assertThat(Collections.list(session.getAttributeNames())).containsExactly("user");
    }

    @Test
    @DisplayName("should store nothing for an unknown appointment")
    void shouldStoreNothing_whenAppointmentUnknown() throws Exception {
        assertThat(store("44")).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
        assertThat(Collections.list(session.getAttributeNames())).containsExactly("user");
    }

    @ParameterizedTest(name = "appointment_no={0}")
    @ValueSource(strings = {"", "0", "-1", "abc", "1234567890", "11;22"})
    @DisplayName("should answer 400 for a malformed appointment number")
    void shouldReturnBadRequest_whenAppointmentNumberMalformed(String appointmentNo) throws Exception {
        assertThat(store(appointmentNo)).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(appointmentDao);
    }

    @Test
    @DisplayName("should answer 400 when no appointment number is posted")
    void shouldReturnBadRequest_whenAppointmentNumberMissing() throws Exception {
        assertThat(store(null)).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(appointmentDao);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should reject a non-POST request before touching the session")
    void shouldRejectRequest_whenMethodIsNotPost(String method) throws Exception {
        newRequest(method);

        assertThat(store("11")).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verifyNoInteractions(appointmentDao);
        assertThat(Collections.list(session.getAttributeNames())).containsExactly("user");
    }

    @Test
    @DisplayName("should refuse a caller without appointment read access")
    void shouldThrowSecurityException_whenAppointmentReadMissing() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "r", null)).thenReturn(false);

        assertThatThrownBy(() -> store("11"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_appointment)");
        verifyNoInteractions(appointmentDao);
    }

    private void newRequest(String method) {
        request = new MockHttpServletRequest(method, "/carlos/provider/ViewStoreApptInSession");
        request.setSession(session);
        response = new MockHttpServletResponse();
    }

    private String store(String appointmentNo) throws Exception {
        if (appointmentNo != null) {
            request.setParameter("appointment_no", appointmentNo);
        }
        return new ViewStoreApptInSession2Action(securityInfoManager, appointmentDao).execute();
    }

    private static Appointment appointment(int id, int demographicNo) {
        Appointment appointment = new Appointment();
        appointment.setId(id);
        appointment.setDemographicNo(demographicNo);
        return appointment;
    }
}
