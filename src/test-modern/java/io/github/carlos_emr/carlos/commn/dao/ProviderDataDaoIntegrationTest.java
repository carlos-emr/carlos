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
import io.github.carlos_emr.carlos.commn.model.ProviderData;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ProviderDataDao}.
 *
 * <p>Migrated from legacy {@code ProviderDataDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ProviderDataDao
 */
@DisplayName("ProviderData Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("provider")
@Transactional
public class ProviderDataDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProviderDataDao dao;

    private ProviderData newProvider(String id) {
        ProviderData result = new ProviderData();
        result.set(id);
        try {
            EntityDataGenerator.generateTestDataForModelClass(result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find providers by type and OHIP number")
        void shouldFindProviders_byTypeAndOhip() {
            List<ProviderData> data = dao.findByTypeAndOhip("doctor", "OHIP NO");
            assertThat(data).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should find providers by type")
        void shouldFindProviders_byType() {
            List<ProviderData> data = dao.findByType("doctor");
            assertThat(data).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should find providers by name with various parameter combinations")
        void shouldFindProviders_byName() {
            assertThat(dao.findByName(null, null, false)).isNotNull();
            assertThat(dao.findByName(null, null, true)).isNotNull();
            assertThat(dao.findByName(null, "FIRST", true)).isNotNull();
            assertThat(dao.findByName(null, "FIRST", false)).isNotNull();
            assertThat(dao.findByName("LAST", null, false)).isNotNull();
            assertThat(dao.findByName("LAST", null, true)).isNotNull();
            assertThat(dao.findByName("LAST", "FIRST", true)).isNotNull();
            assertThat(dao.findByName("LAST", "FIRST", false)).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should find all providers with active flag")
        void shouldFindAllProviders_withActiveFlag() {
            List<ProviderData> data = dao.findAll(true);
            assertThat(data).isNotNull();

            data = dao.findAll(false);
            assertThat(data).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return last provider ID")
        void shouldReturnLastId_whenProvidersExist() {
            ProviderData pd = newProvider("-1001");
            dao.persist(pd);

            pd = newProvider("-2");
            dao.persist(pd);

            pd = newProvider("1");
            dao.persist(pd);

            Integer id = dao.getLastId();
            assertThat(id).isEqualTo(Integer.valueOf(-1001));
        }
    }
}
