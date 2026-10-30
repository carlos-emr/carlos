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

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;

import io.github.carlos_emr.carlos.commn.model.CdsImportRun;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Selects one already durable immutable report-set manifest; stale projectors cannot overwrite it. */
@Service
public class CdsImportReportService {
    private final CdsImportLedgerService ledger;
    private final TransactionTemplate transaction;

    public CdsImportReportService(CdsImportLedgerService ledger, PlatformTransactionManager manager) {
        this.ledger=ledger; transaction=CdsImportLedgerService.template(manager);
    }

    public CdsImportJobDto select(String runId, long epoch, long expectedGeneration, String manifestDigest,
            String reportSetLocation, String reportSetDigest) {
        CdsImportLedgerService.requireNoTransaction();
        CdsImportKeys.digestValue(manifestDigest, "source manifest digest");
        CdsImportKeys.required(reportSetLocation, "report set location", 1024);
        CdsImportKeys.digestValue(reportSetDigest, "report set digest");
        return transaction.execute(status -> {
            CdsImportRun run=ledger.lockOperational(runId, epoch);
            String expectedManifest=run.getManifestDigest()==null?run.getSourceDigest():run.getManifestDigest();
            if (!manifestDigest.equals(expectedManifest) || run.getReportGeneration()!=expectedGeneration
                    || expectedGeneration==Long.MAX_VALUE) throw new CdsImportException("REPORT_GENERATION_CONFLICT");
            run.setReportState("DELIVERED"); run.setReportGeneration(expectedGeneration+1); run.setReportLocation(reportSetLocation); run.setReportDigest(reportSetDigest);
            ledger.touch(run); ledger.event(run, null, "REPORT_SELECTED"); return CdsImportLedgerService.dto(run);
        });
    }
}
