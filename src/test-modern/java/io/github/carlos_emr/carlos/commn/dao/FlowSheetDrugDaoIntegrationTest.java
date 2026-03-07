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
import io.github.carlos_emr.carlos.commn.model.FlowSheetDrug;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link FlowSheetDrugDao} with full method coverage matching legacy tests.
 *
 * <p>Migrated from legacy {@code FlowSheetDrugDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see FlowSheetDrugDao
 */
@DisplayName("FlowSheetDrug Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class FlowSheetDrugDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private FlowSheetDrugDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist flow sheet drug with generated ID")
    void shouldPersistFlowSheetDrug_whenValidDataProvided() {
        FlowSheetDrug entity = new FlowSheetDrug();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return matching non-archived drugs when filtered by flowsheet and demographic")
    void shouldReturnMatchingDrugs_whenFilteredByFlowsheetAndDemographic() {
        String flowsheet1 = "alpha";
        String flowsheet2 = "bravo";
        boolean isArchived = true;
        int demographicNo1 = 111;
        int demographicNo2 = 222;

        FlowSheetDrug flowSheetDrug1 = new FlowSheetDrug();
        EntityDataGenerator.generateTestDataForModelClass(flowSheetDrug1);
        flowSheetDrug1.setFlowsheet(flowsheet1);
        flowSheetDrug1.setArchived(!isArchived);
        flowSheetDrug1.setDemographicNo(demographicNo1);
        dao.persist(flowSheetDrug1);

        FlowSheetDrug flowSheetDrug2 = new FlowSheetDrug();
        EntityDataGenerator.generateTestDataForModelClass(flowSheetDrug2);
        flowSheetDrug2.setFlowsheet(flowsheet2);
        flowSheetDrug2.setArchived(!isArchived);
        flowSheetDrug2.setDemographicNo(demographicNo2);
        dao.persist(flowSheetDrug2);

        FlowSheetDrug flowSheetDrug3 = new FlowSheetDrug();
        EntityDataGenerator.generateTestDataForModelClass(flowSheetDrug3);
        flowSheetDrug3.setFlowsheet(flowsheet1);
        flowSheetDrug3.setArchived(!isArchived);
        flowSheetDrug3.setDemographicNo(demographicNo1);
        dao.persist(flowSheetDrug3);

        List<FlowSheetDrug> expectedResult = Arrays.asList(flowSheetDrug1, flowSheetDrug3);
        List<FlowSheetDrug> result = dao.getFlowSheetDrugs(flowsheet1, demographicNo1);

        assertThat(result).hasSize(expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
        }
    }
}
