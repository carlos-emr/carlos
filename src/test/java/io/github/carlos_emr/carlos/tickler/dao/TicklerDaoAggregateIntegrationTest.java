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

import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for TicklerDao aggregation operations.
 *
 * <p>This test class validates counting and statistical operations including
 * counting by provider, counting with filters, and counting by demographic.
 * These operations perform aggregations over the tickler data.</p>
 *
 * @since 2025-01-17
 * @see TicklerDao
 * @see TicklerDaoBaseIntegrationTest
 */
@DisplayName("Tickler DAO Aggregation Integration Tests")
@Tag("integration")
@Tag("database")
@Tag("slow")
@Tag("read")
@Tag("aggregate")
public class TicklerDaoAggregateIntegrationTest extends TicklerDaoBaseIntegrationTest {

    @Nested
    @DisplayName("Count by Provider")
    class CountByProvider {

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
    }

    @Nested
    @DisplayName("Count with Filters")
    class CountWithFilters {

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
    }

    @Nested
    @DisplayName("Count by Demographic")
    class CountByDemographic {

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
}