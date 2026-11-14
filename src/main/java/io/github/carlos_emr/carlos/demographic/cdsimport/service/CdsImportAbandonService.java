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

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportPatientDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The supported way out of an import that stopped after writing charts and cannot complete,
 * for example because a defect stops it on every resume or because an upgrade changed the
 * ruleset it is bound to. Run through the one-shot CLI ({@code carlos-ctl cds-import --abandon})
 * by a root-delegated operator acting for the run's provider: its own operator, or another whose
 * delegation covers the run's provider, facility and program (the browser has no abandon).
 *
 * <p>An abandon never deletes or changes a clinical row, unit, checkpoint, lineage, source field
 * or file. Under the same physical ownership a resume requires (the coordinator lock and a new,
 * verified maintenance owner), it re-verifies each held chart's committed rows against their
 * sealed receipts and records the result; then, in one transaction, the held charts become
 * {@code ABANDONED} without a hold, each with a diagnostic, an event and a chart-audit row, and
 * the run becomes {@code ABANDONED} for good. The operator's stated reason is kept only in a
 * protected record. Maintenance is then released through the ordinary release proof
 * ({@code RELEASE_DEFERRED} while another run still holds a chart), and the reports are
 * regenerated. The run never reports a clean completion, a resume or second abandon is refused,
 * and the exporter keeps refusing the released charts.
 */
@Service
public class CdsImportAbandonService {
    private static final org.apache.logging.log4j.Logger LOG =
            org.apache.logging.log4j.LogManager.getLogger(CdsImportAbandonService.class);

    /** The longest stated reason, in characters. */
    static final int MAXIMUM_REASON = 1000;

    private final CdsImportLedgerService ledger;
    private final CdsImportCoordinatorService coordinators;
    private final CdsImportMaintenanceService maintenance;
    private final CdsImportTargetSafetyService safety;
    private final CdsTargetReceiptService receipts;
    private final CdsImportResultService results;
    private final CdsImportFailureEvidenceService evidence;
    private final CdsImportConfigurationService configuration;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Autowired
    public CdsImportAbandonService(
            CdsImportLedgerService ledger,
            CdsImportCoordinatorService coordinators,
            CdsImportMaintenanceService maintenance,
            CdsImportTargetSafetyService safety,
            CdsTargetReceiptService receipts,
            CdsImportResultService results,
            CdsImportFailureEvidenceService evidence,
            CdsImportConfigurationService configuration) {
        this.ledger = ledger;
        this.coordinators = coordinators;
        this.maintenance = maintenance;
        this.safety = safety;
        this.receipts = receipts;
        this.results = results;
        this.evidence = evidence;
        this.configuration = configuration;
    }

    /**
     * Abandons a stopped, cancelled written run.
     *
     * @param runId the run to abandon
     * @param actor a root-delegated operator acting for the run's provider
     * @param confirmation the run ID typed a second time; it must match
     * @param reason the operator's stated reason, kept only in the protected record
     * @return the run's status after the abandon
     * @throws CdsImportException with a stable code when the run cannot be abandoned; nothing
     *     is changed then
     * @throws SecurityException when the actor does not act for the run's provider
     */
    public CdsImportJobDto abandon(
            String runId, CdsImportActorDto actor, String confirmation, String reason) {
        CdsImportLedgerService.requireNoTransaction();
        Objects.requireNonNull(actor, "actor");
        if (runId == null || !runId.equals(confirmation))
            throw new CdsImportException("ABANDON_CONFIRMATION_MISMATCH");
        String stated = reason(reason);
        configuration.requireEnabled();
        try (var coordinator = coordinators.acquire()) {
            // Set once the abandon transaction has committed: from then on the run is abandoned,
            // so a later failure (the release, the owner's close, report delivery) is recorded and
            // reported through the run's status, never thrown as if nothing had been done.
            boolean abandoned = false;
            try {
                // Refuse before taking the maintenance window: an owner that closes without an
                // explicit reopen keeps the clinic closed, so a refused request must never take
                // one.
                ledger.requireAbandonable(runId, actor);
                try (var owner = maintenance.acquire()) {
                    safety.verify(owner);
                    long epoch = ledger.claimAbandon(runId, actor, coordinator, owner);
                    Map<String, String> verification = new LinkedHashMap<>();
                    String record;
                    // The published digest is salted: a short reason that names a patient cannot be
                    // recovered by guessing. The salt is kept beside the reason, in the protected
                    // record only.
                    String salt = CdsPaths.hex(salt());
                    String digest = reasonDigest(salt, stated);
                    try {
                        verify(runId, epoch, coordinator, owner, verification);
                        record =
                                evidence.writeAbandonment(
                                        runId,
                                        epoch,
                                        record(runId, actor, stated, salt, digest, verification));
                        if (record == null)
                            throw new CdsImportException("ABANDON_EVIDENCE_UNAVAILABLE");
                        coordinator.check();
                        ledger.abandon(
                                runId,
                                epoch,
                                actor,
                                verification,
                                digest,
                                record,
                                (demographicNo, checked) ->
                                        chartAudit(runId, actor, demographicNo, checked));
                    } catch (RuntimeException refused) {
                        // Nothing was abandoned: give back administrative admission only. The
                        // maintenance window and every hold stay exactly as they were.
                        try {
                            ledger.release(runId, epoch);
                        } catch (RuntimeException retained) {
                            refused.addSuppressed(retained);
                        }
                        throw refused;
                    }
                    abandoned = true;
                    release(runId, epoch, owner);
                }
            } catch (RuntimeException afterCommit) {
                if (!abandoned) throw afterCommit;
                // Maintenance stays closed until a reconciled release; the run's status names
                // the release state, and the operator reconciles as for any failed close.
                LOG.error("CDS import abandon: ABANDON_RELEASE_UNCONFIRMED {}",
                        io.github.carlos_emr.carlos.utility.LogSafe.exceptionTrace(afterCommit));
            }
            try {
                long reporting = ledger.claimReporting(runId);
                try {
                    results.generate(runId, reporting, coordinator);
                } catch (IOException | RuntimeException undelivered) {
                    reportFailure(runId, reporting, undelivered);
                }
            } catch (RuntimeException unclaimed) {
                // The abandon is durable; its reports are delivered like any stopped run's.
                LOG.error("CDS import abandon: ABANDON_REPORT_UNCLAIMED {}",
                        io.github.carlos_emr.carlos.utility.LogSafe.exceptionTrace(unclaimed));
            }
        }
        return ledger.status(runId);
    }

    /** Each held, written or uncertain chart, checked against its receipts under the owner. */
    private void verify(
            String runId,
            long epoch,
            CdsImportCoordinatorService.Owner coordinator,
            CdsImportMaintenanceService.Owner owner,
            Map<String, String> verification) {
        String after = "";
        while (true) {
            List<CdsImportPatientDto> page = ledger.patients(runId, after, 100);
            if (page.isEmpty()) break;
            for (CdsImportPatientDto patient : page) {
                after = patient.patientKey();
                if (!patient.held()
                        && !CdsImportLedgerService.ABANDONABLE_PATIENT.contains(patient.state()))
                    continue;
                coordinator.check();
                owner.heartbeat();
                verification.put(
                        patient.patientKey(),
                        owner.read(
                                () ->
                                        ledger.abandonVerification(
                                                runId,
                                                epoch,
                                                patient.patientKey(),
                                                receipts::verify)));
            }
            if (page.size() < 100) break;
        }
    }

    /**
     * Reopens ordinary clinical access only through the owner's read-back proof. Another run's
     * hold defers the release; a failed close keeps maintenance closed for reconciliation.
     */
    private void release(String runId, long epoch, CdsImportMaintenanceService.Owner owner) {
        try {
            owner.allowReopen();
            owner.close();
        } catch (CdsImportException failed) {
            ledger.maintenanceReleaseFailed(runId, epoch, null);
            return;
        }
        if (owner.releaseVerified()) ledger.maintenanceReleaseReconciled(runId, epoch);
        else ledger.maintenanceReleaseDeferred(runId, epoch, "GLOBAL_HOLDS_REMAIN");
    }

    /**
     * The chart's ordinary audit row, in the abandon transaction: the importing provider and
     * security record, and only the run, the action and the witness result as data.
     */
    private void chartAudit(
            String runId, CdsImportActorDto actor, int demographicNo, String verification) {
        var entry = new io.github.carlos_emr.carlos.commn.model.OscarLog();
        entry.setProviderNo(actor.providerNo());
        if (actor.securityNo() != null) entry.setSecurityId(actor.securityNo());
        entry.setAction(io.github.carlos_emr.carlos.log.LogConst.IMPORT_ABANDONED);
        entry.setContent(io.github.carlos_emr.carlos.log.LogConst.CON_DEMOGRAPHIC);
        entry.setContentId(Integer.toString(demographicNo));
        entry.setDemographicId(demographicNo);
        entry.setData(
                "CDS_IMPORT_ABANDONED run="
                        + runId
                        + " receipts="
                        + verification
                        + "; import hold released incomplete; outcomes=cds_import_event");
        // OscarLog's identity key makes persist insert at once, while the chart-audit context
        // is bound. A flush here would dirty-check the whole growing release transaction once
        // per chart.
        entityManager.persist(entry);
    }

    private static Map<String, Object> record(
            String runId,
            CdsImportActorDto actor,
            String reason,
            String salt,
            String digest,
            Map<String, String> verification) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("runId", runId);
        value.put("recordedAt", Instant.now().toString());
        value.put("operator", actor.operator());
        value.put("providerNo", actor.providerNo());
        value.put("operatorProviderNo", actor.operatorProviderNo());
        value.put("securityNo", actor.securityNo());
        value.put("reason", reason);
        value.put("reasonSalt", salt);
        value.put("reasonSha256", digest);
        List<Map<String, String>> charts = new ArrayList<>();
        verification.forEach(
                (patientKey, checked) ->
                        charts.add(Map.of("patientKey", patientKey, "receipts", checked)));
        value.put("charts", charts);
        return value;
    }

    /**
     * The operator's reason: required, one line of at most {@link #MAXIMUM_REASON} characters,
     * no control characters.
     */
    static String reason(String value) {
        if (value == null
                || value.isBlank()
                // Characters as a person counts them (code points), as carlos-ctl counts them.
                || value.codePointCount(0, value.length()) > MAXIMUM_REASON
                || value.chars()
                        .anyMatch(
                                c ->
                                        Character.isISOControl(c)
                                                || Character.getType(c)
                                                        == Character.LINE_SEPARATOR
                                                || Character.getType(c)
                                                        == Character.PARAGRAPH_SEPARATOR))
            throw new CdsImportException("ABANDON_REASON_INVALID");
        return value.strip();
    }

    static String sha256(String value) {
        return CdsPaths.hex(CdsPaths.sha256().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    /** The published digest of a reason: SHA-256 over the salt, a newline and the reason. */
    static String reasonDigest(String salt, String reason) {
        return sha256(salt + "\n" + reason);
    }

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    private static byte[] salt() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return value;
    }

    private void reportFailure(String runId, long epoch, Exception failure) {
        String code =
                failure instanceof CdsImportException known
                        ? known.getCode()
                        : "IMPORT_REPORT_DELIVERY_FAILED";
        try {
            String detail = evidence.write(runId, epoch, failure);
            ledger.reportingFailureDiagnostic(runId, epoch, code, detail);
        } catch (RuntimeException unavailable) {
            // The abandon itself is durable; report delivery is retried like any stopped run's.
        }
    }
}
