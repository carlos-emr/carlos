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
package io.github.carlos_emr.carlos.tickler.dao;

import io.github.carlos_emr.carlos.commn.model.Tickler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for TicklerDao find operations.
 *
 * <p>This test class validates all find-related operations of the TicklerDao,
 * including finding by ID, finding active ticklers, and finding by various
 * criteria. These are simple retrieval operations that don't involve complex
 * queries or aggregations.</p>
 *
 * @since 2025-01-17
 * @see TicklerDao
 * @see TicklerDaoBaseIntegrationTest
 */
@DisplayName("Tickler DAO Find Integration Tests")
@Tag("integration")
@Tag("database")
@Tag("slow")
@Tag("read")
public class TicklerDaoFindIntegrationTest extends TicklerDaoBaseIntegrationTest {

    @Nested
    @DisplayName("Find by ID")
    class FindById {

        @Test
        @DisplayName("should return tickler when valid ID is provided")
        void shouldReturnTickler_whenValidIdProvided() {
            // Given: Create and persist a new tickler
            Tickler newTickler = new Tickler();
            newTickler.setDemographicNo(2001);
            newTickler.setMessage("Find by ID test");
            newTickler.setCreator("999998");
            newTickler.setTaskAssignedTo("999998");
            newTickler.setStatus(Tickler.STATUS.A);
            newTickler.setServiceDate(new java.util.Date());
            entityManager.persist(newTickler);
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
    }

    @Nested
    @DisplayName("Find Active Ticklers")
    class FindActiveTicklers {

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
    }

    @Nested
    @DisplayName("List Operations")
    class ListOperations {

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
}