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
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.config.CacheConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
public class MeasurementTypeDaoImpl extends AbstractDaoImpl<MeasurementType> implements MeasurementTypeDao {
    private static final String ALL_BY_TYPE_KEY = "allByType";
    private static final String ALL_BY_NAME_KEY = "allByName";
    private static final String ALL_BY_ID_KEY = "allById";
    private static final String FIND_ALL_BY_TYPE = "select x from MeasurementType x order by x.type";
    private static final String FIND_ALL_BY_NAME = "select x from MeasurementType x order by x.typeDisplayName";
    private static final String FIND_ALL_BY_ID = "select x from MeasurementType x order by x.id";

    /*
     * Reads use CacheManager directly instead of @Cacheable so the cache holds private copies
     * and every read, hit or miss, returns fresh copies (issue #5304). @Cacheable would hand
     * every caller the same cached entity instances, so one caller's change would be seen by
     * all readers until the cache expired. Keys, bounds (CacheConfig) and the transaction-aware
     * put/evict behaviour are unchanged; every write still evicts through the @CacheEvict
     * overrides below.
     */
    private final CacheManager cacheManager;

    /**
     * @param cacheManager the application's transaction-aware cache manager ({@code CacheConfig}),
     *                     which must define the {@code measurementTypes} cache
     */
    @Autowired
    public MeasurementTypeDaoImpl(CacheManager cacheManager) {
        super(MeasurementType.class);
        this.cacheManager = cacheManager;
    }

    /**
     * Returns every measurement type ordered by type code.
     *
     * @return an unmodifiable list of detached copies; changing an element affects only this
     *         caller's copy and writes nothing to the database
     */
    @Override
    public List<MeasurementType> findAll() {
        return findAllCached(ALL_BY_TYPE_KEY, FIND_ALL_BY_TYPE);
    }

    /**
     * Returns every measurement type ordered by display name.
     *
     * @return an unmodifiable list of detached copies; changing an element affects only this
     *         caller's copy and writes nothing to the database
     */
    @Override
    public List<MeasurementType> findAllOrderByName() {
        return findAllCached(ALL_BY_NAME_KEY, FIND_ALL_BY_NAME);
    }

    /**
     * Returns every measurement type ordered by id.
     *
     * @return an unmodifiable list of detached copies; changing an element affects only this
     *         caller's copy and writes nothing to the database
     */
    @Override
    public List<MeasurementType> findAllOrderById() {
        return findAllCached(ALL_BY_ID_KEY, FIND_ALL_BY_ID);
    }

    private List<MeasurementType> findAllCached(String cacheKey, String jpql) {
        List<MeasurementType> cached = getCachedList(cacheKey);
        if (cached != null) {
            return copyMeasurementTypes(cached);
        }

        Query query = entityManager.createQuery(jpql);
        List<MeasurementType> results = query.getResultList();

        cache().put(cacheKey, copyMeasurementTypes(results));
        return copyMeasurementTypes(results);
    }

    private Cache cache() {
        Cache cache = cacheManager.getCache(CacheConfig.MEASUREMENT_TYPES);
        if (cache == null) {
            throw new IllegalStateException("Cache not configured: " + CacheConfig.MEASUREMENT_TYPES);
        }
        return cache;
    }

    private List<MeasurementType> getCachedList(String key) {
        Cache.ValueWrapper wrapper = cache().get(key);
        return wrapper == null ? null : (List<MeasurementType>) wrapper.get();
    }

    /*
     * Unmodifiable, as these lists always were; the elements are new copies on every call.
     */
    private static List<MeasurementType> copyMeasurementTypes(List<MeasurementType> source) {
        List<MeasurementType> copies = new ArrayList<>(source.size());
        for (MeasurementType measurementType : source) {
            copies.add(MeasurementType.copyOf(measurementType));
        }
        return Collections.unmodifiableList(copies);
    }

    @Override
    public boolean hasCdsImportType(String type, String expectedInstruction) {
        return !entityManager.createQuery("select x.id from MeasurementType x where x.type=:type "
                + "and (:requireInstruction=false or x.measuringInstruction=:instruction) "
                + "and (:requireValidation=false or exists (select v.id from Validations v where x.validation=cast(v.id as string) "
                + "and v.name='CDS source text (no numeric inference)' and v.regularExp is null "
                + "and v.minValue is null and v.maxValue is null and v.minLength is null and v.maxLength is null "
                + "and v.isNumeric is null and v.isTrue is null and v.isDate is null))", Integer.class)
                .setParameter("type", type).setParameter("requireInstruction", expectedInstruction != null)
                .setParameter("instruction", expectedInstruction)
                .setParameter("requireValidation", java.util.Set.of("CDS_GOALS", "CDS_EXAM", "CDS_CARE_VALUE").contains(type))
                .setMaxResults(1).getResultList().isEmpty();
    }

    @Override
    public List<MeasurementType> findByType(String type) {
        String sqlCommand = "select x from " + modelClass.getSimpleName() + " x where x.type=?1";
        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, type);
        List<MeasurementType> results = query.getResultList();
        return (results);
    }

    @Override
    public List<MeasurementType> findByMeasuringInstructionAndTypeDisplayName(String measuringInstruction, String typeDisplayName) {
        String sqlCommand = "select x from " + modelClass.getSimpleName() + " x where x.measuringInstruction=?1 AND x.typeDisplayName=?2";
        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, measuringInstruction);
        query.setParameter(2, typeDisplayName);
        List<MeasurementType> results = query.getResultList();
        return (results);
    }

    @Override
    public List<MeasurementType> findByTypeDisplayName(String typeDisplayName) {
        String sqlCommand = "select x from " + modelClass.getSimpleName() + " x where x.typeDisplayName=?1";
        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, typeDisplayName);
        List<MeasurementType> results = query.getResultList();
        return (results);
    }

    @Override
    public List<MeasurementType> findByTypeAndMeasuringInstruction(String type, String measuringInstruction) {
        String sqlCommand = "select x from " + modelClass.getSimpleName() + " x where x.type=?1 AND x.measuringInstruction=?2 ";
        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, type);
        query.setParameter(2, measuringInstruction);
        List<MeasurementType> results = query.getResultList();
        return (results);
    }

    @Override
    public List<Object> findUniqueTypeDisplayNames() {
        String sql = "SELECT DISTINCT m.typeDisplayName FROM MeasurementType m order by m.typeDisplayName";
        Query query = entityManager.createQuery(sql);
        return query.getResultList();
    }

    @CacheEvict(value = CacheConfig.MEASUREMENT_TYPES, allEntries = true)
    @Override
    public void persist(AbstractModel<?> o) { super.persist(o); }

    @CacheEvict(value = CacheConfig.MEASUREMENT_TYPES, allEntries = true)
    @Override
    public void merge(AbstractModel<?> o) { super.merge(o); }

    @CacheEvict(value = CacheConfig.MEASUREMENT_TYPES, allEntries = true)
    @Override
    public void remove(AbstractModel<?> o) { super.remove(o); }

    @CacheEvict(value = CacheConfig.MEASUREMENT_TYPES, allEntries = true)
    @Override
    public boolean remove(Object id) { return super.remove(id); }

    @CacheEvict(value = CacheConfig.MEASUREMENT_TYPES, allEntries = true)
    @Override
    public MeasurementType saveEntity(MeasurementType entity) { return super.saveEntity(entity); }

    // batch* methods use a separate EntityManager and invoke persist/remove on it directly,
    // bypassing the Spring proxy — so @CacheEvict on persist/remove never fires through this
    // path. Override both overloads to restore eviction at the proxied boundary.
    //
    // beforeInvocation = true: AbstractDaoImpl.batchPersist commits sub-batches inside its
    // loop, so a later sub-batch failure leaves earlier sub-batches persisted to the DB.
    // Default beforeInvocation = false would skip eviction on exception, pinning stale
    // entries in the cache until TTL.
    @CacheEvict(value = CacheConfig.MEASUREMENT_TYPES, allEntries = true, beforeInvocation = true)
    @Override
    public void batchPersist(List<MeasurementType> oList) { super.batchPersist(oList); }

    @CacheEvict(value = CacheConfig.MEASUREMENT_TYPES, allEntries = true, beforeInvocation = true)
    @Override
    public void batchPersist(List<MeasurementType> oList, int batchSize) { super.batchPersist(oList, batchSize); }

    @CacheEvict(value = CacheConfig.MEASUREMENT_TYPES, allEntries = true, beforeInvocation = true)
    @Override
    public void batchRemove(List<MeasurementType> oList) { super.batchRemove(oList); }

    @CacheEvict(value = CacheConfig.MEASUREMENT_TYPES, allEntries = true, beforeInvocation = true)
    @Override
    public void batchRemove(List<MeasurementType> oList, int batchSize) { super.batchRemove(oList, batchSize); }

    @CacheEvict(value = CacheConfig.MEASUREMENT_TYPES, allEntries = true)
    @Override public void batchPersistAtomically(List<MeasurementType> list) { super.batchPersistAtomically(list); }
    @CacheEvict(value = CacheConfig.MEASUREMENT_TYPES, allEntries = true)
    @Override public void batchPersistAtomically(List<MeasurementType> list, int size) { super.batchPersistAtomically(list, size); }
    @CacheEvict(value = CacheConfig.MEASUREMENT_TYPES, allEntries = true)
    @Override public void batchRemoveAtomically(List<MeasurementType> list) { super.batchRemoveAtomically(list); }
    @CacheEvict(value = CacheConfig.MEASUREMENT_TYPES, allEntries = true)
    @Override public void batchRemoveAtomically(List<MeasurementType> list, int size) { super.batchRemoveAtomically(list, size); }
    @CacheEvict(value = CacheConfig.MEASUREMENT_TYPES, allEntries = true, beforeInvocation = true)
    @Override public void batchPersistWithIndependentCommits(List<MeasurementType> list) { super.batchPersistWithIndependentCommits(list); }
    @CacheEvict(value = CacheConfig.MEASUREMENT_TYPES, allEntries = true, beforeInvocation = true)
    @Override public void batchPersistWithIndependentCommits(List<MeasurementType> list, int size) { super.batchPersistWithIndependentCommits(list, size); }
    @CacheEvict(value = CacheConfig.MEASUREMENT_TYPES, allEntries = true, beforeInvocation = true)
    @Override public void batchRemoveWithIndependentCommits(List<MeasurementType> list) { super.batchRemoveWithIndependentCommits(list); }
    @CacheEvict(value = CacheConfig.MEASUREMENT_TYPES, allEntries = true, beforeInvocation = true)
    @Override public void batchRemoveWithIndependentCommits(List<MeasurementType> list, int size) { super.batchRemoveWithIndependentCommits(list, size); }
}
