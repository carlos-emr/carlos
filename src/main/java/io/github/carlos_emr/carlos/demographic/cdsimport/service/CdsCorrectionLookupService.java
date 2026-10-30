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

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;

import io.github.carlos_emr.carlos.commn.dao.CdsImportDiagnosticDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportReviewDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportRunDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportPatientDao;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsCorrectionSetDto;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Selects an audited immutable overlay; only a complete fresh preflight can supersede prior source blockers. */
@Service
public class CdsCorrectionLookupService {
    private final CdsImportReviewDao reviews;
    private final CdsImportRunDao runs;
    private final CdsImportPatientDao patients;
    private final CdsImportDiagnosticDao diagnostics;
    private final CdsImportLedgerService ledger;
    private final CdsImportJobService jobs;
    private final TransactionTemplate transaction;

    public CdsCorrectionLookupService(CdsImportReviewDao reviews, CdsImportRunDao runs, CdsImportPatientDao patients,
            CdsImportDiagnosticDao diagnostics, CdsImportLedgerService ledger, CdsImportJobService jobs,
            PlatformTransactionManager manager) {
        this.reviews=reviews; this.runs=runs; this.patients=patients; this.diagnostics=diagnostics; this.ledger=ledger; this.jobs=jobs;
        transaction=CdsImportLedgerService.template(manager);
    }

    public long version(String runId) {
        return transaction.execute(status -> {
            var run=runs.get(runId); if (run==null) throw new CdsImportException("IMPORT_RUN_NOT_FOUND");
            return run.getReviewVersion();
        });
    }

    public static String sourceKey(String xmlRelative, String acceptedXmlSha256) {
        return CdsImportKeys.hash("CDS_REVIEW_XML_V1", xmlRelative, acceptedXmlSha256);
    }

    public CdsCorrectionSetDto find(String runId, String xmlRelative, String acceptedXmlSha256, Path parsedDirectory) throws IOException {
        String source=sourceKey(xmlRelative, acceptedXmlSha256);
        java.util.Optional<Selection> found=java.util.Objects.requireNonNull(transaction.execute(status -> {
            var value=reviews.latestCorrection(runId, source);
            return value==null?java.util.Optional.<Selection>empty():java.util.Optional.of(new Selection(value.getManifestLocation(), value.getManifestSha256(), value.getSourceXmlSha256()));
        }));
        if (found.isEmpty()) return null;
        Selection selected=found.get();
        if (!acceptedXmlSha256.equals(selected.xmlDigest())) throw new CdsImportException("REVIEW_ACCEPTED_SOURCE_CHANGED");
        Path root=jobs.root(runId).resolve("corrections").resolve(source);
        Path supplied=Path.of(selected.location());
        if (Files.isSymbolicLink(supplied)) throw new CdsImportException("REVIEW_MANIFEST_LINK_REFUSED");
        Path manifest=CdsPaths.within(supplied, root);
        if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) throw new CdsImportException("REVIEW_MANIFEST_MISSING");
        return new CdsCorrectionSetDto(manifest, selected.digest(), acceptedXmlSha256);
    }

    public boolean declined(String runId, String patientKey) {
        return transaction.execute(status -> reviews.declined(runId, patientKey));
    }

    /** Rebinds only an existing unwritten lineage after an audited overlay reset and fresh pinned-schema parse. */
    public void rebind(String runId, long epoch, long expectedReviewVersion, String xmlRelative, String acceptedXmlSha256,
            String selectedManifestSha256, String patientKey, String newPatientSourceDigest) {
        CdsImportLedgerService.requireNoTransaction();
        CdsImportKeys.digestValue(selectedManifestSha256, "review correction manifest");
        CdsImportKeys.digestValue(newPatientSourceDigest, "review patient source");
        transaction.executeWithoutResult(status -> {
            requireVersion(runId, epoch, expectedReviewVersion);
            var selected=reviews.latestCorrection(runId, sourceKey(xmlRelative, acceptedXmlSha256));
            if (selected==null || !Set.of("OVERLAY_READY", "REVALIDATED").contains(selected.getState())
                    || !acceptedXmlSha256.equals(selected.getSourceXmlSha256())
                    || !selectedManifestSha256.equals(selected.getManifestSha256())) throw new CdsImportException("REVIEW_SOURCE_PROOF_CHANGED");
            var existing=patients.findKey(runId, patientKey);
            if (existing!=null && !newPatientSourceDigest.equals(existing.getSourceDigest())) {
                ledger.reviseUncommittedLineage(runId, epoch, patientKey, expectedReviewVersion, newPatientSourceDigest);
            }
        });
    }

    /** Internal projection only: callers must first pass the existing patient/category source-access gate. */
    public List<io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportReviewProvenanceDto> pageByDemographic(
            int demographic, String category, String afterId, int limit) {
        if (demographic<1 || limit<1 || limit>50 || category==null || category.length()>64
                || (afterId!=null && !afterId.isEmpty() && !afterId.matches("[0-9a-f-]{36}"))) throw new IllegalArgumentException("REVIEW_PAGE_INVALID");
        return transaction.execute(status -> reviews.provenance(demographic, category, afterId==null?"":afterId, limit).stream().map(CdsImportProjections::toDto).toList());
    }

    /**
     * Called by the fenced planner only after all accepted XML, typed values and
     * cross-patient/target identity checks pass again. No diagnostic is retired
     * merely because an operator submitted a correction or clicked a button.
     */
    public void revalidated(String runId, long epoch, long expectedVersion, String prospectiveManifestDigest) {
        CdsImportLedgerService.requireNoTransaction();
        CdsImportKeys.digestValue(prospectiveManifestDigest, "review preflight proof");
        transaction.executeWithoutResult(status -> requireVersion(runId, epoch, expectedVersion));
        if (expectedVersion==0) return;
        long after=0;
        while (true) {
            final long cursor=after;
            List<Long> page=transaction.execute(status -> {
                requireVersion(runId, epoch, expectedVersion);
                var rows=reviews.throughVersion(runId, expectedVersion, cursor, 100);
                for (var row:rows) {
                    if (Set.of("OVERLAY_READY", "REVALIDATED", "DECLINE_READY", "REVISION_READY", "REVISION_REVALIDATED").contains(row.getState())) {
                        row.setRevalidationSha256(prospectiveManifestDigest);
                        if ("OVERLAY_READY".equals(row.getState())) row.setState("REVALIDATED");
                        else if ("REVISION_READY".equals(row.getState())) row.setState("REVISION_REVALIDATED");
                    }
                }
                return rows.stream().map(row -> row.getReviewVersion()).toList();
            });
            if (page.isEmpty()) break;
            after=page.getLast();
        }
        if (after!=expectedVersion) throw new CdsImportException("REVIEW_VERSION_EVIDENCE_MISSING");
        long diagnosticAfter=0;
        while (true) {
            final long cursor=diagnosticAfter;
            List<Long> page=transaction.execute(status -> {
                requireVersion(runId, epoch, expectedVersion);
                var rows=diagnostics.page(runId, cursor, 100);
                for (var row:rows) {
                    if ("PREFLIGHT".equals(row.getStage()) && row.isBlocking() && !row.isResolved()) row.setResolved(true);
                }
                return rows.stream().map(row -> row.getId()).toList();
            });
            if (page.isEmpty()) break;
            diagnosticAfter=page.getLast();
        }
    }

    private void requireVersion(String runId, long epoch, long expectedVersion) {
        if (ledger.lockPlanningOwned(runId, epoch).getReviewVersion()!=expectedVersion) {
            throw new CdsImportException("REVIEW_VERSION_CONFLICT");
        }
    }
    private record Selection(String location, String digest, String xmlDigest) { }
}
