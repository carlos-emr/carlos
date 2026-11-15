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

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsUploadImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.IOException;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

/** Resolves upload options without claiming bytes; job acceptance alone owns the durable claim and acknowledgement. */
@Service
public class CdsUploadSubmissionService {
    private static final Logger LOG = LogManager.getLogger(CdsUploadSubmissionService.class);
    private final CdsImportAccessService access;
    private final CdsImportService imports;
    private final CdsUploadService uploads;
    private final CdsImportConfigurationService configuration;
    private final OscarLogDao audit;

    public CdsUploadSubmissionService(CdsImportAccessService access, CdsImportService imports,
            CdsUploadService uploads, CdsImportConfigurationService configuration, OscarLogDao audit) {
        this.access = Objects.requireNonNull(access);
        this.imports = Objects.requireNonNull(imports);
        this.uploads = Objects.requireNonNull(uploads);
        this.configuration = Objects.requireNonNull(configuration);
        this.audit = Objects.requireNonNull(audit);
    }

    public CdsImportJobDto submit(LoggedInInfo actor, CdsUploadImportCommand requested) throws IOException {
        CdsImportLedgerService.requireNoTransaction();
        Objects.requireNonNull(requested);
        configuration.requireEnabled();
        configuration.requireSource(requested.sourceNamespace(), requested.sourceProfile());
        access.requireOperatorSession(actor);
        String operator = "web-" + actor.getLoggedInSecurity().getSecurityNo();
        var receipt = uploads.status(operator, requested.uploadId());
        var input = uploads.sourceForSubmission(operator, requested.uploadId());
        var command = new CdsImportCommand(input,
                receipt.submissionKey() + (requested.dryRun() ? "-dry-run" : "-import"),
                requested.providerNo() == null ? actor.getLoggedInProviderNo() : requested.providerNo(),
                requested.programId(), requested.facilityId(), requested.sourceNamespace(), requested.sourceProfile(),
                requested.mergeExisting(), requested.dryRun(), requested.matchProviderNames(), requested.timeshiftDays(),
                requested.courseId(), requested.synthetic());
        // Fail known option/authorization errors without changing upload state. The facade rechecks
        // current rights, and JobService rechecks admission before its serialized durable claim.
        access.webActor(actor, command);
        var job = imports.submit(actor, command);
        auditSubmission(actor, job, requested.dryRun());
        return job;
    }

    /**
     * The browser submission's row in the normal chart audit table: who submitted the import, from
     * which client address, and its run. Each committed import unit later writes its own row naming
     * the patient and the same run, but without an address, because the durable worker that writes
     * it serves no request. No source value, file name or health number is written.
     *
     * <p>The row is written after the durable acceptance, in its own transaction. The job is then
     * already accepted and the worker will run it, so a failed write is reported in the server log
     * (without identifiers) instead of telling the browser its accepted import failed.
     */
    private void auditSubmission(LoggedInInfo actor, CdsImportJobDto job, boolean dryRun) {
        try {
            var entry = new OscarLog();
            entry.setProviderNo(actor.getLoggedInProviderNo());
            if (actor.getLoggedInSecurity() != null)
                entry.setSecurityId(actor.getLoggedInSecurity().getSecurityNo());
            entry.setAction(LogConst.IMPORT);
            entry.setContent(LogConst.CON_CDS_IMPORT);
            entry.setContentId(job.id());
            entry.setIp(actor.getIp());
            entry.setData("CDS_IMPORT_SUBMITTED run=" + job.id() + (dryRun ? " dryRun=true" : ""));
            audit.persist(entry);
        } catch (RuntimeException failure) {
            LOG.error("CDS import submission audit row was not written ({})",
                    failure.getClass().getSimpleName());
        }
    }
}
