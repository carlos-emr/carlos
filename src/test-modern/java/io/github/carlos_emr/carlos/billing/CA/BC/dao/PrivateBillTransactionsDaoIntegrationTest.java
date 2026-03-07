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
import io.github.carlos_emr.carlos.billing.CA.BC.model.BillingPrivateTransactions;
import io.github.carlos_emr.carlos.billings.ca.bc.data.PrivateBillTransactionsDAO;
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
 * Integration tests for {@link PrivateBillTransactionsDAO}.
 * <p>Migrated from legacy JUnit 4 / DaoTestFixtures.</p>
 * @since 2026-03-07
 */
@DisplayName("PrivateBillTransactions Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class PrivateBillTransactionsDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private PrivateBillTransactionsDAO privateBillTransactionsDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist entity with generated ID")
        void shouldPersist_whenValidDataProvided() {
            BillingPrivateTransactions entity = new BillingPrivateTransactions();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setCreationDate(new Date());
            privateBillTransactionsDao.persist(entity);
            assertThat(entity.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find entity by ID with correct fields")
        void shouldReturnMatchingEntity_whenFoundById() {
            BillingPrivateTransactions saved = new BillingPrivateTransactions(
                    100, 55.50, new Date(), 1
            );
            privateBillTransactionsDao.persist(saved);

            BillingPrivateTransactions found = privateBillTransactionsDao.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getBillingmasterNo()).isEqualTo(100);
            assertThat(found.getAmountReceived()).isEqualTo(55.50);
            assertThat(found.getPaymentTypeId()).isEqualTo(1);
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when entity not found by ID")
        void shouldReturnNull_whenEntityNotFound() {
            BillingPrivateTransactions found = privateBillTransactionsDao.find(-999);
            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("savePrivateBillTransaction")
    class SavePrivateBillTransaction {

        @Test
        @Tag("create")
        @DisplayName("should save a private bill transaction with correct fields")
        void shouldSaveTransaction_withCorrectFields() {
            BillingPrivateTransactions saved = privateBillTransactionsDao
                    .savePrivateBillTransaction(200, 99.99, 2);

            assertThat(saved).isNotNull();
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getBillingmasterNo()).isEqualTo(200);
            assertThat(saved.getAmountReceived()).isEqualTo(99.99);
            assertThat(saved.getPaymentTypeId()).isEqualTo(2);
            assertThat(saved.getCreationDate()).isNotNull();
        }

        @Test
        @Tag("create")
        @DisplayName("should persist multiple transactions with different billing master numbers")
        void shouldPersistMultipleTransactions_withDifferentBillingMasters() {
            BillingPrivateTransactions tx1 = privateBillTransactionsDao
                    .savePrivateBillTransaction(301, 10.00, 1);
            BillingPrivateTransactions tx2 = privateBillTransactionsDao
                    .savePrivateBillTransaction(302, 20.00, 2);

            assertThat(tx1.getId()).isNotEqualTo(tx2.getId());
            assertThat(tx1.getBillingmasterNo()).isEqualTo(301);
            assertThat(tx2.getBillingmasterNo()).isEqualTo(302);
        }
    }
}
