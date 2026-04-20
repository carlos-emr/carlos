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
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
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

    @Autowired
    private DemographicExtDao demographicExtDao;

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
     * Persists a demographic extension row for the provided patient and flushes it for native-query visibility.
     *
     * @param demographicNo Integer the demographic number
     * @param key String the demographic extension key
     * @param value String the demographic extension value
     */
    private void createDemographicExt(Integer demographicNo, String key, String value) {
        demographicExtDao.saveDemographicExt(demographicNo, key, value);
        demographicExtDao.flush();
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
     * Regression tests for {@link DemographicDaoImpl#getOrderField(String, boolean)} native query allowlisting.
     *
     * <p>These tests pin the native-query branch to the same approved sort fields as the HQL branch so
     * future callers cannot inject arbitrary SQL into {@code ORDER BY} clauses.</p>
     */
    @Nested
    @DisplayName("Native order-by allowlist")
    @Tag("regression")
    class NativeOrderByAllowlist {

        @Test
        @Tag("query")
        @DisplayName("should return safe native default when order-by is unknown")
        void shouldReturnSafeNativeDefault_whenOrderByIsUnknown() {
            assertThat(demographicDaoImpl.getOrderField("last_name desc", true))
                .isEqualTo("de.last_name, de.first_name");
        }

        @Test
        @Tag("query")
        @DisplayName("should return composite native date-of-birth ordering")
        void shouldReturnCompositeNativeDateOfBirthOrdering_whenOrderByIsDob() {
            assertThat(demographicDaoImpl.getOrderField("dob", true))
                .isEqualTo("de.year_of_birth, de.month_of_birth, de.date_of_birth");
        }

        @Test
        @Tag("query")
        @DisplayName("should use safe default when native ext search receives non-whitelisted order-by")
        void shouldUseSafeDefault_whenNativeExtSearchReceivesNonWhitelistedOrderBy() {
            createDemographicExt(demo1.getDemographicNo(), DemographicExt.DemographicProperty.demo_cell.name(), "555-1111");
            createDemographicExt(demo2.getDemographicNo(), DemographicExt.DemographicProperty.demo_cell.name(), "555-2222");

            List<Demographic> results = demographicDao.searchDemographicByExtKeyAndValueLikeAndStatus(
                DemographicExt.DemographicProperty.demo_cell, "555", null, 10, 0, "last_name desc, de.hin",
                null, true);

            assertThat(results)
                .extracting(Demographic::getDemographicNo)
                .containsExactly(demo2.getDemographicNo(), demo1.getDemographicNo());
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

    /** Parameterised native INSERT for the demographic_merged fixture. */
    private static final String INSERT_DEMOGRAPHIC_MERGED = """
            INSERT INTO demographic_merged (demographic_no, merged_to, deleted)
            VALUES (:child, :parent, :deleted)""";

    /** Parameterised native INSERT for the program fixture. */
    private static final String INSERT_PROGRAM = """
            INSERT INTO program (id, name, type, facilityId)
            VALUES (:id, :name, :type, :fac)""";

    /**
     * Parameterised native INSERT for the admission fixture. Populates the
     * NOT NULL columns required by the Admission @Entity (admission_from_transfer,
     * discharge_from_transfer, program_id, provider_no) plus client_id and
     * admission_date that findByReportCriteria's SQL filters on.
     */
    private static final String INSERT_ADMISSION = """
            INSERT INTO admission
                (client_id, program_id, admission_date, provider_no,
                 admission_from_transfer, discharge_from_transfer)
            VALUES (:cid, :pid, :ad, :prv, FALSE, FALSE)""";

    /**
     * Regression tests for native-query result coercion on numeric columns.
     *
     * <p>The #1559 migration switched several native queries from the Hibernate
     * Session / {@code rs.getInt(...)} path (which performs implicit type conversion)
     * to {@code entityManager().createNativeQuery(...).getResultList()}, which returns
     * driver-dependent numeric boxed types (typically {@code Integer} on H2 but
     * {@code Long} or {@code BigInteger} on some MariaDB driver configurations).
     * Returning that raw list as a {@code List<Integer>} would cause
     * {@link ClassCastException} at caller unboxing time.</p>
     *
     * <p>{@code DemographicDaoImpl.getMergedDemographics} and {@code findByReportCriteria}
     * now iterate and coerce via {@code Number.intValue()} or
     * {@code Integer.parseInt((String) ...)} respectively. These tests pin that
     * coercion so future regressions surface even when the backing driver returns
     * a happy-path type.</p>
     */
    @Nested
    @DisplayName("Native-query numeric coercion")
    @Tag("regression")
    class NativeQueryNumericCoercion {

        @Test
        @Tag("query")
        @Tag("merge")
        @DisplayName("should return List<Integer> from getMergedDemographics with no CCE")
        void shouldReturnIntegerList_fromGetMergedDemographics() {
            // Given - mark demo2 and demo3 as merged into demo1, and demo4 as a
            // deleted merge. The INSERTs use named parameters (constant SQL).
            final int parentNo = demo1.getDemographicNo();
            hibernateTemplate.execute(session -> {
                session.createNativeQuery(INSERT_DEMOGRAPHIC_MERGED)
                    .setParameter("child", demo2.getDemographicNo())
                    .setParameter("parent", parentNo)
                    .setParameter("deleted", 0)
                    .executeUpdate();
                session.createNativeQuery(INSERT_DEMOGRAPHIC_MERGED)
                    .setParameter("child", demo3.getDemographicNo())
                    .setParameter("parent", parentNo)
                    .setParameter("deleted", 0)
                    .executeUpdate();
                session.createNativeQuery(INSERT_DEMOGRAPHIC_MERGED)
                    .setParameter("child", demo4.getDemographicNo())
                    .setParameter("parent", parentNo)
                    .setParameter("deleted", 1)
                    .executeUpdate();
                return null;
            });
            hibernateTemplate.flush();

            // When
            List<Integer> mergedIds = demographicDao.getMergedDemographics(parentNo);

            // Then - coercion must produce Integers (so the generic bound holds at
            // call sites) and the deleted=1 row must be filtered out.
            assertThat(mergedIds)
                .isNotEmpty()
                .allSatisfy(id -> assertThat(id).isInstanceOf(Integer.class))
                .containsExactlyInAnyOrder(demo2.getDemographicNo(), demo3.getDemographicNo())
                .doesNotContain(demo4.getDemographicNo());
        }

        @Test
        @Tag("query")
        @DisplayName("should parse VARCHAR DOB columns in findByReportCriteria without CCE")
        void shouldParseVarcharDobColumns_fromFindByReportCriteria() {
            // Given - findByReportCriteria joins demographic × admission × program.
            // Seed one admission / program row so the query produces output for demo1,
            // which has year_of_birth/month_of_birth/date_of_birth populated ("1980"/"01"/"15").
            final int demoNo = demo1.getDemographicNo();
            hibernateTemplate.execute(session -> {
                session.createNativeQuery(INSERT_PROGRAM)
                    .setParameter("id", 99001)
                    .setParameter("name", "TestProg")
                    .setParameter("type", "Service")
                    .setParameter("fac", 1)
                    .executeUpdate();
                session.createNativeQuery(INSERT_ADMISSION)
                    .setParameter("cid", demoNo)
                    .setParameter("pid", 99001)
                    .setParameter("ad", new java.sql.Timestamp(System.currentTimeMillis()))
                    .setParameter("prv", "999998")
                    .executeUpdate();
                return null;
            });
            hibernateTemplate.flush();

            io.github.carlos_emr.carlos.PMmodule.web.formbean.ClientListsReportFormBean form =
                new io.github.carlos_emr.carlos.PMmodule.web.formbean.ClientListsReportFormBean();
            form.setProgramId("99001");

            // When - pre-fix this would CCE because row[1..3] are Strings (VARCHAR DOB
            // columns) being cast to Number by the Task 6b migration.
            java.util.Map<String, DemographicDaoImpl.ClientListsReportResults> results =
                demographicDaoImpl.findByReportCriteria(form);

            // Then - demo1's dateOfBirth was parsed correctly from the three
            // VARCHAR columns (year=1980, month=01 → Calendar.MONTH 0, day=15).
            assertThat(results).isNotEmpty();
            DemographicDaoImpl.ClientListsReportResults row = results.values().stream()
                .filter(r -> r.demographicId == demoNo)
                .findFirst()
                .orElseThrow(() -> new AssertionError("demo1 not returned by findByReportCriteria"));
            assertThat(row.dateOfBirth.get(Calendar.YEAR)).isEqualTo(1980);
            assertThat(row.dateOfBirth.get(Calendar.MONTH)).isZero();       // January
            assertThat(row.dateOfBirth.get(Calendar.DAY_OF_MONTH)).isEqualTo(15);
        }
    }
}
