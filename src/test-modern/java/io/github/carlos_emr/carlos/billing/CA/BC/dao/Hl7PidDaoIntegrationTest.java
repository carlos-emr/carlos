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

import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Msh;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Pid;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link Hl7PidDao}.
 * <p>Migrated from legacy JUnit 4 Hl7PidDaoTest with full method coverage.</p>
 *
 * @since 2026-03-07
 */
@DisplayName("Hl7PidDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class Hl7PidDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private Hl7PidDao dao;

    @Autowired
    private Hl7MshDao mshDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist entity with generated test data")
        void shouldPersistEntity_whenValidDataProvided() {
            Hl7Pid entity = new Hl7Pid();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find entity by ID with correct fields")
        void shouldReturnMatchingEntity_whenFoundById() {
            Hl7Pid entity = new Hl7Pid();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setMessageId(5000);
            entity.setPatientName("TestPatient");
            entity.setSex("M");
            dao.persist(entity);

            Hl7Pid found = dao.find(entity.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(entity.getId());
            assertThat(found.getMessageId()).isEqualTo(5000);
            assertThat(found.getPatientName()).isEqualTo("TestPatient");
            assertThat(found.getSex()).isEqualTo("M");
        }
    }

    @Nested
    @DisplayName("findByMessageId")
    class FindByMessageId {

        @Test
        @Tag("read")
        @DisplayName("should return PIDs matching the given message ID")
        void shouldReturnMatchingPids_whenMessageIdMatches() {
            Hl7Pid match1 = new Hl7Pid();
            EntityDataGenerator.generateTestDataForModelClass(match1);
            match1.setMessageId(6000);
            dao.persist(match1);

            Hl7Pid match2 = new Hl7Pid();
            EntityDataGenerator.generateTestDataForModelClass(match2);
            match2.setMessageId(6000);
            dao.persist(match2);

            Hl7Pid nonMatch = new Hl7Pid();
            EntityDataGenerator.generateTestDataForModelClass(nonMatch);
            nonMatch.setMessageId(6001);
            dao.persist(nonMatch);

            List<Hl7Pid> results = dao.findByMessageId(6000);

            assertThat(results).hasSize(2);
            assertThat(results).extracting(Hl7Pid::getMessageId)
                    .containsOnly(6000);
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no PIDs match message ID")
        void shouldReturnEmptyList_whenNoMessageIdMatches() {
            List<Hl7Pid> results = dao.findByMessageId(99999);
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findPidsAndMshByMessageId")
    class FindPidsAndMshByMessageId {

        @Test
        @Tag("read")
        @DisplayName("should return joined PID and MSH records for a given message ID")
        void shouldReturnJoinedResults_whenMatchingDataExists() {
            Hl7Msh msh = new Hl7Msh();
            EntityDataGenerator.generateTestDataForModelClass(msh);
            msh.setMessageId(7000);
            mshDao.persist(msh);

            Hl7Pid pid = new Hl7Pid();
            EntityDataGenerator.generateTestDataForModelClass(pid);
            pid.setMessageId(7000);
            dao.persist(pid);

            List<Object[]> results = dao.findPidsAndMshByMessageId(7000);

            assertThat(results).hasSize(1);
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no matching message ID")
        void shouldReturnEmptyList_whenNoMatchingMessageId() {
            List<Object[]> results = dao.findPidsAndMshByMessageId(99999);
            assertThat(results).isEmpty();
        }
    }
}
