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

import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsContactDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsContactNormalizationService;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.Reason;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Reconciles one imported external personal contact against its original native projection.
 * Unchanged dependencies retain the complete original subtree, including middle names,
 * purpose choices and telephone components. Unprojected meaning is never guessed after an
 * edit. Changes use the parent's original source coordinates and need full demographic
 * coverage, active authenticated values and final XML validation before publication.
 */
public final class CdsExportContactFieldReconciler {
    private static final Pattern PHONE =
            Pattern.compile("PhoneNumber\\[([1-9][0-9]{0,17})\\]/@phoneNumberType");
    private static final Pattern PURPOSE =
            Pattern.compile("ContactPurpose\\[[1-9][0-9]{0,17}\\](?:/.*)?");

    private enum Group {
        GIVEN("Name/FirstName", "firstName"),
        FAMILY("Name/LastName", "lastName"),
        PURPOSES("ContactPurpose", "role", "ec", "sdm"),
        RESIDENCE("PhoneNumber[R]", "residencePhone"),
        CELL("PhoneNumber[C]", "cellPhone"),
        WORK("PhoneNumber[W]", "workPhone", "workPhoneExtension"),
        EMAIL("EmailAddress", "email"),
        NOTE("Note", "note");
        final String path;
        final Set<String> columns;

        Group(String path, String... columns) {
            this.path = path;
            this.columns = Set.of(columns);
        }

        String table() {
            return this == PURPOSES ? "DemographicContact" : "Contact";
        }
    }

    public record Result(
            CdsExportRetainedContactBinding.Result binding,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            Set<String> retainedPaths,
            Set<String> currentPaths,
            List<CdsExportRetainedSectionWriter.Change> changes) {
        public Result {
            fields = Map.copyOf(fields);
            retainedPaths = Set.copyOf(retainedPaths);
            currentPaths = Set.copyOf(currentPaths);
            changes = List.copyOf(changes);
        }

        public boolean representable() {
            return fields.values().stream()
                    .noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        }

        @Override
        public String toString() {
            return "CdsExportContactFieldReconciler.Result[protected]";
        }
    }

    public Result reconcile(
            int patient,
            String address,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields,
            CdsExportCapturedTextRowDto contact,
            CdsExportNativeRowReceiptDto contactWitness,
            CdsExportCapturedTextRowDto association,
            CdsExportNativeRowReceiptDto associationWitness,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(values);
        Objects.requireNonNull(checkpoint);
        values.requireActive();
        checkpoint.run();
        var binding =
                new CdsExportRetainedContactBinding()
                        .bind(patient, address, index, checked, allFields, checkpoint);
        var source = new CdsExportRetainedPreparationReader().contact(index, address).value();
        var refs = checked.units().get(address);
        if (refs == null
                || !refs.stream()
                        .map(CdsExportRetainedReceiptVerificationDto.CheckedReference::expected)
                        .toList()
                        .equals(index.references().get(address))
                || refs.size() != 2
                || refs.stream().filter(ref -> ref.expected().table().equals("Contact")).count()
                        != 1
                || refs.stream()
                                .filter(ref -> ref.expected().table().equals("DemographicContact"))
                                .count()
                        != 1) scope();
        var unit =
                index.tables()
                        .get("cds_import_unit")
                        .get(CdsImportKeys.hash(address.substring(0, 36), address.substring(37)));
        CdsNativeBaselineCodec.Document baseline = null;
        if (unit.fields().get("native_baseline") != null) {
            try {
                baseline =
                        CdsNativeBaselineCodec.decode(
                                unit.fields().get("native_baseline"),
                                unit.fields().get("target_references"),
                                unit.fields().get("target_digest"));
            } catch (IllegalArgumentException malformed) {
                scope();
            }
        }
        var oldContact =
                qualify(
                        address,
                        index,
                        refs,
                        baseline,
                        contact,
                        contactWitness,
                        "Contact",
                        checkpoint);
        var oldAssociation =
                qualify(
                        address,
                        index,
                        refs,
                        baseline,
                        association,
                        associationWitness,
                        "DemographicContact",
                        checkpoint);
        if (!matches(oldContact, "id", contactWitness.id())
                || !matches(oldAssociation, "id", associationWitness.id())
                || !matches(oldAssociation, "demographicNo", Integer.toString(patient))
                || !matches(oldAssociation, "contactId", contactWitness.id())
                || !matches(oldAssociation, "type", "2")
                || !matches(oldAssociation, "category", "personal")
                || !matches(oldAssociation, "active", "1")
                || !matches(oldAssociation, "deleted", "0")
                || !matches(oldContact, "type", "Contact")
                || !matches(oldContact, "deleted", "0")) scope();
        if (!Integer.toString(patient).equals(association.fields().get("demographicNo"))
                || !contactWitness.id().equals(association.fields().get("contactId"))) scope();
        var candidate =
                new CdsExportPersonalContactMapper()
                        .map(
                                patient,
                                List.of(association),
                                List.of(contact),
                                2,
                                4L << 20,
                                checkpoint);
        var decisions = new LinkedHashMap<>(candidate.fields());
        var retained = new LinkedHashSet<String>();
        var current = new LinkedHashSet<String>();
        String sourceContact = "Contact[" + binding.ordinal() + "]";
        decisions.replaceAll(
                (key, field) ->
                        new CdsExportFieldAssessmentDto(
                                field.presence(),
                                field.outcome(),
                                field.destination().startsWith("Contact[1]")
                                        ? sourceContact
                                                + field.destination()
                                                        .substring("Contact[1]".length())
                                        : field.destination(),
                                field.rule()));
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        var expected = projection(source);
        var nativeRows = Map.of("Contact", contact, "DemographicContact", association);
        var previous = Map.of("Contact", oldContact, "DemographicContact", oldAssociation);
        var differences =
                Map.of(
                        "Contact",
                        CdsNativeRowComparison.compare(oldContact, contactWitness.state()),
                        "DemographicContact",
                        CdsNativeRowComparison.compare(oldAssociation, associationWitness.state()));
        var relative = new TreeMap<String, CdsExportRetainedFieldDto>();
        for (var field : binding.fields()) {
            checkpoint.run();
            String path = field.sourcePath().substring(binding.sourceRoot().length());
            if (!path.startsWith("/") || relative.putIfAbsent(path.substring(1), field) != null)
                scope();
        }
        var phones = new HashMap<String, List<String>>();
        var purposes =
                new TreeSet<String>(
                        Comparator.comparingLong(CdsExportContactFieldReconciler::ordinal));
        for (var field : relative.entrySet()) {
            checkpoint.run();
            var phone = PHONE.matcher(field.getKey());
            if (phone.matches()) {
                String type = read(values, field.getValue(), 1, checkpoint);
                if (!Set.of("R", "C", "W").contains(type)) scope();
                phones.computeIfAbsent(type, ignored -> new ArrayList<>())
                        .add("PhoneNumber[" + phone.group(1) + "]");
            }
            if (PURPOSE.matcher(field.getKey()).matches())
                purposes.add(field.getKey().split("/", 2)[0]);
        }
        String prefix = "Contact[" + binding.ordinal() + "]";
        for (var group : Group.values()) {
            checkpoint.run();
            boolean proved = true;
            for (String column : group.columns)
                proved &=
                        matches(
                                previous.get(group.table()),
                                column,
                                expected.get(group.table()).get(column));
            if (group == Group.NOTE) proved &= matches(oldAssociation, "note", source.note());
            if (!proved) {
                mark(
                        nativeRows,
                        decisions,
                        group,
                        prefix,
                        Outcome.REQUIRES_REVIEW,
                        "PREPARED_NATIVE_CONTACT_PROJECTION_NOT_PROVEN");
                continue;
            }
            boolean unchanged =
                    Collections.disjoint(
                            group.columns, differences.get(group.table()).changedColumns());
            if (group == Group.NOTE)
                unchanged &=
                        !differences.get("DemographicContact").changedColumns().contains("note");
            if (unchanged) {
                retained.add(group.path);
                mark(
                        nativeRows,
                        decisions,
                        group,
                        prefix,
                        Outcome.MAPPED,
                        "VERIFIED_UNCHANGED_CONTACT_SOURCE_PROJECTION");
                continue;
            }
            boolean blocked = false;
            if (group == Group.GIVEN) {
                var middle = relative.get("Name[1]/MiddleName[1]");
                blocked = middle != null && !read(values, middle, 50, checkpoint).isBlank();
            } else if (group == Group.PURPOSES)
                blocked = source.roles().contains(CdsContactNormalizationService.MULTIPLE_PURPOSES);
            else if (group == Group.NOTE)
                blocked =
                        !Objects.equals(
                                contact.fields().get("note"), association.fields().get("note"));
            String type =
                    group == Group.RESIDENCE
                            ? "R"
                            : group == Group.CELL ? "C" : group == Group.WORK ? "W" : null;
            if (type != null) {
                var oldPhone = source.phones().get(type);
                blocked =
                        phones.getOrDefault(type, List.of()).size() > 1
                                || oldPhone == null
                                        && !phones.getOrDefault(type, List.of()).isEmpty()
                                || !type.equals("W")
                                        && oldPhone != null
                                        && oldPhone.extension() != null
                                        && !oldPhone.extension().isEmpty();
            }
            if (blocked) {
                mark(
                        nativeRows,
                        decisions,
                        group,
                        prefix,
                        Outcome.REQUIRES_REVIEW,
                        "EDITED_COMPOUND_OR_UNPROJECTED_CONTACT_VALUE_REQUIRES_REVIEW");
                continue;
            }
            if (group.columns.stream()
                            .anyMatch(
                                    column ->
                                            decisions
                                                            .get(
                                                                    key(
                                                                            nativeRows.get(
                                                                                    group.table()),
                                                                            column))
                                                            .outcome()
                                                    == Outcome.REQUIRES_REVIEW)
                    || group == Group.NOTE
                            && decisions.get(key(association, "note")).outcome()
                                    == Outcome.REQUIRES_REVIEW) continue;
            var output = candidate.contacts().isEmpty() ? null : candidate.contacts().getFirst();
            if ((group == Group.PURPOSES || type != null) && output == null) {
                mark(
                        nativeRows,
                        decisions,
                        group,
                        prefix,
                        Outcome.REQUIRES_REVIEW,
                        "CURRENT_CONTACT_COMPOUND_MAPPING_REQUIRES_REVIEW");
                continue;
            }
            if (group == Group.PURPOSES) {
                var contents = new ArrayList<CdsExportXmlComposer.Content>();
                for (var purpose : output.purposes())
                    contents.add(
                            xml ->
                                    xml.element(
                                            "cds_dt",
                                            purpose.coded()
                                                    ? "PurposeAsEnum"
                                                    : "PurposeAsPlainText",
                                            purpose.value()));
                repeated(
                        relative,
                        prefix,
                        "ContactPurpose",
                        new ArrayList<>(purposes),
                        contents,
                        changes);
            } else if (type != null) {
                var phone =
                        output.phones().stream()
                                .filter(number -> number.type().equals(type))
                                .findFirst()
                                .orElse(null);
                CdsExportXmlComposer.Content content =
                        phone == null
                                ? null
                                : xml -> {
                                    xml.attribute("", "phoneNumberType", type);
                                    xml.element("cds_dt", "phoneNumber", phone.number());
                                    if (phone.extension() != null)
                                        xml.element("cds_dt", "extension", phone.extension());
                                };
                var old = phones.getOrDefault(type, List.of());
                // A new type uses a new original-coordinate ordinal across all phone types.
                if (old.isEmpty() && content != null) {
                    long maximum =
                            phones.values().stream()
                                    .flatMap(List::stream)
                                    .mapToLong(CdsExportContactFieldReconciler::ordinal)
                                    .max()
                                    .orElse(0);
                    String path = "PhoneNumber[" + (maximum + 1) + "]";
                    changes.add(
                            new CdsExportRetainedSectionWriter.Change(
                                    prefix + "/" + path, Set.of(), content));
                    phones.put(type, List.of(path));
                } else if (!old.isEmpty())
                    replace(relative, prefix, old.getFirst(), content, changes);
            } else {
                String path =
                        group == Group.GIVEN
                                ? "Name[1]/FirstName[1]"
                                : group == Group.FAMILY
                                        ? "Name[1]/LastName[1]"
                                        : group == Group.EMAIL ? "EmailAddress[1]" : "Note[1]";
                String value = contact.fields().get(group.columns.iterator().next());
                if (value != null && value.isEmpty()) value = null;
                final String actual = value;
                replace(
                        relative,
                        prefix,
                        path,
                        actual == null ? null : xml -> xml.text(actual),
                        changes);
            }
            current.add(group.path);
        }
        new CdsExportNativeFieldAssessmentValidator()
                .validate(List.of(contact, association), decisions, 1000, checkpoint);
        values.requireActive();
        checkpoint.run();
        return new Result(binding, decisions, retained, current, changes);
    }

    private static CdsNativeRowState qualify(
            String address,
            CdsExportRetainedIndexDto index,
            List<CdsExportRetainedReceiptVerificationDto.CheckedReference> references,
            CdsNativeBaselineCodec.Document baseline,
            CdsExportCapturedTextRowDto row,
            CdsExportNativeRowReceiptDto witness,
            String table,
            Runnable checkpoint)
            throws IOException {
        var ref =
                references.stream()
                        .filter(item -> item.expected().table().equals(table))
                        .findFirst()
                        .orElseThrow();
        if (ref.reason() == Reason.ROW_NOT_IN_CAPTURE)
            throw new IOException("CDS_EXPORT_CONTACT_DELETION_REQUIRES_REVIEW");
        requireCapturedWitness(row, witness, checkpoint);
        if (ref.currentDigest() == null)
            throw new IOException("CDS_EXPORT_CONTACT_NATIVE_WITNESS_REQUIRED");
        if (!table.equals(row.table())
                || !ref.expected().id().equals(witness.id())
                || !ref.currentDigest().equals(witness.digest())) scope();
        for (var unit : index.references().entrySet()) {
            checkpoint.run();
            if (!unit.getKey().equals(address)
                    && unit.getValue().stream()
                            .anyMatch(
                                    item ->
                                            item.table().equals(table)
                                                    && item.id().equals(witness.id())))
                throw new IOException("CDS_EXPORT_CONTACT_ALIAS_REQUIRES_REVIEW");
        }
        var old =
                baseline == null
                        ? (ref.reason() == Reason.UNCHANGED_ROW ? witness.state() : null)
                        : baseline.rows().stream()
                                .filter(
                                        item ->
                                                item.table().equals(table)
                                                        && item.id().equals(witness.id()))
                                .findFirst()
                                .orElse(null);
        if (old == null || !old.digest().equals(ref.expected().digest()))
            throw new IOException("CDS_EXPORT_CONTACT_FIELD_BASELINE_REQUIRED");
        var difference = CdsNativeRowComparison.compare(old, witness.state());
        var operational =
                table.equals("Contact") ? Set.of("updateDate") : Set.of("created", "updateDate");
        if (difference.schemaChanged() || !operational.containsAll(difference.unqualifiedColumns()))
            throw new IOException("CDS_EXPORT_CONTACT_FIELD_CODEC_REQUIRES_REVIEW");
        return old;
    }

    /** Same frozen row proof for imported and subsequently added native personal contacts. */
    static void requireCapturedWitness(
            CdsExportCapturedTextRowDto row,
            CdsExportNativeRowReceiptDto witness,
            Runnable checkpoint)
            throws IOException {
        if (row == null || witness == null || witness.state() == null || witness.partial())
            throw new IOException("CDS_EXPORT_CONTACT_NATIVE_WITNESS_REQUIRED");
        if (!Set.of("Contact", "DemographicContact").contains(row.table())
                || !row.table().equals(witness.table())
                || !row.recordKey().equals(witness.recordKey())
                || !witness.id().equals(row.fields().get("id"))
                || !row.exactNumbers().isEmpty()
                || !row.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        witness.state().columns().stream()
                                                .map(CdsNativeRowDigest.Column::name)
                                                .toList()))) scope();
        for (var column : witness.state().columns()) {
            checkpoint.run();
            if (!matches(witness.state(), column.name(), row.fields().get(column.name()))) scope();
        }
    }

    private static Map<String, Map<String, String>> projection(CdsContactDto source) {
        var contact = new HashMap<String, String>();
        contact.put("firstName", source.firstName());
        contact.put("lastName", source.lastName());
        contact.put("email", source.email());
        contact.put("note", source.note());
        for (var entry :
                Map.of("R", "residencePhone", "C", "cellPhone", "W", "workPhone").entrySet()) {
            var phone = source.phones().get(entry.getKey());
            String number = phone == null ? null : phone.number();
            if (phone != null
                    && !entry.getKey().equals("W")
                    && phone.extension() != null
                    && !phone.extension().isEmpty()) number += " ext " + phone.extension();
            contact.put(entry.getValue(), number);
        }
        var work = source.phones().get("W");
        contact.put("workPhoneExtension", work == null ? null : work.extension());
        var link = new HashMap<String, String>();
        link.put("role", source.roles().getFirst());
        link.put("ec", Boolean.TRUE.equals(source.emergencyContact()) ? "true" : null);
        link.put("sdm", Boolean.TRUE.equals(source.substituteDecisionMaker()) ? "true" : null);
        link.put("note", source.note());
        return Map.of("Contact", contact, "DemographicContact", link);
    }

    private static boolean matches(CdsNativeRowState state, String column, String expected)
            throws IOException {
        int index = -1;
        for (int i = 0; i < state.columns().size(); i++)
            if (state.columns().get(i).name().equals(column)) index = i;
        if (index < 0) scope();
        var cell = state.cells().get(index);
        if (expected == null) return cell.sqlNull();
        byte[] bytes = expected.getBytes(StandardCharsets.UTF_8);
        return !cell.sqlNull()
                && cell.bytes() == bytes.length
                && cell.sha256()
                        .equals(HexFormat.of().formatHex(CdsImportKeys.digest().digest(bytes)));
    }

    private static CdsExportNativeFieldKeyDto key(CdsExportCapturedTextRowDto row, String column) {
        return new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column);
    }

    private static void mark(
            Map<String, CdsExportCapturedTextRowDto> rows,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            Group group,
            String prefix,
            Outcome outcome,
            String rule) {
        for (String column : group.columns) {
            var key = key(rows.get(group.table()), column);
            fields.put(
                    key,
                    new CdsExportFieldAssessmentDto(
                            fields.get(key).presence(), outcome, prefix + "/" + group.path, rule));
        }
        if (group == Group.NOTE) {
            var key = key(rows.get("DemographicContact"), "note");
            fields.put(
                    key,
                    new CdsExportFieldAssessmentDto(
                            fields.get(key).presence(), outcome, prefix + "/Note", rule));
        }
    }

    private static void replace(
            Map<String, CdsExportRetainedFieldDto> fields,
            String prefix,
            String path,
            CdsExportXmlComposer.Content content,
            List<CdsExportRetainedSectionWriter.Change> changes) {
        var ids = ids(fields, path);
        if (!ids.isEmpty() || content != null)
            changes.add(
                    new CdsExportRetainedSectionWriter.Change(prefix + "/" + path, ids, content));
    }

    private static void repeated(
            Map<String, CdsExportRetainedFieldDto> fields,
            String prefix,
            String element,
            List<String> old,
            List<CdsExportXmlComposer.Content> contents,
            List<CdsExportRetainedSectionWriter.Change> changes) {
        for (int i = 0; i < Math.max(old.size(), contents.size()); i++)
            replace(
                    fields,
                    prefix,
                    i < old.size() ? old.get(i) : element + "[" + (i + 1) + "]",
                    i < contents.size() ? contents.get(i) : null,
                    changes);
    }

    private static Set<String> ids(Map<String, CdsExportRetainedFieldDto> fields, String path) {
        var ids = new HashSet<String>();
        fields.forEach(
                (key, field) -> {
                    if (key.equals(path) || key.startsWith(path + "/")) ids.add(field.id());
                });
        return ids;
    }

    private static long ordinal(String path) {
        return Long.parseLong(path.substring(path.indexOf('[') + 1, path.indexOf(']')));
    }

    private static String read(
            CdsExportRetainedSectionWriter.Values values,
            CdsExportRetainedFieldDto field,
            int maximum,
            Runnable checkpoint)
            throws IOException {
        var result = new StringBuilder();
        char[] buffer = new char[128];
        try (Reader input = values.text(field)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                checkpoint.run();
                if (count == 0 || result.length() + count > maximum * 2)
                    throw new IOException("CDS_EXPORT_CONTACT_SOURCE_VALUE_REQUIRES_REVIEW");
                result.append(buffer, 0, count);
            }
        } finally {
            Arrays.fill(buffer, '\0');
        }
        if (result.codePointCount(0, result.length()) > maximum)
            throw new IOException("CDS_EXPORT_CONTACT_SOURCE_VALUE_REQUIRES_REVIEW");
        return result.toString();
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_CONTACT_RECONCILIATION_SCOPE_INVALID");
    }
}
