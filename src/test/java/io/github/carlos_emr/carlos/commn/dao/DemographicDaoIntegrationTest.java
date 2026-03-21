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
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.dao.DemographicDaoImpl.DemographicCriterion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DemographicDao} multi-parameter query methods.
 *
 * <p>These tests validate HQL queries with positional parameters (?1, ?2, ...)
 * bind correctly, ensuring safe migration to Hibernate 6 named parameter syntax.
 * Tests cover CRUD operations, multi-parameter searches, and edge cases.</p>
 *
 * <p>The {@link DemographicDaoImpl#findByCriterion(DemographicCriterion)} method
 * is a particular focus, as it uses 7-8 positional HQL parameters depending on
 * whether a Health Insurance Number (HIN) is provided.</p>
 *
 * @since 2026-02-03
 * @see DemographicDao
 * @see DemographicDaoImpl
 */
@DisplayName("DemographicDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("demographic")
@Transactional
public class DemographicDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DemographicDao demographicDao;

    @Autowired
    private DemographicDaoImpl demographicDaoImpl;

    private Demographic demo1, demo2, demo3, demo4;
    private String uniquePrefix;

    @BeforeEach
    void setUp() {
        // Generate a 10-character nano-time prefix to ensure HIN uniqueness across parallel test runs
        String nanoStr = String.valueOf(System.nanoTime());
        uniquePrefix = nanoStr.substring(nanoStr.length() - 10);

        // Create a mix of demographics: 3 active (different provinces/names) and 1 inactive
        demo1 = createDemographic("John", "Smith", "ON", uniquePrefix + "0", "AC");
        demo2 = createDemographic("John", "Doe", "ON", uniquePrefix + "1", "AC");
        demo3 = createDemographic("Jane", "Smith", "BC", uniquePrefix + "2", "AC");
        demo4 = createDemographic("Bob", "Johnson", "ON", uniquePrefix + "3", "IN");  // Inactive
    }

    /**
     * Creates and persists a {@link Demographic} with the given attributes and no roster status.
     *
     * <p>Delegates to {@link #createDemographicWithRoster(String, String, String, String, String, String)}
     * with a {@code null} roster status. All demographics are created with a fixed provider
     * number ("999998"), birth date (1980-01-15), and sex ("M").</p>
     *
     * @param firstName String the patient's first name
     * @param lastName String the patient's last name
     * @param hcType String the health card type / province code (e.g. "ON", "BC")
     * @param hin String the Health Insurance Number (must be unique per test run)
     * @param patientStatus String the patient status code ("AC" for active, "IN" for inactive)
     * @return Demographic the persisted demographic entity with a generated ID
     */
    private Demographic createDemographic(String firstName, String lastName,
                                          String hcType, String hin, String patientStatus) {
        return createDemographicWithRoster(firstName, lastName, hcType, hin, patientStatus, null);
    }

    /**
     * Creates and persists a {@link Demographic} with the given attributes including roster status.
     *
     * <p>Sets fixed defaults for fields not under test: provider number ("999998"),
     * birth date (1980-01-15), and sex ("M"). The roster status is only set if non-null,
     * allowing tests to distinguish between "no roster" and a specific roster value.</p>
     *
     * @param firstName String the patient's first name
     * @param lastName String the patient's last name
     * @param hcType String the health card type / province code (e.g. "ON", "BC")
     * @param hin String the Health Insurance Number (must be unique per test run)
     * @param patientStatus String the patient status code ("AC" for active, "IN" for inactive)
     * @param rosterStatus String the roster status code (e.g. "RO", "NR"), or {@code null} to leave unset
     * @return Demographic the persisted demographic entity with a generated ID
     */
    private Demographic createDemographicWithRoster(String firstName, String lastName,
                                                     String hcType, String hin, String patientStatus,
                                                     String rosterStatus) {
        Demographic demo = new Demographic();
        demo.setFirstName(firstName);
        demo.setLastName(lastName);
        demo.setHcType(hcType);
        demo.setHin(hin);
        demo.setPatientStatus(patientStatus);
        demo.setProviderNo("999998");
        demo.setYearOfBirth("1980");
        demo.setMonthOfBirth("01");
        demo.setDateOfBirth("15");
        demo.setSex("M");
        if (rosterStatus != null) {
            demo.setRosterStatus(rosterStatus);
        }
        demographicDao.save(demo);
        hibernateTemplate.flush();
        return demo;
    }

    /**
     * Tests for basic CRUD operations on {@link Demographic} entities.
     *
     * <p>Validates create, read, and update operations through {@link DemographicDao},
     * including persistence of new records, retrieval by ID, update propagation,
     * and null handling for invalid IDs.</p>
     */
    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("read")
        @DisplayName("should retrieve demographic by valid ID")
        void shouldRetrieveDemographic_whenValidIdProvided() {
            // When
            Demographic found = demographicDao.getDemographicById(demo1.getDemographicNo());

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getFirstName()).isEqualTo("John");
            assertThat(found.getLastName()).isEqualTo("Smith");
        }

        @Test
        @Tag("create")
        @DisplayName("should persist demographic with valid data")
        void shouldPersistDemographic_whenValidDataProvided() {
            // Given
            Demographic newDemo = new Demographic();
            newDemo.setFirstName("New");
            newDemo.setLastName("Patient");
            newDemo.setHcType("ON");
            newDemo.setHin(uniquePrefix + "9");
            newDemo.setPatientStatus("AC");
            newDemo.setProviderNo("999998");
            newDemo.setYearOfBirth("1990");
            newDemo.setMonthOfBirth("06");
            newDemo.setDateOfBirth("20");
            newDemo.setSex("F");

            // When
            demographicDao.save(newDemo);
            hibernateTemplate.flush();

            // Then
            assertThat(newDemo.getDemographicNo()).isNotNull();
            Demographic found = demographicDao.getDemographicById(newDemo.getDemographicNo());
            assertThat(found).isNotNull();
            assertThat(found.getFirstName()).isEqualTo("New");
        }

        @Test
        @Tag("update")
        @DisplayName("should update demographic with changes")
        void shouldUpdateDemographic_whenChangesProvided() {
            // Given
            Demographic existing = demographicDao.getDemographicById(demo1.getDemographicNo());
            assertThat(existing).isNotNull();

            // When
            existing.setPhone("555-1234");
            demographicDao.save(existing);

            // Then
            Demographic updated = demographicDao.getDemographicById(demo1.getDemographicNo());
            assertThat(updated.getPhone()).isEqualTo("555-1234");
        }

        @Test
        @Tag("read")
        @DisplayName("should return null for invalid ID")
        void shouldReturnNull_whenInvalidIdProvided() {
            // When
            Demographic found = demographicDao.getDemographicById(-999);

            // Then
            assertThat(found).isNull();
        }
    }

    /**
     * Tests for {@link DemographicDao#getClientsByHealthCard(String, String)}.
     *
     * <p>This method uses 2 positional HQL parameters (HIN and health card type)
     * to look up demographics by their provincial health insurance number.</p>
     */
    @Nested
    @DisplayName("getClientsByHealthCard (2 params: hin, hcType)")
    class GetClientsByHealthCard {

        @Test
        @Tag("query")
        @DisplayName("should find demographic when both HIN and HC type match")
        void shouldFind_whenBothHinAndTypeMatch() {
            // When
            List<Demographic> results = demographicDao.getClientsByHealthCard(uniquePrefix + "0", "ON");

            // Then
            assertThat(results)
                .hasSize(1)
                .extracting(Demographic::getDemographicNo)
                .containsExactly(demo1.getDemographicNo());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when HIN doesn't match")
        void shouldReturnEmpty_whenHinDoesntMatch() {
            // When
            List<Demographic> results = demographicDao.getClientsByHealthCard("NONEXISTENT99999", "ON");

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when HC type doesn't match")
        void shouldReturnEmpty_whenTypeDoesntMatch() {
            // When
            List<Demographic> results = demographicDao.getClientsByHealthCard(uniquePrefix + "0", "BC");

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for {@link DemographicDao#getDemographicWithLastFirstDOB(String, String, String, String, String)}.
     *
     * <p>This method accepts 2 or more parameters (last name, first name, and optional
     * date-of-birth components) to search for demographics using LIKE-based matching.</p>
     */
    @Nested
    @DisplayName("getDemographicWithLastFirstDOB (2+ params)")
    class GetDemographicWithLastFirstDOB {

        @Test
        @Tag("query")
        @DisplayName("should find demographics matching last name and first name")
        void shouldFind_whenLastAndFirstNameMatch() {
            // When
            List<Demographic> results = demographicDao.getDemographicWithLastFirstDOB(
                "Smith", "John", null, null, null);

            // Then
            assertThat(results)
                .extracting(Demographic::getDemographicNo)
                .contains(demo1.getDemographicNo());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when names don't match")
        void shouldReturnEmpty_whenNamesDoNotMatch() {
            // When
            List<Demographic> results = demographicDao.getDemographicWithLastFirstDOB(
                "Nonexistent", "Nobody", null, null, null);

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for {@link DemographicDao#getDemographicByRosterStatus(String, String)}.
     *
     * <p>This method uses 2 positional HQL parameters (roster status and patient status)
     * to filter demographics by their enrollment/rostering state.</p>
     */
    @Nested
    @DisplayName("getDemographicByRosterStatus (2 params)")
    class GetDemographicByRosterStatus {

        @Test
        @Tag("query")
        @DisplayName("should filter by roster status and patient status")
        void shouldFilterByRosterAndPatientStatus() {
            // Given
            Demographic rostered = createDemographicWithRoster("Test", "Rostered", "ON", uniquePrefix + "R", "AC", "RO");
            Demographic notRostered = createDemographicWithRoster("Test", "NotRostered", "ON", uniquePrefix + "N", "AC", "NR");

            // When
            List<Demographic> results = demographicDao.getDemographicByRosterStatus("RO", "AC");

            // Then
            assertThat(results)
                .extracting(Demographic::getDemographicNo)
                .contains(rostered.getDemographicNo())
                .doesNotContain(notRostered.getDemographicNo());
        }
    }

    /**
     * Tests for general query operations on {@link DemographicDao}.
     *
     * <p>Covers aggregation ({@code getActiveDemographicCount}), pagination
     * ({@code getActiveDemographics}), provider-based filtering, HIN search,
     * name pattern search, and ID projection methods.</p>
     */
    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("aggregate")
        @DisplayName("should count active demographics")
        void shouldCountDemographics_byActiveStatus() {
            // When
            Long count = demographicDao.getActiveDemographicCount();

            // Then - At least 3 active (demo1, demo2, demo3)
            assertThat(count).isGreaterThanOrEqualTo(3L);
        }

        @Test
        @Tag("query")
        @DisplayName("should retrieve paginated results with offset and limit")
        void shouldRetrievePaginatedResults_withOffsetAndLimit() {
            // When
            List<Demographic> results = demographicDao.getActiveDemographics(0, 2);

            // Then
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("filter")
        @DisplayName("should filter demographic IDs by active status")
        void shouldFilterDemographicIds_byActiveStatus() {
            // When
            List<Integer> activeIds = demographicDao.getActiveDemographicIds();

            // Then
            assertThat(activeIds)
                .contains(demo1.getDemographicNo(), demo2.getDemographicNo(), demo3.getDemographicNo())
                .doesNotContain(demo4.getDemographicNo());
        }

        @Test
        @Tag("read")
        @DisplayName("should get demographics by provider")
        void shouldGetDemographics_byProvider() {
            // When
            List<Demographic> results = demographicDao.getDemographicByProvider("999998", true);

            // Then
            assertThat(results)
                .isNotEmpty()
                .allMatch(d -> d.getPatientStatus().equals("AC"));
        }

        @Test
        @Tag("query")
        @DisplayName("should handle provider queries with no matches")
        void shouldHandleProviderQueries_withNoMatches() {
            // When
            List<Demographic> results = demographicDao.getDemographicByProvider("NONEXISTENT_PROVIDER", true);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("search")
        @DisplayName("should search demographics by HIN")
        void shouldSearchDemographics_byHin() {
            // When
            List<Demographic> results = demographicDao.searchByHealthCard(uniquePrefix + "0");

            // Then
            assertThat(results)
                .hasSize(1)
                .extracting(Demographic::getHin)
                .containsExactly(uniquePrefix + "0");
        }

        @Test
        @Tag("search")
        @DisplayName("should search demographics by name pattern")
        void shouldSearchDemographics_byNamePattern() {
            // When
            List<Demographic> results = demographicDao.getDemographicWithLastFirstDOB(
                "Smith", "", null, null, null);

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(Demographic::getLastName)
                .allMatch(ln -> ln.trim().equals("Smith"));
        }

        @Test
        @Tag("read")
        @DisplayName("should project demographic IDs from database")
        void shouldProjectDemographicIds_fromDatabase() {
            // When
            List<Integer> ids = demographicDao.getDemographicIds();

            // Then
            assertThat(ids)
                .isNotEmpty()
                .contains(demo1.getDemographicNo(), demo2.getDemographicNo());
        }
    }

    /**
     * Tests for additional single-parameter (?1) query methods in {@link DemographicDao}.
     *
     * <p>These methods each use a single positional HQL parameter for filtering:
     * chart number lookup, year-of-birth filtering, health number search, exact name
     * matching, date-based additions, and active-after-date filtering.</p>
     */
    @Nested
    @DisplayName("Additional ?1 parameter queries")
    class AdditionalParameterQueries {

        @Test
        @Tag("query")
        @DisplayName("should find demographic by chart number")
        void shouldFindDemographic_byChartNo() {
            // Given -- assign a chart number post-creation to isolate this field
            demo1.setChartNo("CHT001");
            demographicDao.save(demo1);
            // Flush through Hibernate session since DemographicDao extends HibernateDaoSupport
            hibernateTemplate.flush();

            // When
            List<Demographic> results = demographicDao.getClientsByChartNo("CHT001");

            // Then
            assertThat(results)
                .hasSize(1)
                .extracting(Demographic::getDemographicNo)
                .containsExactly(demo1.getDemographicNo());
        }

        @Test
        @Tag("query")
        @DisplayName("should filter demographics by year of birth greater than threshold")
        void shouldFilterDemographics_byYearOfBirthGreaterThan() {
            // Given -- all setUp demographics have yearOfBirth "1980"; create one born in 2000
            // to verify the greater-than threshold filter excludes 1980 and includes 2000
            Demographic youngDemo = createDemographic("Young", "Person", "ON", uniquePrefix + "Y", "AC");
            youngDemo.setYearOfBirth("2000");
            demographicDao.save(youngDemo);
            hibernateTemplate.flush();

            // When
            List<Demographic> results = demographicDao.getDemographicWithGreaterThanYearOfBirth(1990);

            // Then
            assertThat(results)
                .extracting(Demographic::getDemographicNo)
                .contains(youngDemo.getDemographicNo())
                .doesNotContain(demo1.getDemographicNo());
        }

        @Test
        @Tag("query")
        @DisplayName("should find demographics by health number")
        void shouldFindDemographics_byHealthNum() {
            // When
            List<Demographic> results = demographicDao.getDemographicsByHealthNum(uniquePrefix + "0");

            // Then
            assertThat(results)
                .hasSize(1)
                .extracting(Demographic::getDemographicNo)
                .containsExactly(demo1.getDemographicNo());
        }

        @Test
        @Tag("query")
        @DisplayName("should find demographic by exact name match")
        void shouldFindDemographic_byExactNameMatch() {
            // When
            List<Demographic> results = demographicDao.getDemographicWithLastFirstDOBExact(
                "Smith", "John", null, null, null);

            // Then
            assertThat(results)
                .extracting(Demographic::getDemographicNo)
                .contains(demo1.getDemographicNo())
                .doesNotContain(demo2.getDemographicNo());  // John Doe should not match
        }

        @Test
        @Tag("query")
        @DisplayName("should return demographic IDs added since date")
        void shouldReturnDemographicIds_addedSinceDate() {
            // Given — demos were just created, so lastUpdateDate is recent
            Date pastDate = daysAgo(1);

            // When
            List<Integer> results = demographicDao.getDemographicIdsAddedSince(pastDate);

            // Then
            assertThat(results)
                .contains(demo1.getDemographicNo(), demo2.getDemographicNo());
        }

        @Test
        @Tag("query")
        @DisplayName("should return active demographics after date")
        void shouldReturnActiveDemographics_afterDate() {
            // Given — demos were just created with lastUpdateDate = now
            Date pastDate = daysAgo(1);

            // When
            List<Demographic> results = demographicDao.getActiveDemographicAfter(pastDate);

            // Then — demo4 is inactive (IN), should be excluded
            assertThat(results)
                .extracting(Demographic::getDemographicNo)
                .contains(demo1.getDemographicNo(), demo2.getDemographicNo(), demo3.getDemographicNo())
                .doesNotContain(demo4.getDemographicNo());
        }

        /**
         * Returns a {@link Date} representing the specified number of days before now.
         *
         * @param days int the number of days to subtract from the current date
         * @return Date the calculated past date
         */
        private Date daysAgo(int days) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -days);
            return cal.getTime();
        }
    }

    /**
     * Tests for {@code findByCriterion(DemographicCriterion)} - multi-parameter search
     * with 7-8 positional HQL parameters depending on whether HIN is provided.
     *
     * <p>This is a critical method for Hibernate 6 migration because it uses
     * 7 parameters (?1-?7) without HIN and 8 parameters (?1-?8) with HIN.</p>
     */
    @Nested
    @DisplayName("findByCriterion (7-8 params: HQL positional)")
    class FindByCriterion {

        @Test
        @Tag("query")
        @DisplayName("should find demographic by criterion without HIN (7 params)")
        void shouldFindDemographic_byCriterionWithoutHin() {
            // Given - demo1: John Smith, ON, 1980-01-15, M, AC
            // Flush to ensure @BeforeEach data is written before the criterion query executes
            hibernateTemplate.flush();

            DemographicCriterion criterion = new DemographicCriterion(
                "", "Smith", "John", "1980", "01", "15", "M", "AC");

            // When
            List<Demographic> results = demographicDaoImpl.findByCriterion(criterion);

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(Demographic::getDemographicNo)
                .contains(demo1.getDemographicNo());
        }

        @Test
        @Tag("query")
        @DisplayName("should find demographic by criterion with HIN (8 params)")
        void shouldFindDemographic_byCriterionWithHin() {
            // Given - demo1: John Smith, HIN=uniquePrefix+"0", 1980-01-15, M, AC
            // When HIN is non-empty, findByCriterion uses 8 positional params (?1-?8) instead of 7
            hibernateTemplate.flush();

            DemographicCriterion criterion = new DemographicCriterion(
                uniquePrefix + "0", "Smith", "John", "1980", "01", "15", "M", "AC");

            // When
            List<Demographic> results = demographicDaoImpl.findByCriterion(criterion);

            // Then
            assertThat(results)
                .hasSize(1)
                .extracting(Demographic::getDemographicNo)
                .containsExactly(demo1.getDemographicNo());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when criterion matches no demographic")
        void shouldReturnEmpty_whenCriterionMatchesNone() {
            // Given - no demographic with these exact details
            hibernateTemplate.flush();

            DemographicCriterion criterion = new DemographicCriterion(
                "", "Nonexistent", "Nobody", "2099", "12", "31", "F", "AC");

            // When
            List<Demographic> results = demographicDaoImpl.findByCriterion(criterion);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when HIN criterion does not match")
        void shouldReturnEmpty_whenHinCriterionDoesNotMatch() {
            // Given - valid name/DOB but wrong HIN
            hibernateTemplate.flush();

            DemographicCriterion criterion = new DemographicCriterion(
                "NONEXISTENT_HIN", "Smith", "John", "1980", "01", "15", "M", "AC");

            // When
            List<Demographic> results = demographicDaoImpl.findByCriterion(criterion);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should distinguish inactive from active demographics by status param")
        void shouldDistinguishDemographics_byStatusParam() {
            // Given - demo4 is inactive (IN)
            hibernateTemplate.flush();

            // Same demographic data but different patient status to verify the status parameter
            // correctly filters active vs inactive records
            DemographicCriterion activeCriterion = new DemographicCriterion(
                "", "Johnson", "Bob", "1980", "01", "15", "M", "AC");
            DemographicCriterion inactiveCriterion = new DemographicCriterion(
                "", "Johnson", "Bob", "1980", "01", "15", "M", "IN");

            // When
            List<Demographic> activeResults = demographicDaoImpl.findByCriterion(activeCriterion);
            List<Demographic> inactiveResults = demographicDaoImpl.findByCriterion(inactiveCriterion);

            // Then - only inactive criterion should match demo4
            assertThat(activeResults)
                .extracting(Demographic::getDemographicNo)
                .doesNotContain(demo4.getDemographicNo());
            assertThat(inactiveResults)
                .extracting(Demographic::getDemographicNo)
                .contains(demo4.getDemographicNo());
        }
    }
}
