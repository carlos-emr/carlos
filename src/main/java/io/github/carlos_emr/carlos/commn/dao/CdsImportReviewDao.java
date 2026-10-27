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

import io.github.carlos_emr.carlos.commn.model.CdsImportReview;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Bounded protected review selection; policy and state transitions belong to review services. */
@Repository
public class CdsImportReviewDao extends AbstractDaoImpl<CdsImportReview> {
    public CdsImportReviewDao(){super(CdsImportReview.class); }
    public CdsImportReview latestCorrection(String run, String source) {
        List<CdsImportReview> rows=entityManager.createQuery("select r from CdsImportReview r where r.runId=:run and r.sourceKey=:source and r.kind='CORRECT_VALUE' and r.state in ('OVERLAY_PUBLISHED','OVERLAY_READY','REVALIDATED') order by r.reviewVersion desc", CdsImportReview.class)
                .setParameter("run", run).setParameter("source", source).setMaxResults(1).getResultList();
        return rows.isEmpty()?null:rows.get(0);
    }
    public boolean declined(String run, String patient) {
        return entityManager.createQuery("select count(r) from CdsImportReview r where r.runId=:run and r.patientKey=:patient and r.kind='DECLINE_PATIENT' and r.state in ('PATIENT_DECLINED','DECLINE_READY')", Long.class)
                .setParameter("run", run).setParameter("patient", patient).getSingleResult()>0;
    }
    public List<CdsImportReview> throughVersion(String run, long version, long after, int limit) {
        return entityManager.createQuery("select r from CdsImportReview r where r.runId=:run and r.reviewVersion<=:version and r.reviewVersion>:after order by r.reviewVersion", CdsImportReview.class)
                .setParameter("run", run).setParameter("version", version).setParameter("after", after).setMaxResults(limit).getResultList();
    }
    public CdsImportReview get(String id){return entityManager.find(CdsImportReview.class, id); }
    public CdsImportReview lock(String id){var row=entityManager.find(CdsImportReview.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE); if (row!=null) entityManager.refresh(row, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE); return row; }
    public List<CdsImportReview> pending(int limit) {
        return entityManager.createQuery("select r from CdsImportReview r where r.state in ('REQUESTED','VALIDATING','OVERLAY_PUBLISHED','PATIENT_DECLINED','REVISION_PUBLISHED') and (r.safeErrorCode is null or r.safeErrorCode<>'REVIEW_WORKER_FATAL') order by r.createdAt,r.id", CdsImportReview.class).setMaxResults(limit).getResultList();
    }
    public List<CdsImportReview> pendingRun(String run, int limit) {
        return entityManager.createQuery("select r from CdsImportReview r where r.runId=:run and r.state in ('REQUESTED','VALIDATING','OVERLAY_PUBLISHED','PATIENT_DECLINED','REVISION_PUBLISHED') order by r.reviewVersion", CdsImportReview.class).setParameter("run", run).setMaxResults(limit).getResultList();
    }
    public CdsImportReview latest(String run) {
        var rows=entityManager.createQuery("select r from CdsImportReview r where r.runId=:run order by r.reviewVersion desc", CdsImportReview.class).setParameter("run", run).setMaxResults(1).getResultList(); return rows.isEmpty()?null:rows.get(0);
    }
    public CdsImportReview latestDraft(String run, String source) {
        var rows=entityManager.createQuery("select r from CdsImportReview r where r.runId=:run and r.sourceKey=:source and r.kind='CORRECT_VALUE' and r.state='DRAFT_REQUIRES_CORRECTION' order by r.reviewVersion desc", CdsImportReview.class).setParameter("run", run).setParameter("source", source).setMaxResults(1).getResultList(); return rows.isEmpty()?null:rows.get(0);
    }
    public long inactiveDraftCount(String run) {
        return entityManager.createQuery("select count(r) from CdsImportReview r where r.runId=:run and r.state='DRAFT_REQUIRES_CORRECTION' and not exists(select a.id from CdsImportReview a where a.runId=r.runId and a.sourceKey=r.sourceKey and a.reviewVersion>r.reviewVersion and a.state in ('OVERLAY_PUBLISHED','OVERLAY_READY','REVALIDATED'))", Long.class)
                .setParameter("run", run).getSingleResult();
    }
    public List<io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportReviewOriginDto> origins(String run, java.util.Collection<String> ids) {
        if (ids.isEmpty()) return List.of(); if (ids.size()>128) throw new IllegalArgumentException("REVIEW_ORIGIN_CAPACITY_EXCEEDED");
        return entityManager.createQuery("select new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportReviewOriginDto(r.id,r.operator,r.securityNo,r.sourceKey,r.reviewedSourceXPath,r.state,r.reviewVersion) from CdsImportReview r where r.runId=:run and r.id in :ids", io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportReviewOriginDto.class).setParameter("run", run).setParameter("ids", ids).getResultList();
    }
    public List<io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportReviewProvenanceDto> provenance(int demographic, String category, String after, int limit) {
        return entityManager.createQuery("select new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportReviewProvenanceDto(r.id,r.operator,r.appliedAt,r.kind,r.state,r.reviewReason,r.reviewVersion,r.reviewedSourceXPath) from CdsImportReview r where r.id>:after and r.state in ('OVERLAY_READY','DECLINE_READY','REVALIDATED','REVISION_READY','REVISION_REVALIDATED') and (r.category=:category or r.kind in ('DECLINE_PATIENT','APPROVE_UNWRITTEN_REVISION')) and exists(select p.id from CdsImportPatient p where p.targetDemographicNo=:target and p.runId=r.runId and p.sourcePatientId=r.sourcePatientId and (r.patientKey is null or r.patientKey=p.patientKey)) order by r.id", io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportReviewProvenanceDto.class)
                .setParameter("after", after).setParameter("category", category).setParameter("target", demographic).setMaxResults(limit).getResultList();
    }
}
