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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import java.util.List;

/**
 * The prepared native clinical note: its whole content, or the documented placeholder when the
 * note's prepared record does not fit its column; oversized source text is never clipped into a
 * false statement. The record's size is held by that column ({@code cds_import_preparation}),
 * not by a length here.
 *
 * @param issueCode the CPP section tag of the record's own section, or null for an encounter note
 * @param additionalIssueCodes the other CPP section tags a CARLOS sender filed the item under
 *     ({@link CdsNativeExtensionContract#HISTORY_SECTIONS}), each one the receiving catalogue
 *     holds, in CDS PatientRecord order; the persister links the item to each of them as well.
 *     Empty for an item of one section, and omitted from the stored payload then, so a payload
 *     sealed before this component existed reads as empty
 *     ({@code CdsPreparedHistoryDefaults#applyNative})
 */
public record CdsHistoryNativeDto(
        String content,
        String issueCode,
        Long observationMillis,
        Long createdMillis,
        String providerNo,
        CdsTemporalDto observationDate,
        String observationField,
        List<CdsClinicalDiagnosticDto> diagnostics,
        @com.fasterxml.jackson.annotation.JsonInclude(
                        com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
                boolean archived,
        @com.fasterxml.jackson.annotation.JsonInclude(
                        com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
                List<String> additionalIssueCodes) {
    public CdsHistoryNativeDto(
            String content,
            String issueCode,
            Long observationMillis,
            Long createdMillis,
            String providerNo,
            CdsTemporalDto observationDate,
            String observationField,
            List<CdsClinicalDiagnosticDto> diagnostics,
            boolean archived) {
        this(
                content,
                issueCode,
                observationMillis,
                createdMillis,
                providerNo,
                observationDate,
                observationField,
                diagnostics,
                archived,
                List.of());
    }

    public CdsHistoryNativeDto(
            String content,
            String issueCode,
            Long observationMillis,
            Long createdMillis,
            String providerNo,
            CdsTemporalDto observationDate,
            String observationField,
            List<CdsClinicalDiagnosticDto> diagnostics) {
        this(
                content,
                issueCode,
                observationMillis,
                createdMillis,
                providerNo,
                observationDate,
                observationField,
                diagnostics,
                false);
    }

    public CdsHistoryNativeDto {
        diagnostics = List.copyOf(diagnostics);
        additionalIssueCodes =
                additionalIssueCodes == null ? List.of() : List.copyOf(additionalIssueCodes);
        if (content == null || providerNo != null && providerNo.length() > 6)
            throw new IllegalArgumentException("HISTORY_PREPARED_TEXT_INVALID");
        // Only a CPP item has other sections: each a section tag other than its own, once, in
        // the contract's order.
        if (!additionalIssueCodes.isEmpty()
                && (issueCode == null
                        || !CdsNativeExtensionContract.HISTORY_SECTION_TAGS.contains(issueCode)
                        || additionalIssueCodes.contains(issueCode)
                        || !additionalIssueCodes.equals(
                                CdsNativeExtensionContract.HISTORY_SECTION_TAGS.stream()
                                        .filter(additionalIssueCodes::contains)
                                        .toList())))
            throw new IllegalArgumentException("HISTORY_PREPARED_SECTIONS_INVALID");
    }

    @Override
    public String toString() {
        return "CdsHistoryNativeDto[protected]";
    }
}
