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
import io.github.carlos_emr.carlos.commn.model.RSchedule;
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
 * Integration tests for {@link RScheduleDao} covering provider resource
 * schedule CRUD, availability lookups, and date-based queries.
 *
 * <p>Migrated from legacy {@code RScheduleDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see RScheduleDao
 */
@DisplayName("RScheduleDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("schedule")
@Transactional
public class RScheduleDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private RScheduleDao rScheduleDao;

    private Date createDate(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, day, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private RSchedule createRSchedule(String providerNo, Date sdate, String available, String dayOfWeek) {
        RSchedule rs = new RSchedule();
        rs.setProviderNo(providerNo);
        rs.setsDate(sdate);
        rs.setAvailable(available);
        rs.setDayOfWeek(dayOfWeek);
        rScheduleDao.persist(rs);
        return rs;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist resource schedule with generated ID")
        void shouldPersistRSchedule_whenValidDataProvided() {
            RSchedule rs = createRSchedule("999998", createDate(2026, Calendar.APRIL, 1), "y", "Mon");
            assertThat(rs.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find resource schedule by ID")
        void shouldFindRSchedule_whenValidIdProvided() {
            RSchedule saved = createRSchedule("999998", createDate(2026, Calendar.APRIL, 2), "y", "Tue");
            RSchedule found = rScheduleDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getProviderNo()).isEqualTo("999998");
            assertThat(found.getAvailable()).isEqualTo("y");
        }
    }

    @Nested
    @DisplayName("Availability queries")
    class AvailabilityQueries {

        private Date scheduleDate;

        @BeforeEach
        void setUp() {
            scheduleDate = createDate(2026, Calendar.MAY, 1);
            createRSchedule("100001", scheduleDate, "y", "Fri");
            createRSchedule("100001", scheduleDate, "n", "Sat");
            createRSchedule("100002", scheduleDate, "y", "Fri");
        }

        @Test
        @Tag("query")
        @DisplayName("should find schedules by provider, availability, and date")
        void shouldFindSchedules_byProviderAvailableAndDate() {
            List<RSchedule> results = rScheduleDao.findByProviderAvailableAndDate("100001", "y", scheduleDate);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getDayOfWeek()).isEqualTo("Fri");
        }

        @Test
        @Tag("query")
        @DisplayName("should find unavailable schedules")
        void shouldFindUnavailableSchedules() {
            List<RSchedule> results = rScheduleDao.findByProviderAvailableAndDate("100001", "n", scheduleDate);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getDayOfWeek()).isEqualTo("Sat");
        }

        @Test
        @Tag("query")
        @DisplayName("should find schedules by provider and date")
        void shouldFindSchedules_byProviderNoAndDate() {
            List<RSchedule> results = rScheduleDao.findByProviderNoAndDates("100001", scheduleDate);
            assertThat(results).hasSizeGreaterThanOrEqualTo(1);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty for non-existent provider")
        void shouldReturnEmpty_whenProviderNotFound() {
            List<RSchedule> results = rScheduleDao.findByProviderAvailableAndDate("999999", "y", scheduleDate);
            assertThat(results).isEmpty();
        }
    }
}
