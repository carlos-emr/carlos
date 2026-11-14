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

    /**
     * Health-care-team roles procontact.jsp offers that ReferredPhysician and FamilyPhysician do
     * not mean. An active external contact in such a role is a Demographics/Contact whose
     * ContactPurpose is the role as plain text (CdsExportPersonalContactMapper).
     */
    static final Set<String> CONTACT_ROLES = Set.of("Specialist", "Dietician");

    /**
     * The professional associations exported as Demographics/Contact rather than decided here:
     * an active, current external contact (type 2) in a {@link #CONTACT_ROLES} role whose contact
     * no other professional association names. A contact also named as a family or referring
     * physician, an inactive or removed association, and types 0, 1 and 3 (names in tables this
     * export does not read) stay with this mapper, which carries them.
     */
    static Set<CdsExportCapturedTextRowDto> contactRoleAssociations(
            List<CdsExportCapturedTextRowDto> associations) {
        var candidates = new ArrayList<CdsExportCapturedTextRowDto>();
        var others = new HashSet<String>();
        for (var row : associations) {
            if (!"professional".equals(row.fields().get("category"))
                    || !"2".equals(row.fields().get("type"))) continue;
            if ("0".equals(row.fields().get("deleted"))
                    && "1".equals(row.fields().get("active"))
                    && CONTACT_ROLES.contains(row.fields().get("role"))
                    && row.fields().get("contactId") != null) candidates.add(row);
            else others.add(row.fields().get("contactId"));
        }
        var result = new HashSet<CdsExportCapturedTextRowDto>();
        for (var row : candidates)
            if (!others.contains(row.fields().get("contactId"))) result.add(row);
        return Set.copyOf(result);
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
            // Not limits any more: no row count or size is refused. Callers still pass them.
            int maximumRows,
            long maximumBytes,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(checkpoint);
        checkpoint.run();
        if (patient < 1
                || associations == null
                || contacts == null)
            throw new IllegalArgumentException("CDS_EXPORT_PROFESSIONAL_CONTACT_LIMITS_INVALID");
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
        for (var row : all) {
            checkpoint.run();
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
        var identity =
                Set.of(
                        "id",
                        "demographicNo",
                        "created",
                        "creator",
                        "updateDate",
                        "facilityId",
                        "programNo");
        var carriedContacts = new HashSet<String>();
        var removedContacts = new HashSet<String>();
        // The associations ReferredPhysician or FamilyPhysician can state: an active, current
        // external professional relationship in one of the two roles, naming a current contact
        // row of this chart that has a name. Each element holds one physician, so a role two
        // such associations hold is stated by neither. Everything else travels exactly in the
        // native demographic record; nothing here holds the patient.
        var eligible = new EnumMap<Role, List<CdsExportCapturedTextRowDto>>(Role.class);
        for (var row : associations) {
            checkpoint.run();
            var state = states.get(row);
            Role role = role(state.raw("role"));
            var named = state.raw("contactId") == null ? null : lookup.get(state.raw("contactId"));
            if (role != null
                    && "2".equals(state.raw("type"))
                    && "professional".equals(state.raw("category"))
                    && "0".equals(state.raw("deleted"))
                    && "1".equals(state.raw("active"))
                    && named != null
                    && "0".equals(named.fields().get("deleted"))
                    && !(blank(named.fields().get("firstName"))
                            && blank(named.fields().get("lastName"))))
                eligible.computeIfAbsent(role, ignored -> new ArrayList<>()).add(row);
        }
        for (var row : associations) {
            checkpoint.run();
            var state = states.get(row);
            // A health-care-team member the user deleted is no longer in the chart.
            if ("1".equals(state.raw("deleted"))) {
                for (String column : row.fields().keySet())
                    if (present(row.fields().get(column)))
                        state.operational(column, "REMOVED_CONTACT_ASSOCIATION_NOT_IN_CURRENT_CHART");
                if ("2".equals(state.raw("type"))) removedContacts.add(state.raw("contactId"));
                continue;
            }
            Role role = role(state.raw("role"));
            // A provider or specialist from another table (type 0, 1, 3), an inactive member,
            // roles ReferredPhysician/FamilyPhysician do not mean (Specialist, Dietician...)
            // that callers did not route to Demographics/Contact (contactRoleAssociations), a
            // role that two current members hold (the health care team may list two referring
            // physicians; the element states one and none is chosen for the other), a contact
            // row that is missing, marked deleted or has no name: the stored association and
            // its contact travel in the native demographic record.
            if (role == null
                    || eligible.getOrDefault(role, List.of()).size() != 1
                    || !eligible.get(role).getFirst().recordKey().equals(row.recordKey())) {
                state.carryRemaining(identity);
                if ("2".equals(state.raw("type"))) carriedContacts.add(state.raw("contactId"));
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
            state.set(
                    "contactId",
                    Outcome.MAPPED,
                    role.wire,
                    "EXACT_PATIENT_PROFESSIONAL_CONTACT_REFERENCE");
            // Health-care-team values the two elements have no place for (a note, a set flag).
            state.carryRemaining(identity);
            grouped.computeIfAbsent(role, ignored -> new ArrayList<>()).add(row);
        }
        var physicians = new ArrayList<Physician>();
        var uses = new LinkedHashMap<CdsExportNativeFieldKeyDto, Set<Role>>();
        for (Role role : Role.values()) {
            checkpoint.run();
            var group = grouped.getOrDefault(role, List.of());
            if (group.isEmpty()) continue;
            var association = group.getFirst();
            var row = lookup.get(association.fields().get("contactId"));
            var state = states.get(row);
            // The contact search lists every contact row, so the team may name a plain contact
            // as a physician: the chart shows the person in the role all the same, and the
            // stored subtype travels in the native record.
            if ("ProfessionalContact".equals(state.raw("type")))
                state.operational("type", "EXACT_EXTERNAL_PROFESSIONAL_CONTACT_SUBTYPE");
            else if (present(state.raw("type"))) state.carry("type");
            state.operational("deleted", "EXACT_CURRENT_NATIVE_PROFESSIONAL_CONTACT");
            // Both parts are required elements of at most 50 characters (the columns hold
            // 100). A part with nothing stored is written empty; a longer one is written as
            // its first 50 characters and carried whole (carriedText); a character XML cannot
            // carry is shown as U+FFFD and carried exactly.
            String first = part(state, "firstName", "ProfessionalPhysician/FirstName"),
                    last = part(state, "lastName", "ProfessionalPhysician/LastName");
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
        for (var row : contacts) {
            var state = states.get(row);
            String id = row.fields().get("id");
            boolean used =
                    physicians.stream()
                            .anyMatch(physician -> physician.contactRecordKey().equals(row.recordKey()));
            if (used || carriedContacts.contains(id))
                // Address, phones and the other contact columns ReferredPhysician and
                // FamilyPhysician (a name only) cannot hold.
                state.carryRemaining(Set.of("id", "updateDate"));
            else if (removedContacts.contains(id))
                for (String column : row.fields().keySet())
                    if (present(row.fields().get(column)))
                        state.operational(column, "CONTACT_OF_A_REMOVED_ASSOCIATION_NOT_IN_CURRENT_CHART");
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

    private static boolean present(String value) {
        return value != null && !value.isEmpty();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static Role role(String label) {
        return Arrays.stream(Role.values())
                .filter(candidate -> candidate.label.equals(label))
                .findFirst()
                .orElse(null);
    }

    /** One required name part: empty when nothing is stored, at most 50 code points. */
    private static String part(
            CdsExportNativeFieldAssessments state, String column, String destination) {
        String raw = state.raw(column);
        if (raw == null || raw.isEmpty()) return "";
        String shown = state.carriedText(column, destination, 50);
        if (shown != null) return shown;
        // Longer than the element: carriedText has carried the stored value whole.
        String text = CdsExportXmlText.element(raw);
        return text.substring(0, text.offsetByCodePoints(0, 50));
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_PROFESSIONAL_CONTACT_SOURCE_SCOPE_INVALID");
    }
}
