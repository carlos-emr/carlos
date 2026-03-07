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
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DemographicExtDao} covering demographic
 * extension key-value CRUD, demographic-based lookups, and update operations.
 *
 * <p>Migrated from legacy {@code DemographicExtDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see DemographicExtDao
 */
@DisplayName("DemographicExtDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("demographic")
@Transactional
public class DemographicExtDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DemographicExtDao demographicExtDao;

    private static final int DEMO_1 = 70001;
    private static final int DEMO_2 = 70002;

    private DemographicExt createExt(int demographicNo, String key, String value) {
        DemographicExt ext = new DemographicExt();
        ext.setDemographicNo(demographicNo);
        ext.setProviderNo("999998");
        ext.setKey(key);
        ext.setValue(value);
        demographicExtDao.persist(ext);
        return ext;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist demographic extension with generated ID")
        void shouldPersistDemographicExt_whenValidDataProvided() {
            DemographicExt ext = createExt(DEMO_1, "aboriginal", "yes");
            assertThat(ext.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find demographic extension by ID")
        void shouldFindDemographicExt_whenValidIdProvided() {
            DemographicExt saved = createExt(DEMO_1, "ethnicity", "Canadian");
            DemographicExt found = demographicExtDao.getDemographicExt(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getKey()).isEqualTo("ethnicity");
            assertThat(found.getValue()).isEqualTo("Canadian");
        }

        @Test
        @Tag("update")
        @DisplayName("should update demographic extension value")
        void shouldUpdateDemographicExt_whenModified() {
            DemographicExt saved = createExt(DEMO_1, "language", "en");
            saved.setValue("fr");
            demographicExtDao.updateDemographicExt(saved);
            DemographicExt found = demographicExtDao.getDemographicExt(saved.getId());
            assertThat(found.getValue()).isEqualTo("fr");
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @BeforeEach
        void setUp() {
            createExt(DEMO_1, "aboriginal", "yes");
            createExt(DEMO_1, "ethnicity", "Canadian");
            createExt(DEMO_1, "language", "en");
            createExt(DEMO_2, "aboriginal", "no");
            createExt(DEMO_2, "language", "fr");
        }

        @Test
        @Tag("query")
        @DisplayName("should find extensions by demographic number")
        void shouldFindExtensions_byDemographicNo() {
            List<DemographicExt> results = demographicExtDao.getDemographicExtByDemographicNo(DEMO_1);
            assertThat(results).hasSize(3);
        }

        @Test
        @Tag("query")
        @DisplayName("should find extension by demographic number and key")
        void shouldFindExtension_byDemographicNoAndKey() {
            DemographicExt result = demographicExtDao.getDemographicExt(DEMO_1, "ethnicity");
            assertThat(result).isNotNull();
            assertThat(result.getValue()).isEqualTo("Canadian");
        }

        @Test
        @Tag("query")
        @DisplayName("should find latest extension by demographic number and key")
        void shouldFindLatestExtension_byDemographicNoAndKey() {
            DemographicExt result = demographicExtDao.getLatestDemographicExt(DEMO_1, "language");
            assertThat(result).isNotNull();
            assertThat(result.getValue()).isEqualTo("en");
        }

        @Test
        @Tag("query")
        @DisplayName("should find extensions by key and value")
        void shouldFindExtensions_byKeyAndValue() {
            List<DemographicExt> results = demographicExtDao.getDemographicExtByKeyAndValue("aboriginal", "yes");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getDemographicNo()).isEqualTo(DEMO_1);
        }

        @Test
        @Tag("query")
        @DisplayName("should get all values as map for demographic")
        void shouldGetAllValues_asMapForDemographic() {
            HashMap<String, String> map = (HashMap<String, String>) demographicExtDao.getAllValuesForDemo(DEMO_1);
            assertThat(map).containsEntry("aboriginal", "yes");
            assertThat(map).containsEntry("ethnicity", "Canadian");
            assertThat(map).containsEntry("language", "en");
        }

        @Test
        @Tag("query")
        @DisplayName("should return null for non-existent key")
        void shouldReturnNull_whenKeyNotFound() {
            DemographicExt result = demographicExtDao.getDemographicExt(DEMO_1, "nonexistent");
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Save shortcut operations")
    class SaveShortcutOperations {

        @Test
        @Tag("create")
        @DisplayName("should save demographic ext via shortcut method")
        void shouldSaveDemographicExt_viaShortcutMethod() {
            demographicExtDao.saveDemographicExt(DEMO_1, "test_key", "test_value");
            DemographicExt found = demographicExtDao.getDemographicExt(DEMO_1, "test_key");
            assertThat(found).isNotNull();
            assertThat(found.getValue()).isEqualTo("test_value");
        }
    }
}
