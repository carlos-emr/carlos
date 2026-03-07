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
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Message;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
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

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link Hl7MessageDao}.
 * <p>Migrated from legacy JUnit 4 / DaoTestFixtures.</p>
 * @since 2026-03-07
 */
@DisplayName("Hl7Message Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class Hl7MessageDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private Hl7MessageDao hl7MessageDao;

    @PersistenceContext(unitName = "testPersistenceUnit")
    private EntityManager entityManager;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist entity with generated ID")
        void shouldPersist_whenValidDataProvided() {
            Hl7Message entity = new Hl7Message();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setNotes("Test HL7 message");
            entity.setDateTime(new Date());
            hl7MessageDao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find entity by ID with matching field values")
        void shouldReturnMatchingEntity_whenFoundById() {
            Hl7Message saved = new Hl7Message();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            saved.setNotes("Important message");
            Date now = new Date();
            saved.setDateTime(now);
            hl7MessageDao.persist(saved);

            Hl7Message found = hl7MessageDao.find(saved.getId());
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getNotes()).isEqualTo("Important message");
            assertThat(found.getDateTime()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when entity not found by ID")
        void shouldReturnNull_whenInvalidIdProvided() {
            Hl7Message found = hl7MessageDao.find(-999);
            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("findByDemographicAndLabType")
    class FindByDemographicAndLabType {

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no routing matches demographic and lab type")
        void shouldReturnEmptyList_whenNoMatchExists() {
            List<Object[]> results = hl7MessageDao.findByDemographicAndLabType(-999, "NONEXISTENT");
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return messages matching demographic and lab type via patient lab routing")
        void shouldReturnMessages_whenRoutingMatchesDemographicAndLabType() {
            Hl7Message msg1 = new Hl7Message();
            EntityDataGenerator.generateTestDataForModelClass(msg1);
            msg1.setNotes("Lab Result A");
            msg1.setDateTime(new Date());
            hl7MessageDao.persist(msg1);

            Hl7Message msg2 = new Hl7Message();
            EntityDataGenerator.generateTestDataForModelClass(msg2);
            msg2.setNotes("Lab Result B");
            msg2.setDateTime(new Date());
            hl7MessageDao.persist(msg2);

            Hl7Message msgOtherDemo = new Hl7Message();
            EntityDataGenerator.generateTestDataForModelClass(msgOtherDemo);
            msgOtherDemo.setNotes("Other Demo Lab");
            msgOtherDemo.setDateTime(new Date());
            hl7MessageDao.persist(msgOtherDemo);
            entityManager.flush();

            // Route msg1 and msg2 to demographic 500 with lab type "HL7"
            PatientLabRouting routing1 = new PatientLabRouting(msg1.getId(), "HL7", 500);
            entityManager.persist(routing1);

            PatientLabRouting routing2 = new PatientLabRouting(msg2.getId(), "HL7", 500);
            entityManager.persist(routing2);

            // Route msgOtherDemo to a different demographic
            PatientLabRouting routing3 = new PatientLabRouting(msgOtherDemo.getId(), "HL7", 600);
            entityManager.persist(routing3);

            entityManager.flush();

            List<Object[]> results = hl7MessageDao.findByDemographicAndLabType(500, "HL7");
            assertThat(results).hasSize(2);

            // Each result is [Hl7Message, PatientLabRouting]
            for (Object[] row : results) {
                Hl7Message foundMsg = (Hl7Message) row[0];
                PatientLabRouting foundRouting = (PatientLabRouting) row[1];
                assertThat(foundRouting.getDemographicNo()).isEqualTo(500);
                assertThat(foundRouting.getLabType()).isEqualTo("HL7");
                assertThat(foundMsg.getNotes()).isIn("Lab Result A", "Lab Result B");
            }
        }

        @Test
        @Tag("read")
        @DisplayName("should not return messages for different lab type")
        void shouldNotReturnMessages_whenLabTypeDiffers() {
            Hl7Message msg = new Hl7Message();
            EntityDataGenerator.generateTestDataForModelClass(msg);
            msg.setNotes("CML Lab");
            msg.setDateTime(new Date());
            hl7MessageDao.persist(msg);
            entityManager.flush();

            PatientLabRouting routing = new PatientLabRouting(msg.getId(), "CML", 700);
            entityManager.persist(routing);
            entityManager.flush();

            List<Object[]> results = hl7MessageDao.findByDemographicAndLabType(700, "HL7");
            assertThat(results).isEmpty();
        }
    }
}
