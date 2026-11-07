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
package io.github.carlos_emr.carlos.demographic.cdsexport.dto;

import java.util.Objects;

/** A preliminary value-selection decision; this is never a publication or disclosure receipt. */
public record CdsExportReconciliationDto(String captureDigest, CdsExportIdentityDto identity, Decision decision,
        Reason reason, String representationDigest) {
    public enum Decision { USE_CURRENT_NATIVE, USE_EFFECTIVE_SOURCE, REVIEW_REQUIRED }
    public enum Reason {
        NATIVE_OCCURRENCE, SOURCE_ONLY_OCCURRENCE, UNCHANGED_PROJECTION,
        MAPPING_MISSING, SOURCE_EVIDENCE_MISSING, PROJECTION_BASELINE_MISSING,
        PROJECTION_MISSING, PROJECTION_CHANGED, PROVENANCE_CONFLICT
    }

    public CdsExportReconciliationDto {
        if (captureDigest == null || !captureDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("CDS_EXPORT_CAPTURE_INVALID");
        }
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(reason, "reason");
        boolean selected = decision != Decision.REVIEW_REQUIRED;
        if (selected != (representationDigest != null)
                || representationDigest != null && !representationDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("CDS_EXPORT_RECONCILIATION_INVALID");
        }
        boolean compatible = switch (decision) {
            case USE_CURRENT_NATIVE -> reason == Reason.NATIVE_OCCURRENCE;
            case USE_EFFECTIVE_SOURCE -> reason == Reason.SOURCE_ONLY_OCCURRENCE
                    || reason == Reason.UNCHANGED_PROJECTION;
            case REVIEW_REQUIRED -> reason != Reason.NATIVE_OCCURRENCE
                    && reason != Reason.SOURCE_ONLY_OCCURRENCE && reason != Reason.UNCHANGED_PROJECTION;
        };
        if (!compatible) throw new IllegalArgumentException("CDS_EXPORT_RECONCILIATION_INVALID");
    }

    @Override public String toString() { return "CdsExportReconciliationDto[protected]"; }
}
