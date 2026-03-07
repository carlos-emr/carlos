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

import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Msh;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Obr;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Obx;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Pid;
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
 * Integration tests for {@link Hl7ObrDao}.
 * <p>Migrated from legacy JUnit 4 Hl7ObrDaoTest with full method coverage.</p>
 *
 * @since 2026-03-07
 */
@DisplayName("Hl7ObrDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class Hl7ObrDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private Hl7ObrDao dao;

    @Autowired
    private Hl7PidDao pidDao;

    @Autowired
    private Hl7ObxDao obxDao;

    @Autowired
    private Hl7MshDao mshDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist entity with generated test data")
        void shouldPersistEntity_whenValidDataProvided() {
            Hl7Obr entity = new Hl7Obr();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find entity by ID with correct fields")
        void shouldReturnMatchingEntity_whenFoundById() {
            Hl7Obr entity = new Hl7Obr();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setPidId(555);
            entity.setResultStatus("F");
            entity.setDiagnosticServiceSectId("HM");
            dao.persist(entity);

            Hl7Obr found = dao.find(entity.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(entity.getId());
            assertThat(found.getPidId()).isEqualTo(555);
            assertThat(found.getResultStatus()).isEqualTo("F");
            assertThat(found.getDiagnosticServiceSectId()).isEqualTo("HM");
        }
    }

    @Nested
    @DisplayName("findByPid")
    class FindByPid {

        @Test
        @Tag("read")
        @DisplayName("should return OBR records matching the given PID")
        void shouldReturnMatchingRecords_whenPidMatches() {
            Hl7Obr matching1 = new Hl7Obr();
            EntityDataGenerator.generateTestDataForModelClass(matching1);
            matching1.setPidId(700);
            dao.persist(matching1);

            Hl7Obr matching2 = new Hl7Obr();
            EntityDataGenerator.generateTestDataForModelClass(matching2);
            matching2.setPidId(700);
            dao.persist(matching2);

            Hl7Obr nonMatching = new Hl7Obr();
            EntityDataGenerator.generateTestDataForModelClass(nonMatching);
            nonMatching.setPidId(701);
            dao.persist(nonMatching);

            List<Hl7Obr> results = dao.findByPid(700);

            assertThat(results).hasSize(2);
            assertThat(results).extracting(Hl7Obr::getPidId)
                    .containsOnly(700);
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no OBR records match the PID")
        void shouldReturnEmptyList_whenNoPidMatches() {
            List<Hl7Obr> results = dao.findByPid(99999);
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findLabResultsByPid")
    class FindLabResultsByPid {

        @Test
        @Tag("read")
        @DisplayName("should return joined OBR and OBX records for a given PID")
        void shouldReturnJoinedResults_whenMatchingDataExists() {
            Hl7Obr obr = new Hl7Obr();
            EntityDataGenerator.generateTestDataForModelClass(obr);
            obr.setPidId(800);
            obr.setDiagnosticServiceSectId("CH");
            dao.persist(obr);

            Hl7Obx obx = new Hl7Obx();
            EntityDataGenerator.generateTestDataForModelClass(obx);
            obx.setObrId(obr.getId());
            obxDao.persist(obx);

            List<Object[]> results = dao.findLabResultsByPid(800);

            assertThat(results).hasSize(1);
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no matching lab results")
        void shouldReturnEmptyList_whenNoMatchingLabResults() {
            List<Object[]> results = dao.findLabResultsByPid(99999);
            assertThat(results).isEmpty();
        }
    }
}
