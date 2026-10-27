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

import io.github.carlos_emr.carlos.commn.model.CdsImportDiagnostic;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Data access for one protected CDS import table; orchestration belongs in services. */
@Repository
public class CdsImportDiagnosticDao extends AbstractDaoImpl<CdsImportDiagnostic> {
    public CdsImportDiagnosticDao() { super(CdsImportDiagnostic.class); }
    public CdsImportDiagnostic lock(Long id) { return entityManager.find(CdsImportDiagnostic.class, id, LockModeType.PESSIMISTIC_WRITE); }
    public CdsImportDiagnostic get(Long id) { return entityManager.find(CdsImportDiagnostic.class, id); }
    public void flushAndClear() { entityManager.flush(); entityManager.clear(); }
    public List<CdsImportDiagnostic> page(String runId, long afterId, int limit) {
        return entityManager.createQuery("select d from CdsImportDiagnostic d where d.runId=:run and d.id>:after order by d.id", CdsImportDiagnostic.class)
                .setParameter("run", runId).setParameter("after", afterId).setMaxResults(limit).getResultList();
    }
    public long unresolved(String runId, String patientKey) {
        String jpql="select count(d) from CdsImportDiagnostic d where d.runId=:run and d.blocking=true and d.resolved=false"+(patientKey==null?"":" and (d.patientKey=:patient or d.patientKey is null)");
        var query=entityManager.createQuery(jpql, Long.class).setParameter("run", runId);
        if (patientKey!=null) query.setParameter("patient", patientKey);
        return query.getSingleResult();
    }
    public long warnings(String runId, String patientKey) {
        return entityManager.createQuery("select count(d) from CdsImportDiagnostic d where d.runId=:run and d.patientKey=:patient and d.severity='WARNING'", Long.class)
                .setParameter("run", runId).setParameter("patient", patientKey).getSingleResult();
    }
    public List<CdsImportDiagnostic> unresolvedStage(String runId, String stage, int limit) {
        return entityManager.createQuery("select d from CdsImportDiagnostic d where d.runId=:run and d.stage=:stage and d.blocking=true and d.resolved=false order by d.id", CdsImportDiagnostic.class).setParameter("run", runId).setParameter("stage", stage).setMaxResults(limit).getResultList();
    }
    public List<CdsImportDiagnostic> unresolvedOperationalCode(String runId, String code, int limit) {
        return entityManager.createQuery("select d from CdsImportDiagnostic d where d.runId=:run and d.patientKey is null and d.blocking=false and d.resolved=false and d.code=:code order by d.id", CdsImportDiagnostic.class)
                .setParameter("run", runId).setParameter("code", code).setMaxResults(limit).getResultList();
    }
}
