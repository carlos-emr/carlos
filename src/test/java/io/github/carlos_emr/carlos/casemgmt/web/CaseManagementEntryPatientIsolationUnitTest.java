/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean;
import io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Exercises real billing URL binding when the requested patient has no cached encounter. */
class CaseManagementEntryPatientIsolationUnitTest extends CarlosUnitTestBase {
    private DemographicManager demographics;
    private OscarAppointmentDao appointments;
    private LoggedInInfo login;
    private MockHttpServletRequest request;
    private EctSessionBean otherChart;

    @BeforeEach
    void prepareUncachedPatient() {
        demographics = createAndRegisterMock(DemographicManager.class);
        appointments = createAndRegisterMock(OscarAppointmentDao.class);
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        request = new MockHttpServletRequest("POST", "/carlos/CaseManagementEntry");
        request.setContextPath("/carlos");
        request.setSession(new MockHttpSession());
        request.setParameter("demographicNo", "303");
        request.setAttribute("from", "casemgmt");
        request.getSession().setAttribute("casemgmt_oscar_baseurl", "https://clinic.example");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        otherChart = new EctSessionBean();
        otherChart.demographicNo = "202";
        otherChart.patientFirstName = "Bob";
        otherChart.patientLastName = "Brown";
        otherChart.appointmentNo = "9002";
        otherChart.encType = "office";
        EncounterPatientContext.rememberEncounter(request, otherChart);
        request.getSession().setAttribute("EctSessionBean", otherChart);
    }

    @Test
    void shouldUseEmptyBillingNames_whenTheRequestedDemographicDoesNotExist() {
        when(demographics.getDemographic(login, "303")).thenReturn(null);

        assertThat(billing()).containsEntry("demographic_no", "303")
                .containsEntry("demographic_name", ",").containsEntry("appointment_no", "0")
                .containsEntry("user_no", "999998");
        assertOtherChartUnchanged();
    }

    @Test
    void shouldRetainRequestedDemographicNames_whenTheRecordExists() {
        Demographic patient = new Demographic();
        patient.setFirstName("Carol");
        patient.setLastName("Clark");
        when(demographics.getDemographic(login, "303")).thenReturn(patient);

        assertThat(billing()).containsEntry("demographic_no", "303")
                .containsEntry("demographic_name", "Clark,Carol").containsEntry("appointment_no", "0")
                .containsEntry("user_no", "999998");
        assertOtherChartUnchanged();
    }

    private Map<String, String> billing() {
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        when(properties.getProperty("billregion", "")).thenReturn("ON");
        CaseManagementEntry2Action action = mock(CaseManagementEntry2Action.class, CALLS_REAL_METHODS);
        action.request = request;
        try (var configuration = mockStatic(CarlosProperties.class)) {
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            ReflectionTestUtils.invokeMethod(action, "prepareBillingUrl", "303");
        }
        String url = (String) request.getAttribute("billing_url");
        assertThat(url).isNotNull();
        Map<String, String> parameters = new HashMap<>();
        for (String parameter : URI.create(url).getRawQuery().split("&")) {
            String[] parts = parameter.split("=", 2);
            parameters.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        verify(demographics).getDemographic(login, "303");
        verifyNoMoreInteractions(demographics);
        verifyNoInteractions(appointments);
        return parameters;
    }

    private void assertOtherChartUnchanged() {
        assertThat(request.getSession().getAttribute("billing_url")).isNull();
        assertThat(request.getSession().getAttribute("EctSessionBean")).isSameAs(otherChart);
        EctSessionBean saved = EncounterPatientContext.findEncounter(request, "202");
        assertThat(saved.patientFirstName).isEqualTo("Bob");
        assertThat(saved.patientLastName).isEqualTo("Brown");
        assertThat(saved.appointmentNo).isEqualTo("9002");
        assertThat(saved.encType).isEqualTo("office");
        assertThat(EncounterPatientContext.findEncounter(request, "303")).isNull();
    }
}
