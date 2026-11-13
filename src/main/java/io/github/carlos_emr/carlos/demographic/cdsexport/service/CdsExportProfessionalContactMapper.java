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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact external family/referring relationships. Other contact details require their own supporting-data projection. */
public final class CdsExportProfessionalContactMapper {
    public enum Role {
        REFERRING("Referring Doctor", "ReferredPhysician"),
        FAMILY("Family Doctor", "FamilyPhysician");
        private final String label, wire;

        Role(String label, String wire) {
            this.label = label;
            this.wire = wire;
        }

        public String wireName() {
            return wire;
        }

        public String nativeLabel() {
            return label;
        }
    }

    public record Physician(
            Role role,
            String associationRecordKey,
            String contactRecordKey,
            CdsExportPublicProviderDto name) {
        public Physician {
            Objects.requireNonNull(role);
            Objects.requireNonNull(associationRecordKey);
            Objects.requireNonNull(contactRecordKey);
            Objects.requireNonNull(name);
            if (name.ohipNumber() != null || name.cpsoNumber() != null)
                throw new IllegalArgumentException(
                        "CDS_EXPORT_CONTEXT_PHYSICIAN_IDENTIFIER_INVALID");
        }

        @Override
        public String toString() {
            return "CdsExportProfessionalContactMapper.Physician[protected]";
        }
    }

    public record Result(
            int patient,
            long associationRows,
            long contactRows,
            List<Physician> physicians,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            Map<CdsExportNativeFieldKeyDto, Set<Role>> nameUses) {
        public Result {
            physicians = List.copyOf(physicians);
            fields = Map.copyOf(fields);
            var uses = new LinkedHashMap<CdsExportNativeFieldKeyDto, Set<Role>>();
            nameUses.forEach((key, value) -> uses.put(key, Set.copyOf(value)));
            nameUses = Map.copyOf(uses);
            if (patient < 1
                    || associationRows < 0
                    || contactRows < 0
                    || associationRows + contactRows > 8192
                    || physicians.stream().map(Physician::role).distinct().count()
                            != physicians.size()
                    || fields.size()
                            != associationRows
                                            * CdsExportPatientTableReadSupport.Plan
                                                    .DEMOGRAPHIC_CONTACT
                                                    .columns()
                                                    .size()
                                    + contactRows
                                            * CdsExportPatientTableReadSupport.Plan.CONTACT
                                                    .columns()
                                                    .size()
                    || !fields.keySet().containsAll(nameUses.keySet()))
                throw new IllegalArgumentException(
                        "CDS_EXPORT_PROFESSIONAL_CONTACT_MAPPING_INVALID");
            var identities = new HashMap<String, Map<String, Set<String>>>();
            for (var key : fields.keySet()) {
                if (!Set.of("Contact", "DemographicContact").contains(key.table()))
                    throw new IllegalArgumentException(
                            "CDS_EXPORT_PROFESSIONAL_CONTACT_MAPPING_INVALID");
                identities
                        .computeIfAbsent(key.table(), ignored -> new HashMap<>())
                        .computeIfAbsent(key.recordKey(), ignored -> new HashSet<>())
                        .add(key.column());
            }
            for (var plan :
                    List.of(
                            CdsExportPatientTableReadSupport.Plan.CONTACT,
                            CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_CONTACT)) {
                var rows = identities.getOrDefault(plan.table(), Map.of());
                if (rows.size()
                                != (plan == CdsExportPatientTableReadSupport.Plan.CONTACT
                                        ? contactRows
                                        : associationRows)
                        || rows.values().stream()
                                .anyMatch(columns -> !columns.equals(Set.copyOf(plan.columns()))))
                    throw new IllegalArgumentException(
                            "CDS_EXPORT_PROFESSIONAL_CONTACT_MAPPING_INVALID");
            }
            var emittedPhysicians = physicians;
            for (var entry : nameUses.entrySet())
                if (!entry.getKey().table().equals("Contact")
                        || !Set.of("firstName", "lastName").contains(entry.getKey().column())
                        || entry.getValue().isEmpty()
                        || entry.getValue().stream()
                                .anyMatch(
                                        role ->
                                                emittedPhysicians.stream()
                                                        .noneMatch(
                                                                physician ->
                                                                        physician.role() == role
                                                                                && physician
                                                                                        .contactRecordKey()
                                                                                        .equals(
                                                                                                entry.getKey()
                                                                                                        .recordKey()))))
                    throw new IllegalArgumentException(
                            "CDS_EXPORT_PROFESSIONAL_CONTACT_MAPPING_INVALID");
        }

        public boolean representable() {
            return fields.values().stream()
                    .noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        }

        @Override
        public String toString() {
            return "CdsExportProfessionalContactMapper.Result[protected]";
        }
    }

    /** Maps one patient's supplied relationship slice; it never discovers or borrows another patient's contact. */
    public Result map(
            int patient,
            List<CdsExportCapturedTextRowDto> associations,
            List<CdsExportCapturedTextRowDto> contacts,
            int maximumRows,
            long maximumBytes,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(checkpoint);
        checkpoint.run();
        if (patient < 1
                || associations == null
                || contacts == null
                || maximumRows < 1
                || maximumRows > 8192
                || maximumBytes < 1
                || maximumBytes > (64L << 20))
            throw new IllegalArgumentException("CDS_EXPORT_PROFESSIONAL_CONTACT_LIMITS_INVALID");
        if ((long) associations.size() + contacts.size() > maximumRows) limit();
        var lookup = new TreeMap<String, CdsExportCapturedTextRowDto>();
        var refs = new HashSet<String>();
        var keys = new HashSet<String>();
        for (var row : contacts) {
            checkpoint.run();
            requireRow(row, CdsExportPatientTableReadSupport.Plan.CONTACT);
            if (lookup.putIfAbsent(row.fields().get("id"), row) != null) scope();
        }
        for (var row : associations) {
            checkpoint.run();
            requireRow(row, CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_CONTACT);
            if (!Integer.toString(patient).equals(row.fields().get("demographicNo"))
                    || !keys.add(row.recordKey())) scope();
            if ("2".equals(row.fields().get("type"))) refs.add(row.fields().get("contactId"));
        }
        if (!refs.containsAll(lookup.keySet())) scope();
        var all = new ArrayList<>(contacts);
        all.addAll(associations);
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var states = new HashMap<CdsExportCapturedTextRowDto, CdsExportNativeFieldAssessments>();
        long bytes = 0;
        for (var row : all) {
            checkpoint.run();
            bytes = Math.addExact(bytes, 2048);
            for (String value : row.fields().values())
                if (value != null)
                    bytes =
                            Math.addExact(
                                    bytes, 32L + value.getBytes(StandardCharsets.UTF_8).length);
            if (bytes > maximumBytes) limit();
            var state = new CdsExportNativeFieldAssessments(row, fields);
            states.put(row, state);
            for (String column :
                    row.table().equals("Contact")
                            ? List.of("id", "updateDate")
                            : List.of(
                                    "id",
                                    "demographicNo",
                                    "created",
                                    "creator",
                                    "updateDate",
                                    "facilityId",
                                    "programNo"))
                state.operational(column, "CAPTURED_PROFESSIONAL_CONTACT_IDENTITY_OR_CONTEXT");
            if (row.table().equals("DemographicContact"))
                CdsExportContactConsent.assess(state, "ReferredPhysician");
        }
        var grouped = new EnumMap<Role, List<CdsExportCapturedTextRowDto>>(Role.class);
        for (var row : associations) {
            checkpoint.run();
            var state = states.get(row);
            if (!"2".equals(state.raw("type")) || !"professional".equals(state.raw("category"))) {
                state.review(
                        "type", "ProfessionalPhysician", "OTHER_RELATIONSHIP_PROJECTION_REQUIRED");
                continue;
            }
            if (!"0".equals(state.raw("deleted")) || !"1".equals(state.raw("active"))) {
                state.review(
                        "active",
                        "ProfessionalPhysician",
                        "HISTORICAL_PROFESSIONAL_RELATIONSHIP_REQUIRES_RECONCILIATION");
                continue;
            }
            Role role =
                    Arrays.stream(Role.values())
                            .filter(candidate -> candidate.label.equals(state.raw("role")))
                            .findFirst()
                            .orElse(null);
            if (role == null) {
                state.review(
                        "role",
                        "ProfessionalPhysician",
                        "UNSUPPORTED_PROFESSIONAL_ROLE_REQUIRES_REVIEW");
                continue;
            }
            for (String column : List.of("type", "category", "deleted", "active"))
                state.operational(column, "EXACT_ACTIVE_EXTERNAL_PROFESSIONAL_RELATIONSHIP");
            if ("0".equals(state.raw("mrp")))
                state.operational("mrp", "EXPLICIT_UNSET_RESPONSIBLE_PROVIDER_FLAG");
            for (String column : List.of("ec", "sdm"))
                if ("false".equals(state.raw(column)))
                    state.operational(column, "EXPLICIT_UNSET_ADDITIONAL_CONTACT_PURPOSE");
            state.set(
                    "role",
                    Outcome.MAPPED,
                    role.wire,
                    "EXACT_NATIVE_PROFESSIONAL_RELATIONSHIP_LABEL");
            if (state.raw("contactId") == null || !lookup.containsKey(state.raw("contactId"))) {
                state.review("contactId", role.wire, "EXACT_LINKED_PROFESSIONAL_CONTACT_REQUIRED");
                continue;
            }
            state.set(
                    "contactId",
                    Outcome.MAPPED,
                    role.wire,
                    "EXACT_PATIENT_PROFESSIONAL_CONTACT_REFERENCE");
            grouped.computeIfAbsent(role, ignored -> new ArrayList<>()).add(row);
        }
        var physicians = new ArrayList<Physician>();
        var uses = new LinkedHashMap<CdsExportNativeFieldKeyDto, Set<Role>>();
        for (Role role : Role.values()) {
            checkpoint.run();
            var group = grouped.getOrDefault(role, List.of());
            if (group.size() > 1) {
                for (var row : group)
                    states.get(row)
                            .review(
                                    "role",
                                    role.wire,
                                    "MULTIPLE_PROFESSIONAL_RELATIONSHIPS_EXCEED_WIRE_CARDINALITY");
                continue;
            }
            if (group.isEmpty()) continue;
            var association = group.getFirst();
            var row = lookup.get(association.fields().get("contactId"));
            var state = states.get(row);
            if (!"ProfessionalContact".equals(state.raw("type"))
                    || !"0".equals(state.raw("deleted"))) {
                state.review("type", role.wire, "EXACT_CURRENT_PROFESSIONAL_CONTACT_REQUIRED");
                continue;
            }
            state.operational("type", "EXACT_EXTERNAL_PROFESSIONAL_CONTACT_SUBTYPE");
            state.operational("deleted", "EXACT_CURRENT_NATIVE_PROFESSIONAL_CONTACT");
            String first = state.text("firstName", "ProfessionalPhysician/FirstName", 50),
                    last = state.text("lastName", "ProfessionalPhysician/LastName", 50);
            if ("".equals(state.raw("firstName"))) first = "";
            if ("".equals(state.raw("lastName"))) last = "";
            if (first == null || last == null || first.isBlank() && last.isBlank()) {
                state.review(
                        "firstName",
                        role.wire,
                        "COMPLETE_PROFESSIONAL_NAME_REQUIRED_WITHOUT_INVENTED_PARTS");
                continue;
            }
            for (String column : List.of("firstName", "lastName"))
                uses.computeIfAbsent(
                                new CdsExportNativeFieldKeyDto("Contact", row.recordKey(), column),
                                ignored -> EnumSet.noneOf(Role.class))
                        .add(role);
            for (String column : List.of("firstName", "lastName"))
                if ("".equals(state.raw(column)))
                    state.set(
                            column,
                            Outcome.MAPPED,
                            "ProfessionalPhysician/"
                                    + (column.equals("firstName") ? "FirstName" : "LastName"),
                            "EXACT_NATIVE_EMPTY_NAME_PART");
            physicians.add(
                    new Physician(
                            role,
                            association.recordKey(),
                            row.recordKey(),
                            new CdsExportPublicProviderDto(first, last, null)));
        }
        new CdsExportNativeFieldAssessmentValidator().validate(all, fields, 1_000_000, checkpoint);
        checkpoint.run();
        return new Result(patient, associations.size(), contacts.size(), physicians, fields, uses);
    }

    private static void requireRow(
            CdsExportCapturedTextRowDto row, CdsExportPatientTableReadSupport.Plan plan)
            throws IOException {
        if (row == null
                || !row.table().equals(plan.table())
                || !row.fields().keySet().equals(Set.copyOf(plan.columns()))
                || !row.exactNumbers().isEmpty()) scope();
        try {
            String id = row.fields().get("id");
            long number = Long.parseLong(id);
            if (!Long.toString(number).equals(id)
                    || !CdsExportPatientTableSourceReader.key(number).equals(row.recordKey()))
                scope();
        } catch (NumberFormatException | NullPointerException invalid) {
            scope();
        }
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_PROFESSIONAL_CONTACT_SOURCE_SCOPE_INVALID");
    }

    private static void limit() throws IOException {
        throw new IOException("CDS_EXPORT_PROFESSIONAL_CONTACT_RESOURCE_LIMIT");
    }
}
