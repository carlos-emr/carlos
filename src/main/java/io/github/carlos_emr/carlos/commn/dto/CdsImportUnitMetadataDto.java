/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.dto;

/** Scalar ledger read model; native witnesses are deliberately absent. Protected metadata. */
public record CdsImportUnitMetadataDto(
        String patientKey,
        String unitKey,
        String category,
        long ordinal,
        String sourceDigest,
        String spoolLocation,
        String state,
        String targetReferences,
        String targetDigest) {
    @Override
    public String toString() {
        return "CdsImportUnitMetadataDto[protected]";
    }
}
