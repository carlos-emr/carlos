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

import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportEmailLexical;

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsHealthCardAuthority;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exact native rank-one pharmacy as PreferredPharmacy. A second or third preferred pharmacy, and
 * stored values PreferredPharmacy has no element for (Phone 2, Notes, service location, free-text
 * province, an over-long line), travel in the native demographic NewCategory record; removed
 * links are no longer in the chart and are not exported. Only tied first preferences, a dangling
 * link and text no XML document can hold still require review.
 */
public final class CdsExportPharmacyMapper {
    public record Result(
            int patient,
            long linkRows,
            long pharmacyRows,
            CdsExportPreferredPharmacyDto preferred,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        public Result {
            fields = Map.copyOf(fields);
            if (patient < 1
                    || linkRows < 0
                    || pharmacyRows < 0
                    || linkRows > 8192
                    || pharmacyRows > 8192
                    || fields.size()
                            != linkRows
                                            * CdsExportPatientTableReadSupport.Plan.PHARMACY_LINK
                                                    .columns()
                                                    .size()
                                    + pharmacyRows
                                            * CdsExportPatientTableReadSupport.Plan.PHARMACY
                                                    .columns()
                                                    .size())
                throw new IllegalArgumentException("CDS_EXPORT_PHARMACY_MAPPING_INVALID");
            var identities = new HashMap<String, Map<String, Set<String>>>();
            for (var key : fields.keySet()) {
                if (!Set.of("demographicPharmacy", "pharmacyInfo").contains(key.table()))
                    throw new IllegalArgumentException("CDS_EXPORT_PHARMACY_MAPPING_INVALID");
                identities
                        .computeIfAbsent(key.table(), ignored -> new HashMap<>())
                        .computeIfAbsent(key.recordKey(), ignored -> new HashSet<>())
                        .add(key.column());
            }
            for (var plan :
                    List.of(
                            CdsExportPatientTableReadSupport.Plan.PHARMACY_LINK,
                            CdsExportPatientTableReadSupport.Plan.PHARMACY)) {
                var rows = identities.getOrDefault(plan.table(), Map.of());
                if (rows.size()
                                != (plan == CdsExportPatientTableReadSupport.Plan.PHARMACY_LINK
                                        ? linkRows
                                        : pharmacyRows)
                        || rows.values().stream()
                                .anyMatch(columns -> !columns.equals(Set.copyOf(plan.columns()))))
                    throw new IllegalArgumentException("CDS_EXPORT_PHARMACY_MAPPING_INVALID");
            }
            // Removed links, additional preferences and a carried preferred pharmacy leave no
            // PreferredPharmacy; one is never invented without a link.
            if (preferred != null && linkRows == 0)
                throw new IllegalArgumentException("CDS_EXPORT_PHARMACY_MAPPING_INVALID");
        }

        public boolean nativeRepresentable() {
            return fields.values().stream()
                    .noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        }

        @Override
        public String toString() {
            return "CdsExportPharmacyMapper.Result[protected]";
        }
    }

    public Result map(
            int patient,
            List<CdsExportCapturedTextRowDto> links,
            List<CdsExportCapturedTextRowDto> pharmacies,
            int maximumRows,
            long maximumBytes,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(checkpoint);
        checkpoint.run();
        if (patient < 1
                || links == null
                || pharmacies == null
                || maximumRows < 1
                || maximumRows > 8192
                || maximumBytes < 1
                || maximumBytes > (64L << 20))
            throw new IllegalArgumentException("CDS_EXPORT_PHARMACY_LIMITS_INVALID");
        if ((long) links.size() + pharmacies.size() > maximumRows) limit();
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var lookup = new HashMap<String, CdsExportCapturedTextRowDto>();
        var all = new ArrayList<CdsExportCapturedTextRowDto>();
        var states = new HashMap<CdsExportCapturedTextRowDto, CdsExportNativeFieldAssessments>();
        var referenced = new HashSet<String>();
        var keys = new HashSet<String>();
        long bytes = 0;
        for (var row : pharmacies) {
            checkpoint.run();
            requireRow(row, CdsExportPatientTableReadSupport.Plan.PHARMACY, "recordID");
            if (lookup.putIfAbsent(row.fields().get("recordID"), row) != null) scope();
            all.add(row);
        }
        CdsExportCapturedTextRowDto candidate = null;
        int preferredCount = 0;
        for (var row : links) {
            checkpoint.run();
            requireRow(row, CdsExportPatientTableReadSupport.Plan.PHARMACY_LINK, "id");
            if (!Integer.toString(patient).equals(row.fields().get("demographic_no"))
                    || !keys.add(row.recordKey())) scope();
            all.add(row);
            String reference = row.fields().get("pharmacyID");
            canonical(reference);
            referenced.add(reference);
            if ("1".equals(row.fields().get("status"))
                    && "1".equals(row.fields().get("preferredOrder"))) {
                candidate = row;
                preferredCount++;
            }
        }
        if (!referenced.containsAll(lookup.keySet())) scope();
        for (var row : all) {
            checkpoint.run();
            bytes = Math.addExact(bytes, 2048L);
            for (String value : row.fields().values())
                if (value != null)
                    bytes =
                            Math.addExact(
                                    bytes, value.getBytes(StandardCharsets.UTF_8).length + 32L);
            if (bytes > maximumBytes) limit();
            var state = new CdsExportNativeFieldAssessments(row, fields);
            states.put(row, state);
            for (String column :
                    row.table().equals("pharmacyInfo")
                            ? List.of("recordID", "addDate", "uid")
                            : List.of("id", "demographic_no", "addDate"))
                state.operational(column, "CAPTURED_NATIVE_PHARMACY_IDENTITY_OR_METADATA");
            if (row.table().equals("demographicPharmacy"))
                CdsExportContactConsent.assess(state, "PreferredPharmacy");
        }
        // Pharmacies named only by removed links, and those an additional active link prefers.
        var removedOnly = new HashSet<String>();
        var additional = new HashSet<String>();
        for (var row : links)
            if ("0".equals(row.fields().get("status")))
                removedOnly.add(row.fields().get("pharmacyID"));
        for (var row : links)
            if ("1".equals(row.fields().get("status"))) {
                removedOnly.remove(row.fields().get("pharmacyID"));
                if (!(preferredCount == 1 && row == candidate))
                    additional.add(row.fields().get("pharmacyID"));
            }
        for (var row : links) {
            var state = states.get(row);
            boolean selected = preferredCount == 1 && row == candidate;
            if (!lookup.containsKey(row.fields().get("pharmacyID")))
                state.review(
                        "pharmacyID",
                        "PreferredPharmacy",
                        "LINKED_PHARMACY_MISSING_REQUIRES_REVIEW");
            else if (!"0".equals(row.fields().get("status"))
                    && !"1".equals(row.fields().get("status")))
                state.review(
                        "status",
                        "PreferredPharmacy",
                        "UNKNOWN_PHARMACY_ASSOCIATION_STATUS_REQUIRES_REVIEW");
            else if ("0".equals(row.fields().get("status"))) {
                // The Rx screen's "remove" keeps the link with status 0: it is no longer in the
                // chart, like a superseded demographic snapshot, and is not exported.
                for (String column : row.fields().keySet())
                    if (present(row.fields().get(column)))
                        state.operational(column, "REMOVED_PHARMACY_ASSOCIATION_NOT_IN_CURRENT_CHART");
            } else if (preferredCount <= 1 && !selected) {
                // A second or third preferred pharmacy: PreferredPharmacy holds one, so the link
                // and its pharmacy travel in the native demographic NewCategory record.
                for (String column : List.of("pharmacyID", "preferredOrder", "status", "consentToContact"))
                    if (present(row.fields().get(column))) state.carry(column);
            } else if (selected) {
                state.set(
                        "pharmacyID",
                        Outcome.MAPPED,
                        "PreferredPharmacy",
                        "EXACT_PATIENT_LINK_TO_RANK_ONE_PHARMACY");
                state.set(
                        "preferredOrder",
                        Outcome.MAPPED,
                        "PreferredPharmacy",
                        "EXACT_NATIVE_FIRST_PREFERENCE");
                state.operational("status", "EXACT_ACTIVE_PHARMACY_ASSOCIATION");
            } else {
                state.review(
                        "status",
                        "PreferredPharmacy",
                        "ADDITIONAL_OR_HISTORICAL_PHARMACY_ASSOCIATION_REQUIRES_REVIEW");
                state.review(
                        "preferredOrder",
                        "PreferredPharmacy",
                        preferredCount > 1
                                ? "DUPLICATE_FIRST_PHARMACY_PREFERENCE_REQUIRES_REVIEW"
                                : "PHARMACY_PREFERENCE_REQUIRES_REVIEW");
            }
        }
        CdsExportPreferredPharmacyDto preferred = null;
        String preferredId = null;
        if (preferredCount == 1) {
            preferredId = candidate.fields().get("pharmacyID");
            var row = lookup.get(preferredId);
            if (row != null) {
                preferred = preferred(row, states.get(row));
                // A preferred pharmacy whose name the element cannot hold travels whole.
                if (preferred == null) {
                    states.get(row).carryRemaining(Set.of("recordID", "addDate", "uid"));
                    var link = states.get(candidate);
                    for (String column : List.of("pharmacyID", "preferredOrder", "status", "consentToContact"))
                        if (present(candidate.fields().get(column))) link.carry(column);
                }
            }
        }
        for (var entry : lookup.entrySet()) {
            if (entry.getKey().equals(preferredId)) continue;
            var state = states.get(entry.getValue());
            if (removedOnly.contains(entry.getKey()))
                for (var column : entry.getValue().fields().keySet()) {
                    if (present(entry.getValue().fields().get(column)))
                        state.operational(column, "PHARMACY_OF_A_REMOVED_ASSOCIATION_NOT_IN_CURRENT_CHART");
                }
            else if (additional.contains(entry.getKey()))
                state.carryRemaining(Set.of("recordID", "addDate", "uid"));
        }
        new CdsExportNativeFieldAssessmentValidator().validate(all, fields, 1_000_000, checkpoint);
        checkpoint.run();
        return new Result(patient, links.size(), pharmacies.size(), preferred, fields);
    }

    private static CdsExportPreferredPharmacyDto preferred(
            CdsExportCapturedTextRowDto row, CdsExportNativeFieldAssessments state) {
        String root = "PreferredPharmacy";
        // Manage Pharmacy "delete" marks a pharmacy that patients may still prefer: the chart still
        // shows it, so it is exported and its stored status travels in the native record.
        if (!"1".equals(state.raw("status"))) {
            if (present(state.raw("status"))) state.carry("status");
        } else state.operational("status", "EXACT_ACTIVE_NATIVE_PHARMACY");
        String name = state.text("name", root + "/Name", 50);
        if (name == null) {
            // A longer chain-store name: the whole pharmacy travels in the native record.
            if (present(state.raw("name"))
                    && CdsExportNativeFieldAssessments.legal(state.raw("name"), Integer.MAX_VALUE))
                state.carry("name");
            if (!present(state.raw("name")))
                state.review(
                        "name",
                        root + "/Name",
                        "REQUIRED_PHARMACY_NAME_MISSING_OR_UNREPRESENTABLE");
            return null;
        }
        String line = state.token("address", root + "/Address/Structured/Line1", 50);
        String city = state.token("city", root + "/Address/Structured/City", 80);
        String subdivision = state.raw("province");
        if (present(subdivision)) {
            if (CdsHealthCardAuthority.CANADIAN.contains(subdivision)) {
                subdivision = "CA-" + subdivision;
                state.set(
                        "province",
                        Outcome.MAPPED,
                        root + "/Address/Structured/CountrySubdivisionCode",
                        "CANADIAN_SUBDIVISION_CODE");
            } else if (subdivision.startsWith("CA-")
                            && CdsHealthCardAuthority.CANADIAN.contains(subdivision.substring(3))
                    || CdsHealthCardAuthority.PINNED_US.contains(subdivision))
                state.set(
                        "province",
                        Outcome.MAPPED,
                        root + "/Address/Structured/CountrySubdivisionCode",
                        "EXACT_SUBDIVISION_CODE");
            else {
                // Manage Pharmacy's Province box is free text ("Ontario", "Ont."): the optional
                // subdivision is left out and the stored text travels in the native record.
                state.carry("province");
                subdivision = null;
            }
        } else subdivision = null;
        String postal = state.token("postalCode", root + "/Address/Structured/PostalZipCode", 10);
        if (postal != null && subdivision == null) {
            state.carry("postalCode");
            postal = null;
        }
        if (postal != null) {
            boolean zip = subdivision != null && subdivision.startsWith("US-");
            if (!zip
                    && subdivision != null
                    && subdivision.startsWith("CA-")
                    && postal.matches("[A-Za-z][0-9][A-Za-z] [0-9][A-Za-z][0-9]")) {
                boolean collapsed = !postal.equals(state.raw("postalCode"));
                postal = postal.substring(0, 3) + postal.substring(4);
                state.set(
                        "postalCode",
                        Outcome.MAPPED,
                        root
                                + "/Address/Structured/PostalZipCode/PostalCode"
                                + (collapsed
                                        ? "," + CdsExportDemographicSupportingMapper.DESTINATION
                                        : ""),
                        "CANADIAN_POSTAL_DISPLAY_SEPARATOR");
            }
            if (!postal.matches(zip ? "[0-9-]*" : "[a-zA-Z0-9-]*")) {
                state.carry("postalCode");
                postal = null;
            }
        }
        CdsExportPreferredPharmacyDto.Address address =
                line == null && city == null && subdivision == null && postal == null
                        ? null
                        : new CdsExportPreferredPharmacyDto.Address(
                                line, city, subdivision, postal);
        var phone = phone(state, "phone1", root + "/PhoneNumber");
        var fax = phone(state, "fax", root + "/FaxNumber");
        String email = state.token("email", root + "/EmailAddress", 50);
        if (email != null && !CdsExportEmailLexical.matches(email)) {
            state.carry("email");
            email = null;
        }
        // Phone 2, Notes and the service location have no PreferredPharmacy element.
        for (String column : List.of("phone2", "notes", "serviceLocationIdentifier"))
            if (present(state.raw(column))) state.carry(column);
        return name == null
                ? null
                : new CdsExportPreferredPharmacyDto(name, address, phone, fax, email);
    }

    private static CdsExportDemographicMappingDto.Phone phone(
            CdsExportNativeFieldAssessments state, String column, String path) {
        String value = state.token(column, path + "/phoneNumber", 25);
        return value == null ? null : new CdsExportDemographicMappingDto.Phone("W", value);
    }

    private static void requireRow(
            CdsExportCapturedTextRowDto row, CdsExportPatientTableReadSupport.Plan plan, String key)
            throws IOException {
        if (row == null
                || !row.table().equals(plan.table())
                || !row.fields().keySet().equals(Set.copyOf(plan.columns()))
                || !row.exactNumbers().isEmpty()
                || !CdsExportPatientTableSourceReader.key(canonical(row.fields().get(key)))
                        .equals(row.recordKey())) scope();
    }

    private static long canonical(String value) throws IOException {
        try {
            long parsed = Long.parseLong(value);
            if (!Long.toString(parsed).equals(value)) scope();
            return parsed;
        } catch (NumberFormatException | NullPointerException malformed) {
            scope();
            return 0;
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isEmpty();
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_PHARMACY_SCOPE_INVALID");
    }

    private static void limit() throws IOException {
        throw new IOException("CDS_EXPORT_PHARMACY_RESOURCE_LIMIT");
    }
}
