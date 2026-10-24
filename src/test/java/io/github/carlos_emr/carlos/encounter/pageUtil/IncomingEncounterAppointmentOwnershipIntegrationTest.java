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
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EChart;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The chart entry against real patient, appointment, chart and note rows (issue #4676).
 *
 * <p>Patients A and B are both readable and each has a chart revision and a visit. A request that
 * names A with B's visit, with a visit that does not exist, or with a visit booked without a
 * patient, through the removed {@code appointmentList} switch or the ordinary entry, is refused
 * before either chart is loaded or remembered: both patients' remembered entries, and their
 * appointment, chart and note rows, read the same afterwards. A's own visit still opens A's
 * chart, also when the request carries the old {@code appointmentList} flag.</p>
 *
 * <p>Only authorization and the patient-record manager (a mock in the test context, here reading
 * the real demographic row) are stubbed; the appointment lookup, chart loading, split-chart and
 * note-count queries run against H2. Rows are committed for real and each test removes exactly the
 * rows it created.</p>
 *
 * @since 2026-10-22
 */
@Tag("integration")
@Tag("encounter")
@DisplayName("Chart entry: the remembered appointment is the request's own patient's")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IncomingEncounterAppointmentOwnershipIntegrationTest extends CarlosTestBase {
    private static final String PROVIDER = "999998";

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private DemographicDao demographicDao;
    @Autowired
    @Qualifier("CaseManagementNoteDAO")
    private CaseManagementNoteDAO notes;

    private TransactionTemplate tx;
    private final List<Integer> patients = new ArrayList<>();
    private final List<Integer> visits = new ArrayList<>();
    private int a;
    private int b;
    private int aVisit;
    private int bVisit;
    private int unbookedVisit;
    private LoggedInInfo login;
    private MockHttpSession session;
    private SecurityInfoManager security;
    private DemographicManager demographics;
    private String showEchart;

    @BeforeEach
    void seedTwoReadablePatientsWithChartsAndVisits() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            a = patient("Entry A");
            b = patient("Entry B");
            chart(a, "FAKE chart text A");
            chart(b, "FAKE chart text B");
            aVisit = appointment(a);
            bVisit = appointment(b);
            unbookedVisit = appointment(0);
        });
        login = new LoggedInInfo();
        Provider provider = new Provider();
        provider.setProviderNo(PROVIDER);
        login.setLoggedInProvider(provider);
        session = new MockHttpSession();
        session.setAttribute("user", PROVIDER);
        session.setAttribute("userfirstname", "Synthetic");
        session.setAttribute("userlastname", "Provider");
        LoggedInInfo.setLoggedInInfoIntoSession(session, login);
        security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(login), eq("_demographic"), eq("r"), nullable(String.class))).thenReturn(true);
        demographics = mock(DemographicManager.class);
        when(demographics.getDemographic(eq(login), anyString()))
                .thenAnswer(call -> demographicDao.getDemographic(call.<String>getArgument(1)));
        when(demographics.getDemographic(eq(login), nullable(Integer.class)))
                .thenAnswer(call -> demographicDao.getDemographic(String.valueOf(call.<Integer>getArgument(1))));
        // Past the CAISI program-domain check: these patients are not program clients.
        showEchart = CarlosProperties.getInstance().getProperty("program_domain.show_echart");
        CarlosProperties.getInstance().setProperty("program_domain.show_echart", "true");
    }

    @AfterEach
    void removeOwnedRows() {
        if (showEchart == null) {
            CarlosProperties.getInstance().remove("program_domain.show_echart");
        } else {
            CarlosProperties.getInstance().setProperty("program_domain.show_echart", showEchart);
        }
        tx.executeWithoutResult(status -> {
            if (!visits.isEmpty()) {
                entityManager.createQuery("delete from Appointment x where x.id in :ids")
                        .setParameter("ids", visits).executeUpdate();
            }
            if (!patients.isEmpty()) {
                entityManager.createQuery("delete from EChart x where x.demographicNo in :ids")
                        .setParameter("ids", patients).executeUpdate();
                entityManager.createNativeQuery("DELETE FROM demographic WHERE demographic_no IN (:ids)")
                        .setParameter("ids", patients).executeUpdate();
            }
        });
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"B's visit", "B's visit through appointmentList", "a visit that does not exist",
            "a visit that does not exist through appointmentList", "a visit booked without a patient"})
    @DisplayName("should refuse A with a visit that is not A's, leaving both entries and all rows unchanged")
    void shouldRefuseLeavingEntriesAndRowsUnchanged_whenTheVisitIsNotThePatientsOwn(String scenario) throws Exception {
        assertThat(enter(a, Integer.toString(aVisit), "FAKE reason A", false, new MockHttpServletResponse()))
                .isEqualTo("success2");
        assertThat(enter(b, Integer.toString(bVisit), "FAKE reason B", false, new MockHttpServletResponse()))
                .isEqualTo("success2");
        List<List<Object>> rowsBefore = rows();
        Object sessionBean = session.getAttribute("EctSessionBean");

        int visit = scenario.startsWith("B's") ? bVisit
                : scenario.contains("without a patient") ? unbookedVisit : nonexistentVisit();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest hostile = request(a, Integer.toString(visit), "FAKE hostile reason",
                scenario.endsWith("appointmentList"));
        assertThat(execute(hostile, response)).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(hostile.getAttribute("EctSessionBean")).isNull();
        assertThat(hostile.getAttribute("encounterText")).as("no chart was loaded").isNull();
        assertThat(session.getAttribute("EctSessionBean")).isSameAs(sessionBean);
        assertEntry(EncounterPatientContext.findEncounter(hostile, Integer.toString(a)), a, aVisit, "FAKE reason A");
        assertEntry(EncounterPatientContext.findEncounter(hostile, Integer.toString(b)), b, bVisit, "FAKE reason B");
        assertThat(rows()).isEqualTo(rowsBefore);
    }

    @ParameterizedTest(name = "appointmentList={0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("should open A's chart for A's own visit")
    void shouldOpenThePatientsChart_whenTheVisitIsThePatientsOwn(boolean legacyFlag) throws Exception {
        MockHttpServletRequest request = request(a, Integer.toString(aVisit), "FAKE reason A", legacyFlag);
        assertThat(execute(request, new MockHttpServletResponse())).isEqualTo("success2");

        EctSessionBean bean = (EctSessionBean) request.getAttribute("EctSessionBean");
        assertEntry(bean, a, aVisit, "FAKE reason A");
        assertThat(bean.patientFirstName).isEqualTo("Entry A");
        assertThat(request.getAttribute("encounterText")).asString()
                .contains("FAKE chart text A").doesNotContain("FAKE chart text B");
        assertEntry(EncounterPatientContext.findEncounter(request, Integer.toString(a)), a, aVisit, "FAKE reason A");
        assertThat(EncounterPatientContext.findEncounter(request, Integer.toString(b))).isNull();
    }

    @Test
    @DisplayName("should open A's chart without a visit when the request names none")
    void shouldOpenWithoutAVisit_whenTheRequestNamesNone() throws Exception {
        MockHttpServletRequest request = request(a, "0", "FAKE reason A", false);
        assertThat(execute(request, new MockHttpServletResponse())).isEqualTo("success2");
        assertThat(EncounterPatientContext.findEncounter(request, Integer.toString(a)).appointmentNo).isEqualTo("0");
    }

    private String enter(int patient, String visit, String reason, boolean legacyFlag,
                         MockHttpServletResponse response) throws Exception {
        return execute(request(patient, visit, reason, legacyFlag), response);
    }

    private MockHttpServletRequest request(int patient, String visit, String reason, boolean legacyFlag) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/encounter/IncomingEncounter");
        request.setSession(session);
        request.setParameter("demographicNo", Integer.toString(patient));
        request.setParameter("appointmentNo", visit);
        request.setParameter("providerNo", PROVIDER);
        request.setParameter("curProviderNo", PROVIDER);
        request.setParameter("reason", reason);
        request.setParameter("encType", "face to face encounter with client");
        request.setParameter("appointmentDate", "2026-10-22");
        if (legacyFlag) request.setParameter("appointmentList", "true");
        return request;
    }

    private String execute(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        try (var spring = mockStatic(SpringUtils.class, CALLS_REAL_METHODS);
             var servlet = mockStatic(ServletActionContext.class)) {
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            spring.when(() -> SpringUtils.getBean(DemographicManager.class)).thenReturn(demographics);
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            return new EctIncomingEncounter2Action().execute();
        }
    }

    /** Both patients' visit, chart and note rows, as the database holds them now. */
    private List<List<Object>> rows() {
        return tx.execute(status -> {
            entityManager.clear();
            List<List<Object>> rows = new ArrayList<>();
            for (Object[] row : entityManager.createQuery(
                            "select x.id, x.demographicNo, x.status, x.reason, x.lastUpdateUser, x.updateDateTime"
                                    + " from Appointment x where x.id in :ids order by x.id", Object[].class)
                    .setParameter("ids", visits).getResultList()) {
                rows.add(Arrays.asList(row));
            }
            for (Object[] row : entityManager.createQuery(
                            "select x.id, x.demographicNo, x.encounter, x.timestamp from EChart x"
                                    + " where x.demographicNo in :ids order by x.id", Object[].class)
                    .setParameter("ids", patients).getResultList()) {
                rows.add(Arrays.asList(row));
            }
            for (int patient : patients) {
                rows.add(List.<Object>of(patient, notes.getNotesCountByDemographicId(Integer.toString(patient))));
            }
            return rows;
        });
    }

    private int nonexistentVisit() {
        return tx.execute(status -> entityManager.createQuery("select max(x.id) from Appointment x", Integer.class)
                .getSingleResult()) + 1000;
    }

    private static void assertEntry(EctSessionBean entry, int patient, int visit, String reason) {
        assertThat(entry).isNotNull();
        assertThat(entry.demographicNo).isEqualTo(Integer.toString(patient));
        assertThat(entry.appointmentNo).isEqualTo(Integer.toString(visit));
        assertThat(entry.reason).isEqualTo(reason);
        assertThat(entry.encType).isEqualTo("face to face encounter with client");
    }

    private int patient(String name) {
        Demographic patient = new Demographic();
        patient.setFirstName(name);
        patient.setLastName("FAKE-Entry4676");
        patient.setSex("U");
        patient.setProviderNo(PROVIDER);
        entityManager.persist(patient);
        entityManager.flush();
        patients.add(patient.getDemographicNo());
        return patient.getDemographicNo();
    }

    private void chart(int patient, String encounter) {
        EChart chart = new EChart();
        chart.setDemographicNo(patient);
        chart.setTimestamp(new Date());
        chart.setProviderNo(PROVIDER);
        chart.setSubject("");
        chart.setSocialHistory("");
        chart.setFamilyHistory("");
        chart.setMedicalHistory("");
        chart.setOngoingConcerns("");
        chart.setReminders("");
        chart.setEncounter(encounter);
        entityManager.persist(chart);
        entityManager.flush();
    }

    private int appointment(int patient) {
        Appointment visit = new Appointment();
        visit.setDemographicNo(patient);
        visit.setProviderNo(PROVIDER);
        visit.setAppointmentDate(new Date());
        visit.setStartTime(new Date());
        visit.setEndTime(new Date());
        visit.setName("FAKE-Entry4676");
        visit.setReason("FAKE booked reason");
        visit.setStatus("t");
        visit.setLastUpdateUser("previous");
        entityManager.persist(visit);
        entityManager.flush();
        visits.add(visit.getId());
        return visit.getId();
    }
}
