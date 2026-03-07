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
import io.github.carlos_emr.carlos.commn.model.Relationships;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link RelationshipsDao}.
 *
 * <p>Migrated from legacy {@code RelationshipsDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see RelationshipsDao
 */
@DisplayName("Relationships Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("demographic")
@Transactional
public class RelationshipsDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private RelationshipsDao dao;

    @Nested
    @DisplayName("Read operations")
    class ReadOperations {

        @Test
        @Tag("read")
        @DisplayName("should return all relationships ordered by demographicNo")
        void shouldReturnAllRelationships_whenFindAllCalled() throws Exception {
            int demographicNo1 = 300;
            int demographicNo2 = 100;
            int demographicNo3 = 200;

            Relationships relationships1 = new Relationships();
            EntityDataGenerator.generateTestDataForModelClass(relationships1);
            relationships1.setDemographicNo(demographicNo1);
            dao.persist(relationships1);

            Relationships relationships2 = new Relationships();
            EntityDataGenerator.generateTestDataForModelClass(relationships2);
            relationships2.setDemographicNo(demographicNo2);
            dao.persist(relationships2);

            Relationships relationships3 = new Relationships();
            EntityDataGenerator.generateTestDataForModelClass(relationships3);
            relationships3.setDemographicNo(demographicNo3);
            dao.persist(relationships3);

            List<Relationships> expectedResult = Arrays.asList(relationships2, relationships3, relationships1);
            List<Relationships> result = dao.findAll();

            assertThat(result).hasSize(expectedResult.size());
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }

        @Test
        @Tag("read")
        @DisplayName("should return active relationship by index")
        void shouldReturnActiveRelationship_whenFindActiveCalled() throws Exception {
            Relationships relationships1 = new Relationships();
            EntityDataGenerator.generateTestDataForModelClass(relationships1);
            relationships1.setDeleted(false);
            dao.persist(relationships1);

            Relationships relationships2 = new Relationships();
            EntityDataGenerator.generateTestDataForModelClass(relationships2);
            relationships2.setDeleted(false);
            dao.persist(relationships2);

            Relationships relationships3 = new Relationships();
            EntityDataGenerator.generateTestDataForModelClass(relationships3);
            relationships3.setDeleted(true);
            dao.persist(relationships3);

            Relationships result = dao.findActive(1);

            assertThat(result).isEqualTo(relationships1);
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should return non-deleted relationships by demographic number")
        void shouldReturnRelationships_byDemographicNumber() throws Exception {
            int demographicNo1 = 101;
            int demographicNo2 = 202;

            Relationships relationships1 = new Relationships();
            EntityDataGenerator.generateTestDataForModelClass(relationships1);
            relationships1.setDemographicNo(demographicNo1);
            relationships1.setDeleted(false);
            dao.persist(relationships1);

            Relationships relationships2 = new Relationships();
            EntityDataGenerator.generateTestDataForModelClass(relationships2);
            relationships2.setDemographicNo(demographicNo2);
            relationships2.setDeleted(false);
            dao.persist(relationships2);

            Relationships relationships3 = new Relationships();
            EntityDataGenerator.generateTestDataForModelClass(relationships3);
            relationships3.setDemographicNo(demographicNo1);
            relationships3.setDeleted(true);
            dao.persist(relationships3);

            Relationships relationships4 = new Relationships();
            EntityDataGenerator.generateTestDataForModelClass(relationships4);
            relationships4.setDemographicNo(demographicNo1);
            relationships4.setDeleted(false);
            dao.persist(relationships4);

            List<Relationships> expectedResult = Arrays.asList(relationships1, relationships4);
            List<Relationships> result = dao.findByDemographicNumber(demographicNo1);

            assertThat(result).hasSize(expectedResult.size());
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }

        @Test
        @Tag("query")
        @DisplayName("should return active sub decision makers by demographic number")
        void shouldReturnActiveSubDecisionMakers_byDemographicNumber() throws Exception {
            int demographicNo1 = 101;
            int demographicNo2 = 202;

            String subDecisionMaker1 = "1";
            String subDecisionMaker2 = "FALSE";

            Relationships relationships1 = new Relationships();
            EntityDataGenerator.generateTestDataForModelClass(relationships1);
            relationships1.setDemographicNo(demographicNo1);
            relationships1.setSubDecisionMaker(subDecisionMaker1);
            relationships1.setDeleted(false);
            dao.persist(relationships1);

            Relationships relationships2 = new Relationships();
            EntityDataGenerator.generateTestDataForModelClass(relationships2);
            relationships2.setDemographicNo(demographicNo2);
            relationships2.setSubDecisionMaker(subDecisionMaker2);
            relationships2.setDeleted(false);
            dao.persist(relationships2);

            Relationships relationships3 = new Relationships();
            EntityDataGenerator.generateTestDataForModelClass(relationships3);
            relationships3.setDemographicNo(demographicNo1);
            relationships3.setSubDecisionMaker(subDecisionMaker1);
            relationships3.setDeleted(true);
            dao.persist(relationships3);

            Relationships relationships4 = new Relationships();
            EntityDataGenerator.generateTestDataForModelClass(relationships4);
            relationships4.setDemographicNo(demographicNo1);
            relationships4.setSubDecisionMaker(subDecisionMaker1);
            relationships4.setDeleted(false);
            dao.persist(relationships4);

            List<Relationships> expectedResult = Arrays.asList(relationships1, relationships4);
            List<Relationships> result = dao.findActiveSubDecisionMaker(demographicNo1);

            assertThat(result).hasSize(expectedResult.size());
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }

        @Test
        @Tag("query")
        @DisplayName("should return active relationships by demographic number and facility")
        void shouldReturnActiveRelationships_byDemographicNumberAndFacility() throws Exception {
            int demographicNo1 = 101;
            int demographicNo2 = 202;

            int facilityId1 = 111;
            int facilityId2 = 222;

            Relationships relationships1 = new Relationships();
            EntityDataGenerator.generateTestDataForModelClass(relationships1);
            relationships1.setDemographicNo(demographicNo1);
            relationships1.setFacilityId(facilityId1);
            relationships1.setDeleted(false);
            dao.persist(relationships1);

            Relationships relationships2 = new Relationships();
            EntityDataGenerator.generateTestDataForModelClass(relationships2);
            relationships2.setDemographicNo(demographicNo2);
            relationships2.setFacilityId(facilityId2);
            relationships2.setDeleted(false);
            dao.persist(relationships2);

            Relationships relationships3 = new Relationships();
            EntityDataGenerator.generateTestDataForModelClass(relationships3);
            relationships3.setDemographicNo(demographicNo1);
            relationships3.setFacilityId(facilityId1);
            relationships3.setDeleted(true);
            dao.persist(relationships3);

            Relationships relationships4 = new Relationships();
            EntityDataGenerator.generateTestDataForModelClass(relationships4);
            relationships4.setDemographicNo(demographicNo1);
            relationships4.setFacilityId(facilityId1);
            relationships4.setDeleted(false);
            dao.persist(relationships4);

            List<Relationships> expectedResult = Arrays.asList(relationships1, relationships4);
            List<Relationships> result = dao.findActiveByDemographicNumberAndFacility(demographicNo1, facilityId1);

            assertThat(result).hasSize(expectedResult.size());
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }
    }
}
