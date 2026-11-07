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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportProvenanceDto.Origin.*;
import static io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportReconciliationDto.Decision.*;
import static io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportReconciliationDto.Reason.*;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportProvenanceDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportProvenanceDto.Origin;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Expected decisions are authored independently of import normalization and row receipt code. */
@Tag("unit")
@Tag("cds-export")
class CdsExportReconciliationValidatorUnitTest {
    private static final String CAPTURE = "f".repeat(64);
    private static final String ORIGINAL = "a".repeat(64);
    private static final String CORRECTED = "b".repeat(64);
    private static final String BASELINE = "c".repeat(64);
    private static final String CHANGED = "d".repeat(64);
    private static final String MAPPING = "e".repeat(64);
    private static final CdsExportIdentityDto ID = new CdsExportIdentityDto(
            "1".repeat(64), CdsCategory.CLINICAL_NOTES, "2".repeat(64));
    private final CdsExportReconciliationValidator validator = new CdsExportReconciliationValidator();

    @Test void shouldSelectAcceptedCorrection_whenImportedProjectionIsUnchanged() {
        var result = validator.validate(evidence(IMPORTED_PROJECTED, ORIGINAL, CORRECTED, BASELINE, BASELINE));
        assertThat(result.decision()).isEqualTo(USE_EFFECTIVE_SOURCE);
        assertThat(result.reason()).isEqualTo(UNCHANGED_PROJECTION);
        assertThat(result.captureDigest()).isEqualTo(CAPTURE);
        assertThat(result.representationDigest()).isEqualTo(CORRECTED).isNotEqualTo(ORIGINAL);
    }

    @Test void shouldPreserveSourceOnlyCorrection_withoutInventingANativeProjection() {
        var result = validator.validate(evidence(IMPORTED_SOURCE_ONLY, ORIGINAL, CORRECTED, null, null));
        assertThat(result.decision()).isEqualTo(USE_EFFECTIVE_SOURCE);
        assertThat(result.reason()).isEqualTo(SOURCE_ONLY_OCCURRENCE);
        assertThat(result.representationDigest()).isEqualTo(CORRECTED);
    }

    @Test void shouldRefuseAutomaticPrecedence_whenNativeProjectionHasChanged() {
        var result = validator.validate(evidence(IMPORTED_PROJECTED, ORIGINAL, CORRECTED, BASELINE, CHANGED));
        assertThat(result.decision()).isEqualTo(REVIEW_REQUIRED);
        assertThat(result.reason()).isEqualTo(PROJECTION_CHANGED);
        assertThat(result.representationDigest()).isNull();
    }

    @Test void shouldRefuseOriginalReplay_whenNativeProjectionWasDeleted() {
        var result = validator.validate(evidence(IMPORTED_PROJECTED, ORIGINAL, CORRECTED, BASELINE, null));
        assertThat(result.decision()).isEqualTo(REVIEW_REQUIRED);
        assertThat(result.reason()).isEqualTo(PROJECTION_MISSING);
        assertThat(result.representationDigest()).isNull();
    }

    @Test void shouldRequireBaselineEvidence_forOlderImportedRecords() {
        var result = validator.validate(evidence(IMPORTED_PROJECTED, ORIGINAL, CORRECTED, null, CHANGED));
        assertThat(result.decision()).isEqualTo(REVIEW_REQUIRED);
        assertThat(result.reason()).isEqualTo(PROJECTION_BASELINE_MISSING);
    }

    @Test void shouldRequireBothOriginalAndEffectiveEvidence_forImportedRecords() {
        for (Origin origin : new Origin[]{IMPORTED_SOURCE_ONLY, IMPORTED_PROJECTED}) {
            String baseline = origin == IMPORTED_PROJECTED ? BASELINE : null;
            assertThat(validator.validate(evidence(origin, null, CORRECTED, baseline, baseline)).reason())
                    .isEqualTo(SOURCE_EVIDENCE_MISSING);
            assertThat(validator.validate(evidence(origin, ORIGINAL, null, baseline, baseline)).reason())
                    .isEqualTo(SOURCE_EVIDENCE_MISSING);
        }
    }

    @Test void shouldRequireReviewedMapping_beforeAnyRepresentationIsSelected() {
        var evidence = new CdsExportProvenanceDto(CAPTURE, ID, NATIVE, null, null, null, null, CHANGED);
        var result = validator.validate(evidence);
        assertThat(result.decision()).isEqualTo(REVIEW_REQUIRED);
        assertThat(result.reason()).isEqualTo(MAPPING_MISSING);
    }

    @Test void shouldSelectCurrentNative_forANativeOnlyOccurrence() {
        var result = validator.validate(evidence(NATIVE, null, null, null, CHANGED));
        assertThat(result.decision()).isEqualTo(USE_CURRENT_NATIVE);
        assertThat(result.representationDigest()).isEqualTo(CHANGED);
    }

    @Test void shouldRefuseConflictingOrigins_whenNativeOrSourceOnlyEvidenceContainsProjectionContradictions() {
        assertThat(validator.validate(evidence(NATIVE, ORIGINAL, null, null, CHANGED)).reason())
                .isEqualTo(PROVENANCE_CONFLICT);
        assertThat(validator.validate(evidence(IMPORTED_SOURCE_ONLY, ORIGINAL, CORRECTED, BASELINE, null)).reason())
                .isEqualTo(PROVENANCE_CONFLICT);
        assertThat(validator.validate(evidence(IMPORTED_SOURCE_ONLY, ORIGINAL, CORRECTED, null, CHANGED)).reason())
                .isEqualTo(PROVENANCE_CONFLICT);
    }

    @Test void shouldRejectMalformedEvidence_withoutIncludingItsContentsInErrors() {
        assertThatThrownBy(() -> new CdsExportProvenanceDto(CAPTURE, ID, NATIVE, "patient-value",
                null, null, null, CHANGED)).hasMessage("CDS_EXPORT_PROVENANCE_DIGEST_INVALID");
    }

    @Test void shouldMaskProtectedIdentityAndEvidence_inStringRepresentations() {
        var evidence = evidence(IMPORTED_PROJECTED, ORIGINAL, CORRECTED, BASELINE, BASELINE);
        assertThat(ID.toString()).isEqualTo("CdsExportIdentityDto[protected]");
        assertThat(evidence.toString()).isEqualTo("CdsExportProvenanceDto[protected]");
        assertThat(validator.validate(evidence).toString()).isEqualTo("CdsExportReconciliationDto[protected]");
    }

    private CdsExportProvenanceDto evidence(Origin origin, String original, String effective,
            String baseline, String current) {
        return new CdsExportProvenanceDto(CAPTURE, ID, origin, MAPPING, original, effective, baseline, current);
    }
}
