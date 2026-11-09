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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.io.IOException;
import java.util.LinkedHashMap;

/**
 * Shared exact public-name/OHIP interpretation. The source policy must establish the
 * authority of ohip_no before clinical publication. A billing number or a local provider
 * ID never substitutes for it. Only an explicit CPSO registration populates the CPSO field.
 */
public final class CdsExportPublicProviderMapper {
    public enum Target {
        APPOINTMENT("Provider"),
        PRESCRIBER("PrescribedBy"),
        PRIMARY_PHYSICIAN("PrimaryPhysician"),
        ENROLLED_PHYSICIAN("Enrolment/EnrolmentHistory/EnrolledToPhysician");
        private final String path;

        Target(String path) {
            this.path = path;
        }
    }

    public CdsExportPublicProviderMappingDto map(CdsExportCapturedTextRowDto row, Target target)
            throws IOException {
        if (row == null
                || target == null
                || !row.table().equals("provider")
                || !row.fields().keySet().equals(CdsExportProviderRowDto.COLUMNS)) scope();
        String id = row.fields().get("provider_no");
        if (id == null || !CdsExportPublicProviderSourceReader.key(id).equals(row.recordKey()))
            scope();
        var decisions =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var state = new CdsExportNativeFieldAssessments(row, decisions);
        state.set("provider_no", Outcome.MAPPED, target.path, "EXACT_NATIVE_PROVIDER_REFERENCE");
        String first = state.text("first_name", target.path + "/Name/FirstName", 50);
        String last = state.text("last_name", target.path + "/Name/LastName", 50);
        if ("".equals(state.raw("first_name"))) first = "";
        if ("".equals(state.raw("last_name"))) last = "";
        String ohip = token(state, "ohip_no", target.path + "/OHIPPhysicianId");
        String cpso = null;
        // Other public registration authorities are lookup metadata, not Ontario CPSO assertions.
        state.operational("practitionerNoType", "CAPTURED_PUBLIC_REGISTRATION_AUTHORITY");
        if (target == Target.PRIMARY_PHYSICIAN && "CPSO".equals(state.raw("practitionerNoType"))) {
            cpso = token(state, "practitionerNo", "PrimaryPhysician/PrimaryPhysicianCPSO");
            if (cpso != null)
                state.set(
                        "practitionerNoType",
                        Outcome.MAPPED,
                        "PrimaryPhysician/PrimaryPhysicianCPSO",
                        "EXPLICIT_CPSO_AUTHORITY");
        } else state.operational("practitionerNo", "CAPTURED_PUBLIC_REGISTRATION_LOOKUP_METADATA");
        CdsExportPublicProviderDto value = null;
        try {
            value = new CdsExportPublicProviderDto(first, last, ohip, cpso);
        } catch (IllegalArgumentException review) {
            state.review("provider_no", target.path, "PUBLIC_PROVIDER_VALUES_REQUIRES_REVIEW");
        }
        if (decisions.values().stream()
                .anyMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW)) value = null;
        return new CdsExportPublicProviderMappingDto(value, decisions);
    }

    private static String token(
            CdsExportNativeFieldAssessments state, String column, String destination) {
        String value = state.text(column, destination, 6);
        if (value != null
                && (value.startsWith(" ")
                        || value.endsWith(" ")
                        || value.contains("  ")
                        || value.codePoints().anyMatch(Character::isISOControl))) {
            state.review(column, destination, "TOKEN_NORMALIZATION_REQUIRES_REVIEW");
            return null;
        }
        return value;
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_PUBLIC_PROVIDER_CAPTURE_SCOPE_INVALID");
    }
}
