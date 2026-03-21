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
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link AppointmentArchiveDao} covering persist,
 * date-based queries, and appointment archiving.
 *
 * <p>Migrated from legacy {@code AppointmentArchiveDaoTest} (JUnit 4 / DaoTestFixtures)
 * with expanded coverage and BDD-style naming.</p>
 *
 * @since 2026-03-07
 * @see AppointmentArchiveDao
 */
@DisplayName("AppointmentArchiveDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("appointment")
@Transactional
public class AppointmentArchiveDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private AppointmentArchiveDao dao;

    @Autowired
    private OscarAppointmentDao appointmentDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist appointment archive with generated ID")
        void shouldPersistAppointmentArchive_whenValidDataProvided() throws Exception {
            AppointmentArchive entity = new AppointmentArchive();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("findByUpdateDate queries")
    class FindByUpdateDate {

        @Test
        @Tag("query")
        @DisplayName("should find archives updated after yesterday")
        void shouldFindArchives_whenUpdatedAfterYesterday() throws Exception {
            AppointmentArchive entity = new AppointmentArchive();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DAY_OF_YEAR, -1);
            List<AppointmentArchive> results = dao.findByUpdateDate(cal.getTime(), 99);

            assertThat(results).isNotEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when searching for future updates")
        void shouldReturnEmpty_whenSearchingForFutureUpdates() throws Exception {
            AppointmentArchive entity = new AppointmentArchive();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DAY_OF_YEAR, 1);
            List<AppointmentArchive> results = dao.findByUpdateDate(cal.getTime(), 99);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("archiveAppointment")
    class ArchiveAppointment {

        @Test
        @Tag("create")
        @DisplayName("should archive an existing appointment")
        void shouldArchiveAppointment_whenValidAppointmentProvided() throws Exception {
            Appointment appt = new Appointment();
            EntityDataGenerator.generateTestDataForModelClass(appt);
            appointmentDao.persist(appt);

            AppointmentArchive archive = dao.archiveAppointment(appt);

            assertThat(archive).isNotNull();
            assertThat(archive.getId()).isNotNull();
        }
    }
}
