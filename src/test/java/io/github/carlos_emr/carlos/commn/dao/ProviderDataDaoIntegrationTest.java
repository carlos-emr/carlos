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
        result.setLastName("Last" + id);
        result.setFirstName("First" + id);
        result.setProviderType("doctor");
        result.setSex("M");
        result.setSpecialty("GP");
        result.setStatus("1");
        return result;
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find providers by type and OHIP number")
        void shouldFindProviders_byTypeAndOhip() throws Exception {
            ProviderData pd = newProvider("10001");
            pd.setProviderType("doctor");
            pd.setOhipNo("OH123");
            pd.setStatus("1");
            dao.persist(pd);

            ProviderData pd2 = newProvider("10002");
            pd2.setProviderType("doctor");
            pd2.setOhipNo("OH456");
            pd2.setStatus("1");
            dao.persist(pd2);
            hibernateTemplate.flush();

            // findByTypeAndOhip uses LIKE, so exact match
            List<ProviderData> data = dao.findByTypeAndOhip("doctor", "OH123");
            assertThat(data).hasSize(1);
            assertThat(data.get(0).getId()).isEqualTo("10001");
            assertThat(data.get(0).getOhipNo()).isEqualTo("OH123");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no providers match type and OHIP")
        void shouldReturnEmptyList_whenNoTypeAndOhipMatch() throws Exception {
            List<ProviderData> data = dao.findByTypeAndOhip("doctor", "NONEXISTENT");
            assertThat(data).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should find active providers by type")
        void shouldFindProviders_byType() throws Exception {
            ProviderData pd = newProvider("20001");
            pd.setProviderType("nurse");
            pd.setStatus("1");
            dao.persist(pd);

            ProviderData pd2 = newProvider("20002");
            pd2.setProviderType("doctor");
            pd2.setStatus("1");
            dao.persist(pd2);
            hibernateTemplate.flush();

            List<ProviderData> data = dao.findByType("nurse");
            assertThat(data).hasSize(1);
            assertThat(data.get(0).getId()).isEqualTo("20001");
            assertThat(data.get(0).getProviderType()).isEqualTo("nurse");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for type with no matching providers")
        void shouldReturnEmptyList_whenNoTypeMatch() throws Exception {
            List<ProviderData> data = dao.findByType("nonexistent_type");
            assertThat(data).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should find providers by first name")
        void shouldFindProviders_byFirstName() throws Exception {
            ProviderData pd = newProvider("30001");
            pd.setFirstName("Alice");
            pd.setLastName("Smith");
            pd.setStatus("1");
            dao.persist(pd);

            ProviderData pd2 = newProvider("30002");
            pd2.setFirstName("Bob");
            pd2.setLastName("Jones");
            pd2.setStatus("1");
            dao.persist(pd2);
            hibernateTemplate.flush();

            // findByName(firstName, lastName, onlyActive) - firstName uses LIKE prefix%
            List<ProviderData> data = dao.findByName("Alice", null, false);
            assertThat(data).hasSize(1);
            assertThat(data.get(0).getFirstName()).isEqualTo("Alice");
        }

        @Test
        @Tag("query")
        @DisplayName("should find providers by last name")
        void shouldFindProviders_byLastName() throws Exception {
            ProviderData pd = newProvider("30003");
            pd.setFirstName("Carol");
            pd.setLastName("Walker");
            pd.setStatus("1");
            dao.persist(pd);
            hibernateTemplate.flush();

            List<ProviderData> data = dao.findByName(null, "Walker", false);
            assertThat(data).hasSize(1);
            assertThat(data.get(0).getLastName()).isEqualTo("Walker");
        }

        @Test
        @Tag("query")
        @DisplayName("should filter only active providers when onlyActive is true")
        void shouldReturnOnlyActive_whenOnlyActiveFlagSet() throws Exception {
            ProviderData active = newProvider("30004");
            active.setFirstName("Dan");
            active.setLastName("Active");
            active.setStatus("1");
            dao.persist(active);

            ProviderData inactive = newProvider("30005");
            inactive.setFirstName("Dan");
            inactive.setLastName("Inactive");
            inactive.setStatus("0");
            dao.persist(inactive);
            hibernateTemplate.flush();

            List<ProviderData> activeOnly = dao.findByName("Dan", null, true);
            assertThat(activeOnly).hasSize(1);
            assertThat(activeOnly.get(0).getId()).isEqualTo("30004");

            List<ProviderData> all = dao.findByName("Dan", null, false);
            assertThat(all).hasSize(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no providers match name")
        void shouldReturnEmptyList_whenNoNameMatches() throws Exception {
            List<ProviderData> data = dao.findByName("Zzzzz", "Yyyyy", false);
            assertThat(data).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should find all providers with active flag filtering")
        void shouldFindAllProviders_withActiveFlag() throws Exception {
            ProviderData active = newProvider("40001");
            active.setStatus("1");
            dao.persist(active);

            ProviderData inactive = newProvider("40002");
            inactive.setStatus("0");
            dao.persist(inactive);
            hibernateTemplate.flush();

            // findAll(false) returns only active (status='1') providers
            List<ProviderData> activeOnly = dao.findAll(false);
            assertThat(activeOnly).extracting(ProviderData::getStatus).containsOnly("1");

            // findAll(true) returns ALL providers (active + inactive)
            List<ProviderData> all = dao.findAll(true);
            assertThat(all.size()).isGreaterThanOrEqualTo(activeOnly.size());
        }

        @Test
        @Tag("query")
        @DisplayName("should return last provider ID")
        void shouldReturnLastId_whenProvidersExist() throws Exception {
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
