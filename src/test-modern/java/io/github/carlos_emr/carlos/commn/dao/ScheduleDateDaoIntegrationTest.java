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
import io.github.carlos_emr.carlos.commn.model.ScheduleDate;
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
 * Integration tests for {@link ScheduleDateDao} covering schedule date
 * CRUD, provider-based lookups, and date range queries.
 *
 * <p>Migrated from legacy {@code ScheduleDateDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ScheduleDateDao
 */
@DisplayName("ScheduleDateDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("schedule")
@Transactional
public class ScheduleDateDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ScheduleDateDao scheduleDateDao;

    private Date createDate(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, day, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private ScheduleDate createScheduleDate(String providerNo, Date date, char priority, String hour) {
        ScheduleDate sd = new ScheduleDate();
        sd.setProviderNo(providerNo);
        sd.setDate(date);
        sd.setPriority(priority);
        sd.setHour(hour);
        sd.setStatus('A');
        sd.setReason("Test schedule");
        sd.setCreator("999998");
        scheduleDateDao.persist(sd);
        return sd;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist schedule date with generated ID")
        void shouldPersistScheduleDate_whenValidDataProvided() {
            ScheduleDate sd = createScheduleDate("999998", createDate(2026, Calendar.MARCH, 15), 'a', "09:00-17:00");
            assertThat(sd.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find schedule date by ID")
        void shouldFindScheduleDate_whenValidIdProvided() {
            ScheduleDate saved = createScheduleDate("999998", createDate(2026, Calendar.MARCH, 16), 'a', "08:00-16:00");
            ScheduleDate found = scheduleDateDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getProviderNo()).isEqualTo("999998");
        }
    }

    @Nested
    @DisplayName("Provider and date queries")
    class ProviderDateQueries {

        private Date date1;
        private Date date2;
        private Date date3;

        @BeforeEach
        void setUp() {
            date1 = createDate(2026, Calendar.APRIL, 1);
            date2 = createDate(2026, Calendar.APRIL, 2);
            date3 = createDate(2026, Calendar.APRIL, 3);
            createScheduleDate("100001", date1, 'a', "09:00-17:00");
            createScheduleDate("100001", date2, 'b', "08:00-12:00");
            createScheduleDate("100001", date3, 'a', "13:00-17:00");
            createScheduleDate("100002", date1, 'a', "09:00-17:00");
        }

        @Test
        @Tag("query")
        @DisplayName("should find schedule date by provider and date")
        void shouldFindScheduleDate_byProviderNoAndDate() {
            ScheduleDate found = scheduleDateDao.findByProviderNoAndDate("100001", date1);
            assertThat(found).isNotNull();
            assertThat(found.getHour()).isEqualTo("09:00-17:00");
        }

        @Test
        @Tag("query")
        @DisplayName("should find schedule dates by provider and date range")
        void shouldFindScheduleDates_byProviderAndDateRange() {
            List<ScheduleDate> results = scheduleDateDao.findByProviderAndDateRange("100001", date1, date3);
            assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should find schedule dates by provider priority and date range")
        void shouldFindScheduleDates_byProviderPriorityAndDateRange() {
            List<ScheduleDate> results = scheduleDateDao.findByProviderPriorityAndDateRange("100001", 'a', date1, date3);
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should return null for non-existent provider-date combination")
        void shouldReturnNull_whenProviderDateNotFound() {
            ScheduleDate found = scheduleDateDao.findByProviderNoAndDate("999999", date1);
            assertThat(found).isNull();
        }
    }
}
