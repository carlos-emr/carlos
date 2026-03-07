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

import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Obr;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Obx;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link Hl7ObxDao}.
 * <p>Migrated from legacy JUnit 4 Hl7ObxDaoTest with full method coverage.</p>
 *
 * @since 2026-03-07
 */
@DisplayName("Hl7ObxDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class Hl7ObxDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private Hl7ObxDao dao;

    @Autowired
    private Hl7ObrDao obrDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist entity with generated test data")
        void shouldPersistEntity_whenValidDataProvided() {
            Hl7Obx entity = new Hl7Obx();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);
            assertThat(entity.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find entity by ID with correct fields")
        void shouldReturnMatchingEntity_whenFoundById() {
            Hl7Obx entity = new Hl7Obx();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setObrId(100);
            entity.setAbnormalFlags("H");
            entity.setObservationResultStatus("F");
            dao.persist(entity);

            Hl7Obx found = dao.find(entity.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(entity.getId());
            assertThat(found.getObrId()).isEqualTo(100);
            assertThat(found.getAbnormalFlags()).isEqualTo("H");
            assertThat(found.getObservationResultStatus()).isEqualTo("F");
        }
    }

    @Nested
    @DisplayName("findByObrId")
    class FindByObrId {

        @Test
        @Tag("read")
        @DisplayName("should return OBX records matching the given OBR ID")
        void shouldReturnMatchingRecords_whenObrIdMatches() {
            Hl7Obx match1 = new Hl7Obx();
            EntityDataGenerator.generateTestDataForModelClass(match1);
            match1.setObrId(500);
            dao.persist(match1);

            Hl7Obx match2 = new Hl7Obx();
            EntityDataGenerator.generateTestDataForModelClass(match2);
            match2.setObrId(500);
            dao.persist(match2);

            Hl7Obx nonMatch = new Hl7Obx();
            EntityDataGenerator.generateTestDataForModelClass(nonMatch);
            nonMatch.setObrId(501);
            dao.persist(nonMatch);

            List<Hl7Obx> results = dao.findByObrId(500);

            assertThat(results).hasSize(2);
            assertThat(results).extracting(Hl7Obx::getObrId)
                    .containsOnly(500);
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no OBX records match")
        void shouldReturnEmptyList_whenNoObrIdMatches() {
            List<Hl7Obx> results = dao.findByObrId(99999);
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findObxAndObrByObrId")
    class FindObxAndObrByObrId {

        @Test
        @Tag("read")
        @DisplayName("should return joined OBX and OBR records for a given OBR ID")
        void shouldReturnJoinedResults_whenMatchingDataExists() {
            Hl7Obr obr = new Hl7Obr();
            EntityDataGenerator.generateTestDataForModelClass(obr);
            obr.setPidId(600);
            obrDao.persist(obr);

            Hl7Obx obx1 = new Hl7Obx();
            EntityDataGenerator.generateTestDataForModelClass(obx1);
            obx1.setObrId(obr.getId());
            dao.persist(obx1);

            Hl7Obx obx2 = new Hl7Obx();
            EntityDataGenerator.generateTestDataForModelClass(obx2);
            obx2.setObrId(obr.getId());
            dao.persist(obx2);

            List<Object[]> results = dao.findObxAndObrByObrId(obr.getId());

            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no matching OBR ID")
        void shouldReturnEmptyList_whenNoMatchingObrId() {
            List<Object[]> results = dao.findObxAndObrByObrId(99999);
            assertThat(results).isEmpty();
        }
    }
}
