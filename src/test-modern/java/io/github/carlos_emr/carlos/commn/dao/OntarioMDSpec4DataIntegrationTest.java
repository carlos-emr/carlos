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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for the Ontario MD Spec 4 data population scenario,
 * validating that admissions can be queried by program and admitted date range
 * after populating providers, demographics, programs, and admissions.
 *
 * <p>Migrated from legacy {@code OntarioMDSpec4DataTest} (JUnit 4 / DaoTestFixtures).
 * The legacy test was a single massive 3791-line test that populated the full
 * Ontario MD Spec 4 validation dataset (15 providers, 20+ patients, case management
 * notes, prescriptions, measurements, allergies, etc.) and then asserted that
 * admissions were retrievable by program and date range.</p>
 *
 * <p>This modern version creates the minimum representative data needed to verify
 * the same core assertion: that {@link AdmissionDao#getAdmissionsByProgramAndAdmittedDate}
 * returns non-empty results after proper data setup with providers, demographics,
 * programs, program providers, and admissions.</p>
 *
 * @since 2026-03-07
 * @see AdmissionDao
 */
@DisplayName("OntarioMDSpec4Data Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class OntarioMDSpec4DataIntegrationTest extends CarlosTestBase {

    @Autowired
    private AdmissionDao admissionDao;

    @Autowired
    private ProviderDao providerDao;

    @Autowired
    private DemographicDao demographicDao;

    @Autowired
    private ProgramDao programDao;

    @Autowired
    @Qualifier("programProviderDAO")
    private ProgramProviderDAO programProviderDAO;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private Integer oscarProgramID;
    private Date referenceDate;

    @BeforeEach
    void setUp() {
        Calendar cal = Calendar.getInstance();
        referenceDate = cal.getTime();

        // Create or find the OSCAR program
        oscarProgramID = programDao.getProgramIdByProgramName("OSCAR");

        if (oscarProgramID == null) {
            Program program = new Program();
            program.setFacilityId(1);
            program.setName("OSCAR");
            program.setEmergencyNumber("");
            program.setMaxAllowed(999999);
            program.setHoldingTank(false);
            program.setType("Bed");
            program.setProgramStatus("active");
            program.setAllowBatchAdmission(false);
            program.setAllowBatchDischarge(false);
            program.setHic(false);
            program.setExclusiveView("");
            program.setDefaultServiceRestrictionDays(0);
            program.setAddress("1 Anystreet");
            program.setPhone("555-555-5555");
            program.setFax("555-555-5556");
            program.setUrl("google.ca");
            program.setEmail("noreply@caisi.ca");
            programDao.saveProgram(program);
            hibernateTemplate.flush();
            oscarProgramID = programDao.getProgramIdByProgramName("OSCAR");
        }
    }

    private Provider createProvider(String firstName, String lastName, String providerNo, String specialty) {
        Provider provider = new Provider();
        provider.setFirstName(firstName);
        provider.setLastName(lastName);
        provider.setProviderNo(providerNo);
        provider.setSpecialty(specialty);
        provider.setProviderType("doctor");
        provider.setSex("M");
        provider.setSignedConfidentiality(new Date());
        provider.setStatus("1");
        providerDao.saveProvider(provider);
        return provider;
    }

    private Demographic createDemographic(String lastName, String firstName, String providerNo) {
        Demographic demographic = new Demographic();
        demographic.setLastName(lastName);
        demographic.setFirstName(firstName);
        demographic.setProviderNo(providerNo);
        demographic.setYearOfBirth("1958");
        demographic.setMonthOfBirth("05");
        demographic.setDateOfBirth("31");
        demographic.setSex("M");
        demographic.setPatientStatus("AC");
        demographic.setRosterStatus("RO");
        demographic.setHcType("ON");
        demographicDao.save(demographic);
        return demographic;
    }

    private Admission createAdmission(Demographic demographic, Date admDate) {
        Admission admission = new Admission();
        admission.setProgramId(oscarProgramID);
        admission.setClient(demographic);
        admission.setClientId(demographic.getDemographicNo());
        admission.setProviderNo(demographic.getProviderNo());
        admission.setAdmissionDate(admDate);
        admission.setAdmissionStatus("current");
        admission.setTeamId(null);
        admissionDao.saveAdmission(admission);
        return admission;
    }

    private ProgramProvider createProgramProvider(Provider provider) {
        // Ensure secrole record exists for FK constraint on role_id
        entityManager.createNativeQuery(
                "MERGE INTO secrole (role_no, role_name) KEY(role_no) VALUES (1, 'test_role')")
                .executeUpdate();

        ProgramProvider pp = new ProgramProvider();
        pp.setProgramId(Long.valueOf(oscarProgramID));
        pp.setProviderNo(provider.getProviderNo());
        pp.setRoleId(1L);
        programProviderDAO.saveProgramProvider(pp);
        return pp;
    }

    @Test
    @Tag("query")
    @DisplayName("should return non-empty admissions when querying by program and admitted date range")
    void shouldReturnNonEmptyAdmissions_whenQueryingByProgramAndAdmittedDateRange() {
        // Given - set up provider, program provider, demographic, and admission
        // (mirrors the core setup pattern from the legacy OntarioMDSpec4DataTest)
        Provider drw = createProvider("Marcus", "Welby", "111112", "00");
        createProgramProvider(drw);

        Demographic ericIdle = createDemographic("Idle", "Eric", drw.getProviderNo());
        createAdmission(ericIdle, referenceDate);

        Demographic ericaIdle = createDemographic("Idle", "Erica", drw.getProviderNo());
        createAdmission(ericaIdle, referenceDate);

        // When - query admissions by program and date range spanning 20 years back
        Calendar cal = Calendar.getInstance();
        Date today = cal.getTime();
        cal.set(Calendar.YEAR, cal.get(Calendar.YEAR) - 20);
        Date twentyYearsAgo = cal.getTime();

        List<Admission> admissions = admissionDao.getAdmissionsByProgramAndAdmittedDate(
                oscarProgramID, twentyYearsAgo, today);

        // Then
        assertThat(admissions).isNotEmpty();
    }

    @Test
    @Tag("query")
    @DisplayName("should return admissions matching the number of admitted patients")
    void shouldReturnAdmissions_matchingNumberOfAdmittedPatients() {
        // Given - create multiple providers and patients as in the legacy test
        Provider drw = createProvider("Marcus", "Welby", "111112", "00");
        Provider drl = createProvider("Livingstone", "drl", "100", "00");
        createProgramProvider(drw);
        createProgramProvider(drl);

        Demographic patient1 = createDemographic("Idle", "Eric", drw.getProviderNo());
        createAdmission(patient1, referenceDate);

        Demographic patient2 = createDemographic("Idle", "Erica", drw.getProviderNo());
        createAdmission(patient2, referenceDate);

        Demographic patient3 = createDemographic("Apricot", "April", drw.getProviderNo());
        createAdmission(patient3, referenceDate);

        // When
        Calendar cal = Calendar.getInstance();
        Date today = cal.getTime();
        cal.set(Calendar.YEAR, cal.get(Calendar.YEAR) - 20);
        Date twentyYearsAgo = cal.getTime();

        List<Admission> admissions = admissionDao.getAdmissionsByProgramAndAdmittedDate(
                oscarProgramID, twentyYearsAgo, today);

        // Then
        assertThat(admissions).hasSize(3);
    }

    @Test
    @Tag("query")
    @DisplayName("should return empty admissions when date range excludes all records")
    void shouldReturnEmptyAdmissions_whenDateRangeExcludesAllRecords() {
        // Given
        Provider drw = createProvider("Marcus", "Welby", "111112", "00");
        createProgramProvider(drw);

        Demographic patient = createDemographic("Idle", "Eric", drw.getProviderNo());
        createAdmission(patient, referenceDate);

        // When - query with a future date range
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, 10);
        Date futureStart = cal.getTime();
        cal.add(Calendar.YEAR, 1);
        Date futureEnd = cal.getTime();

        List<Admission> admissions = admissionDao.getAdmissionsByProgramAndAdmittedDate(
                oscarProgramID, futureStart, futureEnd);

        // Then
        assertThat(admissions).isEmpty();
    }
}
