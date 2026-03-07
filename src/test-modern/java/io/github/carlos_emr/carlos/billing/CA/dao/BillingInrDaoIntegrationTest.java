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
package io.github.carlos_emr.carlos.billing.CA.dao;

import io.github.carlos_emr.carlos.billing.CA.model.BillingInr;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BillingInrDao}.
 *
 * <p>Tests persist and findCurrentByProviderNo methods. The search_inrbilling_dt_billno
 * method requires a Demographic join and is not tested here to avoid complex
 * cross-entity setup.</p>
 *
 * @since 2026-03-07
 * @see BillingInrDao
 */
@DisplayName("BillingInrDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing")
@Transactional
public class BillingInrDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingInrDao dao;

    private BillingInr createEntity(String providerNo, String status) {
        BillingInr entity = new BillingInr();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setProviderNo(providerNo);
        entity.setStatus(status);
        return entity;
    }

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() {
        BillingInr entity = new BillingInr();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should find entity by ID with correct field values")
    void shouldReturnEntity_whenValidIdProvided() {
        BillingInr saved = createEntity("DR001", "A");
        saved.setServiceCode("SVC01");
        saved.setBillingAmount("100.00");
        dao.persist(saved);

        BillingInr found = dao.find(saved.getId());
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getProviderNo()).isEqualTo("DR001");
        assertThat(found.getStatus()).isEqualTo("A");
        assertThat(found.getServiceCode()).isEqualTo("SVC01");
        assertThat(found.getBillingAmount()).isEqualTo("100.00");
    }

    @Test
    @Tag("read")
    @DisplayName("should find current billing INR records by provider number")
    void shouldReturnCurrentRecords_byProviderNo() {
        BillingInr active1 = createEntity("DR100", "A");
        BillingInr active2 = createEntity("DR100", "B");
        BillingInr deleted = createEntity("DR100", "D");
        BillingInr otherProvider = createEntity("DR200", "A");
        dao.persist(active1);
        dao.persist(active2);
        dao.persist(deleted);
        dao.persist(otherProvider);

        // findCurrentByProviderNo filters by providerNo LIKE and status <> 'D'
        List<BillingInr> results = dao.findCurrentByProviderNo("DR100");
        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(b -> {
            assertThat(b.getProviderNo()).isEqualTo("DR100");
            assertThat(b.getStatus()).isNotEqualTo("D");
        });
    }

    @Test
    @Tag("read")
    @DisplayName("should exclude deleted records from current provider results")
    void shouldExcludeDeletedRecords_whenFindingCurrentByProvider() {
        BillingInr deleted = createEntity("DR300", "D");
        dao.persist(deleted);

        List<BillingInr> results = dao.findCurrentByProviderNo("DR300");
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when provider number not found")
    void shouldReturnEmptyList_whenProviderNoNotFound() {
        BillingInr entity = createEntity("DR100", "A");
        dao.persist(entity);

        List<BillingInr> results = dao.findCurrentByProviderNo("NONEXIST");
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should support LIKE matching on provider number")
    void shouldSupportLikeMatching_byProviderNo() {
        BillingInr entity = createEntity("DR100", "A");
        dao.persist(entity);

        // LIKE with wildcard should match
        List<BillingInr> results = dao.findCurrentByProviderNo("DR%");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getProviderNo()).isEqualTo("DR100");
    }
}
