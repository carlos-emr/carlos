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

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportPatientDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportUnitDto;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** HTTP-independent supervised execution; no transaction spans source work, preparation or reports. */
@Service
public class CdsImportExecutionService {
    private static final Set<String> COMPLETE=Set.of("COMPLETE", "COMPLETE_WITH_NONLOSSY_WARNINGS", "ALREADY_COMPLETE");
    private final CdsImportLedgerService ledger;
    private final CdsImportUnitService units;
    private final CdsImportJobService jobs;
    private final CdsImportPlanningService planning;
    private final CdsImportNativeService nativeRecords;
    private final CdsImportAccessService access;
    private final CdsImportConfigurationService configuration;
    private final CdsImportMaintenanceService maintenance;
    private final CdsImportResultService results;
    private final CdsImportCoordinatorService coordinators;
    private final CdsImportFileVerificationService fileVerification;
    private final CdsImportTargetSafetyService safety;
    private final CdsImportFailureEvidenceService failureEvidence;
    private final CdsFieldParser fields;

    public CdsImportExecutionService(CdsImportLedgerService ledger, CdsImportUnitService units, CdsImportJobService jobs,
            CdsImportPlanningService planning, CdsImportNativeService nativeRecords, CdsImportAccessService access,
            CdsImportConfigurationService configuration, CdsImportMaintenanceService maintenance, CdsImportResultService results, CdsImportCoordinatorService coordinators, CdsImportFileVerificationService fileVerification, CdsImportTargetSafetyService safety, CdsImportFailureEvidenceService failureEvidence, CdsFieldParser fields) {
        this.ledger=ledger; this.units=units; this.jobs=jobs; this.planning=planning; this.nativeRecords=nativeRecords;
        this.access=access; this.configuration=configuration; this.maintenance=maintenance; this.results=results; this.coordinators=coordinators; this.fileVerification=fileVerification; this.safety=safety;
        this.failureEvidence=failureEvidence; this.fields=fields;
    }

    public CdsImportJobDto execute(String runId) { return execute(runId, false); }

    /** Explicit resume never reapplies a published patient; it reconciles its own known atomic ledger. */
    public CdsImportJobDto execute(String runId, boolean resume) {
        try (var coordinator=coordinators.acquire()) { return executeOwned(runId, resume, coordinator); }
        catch (CdsImportException busy) {
            if (Set.of("IMPORT_COORDINATOR_BUSY", "IMPORT_BUSY", "MAINTENANCE_OWNER_BUSY").contains(busy.getCode())) return ledger.status(runId);
            throw busy;
        }
    }

    /** Runs on a healthy supervisor thread after the failed worker has unwound its transaction/window. */
    public void superviseFatal(String runId) {
        try (var coordinator=coordinators.acquire()) {
            long epoch=ledger.status(runId).ownerEpoch(); ledger.workerStopped(runId, epoch); deliver(runId, epoch);
        }
    }

    private CdsImportJobDto executeOwned(String runId, boolean resume, CdsImportCoordinatorService.Owner coordinator) {
        CdsImportJobDto job=ledger.status(runId);
        long epoch=job.ownerEpoch();
        String currentPatient=null;
        String currentUnit=null;
        CdsImportMaintenanceService.Owner completedOwner=null;
        try {
            var document=jobs.load(runId);
            access.context(document.actor(), document.command(), null);
            if (Set.of("COMPLETE", "DRY_RUN_COMPLETE").contains(job.state())
                    || !resume && Set.of("INCOMPLETE", "RECONCILIATION_REQUIRED", "FAILED_BEFORE_WRITE", "CANCELLED_BEFORE_WRITE", "DRY_RUN_INCOMPLETE", "REVIEW_REQUIRED").contains(job.state())) {
                boolean releasePending=ledger.maintenanceReleasePending(runId);
                boolean retryRelease=releasePending && (resume || ledger.maintenanceReleaseRetryRequested(runId));
                if (retryRelease) {
                    epoch=ledger.claimReporting(runId);
                    CdsImportMaintenanceService.ReleaseStatus released=maintenance.reconcileCompletedRelease(safety);
                    if (released==CdsImportMaintenanceService.ReleaseStatus.RELEASED) ledger.maintenanceReleaseReconciled(runId, epoch);
                    else if (released==CdsImportMaintenanceService.ReleaseStatus.REQUIRES_RECONCILIATION) ledger.maintenanceReleaseFailed(runId, epoch, null);
                    else ledger.maintenanceReleaseDeferred(runId, epoch, released.name());
                    ledger.maintenanceReleaseRetryAttempted(runId, epoch);
                }
                if (retryRelease || !Boolean.TRUE.equals(ledger.summary(runId).get("reportsDelivered")) || !Boolean.TRUE.equals(ledger.summary(runId).get("cleanupComplete"))) { epoch=ledger.claimReporting(runId); deliver(runId, epoch); }
                return ledger.status(runId);
            }
            coordinator.check(); epoch=ledger.claimPreparation(runId); configuration.requireEnabled();
            if (!job.rulesetDigest().equals(CdsImportJobService.rulesetDigest(document.command(), configuration))
                    || !job.optionsDigest().equals(CdsImportJobService.optionsDigest(document.command()))) throw new CdsImportException("IMPORT_RULESET_OR_OPTIONS_INCOMPATIBLE");
            try (var owner=maintenance.acquire()) {
                safety.verify(owner);
            if (!ledger.isSealed(runId)) {
                epoch=ledger.claimPlanning(runId, resume);
                ledger.resolveReauthorizedAccess(runId, epoch);
                if (resume) ledger.preparePreflightResume(runId, epoch);
                var snapshot=jobs.snapshot(runId, document);
                ledger.inventoryDiscovered(runId, epoch, snapshot.files(), snapshot.patientXmlFiles(), snapshot.totalBytes());
                final long planningEpoch=epoch;
                owner.readIo(() -> {planning.plan(runId,planningEpoch,document,snapshot);return null;});
                job=ledger.status(runId);
                if ("REVIEW_REQUIRED".equals(job.state())) {
                    ledger.release(runId, epoch); deliver(runId, epoch); return ledger.status(runId);
                }
            }
            job=ledger.status(runId);
            if (document.command().dryRun()) {
                ledger.completeDryRun(runId, epoch);
                if ("DRY_RUN_COMPLETE".equals(ledger.status(runId).state()) && ledger.heldPatients(runId)==0) {
                    owner.allowReopen(); owner.close(); settleRelease(runId, epoch, owner);
                }
                deliver(runId, epoch); return ledger.status(runId);
            }
            boolean recovery=resume || Set.of("RUNNING", "FINALIZING", "RECONCILING").contains(job.state());
                epoch=resume?ledger.resume(runId):ledger.acquire(runId);
                try (var control=ioControl(runId, epoch, coordinator, "IMPORT_FILE_VERIFICATION_TIMEOUT")) {
                    fileVerification.verify(runId, epoch);
                }
                // Verify current-run receipts before new writes. Completed patients retain their existing publication.
                String patientAfter="";
                while (true) {
                    List<CdsImportPatientDto> patientPage=ledger.patients(runId, patientAfter, 100); if (patientPage.isEmpty()) break;
                    for (CdsImportPatientDto patient:patientPage) {
                        if (!COMPLETE.contains(patient.state())) {
                            if (recovery) ledger.preparePatientResume(runId, epoch, patient.patientKey());
                            String after="";
                            while (true) {
                                List<CdsImportUnitDto> page=ledger.units(runId, patient.patientKey(), after, 100); if (page.isEmpty()) break;
                                for (CdsImportUnitDto unit:page) {
                                    if (Set.of("COMMITTED", "ALREADY_COMPLETE").contains(unit.state())) {
                                        currentPatient=unit.patientKey(); currentUnit=unit.unitKey();
                                        units.execute(runId, epoch, unit.unitKey(), () -> { throw new CdsImportException("COMMITTED_UNIT_REPLAY_REFUSED"); }, nativeRecords::verify);
                                    }
                                    after=unit.unitKey();
                                }
                            }
                        }
                        patientAfter=patient.patientKey();
                    }
                }
                while (true) {
                    configuration.requireEnabled(); coordinator.check();
                    if (ledger.status(runId).cancelRequested()) throw new CdsImportException("IMPORT_CANCELLED");
                    List<CdsImportUnitDto> ready=ledger.readyUnits(runId, 100);
                    if (ready.isEmpty()) break;
                    for (CdsImportUnitDto unit:ready) {
                        currentPatient=unit.patientKey(); currentUnit=unit.unitKey();
                        var work=planning.work(runId, unit);
                        owner.read(() -> access.context(work.actor(), work.command(), ledger.patientStatus(runId, unit.patientKey()).targetDemographicNo()));
                        planning.publishValues(runId, epoch, work);
                        final long preparationEpoch=epoch;
                        try (var control=ioControl(runId, preparationEpoch, coordinator, "IMPORT_PREPARATION_TIMEOUT")) {
                            owner.readIo(() -> {nativeRecords.prepare(runId,preparationEpoch,unit,work.record(),work.command(),work.snapshot(),work.xmlRelative());return null;});
                        }
                        final long unitEpoch=epoch;
                        units.execute(runId, epoch, unit.unitKey(), () -> nativeRecords.write(runId, unitEpoch, unit, work.record(), work.command(), work.actor(), work.snapshot(), work.xmlRelative()), nativeRecords::verify);
                    }
                }
                currentPatient=null; currentUnit=null;
                try (var control=ioControl(runId, epoch, coordinator, "IMPORT_FILE_VERIFICATION_TIMEOUT")) {
                    fileVerification.verify(runId, epoch);
                }
                if (recovery) ledger.resolveRecoveredExecution(runId, epoch);
                String after="";
                while (true) {
                    List<CdsImportPatientDto> page=ledger.patients(runId, after, 100); if (page.isEmpty()) break;
                    for (CdsImportPatientDto patient:page) {
                        currentPatient=patient.patientKey(); currentUnit=null;
                        if (!CdsImportLedgerService.FINAL_PATIENT.contains(patient.state())) { final long finalEpoch=epoch; owner.read(() -> { ledger.finalizePatient(runId, finalEpoch, patient.patientKey(), nativeRecords::verify); return null; }); }
                        after=patient.patientKey();
                    }
                }
                ledger.finish(runId, epoch);
                if ("COMPLETE".equals(ledger.status(runId).state()) && ledger.heldPatients(runId)==0) { completedOwner=owner; owner.allowReopen(); }
            }
            if (completedOwner!=null) settleRelease(runId, epoch, completedOwner);
        } catch (Exception failure) {
            if (failure instanceof SecurityException) { ledger.authorizationStopped(runId, epoch); deliver(runId, epoch); return ledger.status(runId); }
            if (failure instanceof CdsImportException fenced && Set.of("OWNER_FENCED", "IMPORT_COORDINATOR_LOST", "MAINTENANCE_OWNER_BUSY", "IMPORT_BUSY", "IMPORT_COORDINATOR_BUSY", "REVIEW_RESET_IN_PROGRESS").contains(fenced.getCode())) {
                if("MAINTENANCE_OWNER_BUSY".equals(fenced.getCode()) && ledger.ownsAdmission(runId,epoch)) ledger.release(runId,epoch);
                return ledger.status(runId);
            }
            recordFailure(runId, epoch, currentPatient, currentUnit, failure);
        }
        // A fatal Error is deliberately not caught; the supervisor later reconciles durable state.
        deliver(runId, epoch);
        return ledger.status(runId);
    }

    private void settleRelease(String runId, long epoch, CdsImportMaintenanceService.Owner owner) {
        if (owner.releaseVerified()) ledger.maintenanceReleaseReconciled(runId, epoch);
        else ledger.maintenanceReleaseDeferred(runId, epoch, "GLOBAL_HOLDS_REMAIN");
    }

    private void deliver(String runId, long epoch) {
        try { results.generate(runId, epoch); }
        catch (IOException | RuntimeException deliveryFailure) {
            // Delivery has its own durable state and never reclassifies a clinical outcome.
        }
    }

    private void recordFailure(String runId, long epoch, String affectedPatient, String affectedUnit, Exception failure) {
        String code=failure instanceof CdsImportException known?known.getCode():
                failure instanceof io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException input?input.code():
                failure instanceof io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsClinicalException clinical?clinical.code():"IMPORT_EXECUTION_FAILED";
        if (affectedUnit==null && failure instanceof CdsImportException known) affectedUnit=known.getUnitKey();
        String sourcePath=failureSourcePath(runId, affectedUnit, failure);
        String evidence=failureEvidence.write(runId,epoch,failure);
        if (CdsImportLedgerService.MAINTENANCE_RELEASE_CODE.equals(code) && Set.of("COMPLETE", "DRY_RUN_COMPLETE").contains(ledger.status(runId).state())) {
            ledger.maintenanceReleaseFailed(runId, epoch, evidence); return;
        }
        if(!ledger.ownsAdmission(runId,epoch)) {ledger.preparationStopped(runId,epoch,code,evidence);return;}
        ledger.operational(runId,epoch,null,null,code);
        if (evidence==null) ledger.diagnostic(runId,epoch,affectedPatient,affectedUnit,"WARNING","IMPORT_FAILURE_EVIDENCE_UNAVAILABLE","OBSERVABILITY",null,
                "Bounded operational failure evidence could not be delivered; the original outcome remains authoritative",null,false);
        boolean uncertain=failure instanceof CdsImportException known && known.isOutcomeUnknown()
                || code.contains("RECONCILIATION") || code.startsWith("TARGET_") || code.startsWith("REFERENCED_FILE_");
        boolean cancelled=Set.of("IMPORT_CANCELLED", "CDS_INPUT_CANCELLED", "DOCUMENT_PUBLICATION_CANCELLED").contains(code) || Thread.currentThread().isInterrupted();
        CdsImportJobDto job=ledger.status(runId);
        if (!ledger.isSealed(runId)) {
            if (evidence!=null) ledger.diagnostic(runId,epoch,affectedPatient,affectedUnit,"INFO","IMPORT_FAILURE_EVIDENCE","OBSERVABILITY",sourcePath,
                    "Protected bounded operational failure evidence is available",evidence,false);
            ledger.endPreflight(runId, epoch, cancelled?"CANCELLED_BEFORE_WRITE":"FAILED_BEFORE_WRITE", code, sourcePath);
            ledger.release(runId, epoch); return;
        }
        ledger.diagnostic(runId, epoch, affectedPatient, affectedUnit, "ERROR", code, "EXECUTION", sourcePath,
                uncertain?"Import requires durable outcome reconciliation":"Import stopped; inspect protected source and unit results", evidence, true);
        String after="";
        while (true) {
            List<CdsImportPatientDto> page=ledger.patients(runId, after, 100); if (page.isEmpty()) break;
            for (CdsImportPatientDto patient:page) {
                if (!CdsImportLedgerService.FINAL_PATIENT.contains(patient.state())) {
                    ledger.markIncomplete(runId, epoch, patient.patientKey(), patient.patientKey().equals(affectedPatient), cancelled, uncertain && (affectedPatient==null || patient.patientKey().equals(affectedPatient)));
                }
                after=patient.patientKey();
            }
        }
        ledger.finish(runId, epoch);
    }
    private String failureSourcePath(String runId, String unit, Exception failure) {
        String path=null;
        if (failure instanceof io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException input) path=input.sourceXPath();
        else if (failure instanceof io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsClinicalException clinical) path=clinical.field();
        if ((path==null || path.isBlank()) && unit!=null) {
            try { path=planning.work(runId, ledger.unitStatus(runId, unit)).record().sourceXPath(); }
            catch (IOException | RuntimeException unavailable) { /* Exact unit key remains in durable diagnostics if a source locator cannot be read. */ }
        }
        return path==null || path.isBlank() || path.length()>2048?null:path;
    }
    /** One bounded control scope ends before any clinical unit transaction begins. */
    private CdsFieldParser.ControlScope ioControl(String run, long epoch,
            CdsImportCoordinatorService.Owner coordinator, String timeoutCode) {
        long started = System.nanoTime();
        long maximumMillis = configuration.limits().maxDurationMillis();
        return fields.control(() -> {
            coordinator.check();
            var current = ledger.status(run);
            if (Thread.currentThread().isInterrupted() || current.cancelRequested())
                throw new CdsImportException("IMPORT_CANCELLED");
            if (current.ownerEpoch() != epoch || !ledger.ownsAdmission(run, epoch))
                throw new CdsImportException("OWNER_FENCED");
            if (java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) >= maximumMillis)
                throw new CdsImportException(timeoutCode);
        });
    }

}
