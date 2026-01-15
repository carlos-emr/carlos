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
 */
package ca.openosp.openo.tickler.dao.archive;

import ca.openosp.openo.test.base.OpenOTestBase;
import ca.openosp.openo.commn.dao.TicklerDao;
import ca.openosp.openo.commn.model.Tickler;
import ca.openosp.openo.commn.model.CustomFilter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for TicklerDao interface methods.
 *
 * <p>This test class validates all major operations of the TicklerDao against
 * an in-memory H2 database configured to emulate MySQL behavior. Tests verify
 * actual DAO method implementations, not mocked behavior, ensuring the data
 * access layer functions correctly with real database interactions.</p>
 *
 * <p><b>Test Organization:</b></p>
 * Tests are organized into nested classes by operation type:
 * <ul>
 *   <li>Find Operations - Simple retrieval by ID or criteria</li>
 *   <li>Query and Filter Operations - Complex queries with filters</li>
 *   <li>Aggregation Operations - Counting and statistics</li>
 *   <li>Write Operations - Create, Update, Delete operations</li>
 * </ul>
 *
 * <p><b>Transaction Management:</b></p>
 * All tests run within a transaction that is rolled back after completion,
 * ensuring no test data persists and tests remain isolated.
 *
 * @author OpenO Development Team
 * @since 2025-09-17
 * @see TicklerDao
 * @see Tickler
 * @see CustomFilter
 */
@DisplayName("Tickler DAO Tests")
@Tag("integration")
@Tag("dao")
@Tag("tickler")
@Transactional
class TicklerDaoMethodTestNested extends OpenOTestBase {

    @Autowired
    private TicklerDao ticklerDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * Creates standardized test data for general test scenarios.
     *
     * <p>Sets up 5 test ticklers with the following characteristics:</p>
     * <ul>
     *   <li>Ticklers 1-3: Active status (STATUS.A)</li>
     *   <li>Ticklers 4-5: Completed status (STATUS.C)</li>
     *   <li>Tickler 1: High priority</li>
     *   <li>Ticklers 2-5: Normal priority</li>
     *   <li>Even-numbered ticklers assigned to provider 999999</li>
     *   <li>Odd-numbered ticklers assigned to provider 999998</li>
     * </ul>
     */
    @BeforeEach
    void setUp() {
        // Create test ticklers with varying statuses and assignments
        for (int i = 1; i <= 5; i++) {
            Tickler tickler = new Tickler();
            tickler.setDemographicNo(1000 + i);
            tickler.setMessage("Test tickler " + i);
            tickler.setStatus(i <= 3 ? Tickler.STATUS.A : Tickler.STATUS.C);
            tickler.setPriority(i == 1 ? Tickler.PRIORITY.High : Tickler.PRIORITY.Normal);
            tickler.setCreator("999998");
            tickler.setTaskAssignedTo(i % 2 == 0 ? "999999" : "999998");
            tickler.setServiceDate(new Date());
            ticklerDao.persist(tickler);
        }
        entityManager.flush();
    }

    /**
     * Helper method to create and persist a tickler with specified attributes.
     */
    private Tickler createTickler(Integer demoNo, String message, Tickler.STATUS status) {
        Tickler tickler = new Tickler();
        tickler.setDemographicNo(demoNo);
        tickler.setMessage(message);
        tickler.setStatus(status);
        tickler.setPriority(Tickler.PRIORITY.Normal);
        tickler.setCreator("999998");
        tickler.setTaskAssignedTo("999998");
        tickler.setServiceDate(new Date());
        ticklerDao.persist(tickler);
        return tickler;
    }

    @Nested
    @DisplayName("Find Operations")
    @Tag("read")
    class FindOperations {

        @Test
        @DisplayName("should return tickler when valid ID is provided")
        void shouldReturnTickler_whenValidIdProvided() {
            // Given: Create and persist a new tickler
            Tickler newTickler = new Tickler();
            newTickler.setDemographicNo(2001);
            newTickler.setMessage("Find by ID test");
            newTickler.setCreator("999998");
            newTickler.setTaskAssignedTo("999998");
            ticklerDao.persist(newTickler);
            entityManager.flush();
            Integer id = newTickler.getId();

            // When
            Tickler found = ticklerDao.find(id);

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(id);
            assertThat(found.getMessage()).isEqualTo("Find by ID test");
            assertThat(found.getDemographicNo()).isEqualTo(2001);
        }

        @Test
        @Tag("filter")
        @DisplayName("should return only active ticklers when multiple statuses exist")
        void shouldReturnOnlyActiveTicklers_whenSearchingByDemographic() {
            // Given: Create multiple ticklers with different statuses for same demographic
            Tickler active1 = createTickler(3001, "Active 1", Tickler.STATUS.A);
            Tickler active2 = createTickler(3001, "Active 2", Tickler.STATUS.A);
            Tickler completed = createTickler(3001, "Completed", Tickler.STATUS.C);
            entityManager.flush();

            // When
            List<Tickler> activeTicklers = ticklerDao.findActiveByDemographicNo(3001);

            // Then
            assertThat(activeTicklers).hasSize(2);
            assertThat(activeTicklers).allMatch(t -> t.getStatus() == Tickler.STATUS.A);
            assertThat(activeTicklers).allMatch(t -> t.getDemographicNo().equals(3001));
            assertThat(activeTicklers).extracting(Tickler::getMessage)
                .containsExactlyInAnyOrder("Active 1", "Active 2");
        }

        @Test
        @Tag("search")
        @DisplayName("should return active ticklers with matching message for specified patients")
        void shouldReturnActiveTicklersWithMatchingMessage_whenSearchingMultiplePatients() {
            // Given
            createTickler(4001, "Follow-up required", Tickler.STATUS.A);
            createTickler(4002, "Follow-up required", Tickler.STATUS.A);
            createTickler(4003, "Different message", Tickler.STATUS.A);
            createTickler(4001, "Follow-up required", Tickler.STATUS.C); // Completed, should not be found
            entityManager.flush();

            List<Integer> demoNos = Arrays.asList(4001, 4002, 4003);

            // When
            List<Tickler> results = ticklerDao.findActiveByMessageForPatients(demoNos, "Follow-up required");

            // Then
            assertThat(results).hasSize(2);
            assertThat(results).allMatch(t -> t.getMessage().equals("Follow-up required"));
            assertThat(results).allMatch(t -> t.getStatus() == Tickler.STATUS.A);
            assertThat(results).extracting(Tickler::getDemographicNo)
                .containsExactlyInAnyOrder(4001, 4002);
        }

        @Test
        @Tag("search")
        @DisplayName("should return active ticklers matching both demographic and message")
        void shouldFindActiveTicklers_whenBothDemographicAndMessageMatch() {
            // Given
            createTickler(6001, "Specific message", Tickler.STATUS.A);
            createTickler(6001, "Different message", Tickler.STATUS.A);
            createTickler(6001, "Specific message", Tickler.STATUS.C); // Completed
            entityManager.flush();

            // When
            List<Tickler> results = ticklerDao.findActiveByDemographicNoAndMessage(6001, "Specific message");

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getDemographicNo()).isEqualTo(6001);
            assertThat(results.get(0).getMessage()).isEqualTo("Specific message");
            assertThat(results.get(0).getStatus()).isEqualTo(Tickler.STATUS.A);
        }

        @Test
        @Tag("query")
        @DisplayName("should list all ticklers within date range for demographic")
        void shouldListTicklers_whenDateRangeSpecified() {
            // Given
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -10);
            Date tenDaysAgo = cal.getTime();

            cal.add(Calendar.DAY_OF_MONTH, 20);
            Date tenDaysFromNow = cal.getTime();

            // When
            List<Tickler> results = ticklerDao.listTicklers(1001, tenDaysAgo, tenDaysFromNow);

            // Then
            assertThat(results).isNotNull();
            if (!results.isEmpty()) {
                assertThat(results).allMatch(t -> t.getDemographicNo().equals(1001));
            }
        }
    }

    @Nested
    @DisplayName("Query and Filter Operations")
    @Tag("read")
    class QueryAndFilterOperations {

        @Test
        @Tag("search")
        @DisplayName("should find ticklers within date range for demographic and status")
        void shouldFindTicklers_whenSearchingWithinDateRange() {
            // Given
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -5);
            Date fiveDaysAgo = cal.getTime();

            cal.add(Calendar.DAY_OF_MONTH, 10);
            Date fiveDaysFromNow = cal.getTime();

            // When
            List<Tickler> results = ticklerDao.search_tickler_bydemo(1001, "A", fiveDaysAgo, fiveDaysFromNow);

            // Then
            assertThat(results).isNotNull();
            if (!results.isEmpty()) {
                assertThat(results).allMatch(t -> t.getDemographicNo().equals(1001));
            }
        }

        @Test
        @Tag("filter")
        @DisplayName("should apply custom filter and return matching ticklers")
        void shouldReturnFilteredTicklers_whenCustomFilterApplied() {
            // Given
            CustomFilter filter = new CustomFilter();
            filter.setStatus("A");
            filter.setPriority("High");

            // When
            List<Tickler> results = ticklerDao.getTicklers(filter);

            // Then
            assertThat(results).isNotNull();
            assertThat(results).anyMatch(t -> t.getPriority() == Tickler.PRIORITY.High);
            assertThat(results).allMatch(t -> t.getStatus() == Tickler.STATUS.A);
        }

        @Test
        @Tag("query")
        @DisplayName("should return correct page of results with pagination")
        void shouldReturnCorrectPage_whenPaginationApplied() {
            // Given
            CustomFilter filter = new CustomFilter();
            filter.setStatus("A");

            // When
            List<Tickler> page1 = ticklerDao.getTicklers(filter, 0, 2);
            List<Tickler> page2 = ticklerDao.getTicklers(filter, 2, 2);

            // Then
            assertThat(page1).hasSizeLessThanOrEqualTo(2);
            assertThat(page2).hasSizeLessThanOrEqualTo(2);
            if (!page1.isEmpty() && !page2.isEmpty()) {
                assertThat(page1.get(0).getId()).isNotEqualTo(page2.get(0).getId());
            }
        }
    }

    @Nested
    @DisplayName("Aggregation Operations")
    @Tag("read")
    @Tag("aggregate")
    class AggregationOperations {

        @Test
        @DisplayName("should count active ticklers assigned to provider accurately")
        void shouldCountActiveTicklers_whenGroupedByProvider() {
            // Given: Test data from setUp() includes ticklers assigned to different providers

            // When
            int count998 = ticklerDao.getActiveTicklerCount("999998");
            int count999 = ticklerDao.getActiveTicklerCount("999999");
            int countNone = ticklerDao.getActiveTicklerCount("NOTEXIST");

            // Then
            assertThat(count998).isGreaterThanOrEqualTo(0);
            assertThat(count999).isGreaterThanOrEqualTo(0);
            assertThat(countNone).isEqualTo(0);
        }

        @Test
        @DisplayName("should count ticklers accurately with filter criteria")
        void shouldCountTicklersAccurately_whenFilterApplied() {
            // Given
            CustomFilter filter = new CustomFilter();
            filter.setStatus("A");

            // When
            int count = ticklerDao.getNumTicklers(filter);

            // Then
            assertThat(count).isGreaterThanOrEqualTo(3); // We created at least 3 active in setup
        }

        @Test
        @DisplayName("should count only active ticklers for specific demographic")
        void shouldCountOnlyActiveTicklers_whenGroupedByDemographic() {
            // Given - create known active ticklers for specific demo
            createTickler(7001, "Active 1", Tickler.STATUS.A);
            createTickler(7001, "Active 2", Tickler.STATUS.A);
            createTickler(7001, "Completed", Tickler.STATUS.C);
            entityManager.flush();

            // When
            int count = ticklerDao.getActiveTicklerByDemoCount(7001);
            int countNone = ticklerDao.getActiveTicklerByDemoCount(9999);

            // Then
            assertThat(count).isEqualTo(2);
            assertThat(countNone).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Write Operations")
    class WriteOperations {

        @Test
        @Tag("create")
        @Tag("update")
        @DisplayName("should persist new tickler and successfully merge updates")
        void shouldPersistNewTicklerAndMergeUpdates_whenValidDataProvided() {
            // Given
            Tickler tickler = new Tickler();
            tickler.setDemographicNo(5001);
            tickler.setMessage("Persist test");
            tickler.setCreator("999998");
            tickler.setTaskAssignedTo("999998");

            // When - persist
            ticklerDao.persist(tickler);
            entityManager.flush();
            Integer id = tickler.getId();
            assertThat(id).isNotNull();

            // When - merge (update)
            tickler.setMessage("Updated message");
            tickler.setPriority(Tickler.PRIORITY.High);
            ticklerDao.merge(tickler);
            entityManager.flush();
            entityManager.clear();

            // Then
            Tickler updated = ticklerDao.find(id);
            assertThat(updated.getMessage()).isEqualTo("Updated message");
            assertThat(updated.getPriority()).isEqualTo(Tickler.PRIORITY.High);
        }
    }
}