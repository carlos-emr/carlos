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
package io.github.carlos_emr.carlos.commn.dto;

import java.util.Objects;

/**
 * One public code-table row that a patient's Disease Registry entry or episode names. The
 * {@code codingSystem} and {@code code} are the patient row's exact stored values (the lookup
 * key); the reference columns are the matching code-table row, which the chart's registry list
 * joins under the column's own collation. A code with no matching row has no reference DTO.
 *
 * @param codingSystem the patient row's stored coding system
 * @param code the patient row's stored code
 * @param referenceTable the allowlisted public code table that was read
 * @param referenceId that table's primary-key value for the matching row
 * @param referenceCode that row's own code value
 * @param description that row's description; {@code null} when the table stores SQL NULL
 * @since 2026-11-13
 */
public record CdsExportDiagnosisCodeReferenceDto(
        String codingSystem,
        String code,
        String referenceTable,
        String referenceId,
        String referenceCode,
        String description) {
    public CdsExportDiagnosisCodeReferenceDto {
        Objects.requireNonNull(codingSystem);
        Objects.requireNonNull(code);
        Objects.requireNonNull(referenceTable);
        Objects.requireNonNull(referenceId);
        Objects.requireNonNull(referenceCode);
        if (codingSystem.isEmpty()
                || codingSystem.length() > 64
                || code.isEmpty()
                || code.length() > 64
                || !referenceTable.matches("[a-z0-9_]{1,64}")
                || referenceId.isEmpty()
                || referenceId.length() > 64
                || referenceCode.length() > 64
                || description != null && description.length() > 65_536)
            throw new IllegalArgumentException("CDS_EXPORT_DIAGNOSIS_REFERENCE_INVALID");
    }

    @Override
    public String toString() {
        return "CdsExportDiagnosisCodeReferenceDto[protected]";
    }
}
