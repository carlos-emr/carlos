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
package io.github.carlos_emr.carlos.demographic.cdsimport.dto;

/** AR is an adverse reaction, not evidence of an intolerance subtype. */
public record CdsAllergyDto(
        String description,
        String property,
        String code,
        String codeSystem,
        String reactionType,
        String reaction,
        String severity,
        String lifeStage,
        String notes,
        CdsTemporalDto start,
        CdsTemporalDto recorded,
        @com.fasterxml.jackson.annotation.JsonInclude(
                        com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
                boolean archived,
        @com.fasterxml.jackson.annotation.JsonInclude(
                        com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                String ageOfOnset,
        @com.fasterxml.jackson.annotation.JsonInclude(
                        com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                String onsetOfReaction,
        @com.fasterxml.jackson.annotation.JsonInclude(
                        com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
                boolean atcResidual,
        // Decision 9: a source archive marker is restored natively only when the operator opts
        // in. True records that this import kept the allergy active despite the marker; absent
        // (false) keeps the meaning of every payload sealed before the option existed.
        @com.fasterxml.jackson.annotation.JsonInclude(
                        com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
                boolean archivedNotRestored,
        // The native severity code carried by CARLOS.Allergy.Severity.v1 ("3", Severe), which
        // has no CDS 5.2 Severity value; when present the standard Severity is absent.
        @com.fasterxml.jackson.annotation.JsonInclude(
                        com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                String nativeSeverity) {
    public CdsAllergyDto {
        if (atcResidual
                && (!"ATC".equals(codeSystem)
                        || !io.github.carlos_emr.carlos.demographic.cds.contract
                                .CdsNativeExtensionContract.validAllergyAtc(code)))
            throw new IllegalArgumentException("CDS_ALLERGY_ATC_RESIDUAL_INVALID");
        if (archivedNotRestored && !archived)
            throw new IllegalArgumentException("CDS_ALLERGY_ARCHIVE_DECISION_INVALID");
        if (nativeSeverity != null
                && (severity != null
                        || !io.github.carlos_emr.carlos.demographic.cds.contract
                                .CdsNativeExtensionContract.ALLERGY_SEVERITY_VALUES
                                .containsKey(nativeSeverity)))
            throw new IllegalArgumentException("CDS_ALLERGY_NATIVE_SEVERITY_INVALID");
    }

    /** The marker and ATC provenance shape used before the archive option and severity carrier. */
    public CdsAllergyDto(
            String description,
            String property,
            String code,
            String codeSystem,
            String reactionType,
            String reaction,
            String severity,
            String lifeStage,
            String notes,
            CdsTemporalDto start,
            CdsTemporalDto recorded,
            boolean archived,
            String ageOfOnset,
            String onsetOfReaction,
            boolean atcResidual) {
        this(
                description,
                property,
                code,
                codeSystem,
                reactionType,
                reaction,
                severity,
                lifeStage,
                notes,
                start,
                recorded,
                archived,
                ageOfOnset,
                onsetOfReaction,
                atcResidual,
                false,
                null);
    }

    /** Whether the native allergy row is written (and stays) archived. */
    public boolean nativeArchived() {
        return archived && !archivedNotRestored;
    }

    /** This value with the import's archive policy applied (decision 9; default: keep active). */
    public CdsAllergyDto withArchiveRestoration(boolean restore) {
        return new CdsAllergyDto(
                description,
                property,
                code,
                codeSystem,
                reactionType,
                reaction,
                severity,
                lifeStage,
                notes,
                start,
                recorded,
                archived,
                ageOfOnset,
                onsetOfReaction,
                atcResidual,
                archived && !restore,
                nativeSeverity);
    }

    /** The optional marker provenance must not change old sealed native projections. */
    public CdsAllergyDto(
            String description,
            String property,
            String code,
            String codeSystem,
            String reactionType,
            String reaction,
            String severity,
            String lifeStage,
            String notes,
            CdsTemporalDto start,
            CdsTemporalDto recorded,
            boolean archived,
            String ageOfOnset,
            String onsetOfReaction) {
        this(
                description,
                property,
                code,
                codeSystem,
                reactionType,
                reaction,
                severity,
                lifeStage,
                notes,
                start,
                recorded,
                archived,
                ageOfOnset,
                onsetOfReaction,
                false);
    }

    /** Legacy prepared values and source records without a marker keep their existing behavior. */
    public CdsAllergyDto(
            String description,
            String property,
            String code,
            String codeSystem,
            String reactionType,
            String reaction,
            String severity,
            String lifeStage,
            String notes,
            CdsTemporalDto start,
            CdsTemporalDto recorded) {
        this(
                description,
                property,
                code,
                codeSystem,
                reactionType,
                reaction,
                severity,
                lifeStage,
                notes,
                start,
                recorded,
                false,
                null,
                null);
    }

    /** The archive-only prepared value retains its exact twelve-field/omitted-default shape. */
    public CdsAllergyDto(
            String description,
            String property,
            String code,
            String codeSystem,
            String reactionType,
            String reaction,
            String severity,
            String lifeStage,
            String notes,
            CdsTemporalDto start,
            CdsTemporalDto recorded,
            boolean archived) {
        this(
                description,
                property,
                code,
                codeSystem,
                reactionType,
                reaction,
                severity,
                lifeStage,
                notes,
                start,
                recorded,
                archived,
                null,
                null);
    }
}
