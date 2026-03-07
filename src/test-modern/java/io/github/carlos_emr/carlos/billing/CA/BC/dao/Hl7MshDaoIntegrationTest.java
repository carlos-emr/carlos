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
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Msh;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link Hl7MshDao}.
 * <p>Migrated from legacy JUnit 4 / DaoTestFixtures.</p>
 * @since 2026-03-07
 */
@DisplayName("Hl7Msh Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class Hl7MshDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private Hl7MshDao hl7MshDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist entity with generated ID")
        void shouldPersist_whenValidDataProvided() {
            Hl7Msh entity = new Hl7Msh();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            hl7MshDao.persist(entity);
            assertThat(entity.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find entity by ID with matching fields")
        void shouldReturnMatchingEntity_whenFoundById() {
            Hl7Msh saved = new Hl7Msh();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            saved.setMessageId(9001);
            saved.setSendingApp("TestSendingApp");
            saved.setMessageType("ORU");
            hl7MshDao.persist(saved);

            Hl7Msh found = hl7MshDao.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getMessageId()).isEqualTo(9001);
            assertThat(found.getSendingApp()).isEqualTo("TestSendingApp");
            assertThat(found.getMessageType()).isEqualTo("ORU");
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when entity not found by ID")
        void shouldReturnNull_whenEntityNotFound() {
            Hl7Msh found = hl7MshDao.find(-999);
            assertThat(found).isNull();
        }

        @Test
        @Tag("update")
        @DisplayName("should update entity fields after merge")
        void shouldUpdateFields_whenMerged() {
            Hl7Msh entity = new Hl7Msh();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setSendingApp("OriginalApp");
            hl7MshDao.persist(entity);

            entity.setSendingApp("UpdatedApp");
            hl7MshDao.merge(entity);
            hl7MshDao.flush();

            Hl7Msh found = hl7MshDao.find(entity.getId());
            assertThat(found.getSendingApp()).isEqualTo("UpdatedApp");
        }
    }
}
