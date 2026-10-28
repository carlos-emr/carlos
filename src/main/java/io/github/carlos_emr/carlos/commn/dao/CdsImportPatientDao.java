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

import io.github.carlos_emr.carlos.commn.model.CdsImportPatient;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Data access for one protected CDS import table; orchestration belongs in services. */
@Repository
public class CdsImportPatientDao extends AbstractDaoImpl<CdsImportPatient> {
    public CdsImportPatientDao() { super(CdsImportPatient.class); }
    public CdsImportPatient lock(String id) { return entityManager.find(CdsImportPatient.class, id, LockModeType.PESSIMISTIC_WRITE); }
    public CdsImportPatient get(String id) { return entityManager.find(CdsImportPatient.class, id); }
    public void flushAndClear() { entityManager.flush(); entityManager.clear(); }
    public CdsImportPatient findKey(String runId, String patientKey) {
        return entityManager.createQuery("select p from CdsImportPatient p where p.runId=:run and p.patientKey=:key", CdsImportPatient.class)
                .setParameter("run", runId).setParameter("key", patientKey).setMaxResults(1).getResultList().stream().findFirst().orElse(null);
    }
    public List<CdsImportPatient> page(String runId, String afterKey, int limit) {
        return entityManager.createQuery("select p from CdsImportPatient p where p.runId=:run and p.patientKey>:after order by p.patientKey", CdsImportPatient.class)
                .setParameter("run", runId).setParameter("after", afterKey).setMaxResults(limit).getResultList();
    }
    public long count(String runId) {
        return entityManager.createQuery("select count(p) from CdsImportPatient p where p.runId=:run", Long.class).setParameter("run", runId).getSingleResult();
    }
    public List<Object[]> outcomes(String runId, int limit) {
        return entityManager.createQuery("select p.state,count(p) from CdsImportPatient p where p.runId=:run group by p.state order by p.state", Object[].class)
                .setParameter("run", runId).setMaxResults(limit).getResultList();
    }
    /** A single current statement supplies bounded groups and the global distinct-source scalar. */
    public List<io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportPatientOutcomeDto> summaryOutcomes(String runId, int limit) {
        return entityManager.createQuery("select new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportPatientOutcomeDto(p.state,count(p),(select count(distinct q.sourcePatientId) from CdsImportPatient q where q.runId=:run)) from CdsImportPatient p where p.runId=:run group by p.state order by p.state", io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportPatientOutcomeDto.class)
                .setParameter("run", runId).setMaxResults(limit).getResultList();
    }

    public long sourcePatients(String runId) {
        return entityManager.createQuery("select count(distinct p.sourcePatientId) from CdsImportPatient p where p.runId=:run", Long.class).setParameter("run", runId).getSingleResult();
    }
    public long heldForRun(String runId) {
        return entityManager.createQuery("select count(p) from CdsImportPatient p where p.runId=:run and p.held=true", Long.class).setParameter("run", runId).getSingleResult();
    }
    public boolean held(int demographicNo) {
        return entityManager.createQuery("select count(p) from CdsImportPatient p where p.targetDemographicNo=:demo and p.held=true", Long.class)
                .setParameter("demo", demographicNo).getSingleResult()>0;
    }
    public java.util.List<CdsImportPatient> keys(String runId, java.util.Collection<String> keys) {
        if (keys.isEmpty()) return java.util.List.of();
        return entityManager.createQuery("select p from CdsImportPatient p where p.runId=:run and p.patientKey in :keys", CdsImportPatient.class)
                .setParameter("run", runId).setParameter("keys", keys).setMaxResults(200).getResultList();
    }
    public long actualAttempts(String lineageKey) {
        return entityManager.createQuery("select count(p) from CdsImportPatient p,CdsImportRun r where p.lineageKey=:lineage and p.runId=r.id and r.dryRun=false", Long.class).setParameter("lineage", lineageKey).getSingleResult();
    }
    public List<String> lineageRuns(String lineageKey, String excludingRun, String afterRun, int limit) {
        return entityManager.createQuery("select distinct p.runId from CdsImportPatient p where p.lineageKey=:lineage and p.runId<>:run and p.runId>:after order by p.runId", String.class)
                .setParameter("lineage", lineageKey).setParameter("run", excludingRun).setParameter("after", afterRun).setMaxResults(limit).getResultList();
    }
    public long lineageRunCount(String lineageKey, String excludingRun) {
        return entityManager.createQuery("select count(distinct p.runId) from CdsImportPatient p where p.lineageKey=:lineage and p.runId<>:run", Long.class)
                .setParameter("lineage", lineageKey).setParameter("run", excludingRun).getSingleResult();
    }
    public boolean lineageHasWrittenOrUncertainAttempt(String lineageKey) {
        return !entityManager.createQuery("select p.id from CdsImportPatient p where p.lineageKey=:lineage and (p.held=true or p.targetDemographicNo is not null or p.state='RECONCILIATION_REQUIRED' or exists(select u.id from CdsImportUnit u where u.runId=p.runId and u.patientKey=p.patientKey and u.state<>'EXPECTED') or exists(select d.id from CdsImportDiagnostic d where d.runId=p.runId and d.resolved=false and d.code in ('UNIT_COMMIT_OUTCOME_UNKNOWN','CONTROL_COMMIT_OUTCOME_UNKNOWN') and (d.patientKey is null or d.patientKey=p.patientKey)))", String.class)
                .setParameter("lineage", lineageKey).setMaxResults(1).getResultList().isEmpty();
    }
    public boolean runHasWrittenOrUncertainAttempt(String runId) {
        return !entityManager.createQuery("select p.id from CdsImportPatient p where p.runId=:run and (p.held=true or p.targetDemographicNo is not null or p.state='RECONCILIATION_REQUIRED' or exists(select c.id from CdsImportCheckpoint c where c.lineageKey=p.lineageKey) or exists(select u.id from CdsImportUnit u where u.runId=p.runId and u.state<>'EXPECTED'))", String.class)
                .setParameter("run", runId).setMaxResults(1).getResultList().isEmpty();
    }
}
