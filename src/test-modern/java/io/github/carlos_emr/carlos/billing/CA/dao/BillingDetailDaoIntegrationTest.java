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

import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
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
 * Integration tests for {@link BillingDetailDao}.
 *
 * <p>Migrated from legacy {@code BillingDetailDaoTest} (JUnit 4 / DaoTestFixtures).
 * Tests all DAO methods: findByBillingNo(int), findByBillingNo(Integer),
 * and findByBillingNoAndStatus.</p>
 *
 * @since 2026-03-07
 * @see BillingDetailDao
 */
@DisplayName("BillingDetailDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing")
@Transactional
public class BillingDetailDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingDetailDao dao;

    private BillingDetail createEntity(int billingNo, String status, String serviceCode) throws Exception {
        BillingDetail entity = new BillingDetail();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setBillingNo(billingNo);
        entity.setStatus(status);
        entity.setServiceCode(serviceCode);
        return entity;
    }

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() throws Exception {
        BillingDetail entity = new BillingDetail();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should find billing details by billing number (int)")
    void shouldReturnMatchingRecords_byBillingNoInt() throws Exception {
        BillingDetail match1 = createEntity(3000, "A", "SVC1");
        BillingDetail match2 = createEntity(3000, "B", "SVC2");
        BillingDetail noMatch = createEntity(4000, "A", "SVC3");
        dao.persist(match1);
        dao.persist(match2);
        dao.persist(noMatch);

        List<BillingDetail> results = dao.findByBillingNo(3000);
        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(bd -> assertThat(bd.getBillingNo()).isEqualTo(3000));
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when billing number not found (int)")
    void shouldReturnEmptyList_whenBillingNoIntNotFound() throws Exception {
        BillingDetail entity = createEntity(3000, "A", "SVC1");
        dao.persist(entity);

        List<BillingDetail> results = dao.findByBillingNo(99999);
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should find billing details by billing number and status")
    void shouldReturnMatchingRecords_byBillingNoAndStatus() throws Exception {
        BillingDetail match = createEntity(5000, "PAID", "SVC1");
        BillingDetail diffStatus = createEntity(5000, "PEND", "SVC2");
        BillingDetail diffBilling = createEntity(6000, "PAID", "SVC3");
        dao.persist(match);
        dao.persist(diffStatus);
        dao.persist(diffBilling);

        List<BillingDetail> results = dao.findByBillingNoAndStatus(5000, "PAID");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getBillingNo()).isEqualTo(5000);
        assertThat(results.get(0).getStatus()).isEqualTo("PAID");
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no matching billing number and status")
    void shouldReturnEmptyList_whenNoMatchingBillingNoAndStatus() throws Exception {
        BillingDetail entity = createEntity(5000, "PAID", "SVC1");
        dao.persist(entity);

        List<BillingDetail> results = dao.findByBillingNoAndStatus(5000, "VOID");
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should find billing details by Integer billing number excluding status D")
    void shouldReturnNonDeletedRecords_byBillingNoInteger() throws Exception {
        BillingDetail active = createEntity(7000, "A", "SVC1");
        BillingDetail deleted = createEntity(7000, "D", "SVC2");
        BillingDetail otherBilling = createEntity(8000, "A", "SVC3");
        dao.persist(active);
        dao.persist(deleted);
        dao.persist(otherBilling);

        // findByBillingNo(Integer) excludes status 'D'
        List<BillingDetail> results = dao.findByBillingNo(Integer.valueOf(7000));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStatus()).isNotEqualTo("D");
        assertThat(results.get(0).getBillingNo()).isEqualTo(7000);
    }
}
