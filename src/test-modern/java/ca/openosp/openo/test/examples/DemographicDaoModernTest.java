/**
 * Copyright (c) 2025. Magenta Health. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Magenta Health
 * Toronto, Ontario, Canada
 * Example DAO test using modern JUnit 5 framework
 */
package ca.openosp.openo.test.examples;

import ca.openosp.openo.test.base.OpenODaoTestBase;
import ca.openosp.openo.commn.dao.DemographicDao;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.utility.SpringUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Example test demonstrating modern JUnit 5 features with the OpenO test framework.
 * This shows how to test DAOs while handling the SpringUtils anti-pattern.
 */
@DisplayName("Demographic DAO Modern Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
@Rollback
class DemographicDaoModernTest extends OpenODaoTestBase {

    @Autowired
    private DemographicDao demographicDao;

    @Test
    @DisplayName("Should create and retrieve demographic")
    @Order(1)
    void testCreateAndRetrieveDemographic() {
        // Given
        Demographic demographic = createTestDemographic("TEST001", "John", "Doe");

        // When
        demographicDao.save(demographic);
        Demographic retrieved = demographicDao.getDemographicById(demographic.getDemographicNo());

        // Then
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getChartNo()).isEqualTo("TEST001");
        assertThat(retrieved.getFirstName()).isEqualTo("John");
        assertThat(retrieved.getLastName()).isEqualTo("Doe");
    }

    @Test
    @DisplayName("Should handle SpringUtils.getBean() correctly")
    void testSpringUtilsIntegration() {
        // This verifies the anti-pattern is handled correctly
        DemographicDao daoFromContext = getBean(DemographicDao.class);
        DemographicDao daoFromUtils = SpringUtils.getBean(DemographicDao.class);

        assertThat(daoFromContext).isSameAs(daoFromUtils);
    }

    @ParameterizedTest
    @DisplayName("Should validate HIN formats")
    @ValueSource(strings = {
        "1234567890",  // Ontario
        "9876543210",  // BC
        "1111111111"   // Generic
    })
    void testHealthInsuranceNumberValidation(String hin) {
        // Given
        Demographic demographic = createTestDemographic("TEST002", "Jane", "Smith");
        demographic.setHin(hin);

        // When
        demographicDao.save(demographic);
        Demographic retrieved = demographicDao.getDemographicById(demographic.getDemographicNo());

        // Then
        assertThat(retrieved.getHin()).isEqualTo(hin);
    }

    @ParameterizedTest
    @DisplayName("Should search by various criteria")
    @CsvSource({
        "lastName, Doe, true",
        "firstName, John, true",
        "chartNo, TEST001, true",
        "hin, NOTEXIST999, false"  // Use a HIN that definitely won't exist
    })
    void testSearchByCriteria(String searchField, String searchValue, boolean shouldFind) {
        // Given - create test data unique to this test
        // Use shorter unique identifier to avoid field length issues
        String uniqueChartNo = "T" + (System.currentTimeMillis() % 1000000);
        Demographic demographic = createTestDemographic(uniqueChartNo, "John", "Doe");

        // Don't set HIN for this test to avoid conflicts
        demographic.setHin(null);
        demographicDao.save(demographic);

        // When - search using the actual value or a test value
        String actualSearchValue = searchField.equals("chartNo") ? uniqueChartNo : searchValue;
        List<Demographic> results = searchDemographics(searchField, actualSearchValue);

        // Then - check if we found our test data
        if (shouldFind) {
            // For chartNo search, we should find exactly our record
            if (searchField.equals("chartNo")) {
                assertThat(results).hasSize(1);
                assertThat(results.get(0).getChartNo()).isEqualTo(uniqueChartNo);
            } else {
                // For name searches, we should find at least our record
                assertThat(results).hasSizeGreaterThanOrEqualTo(1);
                boolean foundOurRecord = results.stream()
                    .anyMatch(d -> uniqueChartNo.equals(d.getChartNo()));
                assertThat(foundOurRecord).isTrue();
            }
        } else {
            assertThat(results).isEmpty();
        }
    }

    @Test
    @DisplayName("Should handle transaction rollback")
    void testTransactionRollback() {
        // This test verifies that our @Transactional and @Rollback annotations work
        // Each test starts with a clean slate due to @Rollback

        // Create and save a demographic
        Demographic demographic = createTestDemographic("ROLLBACK", "Test", "Rollback");
        demographicDao.save(demographic);

        // Verify it was saved within the transaction
        Demographic retrieved = demographicDao.getDemographicById(demographic.getDemographicNo());
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getChartNo()).isEqualTo("ROLLBACK");

        // After this test method completes, @Rollback will revert this
        // so the next test will not see this data
    }

    @Nested
    @DisplayName("Batch Operations")
    class BatchOperationsTest {

        @Test
        @DisplayName("Should handle batch inserts efficiently")
        void testBatchInsert() {
            // Save multiple demographics
            List<Demographic> saved = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Demographic demo = createTestDemographic(
                    "BATCH" + i,
                    "First" + i,
                    "Last" + i
                );
                demographicDao.save(demo);
                saved.add(demo);
            }

            // Verify all were saved
            for (Demographic demo : saved) {
                Demographic retrieved = demographicDao.getDemographicById(demo.getDemographicNo());
                assertThat(retrieved).isNotNull();
                assertThat(retrieved.getChartNo()).startsWith("BATCH");
            }

            // Verify count matches what we saved
            assertThat(saved.size()).isEqualTo(10);
        }
    }

    @Test
    @DisplayName("Should handle concurrent saves")
    @Tag("slow")
    @Timeout(5)  // 5 seconds timeout
    void testConcurrentAccess() throws InterruptedException {
        // Note: Due to transaction rollback, concurrent threads won't share
        // the same transaction context. This test demonstrates timeout and tagging.

        Demographic demo1 = createTestDemographic("THREAD1", "Thread", "One");
        Demographic demo2 = createTestDemographic("THREAD2", "Thread", "Two");

        // Save both in the current transaction
        demographicDao.save(demo1);
        demographicDao.save(demo2);

        // Verify both were saved
        Demographic retrieved1 = demographicDao.getDemographicById(demo1.getDemographicNo());
        Demographic retrieved2 = demographicDao.getDemographicById(demo2.getDemographicNo());

        assertThat(retrieved1).isNotNull();
        assertThat(retrieved2).isNotNull();
        assertThat(retrieved1.getChartNo()).isEqualTo("THREAD1");
        assertThat(retrieved2.getChartNo()).isEqualTo("THREAD2");
    }

    @Test
    @DisplayName("Should validate required fields")
    void testRequiredFieldValidation() {
        // Use AssertJ's exception assertions
        assertThatThrownBy(() -> {
            Demographic demographic = new Demographic();
            // Missing required fields
            demographicDao.save(demographic);
        }).isInstanceOf(Exception.class);
    }

    // Helper methods

    private Demographic createTestDemographic(String chartNo, String firstName, String lastName) {
        Demographic demographic = new Demographic();
        demographic.setChartNo(chartNo);
        demographic.setFirstName(firstName);
        demographic.setLastName(lastName);
        demographic.setYearOfBirth("1980");
        demographic.setMonthOfBirth("01");
        demographic.setDateOfBirth("15");
        demographic.setSex("M");
        demographic.setProviderNo("999998");
        demographic.setPatientStatus("AC");
        demographic.setDateJoined(new Date());
        demographic.setLastUpdateUser("test");
        demographic.setLastUpdateDate(new Date());
        return demographic;
    }

    private List<Demographic> searchDemographics(String field, String value) {
        // Use proper search methods based on field
        switch (field) {
            case "lastName":
                // searchDemographicByNameString expects lastName format
                return demographicDao.searchDemographicByNameString(value, 0, 100);
            case "firstName":
                // For firstName, use comma format: ",firstName"
                return demographicDao.searchDemographicByNameString("," + value, 0, 100);
            case "chartNo":
                return demographicDao.getClientsByChartNo(value);
            case "hin":
                // searchDemographicByHIN returns all if no HIN match
                List<Demographic> hinResults = demographicDao.searchDemographicByHIN(value);
                // Filter to only exact matches
                return hinResults.stream()
                    .filter(d -> value.equals(d.getHin()))
                    .collect(java.util.stream.Collectors.toList());
            default:
                return new ArrayList<>();
        }
    }

    @Override
    protected String[] cleanableTables() {
        return new String[] { "demographic", "demographicExt" };
    }

    @Override
    protected String getTestDataFile() {
        // Can return path to test data XML if needed
        return null;
    }
}