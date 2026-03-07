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
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingPreference;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingPreferencesDAO;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link BillingPreferencesDAO}.
 * <p>Migrated from legacy JUnit 4 / DaoTestFixtures.</p>
 * @since 2026-03-07
 */
@DisplayName("BillingPreferences Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class BillingPreferencesDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingPreferencesDAO billingPreferencesDAO;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist entity with generated ID")
        void shouldPersist_whenValidDataProvided() {
            BillingPreference entity = new BillingPreference();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setProviderNo("100001");
            billingPreferencesDAO.persist(entity);
            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find entity by ID with matching field values")
        void shouldReturnMatchingEntity_whenFoundById() {
            BillingPreference saved = new BillingPreference();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            saved.setProviderNo("200001");
            saved.setReferral(2);
            saved.setDefaultPayeeNo("PAY01");
            billingPreferencesDAO.persist(saved);

            BillingPreference found = billingPreferencesDAO.find(saved.getId());
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getProviderNo()).isEqualTo("200001");
            assertThat(found.getReferral()).isEqualTo(2);
            assertThat(found.getDefaultPayeeNo()).isEqualTo("PAY01");
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when entity not found by ID")
        void shouldReturnNull_whenInvalidIdProvided() {
            BillingPreference found = billingPreferencesDAO.find(-999);
            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("getUserBillingPreference")
    class GetUserBillingPreference {

        @Test
        @Tag("read")
        @DisplayName("should return preference for matching provider number")
        void shouldReturnPreference_whenProviderNoMatches() {
            BillingPreference pref = new BillingPreference();
            EntityDataGenerator.generateTestDataForModelClass(pref);
            pref.setProviderNo("300001");
            pref.setReferral(3);
            pref.setDefaultPayeeNo("PAYEE1");
            billingPreferencesDAO.persist(pref);

            BillingPreference found = billingPreferencesDAO.getUserBillingPreference("300001");
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(pref.getId());
            assertThat(found.getProviderNo()).isEqualTo("300001");
            assertThat(found.getReferral()).isEqualTo(3);
            assertThat(found.getDefaultPayeeNo()).isEqualTo("PAYEE1");
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when no preference exists for provider number")
        void shouldReturnNull_whenNoPreferenceExists() {
            BillingPreference found = billingPreferencesDAO.getUserBillingPreference("NONEXISTENT");
            assertThat(found).isNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should return correct preference when multiple providers have preferences")
        void shouldReturnCorrectPreference_whenMultipleProvidersExist() {
            BillingPreference pref1 = new BillingPreference();
            EntityDataGenerator.generateTestDataForModelClass(pref1);
            pref1.setProviderNo("400001");
            pref1.setReferral(1);
            billingPreferencesDAO.persist(pref1);

            BillingPreference pref2 = new BillingPreference();
            EntityDataGenerator.generateTestDataForModelClass(pref2);
            pref2.setProviderNo("400002");
            pref2.setReferral(2);
            billingPreferencesDAO.persist(pref2);

            BillingPreference found = billingPreferencesDAO.getUserBillingPreference("400002");
            assertThat(found).isNotNull();
            assertThat(found.getProviderNo()).isEqualTo("400002");
            assertThat(found.getReferral()).isEqualTo(2);
        }
    }
}
