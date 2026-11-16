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

import io.github.carlos_emr.carlos.commn.dao.CdsExportStatementWaitReadSupport;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Durable patient membership in the normal chart audit, independent of encrypted staging
 * retention. PREPARED records a validated private package candidate; actual publication and
 * delivery outcomes remain explicit, generation-bound events in cds_export_event. Partial
 * preparation or repeated attempts never falsely assert that a patient was delivered.
 *
 * <p>Each batch of audit rows is written in a new transaction of its own, which has no time limit:
 * it runs as a write unit ({@link CdsExportStatementWaitReadSupport#writing}), so a slow database
 * is waited for, and a sending the server ended before its commit is rolled back and written
 * again whole, never twice. The rows are built anew for each sending.</p>
 */
@Service
public class CdsExportAuditPersister {
    private final OscarLogDao logs;

    /** Where each batch's own transaction comes from; null for a caller that writes directly. */
    private final ObjectProvider<PlatformTransactionManager> managers;

    /**
     * Writes each batch directly, in whatever transaction the caller holds: for a test that
     * calls the persister without a container.
     */
    public CdsExportAuditPersister(OscarLogDao logs) {
        this.logs = Objects.requireNonNull(logs);
        this.managers = null;
    }

    /** Writes each batch in a new transaction of its own (REQUIRES_NEW), as a write unit. */
    @Autowired
    public CdsExportAuditPersister(
            OscarLogDao logs, ObjectProvider<PlatformTransactionManager> managers) {
        this.logs = Objects.requireNonNull(logs);
        this.managers = Objects.requireNonNull(managers);
    }

    /** One batch of audit rows, committed alone and waited for (see the class description). */
    private void write(Runnable rows) {
        if (managers == null) {
            rows.run();
            return;
        }
        // Resolved when a batch is written, so a context that only wires the persister needs no
        // transaction manager; no timeout of its own.
        var transactions = new TransactionTemplate(managers.getObject());
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        CdsExportStatementWaitReadSupport.writing(
                transactions,
                status -> {
                    rows.run();
                    return null;
                });
    }

    public void prepared(CdsExportLeaseDto lease, List<Integer> patients) {
        Objects.requireNonNull(lease);
        var batch = List.copyOf(patients);
        if (batch.isEmpty()
                || batch.size() > 100
                || new HashSet<>(batch).size() != batch.size()
                || batch.stream().anyMatch(patient -> patient < 1))
            throw new IllegalArgumentException("CDS_EXPORT_AUDIT_PATIENT_SCOPE_INVALID");
        var actor = lease.document().actor();
        String data =
                "CDS_EXPORT_PACKAGE_PREPARED run="
                        + lease.runId()
                        + " epoch="
                        + lease.epoch()
                        + " operator="
                        + actor.operatorKey()
                        + "; outcomes=cds_export_event";
        write(
                () -> {
                    for (int patient : batch) {
                        var entry = new OscarLog();
                        entry.setProviderNo(actor.providerNo());
                        entry.setSecurityId(actor.securityNo());
                        entry.setAction("EXPORT");
                        entry.setContent("demographic");
                        entry.setContentId(Integer.toString(patient));
                        entry.setDemographicId(patient);
                        entry.setData(data);
                        logs.persist(entry);
                    }
                    // No asynchronous or best-effort audit path may authorize a publishable
                    // package.
                    logs.flush();
                });
    }

    /**
     * The charts the export read again and adopted from an earlier attempt of the same job instead
     * of building them: their new capture was exactly the one the adopted chart was built from.
     * One read row per patient, bound to the run, this generation and the generation adopted from;
     * a packaged chart still gets its {@link #prepared} row as well.
     */
    public void adopted(CdsExportLeaseDto lease, List<Integer> patients, long sourceEpoch) {
        Objects.requireNonNull(lease);
        var batch = List.copyOf(patients);
        if (batch.isEmpty()
                || batch.size() > 100
                || new HashSet<>(batch).size() != batch.size()
                || batch.stream().anyMatch(patient -> patient < 1)
                || sourceEpoch < 1
                || sourceEpoch >= lease.epoch())
            throw new IllegalArgumentException("CDS_EXPORT_AUDIT_PATIENT_SCOPE_INVALID");
        var actor = lease.document().actor();
        String data =
                "CDS_EXPORT_CHART_ADOPTED run="
                        + lease.runId()
                        + " epoch="
                        + lease.epoch()
                        + " sourceEpoch="
                        + sourceEpoch
                        + " operator="
                        + actor.operatorKey()
                        + "; outcomes=cds_export_event";
        write(
                () -> {
                    for (int patient : batch) {
                        var entry = new OscarLog();
                        entry.setProviderNo(actor.providerNo());
                        entry.setSecurityId(actor.securityNo());
                        // The chart was read again, as the chart's own read audit records a read.
                        entry.setAction(io.github.carlos_emr.carlos.log.LogConst.READ);
                        entry.setContent("demographic");
                        entry.setContentId(Integer.toString(patient));
                        entry.setDemographicId(patient);
                        entry.setData(data);
                        logs.persist(entry);
                    }
                    logs.flush();
                });
    }

    /** Why a chart the export read is not in a prepared package. */
    public enum ReadOutcome {
        /** Held back from an otherwise prepared package; named in its ReadMe. */
        HELD,
        /** The job stopped (refused, failed, cancelled or fenced) after reading the chart. */
        STOPPED
    }

    /**
     * Chart access that did not become a prepared package still belongs in the normal chart
     * audit: the export read (or began to read) the patient's record even though nothing was
     * disclosed. One read row per patient, bound to the run and generation; the job outcome
     * itself stays in cds_export_event.
     */
    public void read(CdsExportLeaseDto lease, List<Integer> patients, ReadOutcome outcome) {
        Objects.requireNonNull(lease);
        Objects.requireNonNull(outcome);
        var batch = List.copyOf(patients);
        if (batch.isEmpty()
                || batch.size() > 100
                || new HashSet<>(batch).size() != batch.size()
                || batch.stream().anyMatch(patient -> patient < 1))
            throw new IllegalArgumentException("CDS_EXPORT_AUDIT_PATIENT_SCOPE_INVALID");
        var actor = lease.document().actor();
        String data =
                "CDS_EXPORT_CHART_ACCESS run="
                        + lease.runId()
                        + " epoch="
                        + lease.epoch()
                        + " operator="
                        + actor.operatorKey()
                        + " outcome="
                        + outcome.name()
                        + "; outcomes=cds_export_event";
        write(
                () -> {
                    for (int patient : batch) {
                        var entry = new OscarLog();
                        entry.setProviderNo(actor.providerNo());
                        entry.setSecurityId(actor.securityNo());
                        // The same action the chart's own read audit uses.
                        entry.setAction(io.github.carlos_emr.carlos.log.LogConst.READ);
                        entry.setContent("demographic");
                        entry.setContentId(Integer.toString(patient));
                        entry.setDemographicId(patient);
                        entry.setData(data);
                        logs.persist(entry);
                    }
                    logs.flush();
                });
    }
}
