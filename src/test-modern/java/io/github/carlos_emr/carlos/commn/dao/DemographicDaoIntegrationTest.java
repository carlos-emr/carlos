/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * This software was written for CARLOS EMR Project
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
 * Integration tests for DemographicDao multi-parameter query methods.
 *
 * <p>These tests validate that HQL queries with multiple positional parameters
 * bind parameters correctly. Tests are designed to catch parameter index errors
 * during Hibernate migration.</p>
 *
 * @since 2026-02-03
 * @see DemographicDao
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
        uniquePrefix = String.valueOf(System.nanoTime()).substring(0, 10);

        demo1 = createDemographic("John", "Smith", "ON", uniquePrefix + "0", "AC");
        demo2 = createDemographic("John", "Doe", "ON", uniquePrefix + "1", "AC");
        demo3 = createDemographic("Jane", "Smith", "BC", uniquePrefix + "2", "AC");
        demo4 = createDemographic("Bob", "Johnson", "ON", uniquePrefix + "3", "IN");  // Inactive
    }

    private Demographic createDemographic(String firstName, String lastName,
                                          String hcType, String hin, String patientStatus) {
        return createDemographicWithRoster(firstName, lastName, hcType, hin, patientStatus, null);
    }

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
        return demo;
    }

    /** Tests for CRUD operations on Demographic entities. */
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

    /** Tests for getClientsByHealthCard (2 params). */
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

    /** Tests for getDemographicWithLastFirstDOB (2+ params). */
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

    /** Tests for getDemographicByRosterStatus (2 params). */
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

    /** Tests for query operations covering aggregation, pagination, and search. */
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

    /** Tests for additional single-parameter ?0 query methods. */
    @Nested
    @DisplayName("Additional ?0 parameter queries")
    class AdditionalParameterQueries {

        @Test
        @Tag("query")
        @DisplayName("should find demographic by chart number")
        void shouldFindDemographic_byChartNo() {
            // Given
            demo1.setChartNo("CHT001");
            demographicDao.save(demo1);
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
            // Given — all setUp demos have yearOfBirth "1980"
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
     * 7 parameters (?0-?6) without HIN and 8 parameters (?0-?7) with HIN.</p>
     */
    @Nested
    @DisplayName("findByCriterion (7-8 params: HQL positional)")
    class FindByCriterion {

        @Test
        @Tag("query")
        @DisplayName("should find demographic by criterion without HIN (7 params)")
        void shouldFindDemographic_byCriterionWithoutHin() {
            // Given - demo1: John Smith, ON, 1980-01-15, M, AC
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
