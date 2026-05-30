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

    // Store created demographics for test reference
    private Demographic demo1, demo2, demo3, demo4;
    // Unique prefix for this test run to avoid data pollution
    private String uniquePrefix;

    @BeforeEach
    void setUp() {
        // Generate unique prefix to isolate test data
        uniquePrefix = String.valueOf(System.nanoTime()).substring(0, 10);

        // Create test demographics using DAO's save method to ensure same session
        // Hibernate should auto-flush before queries in the same transaction
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
        // Don't set demographicNo - let it be auto-generated
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

    @Nested
    @DisplayName("getClientsByHealthCard (2 params: hin, hcType)")
    class GetClientsByHealthCard {

        @Test
        @Tag("query")
        @DisplayName("should find demographic when both HIN and HC type match")
        void shouldFind_whenBothHinAndTypeMatch() {
            // When - Use the unique HIN from demo1
            List<Demographic> results = demographicDao.getClientsByHealthCard(uniquePrefix + "0", "ON");

            // Then - Only demo1 should match
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
            // When - HIN exists but with different type (demo1 is ON, search BC)
            List<Demographic> results = demographicDao.getClientsByHealthCard(uniquePrefix + "0", "BC");

            // Then
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("getDemographicWithLastFirstDOB (2+ params)")
    class GetDemographicWithLastFirstDOB {

        @Test
        @Tag("query")
        @DisplayName("should find demographics matching last name and first name")
        void shouldFind_whenLastAndFirstNameMatch() {
            // When - Search with partial matching
            List<Demographic> results = demographicDao.getDemographicWithLastFirstDOB(
                "Smith", "John", null, null, null);

            // Then - Demo1 should match
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

    @Nested
    @DisplayName("getDemographicByRosterStatus (2 params)")
    class GetDemographicByRosterStatus {

        @Test
        @Tag("query")
        @DisplayName("should filter by roster status and patient status")
        void shouldFilterByRosterAndPatientStatus() {
            // Given - Create demographics with roster status using unique HINs
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

    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get demographic by ID")
        void shouldGetDemographicById() {
            // When
            Demographic found = demographicDao.getDemographicById(demo1.getDemographicNo());

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getFirstName()).isEqualTo("John");
            assertThat(found.getLastName()).isEqualTo("Smith");
        }

        @Test
        @Tag("read")
        @DisplayName("should get demographics by provider")
        void shouldGetDemographicsByProvider() {
            // When
            List<Demographic> results = demographicDao.getDemographicByProvider("999998", true);

            // Then - Should return only active demographics for this provider
            assertThat(results)
                .isNotEmpty()
                .allMatch(d -> d.getPatientStatus().equals("AC"));
        }

        @Test
        @Tag("read")
        @DisplayName("should search by health card number")
        void shouldSearchByHealthCard() {
            // When - Use the unique HIN from demo1
            List<Demographic> results = demographicDao.searchByHealthCard(uniquePrefix + "0");

            // Then
            assertThat(results)
                .hasSize(1)
                .extracting(Demographic::getHin)
                .containsExactly(uniquePrefix + "0");
        }

        @Test
        @Tag("read")
        @DisplayName("should get active demographic IDs")
        void shouldGetActiveDemographicIds() {
            // When
            List<Integer> ids = demographicDao.getActiveDemographicIds();

            // Then - Should not include inactive (demo4)
            assertThat(ids)
                .contains(demo1.getDemographicNo(), demo2.getDemographicNo(), demo3.getDemographicNo())
                .doesNotContain(demo4.getDemographicNo());
        }
    }
}
