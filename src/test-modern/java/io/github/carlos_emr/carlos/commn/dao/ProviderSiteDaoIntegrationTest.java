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
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderSite;
import io.github.carlos_emr.carlos.commn.model.ProviderSitePK;
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
 * Integration tests for {@link ProviderSiteDao} covering create,
 * findByProviderNo, and findActiveProvidersWithSites.
 *
 * <p>Migrated from legacy {@code ProviderSiteDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ProviderSiteDao
 */
@DisplayName("ProviderSite Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("provider")
@Transactional
public class ProviderSiteDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProviderSiteDao dao;

    @Nested
    @DisplayName("Create operations")
    class CreateOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist provider site with composite key")
        void shouldPersistProviderSite_whenValidDataProvided() throws Exception {
            ProviderSite entity = new ProviderSite();
            entity.setId(new ProviderSitePK());
            entity.getId().setProviderNo("000001");
            entity.getId().setSiteId(1);
            dao.persist(entity);

            assertThat(entity.getId()).isNotNull();
            ProviderSite found = dao.find(entity.getId());
            assertThat(found).isNotNull();
            assertThat(found.getId().getProviderNo()).isEqualTo("000001");
            assertThat(found.getId().getSiteId()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("findByProviderNo")
    class FindByProviderNo {

        @Test
        @Tag("read")
        @DisplayName("should return sites for the given provider number")
        void shouldReturnSites_whenProviderNoMatches() throws Exception {
            String providerNo1 = "101";
            String providerNo2 = "202";

            ProviderSite ps1 = new ProviderSite();
            EntityDataGenerator.generateTestDataForModelClass(ps1);
            ps1.setId(new ProviderSitePK());
            ps1.getId().setProviderNo(providerNo1);
            dao.persist(ps1);

            ProviderSite ps2 = new ProviderSite();
            EntityDataGenerator.generateTestDataForModelClass(ps2);
            ps2.setId(new ProviderSitePK());
            ps2.getId().setProviderNo(providerNo2);
            dao.persist(ps2);

            List<ProviderSite> result = dao.findByProviderNo(providerNo1);

            assertThat(result).hasSize(1);
            assertThat(result).containsExactly(ps1);
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no sites match provider number")
        void shouldReturnEmptyList_whenNoSitesForProvider() throws Exception {
            List<ProviderSite> result = dao.findByProviderNo("999999");
            assertThat(result).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return multiple sites for same provider")
        void shouldReturnMultipleSites_whenProviderHasMultiple() throws Exception {
            String providerNo = "303";

            ProviderSite ps1 = new ProviderSite();
            EntityDataGenerator.generateTestDataForModelClass(ps1);
            ps1.setId(new ProviderSitePK());
            ps1.getId().setProviderNo(providerNo);
            ps1.getId().setSiteId(10);
            dao.persist(ps1);

            ProviderSite ps2 = new ProviderSite();
            EntityDataGenerator.generateTestDataForModelClass(ps2);
            ps2.setId(new ProviderSitePK());
            ps2.getId().setProviderNo(providerNo);
            ps2.getId().setSiteId(20);
            dao.persist(ps2);

            List<ProviderSite> result = dao.findByProviderNo(providerNo);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(ps -> ps.getId().getProviderNo())
                    .containsOnly(providerNo);
            assertThat(result).extracting(ps -> ps.getId().getSiteId())
                    .containsExactlyInAnyOrder(10, 20);
        }
    }

    @Nested
    @DisplayName("findActiveProvidersWithSites")
    class FindActiveProvidersWithSites {

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no active providers with sites exist")
        void shouldReturnEmptyList_whenNoActiveProvidersWithSites() throws Exception {
            List<Provider> result = dao.findActiveProvidersWithSites("100");

            assertThat(result).isEmpty();
        }
    }
}
