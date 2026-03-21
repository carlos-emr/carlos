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
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderSite;
import io.github.carlos_emr.carlos.commn.model.ProviderSitePK;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link SiteDao} covering persist, find, and
 * many-to-many provider relationships.
 *
 * <p>Migrated from legacy {@code SiteDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see SiteDao
 */
@DisplayName("SiteDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("site")
@Transactional
public class SiteDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private SiteDao siteDao;

    @Autowired
    private ProviderDao providerDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Autowired
    private ProviderSiteDao providerSiteDao;

    private Site createSite(String shortName) throws Exception {
        Site entity = new Site();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setStatus((byte) 1);
        entity.setShortName(shortName);
        siteDao.persist(entity);
        return entity;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist site with generated ID")
        void shouldPersistSite_whenValidDataProvided() throws Exception {
            Site entity = createSite("test1");
            hibernateTemplate.flush();

            assertThat(entity.getId()).isNotNull();
            assertThat(siteDao.find(entity.getId())).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find site by ID after persist")
        void shouldFindSite_whenValidIdProvided() throws Exception {
            Site saved = createSite("find1");
            hibernateTemplate.flush();

            Site found = siteDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getShortName()).isEqualTo("find1");
        }
    }

    @Nested
    @DisplayName("Many-to-many provider relationship")
    class ManyToManyRelationship {

        @Test
        @Tag("read")
        @DisplayName("should load providers associated with site via providersite join table")
        void shouldLoadProviders_whenSiteHasAssociatedProviders() throws Exception {
            Site site1 = createSite("name1");
            Site site2 = createSite("name2");
            hibernateTemplate.flush();

            Integer siteId1 = site1.getId();
            Integer siteId2 = site2.getId();

            // Create a provider
            Provider p = new Provider();
            p.setProviderNo("000001");
            p.setLastName("Smith");
            p.setFirstName("John");
            p.setProviderType("doctor");
            p.setDob(new Date());
            p.setLastUpdateUser("999998");
            p.setLastUpdateDate(new Date());
            p.setSex("M");
            p.setSpecialty("");
            providerDao.saveProvider(p);
            hibernateTemplate.flush();

            // Associate provider with both sites
            ProviderSite ps1 = new ProviderSite();
            ps1.setId(new ProviderSitePK());
            ps1.getId().setProviderNo(p.getProviderNo());
            ps1.getId().setSiteId(siteId1);
            providerSiteDao.persist(ps1);

            ProviderSite ps2 = new ProviderSite();
            ps2.setId(new ProviderSitePK());
            ps2.getId().setProviderNo(p.getProviderNo());
            ps2.getId().setSiteId(siteId2);
            providerSiteDao.persist(ps2);

            entityManager.flush();
            entityManager.clear();

            Site s = siteDao.find(siteId1);
            assertThat(s.getProviders()).isNotNull();
            assertThat(s.getProviders()).hasSize(1);
        }
    }
}
