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
import io.github.carlos_emr.carlos.billing.CA.BC.model.BillRecipients;
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
 * Integration tests for {@link BillRecipientsDao}.
 * <p>Migrated from legacy JUnit 4 / DaoTestFixtures.</p>
 * @since 2026-03-07
 */
@DisplayName("BillRecipients Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class BillRecipientsDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillRecipientsDao billRecipientsDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist entity with generated ID")
        void shouldPersist_whenValidDataProvided() {
            BillRecipients entity = new BillRecipients();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setBillingNo(100);
            billRecipientsDao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find entity by ID with matching field values")
        void shouldReturnMatchingEntity_whenFoundById() {
            BillRecipients saved = new BillRecipients();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            saved.setBillingNo(200);
            saved.setName("Test Recipient");
            saved.setCity("Vancouver");
            saved.setProvince("BC");
            billRecipientsDao.persist(saved);

            BillRecipients found = billRecipientsDao.find(saved.getId());
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getName()).isEqualTo("Test Recipient");
            assertThat(found.getCity()).isEqualTo("Vancouver");
            assertThat(found.getProvince()).isEqualTo("BC");
            assertThat(found.getBillingNo()).isEqualTo(200);
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when entity not found by ID")
        void shouldReturnNull_whenInvalidIdProvided() {
            BillRecipients found = billRecipientsDao.find(-999);
            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("findByBillingNo")
    class FindByBillingNo {

        @Test
        @Tag("read")
        @DisplayName("should return recipients matching the billing number")
        void shouldReturnRecipients_whenBillingNoMatches() {
            BillRecipients match1 = new BillRecipients();
            EntityDataGenerator.generateTestDataForModelClass(match1);
            match1.setBillingNo(300);
            match1.setName("Recipient A");
            billRecipientsDao.persist(match1);

            BillRecipients match2 = new BillRecipients();
            EntityDataGenerator.generateTestDataForModelClass(match2);
            match2.setBillingNo(300);
            match2.setName("Recipient B");
            billRecipientsDao.persist(match2);

            BillRecipients nonMatch = new BillRecipients();
            EntityDataGenerator.generateTestDataForModelClass(nonMatch);
            nonMatch.setBillingNo(999);
            nonMatch.setName("Other Recipient");
            billRecipientsDao.persist(nonMatch);

            List<BillRecipients> results = billRecipientsDao.findByBillingNo(300);
            assertThat(results).hasSize(2);
            assertThat(results).extracting(BillRecipients::getName)
                    .containsExactlyInAnyOrder("Recipient A", "Recipient B");
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no recipients match billing number")
        void shouldReturnEmptyList_whenNoBillingNoMatches() {
            List<BillRecipients> results = billRecipientsDao.findByBillingNo(-1);
            assertThat(results).isEmpty();
        }
    }
}
