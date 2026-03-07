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

import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.JointAdmission;
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
 * Integration tests for {@link JointAdmissionDao} covering create,
 * getSpouseAndDependents, and getJointAdmission.
 *
 * <p>Migrated from legacy {@code JointAdmissionDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see JointAdmissionDao
 */
@DisplayName("JointAdmission Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admission")
@Transactional
public class JointAdmissionDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private JointAdmissionDao dao;

    @Nested
    @DisplayName("Create operations")
    class CreateOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist joint admission with generated ID")
        void shouldPersistJointAdmission_whenValidDataProvided() {
            JointAdmission entity = new JointAdmission();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("getSpouseAndDependents")
    class GetSpouseAndDependents {

        @Test
        @Tag("read")
        @DisplayName("should return non-archived dependents for the head client")
        void shouldReturnNonArchivedDependents_forHeadClient() {
            int headClientId1 = 101, headClientId2 = 202;

            JointAdmission ja1 = new JointAdmission();
            EntityDataGenerator.generateTestDataForModelClass(ja1);
            ja1.setArchived(false);
            ja1.setHeadClientId(headClientId2);
            dao.persist(ja1);

            JointAdmission ja2 = new JointAdmission();
            EntityDataGenerator.generateTestDataForModelClass(ja2);
            ja2.setArchived(false);
            ja2.setHeadClientId(headClientId1);
            dao.persist(ja2);

            JointAdmission ja3 = new JointAdmission();
            EntityDataGenerator.generateTestDataForModelClass(ja3);
            ja3.setArchived(true);
            ja3.setHeadClientId(headClientId1);
            dao.persist(ja3);

            JointAdmission ja4 = new JointAdmission();
            EntityDataGenerator.generateTestDataForModelClass(ja4);
            ja4.setArchived(false);
            ja4.setHeadClientId(headClientId1);
            dao.persist(ja4);

            JointAdmission ja5 = new JointAdmission();
            EntityDataGenerator.generateTestDataForModelClass(ja5);
            ja5.setArchived(false);
            ja5.setHeadClientId(headClientId1);
            dao.persist(ja5);

            List<JointAdmission> result = dao.getSpouseAndDependents(headClientId1);

            assertThat(result).hasSize(3);
            assertThat(result).containsExactly(ja2, ja4, ja5);
        }
    }

    @Nested
    @DisplayName("getJointAdmission")
    class GetJointAdmission {

        @Test
        @Tag("read")
        @DisplayName("should return first non-archived joint admission for the client")
        void shouldReturnNonArchivedAdmission_forClientId() {
            int clientId1 = 101, clientId2 = 202;

            JointAdmission ja1 = new JointAdmission();
            EntityDataGenerator.generateTestDataForModelClass(ja1);
            ja1.setArchived(false);
            ja1.setClientId(clientId2);
            dao.persist(ja1);

            JointAdmission ja2 = new JointAdmission();
            EntityDataGenerator.generateTestDataForModelClass(ja2);
            ja2.setArchived(false);
            ja2.setClientId(clientId1);
            dao.persist(ja2);

            JointAdmission ja3 = new JointAdmission();
            EntityDataGenerator.generateTestDataForModelClass(ja3);
            ja3.setArchived(true);
            ja3.setClientId(clientId1);
            dao.persist(ja3);

            JointAdmission result = dao.getJointAdmission(clientId1);

            assertThat(result).isEqualTo(ja2);
        }
    }
}
