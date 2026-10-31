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

import io.github.carlos_emr.carlos.commn.model.CdsImportDependency;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Data access for one protected CDS import table; orchestration belongs in services. */
@Repository
public class CdsImportDependencyDao extends AbstractDaoImpl<CdsImportDependency> {
    public CdsImportDependencyDao() { super(CdsImportDependency.class); }
    public CdsImportDependency lock(String id) { return entityManager.find(CdsImportDependency.class, id, LockModeType.PESSIMISTIC_WRITE); }
    public CdsImportDependency get(String id) { return entityManager.find(CdsImportDependency.class, id); }
    public void flushAndClear() { entityManager.flush(); entityManager.clear(); }
    public List<CdsImportDependency> page(String runId, String unitKey, String afterKey, int limit) {
        return entityManager.createQuery("select d from CdsImportDependency d where d.runId=:run and d.unitKey=:unit and d.dependsOnUnitKey>:after order by d.dependsOnUnitKey", CdsImportDependency.class)
                .setParameter("run", runId).setParameter("unit", unitKey).setParameter("after", afterKey).setMaxResults(limit).getResultList();
    }
    public long incomplete(String runId, String unitKey) {
        return entityManager.createQuery("select count(d) from CdsImportDependency d where d.runId=:run and d.unitKey=:unit and not exists(select u.id from CdsImportUnit u where u.runId=d.runId and u.unitKey=d.dependsOnUnitKey and u.state in ('COMMITTED','ALREADY_COMPLETE'))", Long.class)
                .setParameter("run", runId).setParameter("unit", unitKey).getSingleResult();
    }
    public List<CdsImportDependency> forUnits(String runId, java.util.Collection<String> unitKeys) {
        if (unitKeys.isEmpty()) return List.of();
        return entityManager.createQuery("select d from CdsImportDependency d where d.runId=:run and d.unitKey in :keys order by d.unitKey,d.dependsOnUnitKey", CdsImportDependency.class)
                .setParameter("run", runId).setParameter("keys", unitKeys).setMaxResults(1601).getResultList();
    }
    public long count(String runId, String unitKey) {
        return entityManager.createQuery("select count(d) from CdsImportDependency d where d.runId=:run and d.unitKey=:unit", Long.class).setParameter("run", runId).setParameter("unit", unitKey).getSingleResult();
    }
    public List<CdsImportDependency> all(String runId, String afterId, int limit) {
        return entityManager.createQuery("select d from CdsImportDependency d where d.runId=:run and d.id>:after order by d.id", CdsImportDependency.class).setParameter("run", runId).setParameter("after", afterId).setMaxResults(limit).getResultList();
    }
}
