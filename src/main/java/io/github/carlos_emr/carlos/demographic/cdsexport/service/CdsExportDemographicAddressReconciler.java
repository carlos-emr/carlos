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

import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.*;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Preserves original address structure and unprojected parts while the chart is unedited. After a
 * CARLOS edit the current columns are the truth: an edited part replaces its source part, and an
 * edit that a part-by-part replacement cannot express (a Formatted source, two source addresses
 * of one type, a source address the importer never projected) replaces the whole source address
 * with the current Structured one, as for a never-imported chart. A superseded source value is
 * never silently dropped: it stays in the import's retained source and the receipt lists its
 * fields as replaced, under the rule named here. Besides an unproven import projection, only
 * text no XML document can hold still requires review.
 */
public final class CdsExportDemographicAddressReconciler {
    private static final String ROOT = "/OmdCds[1]/PatientRecord[1]/Demographics[1]/";
    private static final Pattern ADDRESS =
            Pattern.compile("Address\\[([1-9][0-9]{0,17})\\](?:/.*)?");

    private enum Kind {
        MAIL(
                "M",
                List.of("address", "city", "province", "postal"),
                CdsExportDemographicProjectionEvidence.Group.MAILING_ADDRESS),
        RESIDENTIAL(
                "R",
                List.of(
                        "residentialAddress",
                        "residentialCity",
                        "residentialProvince",
                        "residentialPostal"),
                CdsExportDemographicProjectionEvidence.Group.RESIDENTIAL_ADDRESS);
        final String wire;
        final List<String> columns;
        final CdsExportDemographicProjectionEvidence.Group group;

        Kind(
                String wire,
                List<String> columns,
                CdsExportDemographicProjectionEvidence.Group group) {
            this.wire = wire;
            this.columns = columns;
            this.group = group;
        }
    }

    public record Result(
            String sourceManifestSha256,
            String unitAddress,
            Set<String> sourceFields,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            List<CdsExportRetainedSectionWriter.Change> proposedChanges) {
        public Result {
            sourceFields = Set.copyOf(sourceFields);
            fields = Map.copyOf(fields);
            proposedChanges = List.copyOf(proposedChanges);
        }

        public boolean representable() {
            return fields.values().stream()
                    .noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        }

        public List<CdsExportRetainedSectionWriter.Change> changes() throws IOException {
            if (!representable())
                throw new IOException("CDS_EXPORT_DEMOGRAPHIC_ADDRESSES_REQUIRES_REVIEW");
            return proposedChanges;
        }

        @Override
        public String toString() {
            return "CdsExportDemographicAddressReconciler.Result[protected]";
        }
    }

    public Result reconcile(
            int patient,
            String address,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields,
            CdsExportCapturedTextRowDto current,
            CdsExportNativeRowReceiptDto witness,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(values);
        Objects.requireNonNull(checkpoint);
        values.requireActive();
        checkpoint.run();
        var evidence =
                new CdsExportDemographicProjectionEvidence()
                        .verify(patient, address, index, checked, current, witness, checkpoint);
        var source =
                new CdsExportRetainedOccurrenceFields()
                        .fieldsFor(
                                patient,
                                CdsCategory.DEMOGRAPHICS,
                                address,
                                index,
                                checked,
                                allFields,
                                checkpoint);
        var groups = new TreeMap<Long, Map<String, CdsExportRetainedFieldDto>>();
        var ids = new HashSet<String>();
        long maximum = 0;
        for (var field : source)
            if (field.sourcePath().startsWith(ROOT)) {
                var match = ADDRESS.matcher(field.sourcePath().substring(ROOT.length()));
                if (!match.matches()) continue;
                long ordinal = Long.parseLong(match.group(1));
                maximum = Math.max(maximum, ordinal);
                String path = field.sourcePath().substring(ROOT.length());
                String prefix = "Address[" + ordinal + "]";
                if (groups.computeIfAbsent(ordinal, ignored -> new TreeMap<>())
                                .putIfAbsent(
                                        path.length() == prefix.length()
                                                ? ""
                                                : path.substring(prefix.length() + 1),
                                        field)
                        != null)
                    throw new IOException("CDS_EXPORT_DEMOGRAPHIC_ADDRESS_SOURCE_INVALID");
                ids.add(field.id());
            }
        var byKind = new HashMap<String, List<Long>>();
        for (var entry : groups.entrySet()) {
            var attribute = entry.getValue().get("@addressType");
            if (attribute == null)
                throw new IOException("CDS_EXPORT_DEMOGRAPHIC_ADDRESS_SOURCE_INVALID");
            String kind = readKind(values, attribute, checkpoint);
            if (!Set.of("M", "R").contains(kind))
                throw new IOException("CDS_EXPORT_DEMOGRAPHIC_ADDRESS_SOURCE_INVALID");
            byKind.computeIfAbsent(kind, ignored -> new ArrayList<>()).add(entry.getKey());
        }
        var raw = new LinkedHashMap<>(current.fields());
        raw.remove("demographic_no");
        var nativeMapping =
                new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(patient, raw));
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        for (var kind : Kind.values()) {
            checkpoint.run();
            var projection = evidence.groups().get(kind.group);
            var occurrences = byKind.getOrDefault(kind.wire, List.of());
            CdsDemographicNativeDto.Address prepared =
                    kind == Kind.MAIL
                            ? evidence.preparation().mailing()
                            : evidence.preparation().residential();
            String rule;
            boolean review = false;
            var nativeAddress =
                    nativeMapping.addresses().stream()
                            .filter(candidate -> candidate.type().equals(kind.wire))
                            .findFirst()
                            .orElse(null);
            if (!projection.preparedProjectionProven()) {
                rule = "PREPARED_NATIVE_ADDRESS_PROJECTION_NOT_PROVEN";
                review = true;
            } else if (projection.unchanged()) {
                if (prepared != null && occurrences.isEmpty()) {
                    rule = "PREPARED_SOURCE_ADDRESS_REQUIRED";
                    review = true;
                } else rule = "VERIFIED_UNCHANGED_SOURCE_ADDRESS";
            } else if (occurrences.size() > 1 || prepared == null && !occurrences.isEmpty()) {
                // Two or more source addresses of this type, or one the importer never projected:
                // after a CARLOS edit the current columns are the truth for this type. They
                // replace the first source address and the others are superseded; every replaced
                // source field stays in the import's retained source and is listed as replaced.
                // Columns the importer left empty and a form save then wrote empty (or the forms'
                // default province alone) record no address: an unprojected source is kept.
                if (illegal(kind, nativeMapping)) {
                    rule = "CURRENT_NATIVE_ADDRESS_REQUIRES_REVIEW";
                    review = true;
                } else if (nativeAddress == null && prepared == null)
                    rule = "UNPROJECTED_SOURCE_ADDRESS_KEPT_WITHOUT_A_CURRENT_ADDRESS";
                else {
                    for (int n = 0; n < occurrences.size(); n++)
                        supersede(
                                groups.get(occurrences.get(n)),
                                "Address[" + occurrences.get(n) + "]",
                                kind,
                                n == 0 ? nativeAddress : null,
                                changes);
                    rule =
                            nativeAddress == null
                                    ? "CLEARED_CURRENT_ADDRESS_SUPERSEDES_SOURCE_ADDRESS"
                                    : "EDITED_CURRENT_ADDRESS_SUPERSEDES_SOURCE_ADDRESS";
                }
            } else if (occurrences.isEmpty()) {
                if (prepared != null) {
                    rule = "PREPARED_SOURCE_ADDRESS_REQUIRED";
                    review = true;
                } else if (kind.columns.stream()
                        .anyMatch(
                                column ->
                                        nativeMapping.fields().get(column).outcome()
                                                == CdsExportDemographicMappingDto.Outcome
                                                        .REQUIRES_REVIEW)) {
                    rule = "NEW_NATIVE_ADDRESS_REQUIRES_REVIEW";
                    review = true;
                } else {
                    if (nativeAddress != null) {
                        String path = "Address[" + (++maximum) + "]";
                        changes.add(
                                new CdsExportRetainedSectionWriter.Change(
                                        path,
                                        Set.of(),
                                        xml -> {
                                            xml.attribute("", "addressType", kind.wire);
                                            xml.start("cds_dt", "Structured");
                                            structured(nativeAddress, xml);
                                            xml.end();
                                        }));
                    }
                    rule = "EXACT_ADDED_CURRENT_NATIVE_ADDRESS";
                }
            } else {
                long ordinal = occurrences.getFirst();
                String prefix = "Address[" + ordinal + "]";
                var original = groups.get(ordinal);
                var local = new ArrayList<CdsExportRetainedSectionWriter.Change>();
                var lines =
                        original.keySet().stream()
                                .filter(path -> path.matches("Structured\\[1\\]/Line[123]\\[1\\]"))
                                .toList();
                boolean formatted = original.containsKey("Formatted[1]");
                boolean sourceOnly = hasSourceOnlyParts(prepared, original, lines, formatted);
                boolean absent =
                        kind.columns.stream()
                                .allMatch(
                                        column ->
                                                current.fields().get(column) == null
                                                        || current.fields().get(column).isEmpty());
                if (absent && !sourceOnly) {
                    replace(original, prefix, "", null, local);
                    rule = "EXPLICIT_CURRENT_NATIVE_ADDRESS_ABSENCE";
                } else if (formatted) {
                    String street = current.fields().get(kind.columns.get(0));
                    boolean onlyStreet =
                            kind.columns.subList(1, 4).stream()
                                    .noneMatch(projection.changedColumns()::contains);
                    if (illegal(kind, nativeMapping)) {
                        rule = "CURRENT_NATIVE_ADDRESS_REQUIRES_REVIEW";
                        review = true;
                    } else if (onlyStreet
                            && prepared.address() != null
                            && street != null
                            && !street.isEmpty()
                            && CdsExportNativeFieldAssessments.legal(street, 1000)) {
                        replace(original, prefix, "Formatted[1]", xml -> xml.text(street), local);
                        rule = "EXACT_CURRENT_FORMATTED_ADDRESS";
                    } else
                        // A city, province or postal code entered on an imported Formatted
                        // address, or a street the Formatted arm cannot hold: the current columns
                        // are written Structured, as for a never-imported chart, and supersede the
                        // Formatted source text.
                        rule = supersedeOrKeep(original, prefix, kind, prepared, nativeAddress, local);
                } else if (original.containsKey("Structured[1]")) {
                    if (illegal(kind, nativeMapping)) {
                        rule = "CURRENT_NATIVE_ADDRESS_REQUIRES_REVIEW";
                        review = true;
                    } else if (sourceOnly || nativeAddress == null)
                        rule = supersedeOrKeep(original, prefix, kind, prepared, nativeAddress, local);
                    else {
                        replace(
                                original,
                                prefix,
                                "Structured[1]",
                                xml -> structured(nativeAddress, xml),
                                local);
                        rule = "EXACT_CURRENT_STRUCTURED_ADDRESS";
                    }
                } else {
                    rule = "EXACT_CURRENT_ADDRESS_PARTS";
                    boolean superseded = false;
                    for (int part = 0; part < 4; part++) {
                        String column = kind.columns.get(part);
                        boolean changed =
                                projection.changedColumns().contains(column)
                                        || part == 3
                                                && projection
                                                        .changedColumns()
                                                        .contains(kind.columns.get(2));
                        if (!changed) continue;
                        checkpoint.run();
                        String path = partPath(part, lines);
                        String preparedPart = preparedPartValue(part, prepared);
                        boolean hadSource =
                                part == 0
                                        ? !lines.isEmpty()
                                        : original.keySet().stream()
                                                .anyMatch(
                                                        key ->
                                                                key.equals(path)
                                                                        || key.startsWith(
                                                                                path + "/"));
                        if (nativeMapping.fields().get(column).outcome()
                                == CdsExportDemographicMappingDto.Outcome.REQUIRES_REVIEW) {
                            review = true;
                            rule = "CURRENT_NATIVE_ADDRESS_REQUIRES_REVIEW";
                            continue;
                        }
                        boolean present = CdsExportDemographicSupportingMapper.present(
                                current.fields().get(column));
                        // A part the importer kept source-only left its column empty; an empty
                        // value a form save wrote there is not an edit of that part.
                        if (preparedPart == null && hadSource && !present) continue;
                        // A value typed over a source-only part, or a single-line street typed
                        // over a street of several source lines, is the truth after the edit:
                        // the superseded source part stays in the retained source and is listed
                        // as replaced.
                        if (preparedPart == null && hadSource || part == 0 && lines.size() > 1)
                            superseded |= present;
                        String value = nativePartValue(part, nativeAddress);
                        if (part == 0 && lines.size() > 1) {
                            for (String line : lines)
                                replace(
                                        original,
                                        prefix,
                                        line,
                                        line.equals("Structured[1]/Line1[1]") && value != null
                                                ? xml -> xml.text(value)
                                                : null,
                                        local);
                            if (!lines.contains("Structured[1]/Line1[1]") && value != null)
                                replace(
                                        original,
                                        prefix,
                                        "Structured[1]/Line1[1]",
                                        xml -> xml.text(value),
                                        local);
                        } else if (part == 3) {
                            CdsExportXmlComposer.Content content =
                                    value == null
                                            ? null
                                            : xml ->
                                                    xml.element(
                                                            "cds_dt",
                                                            nativeAddress.subdivision() != null
                                                                            && nativeAddress
                                                                                    .subdivision()
                                                                                    .startsWith(
                                                                                            "US-")
                                                                    ? "ZipCode"
                                                                    : "PostalCode",
                                                            value);
                            replace(original, prefix, path, content, local);
                        } else
                            replace(
                                    original,
                                    prefix,
                                    path,
                                    value == null ? null : xml -> xml.text(value),
                                    local);
                    }
                    if (superseded && !review)
                        rule = "EDITED_CURRENT_ADDRESS_PARTS_SUPERSEDE_SOURCE_PARTS";
                }
                changes.addAll(local);
            }
            for (String column : kind.columns) {
                String value = current.fields().get(column);
                fields.put(
                        new CdsExportNativeFieldKeyDto("demographic", current.recordKey(), column),
                        new CdsExportFieldAssessmentDto(
                                value == null
                                        ? Presence.SQL_NULL
                                        : value.isEmpty() ? Presence.EMPTY : Presence.VALUE,
                                review ? Outcome.REQUIRES_REVIEW : Outcome.MAPPED,
                                "Address[" + kind.wire + "]",
                                rule));
            }
        }
        values.requireActive();
        checkpoint.run();
        return new Result(index.sourceManifestSha256(), address, ids, fields, changes);
    }

    private static boolean hasSourceOnlyParts(
            CdsDemographicNativeDto.Address prepared,
            Map<String, CdsExportRetainedFieldDto> original,
            List<String> lines,
            boolean formatted) {
        return prepared.address() == null && (formatted || !lines.isEmpty())
                || prepared.city() == null && original.containsKey("Structured[1]/City[1]")
                || prepared.province() == null
                        && original.containsKey("Structured[1]/CountrySubdivisionCode[1]")
                || prepared.postal() == null
                        && original.keySet().stream()
                                .anyMatch(
                                        path -> path.startsWith("Structured[1]/PostalZipCode[1]"));
    }

    private static String partPath(int part, List<String> lines) {
        return part == 0
                ? lines.size() == 1 ? lines.getFirst() : "Structured[1]/Line1[1]"
                : part == 1
                        ? "Structured[1]/City[1]"
                        : part == 2
                                ? "Structured[1]/CountrySubdivisionCode[1]"
                                : "Structured[1]/PostalZipCode[1]";
    }

    private static String preparedPartValue(int part, CdsDemographicNativeDto.Address prepared) {
        return switch (part) {
            case 0 -> prepared.address();
            case 1 -> prepared.city();
            case 2 -> prepared.province();
            case 3 -> prepared.postal();
            default -> throw new IllegalStateException();
        };
    }

    /** A current column holding text no XML document can carry: the only address hold left. */
    private static boolean illegal(Kind kind, CdsExportDemographicMappingDto nativeMapping) {
        return kind.columns.stream()
                .anyMatch(
                        column ->
                                nativeMapping.fields().get(column).outcome()
                                        == CdsExportDemographicMappingDto.Outcome.REQUIRES_REVIEW);
    }

    /**
     * Replaces one imported address with the current one (or removes it when the chart records no
     * address of this type) and returns the rule. When the importer projected none of its parts
     * and the chart records no address, nothing the user saw was cleared: the source is kept.
     */
    private static String supersedeOrKeep(
            Map<String, CdsExportRetainedFieldDto> original,
            String prefix,
            Kind kind,
            CdsDemographicNativeDto.Address prepared,
            CdsExportDemographicMappingDto.Address nativeAddress,
            List<CdsExportRetainedSectionWriter.Change> changes) {
        boolean seen =
                prepared.address() != null
                        || prepared.city() != null
                        || prepared.province() != null
                        || prepared.postal() != null;
        if (nativeAddress == null && !seen)
            return "UNPROJECTED_SOURCE_ADDRESS_KEPT_WITHOUT_A_CURRENT_ADDRESS";
        supersede(original, prefix, kind, nativeAddress, changes);
        return nativeAddress == null
                ? "CLEARED_CURRENT_ADDRESS_SUPERSEDES_SOURCE_ADDRESS"
                : "EDITED_CURRENT_ADDRESS_SUPERSEDES_SOURCE_ADDRESS";
    }

    /** The whole imported address replaced by the current Structured one, or removed. */
    private static void supersede(
            Map<String, CdsExportRetainedFieldDto> original,
            String prefix,
            Kind kind,
            CdsExportDemographicMappingDto.Address nativeAddress,
            List<CdsExportRetainedSectionWriter.Change> changes) {
        replace(
                original,
                prefix,
                "",
                nativeAddress == null
                        ? null
                        : xml -> {
                            xml.attribute("", "addressType", kind.wire);
                            xml.start("cds_dt", "Structured");
                            structured(nativeAddress, xml);
                            xml.end();
                        },
                changes);
    }

    private static String nativePartValue(
            int part, CdsExportDemographicMappingDto.Address nativeAddress) {
        return nativeAddress == null
                ? null
                : switch (part) {
                    case 0 -> nativeAddress.line1();
                    case 1 -> nativeAddress.city();
                    case 2 -> nativeAddress.subdivision();
                    case 3 -> nativeAddress.postalCode();
                    default -> throw new IllegalStateException();
                };
    }

    private static void replace(
            Map<String, CdsExportRetainedFieldDto> fields,
            String prefix,
            String path,
            CdsExportXmlComposer.Content content,
            List<CdsExportRetainedSectionWriter.Change> changes) {
        var ids = new HashSet<String>();
        fields.forEach(
                (key, field) -> {
                    if (path.isEmpty() || key.equals(path) || key.startsWith(path + "/"))
                        ids.add(field.id());
                });
        if (!ids.isEmpty() || content != null)
            changes.add(
                    new CdsExportRetainedSectionWriter.Change(
                            prefix + (path.isEmpty() ? "" : "/" + path), ids, content));
    }

    private static void structured(
            CdsExportDemographicMappingDto.Address address, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        if (address.line1() != null) xml.element("cds_dt", "Line1", address.line1());
        if (address.city() != null) xml.element("cds_dt", "City", address.city());
        if (address.subdivision() != null)
            xml.element("cds_dt", "CountrySubdivisionCode", address.subdivision());
        if (address.postalCode() != null) {
            xml.start("cds_dt", "PostalZipCode");
            xml.element(
                    "cds_dt",
                    address.subdivision() != null && address.subdivision().startsWith("US-")
                            ? "ZipCode"
                            : "PostalCode",
                    address.postalCode());
            xml.end();
        }
    }

    private static String readKind(
            CdsExportRetainedSectionWriter.Values values,
            CdsExportRetainedFieldDto field,
            Runnable checkpoint)
            throws IOException {
        try (Reader input = values.text(field)) {
            checkpoint.run();
            int first = input.read(), end = input.read();
            if (first < 0 || end != -1)
                throw new IOException("CDS_EXPORT_DEMOGRAPHIC_ADDRESS_SOURCE_INVALID");
            return Character.toString((char) first);
        }
    }
}
