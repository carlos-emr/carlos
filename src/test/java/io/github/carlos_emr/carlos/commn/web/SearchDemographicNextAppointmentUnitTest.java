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
package io.github.carlos_emr.carlos.commn.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Tests the appointment utility through the real patient-search JSON action. */
// @Isolated because the workflow_enhance switch this contract turns on lives in the process-wide
// CarlosProperties singleton. If JUnit in-process parallelism is enabled, a concurrent test
// must not read or restore this class's temporary value. Surefire forks are separate JVMs.
@Isolated
@DisplayName("Patient search next appointment contract")
class SearchDemographicNextAppointmentUnitTest extends CarlosUnitTestBase {
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final OscarAppointmentDao appointments = mock(OscarAppointmentDao.class);
    private final DemographicDao demographics = mock(DemographicDao.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private String previousWorkflowEnhance;

    @BeforeEach
    void setUpAction() {
        previousWorkflowEnhance = CarlosProperties.getInstance().getProperty("workflow_enhance");
        CarlosProperties.getInstance().setProperty("workflow_enhance", "true");
        registerMock(OscarAppointmentDao.class, appointments);
        registerMock(DemographicDao.class, demographics);
        registerMock(SecurityInfoManager.class, security);
        registerMock(DemographicCustDao.class, mock(DemographicCustDao.class));
        registerMock(ProviderDao.class, mock(ProviderDao.class));
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        request.getSession().setAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", loggedInInfo);
        when(security.hasPrivilege(loggedInInfo, "_demographic", "r", null)).thenReturn(true);
        request.setParameter("term", "Synthetic");
        request.setParameter("jqueryJSON", "true");
        ActionContext.of().withServletRequest(request).withServletResponse(response).bind();
    }

    @AfterEach
    void restoreRequestAndProperties() {
        ActionContext.clear();
        if (previousWorkflowEnhance == null) {
            CarlosProperties.getInstance().remove("workflow_enhance");
        } else {
            CarlosProperties.getInstance().setProperty("workflow_enhance", previousWorkflowEnhance);
        }
    }

    private List<Integer> returnPatients(int count) {
        List<Demographic> patients = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        for (int id = 1; id <= count; id++) {
            Demographic patient = new Demographic();
            patient.setDemographicNo(id);
            patient.setFirstName("Patient");
            patient.setLastName("Synthetic");
            patient.setDateOfBirth("1990-01-15");
            patients.add(patient);
            ids.add(id);
        }
        when(demographics.searchDemographicByName("Synthetic", 100, 0, "999998", false))
                .thenReturn(patients);
        return ids;
    }

    private List<Map<String, Object>> search() throws Exception {
        new SearchDemographicAutoComplete2Action().execute();
        assertThat(response.getContentType()).startsWith("application/json");
        return new ObjectMapper().readValue(response.getContentAsString(), new TypeReference<>() { });
    }

    @Test
    @DisplayName("should resolve 100 patient rows in one appointment lookup and preserve aliases")
    void shouldBatchAppointments_whenSearchReturnsFullPage() throws Exception {
        List<Integer> ids = returnPatients(100);
        when(appointments.findNextAppointmentDates(anyCollection()))
                .thenReturn(Map.of(1, Date.valueOf("2026-10-01"), 100, Date.valueOf("2026-11-02")));

        List<Map<String, Object>> rows = search();

        assertThat(rows).hasSize(100);
        assertThat(rows.get(0)).containsEntry("nextAppointment", "2026-10-01");
        assertThat(rows.get(99)).containsEntry("nextAppointment", "2026-11-02");
        for (int i = 1; i < 99; i++) {
            assertThat(rows.get(i)).containsEntry("nextAppointment", "(none)");
        }
        rows.forEach(row -> assertThat(row.get("nextAppt")).isEqualTo(row.get("nextAppointment")));
        verify(appointments).findNextAppointmentDates(argThat(requested ->
                requested.size() == 100 && requested.containsAll(ids)));
        verifyNoMoreInteractions(appointments);
    }

    @Test
    @DisplayName("should leave appointment fields disabled without querying when workflow enhancement is off")
    void shouldSkipAppointmentLookup_whenWorkflowEnhancementDisabled() throws Exception {
        returnPatients(1);
        CarlosProperties.getInstance().setProperty("workflow_enhance", "false");

        assertThat(search()).singleElement().satisfies(row ->
                assertThat(row).doesNotContainKey("nextAppointment").containsEntry("nextAppt", ""));
        verifyNoInteractions(appointments);
    }

    @Test
    @DisplayName("should avoid an appointment query when no patients match")
    void shouldSkipAppointmentLookup_whenNoPatientsMatch() throws Exception {
        returnPatients(0);
        assertThat(search()).isEmpty();
        verifyNoInteractions(appointments);
    }

    @Test
    @DisplayName("should propagate database failure rather than report no appointment")
    void shouldPropagateFailure_whenAppointmentDatabaseFails() throws Exception {
        returnPatients(1);
        IllegalStateException outage = new IllegalStateException("synthetic database outage");
        when(appointments.findNextAppointmentDates(anyCollection())).thenThrow(outage);

        assertThatThrownBy(this::search).isSameAs(outage);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("should reject unauthorized search before any appointment lookup")
    void shouldRejectSearch_whenDemographicReadDenied() {
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), isNull()))
                .thenReturn(false);
        assertThatThrownBy(this::search).isInstanceOf(SecurityException.class);
        verifyNoInteractions(appointments, demographics);
    }
}
