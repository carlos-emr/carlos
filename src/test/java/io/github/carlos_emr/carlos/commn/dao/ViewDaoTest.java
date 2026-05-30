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
import io.github.carlos_emr.carlos.commn.model.View;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ViewDao} with full method coverage matching legacy tests.
 *
 * <p>Migrated from legacy {@code ViewDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ViewDao
 */
@DisplayName("View Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class ViewDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ViewDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist view entity with generated ID")
    void shouldPersistView_whenValidDataProvided() throws Exception {
        View entity = new View();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should return view map filtered by view name, role, and provider number")
    void shouldReturnViewMap_whenFilteredByViewNameRoleAndProviderNo() throws Exception {
        String role1 = "alpha";
        String role2 = "bravo";
        String providerNo1 = "000000";
        String viewName1 = "sigma";
        String viewName2 = "delta";
        String name1 = "gamma";
        String name2 = "zeta";

        View view1 = new View();
        EntityDataGenerator.generateTestDataForModelClass(view1);
        view1.setRole(role1);
        view1.setView_name(viewName1);
        view1.setName(name1);
        view1.setProviderNo(providerNo1);
        dao.persist(view1);

        View view2 = new View();
        EntityDataGenerator.generateTestDataForModelClass(view2);
        view2.setRole(role2);
        view2.setView_name(viewName2);
        view2.setName(name2);
        view2.setProviderNo(null);
        dao.persist(view2);

        // With provider number
        Map<String, View> expectedResult = new HashMap<>();
        expectedResult.put(name1, view1);
        Map<String, View> result = dao.getView(viewName1, role1, providerNo1);
        assertThat(result).isEqualTo(expectedResult);

        // Without provider number
        Map<String, View> expectedResult2 = new HashMap<>();
        expectedResult2.put(name2, view2);
        Map<String, View> result2 = dao.getView(viewName2, role2);
        assertThat(result2).isEqualTo(expectedResult2);
    }
}
