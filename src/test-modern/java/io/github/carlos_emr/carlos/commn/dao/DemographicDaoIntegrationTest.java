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

import io.github.carlos_emr.carlos.test.base.OpenOTestBase;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

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
public class DemographicDaoIntegrationTest extends OpenOTestBase {

    @Autowired
    private DemographicDao demographicDao;

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
        void shouldCountActiveDemographics() {
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
        @DisplayName("should filter by active status")
        void shouldFilterByActiveStatus() {
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
        void shouldGetDemographicsByProvider() {
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
        @DisplayName("should search by HIN")
        void shouldSearchByHin() {
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
        @DisplayName("should search by name pattern")
        void shouldSearchByNamePattern() {
            // When
            List<Demographic> results = demographicDao.getDemographicWithLastFirstDOB(
                "Smith", null, null, null, null);

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(Demographic::getLastName)
                .allMatch(ln -> ln.trim().equals("Smith"));
        }

        @Test
        @Tag("read")
        @DisplayName("should project demographic IDs")
        void shouldProjectDemographicIds() {
            // When
            List<Integer> ids = demographicDao.getDemographicIds();

            // Then
            assertThat(ids)
                .isNotEmpty()
                .contains(demo1.getDemographicNo(), demo2.getDemographicNo());
        }
    }
}
