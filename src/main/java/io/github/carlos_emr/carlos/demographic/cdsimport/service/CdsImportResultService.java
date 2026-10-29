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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportPatientDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportUnitDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Projects one durable ledger into bounded operator receipts and the required complete protected files. */
@Service
public class CdsImportResultService {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Set<String> TYPES=Set.of("event", "error", "completion", "units");
    private static final Set<String> FINAL_ATTEMPT_STATES=Set.of("COMPLETE", "DRY_RUN_COMPLETE", "DRY_RUN_INCOMPLETE", "INCOMPLETE", "RECONCILIATION_REQUIRED", "FAILED_BEFORE_WRITE", "CANCELLED_BEFORE_WRITE", "REVIEW_REQUIRED");
    private static final List<String> SOURCE_CATEGORIES=List.of("Demographics", "PersonalHistory", "FamilyHistory", "PastHealth", "ProblemList", "RiskFactors", "AllergiesAndAdverseReactions", "MedicationsAndTreatments", "Immunizations", "LaboratoryResults", "Appointments", "ClinicalNotes", "ReportsText", "ReportsBinary", "CareElements", "AlertsAndSpecialNeeds", "NewCategory");
    private final CdsImportLedgerService ledger;
    private final CdsImportReportService selection;
    private final CdsImportConfigurationService configuration;
    private final CdsImportPlanningService planning;
    private final CdsFieldParser fields;
    private final CdsImportCleanupService cleanup;
    private final LongSupplier nanoTime;

    @Autowired
    public CdsImportResultService(CdsImportLedgerService ledger, CdsImportReportService selection,
            CdsImportConfigurationService configuration, CdsImportPlanningService planning, CdsFieldParser fields, CdsImportCleanupService cleanup) {
        this(ledger, selection, configuration, planning, fields, cleanup, System::nanoTime);
    }
    CdsImportResultService(CdsImportLedgerService ledger, CdsImportReportService selection,
            CdsImportConfigurationService configuration, CdsImportPlanningService planning, CdsFieldParser fields, CdsImportCleanupService cleanup, LongSupplier nanoTime) {
        this.ledger=ledger; this.selection=selection; this.configuration=configuration; this.planning=planning; this.fields=fields; this.cleanup=cleanup;
        this.nanoTime=nanoTime;
    }

    /** No clinical transaction spans report I/O. Only a fully durable immutable set may become authoritative. */
    public void generate(String runId, long epoch) throws IOException {
        generateOwned(runId, epoch, null);
    }

    void generate(String runId, long epoch, CdsImportCoordinatorService.Owner coordinator) throws IOException {
        generateOwned(runId, epoch, java.util.Objects.requireNonNull(coordinator));
    }

    private void generateOwned(String runId, long epoch, CdsImportCoordinatorService.Owner coordinator) throws IOException {
        boolean reportSelected=false, priorDelivered=false;
        try {
        long started=nanoTime.getAsLong(), maximumMillis=configuration.limits().maxDurationMillis();
        var entryFacts=ledger.reportEntry(runId);
        priorDelivered=entryFacts.reportsDelivered();
        Runnable reportBudget=() -> {
            if (java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong()-started)>=maximumMillis)
                throw new CdsImportException("IMPORT_REPORT_GENERATION_TIMEOUT");
        };
        var shared=reportControl(runId, epoch, coordinator, reportBudget);
        Runnable reportProbe=() -> {
            if (shared!=null) shared.check(false);
            else {reportBudget.run(); checkGeneration(runId, epoch); reportBudget.run(); }
        };
        reportProbe.run();
        try (shared; var control=shared==null ? fields.control(reportProbe) : fields.controlEveryCheckpoint(reportProbe)) {
        CdsImportJobDto before=ledger.jobBinding(runId).job();
        // The private job location is assigned only at acceptance. Mutable report
        // generation and manifest facts still come from the fresh binding above.
        Path job=CdsPaths.within(Path.of(entryFacts.documentLocation()), configuration.stagingRoot()).getParent();
        Path directory=CdsPaths.privateDirectory(job.resolve("reports-"+epoch+"-"+UUID.randomUUID()), job);
        Path ownership=directory.resolve("owner.json");
        try (OutputStream output=output(ownership, directory)) { line(output, Map.of("protocol", 1, "runId", runId, "ownerEpoch", epoch, "directory", directory.getFileName().toString())); }
        CdsPaths.forceFile(ownership); CdsPaths.forceDirectory(directory); CdsPaths.forceDirectory(job);
        Map<String, Object> manifest=new LinkedHashMap<>();
        manifest.put("version", 1); manifest.put("runId", runId); manifest.put("generation", before.reportGeneration()+1);
        manifest.put("createdAt", Instant.now().toString()); manifest.put("sourceDigest", before.sourceDigest());
        manifest.put("manifestDigest", before.manifestDigest());
        Map<String, Object> files=new LinkedHashMap<>();
        boolean inventoryComplete=entryFacts.discoveryComplete();
        boolean preview=entryFacts.dryRun();
        boolean sealedInventory=entryFacts.sealedInventory();
        io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportCleanupDto cleanupResult;
            Path event=directory.resolve("ImportEventLog.jsonl"), error=directory.resolve("ImportErrorLog.jsonl"), unitsFile=directory.resolve("units.jsonl");
            try (OutputStream events=output(event, directory); OutputStream errors=output(error, directory); OutputStream unitRows=output(unitsFile, directory)) {
                String after="";
                while (true) {
                    reportProbe.run();
                    List<CdsImportPatientDto> page=ledger.patients(runId, after, 100); if (page.isEmpty()) break;
                    for (CdsImportPatientDto patient:page) {
                        reportBudget.run();
                        Map<String, Long> imported=emptyCounts(), prior=emptyCounts(), pending=emptyCounts();
                        String unitAfter="";
                        while (true) {
                            reportProbe.run();
                            List<CdsImportUnitDto> unitPage=ledger.units(runId, patient.patientKey(), unitAfter, 100); if (unitPage.isEmpty()) break;
                            for (CdsImportUnitDto unit:unitPage) {
                                reportBudget.run();
                                Map<String, Object> row=new LinkedHashMap<>(); row.put("patientId", patient.sourcePatientId());
                                row.put("patientKey", patient.patientKey()); row.put("copyKey", patient.copyKey()); row.put("unitKey", unit.unitKey());
                                row.put("category", CdsImportLedgerService.clinicalCategory(unit.category())); row.put("unitCategory", unit.category());
                                row.put("sourceOrdinal", unit.ordinal()); row.put("state", unit.state()); row.put("sourceDigest", unit.sourceDigest());
                                row.put("targetDigest", unit.targetDigest());
                                if (unit.targetReferences()!=null) row.put("targets", JSON.readTree(unit.targetReferences()));
                                line(unitRows, row);
                                if (!CdsImportLedgerService.isHelperCategory(unit.category())) {
                                    String category=reportCategory(runId, unit);
                                    Map<String, Long> counts="COMMITTED".equals(unit.state())?imported:("ALREADY_COMPLETE".equals(unit.state())?prior:pending);
                                    counts.merge(category, 1L, Math::addExact);
                                }
                                if (!preview && !Set.of("COMMITTED", "ALREADY_COMPLETE").contains(unit.state())) {
                                    Map<String, Object> failure=new LinkedHashMap<>(row); failure.put("code", "SOURCE_UNIT_NOT_COMMITTED");
                                    failure.put("patientOutcome", patient.state()); line(errors, failure);
                                }
                                unitAfter=unit.unitKey();
                            }
                            if (sealedInventory && unitPage.size()<100) break;
                        }
                        Map<String, Object> receipt=new LinkedHashMap<>(); receipt.put("patientId", patient.sourcePatientId());
                        receipt.put("patientKey", patient.patientKey()); receipt.put("copyKey", patient.copyKey()); receipt.put("outcome", patient.state());
                        receipt.put("inventoryComplete", inventoryComplete);
                        receipt.put("held", patient.held()); receipt.put("importedOccurrences", imported);
                        receipt.put("verifiedPriorOccurrences", prior); receipt.put("notCommittedOccurrences", pending);
                        receipt.put("expectedUnits", patient.expectedUnits()); line(events, receipt);
                        if (!CdsImportLedgerService.COMPLETE_PATIENT.contains(patient.state()) && !(preview && "PLANNED".equals(patient.state()))) {
                            Map<String, Object> failure=new LinkedHashMap<>(receipt); failure.put("code", "PATIENT_"+patient.state()); failure.put("severity", "ERROR"); line(errors, failure);
                        }
                        after=patient.patientKey();
                    }
                    if (sealedInventory && page.size()<100) break;
                }
                long diagnosticAfter=0;
                while (true) {
                    reportProbe.run();
                    List<CdsImportDiagnosticDto> page=ledger.diagnostics(runId, diagnosticAfter, 100); if (page.isEmpty()) break;
                    Set<String> keys=page.stream().map(CdsImportDiagnosticDto::patientKey).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
                    Map<String, String> sourceIds=ledger.sourcePatientIds(runId, keys);
                    for (CdsImportDiagnosticDto diagnostic:page) {
                        reportBudget.run();
                        Map<String, Object> row=new LinkedHashMap<>(); row.put("diagnosticId", diagnostic.id()); row.put("patientKey", diagnostic.patientKey()); row.put("patientId", diagnostic.patientKey()==null?null:sourceIds.get(diagnostic.patientKey()));
                        row.put("unitKey", diagnostic.unitKey()); row.put("severity", diagnostic.severity()); row.put("code", diagnostic.code());
                        row.put("stage", diagnostic.stage()); row.put("sourceXPath", diagnostic.sourcePath()); row.put("summary", diagnostic.safeSummary());
                        row.put("blocking", diagnostic.blocking()); row.put("resolved", diagnostic.resolved());
                        row.put("detailReference", diagnostic.protectedDetailLocation()); line(errors, row); diagnosticAfter=diagnostic.id();
                    }
                }
                final long[] validationRows={0};
                java.util.function.Consumer<Map<String, Object>> validationConsumer=row -> {
                    reportBudget.run();
                    if (++validationRows[0]%100==0) reportProbe.run();
                    try { line(errors, row); } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
                };
                if (shared==null) planning.forEachValidationDiagnostic(runId, validationConsumer, reportProbe);
                else planning.forEachValidationDiagnostic(runId, epoch, validationConsumer, shared);
                reportProbe.run();
                cleanupResult=shared==null ? cleanup.reconcile(runId, epoch, directory, reportBudget)
                        : cleanup.reconcile(runId, epoch, directory, reportBudget, shared);
                reportBoundary(shared, reportProbe);
                for (String code:cleanupResult.safeCodes()) line(errors, Map.of("severity", "WARNING", "code", code, "stage", "CLEANUP", "summary", "Owned artifacts require reconciliation; uncertain artifacts were retained"));
            }
            // An older delivery acknowledgement cannot certify this still-unselected attempt.
            ledger.operational(runId, epoch, null, priorDelivered && "COMPLETE".equals(cleanupResult.state())?"PENDING":cleanupResult.state(), cleanupResult.safeCodes().isEmpty()?null:"IMPORT_CLEANUP_RECONCILIATION_REQUIRED");
            for (Path path:List.of(event, error, unitsFile)) {reportProbe.run(); CdsPaths.forceFile(path); }
            Map<String, Object> completion=new LinkedHashMap<>(ledger.summary(runId));
            completion.put("sourceDigest", before.sourceDigest()); completion.put("sourceNamespace", before.sourceNamespace());
            completion.put("sourceProfile", before.profile()); completion.put("rulesetDigest", before.rulesetDigest());
            completion.put("optionsDigest", before.optionsDigest()); completion.put("manifestDigest", before.manifestDigest());
            completion.put("startedAt", before.createdAt().toString()); completion.put("receiptAt", Instant.now().toString());
            completion.put("sourceDisposition", "RETAINED_FOR_RECONCILIATION");
            // This receipt describes the durable projection just generated, not an old pending-report observation.
            completion.put("reportState", "DELIVERED"); completion.put("reportsDelivered", true);
            boolean releasePending=Boolean.TRUE.equals(completion.get("maintenanceReleasePending"));
            completion.put("cleanup", cleanupResult); completion.put("cleanupState", releasePending?completion.get("cleanupState"):cleanupResult.state()); completion.put("cleanupComplete", !releasePending && "COMPLETE".equals(cleanupResult.state()));
            boolean clinical=Boolean.TRUE.equals(completion.get("clinicalComplete"));
            boolean dryRun=Boolean.TRUE.equals(completion.get("dryRun")) && "DRY_RUN_COMPLETE".equals(completion.get("state"));
            boolean resumeRequested=Boolean.TRUE.equals(completion.get("resumeRequested"));
            boolean cleanupComplete=Boolean.TRUE.equals(completion.get("cleanupComplete"));
            // Project delivery of this generation without turning a newer same-epoch recovery
            // request or active/replanning state into a terminal outcome. These are the live
            // ledger summary's completion predicates, with this durable report set delivered.
            completion.put("cleanComplete", !resumeRequested && (clinical || dryRun) && cleanupComplete);
            completion.put("operationTerminal", !resumeRequested && FINAL_ATTEMPT_STATES.contains(completion.get("state"))
                    && (cleanupComplete || releasePending || "FAILED".equals(completion.get("cleanupState"))));
            Path receipt=directory.resolve("completion.json");
            try (OutputStream output=output(receipt, directory)) { line(output, completion); }
            CdsPaths.forceFile(receipt);
            for (Map.Entry<String, Path> entry:Map.of("event", event, "error", error, "units", unitsFile, "completion", receipt).entrySet()) {
                files.put(entry.getKey(), Map.of("name", entry.getValue().getFileName().toString(), "sha256", hash(entry.getValue(), reportProbe, reportBudget), "bytes", Files.size(entry.getValue())));
            }
            manifest.put("files", files);
            Path selected=directory.resolve("report-set.json");
            try (OutputStream output=output(selected, directory)) { line(output, manifest); }
            CdsPaths.forceFile(selected); CdsPaths.forceDirectory(directory); CdsPaths.forceDirectory(job);
            String digest=hash(selected, reportProbe, reportBudget);
            reportBoundary(shared, reportProbe);
            try {
                selection.select(runId, epoch, before.reportGeneration(), before.manifestDigest()==null?before.sourceDigest():before.manifestDigest(), selected.toString(), digest);
            } catch (RuntimeException unknownSelection) {
                CdsImportJobDto actual=ledger.status(runId);
                if (!digest.equals(actual.reportDigest()) || actual.reportGeneration()!=before.reportGeneration()+1) throw unknownSelection;
            }
            reportSelected=true;
            ledger.operational(runId, epoch, null, cleanupResult.state(), null);
        }
        } catch (IOException | RuntimeException failure) {
            boolean timedOut=failure instanceof CdsImportException known && "IMPORT_REPORT_GENERATION_TIMEOUT".equals(known.getCode());
            try { ledger.operational(runId, epoch, reportSelected || timedOut && priorDelivered?null:"FAILED", "FAILED", reportSelected?"IMPORT_CLEANUP_FAILED":"IMPORT_REPORT_DELIVERY_FAILED"); }
            catch (RuntimeException unavailable) { failure.addSuppressed(unavailable); }
            if (timedOut) {
                try { ledger.diagnostic(runId, epoch, null, null, "WARNING", "IMPORT_REPORT_GENERATION_TIMEOUT", "REPORTING", null,
                        "Protected report generation exceeded its active attempt budget; clinical receipts and source were retained", null, false); }
                catch (RuntimeException unavailable) {failure.addSuppressed(unavailable); }
            }
            throw failure;
        }
    }

    /** Authorization belongs to the facade. Verify selected hashes before handing a file to a download adapter. */
    public Path report(String runId, String type) throws IOException {
        if ("receipt".equals(type)) type="completion";
        if (!TYPES.contains(type)) throw new CdsImportException("REPORT_TYPE_INVALID");
        CdsImportJobDto job=ledger.status(runId);
        if (job.reportLocation()==null || job.reportDigest()==null) throw new CdsImportException("REPORT_NOT_READY");
        Path root=jobRoot(runId); Path selected=CdsPaths.within(Path.of(job.reportLocation()), root);
        if (!hash(selected).equals(job.reportDigest())) throw new CdsImportException("REPORT_MANIFEST_HASH_MISMATCH");
        JsonNode metadata;
        try (InputStream input=Files.newInputStream(selected, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes=CdsFieldParser.readMetadataLine(input);
            if (bytes==null) throw new CdsImportException("REPORT_MANIFEST_MISSING"); metadata=JSON.readTree(bytes);
        }
        JsonNode file=metadata.path("files").path(type);
        String name=file.path("name").asText();
        if (!name.matches("[A-Za-z0-9.-]{1,100}")) throw new CdsImportException("REPORT_MANIFEST_INVALID");
        Path path=CdsPaths.within(selected.getParent().resolve(name), root);
        if (Files.size(path)!=file.path("bytes").asLong(-1) || !hash(path).equals(file.path("sha256").asText())) throw new CdsImportException("REPORT_FILE_HASH_MISMATCH");
        return path;
    }

    private String reportCategory(String runId, CdsImportUnitDto unit) throws IOException {
        String category=CdsImportLedgerService.clinicalCategory(unit.category());
        if (!"Reports".equals(category)) return category;
        var work=planning.work(runId, unit);
        String format=fields.firstToken(work.record(), "Format", 16);
        if (!Set.of("Text", "Binary").contains(format==null?"":format)) throw new CdsImportException("REPORT_FORMAT_RECONCILIATION_REQUIRED");
        return "Reports"+format;
    }

    private Path jobRoot(String runId) {
        return CdsPaths.within(Path.of(ledger.snapshotLocation(runId)), configuration.stagingRoot()).getParent();
    }
    private static Map<String, Long> emptyCounts() {
        Map<String, Long> counts=new LinkedHashMap<>(); for (String category:SOURCE_CATEGORIES) counts.put(category, 0L); return counts;
    }
    private static OutputStream output(Path file, Path root) throws IOException { return new BufferedOutputStream(CdsPaths.newPrivateFile(file, root), 65536); }
    private static void line(OutputStream output, Object row) throws IOException {
        byte[] bytes=JSON.writeValueAsBytes(row);
        if (bytes.length>(1<<20)) throw new CdsImportException("REPORT_ROW_CAPACITY_EXCEEDED");
        output.write(bytes); output.write('\n');
    }
    private CdsImportReportControl reportControl(String runId, long epoch,
            CdsImportCoordinatorService.Owner coordinator, Runnable reportBudget) {
        if (coordinator==null)return null;
        try {return new CdsImportReportControl(runId, epoch, coordinator, nanoTime, reportBudget); }
        catch (CdsImportException unavailable) {
            // Only an initial lost/closed coordinator uses the established fresh epoch-only report path.
            // No generation has started, no selection is retried, and no new owner is acquired here.
            if ("IMPORT_COORDINATOR_LOST".equals(unavailable.getCode()))return null;
            throw unavailable;
        }
    }
    private static void reportBoundary(CdsImportReportControl shared, Runnable legacyProbe) {
        if (shared==null)legacyProbe.run(); else shared.check(true);
    }
    private void checkGeneration(String runId, long epoch) {
        if (Thread.currentThread().isInterrupted()) throw new CdsImportException("REPORT_CANCELLED");
        if (ledger.status(runId).ownerEpoch()!=epoch) throw new CdsImportException("OWNER_FENCED");
    }
    private static String hash(Path path) throws IOException { return hash(path, () -> {}, () -> {}); }
    private static String hash(Path path, Runnable probe, Runnable budget) throws IOException {
        probe.run(); long bytes=0, nextCheck=16L<<20;
        MessageDigest digest=CdsPaths.sha256();
        try (InputStream input=new BufferedInputStream(Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS), 65536)) {
            byte[] buffer=new byte[65536]; int count;
            while ((count=input.read(buffer))!=-1) {
                budget.run();
                if (Thread.currentThread().isInterrupted()) throw new CdsImportException("REPORT_CANCELLED");
                digest.update(buffer, 0, count); bytes=Math.addExact(bytes, count);
                if (bytes>=nextCheck) { probe.run(); nextCheck=Math.addExact(bytes, 16L<<20); }
            }
        }
        probe.run(); return CdsPaths.hex(digest.digest());
    }
}
