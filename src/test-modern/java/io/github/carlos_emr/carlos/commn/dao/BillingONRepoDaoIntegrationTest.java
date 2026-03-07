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
import io.github.carlos_emr.carlos.commn.model.BillingONRepo;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link BillingONRepoDao} covering basic CRUD operations.
 *
 * <p>Tests persist, find, and getCountAll (inherited from AbstractDaoImpl).
 * The DAO's custom methods (createBillingONItemEntry, createBillingONCHeader1Entry)
 * require complex dependencies (ProviderDao via SpringUtils) and are not tested here.</p>
 *
 * @since 2026-03-07
 * @see BillingONRepoDao
 */
@DisplayName("BillingONRepo Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing")
@Transactional
public class BillingONRepoDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingONRepoDao billingONRepoDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist billingonrepo with generated ID")
        void shouldPersistBillingONRepo_whenValidDataProvided() {
            BillingONRepo entity = new BillingONRepo();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            billingONRepoDao.persist(entity);
            assertThat(entity.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find billingonrepo by ID with correct field values")
        void shouldFindBillingONRepo_whenValidIdProvided() {
            BillingONRepo saved = new BillingONRepo();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            saved.setCategory("test-category");
            saved.setContent("test-content");
            saved.sethId(42);
            saved.setCreateDateTime(new Date());
            billingONRepoDao.persist(saved);

            BillingONRepo found = billingONRepoDao.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getCategory()).isEqualTo("test-category");
            assertThat(found.getContent()).isEqualTo("test-content");
            assertThat(found.gethId()).isEqualTo(42);
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when billingonrepo not found by ID")
        void shouldReturnNull_whenBillingONRepoNotFoundById() {
            BillingONRepo found = billingONRepoDao.find(99999);
            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should count all billingonrepo records accurately")
        void shouldCountAllBillingONRepos() {
            BillingONRepo entity1 = new BillingONRepo();
            EntityDataGenerator.generateTestDataForModelClass(entity1);
            billingONRepoDao.persist(entity1);

            BillingONRepo entity2 = new BillingONRepo();
            EntityDataGenerator.generateTestDataForModelClass(entity2);
            billingONRepoDao.persist(entity2);

            BillingONRepo entity3 = new BillingONRepo();
            EntityDataGenerator.generateTestDataForModelClass(entity3);
            billingONRepoDao.persist(entity3);

            long count = billingONRepoDao.getCountAll();
            assertThat(count).isEqualTo(3);
        }
    }
}
