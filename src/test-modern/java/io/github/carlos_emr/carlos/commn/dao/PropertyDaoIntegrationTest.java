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
import io.github.carlos_emr.carlos.commn.model.Property;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link PropertyDao} covering system/provider
 * property CRUD, name-based lookups, value filtering, and boolean property checks.
 *
 * <p>Migrated from legacy {@code PropertyDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see PropertyDao
 */
@DisplayName("PropertyDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("property")
@Transactional
public class PropertyDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private PropertyDao propertyDao;

    private Property createProperty(String name, String value, String providerNo) {
        Property prop = new Property();
        prop.setName(name);
        prop.setValue(value);
        prop.setProviderNo(providerNo);
        propertyDao.persist(prop);
        return prop;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist property with generated ID")
        void shouldPersistProperty_whenValidDataProvided() {
            Property prop = createProperty("test.setting", "enabled", "999998");
            assertThat(prop.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find property by ID")
        void shouldFindProperty_whenValidIdProvided() {
            Property saved = createProperty("test.read", "value1", "999998");
            Property found = propertyDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getName()).isEqualTo("test.read");
            assertThat(found.getValue()).isEqualTo("value1");
        }
    }

    @Nested
    @DisplayName("Name-based queries")
    class NameBasedQueries {

        @BeforeEach
        void setUp() {
            createProperty("clinic.hours", "9-5", "100001");
            createProperty("clinic.hours", "8-4", "100002");
            createProperty("clinic.name", "TestClinic", "100001");
        }

        @Test
        @Tag("query")
        @DisplayName("should find properties by name")
        void shouldFindProperties_byName() {
            List<Property> results = propertyDao.findByName("clinic.hours");
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should find property by name and provider")
        void shouldFindProperty_byNameAndProvider() {
            List<Property> results = propertyDao.findByNameAndProvider("clinic.hours", "100001");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getValue()).isEqualTo("9-5");
        }

        @Test
        @Tag("query")
        @DisplayName("should return single property via checkByName")
        void shouldReturnSingleProperty_viaCheckByName() {
            Property result = propertyDao.checkByName("clinic.name");
            assertThat(result).isNotNull();
            assertThat(result.getValue()).isEqualTo("TestClinic");
        }

        @Test
        @Tag("query")
        @DisplayName("should return null for non-existent name")
        void shouldReturnNull_whenNameNotFound() {
            Property result = propertyDao.checkByName("nonexistent.property");
            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should find properties by name and value")
        void shouldFindProperties_byNameAndValue() {
            List<Property> results = propertyDao.findByNameAndValue("clinic.hours", "9-5");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getProviderNo()).isEqualTo("100001");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for non-matching value")
        void shouldReturnEmptyList_whenValueNotMatched() {
            List<Property> results = propertyDao.findByNameAndValue("clinic.hours", "nonexistent");
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("Delete operations")
    class DeleteOperations {

        @Test
        @Tag("delete")
        @DisplayName("should remove property by name")
        void shouldRemoveProperty_byName() {
            createProperty("to.delete", "val", "100001");
            createProperty("to.delete", "val2", "100002");
            propertyDao.removeByName("to.delete");
            List<Property> results = propertyDao.findByName("to.delete");
            assertThat(results).isEmpty();
        }
    }
}
