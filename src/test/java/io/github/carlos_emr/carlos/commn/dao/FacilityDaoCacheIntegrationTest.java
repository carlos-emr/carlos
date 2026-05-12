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

import com.github.benmanes.caffeine.cache.Cache;
import io.github.carlos_emr.carlos.config.CacheConfig;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FacilityDao cache invalidation")
@Tag("integration")
@Tag("dao")
@Tag("cache")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FacilityDaoCacheIntegrationTest extends CarlosTestBase {

    private static final String CACHE_NAME = CacheConfig.FACILITIES;

    @Autowired
    private FacilityDao facilityDao;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Integer> idsToCleanUp = new ArrayList<>();
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUpTransactionsAndCaches() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        clearCache();
    }

    @AfterEach
    void cleanUpCommittedRows() {
        clearCache();
        for (Integer id : idsToCleanUp) {
            transactionTemplate.executeWithoutResult(status -> {
                Facility entity = facilityDao.find(id);
                if (entity != null) {
                    facilityDao.remove(entity);
                }
            });
        }
    }

    @Test
    @DisplayName("should cache active facility list when called")
    void shouldCacheActiveFacilityList_whenCalled() {
        transactionTemplate.executeWithoutResult(status -> {
            Facility active = buildFacility("cacheFacility_" + randomSuffix(), false);
            facilityDao.persist(active);
            idsToCleanUp.add(active.getId());
        });

        List<Facility> firstResult = transactionTemplate.execute(status -> facilityDao.findAll(true));
        List<Facility> secondResult = transactionTemplate.execute(status -> facilityDao.findAll(true));

        assertThat(cacheEntryCount())
                .as("findAll(true) should populate facilities cache")
                .isPositive();
        assertThat(secondResult)
                .as("second findAll(true) call should be served from the facilities cache")
                .isSameAs(firstResult);
    }

    @Test
    @DisplayName("should evict facilities cache when merge commits")
    void shouldEvictFacilitiesCache_whenMergeCommits() {
        Facility seeded = persistSeed("cacheFacility_" + randomSuffix(), false);

        transactionTemplate.executeWithoutResult(status -> facilityDao.findAll(true));
        assertThat(cacheEntryCount()).isPositive();

        transactionTemplate.executeWithoutResult(status -> {
            seeded.setDescription("updated-description");
            facilityDao.merge(seeded);
        });

        assertThat(cacheEntryCount())
                .as("merge must evict facilities cache after commit")
                .isZero();
    }

    private Facility persistSeed(String name, boolean disabled) {
        Facility seeded = buildFacility(name, disabled);
        transactionTemplate.executeWithoutResult(status -> {
            facilityDao.persist(seeded);
            idsToCleanUp.add(seeded.getId());
        });
        return seeded;
    }

    private Facility buildFacility(String name, boolean disabled) {
        Facility entity = new Facility();
        entity.setName(name);
        entity.setDescription("cache test");
        entity.setDisabled(disabled);
        return entity;
    }

    private String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void clearCache() {
        org.springframework.cache.Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).as("cache %s should exist", CACHE_NAME).isNotNull();
        cache.clear();
    }

    @SuppressWarnings("unchecked")
    private long cacheEntryCount() {
        org.springframework.cache.Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();
        Cache<Object, Object> nativeCache = (Cache<Object, Object>) cache.getNativeCache();
        return nativeCache.asMap().size();
    }
}
