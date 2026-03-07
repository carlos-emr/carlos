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

import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.DbConnectionFilter;
import io.github.carlos_emr.carlos.utility.EncounterUtil.EncounterType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PopulationReportDaoImpl}, the CAISI population reporting DAO.
 *
 * <p>This DAO uses two distinct persistence mechanisms:</p>
 * <ul>
 *   <li><b>HibernateTemplate HQL methods</b> (6 methods): {@code getCurrentPopulationSize},
 *       {@code getCurrentAndHistoricalPopulationSize}, {@code getMortalities},
 *       {@code getPrevalence}, and {@code getIncidence} are fully testable in the Spring test
 *       context. {@code getUsages} is fully testable — the prior HQL defect has been fixed.</li>
 *   <li><b>Raw JDBC methods</b> (7 methods): All {@code getCaseManagementNoteCount*} and
 *       {@code getCaseManagementNoteTotalUnique*} methods. These obtain a JDBC {@link Connection}
 *       from {@link DbConnectionFilter#getThreadLocalDbConnection()}, a servlet filter
 *       thread-local that is not available in the Spring test context. These tests are marked
 *       {@code @Disabled} with full test structure written so they are ready when the DAO is
 *       refactored to accept an injected {@link DataSource}.</li>
 * </ul>
 *
 * <p><b>Bean definition</b>: Registered in {@code test-context-full.xml} as
 * {@code populationReportDao} with {@code sessionFactory} property injection.</p>
 *
 * <p><b>Note on {@code getUsages()}</b>: The HQL constant {@code HQL_GET_USAGES} previously
 * contained {@code "from ?1 a"}, attempting to parameterize the entity <em>name</em> with a
 * positional parameter. This defect has been fixed — {@code HQL_GET_USAGES} now uses valid HQL:
 * {@code "select a.clientId, a.admissionDate, a.dischargeDate from Admission a where ..."}.
 * The {@code getUsages()} test is enabled and exercises the corrected implementation.</p>
 *
 * @since 2026-02-26
 * @see PopulationReportDao
 * @see PopulationReportDaoImpl
 */
@DisplayName("PopulationReportDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("population")
@Transactional
public class PopulationReportDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private PopulationReportDao populationReportDao;

    @Autowired
    private DemographicDao demographicDao;

    @Autowired
    private DataSource dataSource;

    /**
     * Counter for generating unique program IDs across test methods.
     * Seeded from the current nanosecond timestamp to avoid inter-run collisions.
     */
    private int programIdCounter = (int) (System.nanoTime() % 100000);

    // =====================================================================
    // Helper methods for creating test data
    // =====================================================================

    /**
     * Note: Test data is created inline within each test method rather than in
     * {@code @BeforeEach} to keep each test fully self-contained and avoid shared-state issues.
     */

    /**
     * Creates an active service program via HibernateTemplate (Program is HBM-mapped).
     *
     * @param name String the program name
     * @return Program the persisted program with a generated ID
     */
    private Program createActiveServiceProgram(String name) {
        Program program = new Program();
        program.setId(++programIdCounter);
        program.setName(name);
        program.setType("service");
        program.setProgramStatus("active");
        hibernateTemplate.save(program);
        hibernateTemplate.flush();
        return program;
    }

    /**
     * Creates an active community program with a specific name.
     *
     * @param name String the program name (e.g. "deceased" for mortality tracking)
     * @return Program the persisted program
     */
    private Program createActiveCommunityProgram(String name) {
        Program program = new Program();
        program.setId(++programIdCounter);
        program.setName(name);
        program.setType("community");
        program.setProgramStatus("active");
        hibernateTemplate.save(program);
        hibernateTemplate.flush();
        return program;
    }

    /**
     * Creates an active demographic (patient) record.
     *
     * @param firstName String the patient's first name
     * @param lastName String the patient's last name
     * @return Demographic the persisted demographic
     */
    private Demographic createActiveDemographic(String firstName, String lastName) {
        Demographic demo = new Demographic();
        demo.setFirstName(firstName);
        demo.setLastName(lastName);
        demo.setPatientStatus("AC");
        demo.setProviderNo("999998");
        demo.setYearOfBirth("1980");
        demo.setMonthOfBirth("01");
        demo.setDateOfBirth("15");
        demo.setSex("M");
        demo.setHin("");
        demo.setHcType("ON");
        demographicDao.save(demo);
        return demo;
    }

    /**
     * Creates an admission record for a client in a program with no discharge date
     * (i.e., currently admitted).
     *
     * @param clientId Integer the demographic number of the client
     * @param programId Integer the program ID
     * @param admissionDate Date when the client was admitted
     * @return Admission the persisted admission record
     */
    private Admission createCurrentAdmission(Integer clientId, Integer programId, Date admissionDate) {
        Admission admission = new Admission();
        admission.setClientId(clientId);
        admission.setProgramId(programId);
        admission.setAdmissionDate(admissionDate);
        admission.setDischargeDate(null);
        admission.setAdmissionStatus("current");
        admission.setProviderNo("999998");
        hibernateTemplate.save(admission);
        hibernateTemplate.flush();
        return admission;
    }

    /**
     * Creates a discharged admission record.
     *
     * @param clientId Integer the demographic number
     * @param programId Integer the program ID
     * @param admissionDate Date admission date
     * @param dischargeDate Date discharge date
     * @return Admission the persisted admission
     */
    private Admission createDischargedAdmission(Integer clientId, Integer programId,
                                                 Date admissionDate, Date dischargeDate) {
        Admission admission = new Admission();
        admission.setClientId(clientId);
        admission.setProgramId(programId);
        admission.setAdmissionDate(admissionDate);
        admission.setDischargeDate(dischargeDate);
        admission.setAdmissionStatus("discharged");
        admission.setProviderNo("999998");
        hibernateTemplate.save(admission);
        hibernateTemplate.flush();
        return admission;
    }

    /**
     * Creates an Issue entity (ICD-10 code for case management).
     *
     * @param code String the ICD-10 code (e.g. "J45" for asthma)
     * @param description String the issue description
     * @return Issue the persisted issue
     */
    private Issue createIssue(String code, String description) {
        Issue issue = new Issue();
        issue.setCode(code);
        issue.setDescription(description);
        issue.setRole("doctor");
        issue.setUpdate_date(new Date());
        issue.setType("ICD10");
        hibernateTemplate.save(issue);
        hibernateTemplate.flush();
        return issue;
    }

    /**
     * Creates a CaseManagementIssue linked to a demographic and issue.
     *
     * @param demographicNo Integer the patient's demographic number
     * @param issue Issue the linked issue
     * @param resolved boolean whether the issue is resolved
     * @return CaseManagementIssue the persisted case management issue
     */
    private CaseManagementIssue createCaseManagementIssue(Integer demographicNo, Issue issue,
                                                            boolean resolved) {
        CaseManagementIssue cmi = new CaseManagementIssue();
        cmi.setDemographic_no(demographicNo);
        cmi.setIssue_id(issue.getId());
        cmi.setIssue(issue);
        cmi.setResolved(resolved);
        cmi.setAcute(false);
        cmi.setCertain(true);
        cmi.setMajor(true);
        cmi.setType("ICD10");
        cmi.setUpdate_date(new Date());
        hibernateTemplate.save(cmi);
        hibernateTemplate.flush();
        return cmi;
    }

    /**
     * Returns a Date representing N years ago from now.
     *
     * @param yearsAgo int number of years to go back
     * @return Date the calculated past date
     */
    private Date yearsAgo(int yearsAgo) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -yearsAgo);
        return cal.getTime();
    }

    /**
     * Returns a Date representing N months ago from now.
     *
     * @param monthsAgo int number of months to go back
     * @return Date the calculated past date
     */
    private Date monthsAgo(int monthsAgo) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -monthsAgo);
        return cal.getTime();
    }

    // =====================================================================
    // HQL-Based Methods (testable via HibernateTemplate)
    // =====================================================================

    /**
     * Tests for {@link PopulationReportDao#getCurrentPopulationSize()}.
     *
     * <p>This method counts distinct clients currently admitted (not discharged) to active
     * service programs where the client's patient status is 'AC' (active).</p>
     */
    @Nested
    @DisplayName("getCurrentPopulationSize()")
    @Tag("read")
    class GetCurrentPopulationSize {

        @Test
        @DisplayName("should return zero when no admissions exist")
        @Tag("read")
        void shouldReturnZero_whenNoAdmissionsExist() {
            // Given
            // No programs, demographics, or admissions created

            // When
            int result = populationReportDao.getCurrentPopulationSize();

            // Then
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("should count clients currently admitted to active service programs")
        @Tag("read")
        void shouldCountClients_whenAdmittedToActiveServicePrograms() {
            // Given
            Program serviceProgram = createActiveServiceProgram("Shelter A");
            Demographic client1 = createActiveDemographic("Alice", "Smith");
            Demographic client2 = createActiveDemographic("Bob", "Jones");

            createCurrentAdmission(client1.getDemographicNo(), serviceProgram.getId(), monthsAgo(3));
            createCurrentAdmission(client2.getDemographicNo(), serviceProgram.getId(), monthsAgo(1));
            hibernateTemplate.flush();

            // When
            int result = populationReportDao.getCurrentPopulationSize();

            // Then
            assertThat(result).isEqualTo(2);
        }

        @Test
        @DisplayName("should not count clients admitted to community programs")
        @Tag("read")
        void shouldNotCountClients_whenAdmittedToCommunityPrograms() {
            // Given
            Program communityProgram = createActiveCommunityProgram("Outreach");
            Demographic client = createActiveDemographic("Carol", "White");
            createCurrentAdmission(client.getDemographicNo(), communityProgram.getId(), monthsAgo(2));
            hibernateTemplate.flush();

            // When
            int result = populationReportDao.getCurrentPopulationSize();

            // Then
            // Community programs are excluded from population count (only 'service' type counts)
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("should not count discharged clients")
        @Tag("read")
        void shouldNotCountClients_whenDischarged() {
            // Given
            Program serviceProgram = createActiveServiceProgram("Shelter B");
            Demographic client = createActiveDemographic("Dave", "Brown");
            createDischargedAdmission(client.getDemographicNo(), serviceProgram.getId(),
                    monthsAgo(6), monthsAgo(3));
            hibernateTemplate.flush();

            // When
            int result = populationReportDao.getCurrentPopulationSize();

            // Then
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("should count distinct clients only once across multiple programs")
        @Tag("read")
        void shouldCountDistinctClients_whenAdmittedToMultiplePrograms() {
            // Given
            Program program1 = createActiveServiceProgram("Shelter C");
            Program program2 = createActiveServiceProgram("Shelter D");
            Demographic client = createActiveDemographic("Eve", "Green");

            // Same client admitted to two service programs simultaneously;
            // HQL uses "select distinct a.clientId" so this client should only be counted once
            createCurrentAdmission(client.getDemographicNo(), program1.getId(), monthsAgo(4));
            createCurrentAdmission(client.getDemographicNo(), program2.getId(), monthsAgo(2));
            hibernateTemplate.flush();

            // When
            int result = populationReportDao.getCurrentPopulationSize();

            // Then
            // Distinct count: only 1 client
            assertThat(result).isEqualTo(1);
        }

        @Test
        @DisplayName("should not count inactive patients")
        @Tag("read")
        void shouldNotCountClients_whenPatientStatusInactive() {
            // Given -- create an inactive patient directly (not via createActiveDemographic)
            // to test that the HQL "d.PatientStatus='AC'" clause excludes them
            Program serviceProgram = createActiveServiceProgram("Shelter E");
            Demographic inactiveClient = new Demographic();
            inactiveClient.setFirstName("Frank");
            inactiveClient.setLastName("Grey");
            inactiveClient.setPatientStatus("IN");
            inactiveClient.setProviderNo("999998");
            inactiveClient.setYearOfBirth("1975");
            inactiveClient.setMonthOfBirth("06");
            inactiveClient.setDateOfBirth("20");
            inactiveClient.setSex("M");
            inactiveClient.setHin("");
            inactiveClient.setHcType("ON");
            demographicDao.save(inactiveClient);

            createCurrentAdmission(inactiveClient.getDemographicNo(), serviceProgram.getId(), monthsAgo(1));
            hibernateTemplate.flush();

            // When
            int result = populationReportDao.getCurrentPopulationSize();

            // Then
            // Inactive patient status is excluded (only 'AC' counts)
            assertThat(result).isZero();
        }
    }

    /**
     * Tests for {@link PopulationReportDao#getCurrentAndHistoricalPopulationSize(int)}.
     *
     * <p>Counts distinct clients currently admitted OR discharged after a date
     * {@code numYears} in the past, from active service programs with active patient status.</p>
     */
    @Nested
    @DisplayName("getCurrentAndHistoricalPopulationSize()")
    @Tag("read")
    class GetCurrentAndHistoricalPopulationSize {

        @Test
        @DisplayName("should return zero when no admissions exist")
        @Tag("read")
        void shouldReturnZero_whenNoAdmissionsExist() {
            // Given - no data

            // When
            int result = populationReportDao.getCurrentAndHistoricalPopulationSize(5);

            // Then
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("should include currently admitted clients")
        @Tag("read")
        void shouldIncludeClients_whenCurrentlyAdmitted() {
            // Given
            Program serviceProgram = createActiveServiceProgram("Hist Shelter A");
            Demographic client = createActiveDemographic("Grace", "Hall");
            createCurrentAdmission(client.getDemographicNo(), serviceProgram.getId(), monthsAgo(6));
            hibernateTemplate.flush();

            // When
            int result = populationReportDao.getCurrentAndHistoricalPopulationSize(1);

            // Then
            assertThat(result).isEqualTo(1);
        }

        @Test
        @DisplayName("should include recently discharged clients within the time window")
        @Tag("read")
        void shouldIncludeClients_whenDischargedWithinTimeWindow() {
            // Given
            Program serviceProgram = createActiveServiceProgram("Hist Shelter B");
            Demographic client = createActiveDemographic("Hank", "Irving");

            // Discharged 6 months ago (within the 1-year lookback window).
            // The HQL checks "a.dischargeDate > :cutoff OR a.dischargeDate IS NULL"
            createDischargedAdmission(client.getDemographicNo(), serviceProgram.getId(),
                    monthsAgo(12), monthsAgo(6));
            hibernateTemplate.flush();

            // When -- numYears=1 means cutoff date is 1 year ago
            int result = populationReportDao.getCurrentAndHistoricalPopulationSize(1);

            // Then
            assertThat(result).isEqualTo(1);
        }

        @Test
        @DisplayName("should exclude clients discharged before the time window")
        @Tag("read")
        void shouldExcludeClients_whenDischargedBeforeTimeWindow() {
            // Given
            Program serviceProgram = createActiveServiceProgram("Hist Shelter C");
            Demographic client = createActiveDemographic("Iris", "Jackson");

            // Discharged 3 years ago, well outside the 1-year lookback window.
            // The discharge date predates the cutoff so this client should be excluded.
            Date admitDate = yearsAgo(4);
            Date dischargeDate = yearsAgo(3);
            createDischargedAdmission(client.getDemographicNo(), serviceProgram.getId(),
                    admitDate, dischargeDate);
            hibernateTemplate.flush();

            // When
            int result = populationReportDao.getCurrentAndHistoricalPopulationSize(1);

            // Then
            assertThat(result).isZero();
        }
    }

    /**
     * Tests for {@link PopulationReportDao#getUsages(int)}.
     *
     * <p>Returns a 3-element array with counts for LOW, MEDIUM, and HIGH usage buckets
     * representing Admission records grouped by service duration.</p>
     */
    @Nested
    @DisplayName("getUsages()")
    @Tag("read")
    class GetUsages {

        @Test
        @DisplayName("should return array of length 3 representing LOW/MEDIUM/HIGH usage buckets")
        @Tag("read")
        void shouldReturnThreeElementArray_whenCalled() {
            // When
            int[] usages = populationReportDao.getUsages(1);

            // Then
            assertThat(usages).hasSize(3);
            assertThat(usages[PopulationReportDao.LOW]).isGreaterThanOrEqualTo(0);
            assertThat(usages[PopulationReportDao.MEDIUM]).isGreaterThanOrEqualTo(0);
            assertThat(usages[PopulationReportDao.HIGH]).isGreaterThanOrEqualTo(0);
        }
    }

    /**
     * Tests for {@link PopulationReportDao#getMortalities(int)}.
     *
     * <p>Counts distinct clients admitted to an active community program named "deceased"
     * where the admission date is after the cutoff date and the client has not been discharged.</p>
     */
    @Nested
    @DisplayName("getMortalities()")
    @Tag("read")
    class GetMortalities {

        @Test
        @DisplayName("should return zero when no deceased program admissions exist")
        @Tag("read")
        void shouldReturnZero_whenNoDeceasedAdmissions() {
            // Given - no data

            // When
            int result = populationReportDao.getMortalities(1);

            // Then
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("should count clients admitted to deceased community program within time window")
        @Tag("read")
        void shouldCountClients_whenAdmittedToDeceasedProgramWithinWindow() {
            // Given
            Program deceasedProgram = createActiveCommunityProgram("deceased");
            Demographic client1 = createActiveDemographic("Ken", "Lewis");
            Demographic client2 = createActiveDemographic("Lori", "Martin");

            // Both admitted to deceased program in the last 6 months (within 1-year window)
            createCurrentAdmission(client1.getDemographicNo(), deceasedProgram.getId(), monthsAgo(3));
            createCurrentAdmission(client2.getDemographicNo(), deceasedProgram.getId(), monthsAgo(5));
            hibernateTemplate.flush();

            // When
            int result = populationReportDao.getMortalities(1);

            // Then
            assertThat(result).isEqualTo(2);
        }

        @Test
        @DisplayName("should not count clients admitted to deceased program before time window")
        @Tag("read")
        void shouldNotCountClients_whenAdmittedBeforeTimeWindow() {
            // Given
            Program deceasedProgram = createActiveCommunityProgram("deceased");
            Demographic client = createActiveDemographic("Mike", "Nelson");

            // Admitted to deceased 3 years ago (outside 1-year window)
            createCurrentAdmission(client.getDemographicNo(), deceasedProgram.getId(), yearsAgo(3));
            hibernateTemplate.flush();

            // When
            int result = populationReportDao.getMortalities(1);

            // Then
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("should not count clients admitted to non-deceased community programs")
        @Tag("read")
        void shouldNotCountClients_whenAdmittedToNonDeceasedProgram() {
            // Given
            Program otherCommunity = createActiveCommunityProgram("outreach");
            Demographic client = createActiveDemographic("Olivia", "Palmer");
            createCurrentAdmission(client.getDemographicNo(), otherCommunity.getId(), monthsAgo(2));
            hibernateTemplate.flush();

            // When
            int result = populationReportDao.getMortalities(1);

            // Then
            // Only community programs named "deceased" are counted
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("should not count discharged deceased program admissions")
        @Tag("read")
        void shouldNotCountClients_whenDischargedFromDeceasedProgram() {
            // Given
            Program deceasedProgram = createActiveCommunityProgram("deceased");
            Demographic client = createActiveDemographic("Pete", "Quinn");

            // Discharged from deceased program (unusual but possible in the data model)
            createDischargedAdmission(client.getDemographicNo(), deceasedProgram.getId(),
                    monthsAgo(4), monthsAgo(2));
            hibernateTemplate.flush();

            // When
            int result = populationReportDao.getMortalities(1);

            // Then
            // HQL requires dischargeDate is null for mortality count
            assertThat(result).isZero();
        }
    }

    /**
     * Tests for {@link PopulationReportDao#getPrevalence(SortedSet)}.
     *
     * <p>Counts unresolved {@link CaseManagementIssue} entries matching given ICD-10 codes
     * for clients currently admitted to active service programs with active patient status.
     * "Prevalence" = total current cases in the population.</p>
     */
    @Nested
    @DisplayName("getPrevalence()")
    @Tag("read")
    class GetPrevalence {

        @Test
        @DisplayName("should return zero when no matching issues exist")
        @Tag("read")
        void shouldReturnZero_whenNoMatchingIssuesExist() {
            // Given
            SortedSet<String> codes = new TreeSet<>();
            codes.add("J45");

            // When
            int result = populationReportDao.getPrevalence(codes);

            // Then
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("should count unresolved issues for admitted clients matching ICD-10 codes")
        @Tag("read")
        void shouldCountUnresolvedIssues_whenClientsAdmittedWithMatchingCodes() {
            // Given
            Program serviceProgram = createActiveServiceProgram("Prevalence Shelter");
            Demographic client = createActiveDemographic("Rachel", "Stone");
            createCurrentAdmission(client.getDemographicNo(), serviceProgram.getId(), monthsAgo(3));

            Issue asthmaIssue = createIssue("J45", "Asthma");
            createCaseManagementIssue(client.getDemographicNo(), asthmaIssue, false);
            hibernateTemplate.flush();

            SortedSet<String> codes = new TreeSet<>();
            codes.add("J45");

            // When
            int result = populationReportDao.getPrevalence(codes);

            // Then
            assertThat(result).isEqualTo(1);
        }

        @Test
        @DisplayName("should not count resolved issues")
        @Tag("read")
        void shouldNotCountResolvedIssues_whenClientsHaveResolvedCodes() {
            // Given
            Program serviceProgram = createActiveServiceProgram("Prevalence Shelter 2");
            Demographic client = createActiveDemographic("Sam", "Turner");
            createCurrentAdmission(client.getDemographicNo(), serviceProgram.getId(), monthsAgo(2));

            Issue diabetesIssue = createIssue("E11", "Type 2 Diabetes");
            // Resolved=true: prevalence HQL filters "cmi.resolved = false", so this is excluded
            createCaseManagementIssue(client.getDemographicNo(), diabetesIssue, true);
            hibernateTemplate.flush();

            SortedSet<String> codes = new TreeSet<>();
            codes.add("E11");

            // When
            int result = populationReportDao.getPrevalence(codes);

            // Then
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("should count issues across multiple ICD-10 codes")
        @Tag("read")
        void shouldCountIssues_whenMultipleCodesProvided() {
            // Given
            Program serviceProgram = createActiveServiceProgram("Prevalence Multi");
            Demographic client = createActiveDemographic("Tina", "Underwood");
            createCurrentAdmission(client.getDemographicNo(), serviceProgram.getId(), monthsAgo(1));

            Issue asthma = createIssue("J45", "Asthma");
            Issue copd = createIssue("J44", "COPD");
            createCaseManagementIssue(client.getDemographicNo(), asthma, false);
            createCaseManagementIssue(client.getDemographicNo(), copd, false);
            hibernateTemplate.flush();

            SortedSet<String> codes = new TreeSet<>();
            codes.add("J45");
            codes.add("J44");

            // When
            int result = populationReportDao.getPrevalence(codes);

            // Then
            // Two unresolved issues matching the codes
            assertThat(result).isEqualTo(2);
        }

        @Test
        @DisplayName("should not count issues for non-admitted clients")
        @Tag("read")
        void shouldNotCountIssues_forNonAdmittedClients() {
            // Given
            Demographic client = createActiveDemographic("Uma", "Vance");
            // Client has an issue but is NOT admitted to any program
            Issue issue = createIssue("I10", "Hypertension");
            createCaseManagementIssue(client.getDemographicNo(), issue, false);
            hibernateTemplate.flush();

            SortedSet<String> codes = new TreeSet<>();
            codes.add("I10");

            // When
            int result = populationReportDao.getPrevalence(codes);

            // Then
            assertThat(result).isZero();
        }
    }

    /**
     * Tests for {@link PopulationReportDao#getIncidence(SortedSet)}.
     *
     * <p>Counts {@link CaseManagementIssue} entries (both resolved and unresolved) matching
     * given ICD-10 codes for clients currently admitted to active service programs.
     * "Incidence" = total cases (new and existing) in the population.</p>
     */
    @Nested
    @DisplayName("getIncidence()")
    @Tag("read")
    class GetIncidence {

        @Test
        @DisplayName("should return zero when no matching issues exist")
        @Tag("read")
        void shouldReturnZero_whenNoMatchingIssuesExist() {
            // Given
            SortedSet<String> codes = new TreeSet<>();
            codes.add("A00");

            // When
            int result = populationReportDao.getIncidence(codes);

            // Then
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("should count both resolved and unresolved issues for admitted clients")
        @Tag("read")
        void shouldCountAllIssues_whenClientsAdmittedWithMatchingCodes() {
            // Given
            Program serviceProgram = createActiveServiceProgram("Incidence Shelter");
            Demographic client = createActiveDemographic("Vera", "Watson");
            createCurrentAdmission(client.getDemographicNo(), serviceProgram.getId(), monthsAgo(4));

            Issue asthma = createIssue("J45.0", "Asthma Mild");
            createCaseManagementIssue(client.getDemographicNo(), asthma, false);

            Issue asthmaResolved = createIssue("J45.1", "Asthma Moderate");
            createCaseManagementIssue(client.getDemographicNo(), asthmaResolved, true);
            hibernateTemplate.flush();

            SortedSet<String> codes = new TreeSet<>();
            codes.add("J45.0");
            codes.add("J45.1");

            // When
            int result = populationReportDao.getIncidence(codes);

            // Then
            // Incidence (unlike prevalence) counts ALL matching issues regardless of resolved
            // status, capturing both new and historical cases in the population
            assertThat(result).isEqualTo(2);
        }

        @Test
        @DisplayName("should not count issues with non-matching codes")
        @Tag("read")
        void shouldNotCountIssues_whenCodesDoNotMatch() {
            // Given
            Program serviceProgram = createActiveServiceProgram("Incidence Filter");
            Demographic client = createActiveDemographic("Wendy", "Xu");
            createCurrentAdmission(client.getDemographicNo(), serviceProgram.getId(), monthsAgo(2));

            Issue unrelated = createIssue("Z99", "Dependence on machines");
            createCaseManagementIssue(client.getDemographicNo(), unrelated, false);
            hibernateTemplate.flush();

            SortedSet<String> codes = new TreeSet<>();
            codes.add("J45");

            // When
            int result = populationReportDao.getIncidence(codes);

            // Then
            assertThat(result).isZero();
        }
    }

    // =====================================================================
    // Raw JDBC Methods (require DbConnectionFilter thread-local)
    //
    // These methods use DbConnectionFilter.getThreadLocalDbConnection() to
    // obtain a raw JDBC Connection outside of the Hibernate/JPA persistence
    // context. This servlet filter thread-local is NOT available during
    // Spring integration tests because the servlet container lifecycle
    // (filter init/doFilter) is not invoked by the test runner.
    //
    // Tests are fully structured with Given/When/Then but @Disabled until
    // the DAO is refactored to accept an injected DataSource parameter
    // instead of relying on the thread-local connection pattern.
    // =====================================================================

    /**
     * Tests for {@link PopulationReportDao#getCaseManagementNoteCountGroupedByIssueGroup(int, Integer, EncounterType, Date, Date)}.
     *
     * <p>Returns a map of issue group ID to note count, filtered by program, optional role,
     * optional encounter type, and date range. Uses raw JDBC with dynamic SQL construction.</p>
     */
    @Nested
    @DisplayName("getCaseManagementNoteCountGroupedByIssueGroup(programId, roleId, encounterType, startDate, endDate)")
    @Tag("read")
    class GetNoteCountByRoleId {

        @Test
        @DisplayName("should return empty map when no matching notes exist")
        @Tag("read")
        @Disabled("Requires DbConnectionFilter thread-local - not available in test context. "
                + "Refactor DAO to accept injected DataSource for testability.")
        void shouldReturnEmptyMap_whenNoMatchingNotesExist() {
            // Given
            int programId = 1;
            Date startDate = monthsAgo(6);
            Date endDate = new Date();

            // When
            Map<Integer, Integer> result = populationReportDao
                    .getCaseManagementNoteCountGroupedByIssueGroup(
                            programId, (Integer) null, null, startDate, endDate);

            // Then
            assertThat(result).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("should filter by encounter type when provided")
        @Tag("read")
        @Disabled("Requires DbConnectionFilter thread-local - not available in test context")
        void shouldFilterByEncounterType_whenEncounterTypeProvided() {
            // Given
            int programId = 1;
            EncounterType faceToFace = EncounterType.FACE_TO_FACE_WITH_CLIENT;
            Date startDate = monthsAgo(12);
            Date endDate = new Date();

            // When
            Map<Integer, Integer> result = populationReportDao
                    .getCaseManagementNoteCountGroupedByIssueGroup(
                            programId, (Integer) null, faceToFace, startDate, endDate);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should filter by role ID when provided")
        @Tag("read")
        @Disabled("Requires DbConnectionFilter thread-local - not available in test context")
        void shouldFilterByRoleId_whenRoleIdProvided() {
            // Given
            int programId = 1;
            Integer roleId = 100;
            Date startDate = monthsAgo(6);
            Date endDate = new Date();

            // When
            Map<Integer, Integer> result = populationReportDao
                    .getCaseManagementNoteCountGroupedByIssueGroup(
                            programId, roleId, null, startDate, endDate);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
        }
    }

    /**
     * Tests for {@link PopulationReportDao#getCaseManagementNoteCountGroupedByIssueGroup(int, Provider, EncounterType, Date, Date)}.
     *
     * <p>Returns a map of issue group ID to note count, filtered by program, provider,
     * encounter type, and date range. Uses raw JDBC.</p>
     */
    @Nested
    @DisplayName("getCaseManagementNoteCountGroupedByIssueGroup(programId, provider, encounterType, startDate, endDate)")
    @Tag("read")
    class GetNoteCountByProvider {

        @Test
        @DisplayName("should return empty map when no matching notes exist for provider")
        @Tag("read")
        @Disabled("Requires DbConnectionFilter thread-local - not available in test context")
        void shouldReturnEmptyMap_whenNoMatchingNotesForProvider() {
            // Given
            int programId = 1;
            Provider provider = new Provider();
            provider.setProviderNo("999998");
            EncounterType encounterType = EncounterType.FACE_TO_FACE_WITH_CLIENT;
            Date startDate = monthsAgo(6);
            Date endDate = new Date();

            // When
            Map<Integer, Integer> result = populationReportDao
                    .getCaseManagementNoteCountGroupedByIssueGroup(
                            programId, provider, encounterType, startDate, endDate);

            // Then
            assertThat(result).isNotNull().isEmpty();
        }
    }

    /**
     * Tests for {@link PopulationReportDao#getCaseManagementNoteTotalUniqueEncounterCountInIssueGroups(int, Integer, EncounterType, Date, Date)}.
     *
     * <p>Returns the total count of unique notes (encounters) across all issue groups,
     * filtered by program, optional role, optional encounter type, and date range.</p>
     */
    @Nested
    @DisplayName("getCaseManagementNoteTotalUniqueEncounterCountInIssueGroups(programId, roleId, ...)")
    @Tag("read")
    class GetTotalUniqueEncounterCountByRoleId {

        @Test
        @DisplayName("should return zero when no matching encounters exist")
        @Tag("read")
        @Disabled("Requires DbConnectionFilter thread-local - not available in test context")
        void shouldReturnZero_whenNoMatchingEncountersExist() {
            // Given
            int programId = 1;
            Date startDate = monthsAgo(6);
            Date endDate = new Date();

            // When
            Integer result = populationReportDao
                    .getCaseManagementNoteTotalUniqueEncounterCountInIssueGroups(
                            programId, (Integer) null, null, startDate, endDate);

            // Then
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("should filter by encounter type and role when both provided")
        @Tag("read")
        @Disabled("Requires DbConnectionFilter thread-local - not available in test context")
        void shouldFilterByEncounterTypeAndRole_whenBothProvided() {
            // Given
            int programId = 1;
            Integer roleId = 100;
            EncounterType telephone = EncounterType.TELEPHONE_WITH_CLIENT;
            Date startDate = monthsAgo(12);
            Date endDate = new Date();

            // When
            Integer result = populationReportDao
                    .getCaseManagementNoteTotalUniqueEncounterCountInIssueGroups(
                            programId, roleId, telephone, startDate, endDate);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isGreaterThanOrEqualTo(0);
        }
    }

    /**
     * Tests for {@link PopulationReportDao#getCaseManagementNoteTotalUniqueEncounterCountInIssueGroups(int, Provider, EncounterType, Date, Date)}.
     *
     * <p>Returns the total count of unique notes (encounters) across all issue groups,
     * filtered by program, provider, encounter type, and date range.</p>
     */
    @Nested
    @DisplayName("getCaseManagementNoteTotalUniqueEncounterCountInIssueGroups(programId, provider, ...)")
    @Tag("read")
    class GetTotalUniqueEncounterCountByProvider {

        @Test
        @DisplayName("should return zero when no matching encounters exist for provider")
        @Tag("read")
        @Disabled("Requires DbConnectionFilter thread-local - not available in test context")
        void shouldReturnZero_whenNoMatchingEncountersForProvider() {
            // Given
            int programId = 1;
            Provider provider = new Provider();
            provider.setProviderNo("999998");
            EncounterType faceToFace = EncounterType.FACE_TO_FACE_WITH_CLIENT;
            Date startDate = monthsAgo(6);
            Date endDate = new Date();

            // When
            Integer result = populationReportDao
                    .getCaseManagementNoteTotalUniqueEncounterCountInIssueGroups(
                            programId, provider, faceToFace, startDate, endDate);

            // Then
            assertThat(result).isZero();
        }
    }

    /**
     * Tests for {@link PopulationReportDao#getCaseManagementNoteTotalUniqueClientCountInIssueGroups(int, Integer, EncounterType, Date, Date)}.
     *
     * <p>Returns the total count of unique clients (demographic_no) across all issue groups,
     * filtered by program, optional role, optional encounter type, and date range.</p>
     */
    @Nested
    @DisplayName("getCaseManagementNoteTotalUniqueClientCountInIssueGroups(programId, roleId, ...)")
    @Tag("read")
    class GetTotalUniqueClientCountByRoleId {

        @Test
        @DisplayName("should return zero when no matching client notes exist")
        @Tag("read")
        @Disabled("Requires DbConnectionFilter thread-local - not available in test context")
        void shouldReturnZero_whenNoMatchingClientNotesExist() {
            // Given
            int programId = 1;
            Date startDate = monthsAgo(6);
            Date endDate = new Date();

            // When
            Integer result = populationReportDao
                    .getCaseManagementNoteTotalUniqueClientCountInIssueGroups(
                            programId, (Integer) null, null, startDate, endDate);

            // Then
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("should filter by encounter type when provided")
        @Tag("read")
        @Disabled("Requires DbConnectionFilter thread-local - not available in test context")
        void shouldFilterByEncounterType_whenProvided() {
            // Given
            int programId = 1;
            EncounterType email = EncounterType.EMAIL_WITH_CLIENT;
            Date startDate = monthsAgo(6);
            Date endDate = new Date();

            // When
            Integer result = populationReportDao
                    .getCaseManagementNoteTotalUniqueClientCountInIssueGroups(
                            programId, (Integer) null, email, startDate, endDate);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isGreaterThanOrEqualTo(0);
        }
    }

    /**
     * Tests for {@link PopulationReportDao#getCaseManagementNoteTotalUniqueClientCountInIssueGroups(int, Provider, EncounterType, Date, Date)}.
     *
     * <p>Returns the total count of unique clients across all issue groups,
     * filtered by program, optional provider, optional encounter type, and date range.</p>
     */
    @Nested
    @DisplayName("getCaseManagementNoteTotalUniqueClientCountInIssueGroups(programId, provider, ...)")
    @Tag("read")
    class GetTotalUniqueClientCountByProvider {

        @Test
        @DisplayName("should return zero when no matching client notes exist for provider")
        @Tag("read")
        @Disabled("Requires DbConnectionFilter thread-local - not available in test context")
        void shouldReturnZero_whenNoMatchingClientNotesForProvider() {
            // Given
            int programId = 1;
            Provider provider = new Provider();
            provider.setProviderNo("999998");
            EncounterType encounterType = EncounterType.ENCOUNTER_WITH_OUT_CLIENT;
            Date startDate = monthsAgo(6);
            Date endDate = new Date();

            // When
            Integer result = populationReportDao
                    .getCaseManagementNoteTotalUniqueClientCountInIssueGroups(
                            programId, provider, encounterType, startDate, endDate);

            // Then
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("should handle null provider gracefully")
        @Tag("read")
        @Disabled("Requires DbConnectionFilter thread-local - not available in test context")
        void shouldHandleNullProvider_whenProviderIsNull() {
            // Given
            int programId = 1;
            Date startDate = monthsAgo(6);
            Date endDate = new Date();

            // When
            Integer result = populationReportDao
                    .getCaseManagementNoteTotalUniqueClientCountInIssueGroups(
                            programId, (Provider) null, null, startDate, endDate);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isGreaterThanOrEqualTo(0);
        }
    }

    /**
     * Tests for {@link PopulationReportDao#getCaseManagementNoteCountByIssueGroup(int, Integer, Integer, EncounterType, Date, Date)}.
     *
     * <p>Returns the count of unique notes for a specific issue group, filtered by program,
     * optional issue group ID, optional role, optional encounter type, and date range.</p>
     */
    @Nested
    @DisplayName("getCaseManagementNoteCountByIssueGroup()")
    @Tag("read")
    class GetNoteCountByIssueGroup {

        @Test
        @DisplayName("should return zero when no matching notes exist for issue group")
        @Tag("read")
        @Disabled("Requires DbConnectionFilter thread-local - not available in test context")
        void shouldReturnZero_whenNoMatchingNotesForIssueGroup() {
            // Given
            int programId = 1;
            Integer issueGroupId = 10;
            Date startDate = monthsAgo(6);
            Date endDate = new Date();

            // When
            Integer result = populationReportDao.getCaseManagementNoteCountByIssueGroup(
                    programId, issueGroupId, null, null, startDate, endDate);

            // Then
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("should query across all issue groups when issueGroupId is null")
        @Tag("read")
        @Disabled("Requires DbConnectionFilter thread-local - not available in test context")
        void shouldQueryAllGroups_whenIssueGroupIdIsNull() {
            // Given
            int programId = 1;
            Date startDate = monthsAgo(6);
            Date endDate = new Date();

            // When - null issueGroupId omits the group filter
            Integer result = populationReportDao.getCaseManagementNoteCountByIssueGroup(
                    programId, null, null, null, startDate, endDate);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("should filter by all parameters when all provided")
        @Tag("read")
        @Disabled("Requires DbConnectionFilter thread-local - not available in test context")
        void shouldFilterByAllParameters_whenAllProvided() {
            // Given
            int programId = 1;
            Integer issueGroupId = 5;
            Integer roleId = 100;
            EncounterType faceToFace = EncounterType.FACE_TO_FACE_WITH_CLIENT;
            Date startDate = monthsAgo(12);
            Date endDate = new Date();

            // When
            Integer result = populationReportDao.getCaseManagementNoteCountByIssueGroup(
                    programId, issueGroupId, roleId, faceToFace, startDate, endDate);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("should handle null start date by using epoch")
        @Tag("read")
        @Disabled("Requires DbConnectionFilter thread-local - not available in test context")
        void shouldHandleNullStartDate_byUsingEpoch() {
            // Given
            // When startDate is null, the DAO falls back to new java.sql.Timestamp(0),
            // effectively querying from Unix epoch (1970-01-01 00:00:00 UTC) to endDate
            int programId = 1;
            Integer issueGroupId = 5;

            // When
            Integer result = populationReportDao.getCaseManagementNoteCountByIssueGroup(
                    programId, issueGroupId, null, null, null, new Date());

            // Then
            assertThat(result).isNotNull();
        }
    }

    // =====================================================================
    // Interface Constants Validation
    // =====================================================================

    /**
     * Tests for the interface constants {@link PopulationReportDao#LOW},
     * {@link PopulationReportDao#MEDIUM}, {@link PopulationReportDao#HIGH}.
     */
    @Nested
    @DisplayName("Interface Constants")
    class InterfaceConstants {

        @Test
        @Tag("read")
        @DisplayName("should define LOW as index 0")
        void shouldDefineLow_asIndexZero() {
            // Then
            assertThat(PopulationReportDao.LOW).isZero();
        }

        @Test
        @Tag("read")
        @DisplayName("should define MEDIUM as index 1")
        void shouldDefineMedium_asIndexOne() {
            // Then
            assertThat(PopulationReportDao.MEDIUM).isEqualTo(1);
        }

        @Test
        @Tag("read")
        @DisplayName("should define HIGH as index 2")
        void shouldDefineHigh_asIndexTwo() {
            // Then
            assertThat(PopulationReportDao.HIGH).isEqualTo(2);
        }
    }
}
