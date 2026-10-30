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

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;

/** Pure conversion from scalar persistence facts to the existing application contracts. */
final class CdsImportProjections {
    private CdsImportProjections() { }

    static CdsProgramContextDto toDto(io.github.carlos_emr.carlos.commn.dto.CdsProgramContextDto row) {
        return row == null ? null : new CdsProgramContextDto(row.id(), row.name(), row.status(), row.facilityId());
    }

    static CdsProgramMembershipDto toDto(io.github.carlos_emr.carlos.commn.dto.CdsProgramMembershipDto row) {
        return row == null ? null : new CdsProgramMembershipDto(row.membershipId(), row.programId(), row.status());
    }

    static CdsImportControlDto toDto(io.github.carlos_emr.carlos.commn.dto.CdsImportControlDto row) {
        return row == null ? null : new CdsImportControlDto(row.coordinatorOwned(), row.runPresent(), row.ownerEpoch(), row.cancelRequested(), row.admissionOwned());
    }

    static CdsImportPatientOutcomeDto toDto(io.github.carlos_emr.carlos.commn.dto.CdsImportPatientOutcomeDto row) {
        return row == null ? null : new CdsImportPatientOutcomeDto(row.state(), row.copies(), row.sourcePatients());
    }

    static CdsImportDiagnosticSummaryDto toDto(io.github.carlos_emr.carlos.commn.dto.CdsImportDiagnosticSummaryDto row) {
        return row == null ? null : new CdsImportDiagnosticSummaryDto(row.unresolved(), row.releaseFailures(), row.releaseDeferred(), row.releasePending());
    }

    static CdsImportReviewSummaryDto toDto(io.github.carlos_emr.carlos.commn.dto.CdsImportReviewSummaryDto row) {
        return row == null ? null : new CdsImportReviewSummaryDto(row.id(), row.state(), row.errorCode(), row.pending(), row.inactiveDrafts());
    }

    static CdsImportReviewOriginDto toDto(io.github.carlos_emr.carlos.commn.dto.CdsImportReviewOriginDto row) {
        return row == null ? null : new CdsImportReviewOriginDto(row.reviewId(), row.operator(), row.securityNo(), row.sourceKey(), row.sourceXPath(), row.state(), row.version());
    }

    static CdsImportReviewProvenanceDto toDto(io.github.carlos_emr.carlos.commn.dto.CdsImportReviewProvenanceDto row) {
        return row == null ? null : new CdsImportReviewProvenanceDto(row.reviewId(), row.operator(), row.actedAt(), row.kind(), row.state(), row.reason(), row.acceptedVersion(), row.sourceXPath());
    }

    static CdsImportJobBindingDto toDto(io.github.carlos_emr.carlos.commn.dto.CdsImportJobBindingDto row) {
        return row == null ? null : new CdsImportJobBindingDto(row.id(), row.operator(), row.providerNo(), row.sourceNamespace(), row.profile(), row.state(), row.sourceDigest(), row.optionsDigest(), row.rulesetDigest(), row.manifestDigest(), row.expectedPatients(), row.expectedUnits(), row.ownerEpoch(), row.cancelRequested(), row.createdAt(), row.updatedAt(), row.reportGeneration(), row.reportLocation(), row.reportDigest(), row.documentLocation());
    }

    static CdsImportReportEntryDto toDto(io.github.carlos_emr.carlos.commn.dto.CdsImportReportEntryDto row) {
        return row == null ? null : new CdsImportReportEntryDto(row.reportState(), row.discoveryComplete(), row.dryRun(), row.sealedInventory(), row.documentLocation());
    }
}
