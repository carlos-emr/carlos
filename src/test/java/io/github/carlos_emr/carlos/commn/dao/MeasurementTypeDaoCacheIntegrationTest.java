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
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cache invalidation and isolation regression tests for {@link MeasurementTypeDao}. Verifies
 * writes evict the {@code measurementTypes} cache populated by {@code findAll*} read methods,
 * and that every read returns its own detached copies (issue #5304): changing a returned type,
 * including its creation date, must not reach another reader, the cached snapshot or the
 * database.
 */
@DisplayName("MeasurementTypeDao cache invalidation and isolation")
@Tag("integration")
@Tag("dao")
@Tag("cache")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MeasurementTypeDaoCacheIntegrationTest extends CarlosTestBase {

    private static final String CACHE_NAME = "measurementTypes";

    @Autowired
    private MeasurementTypeDao measurementTypeDao;

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
                MeasurementType entity = measurementTypeDao.find(id);
                if (entity != null) {
                    measurementTypeDao.remove(entity);
                }
            });
        }
    }

    @Test
    @DisplayName("should populate distinct entries for findAll, findAllOrderByName, findAllOrderById")
    void shouldPopulateDistinctEntries_forEachFindAllVariant() {
        transactionTemplate.executeWithoutResult(status -> {
            measurementTypeDao.findAll();
            measurementTypeDao.findAllOrderByName();
            measurementTypeDao.findAllOrderById();
        });

        assertThat(cacheEntryCount())
                .as("the three findAll variants use distinct static keys ('allByType','allByName','allById')")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("should evict measurementTypes cache when persist commits")
    void shouldEvictMeasurementTypesCache_whenPersistCommits() {
        transactionTemplate.executeWithoutResult(status -> measurementTypeDao.findAll());
        assertThat(cacheEntryCount()).isPositive();

        transactionTemplate.executeWithoutResult(status -> {
            MeasurementType entity = buildMeasurementType();
            measurementTypeDao.persist(entity);
            idsToCleanUp.add(entity.getId());
        });

        assertThat(cacheEntryCount())
                .as("persist must evict the measurementTypes cache after commit")
                .isZero();
    }

    @Test
    @DisplayName("should evict measurementTypes cache when merge commits")
    void shouldEvictMeasurementTypesCache_whenMergeCommits() {
        MeasurementType seeded = persistSeed();

        transactionTemplate.executeWithoutResult(status -> measurementTypeDao.findAllOrderByName());
        assertThat(cacheEntryCount()).isPositive();

        transactionTemplate.executeWithoutResult(status -> {
            seeded.setTypeDescription("updated-desc");
            measurementTypeDao.merge(seeded);
        });

        assertThat(cacheEntryCount())
                .as("merge must evict the measurementTypes cache after commit")
                .isZero();
    }

    @Test
    @DisplayName("should evict measurementTypes cache when remove commits")
    void shouldEvictMeasurementTypesCache_whenRemoveCommits() {
        MeasurementType seeded = persistSeed();

        transactionTemplate.executeWithoutResult(status -> measurementTypeDao.findAllOrderById());
        assertThat(cacheEntryCount()).isPositive();

        transactionTemplate.executeWithoutResult(status -> {
            MeasurementType managed = measurementTypeDao.find(seeded.getId());
            measurementTypeDao.remove(managed);
            idsToCleanUp.remove(seeded.getId());
        });

        assertThat(cacheEntryCount())
                .as("remove must evict the measurementTypes cache after commit")
                .isZero();
    }

    @Test
    @DisplayName("should evict measurementTypes cache when saveEntity commits")
    void shouldEvictMeasurementTypesCache_whenSaveEntityCommits() {
        transactionTemplate.executeWithoutResult(status -> measurementTypeDao.findAll());
        assertThat(cacheEntryCount()).isPositive();

        transactionTemplate.executeWithoutResult(status -> {
            MeasurementType saved = measurementTypeDao.saveEntity(buildMeasurementType());
            idsToCleanUp.add(saved.getId());
        });

        assertThat(cacheEntryCount())
                .as("saveEntity must evict the measurementTypes cache after commit")
                .isZero();
    }

    @Test
    @DisplayName("should evict measurementTypes cache when batchPersist completes")
    void shouldEvictMeasurementTypesCache_whenBatchPersistCompletes() {
        transactionTemplate.executeWithoutResult(status -> measurementTypeDao.findAll());
        assertThat(cacheEntryCount()).isPositive();

        List<MeasurementType> entities = List.of(buildMeasurementType(), buildMeasurementType());
        measurementTypeDao.batchPersist(entities);
        entities.forEach(e -> idsToCleanUp.add(e.getId()));

        assertThat(cacheEntryCount())
                .as("batchPersist must evict the measurementTypes cache (beforeInvocation = true)")
                .isZero();
    }

    @Test
    @DisplayName("should evict measurementTypes cache before batchPersist runs to prevent stale entries on partial failure")
    void shouldEvictMeasurementTypesCache_beforeBatchPersistRuns() {
        transactionTemplate.executeWithoutResult(status -> measurementTypeDao.findAll());
        assertThat(cacheEntryCount()).isPositive();

        // An entity with a null required column triggers persistence failure; eviction
        // must have fired BEFORE the body so the cache is coherent with the DB afterwards.
        MeasurementType invalid = buildMeasurementType();
        invalid.setType(null);

        try {
            measurementTypeDao.batchPersist(List.of(invalid));
        } catch (RuntimeException expected) {
            // Expected: persistence failure on NOT NULL type
        }

        assertThat(cacheEntryCount())
                .as("batchPersist must evict BEFORE body runs — partial-commit failures otherwise pin stale entries until TTL")
                .isZero();
    }

    @Test
    @DisplayName("should evict measurementTypes cache when batchRemove completes")
    void shouldEvictMeasurementTypesCache_whenBatchRemoveCompletes() {
        MeasurementType first = persistSeed();
        MeasurementType second = persistSeed();

        transactionTemplate.executeWithoutResult(status -> measurementTypeDao.findAll());
        assertThat(cacheEntryCount()).isPositive();

        measurementTypeDao.batchRemove(List.of(first, second));
        idsToCleanUp.remove(first.getId());
        idsToCleanUp.remove(second.getId());

        assertThat(cacheEntryCount())
                .as("batchRemove must evict the measurementTypes cache")
                .isZero();
    }

    @Test
    @DisplayName("should return independent copies for each findAll variant when served from the cache")
    void shouldReturnIndependentCopies_forEachFindAllVariantServedFromCache() {
        MeasurementType seeded = persistSeed();
        Map<String, Supplier<List<MeasurementType>>> readsByCacheKey = new LinkedHashMap<>();
        readsByCacheKey.put("allByType", measurementTypeDao::findAll);
        readsByCacheKey.put("allByName", measurementTypeDao::findAllOrderByName);
        readsByCacheKey.put("allById", measurementTypeDao::findAllOrderById);

        for (Map.Entry<String, Supplier<List<MeasurementType>>> read : readsByCacheKey.entrySet()) {
            String key = read.getKey();
            List<MeasurementType> first = transactionTemplate.execute(status -> read.getValue().get());
            List<MeasurementType> second = transactionTemplate.execute(status -> read.getValue().get());

            assertThat(second).as(key).isNotSameAs(first);
            assertThat(findById(second, seeded.getId())).as(key).isNotSameAs(findById(first, seeded.getId()));
            assertNoSharedInstances(second, cachedList(key));
            assertThatThrownBy(() -> first.remove(0))
                    .as("%s must stay unmodifiable", key)
                    .isInstanceOf(UnsupportedOperationException.class);

            MeasurementType changed = findById(first, seeded.getId());
            changed.setTypeDescription("mutated-copy");
            changed.getCreateDate().setTime(0L);

            List<MeasurementType> third = transactionTemplate.execute(status -> read.getValue().get());
            MeasurementType reread = findById(third, seeded.getId());
            assertThat(reread.getTypeDescription())
                    .as("%s: changing one returned type must not change the next read", key)
                    .isEqualTo("cache-test");
            assertThat(reread.getCreateDate().getTime())
                    .as("%s: the creation date is copied, not shared", key)
                    .isNotZero();
            MeasurementType snapshot = findById(cachedList(key), seeded.getId());
            assertThat(snapshot.getTypeDescription()).as(key).isEqualTo("cache-test");
            assertThat(snapshot.getCreateDate().getTime()).as(key).isNotZero();
        }
    }

    @Test
    @DisplayName("should neither write nor cache a change made to a cache-miss result inside a transaction")
    void shouldNotWriteOrCacheChange_whenCacheMissResultChangedInsideTransaction() {
        MeasurementType seeded = persistSeed();

        // Before #5304 a cache miss returned managed entities: this change was flushed to the
        // database when the transaction committed, and the same instance became the cached value.
        transactionTemplate.executeWithoutResult(status -> {
            List<MeasurementType> loaded = measurementTypeDao.findAll();
            findById(loaded, seeded.getId()).setTypeDescription("changed-in-tx");
        });

        MeasurementType stored = transactionTemplate.execute(status -> measurementTypeDao.find(seeded.getId()));
        assertThat(stored.getTypeDescription())
                .as("a returned type is a detached copy: changing it must not write to the database")
                .isEqualTo("cache-test");
        assertThat(findById(cachedList("allByType"), seeded.getId()).getTypeDescription())
                .as("the cached snapshot must hold what was read, not the caller's later change")
                .isEqualTo("cache-test");
    }

    @Test
    @DisplayName("should not populate the cache when the reading transaction rolls back")
    void shouldNotPopulateCache_whenReadingTransactionRollsBack() {
        transactionTemplate.executeWithoutResult(status -> {
            measurementTypeDao.findAll();
            measurementTypeDao.findAllOrderByName();
            measurementTypeDao.findAllOrderById();
            status.setRollbackOnly();
        });

        assertThat(cacheEntryCount())
                .as("cache puts are transaction-aware: a rolled-back read must leave the cache empty")
                .isZero();
    }

    private MeasurementType persistSeed() {
        MeasurementType seeded = buildMeasurementType();
        transactionTemplate.executeWithoutResult(status -> {
            measurementTypeDao.persist(seeded);
            idsToCleanUp.add(seeded.getId());
        });
        return seeded;
    }

    private MeasurementType buildMeasurementType() {
        String suffix = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 999999));
        MeasurementType entity = new MeasurementType();
        entity.setType("CT" + suffix);
        entity.setTypeDisplayName("CacheTest" + suffix);
        entity.setTypeDescription("cache-test");
        entity.setMeasuringInstruction("test");
        entity.setValidation("numeric");
        return entity;
    }

    private MeasurementType findById(List<MeasurementType> types, Integer id) {
        for (MeasurementType type : types) {
            if (id.equals(type.getId())) {
                return type;
            }
        }
        throw new AssertionError("Expected measurement type id " + id + " in result list");
    }

    @SuppressWarnings("unchecked")
    private List<MeasurementType> cachedList(String key) {
        org.springframework.cache.Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();
        org.springframework.cache.Cache.ValueWrapper wrapper = cache.get(key);
        assertThat(wrapper).as("cache entry %s should be populated", key).isNotNull();
        return (List<MeasurementType>) wrapper.get();
    }

    private void assertNoSharedInstances(List<MeasurementType> returned, List<MeasurementType> cached) {
        Set<Object> cachedInstances = Collections.newSetFromMap(new IdentityHashMap<>());
        for (MeasurementType cachedType : cached) {
            cachedInstances.add(cachedType);
            if (cachedType.getCreateDate() != null) {
                cachedInstances.add(cachedType.getCreateDate());
            }
        }
        for (MeasurementType returnedType : returned) {
            assertThat(cachedInstances.contains(returnedType))
                    .as("returned type %s must not be the cached instance", returnedType.getId())
                    .isFalse();
            assertThat(returnedType.getCreateDate() != null && cachedInstances.contains(returnedType.getCreateDate()))
                    .as("returned type %s must not share the cached creation date", returnedType.getId())
                    .isFalse();
        }
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
