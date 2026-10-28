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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.commn.dao.CdsImportDiagnosticDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportPatientDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportReviewDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportDiagnostic;
import io.github.carlos_emr.carlos.commn.model.CdsImportPatient;
import io.github.carlos_emr.carlos.commn.model.CdsImportReview;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;

/** Shared real-query contract matrix, run on in-memory Hibernate and the required native database. */
final class CdsReadProjectionTestFixture {
    private static final String FAILED="MAINTENANCE_RELEASE_REQUIRES_RECONCILIATION";
    private static final String DEFERRED="MAINTENANCE_RELEASE_DEFERRED";
    private static final String PENDING="MAINTENANCE_RELEASE_PENDING";
    private CdsReadProjectionTestFixture() { }

    static void verify(EntityManager entityManager, String run, String other) {
        var patients=new CdsImportPatientDao(); var diagnostics=new CdsImportDiagnosticDao(); var reviews=new CdsImportReviewDao();
        for (Object dao:List.of(patients,diagnostics,reviews)) ReflectionTestUtils.setField(dao,"entityManager",entityManager);
        assertThat(patients.summaryOutcomes(run,17)).isEmpty();
        assertThat(diagnostics.summary(run,FAILED,DEFERRED,PENDING).unresolved()).isZero();
        assertThat(reviews.summary(run).state()).isEqualTo("IDLE");
        patient(entityManager,run,"first","same-source","PLANNED");
        patient(entityManager,run,"second","same-source","REFUSED");
        var changedPatient=patient(entityManager,run,"third","other-source","PLANNED");
        patient(entityManager,other,"foreign","foreign-source","FOREIGN");
        var blocker=diagnostic(entityManager,run,"BLOCKED",true,false,null);
        diagnostic(entityManager,run,"RESOLVED",true,true,null);
        var failure=diagnostic(entityManager,run,FAILED,false,false,null);
        diagnostic(entityManager,run,FAILED,false,false,"patient");
        diagnostic(entityManager,run,FAILED,false,true,null);
        diagnostic(entityManager,run,DEFERRED,false,false,null);
        diagnostic(entityManager,run,PENDING,false,false,null);
        diagnostic(entityManager,run,PENDING,false,false,null);
        var foreignBlocker=diagnostic(entityManager,other,FAILED,true,false,null);
        int version=0;
        for (String state:List.of("REQUESTED","VALIDATING","OVERLAY_PUBLISHED","PATIENT_DECLINED","REVISION_PUBLISHED"))
            review(entityManager,run,blocker.getId(),++version,state,"pending-"+version);
        review(entityManager,run,blocker.getId(),++version,"DRAFT_REQUIRES_CORRECTION","superseded");
        review(entityManager,run,blocker.getId(),++version,"OVERLAY_READY","superseded");
        var draft=review(entityManager,run,blocker.getId(),++version,"DRAFT_REQUIRES_CORRECTION","still-inactive");
        review(entityManager,run,blocker.getId(),++version,"DRAFT_REQUIRES_CORRECTION","rejected-later");
        review(entityManager,run,blocker.getId(),++version,"REJECTED","rejected-later");
        review(entityManager,run,blocker.getId(),++version,"DRAFT_REQUIRES_CORRECTION",null);
        review(entityManager,run,blocker.getId(),++version,"REVALIDATED",null);
        var latest=review(entityManager,run,blocker.getId(),++version,"REJECTED","latest"); latest.setSafeErrorCode("CDS_XML_SCHEMA_INVALID");
        review(entityManager,other,foreignBlocker.getId(),99,"VALIDATING","foreign");
        entityManager.flush();
        var statistics=entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean enabled=statistics.isStatisticsEnabled(); statistics.setStatisticsEnabled(true);
        try {
            long before=statistics.getPrepareStatementCount();
            var outcomes=patients.summaryOutcomes(run,17); var diagnosticSummary=diagnostics.summary(run,FAILED,DEFERRED,PENDING);
            var reviewSummary=reviews.summary(run);
            assertThat(statistics.getPrepareStatementCount()-before).isEqualTo(3);
            assertThat(outcomes.stream().mapToLong(row->row.copies()).sum()).isEqualTo(3);
            assertThat(outcomes).allSatisfy(row->assertThat(row.sourcePatients()).isEqualTo(2));
            Map<String,Long> legacy=patients.outcomes(run,17).stream().collect(Collectors.toMap(row->(String)row[0],row->((Number)row[1]).longValue()));
            assertThat(outcomes.stream().collect(Collectors.toMap(row->row.state(),row->row.copies()))).isEqualTo(legacy);
            assertThat(patients.sourcePatients(run)).isEqualTo(2);
            assertThat(diagnosticSummary.unresolved()).isEqualTo(diagnostics.unresolved(run,null)).isEqualTo(1);
            assertThat(diagnosticSummary.releaseFailures()).isEqualTo(1);
            assertThat(diagnosticSummary.releaseDeferred()).isEqualTo(1); assertThat(diagnosticSummary.releasePending()).isEqualTo(1);
            assertThat(diagnostics.unresolvedOperationalCode(run,FAILED,1)).hasSize(1);
            assertThat(reviewSummary.id()).isEqualTo(reviews.latest(run).getId()).isEqualTo(latest.getId());
            assertThat(reviewSummary.state()).isEqualTo("REJECTED"); assertThat(reviewSummary.errorCode()).isEqualTo("CDS_XML_SCHEMA_INVALID");
            assertThat(reviewSummary.pending()).isEqualTo(reviews.pendingRun(run,2).size()).isEqualTo(2);
            assertThat(reviewSummary.inactiveDrafts()).isEqualTo(reviews.inactiveDraftCount(run)).isEqualTo(3);
            failure.setResolved(true); draft.setState("OVERLAY_READY"); latest.setState("REQUESTED"); latest.setSafeErrorCode(null);
            changedPatient.setState("FUTURE_OUTCOME"); entityManager.flush();
            assertThat(diagnostics.summary(run,FAILED,DEFERRED,PENDING).releaseFailures()).isZero();
            assertThat(reviews.summary(run).inactiveDrafts()).isEqualTo(2);
            assertThat(reviews.summary(run).state()).isEqualTo("REQUESTED"); assertThat(reviews.summary(run).errorCode()).isNull();
            assertThat(patients.summaryOutcomes(run,17)).anySatisfy(row->assertThat(row.state()).isEqualTo("FUTURE_OUTCOME"));
            assertThat(patients.summaryOutcomes(run,17)).allSatisfy(row->assertThat(row.sourcePatients()).isEqualTo(2));
        } finally { statistics.setStatisticsEnabled(enabled); }
    }

    private static CdsImportPatient patient(EntityManager manager,String run,String key,String source,String state) {
        var patient=new CdsImportPatient(); patient.setId(CdsImportKeys.hash(run,key)); patient.setRunId(run); patient.setPatientKey(key);
        patient.setSourcePatientId(source); patient.setCopyKey(key); patient.setLineageKey(CdsImportKeys.hash(run,"lineage",key));
        patient.setSourceDigest("a".repeat(64)); patient.setState(state); manager.persist(patient); return patient;
    }
    private static CdsImportDiagnostic diagnostic(EntityManager manager,String run,String code,boolean blocking,boolean resolved,String patient) {
        var diagnostic=new CdsImportDiagnostic(); diagnostic.setRunId(run); diagnostic.setCode(code); diagnostic.setPatientKey(patient);
        diagnostic.setBlocking(blocking); diagnostic.setResolved(resolved); diagnostic.setSeverity("INFO"); diagnostic.setStage("MAINTENANCE");
        diagnostic.setSafeSummary("Synthetic projection proof"); diagnostic.setCreatedAt(Instant.EPOCH); manager.persist(diagnostic); return diagnostic;
    }
    private static CdsImportReview review(EntityManager manager,String run,long diagnosticId,long version,String state,String source) {
        var review=new CdsImportReview(); review.setId(UUID.randomUUID().toString()); review.setRunId(run); review.setReviewVersion(version);
        review.setKind("CORRECT_VALUE"); review.setState(state); review.setSourceKey(source); review.setDiagnosticId(diagnosticId);
        review.setOperator("fixture"); review.setProviderNo("999998"); review.setCreatedAt(Instant.EPOCH);
        review.setPayload("{}"); review.setPayloadSha256("a".repeat(64)); review.setPlanLocation(""); review.setPlanSha256("");
        manager.persist(review); return review;
    }
}
