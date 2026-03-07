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
package io.github.carlos_emr.carlos.billing.CA.BC.dao;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Orc;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Pid;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link Hl7OrcDao}.
 * <p>Migrated from legacy JUnit 4 / DaoTestFixtures.</p>
 * @since 2026-03-07
 */
@DisplayName("Hl7Orc Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class Hl7OrcDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private Hl7OrcDao hl7OrcDao;

    @Autowired
    private Hl7PidDao hl7PidDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist entity with generated ID")
        void shouldPersist_whenValidDataProvided() {
            Hl7Orc entity = new Hl7Orc();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            hl7OrcDao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find entity by ID with correct fields")
        void shouldReturnMatchingEntity_whenFoundById() {
            Hl7Orc saved = new Hl7Orc();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            saved.setPidId(300);
            saved.setFillerOrderNumber("FILLER-001");
            saved.setOrderControl("NW");
            hl7OrcDao.persist(saved);

            Hl7Orc found = hl7OrcDao.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getPidId()).isEqualTo(300);
            assertThat(found.getFillerOrderNumber()).isEqualTo("FILLER-001");
            assertThat(found.getOrderControl()).isEqualTo("NW");
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when entity not found by ID")
        void shouldReturnNull_whenEntityNotFound() {
            Hl7Orc found = hl7OrcDao.find(-999);
            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("findOrcAndPidByMessageId")
    class FindOrcAndPidByMessageId {

        @Test
        @Tag("read")
        @DisplayName("should return joined ORC and PID records for a given message ID")
        void shouldReturnJoinedResults_whenMatchingDataExists() {
            Hl7Pid pid = new Hl7Pid();
            EntityDataGenerator.generateTestDataForModelClass(pid);
            pid.setMessageId(4000);
            hl7PidDao.persist(pid);

            Hl7Orc orc = new Hl7Orc();
            EntityDataGenerator.generateTestDataForModelClass(orc);
            orc.setPidId(pid.getId());
            hl7OrcDao.persist(orc);

            List<Object[]> results = hl7OrcDao.findOrcAndPidByMessageId(4000);

            assertThat(results).hasSize(1);
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no matching message ID")
        void shouldReturnEmptyList_whenNoMatchingMessageId() {
            List<Object[]> results = hl7OrcDao.findOrcAndPidByMessageId(99999);
            assertThat(results).isEmpty();
        }
    }
}
