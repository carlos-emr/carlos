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
package io.github.carlos_emr.carlos.billing.CA.ON.dao;

import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONProc;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BillingONProcDao}.
 *
 * <p>Tests persist and find (inherited from AbstractDaoImpl) methods with
 * meaningful assertions. BillingONProcDao has no custom query methods;
 * only inherited CRUD operations are tested.</p>
 *
 * @since 2026-03-07
 * @see BillingONProcDao
 */
@DisplayName("BillingONProcDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-on")
@Transactional
public class BillingONProcDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingONProcDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() throws Exception {
        BillingONProc entity = new BillingONProc();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should find persisted entity by ID with correct field values")
    void shouldFindEntity_whenValidIdProvided() throws Exception {
        BillingONProc entity = new BillingONProc();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setCreator("TestCreator");
        entity.setAction("TestAction");
        entity.setComment("TestComment");
        dao.persist(entity);

        BillingONProc found = dao.find(entity.getId());

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(entity.getId());
        assertThat(found.getCreator()).isEqualTo("TestCreator");
        assertThat(found.getAction()).isEqualTo("TestAction");
        assertThat(found.getComment()).isEqualTo("TestComment");
    }

    @Test
    @Tag("read")
    @DisplayName("should return null when entity not found by ID")
    void shouldReturnNull_whenEntityNotFoundById() throws Exception {
        BillingONProc found = dao.find(99999);

        assertThat(found).isNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should count all persisted entities")
    void shouldCountAllEntities_afterPersisting() throws Exception {
        BillingONProc entity1 = new BillingONProc();
        EntityDataGenerator.generateTestDataForModelClass(entity1);
        dao.persist(entity1);

        BillingONProc entity2 = new BillingONProc();
        EntityDataGenerator.generateTestDataForModelClass(entity2);
        dao.persist(entity2);

        int count = dao.getCountAll();

        assertThat(count).isEqualTo(2);
    }
}
