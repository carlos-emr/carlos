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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.io.IOException;
import java.util.*;

/**
 * External personal contacts, and specialist or dietician external contacts of the health-care
 * team, with exact identities, unioned purposes and independently accounted fields. A team role
 * is the ContactPurpose as plain text. No chart/provider lookup or guessing.
 */
public final class CdsExportPersonalContactMapper {
    private static final Map<String, String> ROLES =
            Map.of(
                    "Emergency Contact",
                    "EC",
                    "Next of Kin",
                    "NK",
                    "Administrative Staff",
                    "AS",
                    "Care Giver",
                    "CG",
                    "Power of Attorney",
                    "PA",
                    "Insurance",
                    "IN",
                    "Guarantor",
                    "GT",
                    "Substitute Decision Maker",
                    "SDM");

    public record Result(
            int patient,
            long associationRows,
            long contactRows,
            List<CdsExportPersonalContactDto> contacts,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        public Result {
            contacts = List.copyOf(contacts);
            fields = Map.copyOf(fields);
            if (patient < 1
                    || associationRows < 0
                    || contactRows < 0
                    || contacts.size() > contactRows
                    || contacts.stream()
                                    .map(CdsExportPersonalContactDto::sourceRecordKey)
                                    .distinct()
                                    .count()
                            != contacts.size()
                    || fields.size()
                            != associationRows
                                            * CdsExportPatientTableReadSupport.Plan
                                                    .DEMOGRAPHIC_CONTACT
                                                    .columns()
                                                    .size()
                                    + contactRows
                                            * CdsExportPatientTableReadSupport.Plan.CONTACT
                                                    .columns()
                                                    .size()) invalid();
            var identities = new HashMap<String, Map<String, Set<String>>>();
            for (var key : fields.keySet()) {
                if (!Set.of("DemographicContact", "Contact").contains(key.table())) invalid();
                identities
                        .computeIfAbsent(key.table(), ignored -> new HashMap<>())
                        .computeIfAbsent(key.recordKey(), ignored -> new HashSet<>())
                        .add(key.column());
            }
            for (var plan :
                    List.of(
                            CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_CONTACT,
                            CdsExportPatientTableReadSupport.Plan.CONTACT)) {
                var rows = identities.getOrDefault(plan.table(), Map.of());
                if (rows.size()
                                != (plan
                                                == CdsExportPatientTableReadSupport.Plan
                                                        .DEMOGRAPHIC_CONTACT
                                        ? associationRows
                                        : contactRows)
                        || rows.values().stream()
                                .anyMatch(columns -> !columns.equals(Set.copyOf(plan.columns()))))
                    invalid();
            }
            // Removed, inactive and non-personal associations (and their contacts) are not
            // Contact elements, so a representable mapping may emit fewer contacts than rows.
            if (!contacts.isEmpty() && associationRows == 0) invalid();
        }

        public boolean nativeRepresentable() {
            return fields.values().stream()
                    .noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        }

        private static void invalid() {
            throw new IllegalArgumentException("CDS_EXPORT_CONTACT_MAPPING_INVALID");
        }

        @Override
        public String toString() {
            return "CdsExportPersonalContactMapper.Result[protected]";
        }
    }

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
            throw new IllegalArgumentException("CDS_EXPORT_CONTACT_LIMITS_INVALID");
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var all = new ArrayList<CdsExportCapturedTextRowDto>();
        var lookup = new TreeMap<String, CdsExportCapturedTextRowDto>();
        var references = new HashSet<String>();
        var keys = new HashSet<String>();
        for (var row : contacts) {
            checkpoint.run();
            requireRow(row, CdsExportPatientTableReadSupport.Plan.CONTACT);
            if (lookup.putIfAbsent(row.fields().get("id"), row) != null) scope();
            all.add(row);
        }
        for (var row : associations) {
            checkpoint.run();
            requireRow(row, CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_CONTACT);
            if (!Integer.toString(patient).equals(row.fields().get("demographicNo"))
                    || !keys.add(row.recordKey())) scope();
            if ("2".equals(row.fields().get("type"))) references.add(row.fields().get("contactId"));
            all.add(row);
        }
        if (!references.containsAll(lookup.keySet())) scope();
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
                state.operational(column, "CAPTURED_NATIVE_CONTACT_IDENTITY_OR_CONTEXT");
            if (row.table().equals("DemographicContact"))
                CdsExportContactConsent.assess(state, "Contact");
        }
        var groups = new TreeMap<String, List<CdsExportCapturedTextRowDto>>();
        // Contacts named by an association the record carries, or only by removed ones.
        var carriedContacts = new HashSet<String>();
        var removedContacts = new HashSet<String>();
        var identity =
                Set.of(
                        "id",
                        "demographicNo",
                        "created",
                        "creator",
                        "updateDate",
                        "facilityId",
                        "programNo");
        for (var row : associations) {
            var state = states.get(row);
            String root = "Contact";
            // Manage Contacts "Delete" keeps the association with deleted=1: no longer in the
            // chart, like a superseded snapshot, and not exported.
            if ("1".equals(state.raw("deleted"))) {
                for (String column : row.fields().keySet())
                    if (present(row.fields().get(column)))
                        state.operational(column, "REMOVED_CONTACT_ASSOCIATION_NOT_IN_CURRENT_CHART");
                if ("2".equals(state.raw("type"))) removedContacts.add(state.raw("contactId"));
                continue;
            }
            // An "Internal" contact (another patient), a provider or specialist (type 0, 1, 3:
            // their names live in tables this export does not read), a relationship of another
            // category, or an association marked Inactive: the stored association travels
            // exactly in the native demographic NewCategory record.
            // A specialist or dietician external contact is a Contact too: its role is the
            // purpose (CdsExportProfessionalContactMapper#contactRoleAssociations routes it here).
            boolean contactRole =
                    "professional".equals(state.raw("category"))
                            && CdsExportProfessionalContactMapper.CONTACT_ROLES.contains(
                                    state.raw("role"));
            if (!"2".equals(state.raw("type"))
                    || !"personal".equals(state.raw("category")) && !contactRole
                    || !"0".equals(state.raw("deleted"))
                    || !"1".equals(state.raw("active"))) {
                state.carryRemaining(identity);
                if ("2".equals(state.raw("type"))) carriedContacts.add(state.raw("contactId"));
                continue;
            }
            var contact =
                    state.raw("contactId") == null ? null : lookup.get(state.raw("contactId"));
            if (contact == null) {
                // The association names no contact row of this chart (an empty or dangling
                // contactId): there is no name to write, so the stored association travels
                // exactly in the native demographic record. Never held.
                state.carryRemaining(identity);
                continue;
            }
            for (String column : List.of("type", "category", "deleted", "active"))
                state.operational(column, "EXACT_ACTIVE_PERSONAL_EXTERNAL_CONTACT_ASSOCIATION");
            state.set("contactId", Outcome.MAPPED, root, "EXACT_PATIENT_CONTACT_ASSOCIATION");
            if ("0".equals(state.raw("mrp")))
                state.operational("mrp", "EXACT_UNSET_RESPONSIBLE_PROVIDER_FLAG");
            groups.computeIfAbsent(contact.recordKey(), ignored -> new ArrayList<>()).add(row);
        }
        var mapped = new ArrayList<CdsExportPersonalContactDto>();
        for (var row :
                contacts.stream()
                        .sorted(Comparator.comparing(CdsExportCapturedTextRowDto::recordKey))
                        .toList()) {
            checkpoint.run();
            var group = groups.get(row.recordKey());
            if (group == null) {
                var state = states.get(row);
                if (carriedContacts.contains(row.fields().get("id")))
                    state.carryRemaining(Set.of("id", "updateDate"));
                else if (removedContacts.contains(row.fields().get("id")))
                    for (String column : row.fields().keySet())
                        if (present(row.fields().get(column)))
                            state.operational(
                                    column, "CONTACT_OF_A_REMOVED_ASSOCIATION_NOT_IN_CURRENT_CHART");
                continue;
            }
            var state = states.get(row);
            String root = "Contact[" + (mapped.size() + 1) + "]";
            boolean professionalGroup =
                    group.stream()
                            .allMatch(
                                    link -> "professional".equals(link.fields().get("category")));
            if ("Contact".equals(state.raw("type")))
                state.operational("type", "EXACT_NATIVE_PERSONAL_CONTACT_TYPE");
            else if (professionalGroup && "ProfessionalContact".equals(state.raw("type")))
                state.operational("type", "EXACT_NATIVE_PROFESSIONAL_CONTACT_TYPE");
            // The contact search lists every contact row, so a personal relationship may name a
            // professional contact (and the reverse): the person is the patient's contact all
            // the same, and the stored subtype travels in the native demographic record.
            else if (present(state.raw("type"))) state.carry("type");
            if ("0".equals(state.raw("deleted")))
                state.operational("deleted", "EXACT_CURRENT_NATIVE_CONTACT");
            // The chart lists a contact by its association; a contact row marked deleted while
            // a current association names it is still shown, and the flag is carried.
            else if (present(state.raw("deleted"))) state.carry("deleted");
            // Both name parts are optional in Demographics/Contact/Name: a contact saved with
            // no name, or with parts longer than the element (carried exactly), is written with
            // the parts it has and never held.
            String first = state.carriedText("firstName", root + "/Name/FirstName", 50),
                    last = state.carriedText("lastName", root + "/Name/LastName", 50);
            var purposes = new LinkedHashSet<CdsExportPersonalContactDto.Purpose>();
            for (var link : group) {
                var linkState = states.get(link);
                String role = linkState.raw("role");
                if (present(role)) {
                    var purpose = rolePurpose(role);
                    if (purpose == null)
                        // No ContactPurpose is written from it and the stored text travels in
                        // the native record: see rolePurpose for the three cases.
                        linkState.carry("role");
                    else {
                        purposes.add(purpose);
                        linkState.set(
                                "role",
                                Outcome.MAPPED,
                                root + "/ContactPurpose",
                                purpose.coded()
                                        ? "EXACT_NATIVE_CONTACT_PURPOSE_LABEL"
                                        : "professional".equals(linkState.raw("category"))
                                                ? "HEALTH_CARE_TEAM_ROLE_AS_PLAIN_TEXT_PURPOSE"
                                                : "EXACT_NATIVE_FREE_TEXT_PURPOSE");
                    }
                }
                purposeFlag(linkState, "ec", "EC", root, purposes);
                purposeFlag(linkState, "sdm", "SDM", root, purposes);
            }
            var phones = new ArrayList<CdsExportDemographicMappingDto.Phone>();
            phone(state, phones, "residencePhone", "R", root);
            phone(state, phones, "cellPhone", "C", root);
            phone(state, phones, "workPhone", "W", root);
            String extension = state.raw("workPhoneExtension");
            if (present(extension)) {
                int work = -1;
                for (int i = 0; i < phones.size(); i++)
                    if (phones.get(i).type().equals("W")) work = i;
                if (work >= 0) {
                    try {
                        var old = phones.get(work);
                        var actual =
                                new CdsExportDemographicMappingDto.Phone(
                                        "W", old.number(), extension);
                        phones.set(work, actual);
                        state.set(
                                "workPhoneExtension",
                                Outcome.MAPPED,
                                root + "/PhoneNumber[W]/extension",
                                "EXACT_NATIVE_CONTACT_WORK_EXTENSION");
                    } catch (IllegalArgumentException unsupported) {
                        // The extension box has no length limit; the stored value is carried.
                        state.carry("workPhoneExtension");
                    }
                } else if (present(extension)) state.carry("workPhoneExtension");
            }
            String email = state.token("email", root + "/EmailAddress", 50);
            if (email != null && !CdsExportEmailLexical.matches(email)) {
                state.carry("email");
                email = null;
            }
            // Contact columns Demographics/Contact has no element for travel in the record.
            state.carryRemaining(Set.of("id", "updateDate"));
            String note = note(row, group, states, root);
            // An association value Contact has no element for (a set responsible-provider flag).
            for (var link : group) states.get(link).carryRemaining(identity);
            mapped.add(
                    new CdsExportPersonalContactDto(
                            row.recordKey(),
                            first,
                            last,
                            new ArrayList<>(purposes),
                            phones,
                            email,
                            note));
        }
        new CdsExportNativeFieldAssessmentValidator().validate(all, fields, 1_000_000, checkpoint);
        checkpoint.run();
        return new Result(patient, associations.size(), contacts.size(), mapped, fields);
    }

    private static String note(
            CdsExportCapturedTextRowDto contact,
            List<CdsExportCapturedTextRowDto> links,
            Map<CdsExportCapturedTextRowDto, CdsExportNativeFieldAssessments> states,
            String root) {
        var values = new LinkedHashSet<String>();
        var sources = new ArrayList<CdsExportCapturedTextRowDto>();
        sources.add(contact);
        sources.addAll(links);
        for (var row : sources)
            if (present(row.fields().get("note"))) values.add(row.fields().get("note"));
        // Contact has one Note of 200 characters: distinct notes on the contact and its
        // associations, or a longer note, travel exactly in the native demographic record.
        String value = values.size() == 1 ? values.iterator().next() : null;
        // A note holding a character XML cannot carry is written with U+FFFD and carried exactly.
        String shown = value == null ? null : CdsExportXmlText.element(value);
        boolean fits = shown != null && shown.codePointCount(0, shown.length()) <= 200;
        for (var row : sources)
            if (present(row.fields().get("note"))) {
                if (fits) states.get(row).carriedText("note", root + "/Note", 200);
                else states.get(row).carry("note");
            }
        return fits ? shown : null;
    }

    private static void purposeFlag(
            CdsExportNativeFieldAssessments state,
            String column,
            String code,
            String root,
            Set<CdsExportPersonalContactDto.Purpose> purposes) {
        String value = state.raw(column);
        if (!present(value)) return;
        if (value.equals("true")) {
            purposes.add(new CdsExportPersonalContactDto.Purpose(true, code));
            state.set(
                    column,
                    Outcome.MAPPED,
                    root + "/ContactPurpose",
                    "EXACT_NATIVE_CONTACT_PURPOSE_FLAG");
        } else if (value.equals("false")) {
            // "false" beside a relationship that names the same purpose (no screen writes
            // "false"; older data does): the relationship the chart shows is the purpose, and
            // the stored flag travels in the native record.
            if (state.raw("role") != null && code.equals(ROLES.get(state.raw("role"))))
                state.carry(column);
            else state.operational(column, "EXACT_UNSET_CONTACT_PURPOSE_FLAG");
        } else
            // Any other spelling: no purpose is asserted from it; carried exactly.
            state.carry(column);
    }

    private static void phone(
            CdsExportNativeFieldAssessments state,
            List<CdsExportDemographicMappingDto.Phone> phones,
            String column,
            String type,
            String root) {
        String value = state.token(column, root + "/PhoneNumber[" + type + "]/phoneNumber", 25);
        if (value != null) phones.add(new CdsExportDemographicMappingDto.Phone(type, value));
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

    /**
     * The ContactPurpose a populated relationship column states, or null when none is written
     * from it and the stored text travels in the native demographic record instead:
     *
     * <ul>
     *   <li>the importer's marker for a source contact with several purposes. It is not a
     *       relationship; the purposes themselves stay in the imported Contact element while the
     *       import's projection stands ({@link CdsExportContactFieldReconciler}). It was a hold
     *       (RETAINED_CONTACT_PURPOSE_RECONCILIATION_REQUIRED);
     *   <li>a typed relationship longer than PurposeAsPlainText (50; the column holds 100) or
     *       holding a character XML cannot carry;
     *   <li>a CtlRelationships row id of older health-care-team data ("3" is Parent in the
     *       shipped table; DemographicContactCreator still resolves it for display). The export
     *       capture does not include that site-editable table, so the id cannot be resolved from
     *       authenticated evidence, and the digits as a plain-text purpose would misstate the
     *       relationship.
     * </ul>
     */
    static CdsExportPersonalContactDto.Purpose rolePurpose(String role) {
        if (!present(role)
                || role.equals(
                        io.github.carlos_emr.carlos.demographic.cdsimport.service
                                .CdsContactNormalizationService.MULTIPLE_PURPOSES)
                || !CdsExportNativeFieldAssessments.legal(role, 50)
                || legacyRelationshipId(role)) return null;
        String code = ROLES.get(role);
        return new CdsExportPersonalContactDto.Purpose(code != null, code == null ? role : code);
    }

    private static boolean present(String value) {
        return value != null && !value.isEmpty();
    }

    /** The same test DemographicContactCreator applies before its CtlRelationships lookup. */
    static boolean legacyRelationshipId(String role) {
        String trimmed = role.trim();
        return !trimmed.isEmpty() && trimmed.chars().allMatch(Character::isDigit);
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_CONTACT_SOURCE_SCOPE_INVALID");
    }
}
