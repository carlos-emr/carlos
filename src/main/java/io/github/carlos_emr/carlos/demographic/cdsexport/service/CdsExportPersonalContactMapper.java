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

/** External personal contacts with exact identities, unioned purposes and independently accounted fields. No chart/provider lookup or guessing. */
public final class CdsExportPersonalContactMapper {
    private static final Map<String, String> ROLES = Map.of("Emergency Contact", "EC", "Next of Kin", "NK", "Administrative Staff", "AS",
            "Care Giver", "CG", "Power of Attorney", "PA", "Insurance", "IN", "Guarantor", "GT", "Substitute Decision Maker", "SDM");
    public record Result(int patient, long associationRows, long contactRows, List<CdsExportPersonalContactDto> contacts,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        public Result {
            contacts = List.copyOf(contacts); fields = Map.copyOf(fields);
            if (patient < 1 || associationRows < 0 || contactRows < 0 || associationRows > 8192 || contactRows > 8192 || contacts.size() > contactRows
                    || contacts.stream().map(CdsExportPersonalContactDto::sourceRecordKey).distinct().count() != contacts.size()
                    || fields.size() != associationRows * CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_CONTACT.columns().size()
                            + contactRows * CdsExportPatientTableReadSupport.Plan.CONTACT.columns().size()) invalid();
            var identities = new HashMap<String, Map<String, Set<String>>>();
            for (var key : fields.keySet()) {
                if (!Set.of("DemographicContact", "Contact").contains(key.table())) invalid();
                identities.computeIfAbsent(key.table(), ignored -> new HashMap<>()).computeIfAbsent(key.recordKey(), ignored -> new HashSet<>()).add(key.column());
            }
            for (var plan : List.of(CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_CONTACT, CdsExportPatientTableReadSupport.Plan.CONTACT)) {
                var rows = identities.getOrDefault(plan.table(), Map.of());
                if (rows.size() != (plan == CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_CONTACT ? associationRows : contactRows)
                        || rows.values().stream().anyMatch(columns -> !columns.equals(Set.copyOf(plan.columns())))) invalid();
            }
            if (fields.values().stream().noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW)
                    && ((associationRows > 0) != !contacts.isEmpty() || contacts.size() != contactRows)) invalid();
        }
        public boolean nativeRepresentable() { return fields.values().stream().noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW); }
        private static void invalid() { throw new IllegalArgumentException("CDS_EXPORT_CONTACT_MAPPING_INVALID"); }
        @Override public String toString() { return "CdsExportPersonalContactMapper.Result[protected]"; }
    }
    public Result map(int patient, List<CdsExportCapturedTextRowDto> associations, List<CdsExportCapturedTextRowDto> contacts,
            int maximumRows, long maximumBytes, Runnable checkpoint) throws IOException {
        Objects.requireNonNull(checkpoint); checkpoint.run();
        if (patient < 1 || associations == null || contacts == null || maximumRows < 1 || maximumRows > 8192 || maximumBytes < 1 || maximumBytes > (64L << 20))
            throw new IllegalArgumentException("CDS_EXPORT_CONTACT_LIMITS_INVALID");
        if ((long) associations.size() + contacts.size() > maximumRows) limit();
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>(); var all = new ArrayList<CdsExportCapturedTextRowDto>();
        var lookup = new TreeMap<String, CdsExportCapturedTextRowDto>(); var references = new HashSet<String>(); var keys = new HashSet<String>();
        for (var row : contacts) {
            checkpoint.run(); requireRow(row, CdsExportPatientTableReadSupport.Plan.CONTACT);
            if (lookup.putIfAbsent(row.fields().get("id"), row) != null) scope(); all.add(row);
        }
        for (var row : associations) {
            checkpoint.run(); requireRow(row, CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_CONTACT);
            if (!Integer.toString(patient).equals(row.fields().get("demographicNo")) || !keys.add(row.recordKey())) scope();
            if ("2".equals(row.fields().get("type"))) references.add(row.fields().get("contactId")); all.add(row);
        }
        if (!references.containsAll(lookup.keySet())) scope();
        var states = new HashMap<CdsExportCapturedTextRowDto, CdsExportNativeFieldAssessments>(); long bytes = 0;
        for (var row : all) {
            checkpoint.run(); bytes = Math.addExact(bytes, 2048L);
            for (String value : row.fields().values()) if (value != null) bytes = Math.addExact(bytes, value.getBytes(StandardCharsets.UTF_8).length + 32L);
            if (bytes > maximumBytes) limit(); var state = new CdsExportNativeFieldAssessments(row, fields); states.put(row, state);
            for (String column : row.table().equals("Contact") ? List.of("id", "updateDate") : List.of("id", "demographicNo", "created", "creator", "updateDate", "facilityId", "programNo"))
                state.operational(column, "CAPTURED_NATIVE_CONTACT_IDENTITY_OR_CONTEXT");
        }
        var groups = new TreeMap<String, List<CdsExportCapturedTextRowDto>>();
        for (var row : associations) {
            var state = states.get(row); String root = "Contact";
            if (!"2".equals(state.raw("type"))) { state.review("type", root, "PATIENT_PROVIDER_OR_SPECIALIST_CONTACT_IDENTITY_PROJECTION_REQUIRED"); continue; }
            if (!"personal".equals(state.raw("category"))) { state.review("category", root, "PROFESSIONAL_OR_UNKNOWN_RELATIONSHIP_REQUIRES_SEPARATE_PROJECTION"); continue; }
            if (!"0".equals(state.raw("deleted")) || !"1".equals(state.raw("active"))) {
                state.review("deleted", root, "HISTORICAL_OR_UNKNOWN_CONTACT_ASSOCIATION_REQUIRES_REVIEW"); state.review("active", root, "HISTORICAL_OR_UNKNOWN_CONTACT_ASSOCIATION_REQUIRES_REVIEW"); continue;
            }
            var contact = lookup.get(state.raw("contactId"));
            if (contact == null) { state.review("contactId", root, "EXACT_LINKED_CONTACT_MISSING_REQUIRES_REVIEW"); continue; }
            for (String column : List.of("type", "category", "deleted", "active")) state.operational(column, "EXACT_ACTIVE_PERSONAL_EXTERNAL_CONTACT_ASSOCIATION");
            state.set("contactId", Outcome.MAPPED, root, "EXACT_PATIENT_CONTACT_ASSOCIATION");
            if ("0".equals(state.raw("mrp"))) state.operational("mrp", "EXACT_UNSET_RESPONSIBLE_PROVIDER_FLAG");
            groups.computeIfAbsent(contact.recordKey(), ignored -> new ArrayList<>()).add(row);
        }
        var mapped = new ArrayList<CdsExportPersonalContactDto>();
        for (var row : contacts.stream().sorted(Comparator.comparing(CdsExportCapturedTextRowDto::recordKey)).toList()) {
            checkpoint.run(); var group = groups.get(row.recordKey()); if (group == null) continue;
            var state = states.get(row); String root = "Contact[" + (mapped.size() + 1) + "]";
            if ("Contact".equals(state.raw("type"))) state.operational("type", "EXACT_NATIVE_PERSONAL_CONTACT_TYPE");
            else state.review("type", root, "UNEXPECTED_NATIVE_CONTACT_SUBTYPE_REQUIRES_REVIEW");
            if ("0".equals(state.raw("deleted"))) state.operational("deleted", "EXACT_CURRENT_NATIVE_CONTACT");
            else state.review("deleted", root, "DELETED_OR_UNKNOWN_CONTACT_HISTORY_REQUIRES_REVIEW");
            String first = state.text("firstName", root + "/Name/FirstName", 50), last = state.text("lastName", root + "/Name/LastName", 50);
            if (first == null && last == null) state.review("firstName", root + "/Name", "CONTACT_NAME_MISSING_OR_UNREPRESENTABLE");
            var purposes = new LinkedHashSet<CdsExportPersonalContactDto.Purpose>();
            for (var link : group) {
                var linkState = states.get(link); String role = linkState.raw("role");
                if (present(role)) {
                    if (role.equals("Multiple source purposes; see imported Contact details")) linkState.review("role", root + "/ContactPurpose", "RETAINED_CONTACT_PURPOSE_RECONCILIATION_REQUIRED");
                    else if (!CdsExportNativeFieldAssessments.legal(role, 50)) linkState.review("role", root + "/ContactPurpose", "CONTACT_PURPOSE_VALUES_REQUIRES_REVIEW");
                    else {
                        String code = ROLES.get(role); purposes.add(new CdsExportPersonalContactDto.Purpose(code != null, code == null ? role : code));
                        linkState.set("role", Outcome.MAPPED, root + "/ContactPurpose", code == null ? "EXACT_NATIVE_FREE_TEXT_PURPOSE" : "EXACT_NATIVE_CONTACT_PURPOSE_LABEL");
                    }
                }
                purposeFlag(linkState, "ec", "EC", root, purposes); purposeFlag(linkState, "sdm", "SDM", root, purposes);
            }
            var phones = new ArrayList<CdsExportDemographicMappingDto.Phone>();
            phone(state, phones, "residencePhone", "R", root); phone(state, phones, "cellPhone", "C", root); phone(state, phones, "workPhone", "W", root);
            String extension = state.raw("workPhoneExtension");
            if (extension != null) {
                int work = -1; for (int i = 0; i < phones.size(); i++) if (phones.get(i).type().equals("W")) work = i;
                if (work >= 0) {
                    try { var old = phones.get(work); var actual = new CdsExportDemographicMappingDto.Phone("W", old.number(), extension); phones.set(work, actual); state.set("workPhoneExtension", Outcome.MAPPED, root + "/PhoneNumber[W]/extension", "EXACT_NATIVE_CONTACT_WORK_EXTENSION"); }
                    catch (IllegalArgumentException unsupported) { state.review("workPhoneExtension", root + "/PhoneNumber[W]/extension", "CONTACT_PHONE_EXTENSION_REQUIRES_REVIEW"); }
                } else if (present(extension)) state.review("workPhoneExtension", root + "/PhoneNumber[W]/extension", "CONTACT_EXTENSION_WITHOUT_WORK_NUMBER");
            }
            String email = token(state, "email", root + "/EmailAddress", 50);
            if (email != null && !email.matches("([.a-zA-Z0-9_-])+@([a-zA-Z0-9_-])+(([a-zA-Z0-9_-])*\\.([a-zA-Z0-9_-])+)+")) { state.review("email", root + "/EmailAddress", "WIRE_EMAIL_REQUIRES_REVIEW"); email = null; }
            String note = note(row, group, states, root);
            if (first != null || last != null) mapped.add(new CdsExportPersonalContactDto(row.recordKey(), first, last, new ArrayList<>(purposes), phones, email, note));
        }
        new CdsExportNativeFieldAssessmentValidator().validate(all, fields, 1_000_000, checkpoint);
        checkpoint.run(); return new Result(patient, associations.size(), contacts.size(), mapped, fields);
    }
    private static String note(CdsExportCapturedTextRowDto contact, List<CdsExportCapturedTextRowDto> links,
            Map<CdsExportCapturedTextRowDto, CdsExportNativeFieldAssessments> states, String root) {
        var values = new LinkedHashSet<String>(); var sources = new ArrayList<CdsExportCapturedTextRowDto>(); sources.add(contact); sources.addAll(links);
        for (var row : sources) if (present(row.fields().get("note"))) values.add(row.fields().get("note"));
        if (values.size() > 1) {
            for (var row : sources) if (present(row.fields().get("note"))) states.get(row).review("note", root + "/Note", "DISTINCT_CONTACT_NOTES_REQUIRE_EXPLICIT_RECONCILIATION"); return null;
        }
        String value = values.isEmpty() ? null : values.iterator().next();
        for (var row : sources) if (present(row.fields().get("note"))) states.get(row).text("note", root + "/Note", 200);
        return value != null && CdsExportNativeFieldAssessments.legal(value, 200) ? value : null;
    }
    private static void purposeFlag(CdsExportNativeFieldAssessments state, String column, String code, String root, Set<CdsExportPersonalContactDto.Purpose> purposes) {
        String value = state.raw(column); if (!present(value)) return;
        if (value.equals("true")) { purposes.add(new CdsExportPersonalContactDto.Purpose(true, code)); state.set(column, Outcome.MAPPED, root + "/ContactPurpose", "EXACT_NATIVE_CONTACT_PURPOSE_FLAG"); }
        else if (value.equals("false")) {
            if (code.equals(ROLES.get(state.raw("role")))) { state.review(column, root + "/ContactPurpose", "CONFLICTING_CONTACT_PURPOSE_ASSERTIONS_REQUIRES_REVIEW"); state.review("role", root + "/ContactPurpose", "CONFLICTING_CONTACT_PURPOSE_ASSERTIONS_REQUIRES_REVIEW"); }
            else state.operational(column, "EXACT_UNSET_CONTACT_PURPOSE_FLAG");
        } else state.review(column, root + "/ContactPurpose", "UNKNOWN_NATIVE_CONTACT_PURPOSE_FLAG_REQUIRES_REVIEW");
    }
    private static void phone(CdsExportNativeFieldAssessments state, List<CdsExportDemographicMappingDto.Phone> phones, String column, String type, String root) {
        String value = token(state, column, root + "/PhoneNumber[" + type + "]/phoneNumber", 25); if (value != null) phones.add(new CdsExportDemographicMappingDto.Phone(type, value));
    }
    private static String token(CdsExportNativeFieldAssessments state, String column, String path, int maximum) {
        String value = state.raw(column); if (!present(value)) return null;
        if (value.startsWith(" ") || value.endsWith(" ") || value.contains("  ") || value.codePoints().anyMatch(code -> code == '\t' || code == '\r' || code == '\n')) { state.review(column, path, "TOKEN_WHITESPACE_REQUIRES_REVIEW"); return null; }
        return state.text(column, path, maximum);
    }
    private static void requireRow(CdsExportCapturedTextRowDto row, CdsExportPatientTableReadSupport.Plan plan) throws IOException {
        if (row == null || !row.table().equals(plan.table()) || !row.fields().keySet().equals(Set.copyOf(plan.columns())) || !row.exactNumbers().isEmpty()) scope();
        try { String id = row.fields().get("id"); long number = Long.parseLong(id); if (!Long.toString(number).equals(id) || !CdsExportPatientTableSourceReader.key(number).equals(row.recordKey())) scope(); }
        catch (NumberFormatException | NullPointerException invalid) { scope(); }
    }
    private static boolean present(String value) { return value != null && !value.isEmpty(); }
    private static void scope() throws IOException { throw new IOException("CDS_EXPORT_CONTACT_SOURCE_SCOPE_INVALID"); }
    private static void limit() throws IOException { throw new IOException("CDS_EXPORT_CONTACT_RESOURCE_LIMIT"); }
}
