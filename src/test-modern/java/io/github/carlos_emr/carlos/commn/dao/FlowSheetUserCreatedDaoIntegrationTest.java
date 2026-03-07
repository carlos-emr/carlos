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
import io.github.carlos_emr.carlos.commn.model.FlowSheetUserCreated;
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
 * Integration tests for {@link FlowSheetUserCreatedDao} with full method coverage matching legacy tests.
 *
 * <p>Migrated from legacy {@code FlowSheetUserCreatedDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see FlowSheetUserCreatedDao
 */
@DisplayName("FlowSheetUserCreated Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class FlowSheetUserCreatedDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private FlowSheetUserCreatedDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist flow sheet user created with generated ID")
    void shouldPersistFlowSheetUserCreated_whenValidDataProvided() {
        FlowSheetUserCreated entity = new FlowSheetUserCreated();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return only non-archived flow sheets when getting all user created")
    void shouldReturnNonArchivedFlowSheets_whenGettingAllUserCreated() {
        boolean isArchived = true;

        FlowSheetUserCreated flowSheetUserCreated1 = new FlowSheetUserCreated();
        EntityDataGenerator.generateTestDataForModelClass(flowSheetUserCreated1);
        flowSheetUserCreated1.setArchived(!isArchived);
        dao.persist(flowSheetUserCreated1);

        FlowSheetUserCreated flowSheetUserCreated2 = new FlowSheetUserCreated();
        EntityDataGenerator.generateTestDataForModelClass(flowSheetUserCreated2);
        flowSheetUserCreated2.setArchived(isArchived);
        dao.persist(flowSheetUserCreated2);

        FlowSheetUserCreated flowSheetUserCreated3 = new FlowSheetUserCreated();
        EntityDataGenerator.generateTestDataForModelClass(flowSheetUserCreated3);
        flowSheetUserCreated3.setArchived(!isArchived);
        dao.persist(flowSheetUserCreated3);

        List<FlowSheetUserCreated> expectedResult = Arrays.asList(flowSheetUserCreated1, flowSheetUserCreated3);
        List<FlowSheetUserCreated> result = dao.getAllUserCreatedFlowSheets();

        assertThat(result).hasSize(expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
        }
    }
}
