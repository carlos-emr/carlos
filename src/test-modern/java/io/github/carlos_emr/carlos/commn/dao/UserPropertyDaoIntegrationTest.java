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
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link UserPropertyDAO}.
 *
 * <p>Migrated from legacy {@code UserPropertyDAOTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see UserPropertyDAO
 */
@DisplayName("UserProperty Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class UserPropertyDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private UserPropertyDAO dao;

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should get property by provider number and name")
        void shouldGetProperty_byProviderNoAndName() throws Exception {
            String name1 = "alpha";
            String name2 = "bravo";
            String providerNo1 = "100";
            String providerNo2 = "200";

            UserProperty userProperty1 = new UserProperty();
            EntityDataGenerator.generateTestDataForModelClass(userProperty1);
            userProperty1.setName(name1);
            userProperty1.setProviderNo(providerNo1);
            dao.persist(userProperty1);

            UserProperty userProperty2 = new UserProperty();
            EntityDataGenerator.generateTestDataForModelClass(userProperty2);
            userProperty2.setProviderNo(name2);
            userProperty2.setProviderNo(providerNo2);
            dao.persist(userProperty2);

            UserProperty result = dao.getProp(providerNo1, name1);

            assertThat(result).isEqualTo(userProperty1);
        }

        @Test
        @Tag("query")
        @DisplayName("should get property by name")
        void shouldGetProperty_byName() throws Exception {
            String name1 = "alpha";
            String name2 = "bravo";

            UserProperty userProperty1 = new UserProperty();
            EntityDataGenerator.generateTestDataForModelClass(userProperty1);
            userProperty1.setName(name1);
            dao.persist(userProperty1);

            UserProperty userProperty2 = new UserProperty();
            EntityDataGenerator.generateTestDataForModelClass(userProperty2);
            userProperty2.setProviderNo(name2);
            dao.persist(userProperty2);

            UserProperty result = dao.getProp(name1);

            assertThat(result).isEqualTo(userProperty1);
        }

        @Test
        @Tag("query")
        @DisplayName("should get demographic properties by provider number")
        void shouldGetDemographicProperties_byProviderNo() throws Exception {
            String providerNo1 = "100";
            String providerNo2 = "200";

            UserProperty userProperty1 = new UserProperty();
            EntityDataGenerator.generateTestDataForModelClass(userProperty1);
            userProperty1.setProviderNo(providerNo1);
            dao.persist(userProperty1);

            UserProperty userProperty2 = new UserProperty();
            EntityDataGenerator.generateTestDataForModelClass(userProperty2);
            userProperty2.setProviderNo(providerNo2);
            dao.persist(userProperty2);

            UserProperty userProperty3 = new UserProperty();
            EntityDataGenerator.generateTestDataForModelClass(userProperty3);
            userProperty3.setProviderNo(providerNo1);
            dao.persist(userProperty3);

            List<UserProperty> expectedResult = Arrays.asList(userProperty1, userProperty3);
            List<UserProperty> result = dao.getDemographicProperties(providerNo1);

            assertThat(result).hasSize(expectedResult.size());
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }

        @Test
        @Tag("query")
        @DisplayName("should get provider properties as map")
        void shouldGetProviderProperties_asMap() throws Exception {
            String providerNo1 = "100";
            String providerNo2 = "200";
            String name1 = "alpha";
            String name2 = "bravo";
            String value1 = "Value1";
            String value2 = "Value2";

            UserProperty userProperty1 = new UserProperty();
            EntityDataGenerator.generateTestDataForModelClass(userProperty1);
            userProperty1.setProviderNo(providerNo1);
            userProperty1.setName(name1);
            userProperty1.setValue(value1);
            dao.persist(userProperty1);

            UserProperty userProperty2 = new UserProperty();
            EntityDataGenerator.generateTestDataForModelClass(userProperty2);
            userProperty2.setProviderNo(providerNo2);
            userProperty1.setName(name2);
            userProperty1.setValue(value2);
            dao.persist(userProperty2);

            Map<String, String> expectedResult = new HashMap<>();
            Map<String, String> result = dao.getProviderPropertiesAsMap(providerNo1);

            expectedResult.put(name1, value1);

            assertThat(result).isEqualTo(expectedResult);
        }
    }
}
