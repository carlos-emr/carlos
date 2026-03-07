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
import io.github.carlos_emr.carlos.commn.model.WorkFlow;
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
 * Integration tests for {@link WorkFlowDao}.
 *
 * <p>Migrated from legacy {@code WorkFlowDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see WorkFlowDao
 */
@DisplayName("WorkFlow Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class WorkFlowDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private WorkFlowDao dao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist workflow with generated ID")
        void shouldPersistWorkFlow_whenValidDataProvided() throws Exception {
            WorkFlow entity = new WorkFlow();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find workflows by workflow type")
        void shouldFindWorkFlows_byWorkflowType() throws Exception {
            String workflowType1 = "alpha";
            String workflowType2 = "bravo";

            WorkFlow workFlow1 = new WorkFlow();
            EntityDataGenerator.generateTestDataForModelClass(workFlow1);
            workFlow1.setWorkflowType(workflowType1);
            dao.persist(workFlow1);

            WorkFlow workFlow2 = new WorkFlow();
            EntityDataGenerator.generateTestDataForModelClass(workFlow2);
            workFlow2.setWorkflowType(workflowType2);
            dao.persist(workFlow2);

            WorkFlow workFlow3 = new WorkFlow();
            EntityDataGenerator.generateTestDataForModelClass(workFlow3);
            workFlow3.setWorkflowType(workflowType1);
            dao.persist(workFlow3);

            List<WorkFlow> expectedResult = Arrays.asList(workFlow1, workFlow3);
            List<WorkFlow> result = dao.findByWorkflowType(workflowType1);

            assertThat(result).hasSize(expectedResult.size());
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }

        @Test
        @Tag("query")
        @DisplayName("should find active workflows by workflow type excluding completed state")
        void shouldFindActiveWorkFlows_byWorkflowType() throws Exception {
            String workflowType1 = "alpha";
            String workflowType2 = "bravo";
            String currentState1 = "C";
            String currentState2 = "B";

            WorkFlow workFlow1 = new WorkFlow();
            EntityDataGenerator.generateTestDataForModelClass(workFlow1);
            workFlow1.setWorkflowType(workflowType1);
            workFlow1.setCurrentState(currentState2);
            dao.persist(workFlow1);

            WorkFlow workFlow2 = new WorkFlow();
            EntityDataGenerator.generateTestDataForModelClass(workFlow2);
            workFlow2.setWorkflowType(workflowType2);
            workFlow2.setCurrentState(currentState1);
            dao.persist(workFlow2);

            WorkFlow workFlow3 = new WorkFlow();
            EntityDataGenerator.generateTestDataForModelClass(workFlow3);
            workFlow3.setWorkflowType(workflowType1);
            workFlow3.setCurrentState(currentState2);
            dao.persist(workFlow3);

            WorkFlow workFlow4 = new WorkFlow();
            EntityDataGenerator.generateTestDataForModelClass(workFlow4);
            workFlow4.setWorkflowType(workflowType1);
            workFlow4.setCurrentState(currentState1);
            dao.persist(workFlow4);

            List<WorkFlow> expectedResult = Arrays.asList(workFlow1, workFlow3);
            List<WorkFlow> result = dao.findActiveByWorkflowType(workflowType1);

            assertThat(result).hasSize(expectedResult.size());
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }

        @Test
        @Tag("query")
        @DisplayName("should find active workflows by workflow type and demographic number")
        void shouldFindActiveWorkFlows_byWorkflowTypeAndDemographicNo() throws Exception {
            String workflowType1 = "alpha";
            String workflowType2 = "bravo";
            String currentState1 = "C";
            String currentState2 = "B";
            String demographicNo1 = "100";
            String demographicNo2 = "200";

            WorkFlow workFlow1 = new WorkFlow();
            EntityDataGenerator.generateTestDataForModelClass(workFlow1);
            workFlow1.setWorkflowType(workflowType1);
            workFlow1.setCurrentState(currentState2);
            workFlow1.setDemographicNo(demographicNo1);
            dao.persist(workFlow1);

            WorkFlow workFlow2 = new WorkFlow();
            EntityDataGenerator.generateTestDataForModelClass(workFlow2);
            workFlow2.setWorkflowType(workflowType2);
            workFlow2.setCurrentState(currentState1);
            workFlow2.setDemographicNo(demographicNo2);
            dao.persist(workFlow2);

            WorkFlow workFlow3 = new WorkFlow();
            EntityDataGenerator.generateTestDataForModelClass(workFlow3);
            workFlow3.setWorkflowType(workflowType1);
            workFlow3.setCurrentState(currentState2);
            workFlow3.setDemographicNo(demographicNo1);
            dao.persist(workFlow3);

            WorkFlow workFlow4 = new WorkFlow();
            EntityDataGenerator.generateTestDataForModelClass(workFlow4);
            workFlow4.setWorkflowType(workflowType1);
            workFlow4.setCurrentState(currentState1);
            workFlow4.setDemographicNo(demographicNo1);
            dao.persist(workFlow4);

            List<WorkFlow> expectedResult = Arrays.asList(workFlow1, workFlow3);
            List<WorkFlow> result = dao.findActiveByWorkflowTypeAndDemographicNo(workflowType1, demographicNo1);

            assertThat(result).hasSize(expectedResult.size());
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }
    }
}
