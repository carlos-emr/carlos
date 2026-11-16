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
package io.github.carlos_emr.carlos.demographic.cdsexport.validator;

import static io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportReconciliationDto.Decision.*;
import static io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportReconciliationDto.Reason.*;

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportProvenanceDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportReconciliationDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportReconciliationDto.Decision;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportReconciliationDto.Reason;
import java.util.Objects;

/**
 * Conservative occurrence-level reconciliation, without clinical reads or writes. A changed or
 * missing native projection requires a category-specific, audited reconciliation; it can never
 * select original source, current native data, or both automatically. Unknown clinical values
 * remain values within their representation and are distinct from missing provenance evidence.
 */
public final class CdsExportReconciliationValidator {
    public CdsExportReconciliationDto validate(CdsExportProvenanceDto evidence) {
        Objects.requireNonNull(evidence, "evidence");
        if (evidence.mappingDigest() == null) return review(evidence, MAPPING_MISSING);
        return switch (evidence.origin()) {
            case NATIVE -> nativeOccurrence(evidence);
            case IMPORTED_SOURCE_ONLY -> sourceOnly(evidence);
            case IMPORTED_PROJECTED -> projected(evidence);
        };
    }

    private CdsExportReconciliationDto nativeOccurrence(CdsExportProvenanceDto evidence) {
        if (evidence.originalDigest() != null || evidence.effectiveDigest() != null
                || evidence.baselineProjectionDigest() != null) return review(evidence, PROVENANCE_CONFLICT);
        if (evidence.currentProjectionDigest() == null) return review(evidence, PROJECTION_MISSING);
        return select(evidence, USE_CURRENT_NATIVE, NATIVE_OCCURRENCE, evidence.currentProjectionDigest());
    }

    private CdsExportReconciliationDto sourceOnly(CdsExportProvenanceDto evidence) {
        if (evidence.baselineProjectionDigest() != null || evidence.currentProjectionDigest() != null) {
            return review(evidence, PROVENANCE_CONFLICT);
        }
        if (evidence.originalDigest() == null || evidence.effectiveDigest() == null) {
            return review(evidence, SOURCE_EVIDENCE_MISSING);
        }
        return select(evidence, USE_EFFECTIVE_SOURCE, SOURCE_ONLY_OCCURRENCE, evidence.effectiveDigest());
    }

    private CdsExportReconciliationDto projected(CdsExportProvenanceDto evidence) {
        if (evidence.originalDigest() == null || evidence.effectiveDigest() == null) {
            return review(evidence, SOURCE_EVIDENCE_MISSING);
        }
        if (evidence.baselineProjectionDigest() == null) return review(evidence, PROJECTION_BASELINE_MISSING);
        if (evidence.currentProjectionDigest() == null) return review(evidence, PROJECTION_MISSING);
        if (!evidence.baselineProjectionDigest().equals(evidence.currentProjectionDigest())) {
            return review(evidence, PROJECTION_CHANGED);
        }
        // Effective source includes accepted logical corrections. Original evidence is not replayed.
        return select(evidence, USE_EFFECTIVE_SOURCE, UNCHANGED_PROJECTION, evidence.effectiveDigest());
    }

    private CdsExportReconciliationDto review(CdsExportProvenanceDto evidence, Reason reason) {
        return select(evidence, REVIEW_REQUIRED, reason, null);
    }

    private CdsExportReconciliationDto select(CdsExportProvenanceDto evidence, Decision decision,
            Reason reason, String digest) {
        return new CdsExportReconciliationDto(evidence.captureDigest(), evidence.identity(), decision, reason, digest);
    }
}
