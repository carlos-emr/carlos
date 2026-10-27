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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Objects;

/** Reconciles lost submission acknowledgements without a cross-operator or cross-provider lookup. */
@Service
public class CdsImportSubmissionService {
    private final CdsImportLedgerService ledger;
    private final CdsImportJobService jobs;
    private final CdsImportAccessService access;

    public CdsImportSubmissionService(
            CdsImportLedgerService ledger,
            CdsImportJobService jobs,
            CdsImportAccessService access) {
        this.ledger = ledger;
        this.jobs = jobs;
        this.access = access;
    }

    public record Resolved(String runId, CdsImportCommand command, CdsImportActorDto actor) {
        @Override
        public String toString() {
            return "Resolved[protected]";
        }
    }

    /** verifiedOperator comes only from the independently verified delegation ticket. */
    public Resolved resolve(String verifiedOperator, String selectedProvider, String submissionKey)
            throws IOException {
        CdsImportLedgerService.requireNoTransaction();
        if (!bounded(verifiedOperator, 65)
                || !bounded(selectedProvider, 16)
                || !bounded(submissionKey, 128)) throw unknown();
        var row = ledger.findSubmission(verifiedOperator, submissionKey);
        if (row == null
                || !Objects.equals(verifiedOperator, row.operator())
                || !Objects.equals(selectedProvider, row.providerNo())) throw unknown();
        // The ledger's bounded lookup transaction has ended before protected job-document I/O.
        CdsImportCommand command = jobs.load(row.id()).command();
        if (!Objects.equals(selectedProvider, command.providerNo())
                || !Objects.equals(submissionKey, command.submissionKey())) throw unknown();
        CdsImportActorDto actor = access.delegatedActor(verifiedOperator, command);
        return new Resolved(row.id(), command, actor);
    }

    private static boolean bounded(String value, int maximum) {
        return value != null
                && !value.isBlank()
                && value.length() <= maximum
                && value.chars().noneMatch(c -> c < 32 || c == 127);
    }

    private static CdsImportException unknown() {
        return new CdsImportException("SUBMISSION_OUTCOME_NOT_YET_KNOWN", true, null);
    }
}
