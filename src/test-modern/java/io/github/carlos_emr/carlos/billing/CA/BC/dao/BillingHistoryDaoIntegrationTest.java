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

import io.github.carlos_emr.carlos.billing.CA.BC.model.BillingHistory;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BillingHistoryDao}.
 * <p>Migrated from legacy JUnit 4 BillingHistoryDaoTest with full method coverage.</p>
 *
 * @since 2026-03-07
 */
@DisplayName("BillingHistoryDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class BillingHistoryDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingHistoryDao dao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist entity with generated test data")
        void shouldPersistEntity_whenValidDataProvided() throws Exception {
            BillingHistory entity = new BillingHistory();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find entity by ID with matching field values")
        void shouldReturnMatchingEntity_whenFoundById() throws Exception {
            BillingHistory entity = new BillingHistory();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setBillingMasterNo(500);
            entity.setStatus("A");
            entity.setAmount("150.00");
            entity.setAmountReceived("100.00");
            dao.persist(entity);

            BillingHistory found = dao.find(entity.getId());
            assertThat(found.getId()).isEqualTo(entity.getId());
            assertThat(found.getBillingMasterNo()).isEqualTo(500);
            assertThat(found.getStatus()).isEqualTo("A");
            assertThat(found.getAmount()).isEqualTo("150.00");
            assertThat(found.getAmountReceived()).isEqualTo("100.00");
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when entity not found by ID")
        void shouldReturnNull_whenInvalidIdProvided() throws Exception {
            BillingHistory found = dao.find(-999);
            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("findByBillingMasterNo")
    class FindByBillingMasterNo {

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no history exists for billing master number")
        void shouldReturnEmptyList_whenNoBillingMasterNoMatches() throws Exception {
            List<Object[]> results = dao.findByBillingMasterNo(-999);
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return billing history with payment type for matching billing master number")
        void shouldReturnHistoryWithPaymentType_whenBillingMasterNoMatches() throws Exception {
            BillingPaymentType paymentType = new BillingPaymentType();
            paymentType.setPaymentType("Cash");
            entityManager.persist(paymentType);
            entityManager.flush();

            BillingHistory history = new BillingHistory();
            EntityDataGenerator.generateTestDataForModelClass(history);
            history.setBillingMasterNo(700);
            history.setPaymentTypeId(paymentType.getId());
            history.setAmountReceived("200.00");
            dao.persist(history);
            entityManager.flush();

            List<Object[]> results = dao.findByBillingMasterNo(700);
            assertThat(results).hasSize(1);

            Object[] row = results.get(0);
            BillingHistory foundHistory = (BillingHistory) row[0];
            BillingPaymentType foundPaymentType = (BillingPaymentType) row[1];
            assertThat(foundHistory.getBillingMasterNo()).isEqualTo(700);
            assertThat(foundHistory.getAmountReceived()).isEqualTo("200.00");
            assertThat(foundPaymentType.getPaymentType()).isEqualTo("Cash");
        }
    }

    @Nested
    @DisplayName("getTotalPaidFromHistory")
    class GetTotalPaidFromHistory {

        @Test
        @Tag("read")
        @DisplayName("should return zero when no history exists for billing master number")
        void shouldReturnZero_whenNoHistoryExists() throws Exception {
            Double total = dao.getTotalPaidFromHistory(-999, false);
            assertThat(total).isEqualTo(0.0);
        }

        @Test
        @Tag("read")
        @DisplayName("should return zero when no history exists with IA exclusion")
        void shouldReturnZero_whenNoHistoryExistsWithIaExclusion() throws Exception {
            Double total = dao.getTotalPaidFromHistory(-999, true);
            assertThat(total).isEqualTo(0.0);
        }

        @Test
        @Tag("read")
        @DisplayName("should sum amount received for matching billing master number")
        void shouldSumAmountReceived_whenHistoryExists() throws Exception {
            BillingHistory h1 = new BillingHistory();
            EntityDataGenerator.generateTestDataForModelClass(h1);
            h1.setBillingMasterNo(800);
            h1.setAmountReceived("100.50");
            h1.setPaymentTypeId(1);
            dao.persist(h1);

            BillingHistory h2 = new BillingHistory();
            EntityDataGenerator.generateTestDataForModelClass(h2);
            h2.setBillingMasterNo(800);
            h2.setAmountReceived("50.25");
            h2.setPaymentTypeId(2);
            dao.persist(h2);

            // different billing master - should not be included
            BillingHistory h3 = new BillingHistory();
            EntityDataGenerator.generateTestDataForModelClass(h3);
            h3.setBillingMasterNo(801);
            h3.setAmountReceived("999.00");
            h3.setPaymentTypeId(1);
            dao.persist(h3);
            entityManager.flush();

            Double total = dao.getTotalPaidFromHistory(800, false);
            assertThat(total).isEqualTo(150.75);
        }

        @Test
        @Tag("read")
        @DisplayName("should exclude IA payment type when ignoreIA is true")
        void shouldExcludeIaPaymentType_whenIgnoreIaIsTrue() throws Exception {
            // PAYTYPE_IA = "10"
            BillingHistory h1 = new BillingHistory();
            EntityDataGenerator.generateTestDataForModelClass(h1);
            h1.setBillingMasterNo(900);
            h1.setAmountReceived("75.00");
            h1.setPaymentTypeId(1);
            dao.persist(h1);

            BillingHistory h2 = new BillingHistory();
            EntityDataGenerator.generateTestDataForModelClass(h2);
            h2.setBillingMasterNo(900);
            h2.setAmountReceived("25.00");
            h2.setPaymentTypeId(10);
            dao.persist(h2);
            entityManager.flush();

            Double totalWithIa = dao.getTotalPaidFromHistory(900, false);
            assertThat(totalWithIa).isEqualTo(100.0);

            Double totalWithoutIa = dao.getTotalPaidFromHistory(900, true);
            assertThat(totalWithoutIa).isEqualTo(75.0);
        }
    }
}
