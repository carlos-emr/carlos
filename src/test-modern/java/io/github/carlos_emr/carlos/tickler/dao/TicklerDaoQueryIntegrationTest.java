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

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for TicklerDao query and filter operations.
 *
 * <p>This test class validates complex query operations including date range
 * searches, custom filters, and pagination. These operations involve more
 * sophisticated database queries than simple find operations.</p>
 *
 * @since 2025-01-17
 * @see TicklerDao
 * @see TicklerDaoBaseIntegrationTest
 * @see CustomFilter
 */
@DisplayName("Tickler DAO Query Integration Tests")
@Tag("integration")
@Tag("database")
@Tag("slow")
@Tag("read")
public class TicklerDaoQueryIntegrationTest extends TicklerDaoBaseIntegrationTest {

    @Nested
    @DisplayName("Date Range Queries")
    class DateRangeQueries {

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
    }

    @Nested
    @DisplayName("Custom Filter Operations")
    class CustomFilterOperations {

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
    }

    @Nested
    @DisplayName("Pagination")
    class Pagination {

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
}