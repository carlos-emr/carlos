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

import io.github.carlos_emr.carlos.commn.dao.CdsImportAdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportCheckpointDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportDependencyDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportDiagnosticDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportEventDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportLineageDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportPatientDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportRunDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportUnitDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportAdmission;
import io.github.carlos_emr.carlos.commn.model.CdsImportCheckpoint;
import io.github.carlos_emr.carlos.commn.model.CdsImportDependency;
import io.github.carlos_emr.carlos.commn.model.CdsImportDiagnostic;
import io.github.carlos_emr.carlos.commn.model.CdsImportEvent;
import io.github.carlos_emr.carlos.commn.model.CdsImportLineage;
import io.github.carlos_emr.carlos.commn.model.CdsImportPatient;
import io.github.carlos_emr.carlos.commn.model.CdsImportRun;
import io.github.carlos_emr.carlos.commn.model.CdsImportUnit;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportJobCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportEventDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportPatientDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportUnitDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Short durable administrative transitions. Clinical mutation is owned by CdsImportUnitService.
 * No method leaves an EntityManager graph or open transaction with its caller.
 */
@Service
public class CdsImportLedgerService {
    static final String MAINTENANCE_PENDING_CODE="MAINTENANCE_RELEASE_PENDING";
    static final String MAINTENANCE_DEFERRED_CODE="MAINTENANCE_RELEASE_DEFERRED";
    static final String MAINTENANCE_RELEASE_CODE="MAINTENANCE_RELEASE_REQUIRES_RECONCILIATION";
    static final Set<String> COMPLETE_PATIENT = Set.of("COMPLETE", "COMPLETE_WITH_NONLOSSY_WARNINGS", "ALREADY_COMPLETE");
    static final Set<String> FINAL_PATIENT = Set.of("COMPLETE", "COMPLETE_WITH_NONLOSSY_WARNINGS", "ALREADY_COMPLETE", "REFUSED", "FAILED_BEFORE_WRITE", "CANCELLED_BEFORE_WRITE", "NOT_STARTED", "PARTIAL_COMMITTED", "RECONCILIATION_REQUIRED");
    private final CdsImportRunDao runs;
    private final CdsImportPatientDao patients;
    private final CdsImportUnitDao units;
    private final CdsImportDependencyDao dependencies;
    private final CdsImportLineageDao lineages;
    private final CdsImportCheckpointDao checkpoints;
    private final CdsImportDiagnosticDao diagnostics;
    private final CdsImportAdmissionDao admission;
    private final CdsImportEventDao events;
    private final io.github.carlos_emr.carlos.commn.dao.CdsImportIdentityDao identities;
    private final TransactionTemplate transaction;

    public CdsImportLedgerService(CdsImportRunDao runs, CdsImportPatientDao patients, CdsImportUnitDao units,
            CdsImportDependencyDao dependencies, CdsImportLineageDao lineages, CdsImportCheckpointDao checkpoints,
            CdsImportDiagnosticDao diagnostics, CdsImportAdmissionDao admission, CdsImportEventDao events,
            io.github.carlos_emr.carlos.commn.dao.CdsImportIdentityDao identities, PlatformTransactionManager transactionManager) {
        this.runs=runs; this.patients=patients; this.units=units; this.dependencies=dependencies;
        this.lineages=lineages; this.checkpoints=checkpoints; this.diagnostics=diagnostics;
        this.admission=admission; this.events=events; this.identities=identities;
        transaction=template(transactionManager);
    }

    static TransactionTemplate template(PlatformTransactionManager manager) {
        TransactionTemplate value=new TransactionTemplate(manager);
        value.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        value.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        value.setTimeout(120);
        return value;
    }

    static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CdsImportException("AMBIENT_TRANSACTION_REFUSED");
    }

    private <T> T write(Supplier<T> action) {
        requireNoTransaction();
        return transaction.execute(status -> action.get());
    }

    public CdsImportJobDto accept(CdsImportJobCommand command) {
        Objects.requireNonNull(command, "command");
        CdsImportKeys.token(command.submissionKey(), "submission key", 128);
        CdsImportKeys.required(command.operator(), "operator", 128);
        CdsImportKeys.token(command.providerNo(), "provider", 16);
        CdsImportKeys.token(command.sourceNamespace(), "registered source namespace", 128);
        CdsImportKeys.token(command.profile(), "profile", 64);
        CdsImportKeys.digestValue(command.sourceDigest(), "source digest");
        CdsImportKeys.digestValue(command.optionsDigest(), "options digest");
        CdsImportKeys.digestValue(command.rulesetDigest(), "ruleset digest");
        CdsImportKeys.required(command.snapshotLocation(), "snapshot location", 1024);
        return write(() -> {
            CdsImportRun prior=runs.findSubmission(command.operator(), command.submissionKey());
            if (prior!=null) {
                if (!sameAcceptance(prior, command)) throw new CdsImportException("SUBMISSION_CONFLICT");
                return dto(prior);
            }
            CdsImportRun run=new CdsImportRun();
            run.setId(UUID.randomUUID().toString()); run.setSubmissionKey(command.submissionKey());
            run.setOperator(command.operator()); run.setProviderNo(command.providerNo());
            run.setSourceNamespace(command.sourceNamespace()); run.setProfile(command.profile());
            run.setSourceDigest(command.sourceDigest()); run.setOptionsDigest(command.optionsDigest());
            run.setRulesetDigest(command.rulesetDigest()); run.setSnapshotLocation(command.snapshotLocation());
            run.setDiscoveredFiles(-1); run.setPatientXmlFiles(-1); run.setSourceBytes(-1);
            run.setDryRun(command.dryRun()); run.setReportState("PENDING"); run.setCleanupState("PENDING"); run.setState(command.cli()?"CLI_ACCEPTED":"ACCEPTED"); run.setCreatedAt(Instant.now()); run.setUpdatedAt(run.getCreatedAt());
            runs.persist(run); event(run, null, "ACCEPTED"); runs.flushAndClear();
            return dto(run);
        });
    }

    private boolean sameAcceptance(CdsImportRun run, CdsImportJobCommand command) {
        return run.isDryRun()==command.dryRun() && run.getProviderNo().equals(command.providerNo()) && run.getSourceNamespace().equals(command.sourceNamespace())
                && run.getProfile().equals(command.profile()) && run.getSourceDigest().equals(command.sourceDigest())
                && run.getOptionsDigest().equals(command.optionsDigest()) && run.getRulesetDigest().equals(command.rulesetDigest());
    }

    public CdsImportPatientDto planPatient(String runId, String patientKey, String sourcePatientId, String copyKey, String sourceDigest) { return planPatient(null, runId, patientKey, sourcePatientId, copyKey, sourceDigest); }
    public CdsImportPatientDto planPatient(String runId, long epoch, String patientKey, String sourcePatientId, String copyKey, String sourceDigest) { return planPatient(Long.valueOf(epoch), runId, patientKey, sourcePatientId, copyKey, sourceDigest); }
    private CdsImportPatientDto planPatient(Long epoch, String runId, String patientKey, String sourcePatientId, String copyKey, String sourceDigest) {
        CdsImportKeys.token(patientKey, "patient key", 64);
        CdsImportKeys.required(sourcePatientId, "source patient identifier", 128);
        CdsImportKeys.token(copyKey, "copy key", 64);
        CdsImportKeys.digestValue(sourceDigest, "patient source digest");
        return write(() -> {
            CdsImportRun run=planningOwned(runId, epoch);
            String lineageKey=CdsImportKeys.hash(run.getSourceNamespace(), sourcePatientId, copyKey);
            CdsImportPatient old=patients.findKey(runId, patientKey);
            if (old!=null) {
                if (!old.getLineageKey().equals(lineageKey) || !old.getSourceDigest().equals(sourceDigest)) throw new CdsImportException("PATIENT_PLAN_CONFLICT");
                return dto(old);
            }
            CdsImportLineage lineage=lineages.lock(lineageKey);
            if (lineage==null) {
                lineage=new CdsImportLineage(); lineage.setId(lineageKey); lineage.setSourceNamespace(run.getSourceNamespace());
                lineage.setSourcePatientId(sourcePatientId); lineage.setCopyKey(copyKey); lineage.setSourceDigest(sourceDigest);
                lineage.setOptionsDigest(run.getOptionsDigest()); lineage.setRulesetDigest(run.getRulesetDigest());
                lineages.persist(lineage);
            } else if (!lineage.getSourceDigest().equals(sourceDigest) || !lineage.getOptionsDigest().equals(run.getOptionsDigest())
                    || !lineage.getRulesetDigest().equals(run.getRulesetDigest())) {
                boolean purePreview=lineage.getSourceDigest().equals(sourceDigest) && lineage.getRulesetDigest().equals(run.getRulesetDigest())
                        && lineage.getTargetDemographicNo()==null && checkpoints.countLineage(lineageKey)==0 && patients.actualAttempts(lineageKey)==0;
                if (!purePreview) throw new CdsImportException("SOURCE_REVISION_REVIEW_REQUIRED");
                lineage.setOptionsDigest(run.getOptionsDigest()); event(run, null, "PREVIEW_MAPPING_REVISED");
            }
            CdsImportPatient patient=new CdsImportPatient();
            patient.setId(CdsImportKeys.hash(runId, patientKey)); patient.setRunId(runId); patient.setPatientKey(patientKey);
            patient.setSourcePatientId(sourcePatientId); patient.setCopyKey(copyKey); patient.setLineageKey(lineageKey);
            patient.setSourceDigest(sourceDigest); patient.setState("PLANNED"); patient.setTargetDemographicNo(lineage.getTargetDemographicNo());
            patients.persist(patient); run.setState("PLANNING"); touch(run); return dto(patient);
        });
    }

    public CdsImportUnitDto planUnit(String runId, String patientKey, String unitKey, String category, long ordinal, String sourceDigest, String spoolLocation) { return planUnit(null, runId, patientKey, unitKey, category, ordinal, sourceDigest, spoolLocation); }
    public CdsImportUnitDto planUnit(String runId, long epoch, String patientKey, String unitKey, String category, long ordinal, String sourceDigest, String spoolLocation) { return planUnit(Long.valueOf(epoch), runId, patientKey, unitKey, category, ordinal, sourceDigest, spoolLocation); }
    private CdsImportUnitDto planUnit(Long epoch, String runId, String patientKey, String unitKey, String category, long ordinal, String sourceDigest, String spoolLocation) {
        CdsImportKeys.digestValue(unitKey, "stable unit key"); CdsImportKeys.token(category, "category", 64);
        CdsImportKeys.digestValue(sourceDigest, "unit source digest"); CdsImportKeys.required(spoolLocation, "spool location", 1024);
        if (ordinal<0) throw new IllegalArgumentException("Invalid source ordinal");
        return write(() -> {
            CdsImportRun run=planningOwned(runId, epoch); CdsImportPatient patient=patient(runId, patientKey);
            CdsImportUnit previous=units.findKey(runId, unitKey);
            if (previous!=null) {
                if (!previous.getSourceDigest().equals(sourceDigest) || !previous.getPatientKey().equals(patientKey)
                        || !previous.getCategory().equals(category) || previous.getOrdinal()!=ordinal) throw new CdsImportException("UNIT_PLAN_CONFLICT");
                if (!"EXPECTED".equals(previous.getState())) throw new CdsImportException("COMMITTED_PLAN_REVISION_REFUSED");
                previous.setSpoolLocation(spoolLocation); touch(run);
                return dto(previous);
            }
            CdsImportUnit unit=new CdsImportUnit(); unit.setId(CdsImportKeys.hash(runId, unitKey));
            unit.setRunId(runId); unit.setPatientKey(patientKey); unit.setLineageKey(patient.getLineageKey());
            unit.setUnitKey(unitKey); unit.setCategory(category); unit.setOrdinal(ordinal); unit.setSourceDigest(sourceDigest);
            unit.setSpoolLocation(spoolLocation); unit.setState("EXPECTED"); units.persist(unit); touch(run); return dto(unit);
        });
    }

    public void addDependency(String runId, String unitKey, String dependsOnUnitKey) { addDependency(null, runId, unitKey, dependsOnUnitKey); }
    public void addDependency(String runId, long epoch, String unitKey, String dependsOnUnitKey) { addDependency(Long.valueOf(epoch), runId, unitKey, dependsOnUnitKey); }
    private void addDependency(Long epoch, String runId, String unitKey, String dependsOnUnitKey) {
        if (Objects.equals(unitKey, dependsOnUnitKey)) throw new CdsImportException("SELF_DEPENDENCY_REFUSED");
        write(() -> {
            planningOwned(runId, epoch); CdsImportUnit dependent=unit(runId, unitKey); CdsImportUnit prerequisite=unit(runId, dependsOnUnitKey);
            if (!dependent.getPatientKey().equals(prerequisite.getPatientKey()) || dependencyRank(dependent.getCategory())<=dependencyRank(prerequisite.getCategory())) throw new CdsImportException("DEPENDENCY_ORDER_INVALID");
            String id=CdsImportKeys.hash(runId, unitKey, dependsOnUnitKey);
            if (dependencies.get(id)==null) {
                if (dependencies.count(runId, unitKey)>=8) throw new CdsImportException("UNIT_DEPENDENCY_LIMIT");
                CdsImportDependency dependency=new CdsImportDependency(); dependency.setId(id); dependency.setRunId(runId);
                dependency.setUnitKey(unitKey); dependency.setDependsOnUnitKey(dependsOnUnitKey); dependencies.persist(dependency);
            }
            return null;
        });
    }

    /** Freeze planning first, compute bounded page seals, then publish the seal in a short transaction. */
    public void seal(String runId, String manifestDigest, long expectedPatients, long expectedUnits) { seal(null, runId, manifestDigest, expectedPatients, expectedUnits); }
    public void seal(String runId, long epoch, String manifestDigest, long expectedPatients, long expectedUnits) { seal(Long.valueOf(epoch), runId, manifestDigest, expectedPatients, expectedUnits); }
    private void seal(Long epoch, String runId, String manifestDigest, long expectedPatients, long expectedUnits) {
        CdsImportKeys.digestValue(manifestDigest, "manifest digest");
        if (expectedPatients<1 || expectedUnits<0) throw new CdsImportException("EMPTY_MANIFEST_REFUSED");
        boolean alreadySealed=write(() -> {
            CdsImportRun run=lockSealingOwner(runId, epoch);
            if (run.getExecutionDigest()!=null) {
                if (!manifestDigest.equals(run.getManifestDigest()) || run.getExpectedPatients()!=expectedPatients || run.getExpectedUnits()!=expectedUnits) throw new CdsImportException("MANIFEST_REVISION_REVIEW_REQUIRED");
                return true;
            }
            if ("SEALING".equals(run.getState()) && (!manifestDigest.equals(run.getManifestDigest())
                    || run.getExpectedPatients()!=expectedPatients || run.getExpectedUnits()!=expectedUnits)) throw new CdsImportException("MANIFEST_REVISION_REVIEW_REQUIRED");
            if (patients.count(runId)!=expectedPatients || units.count(runId, null)!=expectedUnits) throw new CdsImportException("MANIFEST_COUNTS_MISMATCH");
            if (diagnostics.unresolved(runId, null)!=0) throw new CdsImportException("PREFLIGHT_BLOCKERS_UNRESOLVED");
            run.setState("SEALING"); run.setManifestDigest(manifestDigest);
            run.setExpectedPatients(expectedPatients); run.setExpectedUnits(expectedUnits); touch(run); return false;
        });
        if (alreadySealed) return;
        String after="";
        while (true) {
            List<PatientSeal> page=patientSeals(runId, after);
            if (page.isEmpty()) break;
            for (PatientSeal patient:page) {
                String expectedDigest=patientDigest(runId, patient.key());
                write(() -> {
                    requireSealing(runId, manifestDigest, epoch);
                    CdsImportPatient managed=patients.lock(patient.id());
                    long count=units.count(runId, patient.key());
                    if (count<1 && !Set.of("REFUSED", "FAILED_BEFORE_WRITE", "CANCELLED_BEFORE_WRITE", "NOT_STARTED").contains(managed.getState())) throw new CdsImportException("PATIENT_WITHOUT_EXPECTED_WORK");
                    managed.setExpectedUnits(count); managed.setExecutionDigest(expectedDigest); return null;
                });
                after=patient.key();
            }
        }
        String expectedDigest=executionDigest(runId);
        write(() -> {
            CdsImportRun run=requireSealing(runId, manifestDigest, epoch);
            if (patients.count(runId)!=expectedPatients || units.count(runId, null)!=expectedUnits) throw new CdsImportException("MANIFEST_COUNTS_MISMATCH");
            if (diagnostics.unresolved(runId, null)!=0) throw new CdsImportException("PREFLIGHT_BLOCKERS_UNRESOLVED");
            run.setExecutionDigest(expectedDigest); run.setState("READY"); touch(run);
            event(run, null, "MANIFEST_SEALED"); return null;
        });
    }

    private CdsImportRun lockSealingOwner(String runId, Long epoch) {
        if (epoch==null) {
            CdsImportRun run=requiredRun(runId, true); if (run.getOwnerEpoch()!=0) throw new CdsImportException("PLANNING_OWNER_REQUIRED"); return run;
        }
        CdsImportAdmission gate=admission.lock("global"); CdsImportRun run=requiredRun(runId, true);
        if (gate==null || !runId.equals(gate.getOwnerRunId()) || run.getOwnerEpoch()!=epoch) throw new CdsImportException("OWNER_FENCED");
        return run;
    }

    private CdsImportRun requireSealing(String runId, String digest, Long epoch) {
        CdsImportRun run=lockSealingOwner(runId, epoch);
        if (!"SEALING".equals(run.getState()) || run.getExecutionDigest()!=null || !digest.equals(run.getManifestDigest())) throw new CdsImportException("MANIFEST_SEAL_CONFLICT");
        return run;
    }

    /** Caller must hold the approved migration-window/advisory protocol; never infer takeover from a heartbeat. */
    public long acquire(String runId) { return claimExecution(runId, false); }
    private long claimExecution(String runId, boolean resumed) {
        return write(() -> {
            CdsImportAdmission gate=admission.lock("global");
            if (gate==null) throw new CdsImportException("IMPORT_SCHEMA_NOT_READY");
            if (gate.getOwnerRunId()!=null && !gate.getOwnerRunId().equals(runId)) throw new CdsImportException("IMPORT_BUSY");
            CdsImportRun run=requiredRun(runId, true);
            if (run.getExecutionDigest()==null) throw new CdsImportException("UNSEALED_MANIFEST_REFUSED");
            if (resumed && run.isCancelRequested()) throw new CdsImportException("IMPORT_CANCELLED");
            if (run.getOwnerEpoch()==Long.MAX_VALUE) throw new CdsImportException("OWNER_EPOCH_EXHAUSTED");
            gate.setOwnerRunId(runId); run.setOwnerEpoch(run.getOwnerEpoch()+1);
            if (!"COMPLETE".equals(run.getState())) run.setState("RUNNING");
            touch(run); event(run, null, "OWNER_ACQUIRED"); return run.getOwnerEpoch();
        });
    }

    /** Administrative fencing precedes window acquisition; this does not grant a clinical write capability. */
    public long claimPreparation(String runId) {
        return write(() -> { CdsImportAdmission gate=admission.lock("global");
            if(gate==null) throw new CdsImportException("IMPORT_SCHEMA_NOT_READY");
            if(gate.getOwnerRunId()!=null && !runId.equals(gate.getOwnerRunId())) throw new CdsImportException("IMPORT_BUSY");
            CdsImportRun run=requiredRun(runId,true); if(run.getOwnerEpoch()==Long.MAX_VALUE) throw new CdsImportException("OWNER_EPOCH_EXHAUSTED");
            gate.setOwnerRunId(runId);run.setOwnerEpoch(run.getOwnerEpoch()+1);touch(run);event(run,null,"PREPARATION_OWNER_ACQUIRED");return run.getOwnerEpoch(); });
    }
    public boolean ownsAdmission(String runId,long epoch) {
        return write(() -> { CdsImportAdmission gate=admission.get("global");CdsImportRun run=requiredRun(runId,false);return gate!=null && runId.equals(gate.getOwnerRunId()) && run.getOwnerEpoch()==epoch; });
    }
    public void preparationStopped(String runId,long epoch,String code,String evidence) {
        stoppedOperationally(runId,epoch,code,"PREPARATION","Import preparation stopped; retained work requires authorized recovery");
        if(evidence!=null) write(() -> { lockOperational(runId,epoch);addDiagnostic(runId,null,null,"INFO","IMPORT_FAILURE_EVIDENCE","OBSERVABILITY",null,"Protected bounded failure evidence is available",evidence,false);return null; });
    }

    /** Claim preflight without allowing clinical units; all native writes still require the final seal. */
    public long claimPlanning(String runId) { return claimPlanning(runId, false); }

    public long claimPlanning(String runId, boolean resume) { return claimPlanning(runId, resume, false); }
    /** Finish an already accepted administrative review without erasing a later cancellation. */
    public long claimReviewPlanning(String runId) { return claimPlanning(runId, true, true); }
    private long claimPlanning(String runId, boolean resume, boolean reviewAdministrative) {
        return write(() -> {
            CdsImportAdmission gate=admission.lock("global");
            if (gate==null) throw new CdsImportException("IMPORT_SCHEMA_NOT_READY");
            if (gate.getOwnerRunId()!=null && !runId.equals(gate.getOwnerRunId())) throw new CdsImportException("IMPORT_BUSY");
            CdsImportRun run=requiredRun(runId, true);
            if (run.getExecutionDigest()!=null) throw new CdsImportException("SEALED_RUN_REQUIRES_EXECUTION_OWNER");
            if (run.getOwnerEpoch()==Long.MAX_VALUE) throw new CdsImportException("OWNER_EPOCH_EXHAUSTED");
            gate.setOwnerRunId(runId); run.setOwnerEpoch(run.getOwnerEpoch()+1);
            if (resume) { if (run.isCancelRequested() && !reviewAdministrative) throw new CdsImportException("IMPORT_CANCELLED"); if (Set.of("FAILED_BEFORE_WRITE", "CANCELLED_BEFORE_WRITE", "REVIEW_REQUIRED").contains(run.getState())) run.setState(run.getManifestDigest()==null?"PLANNING":"SEALING"); }
            touch(run); event(run, null, "PLANNING_OWNER_ACQUIRED"); return run.getOwnerEpoch();
        });
    }

    CdsImportRun lockPlanningOwned(String runId, long epoch) { return planningOwned(runId, Long.valueOf(epoch)); }

    private CdsImportRun planningOwned(String runId, Long epoch) {
        if (epoch==null) { CdsImportAdmission gate=admission.lock("global"); if (gate!=null && gate.getOwnerRunId()!=null) throw new CdsImportException("PLANNING_OWNER_REQUIRED"); CdsImportRun run=planning(runId); if (run.getOwnerEpoch()!=0) throw new CdsImportException("PLANNING_OWNER_REQUIRED"); return run; }
        CdsImportAdmission gate=admission.lock("global");
        if (gate==null || !Objects.equals(gate.getOwnerRunId(), runId)) throw new CdsImportException("OWNER_FENCED");
        CdsImportRun run=planning(runId);
        if (run.getOwnerEpoch()!=epoch) throw new CdsImportException("OWNER_FENCED");
        return run;
    }

    public boolean isSealed(String runId) { return write(() -> requiredRun(runId, false).getExecutionDigest()!=null); }

    public void endPreflight(String runId, long epoch, String outcome, String safeCode) { endPreflight(runId, epoch, outcome, safeCode, null); }
    public void endPreflight(String runId, long epoch, String outcome, String safeCode, String sourcePath) {
        CdsImportKeys.optional(sourcePath, "source path", 2048);
        if (!Set.of("FAILED_BEFORE_WRITE", "CANCELLED_BEFORE_WRITE", "REVIEW_REQUIRED").contains(outcome)) throw new IllegalArgumentException("Invalid preflight outcome");
        CdsImportKeys.token(safeCode, "error code", 96);
        write(() -> { CdsImportRun run=lockOperational(runId, epoch);
            if (run.getExecutionDigest()!=null || units.countState(runId, null, "COMMITTED")!=0 || units.countState(runId, null, "ALREADY_COMPLETE")!=0) throw new CdsImportException("COMMITTED_OUTCOME_REQUIRES_RECONCILIATION");
            run.setState(outcome); run.setSafeErrorCode(safeCode); touch(run);
            addDiagnostic(runId, null, null, "ERROR", safeCode, "PREFLIGHT_STOP", sourcePath, "Import preflight did not complete", null, true); event(run, null, outcome); return null; });
        String after="";
        while (true) {
            final String cursor=after;
            List<String> page=write(() -> { CdsImportRun run=lockOperational(runId, epoch); List<CdsImportPatient> values=patients.page(runId, cursor, 200);
                for (CdsImportPatient p:values) if ("PLANNED".equals(p.getState())) p.setState("CANCELLED_BEFORE_WRITE".equals(outcome)?"CANCELLED_BEFORE_WRITE":"NOT_STARTED");
                touch(run); return values.stream().map(CdsImportPatient::getPatientKey).toList(); });
            if (page.isEmpty()) break; after=page.get(page.size()-1);
        }
    }

    /**
     * After the review service durably archives the old plan and publishes its versioned proof,
     * clear only administrative EXPECTED work. Every phase is bounded and restartable; native data
     * and original source evidence are never deleted. The review service must retain its coordinator.
     */
    public void resetUnsealedPlan(String runId, long epoch, long reviewVersion) {
        if (reviewVersion<1) throw new CdsImportException("PUBLISHED_REVIEW_REQUIRED");
        write(() -> { CdsImportRun run=lockSealingOwner(runId, epoch);
            if (run.getReviewVersion()!=reviewVersion) throw new CdsImportException("REVIEW_VERSION_CONFLICT");
            if (run.getManifestDigest()!=null || run.getExecutionDigest()!=null || units.countState(runId, null, "COMMITTED")!=0 || units.countState(runId, null, "ALREADY_COMPLETE")!=0 || patients.heldForRun(runId)!=0) throw new CdsImportException("REVIEW_AFTER_WRITE_REFUSED");
            run.setState("REPLANNING"); run.setReportState("PENDING"); run.setCleanupState("PENDING"); touch(run); event(run, null, "REVIEW_REPLAN_STARTED"); return null;
        });
        String after="";
        while (true) {
            final String cursor=after;
            List<String> page=write(() -> { requireReviewReset(runId, epoch, reviewVersion); List<CdsImportPatient> values=patients.page(runId, cursor, 200);
                List<String> keys=values.stream().map(CdsImportPatient::getLineageKey).toList();
                if (values.stream().anyMatch(p -> p.isHeld() || p.getTargetDemographicNo()!=null) || lineages.boundTargets(keys)!=0 || checkpoints.anyForLineages(keys)!=0) throw new CdsImportException("REVIEW_AFTER_WRITE_REFUSED");
                return values.stream().map(CdsImportPatient::getPatientKey).toList();
            });
            if (page.isEmpty()) break; after=page.get(page.size()-1);
        }
        while (write(() -> { requireReviewReset(runId, epoch, reviewVersion); var page=identities.page(runId, 200); for (var identity:page) identities.remove(identity); identities.flushAndClear(); return !page.isEmpty(); })) { /* Clear only administrative identity evidence for the reviewed replan. */ }
        while (write(() -> { requireReviewReset(runId, epoch, reviewVersion); List<CdsImportDependency> page=dependencies.all(runId, "", 200); for (CdsImportDependency d:page) dependencies.remove(d); dependencies.flushAndClear(); return !page.isEmpty(); })) { /* Bounded committed deletion pages. */ }
        while (write(() -> { requireReviewReset(runId, epoch, reviewVersion); List<CdsImportUnit> page=units.page(runId, null, "", 200);
            for (CdsImportUnit u:page) { if (!"EXPECTED".equals(u.getState())) throw new CdsImportException("REVIEW_AFTER_WRITE_REFUSED"); units.remove(u); }
            units.flushAndClear(); return !page.isEmpty(); })) { /* Bounded committed deletion pages. */ }
        after="";
        while (true) {
            final String cursor=after;
            List<String> page=write(() -> { requireReviewReset(runId, epoch, reviewVersion); List<CdsImportPatient> values=patients.page(runId, cursor, 200);
                for (CdsImportPatient p:values) { p.setExpectedUnits(0); p.setExecutionDigest(null); if (!"REFUSED".equals(p.getState())) p.setState("PLANNED"); }
                List<String> keys=values.stream().map(CdsImportPatient::getPatientKey).toList(); patients.flushAndClear(); return keys;
            });
            if (page.isEmpty()) break; after=page.get(page.size()-1);
        }
        write(() -> { CdsImportRun run=requireReviewReset(runId, epoch, reviewVersion); run.setDiscoveryComplete(false); run.setState("PLANNING"); touch(run); event(run, null, "REVIEW_REPLAN_READY"); return null; });
    }

    private CdsImportRun requireReviewReset(String runId, long epoch, long reviewVersion) {
        CdsImportRun run=lockSealingOwner(runId, epoch);
        if (run.getReviewVersion()!=reviewVersion || !"REPLANNING".equals(run.getState()) || run.getManifestDigest()!=null || run.getExecutionDigest()!=null) throw new CdsImportException("REVIEW_RESET_FENCED");
        return run;
    }

    public List<io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportDependencyDto> dependencyEvidence(String runId, String afterId, int limit) {
        CdsImportKeys.pageSize(limit);
        return write(() -> dependencies.all(runId, afterId==null?"":afterId, limit).stream().map(d -> new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportDependencyDto(d.getId(), d.getUnitKey(), d.getDependsOnUnitKey())).toList());
    }

    /** Preflight reuse requires exact complete prior coverage, never completion of an abandoned partial import. */
    public void requireCompleteCanonicalReplay(String runId, long epoch, String patientKey) {
        String after=""; long count=0;
        while (true) {
            final String cursor=after;
            List<String> page=write(() -> { CdsImportRun run=lockPlanningOwned(runId, epoch); CdsImportPatient patient=patient(runId, patientKey); verifyCanonical(run, patient);
                if (patient.getTargetDemographicNo()==null) throw new CdsImportException("CANONICAL_TARGET_MISSING");
                List<String> keys=units.page(runId, patientKey, cursor, 200).stream().map(CdsImportUnit::getUnitKey).toList();
                if (checkpoints.incompleteReplay(runId, keys, run.getOptionsDigest(), run.getRulesetDigest(), patient.getTargetDemographicNo())!=0) throw new CdsImportException("PRIOR_IMPORT_MUST_RESUME");
                units.flushAndClear(); return keys;
            });
            if (page.isEmpty()) break; count+=page.size(); after=page.get(page.size()-1);
        }
        if (count==0) throw new CdsImportException("PRIOR_IMPORT_MUST_RESUME");
    }

    /** Exact canonical conflict projection under the same administrative and lineage locks used by approval. */
    public io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportRevisionDto unwrittenRevision(
            String runId, long epoch, String sourcePatientId, String copyKey, String newPatientSourceDigest) {
        CdsImportKeys.required(sourcePatientId, "source patient identifier", 128); CdsImportKeys.token(copyKey, "copy key", 64);
        CdsImportKeys.digestValue(newPatientSourceDigest, "patient source digest");
        return write(() -> revision(lockPlanningOwned(runId, epoch), sourcePatientId, copyKey, newPatientSourceDigest));
    }

    /** Stable bounded provenance pages; callers archive every page before publishing approval. */
    public List<String> unwrittenRevisionPriorRuns(String runId, long epoch, String sourcePatientId, String copyKey,
            String afterRunId, int limit) {
        CdsImportKeys.required(sourcePatientId, "source patient identifier", 128); CdsImportKeys.token(copyKey, "copy key", 64); CdsImportKeys.pageSize(limit);
        return write(() -> { CdsImportRun run=lockPlanningOwned(runId, epoch);
            String lineageKey=CdsImportKeys.hash(run.getSourceNamespace(), sourcePatientId, copyKey); if (lineages.lock(lineageKey)==null) throw new CdsImportException("CANONICAL_LINEAGE_NOT_FOUND");
            return List.copyOf(patients.lineageRuns(lineageKey, runId, afterRunId==null?"":afterRunId, limit)); });
    }

    private io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportRevisionDto revision(
            CdsImportRun run, String sourcePatientId, String copyKey, String newSourceDigest) {
        String key=CdsImportKeys.hash(run.getSourceNamespace(), sourcePatientId, copyKey);
        CdsImportLineage canonical=lineages.lock(key);
        if (canonical==null) throw new CdsImportException("CANONICAL_LINEAGE_NOT_FOUND");
        long checkpointCount=checkpoints.countLineage(key);
        boolean differs=!canonical.getSourceDigest().equals(newSourceDigest) || !canonical.getOptionsDigest().equals(run.getOptionsDigest()) || !canonical.getRulesetDigest().equals(run.getRulesetDigest());
        boolean eligible=differs && canonical.getTargetDemographicNo()==null && checkpointCount==0
                && !patients.lineageHasWrittenOrUncertainAttempt(key) && !patients.runHasWrittenOrUncertainAttempt(run.getId());
        List<String> prior=patients.lineageRuns(key, run.getId(), "", 100); long total=patients.lineageRunCount(key, run.getId());
        return new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportRevisionDto(run.getId(), key, key,
                run.getSourceNamespace(), sourcePatientId, copyKey, canonical.getSourceDigest(), canonical.getOptionsDigest(), canonical.getRulesetDigest(),
                newSourceDigest, run.getOptionsDigest(), run.getRulesetDigest(), canonical.getTargetDemographicNo(), checkpointCount,
                eligible, prior, total, total>prior.size());
    }

    /** Publication participant for explicitly reviewed raw-source/policy changes, with no prior clinical effects. */
    public void approveUnwrittenRevision(String runId, long epoch, long expectedReviewVersion,
            String sourcePatientId, String copyKey, String expectedOldSourceDigest, String expectedOldOptionsDigest,
            String expectedOldRulesetDigest, String newPatientSourceDigest) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CdsImportException("REVIEW_REQUIRES_TRANSACTION");
        CdsImportKeys.required(sourcePatientId, "source patient identifier", 128); CdsImportKeys.token(copyKey, "copy key", 64);
        CdsImportKeys.digestValue(expectedOldSourceDigest, "expected old source digest"); CdsImportKeys.digestValue(expectedOldOptionsDigest, "expected old options digest");
        CdsImportKeys.digestValue(expectedOldRulesetDigest, "expected old ruleset digest"); CdsImportKeys.digestValue(newPatientSourceDigest, "reviewed source digest");
        CdsImportRun run=lockPlanningOwned(runId, epoch);
        if (run.getReviewVersion()!=expectedReviewVersion) throw new CdsImportException("REVIEW_VERSION_CONFLICT");
        var proof=revision(run, sourcePatientId, copyKey, newPatientSourceDigest);
        if (!proof.eligible()) throw new CdsImportException("UNWRITTEN_REVISION_AFTER_WRITE_OR_UNCERTAINTY_REFUSED");
        if (!proof.oldSourceDigest().equals(expectedOldSourceDigest) || !proof.oldOptionsDigest().equals(expectedOldOptionsDigest)
                || !proof.oldRulesetDigest().equals(expectedOldRulesetDigest)) throw new CdsImportException("CANONICAL_REVISION_CONFLICT");
        CdsImportLineage canonical=lineages.lock(proof.lineageKey()); canonical.setSourceDigest(newPatientSourceDigest);
        canonical.setOptionsDigest(run.getOptionsDigest()); canonical.setRulesetDigest(run.getRulesetDigest());
        CdsImportPatient existing=patients.findKey(runId, proof.patientKey());
        if (existing!=null) { existing.setSourceDigest(newPatientSourceDigest); existing.setState("PLANNED"); }
        touch(run); event(run, null, "UNWRITTEN_CANONICAL_REVISION_APPROVED");
    }

    /** Participant API for an independently validated, versioned preflight correction; no committed lineage may change. */
    public void reviseUncommittedLineage(String runId, long epoch, String patientKey, long expectedReviewVersion, String newPatientSourceDigest) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CdsImportException("REVIEW_REQUIRES_TRANSACTION");
        CdsImportKeys.digestValue(newPatientSourceDigest, "reviewed source digest");
        CdsImportRun run=lockPlanningOwned(runId, epoch);
        if (run.getReviewVersion()!=expectedReviewVersion) throw new CdsImportException("REVIEW_VERSION_CONFLICT");
        CdsImportPatient patient=patient(runId, patientKey); patient=patients.lock(patient.getId());
        CdsImportLineage lineage=lineages.lock(patient.getLineageKey());
        if (patient.isHeld() || patient.getTargetDemographicNo()!=null || lineage.getTargetDemographicNo()!=null
                || checkpoints.countLineage(lineage.getId())!=0 || units.count(runId, patientKey)!=0) throw new CdsImportException("COMMITTED_OR_PLANNED_LINEAGE_REQUIRES_NEW_REVIEW");
        lineage.setSourceDigest(newPatientSourceDigest); lineage.setOptionsDigest(run.getOptionsDigest()); lineage.setRulesetDigest(run.getRulesetDigest());
        patient.setSourceDigest(newPatientSourceDigest); patient.setState("PLANNED"); touch(run); event(run, null, "UNCOMMITTED_LINEAGE_REVIEWED");
    }

    void verifyCanonical(CdsImportRun run, CdsImportPatient patient) {
        CdsImportLineage lineage=lineages.lock(patient.getLineageKey());
        if (lineage==null || !lineage.getSourceDigest().equals(patient.getSourceDigest()) || !lineage.getOptionsDigest().equals(run.getOptionsDigest())
                || !lineage.getRulesetDigest().equals(run.getRulesetDigest())) throw new CdsImportException("SOURCE_REVISION_REVIEW_REQUIRED");
    }

    public void preparePreflightResume(String runId, long epoch) {
        String after="";
        while (true) {
            final String cursor=after;
            List<String> page=write(() -> { CdsImportRun run=lockSealingOwner(runId, epoch); if (run.getExecutionDigest()!=null) throw new CdsImportException("MANIFEST_ALREADY_SEALED"); if ("REPLANNING".equals(run.getState())) throw new CdsImportException("REVIEW_RESET_IN_PROGRESS"); List<CdsImportPatient> values=patients.page(runId, cursor, 200);
                for (CdsImportPatient p:values) if (Set.of("NOT_STARTED", "FAILED_BEFORE_WRITE", "CANCELLED_BEFORE_WRITE").contains(p.getState())) {
                    if (p.isHeld()) throw new CdsImportException("UNSEALED_CLINICAL_HOLD_REQUIRES_RECONCILIATION"); p.setState("PLANNED");
                }
                return values.stream().map(CdsImportPatient::getPatientKey).toList(); });
            if (page.isEmpty()) break; after=page.get(page.size()-1);
        }
    }

    /** Caller has just reconstructed and verified the exact accepted actor's live authorization. */
    public void resolveReauthorizedAccess(String runId, long epoch) { resolveProvenStage(runId, epoch, "ACCESS", null, false); }

    /** Called only after the planner's complete effective-source scan and immutable expected manifest proof. */
    public void resolveRevalidatedPreflightStops(String runId, long epoch, String validatedManifest) {
        CdsImportKeys.digestValue(validatedManifest, "validated manifest"); resolveProvenStage(runId, epoch, "PREFLIGHT_STOP", validatedManifest, true);
    }

    private void resolveProvenStage(String runId, long epoch, String stage, String validatedManifest, boolean requireDiscovery) {
        while (write(() -> { CdsImportRun run=lockSealingOwner(runId, epoch);
            if (requireDiscovery && (!run.isDiscoveryComplete() || run.getExecutionDigest()!=null || run.getManifestDigest()!=null && !run.getManifestDigest().equals(validatedManifest))) throw new CdsImportException("PREFLIGHT_REVALIDATION_REQUIRED");
            List<CdsImportDiagnostic> page=diagnostics.unresolvedStage(runId, stage, 100); for (CdsImportDiagnostic item:page) item.setResolved(true);
            return !page.isEmpty(); })) { /* Typed proof retires only the matching former supervision stop. */ }
    }

    public void preparePatientResume(String runId, long epoch, String patientKey) {
        write(() -> { CdsImportRun run=lockOwned(runId, epoch); CdsImportPatient patient=patient(runId, patientKey); patient=patients.lock(patient.getId());
            if (COMPLETE_PATIENT.contains(patient.getState()) || "REFUSED".equals(patient.getState())) return null;
            patient.setState(patient.isHeld()?"IMPORTING":"PLANNED"); touch(run); event(run, null, "PATIENT_RESUME_PREPARED"); return null; });
    }

    /** Recovery resolves only execution-stop diagnostics after every non-refused unit has durable verified work. */
    public void resolveRecoveredExecution(String runId, long epoch) {
        long after=0;
        while (true) {
            final long cursor=after;
            List<Long> page=write(() -> { lockOwned(runId, epoch); return diagnostics.page(runId, cursor, 100).stream().map(CdsImportDiagnostic::getId).toList(); });
            if (page.isEmpty()) break;
            for (Long id:page) { write(() -> { lockOwned(runId, epoch); CdsImportDiagnostic item=diagnostics.lock(id);
                if (Set.of("EXECUTION", "ACCESS", "SUPERVISION").contains(item.getStage()) && item.isBlocking() && !item.isResolved()) {
                    if (item.getPatientKey()!=null && units.incomplete(runId, item.getPatientKey())!=0) throw new CdsImportException("EXECUTION_RECOVERY_INCOMPLETE");
                    if (item.getPatientKey()==null && units.unrecovered(runId)!=0) throw new CdsImportException("EXECUTION_RECOVERY_INCOMPLETE");
                    item.setResolved(true);
                }
                return null; }); after=id; }
        }
    }

    public void markIncomplete(String runId, long epoch, String patientKey, boolean affected, boolean cancelled, boolean uncertain) {
        write(() -> { CdsImportRun run=lockOwned(runId, epoch); CdsImportPatient patient=patient(runId, patientKey); patient=patients.lock(patient.getId());
            if (COMPLETE_PATIENT.contains(patient.getState())) return null;
            long committed=units.countState(runId, patientKey, "COMMITTED")+units.countState(runId, patientKey, "ALREADY_COMPLETE");
            String outcome=uncertain?"RECONCILIATION_REQUIRED":(committed>0?"PARTIAL_COMMITTED":(affected?(cancelled?"CANCELLED_BEFORE_WRITE":"FAILED_BEFORE_WRITE"):"NOT_STARTED"));
            patient.setState(outcome); touch(run); event(run, null, "PATIENT_"+outcome); return null; });
    }

    public void observe(String runId, String operator, String providerNo, String operation, long osUid, String verifiedNonce) {
        if (!Set.of("import", "resume", "cancel", "status", "report").contains(operation) || osUid<0) throw new IllegalArgumentException("Invalid CLI observation");
        CdsImportKeys.token(verifiedNonce, "verified delegation nonce", 128);
        write(() -> { CdsImportRun run=requiredRun(runId, true);
            if (!Objects.equals(run.getOperator(), operator) || !Objects.equals(run.getProviderNo(), providerNo)) throw new SecurityException("IMPORT_ACCESS_DENIED");
            CdsImportEvent item=new CdsImportEvent(); item.setRunId(runId); item.setEpoch(run.getOwnerEpoch()); item.setEventType("CLI_"+operation.toUpperCase(java.util.Locale.ROOT));
            item.setOperator(operator); item.setProviderNo(providerNo); item.setCreatedAt(Instant.now());
            item.setObservationContext("{\"uid\":"+osUid+",\"nonce\":\""+verifiedNonce+"\"}"); events.persist(item); return null; });
    }

    /** Explicit resume clears cancellation only after a new fenced owner was established. */
    public long resume(String runId) { return claimExecution(runId, true); }

    /** Report-only ownership also advances the epoch, fencing a coordinator whose physical lock was lost. */
    public long claimReporting(String runId) {
        return write(() -> { CdsImportRun run=requiredRun(runId, true);
            if (!Set.of("COMPLETE", "DRY_RUN_COMPLETE", "DRY_RUN_INCOMPLETE", "INCOMPLETE", "RECONCILIATION_REQUIRED", "FAILED_BEFORE_WRITE", "CANCELLED_BEFORE_WRITE", "REVIEW_REQUIRED").contains(run.getState())) throw new CdsImportException("REPORT_OWNER_REQUIRES_STOPPED_RUN");
            if (run.getOwnerEpoch()==Long.MAX_VALUE) throw new CdsImportException("OWNER_EPOCH_EXHAUSTED");
            run.setOwnerEpoch(run.getOwnerEpoch()+1); run.setReportState("PENDING"); run.setCleanupState("PENDING"); touch(run); event(run, null, "REPORT_OWNER_ACQUIRED"); return run.getOwnerEpoch(); });
    }

    /** Release only administrative admission; chart holds, source and checkpoints are never cleared here. */
    public void release(String runId, long epoch) {
        write(() -> {
            CdsImportAdmission gate=admission.lock("global");
            CdsImportRun run=requiredRun(runId, true);
            if (run.getOwnerEpoch()!=epoch || (gate.getOwnerRunId()!=null && !runId.equals(gate.getOwnerRunId()))) throw new CdsImportException("OWNER_FENCED");
            gate.setOwnerRunId(null); return null;
        });
    }

    public CdsImportJobDto completeDryRun(String runId, long epoch) {
        return write(() -> {
            CdsImportAdmission gate=admission.lock("global"); CdsImportRun run=lockOperational(runId, epoch);
            if (!run.isDryRun() || run.getExecutionDigest()==null || units.countState(runId, null, "COMMITTED")!=0
                    || units.countState(runId, null, "ALREADY_COMPLETE")!=0 || diagnostics.unresolved(runId, null)!=0) throw new CdsImportException("DRY_RUN_INCOMPLETE");
            boolean valid=patients.outcomes(runId, 17).stream().allMatch(row -> "PLANNED".equals(row[0]));
            run.setState(valid?"DRY_RUN_COMPLETE":"DRY_RUN_INCOMPLETE"); if (valid) { run.setSafeErrorCode(null); awaitMaintenanceRelease(run); } touch(run); event(run, null, run.getState());
            if (runId.equals(gate.getOwnerRunId())) gate.setOwnerRunId(null);
            return dto(run);
        });
    }

    /** Participates in a clinical unit; new chart creation and its hold must share that transaction. */
    public void bindTarget(String runId, long epoch, String patientKey, int demographicNo) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CdsImportException("TARGET_BIND_REQUIRES_UNIT");
        String activeUnit=CdsImportUnitService.requireActive(runId, epoch, null);
        if (!patientKey.equals(unit(runId, activeUnit).getPatientKey())) throw new CdsImportException("UNIT_PATIENT_CONFLICT");
        if (demographicNo<1) throw new IllegalArgumentException("Invalid target");
        CdsImportRun run=lockOwned(runId, epoch);
        CdsImportPatient patient=patient(runId, patientKey); patient=patients.lock(patient.getId());
        if (patient.getTargetDemographicNo()!=null && patient.getTargetDemographicNo()!=demographicNo) throw new CdsImportException("TARGET_REVISION_REVIEW_REQUIRED");
        CdsImportLineage lineage=lineages.lock(patient.getLineageKey());
        if (lineage.getTargetDemographicNo()!=null && lineage.getTargetDemographicNo()!=demographicNo) throw new CdsImportException("LINEAGE_TARGET_CONFLICT");
        if (patients.held(demographicNo) && !patient.isHeld()) throw new CdsImportException("TARGET_ALREADY_HELD");
        patient.setTargetDemographicNo(demographicNo); patient.setHeld(true); patient.setState("IMPORTING");
        lineage.setTargetDemographicNo(demographicNo); touch(run);
    }

    public CdsImportJobDto status(String runId) { return write(() -> dto(requiredRun(runId, false))); }
    /** Protected internal source location; the application facade must authorize before calling. */
    public String snapshotLocation(String runId) { return write(() -> requiredRun(runId, false).getSnapshotLocation()); }

    /** Aggregates only bounded groups; no source filenames, identifiers, paths or clinical values. */
    public Map<String, Object> summary(String runId) {
        return write(() -> {
            CdsImportRun run=requiredRun(runId, false);
            Map<String, Object> value=new LinkedHashMap<>();
            value.put("runId", runId); value.put("state", run.getState()); value.put("dryRun", run.isDryRun());
            value.put("clinicalComplete", !run.isDryRun() && "COMPLETE".equals(run.getState()));
            boolean sealed=run.getExecutionDigest()!=null;
            value.put("discoveryComplete", run.isDiscoveryComplete());
            boolean fileInventory=run.getDiscoveredFiles()>=0 && run.getPatientXmlFiles()>=0 && run.getSourceBytes()>=0;
            value.put("sourceFileInventoryKnown", fileInventory);
            value.put("discoveredFiles", fileInventory?run.getDiscoveredFiles():null);
            value.put("patientXmlFiles", fileInventory?run.getPatientXmlFiles():null);
            value.put("sourceBytes", fileInventory?run.getSourceBytes():null);
            value.put("sourcePatientsIdentified", patients.sourcePatients(runId));
            value.put("sourcePatients", run.isDiscoveryComplete()?patients.sourcePatients(runId):null);
            value.put("targetCopiesPlanned", patients.count(runId)); value.put("inventoryState", sealed?"SEALED":"UNSEALED");
            value.put("identifiedPatients", patients.count(runId));
            value.put("expectedPatients", sealed?run.getExpectedPatients():null);
            value.put("plannedUnits", units.count(runId, null));
            value.put("expectedUnits", sealed?run.getExpectedUnits():null);
            long committed=units.countState(runId, null, "COMMITTED");
            long reused=units.countState(runId, null, "ALREADY_COMPLETE");
            value.put("committedUnits", committed); value.put("alreadyCompleteUnits", reused);
            value.put("pendingUnits", units.countState(runId, null, "EXPECTED"));
            Map<String, Long> outcomes=new LinkedHashMap<>();
            List<Object[]> outcomeRows=patients.outcomes(runId, 17);
            if (outcomeRows.size()>16) throw new CdsImportException("UNEXPECTED_OUTCOME_CARDINALITY");
            for (Object[] row:outcomeRows) outcomes.put((String) row[0], ((Number) row[1]).longValue());
            value.put("outcomes", Map.copyOf(outcomes));
            Map<String, long[]> grouped=new LinkedHashMap<>();
            List<Object[]> categoryRows=units.categories(runId, 129);
            if (categoryRows.size()>128) throw new CdsImportException("UNEXPECTED_CATEGORY_CARDINALITY");
            long committedOccurrences=0, priorOccurrences=0;
            for (Object[] row:categoryRows) {
                String raw=(String) row[0]; String category=clinicalCategory(raw);
                long[] counts=grouped.computeIfAbsent(category, ignored -> new long[6]);
                int offset=isHelperCategory(raw)?3:0;
                counts[offset]+=((Number) row[1]).longValue();
                counts[offset+1]+=((Number) row[2]).longValue(); counts[offset+2]+=((Number) row[3]).longValue();
                if (offset==0) { committedOccurrences+=((Number) row[2]).longValue(); priorOccurrences+=((Number) row[3]).longValue(); }
            }
            List<Map<String, Object>> categories=new ArrayList<>();
            for (Map.Entry<String, long[]> entry:grouped.entrySet()) {
                long[] c=entry.getValue(); Map<String, Object> row=new LinkedHashMap<>();
                row.put("category", entry.getKey()); row.put("expected", c[0]); row.put("committed", c[1]); row.put("alreadyComplete", c[2]); row.put("pending", c[0]-c[1]-c[2]);
                row.put("expectedHelperUnits", c[3]); row.put("committedHelperUnits", c[4]); row.put("alreadyCompleteHelperUnits", c[5]); row.put("pendingHelperUnits", c[3]-c[4]-c[5]);
                categories.add(Map.copyOf(row));
            }
            value.put("categories", List.copyOf(categories)); value.put("committedClinicalOccurrences", committedOccurrences); value.put("alreadyCompleteClinicalOccurrences", priorOccurrences);
            value.put("unresolvedReviewItems", diagnostics.unresolved(runId, null));
            value.put("cancelRequested", run.isCancelRequested()); value.put("lastCheckpoint", run.getUpdatedAt().toString());
            boolean delivered="DELIVERED".equals(run.getReportState());
            boolean releasePending=maintenanceReleasePending(run);
            boolean cleanupComplete="COMPLETE".equals(run.getCleanupState()) && !releasePending;
            boolean clinicalComplete=!run.isDryRun() && "COMPLETE".equals(run.getState());
            boolean validDryRun=run.isDryRun() && "DRY_RUN_COMPLETE".equals(run.getState());
            boolean finalAttempt=Set.of("COMPLETE", "DRY_RUN_COMPLETE", "DRY_RUN_INCOMPLETE", "INCOMPLETE", "RECONCILIATION_REQUIRED", "FAILED_BEFORE_WRITE", "CANCELLED_BEFORE_WRITE", "REVIEW_REQUIRED").contains(run.getState());
            value.put("maintenanceReleasePending", releasePending);
            value.put("maintenanceReleaseState", maintenanceReleaseState(run));
            value.put("reportState", run.getReportState()); value.put("cleanupState", releasePending?(maintenanceReleaseFailed(run)?"FAILED":"PENDING"):run.getCleanupState());
            value.put("reportsDelivered", delivered); value.put("cleanupComplete", cleanupComplete);
            value.put("reportDeliveryFailed", "FAILED".equals(run.getReportState()));
            value.put("clinicalComplete", clinicalComplete); value.put("cleanComplete", (clinicalComplete || validDryRun) && delivered && cleanupComplete);
            value.put("operationTerminal", finalAttempt && (delivered || "FAILED".equals(run.getReportState())) && (cleanupComplete || releasePending || "FAILED".equals(run.getCleanupState())));
            value.put("reviewVersion", run.getReviewVersion()); value.put("safeErrorCode", run.getSafeErrorCode());
            value.put("reportAvailable", delivered && run.getReportGeneration()>0 && run.getReportDigest()!=null);
            return java.util.Collections.unmodifiableMap(value);
        });
    }

    public void stage(String runId, String stage) {
        CdsImportKeys.token(stage, "stage", 48);
        if (!Set.of("DISCOVERING", "VALIDATING", "PREFLIGHT", "PREFLIGHTING", "PLANNING", "REVIEW_REQUIRED").contains(stage)) throw new IllegalArgumentException("Invalid preflight stage");
        write(() -> { CdsImportRun run=planning(runId); run.setState(stage); touch(run); event(run, null, stage); return null; });
    }

    public void stage(String runId, long epoch, String stage) {
        CdsImportKeys.token(stage, "stage", 48);
        Set<String> allowed=Set.of("DISCOVERING", "VALIDATING", "PREFLIGHT", "PREFLIGHTING", "PLANNING", "REVIEW_REQUIRED", "RUNNING", "RECONCILING", "PAUSED", "REPORTING");
        if (!allowed.contains(stage)) throw new IllegalArgumentException("Invalid import stage");
        write(() -> {
            CdsImportRun run=lockOperational(runId, epoch);
            if ("SEALING".equals(run.getState())) throw new CdsImportException("MANIFEST_SEAL_IN_PROGRESS");
        if ("REPLANNING".equals(run.getState())) throw new CdsImportException("REVIEW_RESET_IN_PROGRESS");
            if (run.getExecutionDigest()!=null && Set.of("DISCOVERING", "VALIDATING", "PREFLIGHT", "PREFLIGHTING", "PLANNING").contains(stage)) throw new CdsImportException("MANIFEST_ALREADY_SEALED");
            run.setState(stage); touch(run); event(run, null, stage); return null;
        });
    }

    public void failBeforeExecution(String runId, String safeCode) {
        CdsImportKeys.token(safeCode, "failure code", 96);
        write(() -> {
            CdsImportRun run=planning(runId);
            if (units.countState(runId, null, "COMMITTED")!=0 || units.countState(runId, null, "ALREADY_COMPLETE")!=0) throw new CdsImportException("COMMITTED_OUTCOME_REQUIRES_RECONCILIATION");
            run.setState("FAILED_BEFORE_WRITE"); touch(run);
            addDiagnostic(runId, null, null, "ERROR", safeCode, "PREFLIGHT", null, "Import could not complete preflight", null, true);
            event(run, null, "FAILED_BEFORE_WRITE"); return null;
        });
    }

    public CdsImportJobDto findSubmission(String operator, String submissionKey) {
        return write(() -> { CdsImportRun run=runs.findSubmission(operator, submissionKey); return run==null?null:dto(run); });
    }
    public CdsImportUnitDto unitStatus(String runId, String unitKey) { return write(() -> dto(unit(runId, unitKey))); }
    public CdsImportPatientDto patientStatus(String runId, String patientKey) { return write(() -> dto(patient(runId, patientKey))); }
    public List<CdsImportJobDto> pending(String afterId, int limit) {
        CdsImportKeys.pageSize(limit);
        return write(() -> {
            List<CdsImportJobDto> page=new ArrayList<>(runs.pending(afterId==null?"":afterId,limit).stream().map(CdsImportLedgerService::dto).toList());
            if(afterId==null || afterId.isEmpty()) {
                CdsImportAdmission gate=admission.get("global");
                if(gate!=null && gate.getOwnerRunId()!=null && page.stream().noneMatch(job -> gate.getOwnerRunId().equals(job.id()))) {
                    CdsImportRun owner=runs.get(gate.getOwnerRunId());
                    if(owner!=null && Set.of("ACCEPTED","CLI_ACCEPTED","RESUME_REQUESTED","DISCOVERING","VALIDATING","PREFLIGHT","PREFLIGHTING","PLANNING","SEALING","READY","RUNNING","FINALIZING").contains(owner.getState())) {
                        if(page.size()==limit) page.remove(page.size()-1);page.add(0,dto(owner));
                    }
                }
            }
            return List.copyOf(page);
        });
    }
    public List<CdsImportUnitDto> readyUnits(String runId, int limit) {
        CdsImportKeys.pageSize(limit);
        return write(() -> units.ready(runId, limit).stream().map(CdsImportLedgerService::dto).toList());
    }
    public long heldPatients(String runId) { return write(() -> patients.heldForRun(runId)); }
    public void inventoryDiscovered(String runId, long epoch, long files, long xmls, long bytes) {
        if (files<0 || xmls<0 || bytes<0) throw new IllegalArgumentException("Invalid source inventory");
        write(() -> { CdsImportRun run=lockSealingOwner(runId, epoch); setInventory(run, files, xmls, bytes); touch(run); return null; });
    }
    public void discovery(String runId, long epoch, long files, long xmls, long bytes) {
        if (files<0 || xmls<0 || bytes<0) throw new IllegalArgumentException("Invalid discovery counts");
        write(() -> { CdsImportRun run=lockOperational(runId, epoch); run.setDiscoveryComplete(true); setInventory(run, files, xmls, bytes); touch(run); return null; });
    }
    private static void setInventory(CdsImportRun run, long files, long xmls, long bytes) {
        if (run.getDiscoveredFiles()>=0 && (run.getDiscoveredFiles()!=files || run.getPatientXmlFiles()!=xmls || run.getSourceBytes()!=bytes)) throw new CdsImportException("SOURCE_FILE_INVENTORY_CHANGED");
        run.setDiscoveredFiles(files); run.setPatientXmlFiles(xmls); run.setSourceBytes(bytes);
    }
    public void operational(String runId, long epoch, String reportState, String cleanupState, String safeErrorCode) {
        if (reportState!=null && !Set.of("PENDING", "DELIVERED", "FAILED").contains(reportState)) throw new IllegalArgumentException("Invalid report state");
        if (cleanupState!=null && !Set.of("PENDING", "COMPLETE", "FAILED").contains(cleanupState)) throw new IllegalArgumentException("Invalid cleanup state");
        if (safeErrorCode!=null) CdsImportKeys.token(safeErrorCode, "error code", 96);
        write(() -> {
            CdsImportRun run=lockOperational(runId, epoch);
            if (reportState!=null) {
                if ("DELIVERED".equals(reportState)) throw new CdsImportException("REPORT_SELECTION_REQUIRED");
                run.setReportState(reportState);
            }
            if (cleanupState!=null) run.setCleanupState("COMPLETE".equals(cleanupState) && maintenanceReleasePending(run)?(maintenanceReleaseFailed(run)?"FAILED":"PENDING"):cleanupState);
            if (safeErrorCode!=null && run.getSafeErrorCode()==null) run.setSafeErrorCode(safeErrorCode);
            if ("COMPLETE".equals(cleanupState) && "DELIVERED".equals(run.getReportState()) && Set.of("IMPORT_REPORT_DELIVERY_FAILED", "IMPORT_CLEANUP_FAILED").contains(run.getSafeErrorCode()==null?"":run.getSafeErrorCode())) run.setSafeErrorCode(null);
            touch(run); event(run, null, "OPERATION_STATUS"); return null;
        });
    }

    public List<CdsImportJobDto> jobs(String operator, String afterId, int limit) {
        CdsImportKeys.pageSize(limit);
        return write(() -> runs.page(operator, afterId==null?"":afterId, limit).stream().map(CdsImportLedgerService::dto).toList());
    }
    public List<CdsImportPatientDto> patients(String runId, String afterPatientKey, int limit) {
        CdsImportKeys.pageSize(limit);
        return write(() -> patients.page(runId, afterPatientKey==null?"":afterPatientKey, limit).stream().map(CdsImportLedgerService::dto).toList());
    }
    public List<CdsImportUnitDto> units(String runId, String patientKey, String afterUnitKey, int limit) {
        CdsImportKeys.pageSize(limit);
        return write(() -> units.page(runId, patientKey, afterUnitKey==null?"":afterUnitKey, limit).stream().map(CdsImportLedgerService::dto).toList());
    }
    public CdsImportDiagnosticDto diagnosticStatus(String runId,long id) {
        if(id<1) throw new IllegalArgumentException("DIAGNOSTIC_ID_INVALID");
        return write(() -> {CdsImportDiagnostic d=diagnostics.get(id);if(d==null || !runId.equals(d.getRunId())) throw new CdsImportException("DIAGNOSTIC_NOT_FOUND");
            return new CdsImportDiagnosticDto(d.getId(),d.getPatientKey(),d.getUnitKey(),d.getSeverity(),d.getCode(),d.getStage(),d.getSourcePath(),d.getSafeSummary(),d.getProtectedDetailLocation(),d.isBlocking(),d.isResolved(),d.getCreatedAt());});
    }

    public List<CdsImportDiagnosticDto> diagnostics(String runId, long afterId, int limit) {
        CdsImportKeys.pageSize(limit);
        return write(() -> diagnostics.page(runId, afterId, limit).stream().map(d -> new CdsImportDiagnosticDto(d.getId(), d.getPatientKey(), d.getUnitKey(), d.getSeverity(), d.getCode(), d.getStage(), d.getSourcePath(), d.getSafeSummary(), d.getProtectedDetailLocation(), d.isBlocking(), d.isResolved(), d.getCreatedAt())).toList());
    }
    public Map<String, String> sourcePatientIds(String runId, Set<String> patientKeys) {
        if (patientKeys.size()>200) throw new IllegalArgumentException("Page size exceeds limit");
        return write(() -> { Map<String, String> result=new LinkedHashMap<>(); for (CdsImportPatient p:patients.keys(runId, patientKeys)) result.put(p.getPatientKey(), p.getSourcePatientId()); return Map.copyOf(result); });
    }

    public List<CdsImportEventDto> events(String runId, long afterId, int limit) {
        CdsImportKeys.pageSize(limit);
        return write(() -> events.page(runId, afterId, limit).stream().map(e -> new CdsImportEventDto(e.getId(), e.getUnitKey(), e.getEpoch(), e.getEventType(), e.getCreatedAt())).toList());
    }
    public boolean isHeld(int demographicNo) { return write(() -> patients.held(demographicNo)); }

    public void diagnostic(String runId, String patientKey, String unitKey, String severity, String code, String stage,
            String sourcePath, String safeSummary, String protectedDetailLocation, boolean blocking) {
        appendDiagnostic(runId, null, patientKey, unitKey, severity, code, stage, sourcePath, safeSummary, protectedDetailLocation, blocking);
    }

    public void diagnostic(String runId, long epoch, String patientKey, String unitKey, String severity, String code, String stage,
            String sourcePath, String safeSummary, String protectedDetailLocation, boolean blocking) {
        appendDiagnostic(runId, epoch, patientKey, unitKey, severity, code, stage, sourcePath, safeSummary, protectedDetailLocation, blocking);
    }

    private void appendDiagnostic(String runId, Long epoch, String patientKey, String unitKey, String severity, String code, String stage,
            String sourcePath, String safeSummary, String protectedDetailLocation, boolean blocking) {
        CdsImportKeys.token(severity, "severity", 16); CdsImportKeys.token(code, "diagnostic code", 96);
        CdsImportKeys.token(stage, "stage", 64); CdsImportKeys.required(safeSummary, "safe summary", 2048);
        CdsImportKeys.optional(sourcePath, "source path", 2048); CdsImportKeys.optional(protectedDetailLocation, "detail location", 1024);
        Runnable action=() -> {
            CdsImportRun run=epoch==null?requiredRun(runId, true):lockSealingOwner(runId, epoch);
            if (epoch==null && run.getExecutionDigest()!=null) throw new CdsImportException("DIAGNOSTIC_OWNER_REQUIRED");
            if (blocking && "COMPLETE".equals(run.getState())) throw new CdsImportException("PUBLISHED_RESULT_REQUIRES_REVIEW_TRANSITION");
            if (blocking && patientKey!=null && COMPLETE_PATIENT.contains(patient(runId, patientKey).getState())) throw new CdsImportException("PUBLISHED_PATIENT_REQUIRES_REVIEW_TRANSITION");
            addDiagnostic(runId, patientKey, unitKey, severity, code, stage, sourcePath, safeSummary, protectedDetailLocation, blocking);
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) action.run();
        else write(() -> { action.run(); return null; });
    }

    private void addDiagnostic(String runId, String patientKey, String unitKey, String severity, String code, String stage,
            String sourcePath, String safeSummary, String protectedDetailLocation, boolean blocking) {
        requiredRun(runId, false);
        CdsImportDiagnostic diagnostic=new CdsImportDiagnostic(); diagnostic.setRunId(runId); diagnostic.setPatientKey(patientKey);
        diagnostic.setUnitKey(unitKey); diagnostic.setSeverity(severity); diagnostic.setCode(code); diagnostic.setStage(stage);
        diagnostic.setSourcePath(sourcePath); diagnostic.setSafeSummary(safeSummary); diagnostic.setProtectedDetailLocation(protectedDetailLocation);
        diagnostic.setBlocking(blocking); diagnostic.setCreatedAt(Instant.now()); diagnostics.persist(diagnostic);
    }

    public void resolveDiagnostic(String runId, long epoch, long diagnosticId) {
        write(() -> {
            lockOperational(runId, epoch); CdsImportDiagnostic item=diagnostics.lock(diagnosticId);
            if (item==null || !item.getRunId().equals(runId)) throw new CdsImportException("DIAGNOSTIC_NOT_FOUND");
            if (item.isBlocking()) throw new CdsImportException("BLOCKING_DIAGNOSTIC_REQUIRES_VERIFIED_CORRECTION");
            if (Set.of(MAINTENANCE_RELEASE_CODE, MAINTENANCE_PENDING_CODE, MAINTENANCE_DEFERRED_CODE).contains(item.getCode())) throw new CdsImportException("MAINTENANCE_RELEASE_PROOF_REQUIRED");
            item.setResolved(true); return null;
        });
    }

    /** A failed close belongs to this owner; other runs' retained holds alone do not create this outcome. */
    public void maintenanceReleaseFailed(String runId, long epoch, String protectedEvidence) {
        write(() -> { CdsImportRun run=lockOperational(runId, epoch);
            if (!Set.of("COMPLETE", "DRY_RUN_COMPLETE").contains(run.getState())) throw new CdsImportException("CLINICAL_COMPLETION_REQUIRED");
            if (!maintenanceReleaseFailed(run)) addDiagnostic(runId, null, null, "ERROR", MAINTENANCE_RELEASE_CODE, "MAINTENANCE", null,
                    "Clinical processing completed, but reopening clinical access requires verified maintenance recovery", protectedEvidence, false);
            run.setCleanupState("FAILED"); run.setReportState("PENDING");
            if (run.getSafeErrorCode()==null) run.setSafeErrorCode(MAINTENANCE_RELEASE_CODE);
            touch(run); event(run, null, MAINTENANCE_RELEASE_CODE); return null; });
    }

    public boolean maintenanceReleasePending(String runId) { return write(() -> maintenanceReleasePending(requiredRun(runId, false))); }
    private void awaitMaintenanceRelease(CdsImportRun run) {
        if (!maintenanceReleasePending(run)) addDiagnostic(run.getId(), null, null, "INFO", MAINTENANCE_PENDING_CODE, "MAINTENANCE", null,
                "Clinical processing completed; the maintenance owner's durable release still requires verification", null, false);
        run.setCleanupState("PENDING"); run.setReportState("PENDING"); event(run, null, "MAINTENANCE_RELEASE_PENDING");
    }
    private boolean maintenanceReleasePending(CdsImportRun run) {
        String state=maintenanceReleaseState(run); return !"NOT_APPLICABLE".equals(state) && !"RELEASED".equals(state);
    }
    private boolean maintenanceReleaseFailed(CdsImportRun run) {
        return !diagnostics.unresolvedOperationalCode(run.getId(), MAINTENANCE_RELEASE_CODE, 1).isEmpty();
    }
    private String maintenanceReleaseState(CdsImportRun run) {
        if (!Set.of("COMPLETE", "DRY_RUN_COMPLETE").contains(run.getState())) return "NOT_APPLICABLE";
        if (maintenanceReleaseFailed(run)) return "REQUIRES_RECONCILIATION";
        if (!diagnostics.unresolvedOperationalCode(run.getId(), MAINTENANCE_DEFERRED_CODE, 1).isEmpty()) return "RELEASE_DEFERRED";
        return diagnostics.unresolvedOperationalCode(run.getId(), MAINTENANCE_PENDING_CODE, 1).isEmpty()?"RELEASED":"RELEASE_PENDING";
    }

    void maintenanceReleaseDeferred(String runId, long epoch, String reason) {
        if (!Set.of("GLOBAL_HOLDS_REMAIN", "OWNER_BUSY", "REQUIRES_RECONCILIATION").contains(reason)) throw new IllegalArgumentException("Invalid release outcome");
        write(() -> { CdsImportRun run=lockOperational(runId, epoch);
            if (!maintenanceReleasePending(run)) throw new CdsImportException("MAINTENANCE_RELEASE_NOT_PENDING");
            if (diagnostics.unresolvedOperationalCode(runId, MAINTENANCE_DEFERRED_CODE, 1).isEmpty()) addDiagnostic(runId, null, null, "INFO", MAINTENANCE_DEFERRED_CODE, "MAINTENANCE", null,
                    "Clinical processing completed; operational release is deferred: "+reason, null, false);
            run.setCleanupState(maintenanceReleaseFailed(run)?"FAILED":"PENDING"); run.setReportState("PENDING"); touch(run);
            event(run, null, "MAINTENANCE_RELEASE_"+reason); return null; });
    }

    /** Called only after the maintenance service has read back this owner's durable inactive release under its advisory lock. */
    void maintenanceReleaseReconciled(String runId, long epoch) {
        while (write(() -> { CdsImportRun run=lockOperational(runId, epoch);
            if (!Set.of("COMPLETE", "DRY_RUN_COMPLETE").contains(run.getState())) throw new CdsImportException("CLINICAL_COMPLETION_REQUIRED");
            List<CdsImportDiagnostic> page=new ArrayList<>();
            for (String code:List.of(MAINTENANCE_RELEASE_CODE, MAINTENANCE_PENDING_CODE, MAINTENANCE_DEFERRED_CODE)) {
                if (page.size()<200) page.addAll(diagnostics.unresolvedOperationalCode(runId, code, 200-page.size()));
            }
            for (CdsImportDiagnostic diagnostic:page) diagnostic.setResolved(true);
            if (MAINTENANCE_RELEASE_CODE.equals(run.getSafeErrorCode())) run.setSafeErrorCode(null);
            run.setCleanupState("PENDING"); run.setReportState("PENDING"); touch(run);
            event(run, null, "MAINTENANCE_RELEASE_VERIFIED"); return !page.isEmpty(); })) { /* Normally one row; retain bounded recovery even after repeated historical owner failures. */ }
    }

    /** Authorization loss stops future units, preserves every chart hold and records truthful administrative outcomes. */
    public void authorizationStopped(String runId, long epoch) { stoppedOperationally(runId, epoch, "ACCESS_REVOKED", "ACCESS", "Import authorization was revoked; retained work requires authorized review"); }
    public void workerStopped(String runId, long epoch) { stoppedOperationally(runId, epoch, "WORKER_FATAL", "SUPERVISION", "Import worker terminated; durable receipts and retained work require reconciliation"); }
    private void stoppedOperationally(String runId, long epoch, String code, String stage, String summary) {
        boolean published=write(() -> { CdsImportRun run=lockOperational(runId, epoch); return Set.of("COMPLETE", "DRY_RUN_COMPLETE").contains(run.getState()); });
        if (published) { write(() -> { CdsImportRun run=lockOperational(runId, epoch); event(run, null, code); return null; }); return; }
        String after="";
        while (true) {
            final String cursor=after;
            List<String> page=write(() -> { CdsImportRun run=lockOperational(runId, epoch); List<CdsImportPatient> values=patients.page(runId, cursor, 200);
                for (CdsImportPatient p:values) if (!COMPLETE_PATIENT.contains(p.getState()) && !Set.of("REFUSED","RECONCILIATION_REQUIRED").contains(p.getState())) p.setState(p.isHeld()?"PARTIAL_COMMITTED":"NOT_STARTED");
                touch(run); return values.stream().map(CdsImportPatient::getPatientKey).toList(); });
            if (page.isEmpty()) break; after=page.get(page.size()-1);
        }
        write(() -> { CdsImportAdmission gate=admission.lock("global"); CdsImportRun run=lockOperational(runId, epoch);
            boolean held=patients.heldForRun(runId)>0; long committed=units.countState(runId, null, "COMMITTED")+units.countState(runId, null, "ALREADY_COMPLETE");
            boolean uncertain=patients.outcomes(runId,17).stream().anyMatch(row -> "RECONCILIATION_REQUIRED".equals(row[0]));
            run.setState(held || uncertain?"RECONCILIATION_REQUIRED":committed>0?"INCOMPLETE":"FAILED_BEFORE_WRITE"); run.setSafeErrorCode(code); touch(run);
            addDiagnostic(runId, null, null, "ERROR", code, Set.of("SUPERVISION","PREPARATION").contains(stage) && run.getExecutionDigest()==null?"PREFLIGHT_STOP":stage, null, summary, null, true);
            event(run, null, code); if (gate!=null && runId.equals(gate.getOwnerRunId())) gate.setOwnerRunId(null); return null; });
    }

    @FunctionalInterface public interface ControlWork { void apply() throws Exception; }
    private record ControlContext(String runId, String operation) { }

    /** Current initiator audit and the requested metadata transition share the same real owning transaction. */
    public void control(String runId, io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto initiator, String operation, ControlWork work) {
        requireNoTransaction(); Objects.requireNonNull(initiator, "initiator"); Objects.requireNonNull(work, "work");
        if (!Set.of("CANCEL_REQUESTED", "RESUME_REQUESTED").contains(operation)) throw new IllegalArgumentException("Invalid control operation");
        java.util.concurrent.atomic.AtomicBoolean applied=new java.util.concurrent.atomic.AtomicBoolean();
        try {
            transaction.executeWithoutResult(status -> {
                CdsImportRun run=requiredRun(runId, true);
                TransactionSynchronizationManager.bindResource(ControlContext.class, new ControlContext(runId, operation));
                try { work.apply(); actorEvent(run, initiator, operation); runs.flushAndClear(); applied.set(true); }
                catch (CdsImportException known) { throw known; }
                catch (Exception failure) { throw new CdsImportException("CONTROL_WRITE_FAILED", false, failure); }
                finally { TransactionSynchronizationManager.unbindResource(ControlContext.class); }
            });
        } catch (RuntimeException failure) {
            if (applied.get()) throw new CdsImportException("CONTROL_COMMIT_OUTCOME_UNKNOWN", true, failure);
            throw failure;
        }
    }
    private CdsImportRun requireControl(String runId, String operation) {
        Object value=TransactionSynchronizationManager.getResource(ControlContext.class);
        if (!(value instanceof ControlContext context) || !runId.equals(context.runId()) || !operation.equals(context.operation())) throw new CdsImportException("OWNING_CONTROL_REQUIRED");
        return requiredRun(runId, true);
    }
    public void requestResumeParticipating(String runId) { applyResume(requireControl(runId, "RESUME_REQUESTED")); }
    public void cancelParticipating(String runId) { applyCancel(requireControl(runId, "CANCEL_REQUESTED")); }

    /** Persist operator intent before enqueueing; a later cancel always remains authoritative. */
    public void requestResume(String runId) { write(() -> { CdsImportRun run=requiredRun(runId, true); applyResume(run); event(run, null, "RESUME_REQUESTED"); return null; }); }
    private void applyResume(CdsImportRun run) {
        if (Set.of("RUNNING", "FINALIZING", "DISCOVERING", "VALIDATING", "PREFLIGHT", "PREFLIGHTING", "SEALING").contains(run.getState())) throw new CdsImportException("IMPORT_ALREADY_RUNNING");
        if ("REPLANNING".equals(run.getState())) throw new CdsImportException("REVIEW_RESET_IN_PROGRESS");
        run.setCancelRequested(false); run.setReportState("PENDING"); run.setCleanupState("PENDING");
        if (!Set.of("COMPLETE", "DRY_RUN_COMPLETE").contains(run.getState())) run.setState("RESUME_REQUESTED"); touch(run);
    }

    /** A completed review is prior consent; it must never erase a newer cancellation. */
    public boolean requestResumeAfterReview(String runId) {
        return write(() -> { CdsImportRun run=requiredRun(runId, true);
            if (run.isCancelRequested() || run.getExecutionDigest()!=null || run.getManifestDigest()!=null || !"PLANNING".equals(run.getState())) return false;
            run.setState("RESUME_REQUESTED"); run.setReportState("PENDING"); run.setCleanupState("PENDING"); touch(run); event(run, null, "REVIEW_RESUME_REQUESTED"); return true;
        });
    }

    public void cancel(String runId) { write(() -> { CdsImportRun run=requiredRun(runId, true); applyCancel(run); event(run, null, "CANCEL_REQUESTED"); return null; }); }
    private void applyCancel(CdsImportRun run) {
        if (Set.of("COMPLETE", "DRY_RUN_COMPLETE").contains(run.getState())) return;
        run.setCancelRequested(true); run.setReportState("PENDING"); run.setCleanupState("PENDING");
        if ("RESUME_REQUESTED".equals(run.getState()) || Set.of("REVIEW_REQUIRED", "FAILED_BEFORE_WRITE", "CANCELLED_BEFORE_WRITE").contains(run.getState())) run.setState(run.getExecutionDigest()==null?"CANCELLED_BEFORE_WRITE":"INCOMPLETE");
        touch(run);
    }

    /** Explicit accounted preflight disposition; never turns an unstarted patient into clean completion. */
    public void refusePlannedPatient(String runId, long epoch, String patientKey, String outcome) {
        if (!Set.of("REFUSED", "FAILED_BEFORE_WRITE", "CANCELLED_BEFORE_WRITE", "NOT_STARTED").contains(outcome)) throw new IllegalArgumentException("Invalid preflight patient outcome");
        write(() -> {
            CdsImportRun run=lockOperational(runId, epoch);
            if (run.getExecutionDigest()!=null || "SEALING".equals(run.getState())) throw new CdsImportException("MANIFEST_ALREADY_SEALED");
            CdsImportPatient patient=patient(runId, patientKey); patient=patients.lock(patient.getId());
            if (patient.isHeld() || units.countState(runId, patientKey, "COMMITTED")!=0 || units.countState(runId, patientKey, "ALREADY_COMPLETE")!=0) throw new CdsImportException("COMMITTED_DATA_CANNOT_BE_REFUSED");
            patient.setState(outcome); touch(run); event(run, null, "PATIENT_"+outcome); return null;
        });
    }

    public void markPatient(String runId, long epoch, String patientKey, String outcome) {
        if (!FINAL_PATIENT.contains(outcome) || COMPLETE_PATIENT.contains(outcome)) throw new IllegalArgumentException("Invalid incomplete outcome");
        write(() -> {
            CdsImportRun run=lockOwned(runId, epoch); CdsImportPatient patient=patient(runId, patientKey); patient=patients.lock(patient.getId());
            long committed=patient.getExpectedUnits()-units.incomplete(runId, patientKey);
            if (committed>0 && Set.of("REFUSED", "FAILED_BEFORE_WRITE", "CANCELLED_BEFORE_WRITE", "NOT_STARTED").contains(outcome)) throw new CdsImportException("COMMITTED_DATA_CANNOT_BE_REFUSED");
            patient.setState(outcome); touch(run); event(run, null, "PATIENT_"+outcome); return null;
        });
    }

    /** Fenced paged verification, followed by one atomic clinical completion/hold-release transition. */
    public void finalizePatient(String runId, long epoch, String patientKey, CdsImportUnitService.Verifier verifier) {
        Objects.requireNonNull(verifier, "verifier");
        PatientSeal expected=write(() -> {
            lockOwned(runId, epoch); CdsImportPatient patient=patient(runId, patientKey); patient=patients.lock(patient.getId());
            if (COMPLETE_PATIENT.contains(patient.getState())) return null;
            if (patient.getExecutionDigest()==null || patient.getExpectedUnits()<1 || units.incomplete(runId, patientKey)!=0
                    || diagnostics.unresolved(runId, patientKey)!=0) throw new CdsImportException("PATIENT_INCOMPLETE");
            patient.setState("FINALIZING"); return sealOf(patient);
        });
        if (expected==null) return;
        if (!expected.digest().equals(patientDigest(runId, patientKey))) throw new CdsImportException("SEALED_WORKSET_CHANGED");
        verifyReceipts(runId, epoch, patientKey, verifier);
        write(() -> {
            CdsImportRun run=lockOwned(runId, epoch); CdsImportPatient patient=patient(runId, patientKey); patient=patients.lock(patient.getId());
            if (!"FINALIZING".equals(patient.getState()) || !expected.digest().equals(patient.getExecutionDigest())
                    || expected.count()!=units.count(runId, patientKey) || units.incomplete(runId, patientKey)!=0
                    || diagnostics.unresolved(runId, patientKey)!=0) throw new CdsImportException("PATIENT_FINALIZATION_CONFLICT");
            patient.setState(units.countState(runId, patientKey, "COMMITTED")==0?"ALREADY_COMPLETE":(diagnostics.warnings(runId, patientKey)>0?"COMPLETE_WITH_NONLOSSY_WARNINGS":"COMPLETE"));
            patient.setHeld(false); touch(run); event(run, null, "PATIENT_COMPLETE"); return null;
        });
    }

    public CdsImportJobDto finish(String runId, long epoch) {
        String actualExecutionDigest=executionDigest(runId);
        return write(() -> {
            CdsImportRun run=lockOwned(runId, epoch);
            if (run.getExpectedPatients()!=patients.count(runId) || run.getExpectedUnits()!=units.count(runId, null)
                    || !run.getExecutionDigest().equals(actualExecutionDigest)) throw new CdsImportException("SEALED_WORKSET_CHANGED");
            run=runs.lock(runId);
            boolean complete=true; boolean uncertain=false;
            List<Object[]> outcomeRows=patients.outcomes(runId, 17);
            if (outcomeRows.size()>16) throw new CdsImportException("UNEXPECTED_OUTCOME_CARDINALITY");
            for (Object[] row:outcomeRows) {
                String outcome=(String) row[0];
                if (!FINAL_PATIENT.contains(outcome)) throw new CdsImportException("UNACCOUNTED_PATIENT");
                complete &= COMPLETE_PATIENT.contains(outcome); uncertain |= "RECONCILIATION_REQUIRED".equals(outcome);
            }
            run.setState(complete?"COMPLETE":(uncertain?"RECONCILIATION_REQUIRED":"INCOMPLETE"));
            if (complete) { run.setSafeErrorCode(null); awaitMaintenanceRelease(run); }
            touch(run); event(run, null, "RUN_"+run.getState()); CdsImportJobDto result=dto(run);
            admission.lock("global").setOwnerRunId(null); return result;
        });
    }

    /** Participant guard for rebuilding an identical staging decision, never a clinical checkpoint. */
    public void requireExpectedCheckpointFree(String runId,long epoch,String unitKey) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new CdsImportException("PREPARATION_REPAIR_REQUIRES_TRANSACTION");
        lockOwned(runId,epoch);CdsImportUnit candidate=unit(runId,unitKey);CdsImportUnit locked=units.lock(candidate.getId());
        if(!"EXPECTED".equals(locked.getState()) || checkpoints.lock(unitKey)!=null) throw new CdsImportException("COMMITTED_PREPARATION_REPAIR_REFUSED");
    }

    CdsImportRun lockOwned(String runId, long epoch) {
        CdsImportAdmission gate=admission.lock("global");
        if (gate==null || !Objects.equals(gate.getOwnerRunId(), runId)) throw new CdsImportException("OWNER_FENCED");
        CdsImportRun run=requiredRun(runId, true);
        if (run.getOwnerEpoch()!=epoch || run.getExecutionDigest()==null) throw new CdsImportException("OWNER_FENCED");
        return run;
    }
    // Operational report/cleanup ownership survives release of scarce clinical admission.
    CdsImportRun lockOperational(String runId, long epoch) {
        CdsImportRun run=requiredRun(runId, true);
        if (run.getOwnerEpoch()!=epoch) throw new CdsImportException("OWNER_FENCED");
        return run;
    }

    CdsImportUnit unit(String runId, String unitKey) {
        CdsImportUnit value=units.findKey(runId, unitKey);
        if (value==null) throw new CdsImportException("EXPECTED_UNIT_NOT_FOUND");
        return value;
    }
    CdsImportPatient patient(String runId, String patientKey) {
        CdsImportPatient value=patients.findKey(runId, patientKey);
        if (value==null) throw new CdsImportException("PATIENT_NOT_FOUND");
        return value;
    }
    private CdsImportRun planning(String runId) {
        CdsImportRun run=requiredRun(runId, true);
        if (run.getExecutionDigest()!=null) throw new CdsImportException("MANIFEST_ALREADY_SEALED");
        if (run.getManifestDigest()!=null || "SEALING".equals(run.getState())) throw new CdsImportException("MANIFEST_SEAL_IN_PROGRESS");
        if ("REPLANNING".equals(run.getState())) throw new CdsImportException("REVIEW_RESET_IN_PROGRESS");
        return run;
    }
    private CdsImportRun requiredRun(String runId, boolean locked) {
        CdsImportKeys.required(runId, "run id", 36);
        CdsImportRun run=locked?runs.lock(runId):runs.get(runId);
        if (run==null) throw new CdsImportException("RUN_NOT_FOUND");
        return run;
    }
    void actorEvent(CdsImportRun run, io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto actor, String type) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CdsImportException("CONTROL_AUDIT_REQUIRES_TRANSACTION");
        CdsImportKeys.required(actor.operator(), "current operator", 128); CdsImportKeys.token(actor.providerNo(), "current provider", 16);
        Map<String, Object> context=new LinkedHashMap<>(); context.put("initiatorSecurityNo", actor.securityNo()); context.put("initiatorProviderNo", actor.operatorProviderNo());
        context.put("resultingState", run.getState()); context.put("cancelRequested", run.isCancelRequested()); context.put("selectedProviderNo", run.getProviderNo()); context.put("originalJobOperator", run.getOperator()); context.put("facilityId", actor.facilityId()); context.put("programId", actor.programId());
        CdsImportEvent item=new CdsImportEvent(); item.setRunId(run.getId()); item.setEpoch(run.getOwnerEpoch()); item.setEventType(type);
        item.setOperator(actor.operator()); item.setProviderNo(actor.providerNo()); item.setCreatedAt(Instant.now());
        try {String encoded=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(context); if (encoded.length()>1024) throw new CdsImportException("CONTROL_AUDIT_CAPACITY_EXCEEDED"); item.setObservationContext(encoded); }
        catch (java.io.IOException failure){throw new CdsImportException("CONTROL_AUDIT_ENCODING_FAILED", false, failure); }
        events.persist(item);
    }

    void event(CdsImportRun run, String unitKey, String type) {
        CdsImportEvent event=new CdsImportEvent(); event.setRunId(run.getId()); event.setUnitKey(unitKey);
        event.setEpoch(run.getOwnerEpoch()); event.setEventType(type); event.setOperator(run.getOperator());
        event.setProviderNo(run.getProviderNo()); event.setCreatedAt(Instant.now()); events.persist(event);
    }
    void touch(CdsImportRun run) { run.setUpdatedAt(Instant.now()); }

    private void verifyReceipts(String runId, long epoch, String patientKey, CdsImportUnitService.Verifier verifier) {
        String after="";
        while (true) {
            String cursor=after;
            List<String> verified=write(() -> {
                lockOwned(runId, epoch);
                CdsImportPatient patient=patient(runId, patientKey);
                if (!"FINALIZING".equals(patient.getState())) throw new CdsImportException("PATIENT_FINALIZATION_CONFLICT");
                List<CdsImportUnit> page=units.page(runId, patientKey, cursor, 1);
                List<String> keys=new ArrayList<>();
                for (CdsImportUnit unit:page) {
                    if (dependencies.incomplete(runId, unit.getUnitKey())!=0) throw new CdsImportException("DEPENDENCY_INCOMPLETE", false, null, unit.getUnitKey());
                    CdsImportCheckpoint checkpoint=checkpoints.get(unit.getUnitKey());
                    if (checkpoint==null || !checkpoint.getSourceDigest().equals(unit.getSourceDigest())
                            || !Objects.equals(checkpoint.getTargetDigest(), unit.getTargetDigest())
                            || !Objects.equals(checkpoint.getTargetReferences(), unit.getTargetReferences())) throw new CdsImportException("CHECKPOINT_RECONCILIATION_REQUIRED", false, null, unit.getUnitKey());
                    try {
                        if (!verifier.verify(new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportReceiptDto(unit.getTargetReferences(), unit.getTargetDigest(), true))) throw new CdsImportException("TARGET_RECONCILIATION_REQUIRED");
                    } catch (CdsImportException failure) { throw new CdsImportException(failure.getCode(), failure.isOutcomeUnknown(), failure, unit.getUnitKey()); }
                    catch (Exception failure) { throw new CdsImportException("TARGET_VERIFICATION_FAILED", false, failure, unit.getUnitKey()); }
                    keys.add(unit.getUnitKey());
                }
                units.flushAndClear(); return List.copyOf(keys);
            });
            if (verified.isEmpty()) break;
            after=verified.get(verified.size()-1);
        }
    }

    private record UnitSeal(String key, String lineage, String source, String category, long ordinal, String spool, List<String> dependencies) { }
    private record PatientSeal(String id, String key, String lineage, String source, String digest, long count) { }

    private PatientSeal sealOf(CdsImportPatient patient) {
        return new PatientSeal(patient.getId(), patient.getPatientKey(), patient.getLineageKey(), patient.getSourceDigest(), patient.getExecutionDigest(), patient.getExpectedUnits());
    }

    private List<PatientSeal> patientSeals(String runId, String after) {
        return write(() -> {
            List<PatientSeal> page=patients.page(runId, after, 200).stream().map(this::sealOf).toList();
            patients.flushAndClear(); return page;
        });
    }

    private String patientDigest(String runId, String patientKey) {
        requireNoTransaction(); MessageDigest digest=CdsImportKeys.digest(); String after="";
        while (true) {
            String cursor=after;
            List<UnitSeal> page=write(() -> {
                List<CdsImportUnit> source=units.page(runId, patientKey, cursor, 200);
                Map<String, List<String>> byUnit=new LinkedHashMap<>();
                List<CdsImportDependency> linked=dependencies.forUnits(runId, source.stream().map(CdsImportUnit::getUnitKey).toList());
                if (linked.size()>1600) throw new CdsImportException("UNIT_DEPENDENCY_LIMIT");
                for (CdsImportDependency d:linked) { List<String> list=byUnit.computeIfAbsent(d.getUnitKey(), key -> new ArrayList<>()); list.add(d.getDependsOnUnitKey()); if (list.size()>8) throw new CdsImportException("UNIT_DEPENDENCY_LIMIT"); }
                List<UnitSeal> values=source.stream().map(u -> new UnitSeal(u.getUnitKey(), u.getLineageKey(), u.getSourceDigest(), u.getCategory(), u.getOrdinal(), u.getSpoolLocation(), List.copyOf(byUnit.getOrDefault(u.getUnitKey(), List.of())))).toList();
                units.flushAndClear(); return values;
            });
            if (page.isEmpty()) break;
            for (UnitSeal unit:page) {
                CdsImportKeys.add(digest, unit.key()); CdsImportKeys.add(digest, unit.lineage());
                CdsImportKeys.add(digest, unit.source()); CdsImportKeys.add(digest, unit.category());
                CdsImportKeys.add(digest, Long.toString(unit.ordinal())); CdsImportKeys.add(digest, unit.spool());
                for (String dependency:unit.dependencies()) CdsImportKeys.add(digest, dependency);
                CdsImportKeys.add(digest, "END_UNIT"); after=unit.key();
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String executionDigest(String runId) {
        requireNoTransaction(); MessageDigest digest=CdsImportKeys.digest(); String after="";
        while (true) {
            List<PatientSeal> page=patientSeals(runId, after); if (page.isEmpty()) break;
            for (PatientSeal patient:page) {
                CdsImportKeys.add(digest, patient.key()); CdsImportKeys.add(digest, patient.lineage()); CdsImportKeys.add(digest, patient.source());
                CdsImportKeys.add(digest, Objects.requireNonNull(patient.digest())); CdsImportKeys.add(digest, Long.toString(patient.count()));
                after=patient.key();
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static int dependencyRank(String category) {
        if ("Demographics".equals(category)) return 0;
        return isHelperCategory(category)?2:1;
    }

    static boolean isHelperCategory(String category) {
        return category.endsWith("_FIELDS") || category.endsWith("_CONTACT") || category.endsWith("_CARE");
    }
    static String clinicalCategory(String category) {
        for (String suffix:List.of("_FIELDS", "_CONTACT", "_CARE")) if (category.endsWith(suffix)) return category.substring(0, category.length()-suffix.length());
        return category;
    }

    static CdsImportJobDto dto(CdsImportRun r) {
        return new CdsImportJobDto(r.getId(), r.getOperator(), r.getProviderNo(), r.getSourceNamespace(), r.getProfile(), r.getState(), r.getSourceDigest(), r.getOptionsDigest(), r.getRulesetDigest(), r.getManifestDigest(), r.getExpectedPatients(), r.getExpectedUnits(), r.getOwnerEpoch(), r.isCancelRequested(), r.getCreatedAt(), r.getUpdatedAt(), r.getReportGeneration(), r.getReportLocation(), r.getReportDigest());
    }
    static CdsImportPatientDto dto(CdsImportPatient p) {
        return new CdsImportPatientDto(p.getPatientKey(), p.getSourcePatientId(), p.getCopyKey(), p.getLineageKey(), p.getTargetDemographicNo(), p.getState(), p.isHeld(), p.getExpectedUnits(), p.getSourceDigest(), p.getExecutionDigest());
    }
    static CdsImportUnitDto dto(CdsImportUnit u) {
        return new CdsImportUnitDto(u.getPatientKey(), u.getUnitKey(), u.getCategory(), u.getOrdinal(), u.getSourceDigest(), u.getSpoolLocation(), u.getState(), u.getTargetReferences(), u.getTargetDigest());
    }
}
