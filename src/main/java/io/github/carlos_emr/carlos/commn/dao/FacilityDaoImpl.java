/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
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
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.config.CacheConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Repository;

@Repository
public class FacilityDaoImpl extends AbstractDaoImpl<Facility> implements FacilityDao {
    private static final String ACTIVE_KEY_PREFIX = "active:";

    @Autowired
    private CacheManager cacheManager;

    public FacilityDaoImpl() {
        super(Facility.class);
    }

    /**
     * Find all ordered by name.
     *
     * @param active null is find all, true is find only active, false is find only inactive.
     */
    public List<Facility> findAll(Boolean active) {
        String cacheKey = ACTIVE_KEY_PREFIX + active;
        List<Facility> cached = getCachedList(cacheKey);
        if (cached != null) {
            return copyFacilities(cached);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("select x from Facility x");
        if (active != null) sb.append(" where x.disabled=?1");
        sb.append(" order by x.name");

        Query query = entityManager.createQuery(sb.toString());
        if (active != null) query.setParameter(1, !active);

        @SuppressWarnings("unchecked")
        List<Facility> results = query.getResultList();

        List<Facility> snapshot = Collections.unmodifiableList(copyFacilities(results));
        cache().put(cacheKey, snapshot);
        return copyFacilities(snapshot);
    }

    private Cache cache() {
        Cache cache = cacheManager.getCache(CacheConfig.FACILITIES);
        if (cache == null) {
            throw new IllegalStateException("Cache not configured: " + CacheConfig.FACILITIES);
        }
        return cache;
    }

    @SuppressWarnings("unchecked")
    private List<Facility> getCachedList(String key) {
        Cache.ValueWrapper wrapper = cache().get(key);
        return wrapper == null ? null : (List<Facility>) wrapper.get();
    }

    private List<Facility> copyFacilities(List<Facility> source) {
        List<Facility> copies = new ArrayList<>(source.size());
        for (Facility facility : source) {
            copies.add(Facility.copyOf(facility));
        }
        return copies;
    }

    @CacheEvict(value = CacheConfig.FACILITIES, allEntries = true)
    @Override
    public void persist(AbstractModel<?> o) { super.persist(o); }

    @CacheEvict(value = CacheConfig.FACILITIES, allEntries = true)
    @Override
    public void merge(AbstractModel<?> o) { super.merge(o); }

    @CacheEvict(value = CacheConfig.FACILITIES, allEntries = true)
    @Override
    public void remove(AbstractModel<?> o) { super.remove(o); }

    @CacheEvict(value = CacheConfig.FACILITIES, allEntries = true)
    @Override
    public boolean remove(Object id) { return super.remove(id); }

    @CacheEvict(value = CacheConfig.FACILITIES, allEntries = true)
    @Override
    public Facility saveEntity(Facility entity) { return super.saveEntity(entity); }

    @CacheEvict(value = CacheConfig.FACILITIES, allEntries = true, beforeInvocation = true)
    @Override
    public void batchPersist(List<Facility> oList) { super.batchPersist(oList); }

    @CacheEvict(value = CacheConfig.FACILITIES, allEntries = true, beforeInvocation = true)
    @Override
    public void batchPersist(List<Facility> oList, int batchSize) { super.batchPersist(oList, batchSize); }

    @CacheEvict(value = CacheConfig.FACILITIES, allEntries = true, beforeInvocation = true)
    @Override
    public void batchRemove(List<Facility> oList) { super.batchRemove(oList); }

    @CacheEvict(value = CacheConfig.FACILITIES, allEntries = true, beforeInvocation = true)
    @Override
    public void batchRemove(List<Facility> oList, int batchSize) { super.batchRemove(oList, batchSize); }

}
