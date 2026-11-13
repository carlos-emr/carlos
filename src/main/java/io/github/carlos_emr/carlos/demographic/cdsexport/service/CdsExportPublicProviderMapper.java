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
        NOTE_PARTICIPANT("ParticipatingProviders"),
        NOTE_SIGNER("MyClinicalNotesContent"),
        REPORT_REVIEWER("ReportReviewed"),
        RESULT_REVIEWER("ResultReviewer"),
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
        // The schema type is only a six-character token, but the data dictionary defines the
        // OHIP billing number as six digits. Outside Ontario the same column holds another
        // jurisdiction's practitioner number (a five-character BC MSP number, for example), sites
        // use placeholders such as "0", and the Add/Edit Provider form accepts up to twenty
        // characters with spaces. None of these is an OHIP number, so none is asserted as one;
        // nothing is trimmed or truncated into one either. The value belongs to the provider
        // record, not to the patient's chart, and the physician's name is still exported.
        String ohip = null;
        String billing = state.raw("ohip_no");
        if (billing != null && billing.matches("[0-9]{6}")) {
            state.set(
                    "ohip_no",
                    Outcome.MAPPED,
                    target.path + "/OHIPPhysicianId",
                    "EXACT_NATIVE_TEXT");
            ohip = billing;
        } else if (billing != null && !billing.isEmpty())
            state.operational("ohip_no", "NON_OHIP_BILLING_NUMBER_NOT_ASSERTED");
        String cpso = null;
        // Other public registration authorities are lookup metadata, not Ontario CPSO assertions.
        state.operational("practitionerNoType", "CAPTURED_PUBLIC_REGISTRATION_AUTHORITY");
        String registration = state.raw("practitionerNo");
        boolean registered = registration != null && !registration.isEmpty();
        if (target == Target.PRIMARY_PHYSICIAN
                && "CPSO".equals(state.raw("practitionerNoType"))
                && registered
                && token(registration)) {
            cpso = registration;
            state.set(
                    "practitionerNo",
                    Outcome.MAPPED,
                    "PrimaryPhysician/PrimaryPhysicianCPSO",
                    "EXACT_NATIVE_TEXT");
            state.set(
                    "practitionerNoType",
                    Outcome.MAPPED,
                    "PrimaryPhysician/PrimaryPhysicianCPSO",
                    "EXPLICIT_CPSO_AUTHORITY");
        } else if (target == Target.PRIMARY_PHYSICIAN
                && "CPSO".equals(state.raw("practitionerNoType"))
                && registered)
            // A CPSO-typed number that is not a six-character token (spaces, more characters)
            // cannot be written as PrimaryPhysicianCPSO without changing it. It is a provider
            // record value, so it is not asserted rather than holding the physician's patients.
            state.operational("practitionerNo", "UNREPRESENTABLE_CPSO_NUMBER_NOT_ASSERTED");
        else if (target == Target.PRIMARY_PHYSICIAN
                && registered
                && (state.raw("practitionerNoType") == null
                        || state.raw("practitionerNoType").isBlank()))
            // A registration number with no recorded authority (provider rows created before the
            // type was recorded) may be the MRP's CPSO number or a BC MSP or nursing number. It
            // is not asserted as CPSO; it is provider-record lookup metadata, not chart content,
            // so it no longer holds every patient of that physician.
            state.operational("practitionerNo", "UNTYPED_REGISTRATION_NUMBER_NOT_ASSERTED");
        else state.operational("practitionerNo", "CAPTURED_PUBLIC_REGISTRATION_LOOKUP_METADATA");
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

    /** A value the six-character xs:token type carries exactly, with no normalization. */
    private static boolean token(String value) {
        return CdsExportNativeFieldAssessments.legal(value, 6)
                && value.equals(value.strip())
                && !value.contains("  ")
                && value.codePoints().noneMatch(Character::isISOControl);
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_PUBLIC_PROVIDER_CAPTURE_SCOPE_INVALID");
    }
}
