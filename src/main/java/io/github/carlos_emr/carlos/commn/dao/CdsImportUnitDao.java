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

import io.github.carlos_emr.carlos.commn.model.CdsImportUnit;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Data access for one protected CDS import table; orchestration belongs in services. */
@Repository
public class CdsImportUnitDao extends AbstractDaoImpl<CdsImportUnit> {
    public CdsImportUnitDao() { super(CdsImportUnit.class); }
    public CdsImportUnit lock(String id) { return entityManager.find(CdsImportUnit.class, id, LockModeType.PESSIMISTIC_WRITE); }
    public CdsImportUnit get(String id) { return entityManager.find(CdsImportUnit.class, id); }
    public void flushAndClear() { entityManager.flush(); entityManager.clear(); }
    public CdsImportUnit findKey(String runId, String unitKey) {
        return entityManager.createQuery("select u from CdsImportUnit u where u.runId=:run and u.unitKey=:key", CdsImportUnit.class)
                .setParameter("run", runId).setParameter("key", unitKey).setMaxResults(1).getResultList().stream().findFirst().orElse(null);
    }
    public List<CdsImportUnit> page(String runId, String patientKey, String afterKey, int limit) {
        var query=patientKey==null
                ? entityManager.createQuery("select u from CdsImportUnit u where u.runId=:run and u.unitKey>:after order by u.unitKey", CdsImportUnit.class)
                : entityManager.createQuery("select u from CdsImportUnit u where u.runId=:run and u.unitKey>:after "
                        + "and u.patientKey=:patient order by u.unitKey", CdsImportUnit.class);
        query.setParameter("run", runId).setParameter("after", afterKey).setMaxResults(limit);
        if (patientKey!=null) query.setParameter("patient", patientKey);
        return query.getResultList();
    }
    public List<CdsImportUnit> ready(String runId, int limit) {
        return entityManager.createQuery("select u from CdsImportUnit u where u.runId=:run and u.state='EXPECTED' and not exists(select p.id from CdsImportPatient p where p.runId=u.runId and p.patientKey=u.patientKey and p.state in ('REFUSED','FAILED_BEFORE_WRITE','CANCELLED_BEFORE_WRITE','NOT_STARTED')) and not exists(select d.id from CdsImportDependency d where d.runId=u.runId and d.unitKey=u.unitKey and not exists(select done.id from CdsImportUnit done where done.runId=d.runId and done.unitKey=d.dependsOnUnitKey and done.state in ('COMMITTED','ALREADY_COMPLETE'))) order by u.unitKey", CdsImportUnit.class).setParameter("run", runId).setMaxResults(limit).getResultList();
    }
    public long count(String runId, String patientKey) {
        var query=patientKey==null
                ? entityManager.createQuery("select count(u) from CdsImportUnit u where u.runId=:run", Long.class)
                : entityManager.createQuery("select count(u) from CdsImportUnit u where u.runId=:run and u.patientKey=:patient", Long.class);
        query.setParameter("run", runId);
        if (patientKey!=null) query.setParameter("patient", patientKey);
        return query.getSingleResult();
    }
    public long countState(String runId, String patientKey, String state) {
        var query=patientKey==null
                ? entityManager.createQuery("select count(u) from CdsImportUnit u where u.runId=:run and u.state=:state", Long.class)
                : entityManager.createQuery("select count(u) from CdsImportUnit u where u.runId=:run and u.state=:state and u.patientKey=:patient", Long.class);
        query.setParameter("run", runId).setParameter("state", state);
        if (patientKey!=null) query.setParameter("patient", patientKey);
        return query.getSingleResult();
    }
    public List<Object[]> categories(String runId, int limit) {
        return entityManager.createQuery("select u.category,count(u),sum(case when u.state='COMMITTED' then 1 else 0 end),sum(case when u.state='ALREADY_COMPLETE' then 1 else 0 end) from CdsImportUnit u where u.runId=:run group by u.category order by u.category", Object[].class)
                .setParameter("run", runId).setMaxResults(limit).getResultList();
    }
    /** Summary totals share the bounded category scan, including the exact pending state. */
    public List<Object[]> summaryCategories(String runId, int limit) {
        return entityManager.createQuery("select u.category,count(u),sum(case when u.state='COMMITTED' then 1 else 0 end),sum(case when u.state='ALREADY_COMPLETE' then 1 else 0 end),sum(case when u.state='EXPECTED' then 1 else 0 end) from CdsImportUnit u where u.runId=:run group by u.category order by u.category", Object[].class)
                .setParameter("run", runId).setMaxResults(limit).getResultList();
    }
    public long incomplete(String runId, String patientKey) {
        return entityManager.createQuery("select count(u) from CdsImportUnit u where u.runId=:run and u.patientKey=:patient and u.state not in ('COMMITTED','ALREADY_COMPLETE')", Long.class)
                .setParameter("run", runId).setParameter("patient", patientKey).getSingleResult();
    }
    public long unrecovered(String runId) {
        return entityManager.createQuery("select count(u) from CdsImportUnit u where u.runId=:run and u.state not in ('COMMITTED','ALREADY_COMPLETE') and not exists(select p.id from CdsImportPatient p where p.runId=u.runId and p.patientKey=u.patientKey and p.state='REFUSED')", Long.class).setParameter("run", runId).getSingleResult();
    }
}
