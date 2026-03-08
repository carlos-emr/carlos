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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.CtlRelationships;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link CtlRelationshipsDao} covering create,
 * findAllActive, and findByValue.
 *
 * <p>Migrated from legacy {@code CtlRelationshipsDaoTest}
 * (JUnit 4 / DaoTestFixtures) with exact same test logic and assertions.</p>
 *
 * @since 2026-03-07
 * @see CtlRelationshipsDao
 */
@DisplayName("CtlRelationshipsDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class CtlRelationshipsDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CtlRelationshipsDao dao;

    @Nested
    @DisplayName("create tests")
    @Tag("create")
    class Create {

        @Test
        @DisplayName("should persist entity with generated id")
        void shouldPersistEntity_withGeneratedId() throws Exception {
            CtlRelationships entity = new CtlRelationships();
            entity.setValue("value");
            entity.setLabel("label");
            dao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }
    }

    @Nested
    @DisplayName("findAllActive tests")
    @Tag("read")
    class FindAllActive {

        @Test
        @DisplayName("should return only active relationships")
        void shouldReturnOnlyActiveRelationships() throws Exception {
            boolean isActive = true;

            CtlRelationships ctlRelation1 = new CtlRelationships();
            EntityDataGenerator.generateTestDataForModelClass(ctlRelation1);
            ctlRelation1.setActive(isActive);
            dao.persist(ctlRelation1);

            CtlRelationships ctlRelation2 = new CtlRelationships();
            EntityDataGenerator.generateTestDataForModelClass(ctlRelation2);
            ctlRelation2.setActive(!isActive);
            dao.persist(ctlRelation2);

            CtlRelationships ctlRelation3 = new CtlRelationships();
            EntityDataGenerator.generateTestDataForModelClass(ctlRelation3);
            ctlRelation3.setActive(isActive);
            dao.persist(ctlRelation3);
            hibernateTemplate.flush();

            List<CtlRelationships> expectedResult = Arrays.asList(ctlRelation1, ctlRelation3);
            List<CtlRelationships> result = dao.findAllActive();

            assertThat(result).hasSameSizeAs(expectedResult);
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }
    }

    @Nested
    @DisplayName("findByValue tests")
    @Tag("read")
    class FindByValue {

        @Test
        @DisplayName("should return active relationship matching value")
        void shouldReturnActiveRelationship_whenValueMatches() throws Exception {
            boolean isActive = true;
            String value1 = "alpha";
            String value2 = "bravo";

            CtlRelationships ctlRelation1 = new CtlRelationships();
            EntityDataGenerator.generateTestDataForModelClass(ctlRelation1);
            ctlRelation1.setActive(!isActive);
            ctlRelation1.setValue(value1);
            dao.persist(ctlRelation1);

            CtlRelationships ctlRelation2 = new CtlRelationships();
            EntityDataGenerator.generateTestDataForModelClass(ctlRelation2);
            ctlRelation2.setActive(isActive);
            ctlRelation2.setValue(value2);
            dao.persist(ctlRelation2);

            CtlRelationships ctlRelation3 = new CtlRelationships();
            EntityDataGenerator.generateTestDataForModelClass(ctlRelation3);
            ctlRelation3.setActive(isActive);
            ctlRelation3.setValue(value1);
            dao.persist(ctlRelation3);
            hibernateTemplate.flush();

            CtlRelationships result = dao.findByValue(value1);
            assertThat(result).isEqualTo(ctlRelation3);
        }
    }
}
