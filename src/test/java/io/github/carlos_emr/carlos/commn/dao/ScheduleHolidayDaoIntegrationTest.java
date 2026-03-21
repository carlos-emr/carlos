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
import io.github.carlos_emr.carlos.commn.model.ScheduleHoliday;
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
 * Integration tests for {@link ScheduleHolidayDao} covering holiday
 * schedule CRUD and date-based queries.
 *
 * <p>Migrated from legacy {@code ScheduleHolidayDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ScheduleHolidayDao
 */
@DisplayName("ScheduleHolidayDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("schedule")
@Transactional
public class ScheduleHolidayDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ScheduleHolidayDao scheduleHolidayDao;

    private Date createDate(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, day, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private ScheduleHoliday createHoliday(Date date, String holidayName) {
        ScheduleHoliday holiday = new ScheduleHoliday();
        holiday.setId(date);
        holiday.setHolidayName(holidayName);
        scheduleHolidayDao.persist(holiday);
        return holiday;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist schedule holiday")
        void shouldPersistScheduleHoliday_whenValidDataProvided() {
            Date xmas = createDate(2026, Calendar.DECEMBER, 25);
            ScheduleHoliday holiday = createHoliday(xmas, "Christmas Day");
            ScheduleHoliday found = scheduleHolidayDao.find(xmas);
            assertThat(found).isNotNull();
            assertThat(found.getHolidayName()).isEqualTo("Christmas Day");
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find all holidays")
        void shouldFindAllHolidays() {
            createHoliday(createDate(2026, Calendar.JANUARY, 1), "New Year");
            createHoliday(createDate(2026, Calendar.JULY, 1), "Canada Day");
            createHoliday(createDate(2026, Calendar.DECEMBER, 25), "Christmas");

            List<ScheduleHoliday> all = scheduleHolidayDao.findAll();
            assertThat(all).hasSizeGreaterThanOrEqualTo(3);
        }

        @Test
        @Tag("query")
        @DisplayName("should find holidays after date")
        void shouldFindHolidays_afterDate() {
            createHoliday(createDate(2026, Calendar.MARCH, 1), "Past Holiday");
            createHoliday(createDate(2026, Calendar.JUNE, 15), "Mid Year");
            createHoliday(createDate(2026, Calendar.NOVEMBER, 11), "Remembrance");

            Date cutoff = createDate(2026, Calendar.MAY, 1);
            List<ScheduleHoliday> results = scheduleHolidayDao.findAfterDate(cutoff);
            assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        }
    }
}
