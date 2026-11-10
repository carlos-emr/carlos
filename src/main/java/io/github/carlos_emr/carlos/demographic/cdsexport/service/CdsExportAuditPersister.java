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

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable patient membership in the normal chart audit, independent of encrypted staging
 * retention. PREPARED records a validated private package candidate; actual publication and
 * delivery outcomes remain explicit, generation-bound events in cds_export_event. Partial
 * preparation or repeated attempts never falsely assert that a patient was delivered.
 */
@Service
public class CdsExportAuditPersister {
    private final OscarLogDao logs;

    public CdsExportAuditPersister(OscarLogDao logs) {
        this.logs = Objects.requireNonNull(logs);
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class,
            timeout = 15)
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
        // No asynchronous or best-effort audit path may authorize a publishable package.
        logs.flush();
    }
}
