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

import io.github.carlos_emr.carlos.billing.CA.BC.model.Wcb;
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
 * Integration tests for {@link WcbDao}.
 * <p>Migrated from legacy JUnit 4 WcbDaoTest with full method coverage.</p>
 *
 * @since 2026-03-07
 */
@DisplayName("WcbDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class WcbDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private WcbDao dao;

    private Wcb createEntity(int billingNo, int demographicNo) {
        Wcb entity = new Wcb();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setBillingNo(billingNo);
        entity.setDemographicNo(demographicNo);
        return entity;
    }

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated test data")
    void shouldPersistEntity_whenValidDataProvided() {
        Wcb entity = new Wcb();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find entity by ID with correct field values")
    void shouldReturnEntity_whenValidIdProvided() {
        Wcb saved = createEntity(1001, 2001);
        dao.persist(saved);

        Wcb found = dao.find(saved.getId());
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getBillingNo()).isEqualTo(1001);
        assertThat(found.getDemographicNo()).isEqualTo(2001);
    }

    @Test
    @Tag("read")
    @DisplayName("should find WCB records by billing number")
    void shouldReturnMatchingRecords_byBillingNo() {
        Wcb match1 = createEntity(5000, 100);
        Wcb match2 = createEntity(5000, 200);
        Wcb noMatch = createEntity(9999, 300);
        dao.persist(match1);
        dao.persist(match2);
        dao.persist(noMatch);

        List<Wcb> results = dao.findByBillingNo(5000);
        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(w -> assertThat(w.getBillingNo()).isEqualTo(5000));
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when billing number not found")
    void shouldReturnEmptyList_whenBillingNoNotFound() {
        Wcb entity = createEntity(5000, 100);
        dao.persist(entity);

        List<Wcb> results = dao.findByBillingNo(77777);
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should find WCB records by demographic number")
    void shouldReturnMatchingRecords_byDemographicNo() {
        Wcb match1 = createEntity(1001, 8888);
        Wcb match2 = createEntity(1002, 8888);
        Wcb noMatch = createEntity(1003, 9999);
        dao.persist(match1);
        dao.persist(match2);
        dao.persist(noMatch);

        List<Wcb> results = dao.findByDemographic(8888);
        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(w -> assertThat(w.getDemographicNo()).isEqualTo(8888));
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when demographic number not found")
    void shouldReturnEmptyList_whenDemographicNoNotFound() {
        Wcb entity = createEntity(1001, 100);
        dao.persist(entity);

        List<Wcb> results = dao.findByDemographic(55555);
        assertThat(results).isEmpty();
    }
}
