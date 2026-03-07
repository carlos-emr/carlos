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
import io.github.carlos_emr.carlos.billing.CA.BC.model.CtlServiceCodesDxCodes;
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
 * Integration tests for {@link CtlServiceCodesDxCodesDao}.
 * <p>Migrated from legacy JUnit 4 / DaoTestFixtures.</p>
 * @since 2026-03-07
 */
@DisplayName("CtlServiceCodesDxCodes Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class CtlServiceCodesDxCodesDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CtlServiceCodesDxCodesDao ctlServiceCodesDxCodesDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist entity with generated ID")
        void shouldPersist_whenValidDataProvided() {
            CtlServiceCodesDxCodes entity = new CtlServiceCodesDxCodes();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setServiceCode("SVC100");
            entity.setDxCode("DX200");
            ctlServiceCodesDxCodesDao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find entity by ID with matching field values")
        void shouldReturnMatchingEntity_whenFoundById() {
            CtlServiceCodesDxCodes saved = new CtlServiceCodesDxCodes();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            saved.setServiceCode("SVC101");
            saved.setDxCode("DX201");
            ctlServiceCodesDxCodesDao.persist(saved);

            CtlServiceCodesDxCodes found = ctlServiceCodesDxCodesDao.find(saved.getId());
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getServiceCode()).isEqualTo("SVC101");
            assertThat(found.getDxCode()).isEqualTo("DX201");
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when entity not found by ID")
        void shouldReturnNull_whenInvalidIdProvided() {
            CtlServiceCodesDxCodes found = ctlServiceCodesDxCodesDao.find(-999);
            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @Tag("read")
        @DisplayName("should return all persisted entities")
        void shouldReturnAllEntities_whenMultipleExist() {
            CtlServiceCodesDxCodes e1 = new CtlServiceCodesDxCodes();
            EntityDataGenerator.generateTestDataForModelClass(e1);
            e1.setServiceCode("ALL1");
            e1.setDxCode("DXA");
            ctlServiceCodesDxCodesDao.persist(e1);

            CtlServiceCodesDxCodes e2 = new CtlServiceCodesDxCodes();
            EntityDataGenerator.generateTestDataForModelClass(e2);
            e2.setServiceCode("ALL2");
            e2.setDxCode("DXB");
            ctlServiceCodesDxCodesDao.persist(e2);

            List<CtlServiceCodesDxCodes> results = ctlServiceCodesDxCodesDao.findAll();
            assertThat(results).hasSize(2);
            assertThat(results).extracting(CtlServiceCodesDxCodes::getServiceCode)
                    .containsExactlyInAnyOrder("ALL1", "ALL2");
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no entities exist")
        void shouldReturnEmptyList_whenNoEntitiesExist() {
            List<CtlServiceCodesDxCodes> results = ctlServiceCodesDxCodesDao.findAll();
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByServiceCode")
    class FindByServiceCode {

        @Test
        @Tag("read")
        @DisplayName("should return entities matching the service code")
        void shouldReturnEntities_whenServiceCodeMatches() {
            CtlServiceCodesDxCodes match1 = new CtlServiceCodesDxCodes();
            EntityDataGenerator.generateTestDataForModelClass(match1);
            match1.setServiceCode("MATCH");
            match1.setDxCode("DX1");
            ctlServiceCodesDxCodesDao.persist(match1);

            CtlServiceCodesDxCodes match2 = new CtlServiceCodesDxCodes();
            EntityDataGenerator.generateTestDataForModelClass(match2);
            match2.setServiceCode("MATCH");
            match2.setDxCode("DX2");
            ctlServiceCodesDxCodesDao.persist(match2);

            CtlServiceCodesDxCodes nonMatch = new CtlServiceCodesDxCodes();
            EntityDataGenerator.generateTestDataForModelClass(nonMatch);
            nonMatch.setServiceCode("OTHER");
            nonMatch.setDxCode("DX3");
            ctlServiceCodesDxCodesDao.persist(nonMatch);

            List<CtlServiceCodesDxCodes> results = ctlServiceCodesDxCodesDao.findByServiceCode("MATCH");
            assertThat(results).hasSize(2);
            assertThat(results).extracting(CtlServiceCodesDxCodes::getDxCode)
                    .containsExactlyInAnyOrder("DX1", "DX2");
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no entities match service code")
        void shouldReturnEmptyList_whenNoServiceCodeMatches() {
            CtlServiceCodesDxCodes entity = new CtlServiceCodesDxCodes();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setServiceCode("EXISTS");
            entity.setDxCode("DX1");
            ctlServiceCodesDxCodesDao.persist(entity);

            List<CtlServiceCodesDxCodes> results = ctlServiceCodesDxCodesDao.findByServiceCode("NONEXISTENT");
            assertThat(results).isEmpty();
        }
    }
}
