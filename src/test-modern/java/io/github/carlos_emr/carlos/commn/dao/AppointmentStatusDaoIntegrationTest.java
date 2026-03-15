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
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link AppointmentStatusDao} covering status code
 * CRUD, lookup operations, and status list retrieval.
 *
 * <p>Migrated from legacy {@code AppointmentStatusDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see AppointmentStatusDao
 */
@DisplayName("AppointmentStatusDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("appointment")
@Transactional
public class AppointmentStatusDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private AppointmentStatusDao appointmentStatusDao;

    private AppointmentStatus createStatus(String statusCode, String description, String icon) {
        AppointmentStatus status = new AppointmentStatus();
        status.setStatus(statusCode);
        status.setDescription(description);
        status.setIcon(icon);
        status.setActive(1);
        appointmentStatusDao.persist(status);
        return status;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist appointment status with generated ID")
        void shouldPersistStatus_whenValidDataProvided() {
            AppointmentStatus status = createStatus("T", "Test Status", "test.gif");
            assertThat(status.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find appointment status by ID")
        void shouldFindStatus_whenValidIdProvided() {
            AppointmentStatus saved = createStatus("X", "Experimental", "exp.gif");
            AppointmentStatus found = appointmentStatusDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getStatus()).isEqualTo("X");
            assertThat(found.getDescription()).isEqualTo("Experimental");
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find all appointment statuses")
        void shouldFindAllStatuses() {
            createStatus("A", "Active", "active.gif");
            createStatus("C", "Cancelled", "cancel.gif");
            List<AppointmentStatus> all = appointmentStatusDao.findAll(0, 100);
            assertThat(all).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should count all statuses")
        void shouldCountAllStatuses() {
            createStatus("Z", "Test Count", "z.gif");
            long count = appointmentStatusDao.getCountAll();
            assertThat(count).isGreaterThanOrEqualTo(1);
        }
    }
}
