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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsHealthCardAuthority;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact native rank-one pharmacy. Other preferences, history and non-schema source values remain explicit review. */
public final class CdsExportPharmacyMapper {
    public record Result(int patient, long linkRows, long pharmacyRows, CdsExportPreferredPharmacyDto preferred,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        public Result {
            fields = Map.copyOf(fields);
            if (patient < 1 || linkRows < 0 || pharmacyRows < 0 || linkRows > 8192 || pharmacyRows > 8192
                    || fields.size() != linkRows * CdsExportPatientTableReadSupport.Plan.PHARMACY_LINK.columns().size()
                            + pharmacyRows * CdsExportPatientTableReadSupport.Plan.PHARMACY.columns().size())
                throw new IllegalArgumentException("CDS_EXPORT_PHARMACY_MAPPING_INVALID");
            var identities = new HashMap<String, Map<String, Set<String>>>();
            for (var key : fields.keySet()) {
                if (!Set.of("demographicPharmacy", "pharmacyInfo").contains(key.table())) throw new IllegalArgumentException("CDS_EXPORT_PHARMACY_MAPPING_INVALID");
                identities.computeIfAbsent(key.table(), ignored -> new HashMap<>()).computeIfAbsent(key.recordKey(), ignored -> new HashSet<>()).add(key.column());
            }
            for (var plan : List.of(CdsExportPatientTableReadSupport.Plan.PHARMACY_LINK, CdsExportPatientTableReadSupport.Plan.PHARMACY)) {
                var rows = identities.getOrDefault(plan.table(), Map.of());
                if (rows.size() != (plan == CdsExportPatientTableReadSupport.Plan.PHARMACY_LINK ? linkRows : pharmacyRows)
                        || rows.values().stream().anyMatch(columns -> !columns.equals(Set.copyOf(plan.columns())))) throw new IllegalArgumentException("CDS_EXPORT_PHARMACY_MAPPING_INVALID");
            }
            if (fields.values().stream().noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW) && (linkRows > 0) != (preferred != null))
                throw new IllegalArgumentException("CDS_EXPORT_PHARMACY_MAPPING_INVALID");
        }
        public boolean nativeRepresentable() { return fields.values().stream().noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW); }
        @Override public String toString() { return "CdsExportPharmacyMapper.Result[protected]"; }
    }
    public Result map(int patient, List<CdsExportCapturedTextRowDto> links, List<CdsExportCapturedTextRowDto> pharmacies,
            int maximumRows, long maximumBytes, Runnable checkpoint) throws IOException {
        Objects.requireNonNull(checkpoint); checkpoint.run();
        if (patient < 1 || links == null || pharmacies == null || maximumRows < 1 || maximumRows > 8192 || maximumBytes < 1 || maximumBytes > (64L << 20))
            throw new IllegalArgumentException("CDS_EXPORT_PHARMACY_LIMITS_INVALID");
        if ((long) links.size() + pharmacies.size() > maximumRows) limit();
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var lookup = new HashMap<String, CdsExportCapturedTextRowDto>(); var all = new ArrayList<CdsExportCapturedTextRowDto>();
        var states = new HashMap<CdsExportCapturedTextRowDto, CdsExportNativeFieldAssessments>();
        var referenced = new HashSet<String>(); var keys = new HashSet<String>(); long bytes = 0;
        for (var row : pharmacies) {
            checkpoint.run(); requireRow(row, CdsExportPatientTableReadSupport.Plan.PHARMACY, "recordID");
            if (lookup.putIfAbsent(row.fields().get("recordID"), row) != null) scope(); all.add(row);
        }
        CdsExportCapturedTextRowDto candidate = null; int preferredCount = 0;
        for (var row : links) {
            checkpoint.run(); requireRow(row, CdsExportPatientTableReadSupport.Plan.PHARMACY_LINK, "id");
            if (!Integer.toString(patient).equals(row.fields().get("demographic_no")) || !keys.add(row.recordKey())) scope();
            all.add(row); String reference = row.fields().get("pharmacyID"); canonical(reference); referenced.add(reference);
            if ("1".equals(row.fields().get("status")) && "1".equals(row.fields().get("preferredOrder"))) { candidate = row; preferredCount++; }
        }
        if (!referenced.containsAll(lookup.keySet())) scope();
        for (var row : all) {
            checkpoint.run(); bytes = Math.addExact(bytes, 2048L);
            for (String value : row.fields().values()) if (value != null) bytes = Math.addExact(bytes, value.getBytes(StandardCharsets.UTF_8).length + 32L);
            if (bytes > maximumBytes) limit();
            var state = new CdsExportNativeFieldAssessments(row, fields); states.put(row, state);
            for (String column : row.table().equals("pharmacyInfo") ? List.of("recordID", "addDate", "uid") : List.of("id", "demographic_no", "addDate"))
                state.operational(column, "CAPTURED_NATIVE_PHARMACY_IDENTITY_OR_METADATA");
        }
        for (var row : links) {
            var state = states.get(row);
            boolean selected = preferredCount == 1 && row == candidate;
            if (!lookup.containsKey(row.fields().get("pharmacyID"))) state.review("pharmacyID", "PreferredPharmacy", "LINKED_PHARMACY_MISSING_REQUIRES_REVIEW");
            else if (selected) {
                state.set("pharmacyID", Outcome.MAPPED, "PreferredPharmacy", "EXACT_PATIENT_LINK_TO_RANK_ONE_PHARMACY");
                state.set("preferredOrder", Outcome.MAPPED, "PreferredPharmacy", "EXACT_NATIVE_FIRST_PREFERENCE");
                state.operational("status", "EXACT_ACTIVE_PHARMACY_ASSOCIATION");
            } else {
                state.review("status", "PreferredPharmacy", "ADDITIONAL_OR_HISTORICAL_PHARMACY_ASSOCIATION_REQUIRES_REVIEW");
                state.review("preferredOrder", "PreferredPharmacy", preferredCount > 1 ? "DUPLICATE_FIRST_PHARMACY_PREFERENCE_REQUIRES_REVIEW" : "PHARMACY_PREFERENCE_REQUIRES_REVIEW");
            }
        }
        CdsExportPreferredPharmacyDto preferred = null;
        if (preferredCount == 1) {
            var row = lookup.get(candidate.fields().get("pharmacyID"));
            if (row != null) preferred = preferred(row, states.get(row));
        }
        new CdsExportNativeFieldAssessmentValidator().validate(all, fields, 1_000_000, checkpoint);
        checkpoint.run(); return new Result(patient, links.size(), pharmacies.size(), preferred, fields);
    }
    private static CdsExportPreferredPharmacyDto preferred(CdsExportCapturedTextRowDto row,
            CdsExportNativeFieldAssessments state) {
        String root = "PreferredPharmacy";
        if (!"1".equals(state.raw("status"))) state.review("status", root, "DELETED_OR_UNKNOWN_PHARMACY_REQUIRES_REVIEW");
        else state.operational("status", "EXACT_ACTIVE_NATIVE_PHARMACY");
        String name = state.text("name", root + "/Name", 50);
        if (name == null) state.review("name", root + "/Name", "REQUIRED_PHARMACY_NAME_MISSING_OR_UNREPRESENTABLE");
        String line = token(state, "address", root + "/Address/Structured/Line1", 50);
        String city = token(state, "city", root + "/Address/Structured/City", 80);
        String subdivision = state.raw("province");
        if (present(subdivision)) {
            if (CdsHealthCardAuthority.CANADIAN.contains(subdivision)) {
                subdivision = "CA-" + subdivision; state.set("province", Outcome.MAPPED, root + "/Address/Structured/CountrySubdivisionCode", "CANADIAN_SUBDIVISION_CODE");
            } else if (subdivision.startsWith("CA-") && CdsHealthCardAuthority.CANADIAN.contains(subdivision.substring(3)) || CdsHealthCardAuthority.PINNED_US.contains(subdivision))
                state.set("province", Outcome.MAPPED, root + "/Address/Structured/CountrySubdivisionCode", "EXACT_SUBDIVISION_CODE");
            else { state.review("province", root + "/Address/Structured/CountrySubdivisionCode", "SUBDIVISION_REQUIRES_REVIEW"); subdivision = null; }
        } else subdivision = null;
        String postal = token(state, "postalCode", root + "/Address/Structured/PostalZipCode", 10);
        if (postal != null && subdivision == null) {
            state.review("postalCode", root + "/Address/Structured/PostalZipCode", "POSTAL_JURISDICTION_REQUIRES_REVIEW"); postal = null;
        }
        if (postal != null) {
            boolean zip = subdivision != null && subdivision.startsWith("US-");
            if (!zip && subdivision != null && subdivision.startsWith("CA-") && postal.matches("[A-Za-z][0-9][A-Za-z] [0-9][A-Za-z][0-9]")) {
                postal = postal.substring(0, 3) + postal.substring(4); state.set("postalCode", Outcome.MAPPED, root + "/Address/Structured/PostalZipCode/PostalCode", "CANADIAN_POSTAL_DISPLAY_SEPARATOR");
            }
            if (!postal.matches(zip ? "[0-9-]*" : "[a-zA-Z0-9-]*")) { state.review("postalCode", root + "/Address/Structured/PostalZipCode", "POSTAL_REPRESENTATION_REQUIRES_REVIEW"); postal = null; }
        }
        CdsExportPreferredPharmacyDto.Address address = line == null && city == null && subdivision == null && postal == null ? null : new CdsExportPreferredPharmacyDto.Address(line, city, subdivision, postal);
        var phone = phone(state, "phone1", root + "/PhoneNumber"); var fax = phone(state, "fax", root + "/FaxNumber");
        String email = token(state, "email", root + "/EmailAddress", 50);
        if (email != null && !email.matches("([.a-zA-Z0-9_-])+@([a-zA-Z0-9_-])+(([a-zA-Z0-9_-])*\\.([a-zA-Z0-9_-])+)+")) { state.review("email", root + "/EmailAddress", "WIRE_EMAIL_REQUIRES_REVIEW"); email = null; }
        return name == null ? null : new CdsExportPreferredPharmacyDto(name, address, phone, fax, email);
    }
    private static CdsExportDemographicMappingDto.Phone phone(CdsExportNativeFieldAssessments state, String column, String path) {
        String value = token(state, column, path + "/phoneNumber", 25); return value == null ? null : new CdsExportDemographicMappingDto.Phone("W", value);
    }
    private static String token(CdsExportNativeFieldAssessments state, String column, String path, int maximum) {
        String value = state.raw(column); if (!present(value)) return null;
        if (value.startsWith(" ") || value.endsWith(" ") || value.contains("  ") || value.codePoints().anyMatch(code -> code == '\t' || code == '\r' || code == '\n')) {
            state.review(column, path, "TOKEN_WHITESPACE_REQUIRES_REVIEW"); return null;
        }
        return state.text(column, path, maximum);
    }
    private static void requireRow(CdsExportCapturedTextRowDto row, CdsExportPatientTableReadSupport.Plan plan, String key) throws IOException {
        if (row == null || !row.table().equals(plan.table()) || !row.fields().keySet().equals(Set.copyOf(plan.columns())) || !row.exactNumbers().isEmpty()
                || !CdsExportPatientTableSourceReader.key(canonical(row.fields().get(key))).equals(row.recordKey())) scope();
    }
    private static long canonical(String value) throws IOException {
        try { long parsed = Long.parseLong(value); if (!Long.toString(parsed).equals(value)) scope(); return parsed; }
        catch (NumberFormatException | NullPointerException malformed) { scope(); return 0; }
    }
    private static boolean present(String value) { return value != null && !value.isEmpty(); }
    private static void scope() throws IOException { throw new IOException("CDS_EXPORT_PHARMACY_SCOPE_INVALID"); }
    private static void limit() throws IOException { throw new IOException("CDS_EXPORT_PHARMACY_RESOURCE_LIMIT"); }
}
