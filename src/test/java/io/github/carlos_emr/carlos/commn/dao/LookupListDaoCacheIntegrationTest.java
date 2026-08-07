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
import io.github.carlos_emr.carlos.commn.model.LookupList;
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
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cache invalidation regression tests for {@link LookupListDao}. Verifies writes
 * (persist/merge/remove/saveEntity) evict the {@code lookupLists} cache after commit,
 * and confirms the null-key guard on {@code findByName} prevents cache poisoning.
 */
@DisplayName("LookupListDao cache invalidation")
@Tag("integration")
@Tag("dao")
@Tag("cache")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LookupListDaoCacheIntegrationTest extends CarlosTestBase {

    private static final String CACHE_NAME = "lookupLists";

    @Autowired
    private LookupListDao lookupListDao;

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
                LookupList entity = lookupListDao.find(id);
                if (entity != null) {
                    lookupListDao.remove(entity);
                }
            });
        }
    }

    @Test
    @DisplayName("should evict lookupLists cache when persist commits")
    void shouldEvictLookupListsCache_whenPersistCommits() {
        transactionTemplate.executeWithoutResult(status -> lookupListDao.findAllActive());
        assertThat(cacheEntryCount()).isPositive();

        transactionTemplate.executeWithoutResult(status -> {
            LookupList entity = buildLookupList(uniqueName());
            lookupListDao.persist(entity);
            idsToCleanUp.add(entity.getId());
        });

        assertThat(cacheEntryCount())
                .as("persist must evict the lookupLists cache after commit")
                .isZero();
    }

    @Test
    @DisplayName("should evict lookupLists cache when merge commits")
    void shouldEvictLookupListsCache_whenMergeCommits() {
        LookupList seeded = persistSeed(uniqueName());

        transactionTemplate.executeWithoutResult(status -> lookupListDao.findAllActive());
        assertThat(cacheEntryCount()).isPositive();

        transactionTemplate.executeWithoutResult(status -> {
            seeded.setDescription("updated-description");
            lookupListDao.merge(seeded);
        });

        assertThat(cacheEntryCount())
                .as("merge must evict the lookupLists cache after commit")
                .isZero();
    }

    @Test
    @DisplayName("should evict lookupLists cache when remove commits")
    void shouldEvictLookupListsCache_whenRemoveCommits() {
        LookupList seeded = persistSeed(uniqueName());

        transactionTemplate.executeWithoutResult(status -> lookupListDao.findAllActive());
        assertThat(cacheEntryCount()).isPositive();

        transactionTemplate.executeWithoutResult(status -> {
            LookupList managed = lookupListDao.find(seeded.getId());
            lookupListDao.remove(managed);
            idsToCleanUp.remove(seeded.getId());
        });

        assertThat(cacheEntryCount())
                .as("remove must evict the lookupLists cache after commit")
                .isZero();
    }

    @Test
    @DisplayName("should evict lookupLists cache when saveEntity commits")
    void shouldEvictLookupListsCache_whenSaveEntityCommits() {
        transactionTemplate.executeWithoutResult(status -> lookupListDao.findAllActive());
        assertThat(cacheEntryCount()).isPositive();

        transactionTemplate.executeWithoutResult(status -> {
            LookupList saved = lookupListDao.saveEntity(buildLookupList(uniqueName()));
            idsToCleanUp.add(saved.getId());
        });

        assertThat(cacheEntryCount())
                .as("saveEntity must evict the lookupLists cache after commit")
                .isZero();
    }

    @Test
    @DisplayName("should evict lookupLists cache when batchPersist completes")
    void shouldEvictLookupListsCache_whenBatchPersistCompletes() {
        transactionTemplate.executeWithoutResult(status -> lookupListDao.findAllActive());
        assertThat(cacheEntryCount()).isPositive();

        List<LookupList> entities = List.of(buildLookupList(uniqueName()), buildLookupList(uniqueName()));
        lookupListDao.batchPersist(entities);
        entities.forEach(e -> idsToCleanUp.add(e.getId()));

        assertThat(cacheEntryCount())
                .as("batchPersist must evict the lookupLists cache (beforeInvocation = true)")
                .isZero();
    }

    @Test
    @DisplayName("should evict lookupLists cache before batchPersist runs to prevent stale entries on partial failure")
    void shouldEvictLookupListsCache_beforeBatchPersistRuns() {
        transactionTemplate.executeWithoutResult(status -> lookupListDao.findAllActive());
        assertThat(cacheEntryCount()).isPositive();

        // Same regression pattern as AppointmentStatus: null NOT NULL triggers a failure,
        // eviction must have fired BEFORE the body to keep cache coherent with the partial DB state.
        LookupList invalid = buildLookupList(uniqueName());
        invalid.setName(null);

        try {
            lookupListDao.batchPersist(List.of(invalid));
        } catch (RuntimeException expected) {
            // Expected: persistence failure on NOT NULL name
        }

        assertThat(cacheEntryCount())
                .as("batchPersist must evict BEFORE body runs — partial-commit failures otherwise pin stale entries until TTL")
                .isZero();
    }

    @Test
    @DisplayName("should evict lookupLists cache when batchRemove completes")
    void shouldEvictLookupListsCache_whenBatchRemoveCompletes() {
        LookupList first = persistSeed(uniqueName());
        LookupList second = persistSeed(uniqueName());

        transactionTemplate.executeWithoutResult(status -> lookupListDao.findAllActive());
        assertThat(cacheEntryCount()).isPositive();

        lookupListDao.batchRemove(List.of(first, second));
        idsToCleanUp.remove(first.getId());
        idsToCleanUp.remove(second.getId());

        assertThat(cacheEntryCount())
                .as("batchRemove must evict the lookupLists cache")
                .isZero();
    }

    @Test
    @DisplayName("should not cache findByName result when name is null or empty")
    void shouldNotCacheFindByName_whenNameIsNullOrEmpty() {
        transactionTemplate.executeWithoutResult(status -> {
            lookupListDao.findByName(null);
            lookupListDao.findByName("");
        });

        assertThat(cacheEntryCount())
                .as("null/empty keys must not be cached; compare with ProviderDaoImpl.getProviderName null guard")
                .isZero();
    }

    private LookupList persistSeed(String name) {
        LookupList seeded = buildLookupList(name);
        transactionTemplate.executeWithoutResult(status -> {
            lookupListDao.persist(seeded);
            idsToCleanUp.add(seeded.getId());
        });
        return seeded;
    }

    private LookupList buildLookupList(String name) {
        LookupList entity = new LookupList();
        entity.setName(name);
        entity.setListTitle("Cache test list " + name);
        entity.setDescription("seeded for cache invalidation test");
        entity.setActive(true);
        entity.setCreatedBy("cache-test");
        return entity;
    }

    private String uniqueName() {
        return "cacheTestList_" + ThreadLocalRandom.current().nextInt(100000, 999999);
    }

    private void clearCache() {
        org.springframework.cache.Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).as("cache %s should be present in the test context", CACHE_NAME).isNotNull();
        cache.clear();
    }

    @SuppressWarnings("unchecked")
    private long cacheEntryCount() {
        org.springframework.cache.Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();
        return ((Cache<Object, Object>) cache.getNativeCache()).estimatedSize();
    }
}
