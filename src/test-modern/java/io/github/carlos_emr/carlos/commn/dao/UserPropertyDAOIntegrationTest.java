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

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link UserPropertyDAO} covering property lookups
 * by provider number and name, demographic properties, and map-based retrieval.
 *
 * <p>Migrated from legacy {@code UserPropertyDAOTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see UserPropertyDAO
 */
@DisplayName("UserPropertyDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("userProperty")
@Transactional
public class UserPropertyDAOIntegrationTest extends CarlosTestBase {

    @Autowired
    private UserPropertyDAO userPropertyDAO;

    private UserProperty createProperty(String providerNo, String name, String value) {
        UserProperty prop = new UserProperty();
        EntityDataGenerator.generateTestDataForModelClass(prop);
        prop.setProviderNo(providerNo);
        prop.setName(name);
        prop.setValue(value);
        userPropertyDAO.persist(prop);
        return prop;
    }

    @Nested
    @DisplayName("getProp(providerNo, name)")
    class GetPropByProviderAndName {

        @Test
        @Tag("read")
        @DisplayName("should return property matching provider number and name")
        void shouldReturnProperty_whenProviderNoAndNameMatch() {
            UserProperty prop1 = createProperty("100", "alpha", "val1");
            createProperty("200", "bravo", "val2");
            hibernateTemplate.flush();

            UserProperty result = userPropertyDAO.getProp("100", "alpha");

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(prop1.getId());
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when no property matches provider and name")
        void shouldReturnNull_whenNoPropertyMatchesProviderAndName() {
            createProperty("100", "alpha", "val1");
            hibernateTemplate.flush();

            UserProperty result = userPropertyDAO.getProp("999", "nonexistent");
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("getProp(name)")
    class GetPropByName {

        @Test
        @Tag("read")
        @DisplayName("should return property matching name")
        void shouldReturnProperty_whenNameMatches() {
            UserProperty prop1 = createProperty("100", "alpha", "val1");
            createProperty("200", "bravo", "val2");
            hibernateTemplate.flush();

            UserProperty result = userPropertyDAO.getProp("alpha");

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(prop1.getId());
        }
    }

    @Nested
    @DisplayName("getDemographicProperties")
    class GetDemographicProperties {

        @Test
        @Tag("query")
        @DisplayName("should return all properties for a given provider number")
        void shouldReturnAllProperties_forGivenProviderNo() {
            UserProperty prop1 = createProperty("100", "name1", "val1");
            createProperty("200", "name2", "val2");
            UserProperty prop3 = createProperty("100", "name3", "val3");
            hibernateTemplate.flush();

            List<UserProperty> result = userPropertyDAO.getDemographicProperties("100");

            assertThat(result).hasSize(2);
            assertThat(result).extracting(UserProperty::getId)
                    .containsExactlyInAnyOrder(prop1.getId(), prop3.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no properties exist for provider")
        void shouldReturnEmptyList_whenNoPropertiesExistForProvider() {
            createProperty("100", "name1", "val1");
            hibernateTemplate.flush();

            List<UserProperty> result = userPropertyDAO.getDemographicProperties("999");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getProviderPropertiesAsMap")
    class GetProviderPropertiesAsMap {

        @Test
        @Tag("query")
        @DisplayName("should return properties as name-value map for provider")
        void shouldReturnPropertiesAsMap_forGivenProviderNo() {
            createProperty("100", "alpha", "Value1");
            createProperty("100", "bravo", "Value2");
            createProperty("200", "charlie", "Value3");
            hibernateTemplate.flush();

            Map<String, String> result = userPropertyDAO.getProviderPropertiesAsMap("100");

            assertThat(result).hasSize(2);
            assertThat(result).containsEntry("alpha", "Value1");
            assertThat(result).containsEntry("bravo", "Value2");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty map when no properties exist for provider")
        void shouldReturnEmptyMap_whenNoPropertiesExistForProvider() {
            createProperty("100", "alpha", "Value1");
            hibernateTemplate.flush();

            Map<String, String> result = userPropertyDAO.getProviderPropertiesAsMap("999");
            assertThat(result).isEmpty();
        }
    }
}
