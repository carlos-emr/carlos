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

import io.github.carlos_emr.carlos.commn.model.CdsImportRun;
import io.github.carlos_emr.carlos.commn.dto.CdsImportJobBindingDto;
import io.github.carlos_emr.carlos.commn.dto.CdsImportReportEntryDto;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Data access for one protected CDS import table; orchestration belongs in services. */
@Repository
public class CdsImportRunDao extends AbstractDaoImpl<CdsImportRun>
        implements CdsExportRetainedTableReadDao {
    @Override
    @org.springframework.transaction.annotation.Transactional(
            readOnly = true,
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto
            readCdsExportRetainedSchema() {
        return CdsExportPatientTableReadSupport.schema(
                entityManager, CdsExportPatientTableReadSupport.Plan.IMPORT_RUNS);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(
            readOnly = true,
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public long countCdsExportRetainedRows(int patient, java.util.Set<String> categories) {
        return CdsExportPatientTableReadSupport.retainedCount(
                entityManager,
                CdsExportPatientTableReadSupport.Plan.IMPORT_RUNS,
                patient,
                categories);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(
            readOnly = true,
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public java.util.List<io.github.carlos_emr.carlos.commn.dto.CdsExportRawKeyRowDto>
            findCdsExportRetainedRowsAfter(
                    int patient, java.util.Set<String> categories, String afterId, int limit) {
        return CdsExportPatientTableReadSupport.retainedPage(
                entityManager,
                CdsExportPatientTableReadSupport.Plan.IMPORT_RUNS,
                patient,
                categories,
                afterId,
                limit);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(
            readOnly = true,
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public byte[] readCdsExportRetainedChunk(
            int patient,
            java.util.Set<String> categories,
            String rowId,
            String column,
            long offset,
            int length) {
        return CdsExportPatientTableReadSupport.retainedChunk(
                entityManager,
                CdsExportPatientTableReadSupport.Plan.IMPORT_RUNS,
                patient,
                categories,
                rowId,
                column,
                offset,
                length);
    }

    /** Exact selected applied copy keys for one patient, inside the caller's export snapshot. */
    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public List<String> readAppliedCdsExportCopyKeys(
            int patient, String runId, java.util.Set<String> selected) {
        var allowed =
                java.util.Arrays.stream(
                                io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                                        .values())
                        .map(
                                io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                                        ::wireName)
                        .collect(java.util.stream.Collectors.toSet());
        if (patient < 1
                || runId == null
                || !runId.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                || selected == null
                || selected.isEmpty()
                || !allowed.containsAll(selected)
                || !org.springframework.transaction.support.TransactionSynchronizationManager
                        .isActualTransactionActive()
                || !org.springframework.transaction.support.TransactionSynchronizationManager
                        .isCurrentTransactionReadOnly())
            throw new IllegalArgumentException("CDS_EXPORT_APPLIED_COPY_SCOPE_INVALID");
        var categories = new java.util.TreeSet<String>();
        selected.forEach(
                category -> {
                    categories.add(category);
                    for (String suffix : java.util.List.of("_FIELDS", "_CONTACT", "_CARE"))
                        categories.add(category + suffix);
                });
        var copies =
                entityManager
                        .createQuery(
                                """
                select distinct p.copyKey from CdsImportPatient p
                where p.runId=:run and p.targetDemographicNo=:patient and exists
                    (select u.id from CdsImportUnit u where u.runId=p.runId and u.patientKey=p.patientKey
                    and u.category in :categories and u.state in ('COMMITTED','ALREADY_COMPLETE'))
                order by p.copyKey
                """,
                                String.class)
                        .setParameter("run", runId)
                        .setParameter("patient", patient)
                        .setParameter("categories", categories)
                        .setHint("jakarta.persistence.query.timeout", 120_000)
                        .setMaxResults(4097)
                        .getResultList();
        if (copies.size() > 4096) throw new IllegalStateException("CDS_EXPORT_APPLIED_COPY_LIMIT");
        return List.copyOf(copies);
    }

    public CdsImportRunDao() {
        super(CdsImportRun.class);
    }

    public CdsImportRun lock(String id) {
        return entityManager.find(CdsImportRun.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    public CdsImportRun get(String id) {
        return entityManager.find(CdsImportRun.class, id);
    }

    /** One current scalar statement; no entity or persistence scope escapes before private file I/O. */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public CdsImportJobBindingDto readJobBinding(String runId) {
        var rows =
                entityManager
                        .createQuery(
                                """
                select new io.github.carlos_emr.carlos.commn.dto.CdsImportJobBindingDto(
                    r.id,r.operator,r.providerNo,r.sourceNamespace,r.profile,r.state,
                    r.sourceDigest,r.optionsDigest,r.rulesetDigest,r.manifestDigest,
                    r.expectedPatients,r.expectedUnits,r.ownerEpoch,r.cancelRequested,
                    r.createdAt,r.updatedAt,r.reportGeneration,r.reportLocation,r.reportDigest,r.snapshotLocation)
                from CdsImportRun r where r.id=:run
                """,
                                CdsImportJobBindingDto.class)
                        .setParameter("run", runId)
                        .setMaxResults(1)
                        .setHint("jakarta.persistence.query.timeout", 120_000)
                        .getResultList();
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** Fixed run-row facts only; no patient, unit or diagnostic aggregates precede report I/O. */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public CdsImportReportEntryDto readReportEntry(String runId) {
        var rows =
                entityManager
                        .createQuery(
                                """
                select new io.github.carlos_emr.carlos.commn.dto.CdsImportReportEntryDto(
                    r.reportState,r.discoveryComplete,r.dryRun,r.executionDigest is not null,r.snapshotLocation)
                from CdsImportRun r where r.id=:run
                """,
                                CdsImportReportEntryDto.class)
                        .setParameter("run", runId)
                        .setMaxResults(1)
                        .setHint("jakarta.persistence.query.timeout", 120_000)
                        .getResultList();
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** Rechecked under the run lock before any preparation epoch can replace a stopped review. */
    public boolean reviewBlocksExecution(String runId) {
        return !entityManager
                .createQuery(
                        "select r.id from CdsImportRun r where r.id=:run and exists(select q.id from CdsImportReview q where q.runId=r.id and (q.state in ('REQUESTED','VALIDATING','OVERLAY_PUBLISHED','PATIENT_DECLINED','REVISION_PUBLISHED') or (q.state='DRAFT_REQUIRES_CORRECTION' and not exists(select newer.id from CdsImportReview newer where newer.runId=q.runId and newer.sourceKey=q.sourceKey and newer.reviewVersion>q.reviewVersion and newer.state in ('OVERLAY_PUBLISHED','OVERLAY_READY','REVALIDATED')))))",
                        String.class)
                .setParameter("run", runId)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    public void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    public CdsImportRun findSubmission(String operator, String submissionKey) {
        return entityManager
                .createQuery(
                        "select r from CdsImportRun r where r.operator=:operator and r.submissionKey=:key",
                        CdsImportRun.class)
                .setParameter("operator", operator)
                .setParameter("key", submissionKey)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);
    }

    public List<CdsImportRun> pending(String afterId, int limit) {
        var active =
                entityManager
                        .createQuery(
                                "select r from CdsImportRun r where r.id>:after and r.state in ('RESUME_REQUESTED','ACCEPTED','DISCOVERING','VALIDATING','PREFLIGHT','PREFLIGHTING','PLANNING','SEALING','READY','RUNNING','FINALIZING','REPORTING') order by r.id",
                                CdsImportRun.class)
                        .setParameter("after", afterId)
                        .setMaxResults(limit)
                        .getResultList();
        var reports =
                entityManager
                        .createQuery(
                                "select r from CdsImportRun r where r.id>:after and r.reportState='PENDING' and r.state in ('COMPLETE','DRY_RUN_COMPLETE','DRY_RUN_INCOMPLETE','INCOMPLETE','RECONCILIATION_REQUIRED','REVIEW_REQUIRED','FAILED_BEFORE_WRITE','CANCELLED_BEFORE_WRITE','ABANDONED') order by r.id",
                                CdsImportRun.class)
                        .setParameter("after", afterId)
                        .setMaxResults(limit)
                        .getResultList();
        var resumed =
                entityManager
                        .createQuery(
                                "select r from CdsImportRun r where r.id>:after and r.resumeRequested=true order by r.id",
                                CdsImportRun.class)
                        .setParameter("after", afterId)
                        .setMaxResults(limit)
                        .getResultList();
        return java.util.stream.Stream.of(active, reports, resumed)
                .flatMap(List::stream)
                .collect(
                        java.util.stream.Collectors.toMap(
                                CdsImportRun::getId,
                                java.util.function.Function.identity(),
                                (first, second) -> first,
                                java.util.TreeMap::new))
                .values()
                .stream()
                .limit(limit)
                .toList();
    }

    public List<CdsImportRun> page(String operator, String afterId, int limit) {
        return entityManager
                .createQuery(
                        "select r from CdsImportRun r where r.operator=:operator and r.id>:after order by r.id",
                        CdsImportRun.class)
                .setParameter("operator", operator)
                .setParameter("after", afterId)
                .setMaxResults(limit)
                .getResultList();
    }
}
