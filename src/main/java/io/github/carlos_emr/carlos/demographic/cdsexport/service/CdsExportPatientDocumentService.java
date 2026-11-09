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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportCoverageValidator;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

/**
 * One private patient document transaction shared by all engine adapters. Expected source
 * identities are captured before invoking any mapper. The finished XML is reopened through
 * the independent schema/identity/reference validator. No output becomes downloadable here;
 * whole-chart catalogue qualification and independent clinical-value proof remain separate.
 */
@Service
public class CdsExportPatientDocumentService {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final CdsExportStagingFactory stores;
    private final CdsExportNativeCaptureService captures;
    private final CdsExportPatientInventoryService inventories;
    private final CdsExportDemographicOccurrenceService demographics;
    private final CdsExportClinicalOccurrenceService clinical;

    public CdsExportPatientDocumentService(CdsExportStagingFactory stores, CdsExportNativeCaptureService captures,
            CdsExportPatientInventoryService inventories, CdsExportDemographicOccurrenceService demographics,
            CdsExportClinicalOccurrenceService clinical) {
        this.stores = Objects.requireNonNull(stores); this.captures = Objects.requireNonNull(captures);
        this.inventories = Objects.requireNonNull(inventories); this.demographics = Objects.requireNonNull(demographics);
        this.clinical = Objects.requireNonNull(clinical);
    }

    // Cleanup only throws without a primary failure; otherwise its error is suppressed on that failure.
    @SuppressWarnings("java:S1143")
    public Document compose(CdsExportLeaseDto lease, CdsExportCohortService.Patient patient, Set<String> requiredTables,
            CdsExportPatientCaptureDto source, CdsExportStagedFileDto payloadManifest,
            CdsExportClinicalOccurrenceService.Bounds bounds, String applicationVersion, CdsExportXmlLimitsDto limits,
            CdsExportRunControl control) throws IOException {
        patient.require(lease); Objects.requireNonNull(limits); Objects.requireNonNull(control);
        var tables = Set.copyOf(requiredTables);
        String patientKey = CdsImportKeys.hash("carlos-export-patient-v1", lease.runId(), lease.document().senderNamespace(), Integer.toString(patient.patient()));
        var expected = inventories.capture(lease, patient, patientKey, tables, source, bounds, control);
        var store = stores.openExisting(lease.runId(), lease.epoch(), lease.document().storage(), control::checkpoint);
        var owned = new ArrayList<CdsExportStagedFileDto>(); Throwable primary = null; boolean complete = false;
        var attachments = new TreeMap<String, CdsExportPackageMemberDto>();
        var attachmentOwners = new HashMap<String, CdsExportIdentityDto>();
        var dispositions = new TreeMap<CdsExportIdentityDto, CdsExportDispositionDto>();
        var receipts = new TreeSet<CdsExportIdentityDto>(); var retainedFields = new HashSet<String>();
        var failed = new AtomicBoolean(); var active = new AtomicBoolean(true); Thread ownerThread = Thread.currentThread();
        var xml = new CdsExportStagedFileDto[1]; var composed = new CdsExportXmlComposer.Result[1];
        try {
            var reconciliation = store.stage("PATIENT_RECONCILIATION", audit -> {
                long[] auditBytes = {0};
                var sink = new CdsExportClinicalOccurrenceService.Receipts() {
                    @Override public void accept(CdsExportClinicalOccurrenceService.Receipt receipt) throws IOException {
                        try {
                            if (!active.get() || Thread.currentThread() != ownerThread) invalid();
                            control.checkpoint();
                            if (!expected.sourceManifestSha256().equals(receipt.sourceManifestSha256())
                                    || !expected.identities().contains(receipt.identity()) || !dispositions.containsKey(receipt.identity())
                                    || !receipts.add(receipt.identity())) invalid();
                            var all = new HashSet<>(receipt.retainedFields());
                            if (!Collections.disjoint(all, receipt.replacedFields())) invalid(); all.addAll(receipt.replacedFields());
                            for (String id : all) if (!expected.retainedFields().contains(id) || !retainedFields.add(id)) invalid();
                            line(audit, new ReceiptHeader(receipt.identity(), receipt.table(), receipt.nativeId(), receipt.importedUnit(),
                                    new TreeSet<>(receipt.retainedFields()), new TreeSet<>(receipt.replacedFields())), auditBytes, bounds.indexBytes());
                            for (var entry : receipt.fields().entrySet()) {
                                control.checkpoint();
                                if (entry.getValue().outcome() == CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW) review();
                                line(audit, new FieldDecision(receipt.identity(), entry.getKey(), entry.getValue()), auditBytes, bounds.indexBytes());
                            }
                        } catch (IOException | RuntimeException | Error failure) { failed.set(true); throw failure; }
                    }
                    @Override public void attachment(CdsExportIdentityDto report, CdsExportPackageMemberDto member) throws IOException {
                        try {
                            if (!active.get() || Thread.currentThread() != ownerThread) invalid();
                            control.checkpoint(); Objects.requireNonNull(member);
                            if (!expected.identities().contains(report) || report.category() != CdsCategory.REPORTS
                                    || !"REPORT_ATTACHMENT".equals(member.file().purpose()) || !member.relativePath().startsWith("Reports/")) invalid();
                            CdsExportXmlValidator.relativeReference(member.relativePath());
                            if (attachments.size() >= bounds.indexRows() || attachments.putIfAbsent(member.relativePath(), member) != null
                                    || attachmentOwners.putIfAbsent(member.file().memberId(), report) != null) invalid();
                            owned.add(member.file());
                        } catch (IOException | RuntimeException | Error failure) { failed.set(true); throw failure; }
                    }
                };
                xml[0] = store.stage("PATIENT_XML", output -> composed[0] = new CdsExportXmlComposer().composeFromSource(output,
                        expected.sourceManifestSha256(), patientKey, applicationVersion, lease.document().command().profile(),
                        lease.document().command().categories(), emitter -> {
                            if (Collections.disjoint(lease.document().command().categories(), CdsExportNoteSourceReader.CATEGORIES)) {
                                append(lease, patient, patientKey, tables, source, payloadManifest, bounds, control, emitter, sink, null);
                            } else {
                                clinical.withHistorySource(lease, patient, patientKey, tables, source, payloadManifest, bounds, control, emitter,
                                        history -> append(lease, patient, patientKey, tables, source, payloadManifest, bounds, control, emitter, sink, history));
                            }
                        }, disposition -> {
                            control.checkpoint();
                            if (dispositions.size() >= bounds.indexRows() || !expected.identities().contains(disposition.identity())
                                    || dispositions.putIfAbsent(disposition.identity(), disposition) != null) invalid();
                        }, limits.maxBytes(), control::checkpoint));
                owned.add(xml[0]);
                active.set(false);
                if (failed.get() || !receipts.equals(dispositions.keySet()) || !retainedFields.equals(expected.retainedFields())) invalid();
                for (var owner : attachmentOwners.values()) if (!receipts.contains(owner)) invalid();
                for (var disposition : dispositions.values()) line(audit, disposition, auditBytes, bounds.indexBytes());
                var coverage = new CdsExportCoverageValidator().validate(expected.sourceManifestSha256(), expected.identities().iterator(),
                        dispositions.values().iterator(), bounds.indexRows(), control::checkpoint);
                if (!coverage.sourceCoverageComplete()) invalid();
                line(audit, coverage, auditBytes, bounds.indexBytes());
            });
            owned.add(reconciliation);
            var references = new ArrayList<CdsExportReportReferenceDto>();
            CdsExportXmlValidationDto checked;
            try (var input = store.open(xml[0])) {
                checked = new CdsExportXmlValidator().validate(input,
                        new CdsExportPatientIdentityReconciler().identity(lease.document().senderNamespace(), patient.patient()).senderPatientId(),
                        lease.document().command().categories(), applicationVersion, lease.document().command().profile(), limits,
                        reference -> { if (references.size() >= bounds.indexRows()) limit(); references.add(reference); }, control::checkpoint);
            }
            if (!checked.xmlSha256().equals(xml[0].plaintextSha256()) || checked.xmlBytes() != xml[0].plaintextBytes()
                    || !checked.occurrences().equals(composed[0].occurrences())) invalid();
            var referenced = new HashSet<String>();
            for (var reference : references) {
                control.checkpoint(); var member = attachments.get(reference.relativePath());
                if (member == null || !referenced.add(reference.relativePath())) invalid();
                try (var input = store.open(member.file())) { input.transferTo(OutputStream.nullOutputStream()); }
            }
            if (!referenced.equals(attachments.keySet())) invalid();
            captures.authorizeCapturedSources(lease, patient, tables, source.capture(), control);
            patient.require(lease); control.checkpoint();
            var result = new Document(lease.runId(), lease.epoch(), patient.patient(), expected.sourceManifestSha256(),
                    xml[0], reconciliation, checked, List.copyOf(attachments.values()));
            complete = true; return result;
        } catch (IOException | RuntimeException | Error failure) { primary = failure; throw failure; }
        finally {
            active.set(false);
            if (!complete) for (var member : owned.reversed()) try { store.discardUnpublished(member); }
            catch (IOException | RuntimeException cleanup) { if (primary != null) primary.addSuppressed(cleanup); else throw cleanup; }
        }
    }

    private void append(CdsExportLeaseDto lease, CdsExportCohortService.Patient patient, String patientKey, Set<String> tables,
            CdsExportPatientCaptureDto source, CdsExportStagedFileDto payloadManifest, CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control, CdsExportXmlComposer.SectionEmitter xml, CdsExportClinicalOccurrenceService.Receipts sink,
            CdsExportHistoryOccurrenceService.Source history) throws IOException {
        for (var category : CdsCategory.values()) if (lease.document().command().categories().contains(category)) {
            control.checkpoint();
            if (category == CdsCategory.DEMOGRAPHICS) demographics.append(lease, patient, patientKey, tables, source, payloadManifest, bounds, control, xml, sink);
            else if (CdsExportNoteSourceReader.CATEGORIES.contains(category)) {
                if (history == null) invalid(); history.append(category, sink);
            } else clinical.append(category, lease, patient, patientKey, tables, source, payloadManifest, bounds, control, xml, sink);
        }
    }
    private record ReceiptHeader(CdsExportIdentityDto identity, String table, String nativeId, String importedUnit,
            Set<String> retainedFields, Set<String> replacedFields) { }
    private record FieldDecision(CdsExportIdentityDto identity, CdsExportNativeFieldKeyDto field, CdsExportFieldAssessmentDto assessment) { }
    private static void line(OutputStream out, Object value, long[] used, long maximum) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(value);
        if (bytes.length > maximum - used[0] - 1) limit();
        out.write(bytes); out.write('\n'); used[0] += bytes.length + 1L;
    }
    /** Private validated document evidence; package/disclosure services must still prove their own gates. */
    public static final class Document {
        private final String runId, sourceManifestSha256; private final long epoch; private final int patient;
        private final CdsExportStagedFileDto xml, reconciliation;
        private final CdsExportXmlValidationDto validation;
        private final List<CdsExportPackageMemberDto> attachments;
        private Document(String run, long epoch, int patient, String source, CdsExportStagedFileDto xml,
                CdsExportStagedFileDto reconciliation, CdsExportXmlValidationDto validation, List<CdsExportPackageMemberDto> attachments) {
            runId = run; this.epoch = epoch; this.patient = patient; sourceManifestSha256 = source;
            this.xml = xml; this.reconciliation = reconciliation; this.validation = validation; this.attachments = List.copyOf(attachments);
        }
        public String runId() { return runId; }
        public long epoch() { return epoch; }
        public int patient() { return patient; }
        public String sourceManifestSha256() { return sourceManifestSha256; }
        public CdsExportStagedFileDto xml() { return xml; }
        public CdsExportStagedFileDto reconciliation() { return reconciliation; }
        public CdsExportXmlValidationDto validation() { return validation; }
        public List<CdsExportPackageMemberDto> attachments() { return attachments; }
        @Override public String toString() { return "CdsExportPatientDocument[protected]"; }
    }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_PATIENT_DOCUMENT_INVALID"); }
    private static void review() { throw new CdsExportReviewException(CdsExportReviewException.Reason.UNMAPPED_DATA); }
    private static void limit() { throw new CdsExportReviewException(CdsExportReviewException.Reason.RESOURCE_LIMIT); }
}
