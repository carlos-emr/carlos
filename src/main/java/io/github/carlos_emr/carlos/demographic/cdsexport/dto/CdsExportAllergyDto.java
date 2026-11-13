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

/**
 * Typed allergy values after reconciliation; this DTO alone is not coverage proof.
 * Code authority is logical: DIN uses Code; ATC uses its versioned residual marker.
 */
public record CdsExportAllergyDto(
        String description,
        String property,
        String codeType,
        String codeValue,
        String reactionType,
        CdsExportDateDto start,
        String lifeStage,
        String severity,
        String reaction,
        CdsExportDateDto recorded,
        String notes,
        boolean archived,
        String ageOfOnset,
        String onsetOfReaction,
        String nativeSeverity,
        String longReaction) {
    /** An allergy whose reaction fits the standard Reaction element. */
    public CdsExportAllergyDto(
            String description,
            String property,
            String codeType,
            String codeValue,
            String reactionType,
            CdsExportDateDto start,
            String lifeStage,
            String severity,
            String reaction,
            CdsExportDateDto recorded,
            String notes,
            boolean archived,
            String ageOfOnset,
            String onsetOfReaction,
            String nativeSeverity) {
        this(
                description,
                property,
                codeType,
                codeValue,
                reactionType,
                start,
                lifeStage,
                severity,
                reaction,
                recorded,
                notes,
                archived,
                ageOfOnset,
                onsetOfReaction,
                nativeSeverity,
                null);
    }

    public CdsExportAllergyDto(
            String description,
            String property,
            String codeType,
            String codeValue,
            String reactionType,
            CdsExportDateDto start,
            String lifeStage,
            String severity,
            String reaction,
            CdsExportDateDto recorded,
            String notes,
            boolean archived,
            String ageOfOnset,
            String onsetOfReaction) {
        this(
                description,
                property,
                codeType,
                codeValue,
                reactionType,
                start,
                lifeStage,
                severity,
                reaction,
                recorded,
                notes,
                archived,
                ageOfOnset,
                onsetOfReaction,
                null);
    }

    public CdsExportAllergyDto(
            String description,
            String property,
            String codeType,
            String codeValue,
            String reactionType,
            CdsExportDateDto start,
            String lifeStage,
            String severity,
            String reaction,
            CdsExportDateDto recorded,
            String notes,
            boolean archived) {
        this(
                description,
                property,
                codeType,
                codeValue,
                reactionType,
                start,
                lifeStage,
                severity,
                reaction,
                recorded,
                notes,
                archived,
                null,
                null,
                null);
    }

    public CdsExportAllergyDto {
        Objects.requireNonNull(start);
        Objects.requireNonNull(recorded);
        // A reaction too long for Reaction travels only in its carrier (and, as text, in Notes).
        if (longReaction != null && (reaction != null || longReaction.isEmpty()))
            throw new IllegalArgumentException("CDS_EXPORT_ALLERGY_REACTION_INVALID");
        if ((codeType == null) != (codeValue == null)
                || codeType != null && (codeType.isEmpty() || codeValue.isEmpty())) {
            throw new IllegalArgumentException("CDS_EXPORT_ALLERGY_CODE_INCOMPLETE");
        }
        if (nativeSeverity != null
                && (severity != null
                        || !io.github.carlos_emr.carlos.demographic.cds.contract
                                .CdsNativeExtensionContract.ALLERGY_SEVERITY_VALUES
                                .containsKey(nativeSeverity)))
            throw new IllegalArgumentException("CDS_EXPORT_ALLERGY_SEVERITY_INVALID");
    }

    @Override
    public String toString() {
        return "CdsExportAllergyDto[protected]";
    }
}
