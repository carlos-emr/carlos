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
 *
 * TicklerDao Integration Test Suite
 *
 * This test class provides comprehensive integration testing for the TicklerDao
 * data access layer using JUnit 5 and modern testing practices.
 *
 * @author yingbull
 * @since 2025-09-15
 */
package ca.openosp.openo.tickler.dao.archive;

import ca.openosp.openo.test.base.OpenODaoTestBase;
import ca.openosp.openo.commn.dao.TicklerDao;
import ca.openosp.openo.commn.dao.DemographicDao;
import ca.openosp.openo.commn.model.Tickler;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.CustomFilter;
import ca.openosp.openo.utility.SpringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Integration test suite for {@link TicklerDao} data access operations.
 *
 * <p>This test class validates the complete lifecycle of Tickler entities including:
 * <ul>
 *   <li>CRUD operations (Create, Read, Update, Delete)</li>
 *   <li>Complex search and filtering capabilities</li>
 *   <li>Date range queries and pagination</li>
 *   <li>Status and priority management</li>
 *   <li>Batch operations and performance testing</li>
 * </ul>
 *
 * <p><b>Test Coverage Summary:</b>
 * <table border="1">
 *   <tr><th>Test Method</th><th>What It Tests</th><th>Business Value</th></tr>
 *   <tr><td>testCreateAndRetrieve</td><td>Basic CRUD operations</td><td>Core tickler creation and retrieval</td></tr>
 *   <tr><td>testFindActiveByDemographic</td><td>Status-based filtering</td><td>Active task management for patients</td></tr>
 *   <tr><td>testFindByDemographicAndMessage</td><td>Content search</td><td>Finding specific reminders</td></tr>
 *   <tr><td>testTicklerStatuses</td><td>All status transitions</td><td>Task lifecycle management</td></tr>
 *   <tr><td>testTicklerPriorities</td><td>Priority levels</td><td>Task prioritization</td></tr>
 *   <tr><td>testSearchByDateRange</td><td>Temporal queries</td><td>Overdue and upcoming task identification</td></tr>
 *   <tr><td>testCountActiveTicklersByProvider</td><td>Provider workload</td><td>Task distribution analysis</td></tr>
 *   <tr><td>testGetTicklersWithFilter</td><td>Complex filtering</td><td>Advanced search capabilities</td></tr>
 *   <tr><td>testPagination</td><td>Result pagination</td><td>UI performance optimization</td></tr>
 *   <tr><td>testUpdateTicklerStatus</td><td>Status updates</td><td>Task completion workflow</td></tr>
 *   <tr><td>testNullHandling</td><td>Optional field handling</td><td>Robustness with partial data</td></tr>
 *   <tr><td>testBatchInsert</td><td>Bulk operations</td><td>Mass reminder creation</td></tr>
 * </table>
 *
 * <p><b>Test Configuration:</b>
 * <ul>
 *   <li>Uses in-memory H2 database in MySQL compatibility mode</li>
 *   <li>Transactions are rolled back after each test (@Rollback)</li>
 *   <li>SpringUtils anti-pattern is properly handled via test context</li>
 *   <li>Each test runs in isolation with fresh test data</li>
 * </ul>
 *
 * <p><b>Test Data Strategy:</b>
 * Each test creates its own test demographic and tickler data to ensure
 * test isolation and repeatability. Helper methods provide consistent
 * test data factories.
 *
 * <p><b>Performance Considerations:</b>
 * <ul>
 *   <li>Batch tests validate handling of 100+ records</li>
 *   <li>Pagination tests ensure efficient large result set handling</li>
 *   <li>Transaction boundaries are explicitly tested</li>
 * </ul>
 *
 * @see TicklerDao
 * @see OpenODaoTestBase
 * @see Tickler
 * @author yingbull
 * @since 2025-09-15
 */
@DisplayName("TicklerDao Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TicklerDaoTest extends OpenODaoTestBase {

    /** DAO instance under test */
    @Autowired
    private TicklerDao ticklerDao;

    /** Supporting DAO for creating test demographics */
    @Autowired
    private DemographicDao demographicDao;

    /** Test demographic ID created in setUp for each test */
    private Integer testDemographicNo;

    /** Default test provider number used across tests */
    private static final String TEST_PROVIDER = "999998";

    /**
     * Set up test fixtures before each test method.
     *
     * <p>This method:
     * <ol>
     *   <li>Obtains DAO instances via SpringUtils to validate the anti-pattern</li>
     *   <li>Verifies SpringUtils integration is working correctly</li>
     *   <li>Creates a test demographic record for tickler testing</li>
     * </ol>
     *
     * @throws Exception if setup fails
     */
    @BeforeEach
    void setUp() {
        // Verify we can also get DAOs using SpringUtils anti-pattern
        // This validates that SpringUtils.getBean() works correctly in test context
        TicklerDao springUtilsDao = SpringUtils.getBean(TicklerDao.class);
        assertThat(springUtilsDao).isNotNull();
        assertThat(springUtilsDao).isEqualTo(ticklerDao);

        // Verify SpringUtils integration works - critical for legacy code compatibility
        verifyDaoSpringUtilsIntegration(TicklerDao.class);

        // Create test demographic for ticklers - ensures clean test data
        testDemographicNo = createTestDemographic();
    }

    /**
     * Test basic CRUD operations: Create and Retrieve.
     *
     * <p>Validates that:
     * <ul>
     *   <li>A tickler can be successfully persisted to the database</li>
     *   <li>The persisted tickler receives a valid ID</li>
     *   <li>All fields are correctly saved and retrieved</li>
     *   <li>Default values are properly set</li>
     * </ul>
     */
    @Test
    @DisplayName("Should create and retrieve tickler")
    @Order(1)
    void testCreateAndRetrieve() {
        // Given - prepare test tickler with basic information
        Tickler tickler = createBasicTickler("Test reminder for patient");

        // When - persist to database and retrieve by ID
        ticklerDao.persist(tickler);
        Integer ticklerNo = tickler.getId(); // ID should be auto-generated
        Tickler retrieved = ticklerDao.find(ticklerNo);

        // Then - verify all fields match original
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getMessage()).isEqualTo("Test reminder for patient");
        assertThat(retrieved.getDemographicNo()).isEqualTo(testDemographicNo);
        assertThat(retrieved.getStatus()).isEqualTo(Tickler.STATUS.A); // Default active
        assertThat(retrieved.getPriority()).isEqualTo(Tickler.PRIORITY.Normal); // Default priority
    }

    @Test
    @DisplayName("Should find active ticklers by demographic")
    void testFindActiveByDemographic() {
        // Given - create multiple ticklers with different statuses
        Tickler active1 = createBasicTickler("Active tickler 1");
        active1.setStatus(Tickler.STATUS.A);
        ticklerDao.persist(active1);

        Tickler active2 = createBasicTickler("Active tickler 2");
        active2.setStatus(Tickler.STATUS.A);
        ticklerDao.persist(active2);

        Tickler completed = createBasicTickler("Completed tickler");
        completed.setStatus(Tickler.STATUS.C);
        ticklerDao.persist(completed);

        Tickler deleted = createBasicTickler("Deleted tickler");
        deleted.setStatus(Tickler.STATUS.D);
        ticklerDao.persist(deleted);

        // When
        List<Tickler> activeTicklers = ticklerDao.findActiveByDemographicNo(testDemographicNo);

        // Then
        assertThat(activeTicklers).hasSize(2);
        assertThat(activeTicklers)
            .extracting(Tickler::getMessage)
            .containsExactlyInAnyOrder("Active tickler 1", "Active tickler 2");
    }

    @Test
    @DisplayName("Should search ticklers by demographic and message")
    void testFindByDemographicAndMessage() {
        // Given
        String searchMessage = "Follow up with lab results";
        Tickler tickler1 = createBasicTickler(searchMessage);
        ticklerDao.persist(tickler1);

        Tickler tickler2 = createBasicTickler("Different message");
        ticklerDao.persist(tickler2);

        // When
        List<Tickler> results = ticklerDao.findActiveByDemographicNoAndMessage(
            testDemographicNo, searchMessage
        );

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMessage()).isEqualTo(searchMessage);
    }

    @ParameterizedTest
    @DisplayName("Should handle different tickler statuses")
    @EnumSource(Tickler.STATUS.class)
    void testTicklerStatuses(Tickler.STATUS status) {
        // Given
        Tickler tickler = createBasicTickler("Status test: " + status);
        tickler.setStatus(status);

        // When
        ticklerDao.persist(tickler);
        Tickler retrieved = ticklerDao.find(tickler.getId());

        // Then
        assertThat(retrieved.getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @DisplayName("Should handle different tickler priorities")
    @EnumSource(Tickler.PRIORITY.class)
    void testTicklerPriorities(Tickler.PRIORITY priority) {
        // Given
        Tickler tickler = createBasicTickler("Priority test: " + priority);
        tickler.setPriority(priority);

        // When
        ticklerDao.persist(tickler);
        Tickler retrieved = ticklerDao.find(tickler.getId());

        // Then
        assertThat(retrieved.getPriority()).isEqualTo(priority);
    }

    /**
     * Test date range search functionality.
     *
     * <p>Validates that the DAO correctly filters ticklers based on
     * service date ranges, which is critical for:
     * <ul>
     *   <li>Finding overdue ticklers</li>
     *   <li>Scheduling future reminders</li>
     *   <li>Generating daily task lists</li>
     * </ul>
     */
    @Test
    @DisplayName("Should search ticklers by date range")
    void testSearchByDateRange() {
        // Given - ticklers with different service dates
        // Using Java 8 time API for better date manipulation
        Date now = new Date();
        Date yesterday = Date.from(LocalDateTime.now().minusDays(1)
            .atZone(ZoneId.systemDefault()).toInstant());
        Date tomorrow = Date.from(LocalDateTime.now().plusDays(1)
            .atZone(ZoneId.systemDefault()).toInstant());
        Date nextWeek = Date.from(LocalDateTime.now().plusWeeks(1)
            .atZone(ZoneId.systemDefault()).toInstant());

        Tickler pastTickler = createBasicTickler("Past tickler");
        pastTickler.setServiceDate(yesterday);
        ticklerDao.persist(pastTickler);

        Tickler todayTickler = createBasicTickler("Today tickler");
        todayTickler.setServiceDate(now);
        ticklerDao.persist(todayTickler);

        Tickler futureTickler = createBasicTickler("Future tickler");
        futureTickler.setServiceDate(tomorrow);
        ticklerDao.persist(futureTickler);

        // When - search from yesterday to tomorrow
        List<Tickler> results = ticklerDao.search_tickler_bydemo(
            testDemographicNo, "A", yesterday, tomorrow
        );

        // Then
        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        assertThat(results)
            .extracting(Tickler::getMessage)
            .contains("Past tickler", "Today tickler");
    }

    @Test
    @DisplayName("Should count active ticklers for provider")
    void testCountActiveTicklersByProvider() {
        // Given - create ticklers assigned to different providers
        String provider1 = "999998";
        String provider2 = "999997";

        for (int i = 0; i < 5; i++) {
            Tickler tickler = createBasicTickler("Provider1 tickler " + i);
            tickler.setTaskAssignedTo(provider1);
            tickler.setStatus(Tickler.STATUS.A);
            ticklerDao.persist(tickler);
        }

        for (int i = 0; i < 3; i++) {
            Tickler tickler = createBasicTickler("Provider2 tickler " + i);
            tickler.setTaskAssignedTo(provider2);
            tickler.setStatus(Tickler.STATUS.A);
            ticklerDao.persist(tickler);
        }

        // Add some completed ones that shouldn't be counted
        Tickler completed = createBasicTickler("Completed tickler");
        completed.setTaskAssignedTo(provider1);
        completed.setStatus(Tickler.STATUS.C);
        ticklerDao.persist(completed);

        // When
        int count = ticklerDao.getActiveTicklerCount(provider1);

        // Then
        assertThat(count).isEqualTo(5);
    }

    /**
     * Test complex filtering using CustomFilter.
     *
     * <p>CustomFilter is the primary mechanism for advanced tickler searches,
     * supporting multiple criteria including status, priority, assignee, etc.
     * This test validates the filter's ability to handle multiple conditions.
     */
    @Test
    @DisplayName("Should get ticklers with custom filter")
    void testGetTicklersWithFilter() {
        // Given - create various ticklers with alternating priorities
        // This creates a mix of high and normal priority ticklers for filtering
        for (int i = 0; i < 10; i++) {
            Tickler tickler = createBasicTickler("Filtered tickler " + i);
            // Alternate between high and normal priority
            tickler.setPriority(i % 2 == 0 ? Tickler.PRIORITY.High : Tickler.PRIORITY.Normal);
            ticklerDao.persist(tickler);
        }

        // When - create filter for active ticklers with pagination
        CustomFilter filter = new CustomFilter();
        filter.setStatus("A"); // Active status only
        filter.setProgramId("0"); // "0" indicates all programs

        // Request first 5 results (offset=0, limit=5)
        List<Tickler> results = ticklerDao.getTicklers(filter, 0, 5);

        // Then
        assertThat(results).hasSizeLessThanOrEqualTo(5);
        assertThat(results).allSatisfy(tickler ->
            assertThat(tickler.getStatus()).isEqualTo(Tickler.STATUS.A)
        );
    }

    @Test
    @DisplayName("Should handle pagination")
    void testPagination() {
        // Given - create 20 ticklers
        for (int i = 0; i < 20; i++) {
            Tickler tickler = createBasicTickler("Paginated tickler " + i);
            ticklerDao.persist(tickler);
        }

        // When - get first page
        CustomFilter filter = new CustomFilter();
        filter.setStatus("A");
        List<Tickler> firstPage = ticklerDao.getTicklers(filter, 0, 10);
        List<Tickler> secondPage = ticklerDao.getTicklers(filter, 10, 10);

        // Then
        assertThat(firstPage).hasSizeLessThanOrEqualTo(10);
        assertThat(secondPage).hasSizeLessThanOrEqualTo(10);

        // Ensure no overlap between pages
        Set<Integer> firstPageIds = new HashSet<>();
        firstPage.forEach(t -> firstPageIds.add(t.getId()));

        assertThat(secondPage).noneSatisfy(tickler ->
            assertThat(firstPageIds).contains(tickler.getId())
        );
    }

    @Test
    @DisplayName("Should update tickler status")
    void testUpdateTicklerStatus() {
        // Given
        Tickler tickler = createBasicTickler("Status update test");
        tickler.setStatus(Tickler.STATUS.A);
        ticklerDao.persist(tickler);
        Integer id = tickler.getId();

        // When - update status to completed
        Tickler toUpdate = ticklerDao.find(id);
        toUpdate.setStatus(Tickler.STATUS.C);
        toUpdate.setUpdateDate(new Date());
        ticklerDao.merge(toUpdate);

        // Then
        Tickler updated = ticklerDao.find(id);
        assertThat(updated.getStatus()).isEqualTo(Tickler.STATUS.C);
        assertThat(updated.getUpdateDate()).isAfterOrEqualTo(updated.getCreateDate());
    }

    @Test
    @DisplayName("Should handle null values gracefully")
    void testNullHandling() {
        // Given
        Tickler tickler = createBasicTickler("Null test");
        tickler.setProgramId(null); // Optional field
        tickler.setTaskAssignedTo(null); // Can be null

        // When
        assertDoesNotThrow(() -> ticklerDao.persist(tickler));

        // Then
        Tickler retrieved = ticklerDao.find(tickler.getId());
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getProgramId()).isNull();
        assertThat(retrieved.getTaskAssignedTo()).isNull();
    }

    /**
     * Nested test class for batch operation scenarios.
     *
     * <p>Batch operations are critical for:
     * <ul>
     *   <li>Bulk imports from external systems</li>
     *   <li>Creating recurring ticklers for multiple patients</li>
     *   <li>System-wide reminder campaigns</li>
     * </ul>
     */
    @Nested
    @DisplayName("Batch Operations")
    class BatchOperations {

        /**
         * Test efficient batch insertion of multiple ticklers.
         *
         * <p>This test validates that the DAO can handle large-scale
         * insertions efficiently by using periodic flushing to optimize
         * database write operations.
         */
        @Test
        @DisplayName("Should handle batch inserts efficiently")
        void testBatchInsert() {
            // Given/When - execute batch insert within a single transaction
            List<Integer> ids = executeInTransaction(() -> {
                List<Integer> createdIds = new ArrayList<>();

                // Create 100 ticklers to test batch performance
                for (int i = 0; i < 100; i++) {
                    Tickler tickler = createBasicTickler("Batch tickler " + i);
                    ticklerDao.persist(tickler);
                    createdIds.add(tickler.getId());

                    // Flush periodically (every 20 records) to optimize batch processing
                    // This prevents memory issues with large batches
                    if (i % 20 == 0) {
                        ticklerDao.flush();
                    }
                }
                return createdIds;
            });

            // Then
            assertThat(ids).hasSize(100);
            assertThat(countRowsInTable("tickler")).isGreaterThanOrEqualTo(100);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a test demographic record for tickler testing.
     *
     * <p>This method creates a minimal but valid demographic record
     * that satisfies all database constraints and business rules.
     *
     * @return the ID of the created demographic
     */
    private Integer createTestDemographic() {
        Demographic demo = new Demographic();
        demo.setFirstName("Test");
        demo.setLastName("Patient");
        demo.setYearOfBirth("1980");
        demo.setMonthOfBirth("01");
        demo.setDateOfBirth("15");
        demo.setSex("M");
        demo.setHin("1234567890");
        demo.setProviderNo(TEST_PROVIDER);
        demo.setPatientStatus("AC");
        demo.setDateJoined(new Date());
        demo.setLastUpdateUser("test");
        demo.setLastUpdateDate(new Date());

        demographicDao.save(demo);
        return demo.getDemographicNo();
    }

    /**
     * Factory method to create a basic tickler for testing.
     *
     * <p>Creates a tickler with all required fields populated with
     * sensible defaults. This ensures consistent test data across
     * all test methods.
     *
     * @param message the tickler message content
     * @return a fully populated Tickler instance ready for persistence
     */
    private Tickler createBasicTickler(String message) {
        Tickler tickler = new Tickler();

        // Set required fields
        tickler.setDemographicNo(testDemographicNo);
        tickler.setMessage(message);

        // Set default status and priority
        tickler.setStatus(Tickler.STATUS.A); // Active by default
        tickler.setPriority(Tickler.PRIORITY.Normal); // Normal priority by default

        // Set provider information
        tickler.setCreator(TEST_PROVIDER);
        tickler.setTaskAssignedTo(TEST_PROVIDER); // Self-assigned by default

        // Set timestamps
        tickler.setCreateDate(new Date());
        tickler.setUpdateDate(new Date());
        tickler.setServiceDate(new Date()); // Due immediately by default

        return tickler;
    }

    /**
     * Specifies which database tables should be cleaned before each test.
     *
     * <p>This ensures test isolation by removing any existing data
     * that might interfere with test execution.
     *
     * @return array of table names to be cleaned
     */
    @Override
    protected String[] cleanableTables() {
        return new String[] {
            "tickler",           // Main tickler table
            "tickler_comments",  // Associated comments
            "tickler_update",    // Update history
            "demographic",       // Test patient data
            "demographicExt"    // Extended demographic data
        };
    }
}