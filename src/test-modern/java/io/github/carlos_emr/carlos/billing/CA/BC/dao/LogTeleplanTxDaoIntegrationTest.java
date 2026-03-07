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
import io.github.carlos_emr.carlos.billing.CA.BC.model.LogTeleplanTx;
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
 * Integration tests for {@link LogTeleplanTxDao}.
 * <p>
 * This DAO only inherits CRUD operations from {@link io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl},
 * so tests cover persist, find, merge, and remove.
 * </p>
 * @since 2026-03-07
 */
@DisplayName("LogTeleplanTx Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class LogTeleplanTxDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private LogTeleplanTxDao logTeleplanTxDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist entity with generated ID")
        void shouldPersist_whenValidDataProvided() {
            LogTeleplanTx entity = new LogTeleplanTx();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setSequenceNo(10);
            entity.setBillingMasterNo(200);
            logTeleplanTxDao.persist(entity);
            assertThat(entity.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find entity by ID with correct fields")
        void shouldReturnMatchingEntity_whenFoundById() {
            LogTeleplanTx saved = new LogTeleplanTx();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            saved.setSequenceNo(42);
            saved.setBillingMasterNo(555);
            saved.setClaim("test-claim-data".getBytes());
            logTeleplanTxDao.persist(saved);

            LogTeleplanTx found = logTeleplanTxDao.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getSequenceNo()).isEqualTo(42);
            assertThat(found.getBillingMasterNo()).isEqualTo(555);
            assertThat(found.getClaim()).isEqualTo("test-claim-data".getBytes());
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when entity not found by ID")
        void shouldReturnNull_whenEntityNotFound() {
            LogTeleplanTx found = logTeleplanTxDao.find(-999);
            assertThat(found).isNull();
        }

        @Test
        @Tag("update")
        @DisplayName("should update entity fields after merge")
        void shouldUpdateFields_whenMerged() {
            LogTeleplanTx entity = new LogTeleplanTx();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setSequenceNo(1);
            entity.setBillingMasterNo(100);
            logTeleplanTxDao.persist(entity);

            entity.setSequenceNo(99);
            entity.setBillingMasterNo(999);
            logTeleplanTxDao.merge(entity);
            logTeleplanTxDao.flush();

            LogTeleplanTx found = logTeleplanTxDao.find(entity.getId());
            assertThat(found.getSequenceNo()).isEqualTo(99);
            assertThat(found.getBillingMasterNo()).isEqualTo(999);
        }

        @Test
        @Tag("delete")
        @DisplayName("should remove entity from database")
        void shouldRemoveEntity_whenDeleted() {
            LogTeleplanTx entity = new LogTeleplanTx();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            logTeleplanTxDao.persist(entity);
            Integer savedId = entity.getId();
            assertThat(savedId).isNotNull();

            logTeleplanTxDao.remove(entity);
            logTeleplanTxDao.flush();

            LogTeleplanTx found = logTeleplanTxDao.find(savedId);
            assertThat(found).isNull();
        }

        @Test
        @Tag("create")
        @DisplayName("should persist multiple entities with distinct IDs")
        void shouldPersistMultipleEntities_withDistinctIds() {
            LogTeleplanTx entity1 = new LogTeleplanTx();
            EntityDataGenerator.generateTestDataForModelClass(entity1);
            entity1.setSequenceNo(1);
            logTeleplanTxDao.persist(entity1);

            LogTeleplanTx entity2 = new LogTeleplanTx();
            EntityDataGenerator.generateTestDataForModelClass(entity2);
            entity2.setSequenceNo(2);
            logTeleplanTxDao.persist(entity2);

            assertThat(entity1.getId()).isNotEqualTo(entity2.getId());

            List<LogTeleplanTx> all = logTeleplanTxDao.findAll(null, null);
            assertThat(all).hasSizeGreaterThanOrEqualTo(2);
        }
    }
}
