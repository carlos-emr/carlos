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
package io.github.carlos_emr.carlos.lab.ca.all.upload;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicMergedDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicMerged;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.dao.utils.AuthUtils;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Incoming labs are matched on HIN, date of birth and sex (issue #3880). A merged source record and its head
 * share the patient's HIN, so both rows match; that must still route to the head, while genuinely ambiguous
 * matches stay unrouted and a corrupt merge cycle can no longer overflow the stack.
 *
 * <p>Drives the real {@link MessageUploader#routeReport} against H2 with synthetic patients and a minimal ORU.</p>
 *
 * @since 2026-10-19
 */
@Tag("integration")
@Tag("lab")
@Tag("demographic")
@Isolated("MessageUploader caches Spring beans in static fields")
class MessageUploaderMergedPatientMatchIntegrationTest extends CarlosTestBase {
    private static final String HIN = "9876543210";
    private static final String OTHER_HIN = "9876543211";
    private static final int FIRST_LAB = 9880001;

    @Autowired
    private DemographicDao demographicDao;
    @Autowired
    private DemographicMergedDao demographicMergedDao;
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager em;
    private final Map<Field, Object> originalBeans = new LinkedHashMap<>();

    @BeforeEach
    void useSpringBeans() throws Exception {
        // Provider routing takes a MySQL-only advisory-lock row that the H2 schema does not create.
        em.createNativeQuery("CREATE TABLE IF NOT EXISTS providerLabRoutingLock (lab_no INT PRIMARY KEY)").executeUpdate();
        // A unit test may have initialized this legacy class first with mocked Spring beans.
        for (Field field : MessageUploader.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())
                    && (field.getName().endsWith("Dao") || field.getName().endsWith("Manager"))) {
                field.setAccessible(true);
                originalBeans.put(field, field.get(null));
                field.set(null, applicationContext.getBean(field.getType()));
            }
        }
    }

    /**
     * The test context supplies a mock {@link DemographicManager}; make it load real rows so the head record
     * resolved from a merged source can be read back, as in production.
     */
    @BeforeEach
    void loadDemographicsThroughTheDao() {
        DemographicManager manager = applicationContext.getBean(DemographicManager.class);
        Mockito.reset(manager);
        Mockito.when(manager.getDemographic(ArgumentMatchers.any(LoggedInInfo.class), ArgumentMatchers.any(Integer.class)))
                .thenAnswer(call -> demographicDao.getDemographicById(call.getArgument(1)));
    }

    @AfterEach
    void restoreBeans() throws Exception {
        Mockito.reset(applicationContext.getBean(DemographicManager.class));
        for (var entry : originalBeans.entrySet()) entry.getKey().set(null, entry.getValue());
    }

    private Demographic patient(String hin) {
        Demographic demographic = new Demographic();
        demographic.setFirstName("Merge");
        demographic.setLastName("FAKE-PATIENT");
        demographic.setHin(hin);
        demographic.setHcType("ON");
        demographic.setPatientStatus("AC");
        demographic.setProviderNo("999998");
        demographic.setYearOfBirth("1980");
        demographic.setMonthOfBirth("01");
        demographic.setDateOfBirth("02");
        demographic.setSex("F");
        demographicDao.save(demographic);
        return demographic;
    }

    private void merge(Demographic source, Demographic head) {
        DemographicMerged merged = new DemographicMerged();
        merged.setDemographicNo(source.getDemographicNo());
        merged.setMergedTo(head.getDemographicNo());
        merged.setLastUpdateUser("999998");
        merged.setLastUpdateDate(new java.util.Date());
        demographicMergedDao.persist(merged);
    }

    /** Demographics are saved through the Hibernate session and merge rows through JPA; the JDBC lookup needs both. */
    private void flushAll() {
        hibernateTemplate.flush();
        em.flush();
    }

    private static String lab(String hin, String accession) {
        return String.join("\r",
                "MSH|^~\\&|Reports|SYNTHETIC|||20261019120000||ORU^R01||1|2.3",
                "PID|1|" + hin + "||^^ON|FAKE-PATIENT^Merge||19800102|F",
                "ORC|NW|" + accession + "|||F|||||||999998^DR. PROBE|||20261019",
                "OBR|1|" + accession + "||ML70^SYNTHETIC PANEL||20261019|20261019|||||||||999998^DR. PROBE|||||||||F",
                "OBX|1|ST|7010^SYNTHETIC RESULT|^^CHEMISTRY|NORMAL|||N|||F||765^1007010||70",
                "");
    }

    /** Routes one synthetic lab for the HIN and returns the demographic numbers it was routed to. */
    private List<Integer> routedDemographics(String hin, int fileId) throws Exception {
        MessageUploader.routeReport(AuthUtils.initLoginContext(), "synthetic", null, lab(hin, "ACC" + fileId), fileId);
        em.flush();
        List<PatientLabRouting> routes = em.createQuery("select r from PatientLabRouting r where r.labType = 'HL7'",
                PatientLabRouting.class).getResultList();
        return routes.stream().map(PatientLabRouting::getDemographicNo).toList();
    }

    @Test
    void shouldRouteToHead_whenMergedSourceAndHeadShareTheHin() throws Exception {
        Demographic source = patient(HIN);
        Demographic head = patient(HIN);
        merge(source, head);
        flushAll();

        assertThat(routedDemographics(HIN, FIRST_LAB)).containsExactly(head.getDemographicNo());
    }

    @Test
    void shouldRouteToHead_whenOnlyTheMergedSourceCarriesTheHin() throws Exception {
        Demographic source = patient(HIN);
        Demographic head = patient(OTHER_HIN);
        merge(source, head);
        flushAll();

        assertThat(routedDemographics(HIN, FIRST_LAB + 1)).containsExactly(head.getDemographicNo());
    }

    @Test
    void shouldStayUnrouted_whenTwoUnrelatedPatientsShareTheHin() throws Exception {
        patient(HIN);
        patient(HIN);
        flushAll();

        assertThat(routedDemographics(HIN, FIRST_LAB + 2)).isEmpty();
    }

    @Test
    void shouldNotOverflowTheStack_whenTheMergeChainIsACycle() throws Exception {
        Demographic first = patient(HIN);
        Demographic second = patient(HIN);
        merge(first, second);
        merge(second, first);
        flushAll();

        assertThat(routedDemographics(HIN, FIRST_LAB + 3)).isEmpty();
    }
}
