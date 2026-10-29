/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.commn.model.LookupList;
import io.github.carlos_emr.carlos.config.CacheConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Repository;

@Repository
public class LookupListDaoImpl extends AbstractDaoImpl<LookupList> implements LookupListDao {
    private static final String ALL_ACTIVE_KEY = "allActive";
    private static final String NAME_KEY_PREFIX = "name:";

    /*
     * Reads use CacheManager directly instead of @Cacheable so the cache holds private deep
     * copies (list and items) and every read, hit or miss, returns fresh copies (issue #5304).
     * @Cacheable would hand every caller the same cached entities and item lists, so one
     * caller's change would be seen by all readers until the cache expired. Keys, bounds
     * (CacheConfig) and the transaction-aware put/evict behaviour are unchanged; every write
     * still evicts through the @CacheEvict overrides below and in LookupListManager.
     */
    private final CacheManager cacheManager;

    @Autowired
    public LookupListDaoImpl(CacheManager cacheManager) {
        super(LookupList.class);
        this.cacheManager = cacheManager;
    }

    /**
     * Returns the active lookup lists ordered by name, each with its items.
     *
     * @return an unmodifiable list of detached deep copies; changing a list, its item list or
     *         an item affects only this caller's copy and writes nothing to the database
     */
    @Override
    public List<LookupList> findAllActive() {
        List<LookupList> cached = getCachedList(ALL_ACTIVE_KEY);
        if (cached != null) {
            return copyLookupLists(cached);
        }

        Query q = entityManager.createQuery("select l from LookupList l where l.active=?1 order by l.name asc");
        q.setParameter(1, true);

        @SuppressWarnings("unchecked")
        List<LookupList> result = q.getResultList();

        cache().put(ALL_ACTIVE_KEY, copyLookupLists(result));
        return copyLookupLists(result);
    }

    /**
     * Finds a lookup list, with its items, by its unique name.
     *
     * @param name the list name; a null or empty name is looked up but never cached
     * @return a detached deep copy of the list, or {@code null} when no list has that name (a
     *         miss is not cached)
     */
    @Override
    public LookupList findByName(String name) {
        boolean cacheable = name != null && !name.isEmpty();
        String cacheKey = NAME_KEY_PREFIX + name;
        if (cacheable) {
            LookupList cached = getCachedLookupList(cacheKey);
            if (cached != null) {
                return LookupList.copyOf(cached);
            }
        }

        Query q = entityManager.createQuery("select l from LookupList l where l.name=?1");
        q.setParameter(1, name);

        LookupList ll = this.getSingleResultOrNull(q);
        if (ll != null && cacheable) {
            cache().put(cacheKey, LookupList.copyOf(ll));
        }

        return LookupList.copyOf(ll);
    }

    private Cache cache() {
        Cache cache = cacheManager.getCache(CacheConfig.LOOKUP_LISTS);
        if (cache == null) {
            throw new IllegalStateException("Cache not configured: " + CacheConfig.LOOKUP_LISTS);
        }
        return cache;
    }

    @SuppressWarnings("unchecked")
    private List<LookupList> getCachedList(String key) {
        Cache.ValueWrapper wrapper = cache().get(key);
        return wrapper == null ? null : (List<LookupList>) wrapper.get();
    }

    private LookupList getCachedLookupList(String key) {
        Cache.ValueWrapper wrapper = cache().get(key);
        return wrapper == null ? null : (LookupList) wrapper.get();
    }

    /*
     * Unmodifiable, as this list always was; the lists and items are new copies on every call.
     */
    private static List<LookupList> copyLookupLists(List<LookupList> source) {
        List<LookupList> copies = new ArrayList<>(source.size());
        for (LookupList lookupList : source) {
            copies.add(LookupList.copyOf(lookupList));
        }
        return Collections.unmodifiableList(copies);
    }

    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true)
    @Override
    public void persist(AbstractModel<?> o) { super.persist(o); }

    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true)
    @Override
    public void merge(AbstractModel<?> o) { super.merge(o); }

    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true)
    @Override
    public void remove(AbstractModel<?> o) { super.remove(o); }

    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true)
    @Override
    public boolean remove(Object id) { return super.remove(id); }

    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true)
    @Override
    public LookupList saveEntity(LookupList entity) { return super.saveEntity(entity); }

    // batch* methods use a separate EntityManager and invoke persist/remove on it directly,
    // bypassing the Spring proxy — so @CacheEvict on persist/remove never fires through this
    // path. Override both overloads to restore eviction at the proxied boundary.
    //
    // beforeInvocation = true: AbstractDaoImpl.batchPersist commits sub-batches inside its
    // loop, so a later sub-batch failure leaves earlier sub-batches persisted to the DB.
    // Default beforeInvocation = false would skip eviction on exception, pinning stale
    // entries in the cache until TTL.
    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true, beforeInvocation = true)
    @Override
    public void batchPersist(List<LookupList> oList) { super.batchPersist(oList); }

    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true, beforeInvocation = true)
    @Override
    public void batchPersist(List<LookupList> oList, int batchSize) { super.batchPersist(oList, batchSize); }

    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true, beforeInvocation = true)
    @Override
    public void batchRemove(List<LookupList> oList) { super.batchRemove(oList); }

    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true, beforeInvocation = true)
    @Override
    public void batchRemove(List<LookupList> oList, int batchSize) { super.batchRemove(oList, batchSize); }

    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true)
    @Override public void batchPersistAtomically(List<LookupList> list) { super.batchPersistAtomically(list); }
    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true)
    @Override public void batchPersistAtomically(List<LookupList> list, int size) { super.batchPersistAtomically(list, size); }
    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true)
    @Override public void batchRemoveAtomically(List<LookupList> list) { super.batchRemoveAtomically(list); }
    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true)
    @Override public void batchRemoveAtomically(List<LookupList> list, int size) { super.batchRemoveAtomically(list, size); }
    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true, beforeInvocation = true)
    @Override public void batchPersistWithIndependentCommits(List<LookupList> list) { super.batchPersistWithIndependentCommits(list); }
    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true, beforeInvocation = true)
    @Override public void batchPersistWithIndependentCommits(List<LookupList> list, int size) { super.batchPersistWithIndependentCommits(list, size); }
    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true, beforeInvocation = true)
    @Override public void batchRemoveWithIndependentCommits(List<LookupList> list) { super.batchRemoveWithIndependentCommits(list); }
    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true, beforeInvocation = true)
    @Override public void batchRemoveWithIndependentCommits(List<LookupList> list, int size) { super.batchRemoveWithIndependentCommits(list, size); }
}
