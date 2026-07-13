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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
public class MeasurementTypeDaoImpl extends AbstractDaoImpl<MeasurementType> implements MeasurementTypeDao {

    public MeasurementTypeDaoImpl() {
        super(MeasurementType.class);
    }

    @Cacheable(value = CacheConfig.MEASUREMENT_TYPES, key = "'allByType'")
    @Override
    public List<MeasurementType> findAll() {
        String sqlCommand = "select x from " + modelClass.getSimpleName() + " x order by x.type";
        Query query = entityManager.createQuery(sqlCommand);
        List<MeasurementType> results = query.getResultList();
        return Collections.unmodifiableList(new ArrayList<>(results));
    }

    @Cacheable(value = CacheConfig.MEASUREMENT_TYPES, key = "'allByName'")
    @Override
    public List<MeasurementType> findAllOrderByName() {
        String sqlCommand = "select x from " + modelClass.getSimpleName() + " x order by x.typeDisplayName";
        Query query = entityManager.createQuery(sqlCommand);
        List<MeasurementType> results = query.getResultList();
        return Collections.unmodifiableList(new ArrayList<>(results));
    }

    @Cacheable(value = CacheConfig.MEASUREMENT_TYPES, key = "'allById'")
    @Override
    public List<MeasurementType> findAllOrderById() {
        String sqlCommand = "select x from " + modelClass.getSimpleName() + " x order by x.id";
        Query query = entityManager.createQuery(sqlCommand);
        List<MeasurementType> results = query.getResultList();
        return Collections.unmodifiableList(new ArrayList<>(results));
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
}
