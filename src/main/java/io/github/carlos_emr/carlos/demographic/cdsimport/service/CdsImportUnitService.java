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

import io.github.carlos_emr.carlos.commn.dao.CdsImportCheckpointDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportDependencyDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportPatientDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportUnitDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportCheckpoint;
import io.github.carlos_emr.carlos.commn.model.CdsImportPatient;
import io.github.carlos_emr.carlos.commn.model.CdsImportRun;
import io.github.carlos_emr.carlos.commn.model.CdsImportUnit;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportReceiptDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Owns one bounded READ_COMMITTED transaction, including native writes, provenance and strict audit. */
@Service
public class CdsImportUnitService {
    @FunctionalInterface public interface Work { CdsImportReceiptDto write() throws Exception; }
    @FunctionalInterface public interface Verifier { boolean verify(CdsImportReceiptDto previous) throws Exception; }

    private record ActiveUnit(String runId, long epoch, String unitKey) { }
    static String requireActive(String runId, long epoch, String unitKey) {
        Object resource=TransactionSynchronizationManager.getResource(CdsImportUnitService.class);
        if (!(resource instanceof ActiveUnit active) || !active.runId().equals(runId) || active.epoch()!=epoch
                || (unitKey!=null && !active.unitKey().equals(unitKey)) || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CdsImportException("PARTICIPATING_UNIT_REQUIRED");
        }
        return active.unitKey();
    }

    public static boolean isActiveForMaintenance() {
        return TransactionSynchronizationManager.getResource(CdsImportUnitService.class) instanceof ActiveUnit
                && TransactionSynchronizationManager.isActualTransactionActive();
    }

    private final CdsImportLedgerService ledger;
    private final CdsImportUnitDao units;
    private final CdsImportPatientDao patients;
    private final CdsImportDependencyDao dependencies;
    private final CdsImportCheckpointDao checkpoints;
    private final TransactionTemplate transaction;

    public CdsImportUnitService(CdsImportLedgerService ledger, CdsImportUnitDao units, CdsImportPatientDao patients,
            CdsImportDependencyDao dependencies, CdsImportCheckpointDao checkpoints, PlatformTransactionManager manager) {
        this.ledger=ledger; this.units=units; this.patients=patients; this.dependencies=dependencies;
        this.checkpoints=checkpoints; transaction=CdsImportLedgerService.template(manager);
    }

    /**
     * Work and verifier run inside the actual owning transaction. A returned receipt is a commit acknowledgement.
     * A thrown uncertain-outcome exception requires fenced ledger reconciliation, never whole-unit blind retry.
     */
    public CdsImportReceiptDto execute(String runId, long epoch, String unitKey, Work work, Verifier verifier) {
        Objects.requireNonNull(work, "work"); Objects.requireNonNull(verifier, "verifier");
        CdsImportLedgerService.requireNoTransaction();
        AtomicBoolean callbackFinished=new AtomicBoolean();
        try {
            return transaction.execute(status -> {
                CdsImportRun run=ledger.lockOwned(runId, epoch);
                if (run.isDryRun()) throw new CdsImportException("DRY_RUN_CLINICAL_WRITE_REFUSED");
                if (run.isCancelRequested()) throw new CdsImportException("IMPORT_CANCELLED");
                CdsImportUnit expected=ledger.unit(runId, unitKey);
                CdsImportUnit unit=units.lock(expected.getId());
                CdsImportPatient patient=ledger.patient(runId, unit.getPatientKey());
                ledger.verifyCanonical(run, patient);
                if ((CdsImportLedgerService.FINAL_PATIENT.contains(patient.getState()) && !java.util.Set.of("PARTIAL_COMMITTED", "RECONCILIATION_REQUIRED").contains(patient.getState())) || "FINALIZING".equals(patient.getState())) throw new CdsImportException("PATIENT_NOT_EXECUTABLE");
                if (dependencies.incomplete(runId, unitKey)>0) throw new CdsImportException("DEPENDENCY_INCOMPLETE");
                CdsImportCheckpoint checkpoint=checkpoints.lock(unitKey);
                TransactionSynchronizationManager.bindResource(CdsImportUnitService.class, new ActiveUnit(runId, epoch, unitKey));
                try {
                    CdsImportReceiptDto result;
                    if (checkpoint!=null) {
                        verifyIdentity(run, unit, checkpoint);
                        result=new CdsImportReceiptDto(checkpoint.getTargetReferences(), checkpoint.getTargetDigest(), true);
                        if (!verifier.verify(result)) throw new CdsImportException("TARGET_RECONCILIATION_REQUIRED");
                        // Reused native rows still need this attempt's hold until every expected unit reconciles.
                        if (patient.getTargetDemographicNo()==null) throw new CdsImportException("CHECKPOINT_TARGET_MISSING");
                        ledger.bindTarget(runId, epoch, unit.getPatientKey(), patient.getTargetDemographicNo());
                        if (!runId.equals(checkpoint.getFirstRunId())) unit.setState("ALREADY_COMPLETE");
                        else if (!"COMMITTED".equals(unit.getState())) throw new CdsImportException("CHECKPOINT_RECONCILIATION_REQUIRED");
                    } else {
                        if (!"EXPECTED".equals(unit.getState())) throw new CdsImportException("CHECKPOINT_RECONCILIATION_REQUIRED");
                        result=Objects.requireNonNull(work.write(), "unit receipt");
                        validate(result);
                        if (result.alreadyComplete()) throw new CdsImportException("UNVERIFIED_NOOP_REFUSED");
                        CdsImportPatient bound=patients.get(patient.getId());
                        if (bound==null || bound.getTargetDemographicNo()==null || !bound.isHeld()) throw new CdsImportException("UNIT_TARGET_HOLD_MISSING");
                        checkpoint=new CdsImportCheckpoint(); checkpoint.setId(unitKey); checkpoint.setLineageKey(unit.getLineageKey());
                        checkpoint.setSourceDigest(unit.getSourceDigest()); checkpoint.setOptionsDigest(run.getOptionsDigest());
                        checkpoint.setRulesetDigest(run.getRulesetDigest()); checkpoint.setTargetReferences(result.targetReferences());
                        checkpoint.setTargetDigest(result.targetDigest()); checkpoint.setFirstRunId(runId); checkpoint.setCommittedAt(Instant.now());
                        checkpoints.persist(checkpoint); unit.setState("COMMITTED");
                    }
                    validate(result); unit.setTargetReferences(result.targetReferences()); unit.setTargetDigest(result.targetDigest());
                    unit.setCommittedAt(Instant.now()); ledger.event(run, unitKey, result.alreadyComplete()?"UNIT_ALREADY_COMPLETE":"UNIT_COMMITTED");
                    ledger.touch(run); units.flushAndClear(); callbackFinished.set(true);
                    return result;
                } catch (CdsImportException failure) { throw failure; }
                catch (io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException failure) { throw new CdsImportException(failure.code(), false, failure); }
                catch (io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsClinicalException failure) { throw new CdsImportException(failure.code(), false, failure); }
                catch (Exception failure) { throw new CdsImportException("UNIT_WRITE_FAILED", false, failure); }
                finally { TransactionSynchronizationManager.unbindResource(CdsImportUnitService.class); }
            });
        } catch (RuntimeException failure) {
            if (callbackFinished.get() || failure instanceof TransactionSystemException) {
                throw new CdsImportException("UNIT_COMMIT_OUTCOME_UNKNOWN", true, failure);
            }
            throw failure;
        }
        // Fatal Error deliberately escapes; supervision reconciles durable state after process failure.
    }

    private void verifyIdentity(CdsImportRun run, CdsImportUnit unit, CdsImportCheckpoint checkpoint) {
        if (!checkpoint.getLineageKey().equals(unit.getLineageKey()) || !checkpoint.getSourceDigest().equals(unit.getSourceDigest())
                || !checkpoint.getOptionsDigest().equals(run.getOptionsDigest()) || !checkpoint.getRulesetDigest().equals(run.getRulesetDigest())) {
            throw new CdsImportException("SOURCE_REVISION_REVIEW_REQUIRED");
        }
        if (("COMMITTED".equals(unit.getState()) || "ALREADY_COMPLETE".equals(unit.getState()))
                && (!Objects.equals(unit.getTargetDigest(), checkpoint.getTargetDigest()) || !Objects.equals(unit.getTargetReferences(), checkpoint.getTargetReferences()))) {
            throw new CdsImportException("CHECKPOINT_RECONCILIATION_REQUIRED");
        }
    }

    private void validate(CdsImportReceiptDto receipt) {
        CdsImportKeys.required(receipt.targetReferences(), "target references", 65536);
        CdsImportKeys.digestValue(receipt.targetDigest(), "target digest");
    }
}
