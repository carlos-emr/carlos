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
import io.github.carlos_emr.carlos.commn.model.LookupListItem;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cache invalidation and isolation regression tests for {@link LookupListDao}. Verifies writes
 * (persist/merge/remove/saveEntity) evict the {@code lookupLists} cache after commit,
 * and confirms the null-key guard on {@code findByName} prevents cache poisoning.
 *
 * <p>Also verifies that every read returns its own detached deep copies (issue #5304): changing
 * a returned list, its item list or one of its items must not reach another reader, the cached
 * snapshot or the database.</p>
 */
@DisplayName("LookupListDao cache invalidation and isolation")
@Tag("integration")
@Tag("dao")
@Tag("cache")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LookupListDaoCacheIntegrationTest extends CarlosTestBase {

    private static final String CACHE_NAME = "lookupLists";

    @Autowired
    private LookupListDao lookupListDao;

    @Autowired
    private LookupListItemDao lookupListItemDao;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Integer> idsToCleanUp = new ArrayList<>();
    private final List<Integer> itemIdsToCleanUp = new ArrayList<>();
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
        // Items first: removing a list with items still attached would try to null their
        // NOT NULL lookupListId through the list's one-to-many join column.
        for (Integer itemId : itemIdsToCleanUp) {
            transactionTemplate.executeWithoutResult(status -> {
                LookupListItem item = lookupListItemDao.find(itemId);
                if (item != null) {
                    lookupListItemDao.remove(item);
                }
            });
        }
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

    @Test
    @DisplayName("should return independent deep copies when findByName is served from the cache")
    void shouldReturnIndependentDeepCopies_whenFindByNameServedFromCache() {
        LookupList seeded = persistSeedWithItems(uniqueName());
        String key = "name:" + seeded.getName();

        LookupList first = transactionTemplate.execute(status -> lookupListDao.findByName(seeded.getName()));
        LookupList second = transactionTemplate.execute(status -> lookupListDao.findByName(seeded.getName()));

        assertThat(first.getItems()).extracting(LookupListItem::getLabel).containsExactly("First", "Second");
        assertThat(second).isNotSameAs(first).isNotSameAs(cachedValue(key));
        assertThat(second.getItems()).isNotSameAs(first.getItems());
        assertNoSharedItems(second, (LookupList) cachedValue(key));

        first.setDescription("mutated-copy");
        first.getItems().get(0).setLabel("mutated-label");
        first.getItems().remove(1);
        first.getItems().add(buildItem(seeded.getId(), "added", 9));

        LookupList third = transactionTemplate.execute(status -> lookupListDao.findByName(seeded.getName()));
        assertThat(third.getDescription())
                .as("changing one returned list must not change the next read")
                .isEqualTo("seeded for cache invalidation test");
        assertThat(third.getItems())
                .as("changing one returned item list or item must not change the next read")
                .extracting(LookupListItem::getLabel)
                .containsExactly("First", "Second");
        LookupList snapshot = (LookupList) cachedValue(key);
        assertThat(snapshot.getDescription()).isEqualTo("seeded for cache invalidation test");
        assertThat(snapshot.getItems()).extracting(LookupListItem::getLabel).containsExactly("First", "Second");
    }

    @Test
    @DisplayName("should return unmodifiable independent deep copies when findAllActive is served from the cache")
    void shouldReturnUnmodifiableIndependentDeepCopies_whenFindAllActiveServedFromCache() {
        LookupList seeded = persistSeedWithItems(uniqueName());

        List<LookupList> first = transactionTemplate.execute(status -> lookupListDao.findAllActive());
        List<LookupList> second = transactionTemplate.execute(status -> lookupListDao.findAllActive());

        assertThatThrownBy(() -> first.add(buildLookupList(uniqueName())))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(second).isNotSameAs(first);
        @SuppressWarnings("unchecked")
        List<LookupList> cached = (List<LookupList>) cachedValue("allActive");
        for (LookupList returned : second) {
            assertNoSharedItems(returned, findById(cached, returned.getId()));
        }

        LookupList changed = findById(first, seeded.getId());
        changed.setActive(false);
        changed.getItems().clear();

        List<LookupList> third = transactionTemplate.execute(status -> lookupListDao.findAllActive());
        LookupList reread = findById(third, seeded.getId());
        assertThat(reread.isActive()).isTrue();
        assertThat(reread.getItems()).extracting(LookupListItem::getLabel).containsExactly("First", "Second");
        assertThat(findById(cached, seeded.getId()).getItems()).hasSize(2);
    }

    @Test
    @DisplayName("should neither write nor cache a change made to a cache-miss result inside a transaction")
    void shouldNotWriteOrCacheChange_whenCacheMissResultChangedInsideTransaction() {
        LookupList seeded = persistSeedWithItems(uniqueName());

        // Before #5304 a cache miss returned managed entities: these changes were flushed to the
        // database when the transaction committed, and the same instance became the cached value.
        transactionTemplate.executeWithoutResult(status -> {
            LookupList loaded = lookupListDao.findByName(seeded.getName());
            loaded.setDescription("changed-in-tx");
            loaded.getItems().get(0).setLabel("changed-in-tx");
        });

        LookupList stored = transactionTemplate.execute(status -> lookupListDao.find(seeded.getId()));
        LookupListItem storedItem = transactionTemplate.execute(
                status -> lookupListItemDao.find(itemIdsToCleanUp.get(0)));
        assertThat(stored.getDescription())
                .as("a returned list is a detached copy: changing it must not write to the database")
                .isEqualTo("seeded for cache invalidation test");
        assertThat(storedItem.getLabel())
                .as("a returned item is a detached copy: changing it must not write to the database")
                .isEqualTo("First");
        LookupList snapshot = (LookupList) cachedValue("name:" + seeded.getName());
        assertThat(snapshot.getDescription()).isEqualTo("seeded for cache invalidation test");
        assertThat(snapshot.getItems()).extracting(LookupListItem::getLabel).containsExactly("First", "Second");
    }

    @Test
    @DisplayName("should not populate the cache when the reading transaction rolls back")
    void shouldNotPopulateCache_whenReadingTransactionRollsBack() {
        LookupList seeded = persistSeedWithItems(uniqueName());

        transactionTemplate.executeWithoutResult(status -> {
            lookupListDao.findAllActive();
            lookupListDao.findByName(seeded.getName());
            status.setRollbackOnly();
        });

        assertThat(cacheEntryCount())
                .as("cache puts are transaction-aware: a rolled-back read must leave the cache empty")
                .isZero();
    }

    private LookupList persistSeedWithItems(String name) {
        LookupList seeded = persistSeed(name);
        transactionTemplate.executeWithoutResult(status -> {
            LookupListItem firstItem = buildItem(seeded.getId(), "First", 1);
            LookupListItem secondItem = buildItem(seeded.getId(), "Second", 2);
            lookupListItemDao.persist(firstItem);
            lookupListItemDao.persist(secondItem);
            itemIdsToCleanUp.add(firstItem.getId());
            itemIdsToCleanUp.add(secondItem.getId());
        });
        // Item rows were written without the list's DAO, so start from an empty cache.
        clearCache();
        return seeded;
    }

    private LookupListItem buildItem(Integer lookupListId, String label, int displayOrder) {
        LookupListItem item = new LookupListItem();
        item.setLookupListId(lookupListId);
        item.setValue("v" + ThreadLocalRandom.current().nextInt(100000, 999999));
        item.setLabel(label);
        item.setActive(true);
        item.setDisplayOrder(displayOrder);
        item.setCreatedBy("cachetst");
        return item;
    }

    private LookupList findById(List<LookupList> lists, Integer id) {
        for (LookupList list : lists) {
            if (id.equals(list.getId())) {
                return list;
            }
        }
        throw new AssertionError("Expected lookup list id " + id + " in result list");
    }

    private Object cachedValue(String key) {
        org.springframework.cache.Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();
        org.springframework.cache.Cache.ValueWrapper wrapper = cache.get(key);
        assertThat(wrapper).as("cache entry %s should be populated", key).isNotNull();
        return wrapper.get();
    }

    private void assertNoSharedItems(LookupList returned, LookupList cached) {
        assertThat(returned).isNotSameAs(cached);
        if (returned.getItems() == null) {
            return;
        }
        assertThat(returned.getItems()).isNotSameAs(cached.getItems());
        Set<LookupListItem> cachedItems = Collections.newSetFromMap(new IdentityHashMap<>());
        cachedItems.addAll(cached.getItems());
        for (LookupListItem item : returned.getItems()) {
            assertThat(cachedItems.contains(item))
                    .as("returned item %s must not be the cached instance", item.getId())
                    .isFalse();
        }
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
