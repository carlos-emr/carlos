/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.dto;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCppExtensionProjection;
import java.time.LocalDate;
import java.util.List;

/** One native extension write, with the immutable identity of its prerequisite note unit. */
public record CdsHistoryExtensionNativeDto(String parentSection, long parentOrdinal,
        String parentDigest, String key, String value, LocalDate date, boolean apply,
        List<CdsClinicalDiagnosticDto> diagnostics) {
    public CdsHistoryExtensionNativeDto {
        diagnostics = List.copyOf(diagnostics);
        if (parentSection == null || !CdsCppExtensionProjection.SECTIONS.contains(parentSection) || parentOrdinal < 1
                || parentDigest == null || !parentDigest.matches("[0-9a-f]{64}")
                || key == null || !CdsCppExtensionProjection.DATE_KEYS.contains(key)
                        && !CdsCppExtensionProjection.VALUE_KEYS.contains(key)
                || value != null && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 16_777_215
                || date != null && (date.getYear() < 1 || date.getYear() > 9999))
            throw new IllegalArgumentException("CDS_HISTORY_EXTENSION_PREPARED_INVALID");
    }

    @Override public String toString() { return "CdsHistoryExtensionNativeDto[protected]"; }
}
