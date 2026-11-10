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

import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsUploadImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.IOException;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Resolves upload options without claiming bytes; job acceptance alone owns the durable claim and acknowledgement. */
@Service
public class CdsUploadSubmissionService {
    private final CdsImportAccessService access;
    private final CdsImportService imports;
    private final CdsUploadService uploads;
    private final CdsImportConfigurationService configuration;

    public CdsUploadSubmissionService(CdsImportAccessService access, CdsImportService imports,
            CdsUploadService uploads, CdsImportConfigurationService configuration) {
        this.access = Objects.requireNonNull(access);
        this.imports = Objects.requireNonNull(imports);
        this.uploads = Objects.requireNonNull(uploads);
        this.configuration = Objects.requireNonNull(configuration);
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
        return imports.submit(actor, command);
    }
}
