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
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsWireSchema;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.*;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/** Legal given/family/middle/prefix decisions; alternate source names and untouched part boundaries remain intact. */
public final class CdsExportDemographicNamesReconciler {
    private static final String ROOT = "/OmdCds[1]/PatientRecord[1]/Demographics[1]/Names[1]";
    private static final Pattern OTHER =
            Pattern.compile("LegalName\\[1\\]/OtherName\\[([1-9][0-9]{0,17})\\]/PartType\\[1\\]");

    private enum Part {
        GIVEN(
                "first_name",
                "LegalName[1]/FirstName[1]",
                CdsExportDemographicProjectionEvidence.Group.GIVEN),
        FAMILY(
                "last_name",
                "LegalName[1]/LastName[1]",
                CdsExportDemographicProjectionEvidence.Group.FAMILY),
        MIDDLE(
                "middleNames",
                "LegalName[1]/OtherName",
                CdsExportDemographicProjectionEvidence.Group.OTHER_GIVEN),
        PREFIX("title", "NamePrefix[1]", CdsExportDemographicProjectionEvidence.Group.PREFIX);
        final String column, path;
        final CdsExportDemographicProjectionEvidence.Group group;

        Part(String column, String path, CdsExportDemographicProjectionEvidence.Group group) {
            this.column = column;
            this.path = path;
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
                throw new IOException("CDS_EXPORT_DEMOGRAPHIC_NAMES_REQUIRES_REVIEW");
            return proposedChanges;
        }

        @Override
        public String toString() {
            return "CdsExportDemographicNamesReconciler.Result[protected]";
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
        var relative = new TreeMap<String, CdsExportRetainedFieldDto>();
        var sourceIds = new HashSet<String>();
        for (var field : source)
            if (field.sourcePath().startsWith(ROOT + "/")) {
                String path = field.sourcePath().substring(ROOT.length() + 1);
                verifyType(path, field);
                if (relative.putIfAbsent(path, field) != null)
                    throw new IOException("CDS_EXPORT_DEMOGRAPHIC_NAME_SOURCE_INVALID");
                sourceIds.add(field.id());
            }
        if (relative.isEmpty())
            throw new IOException("CDS_EXPORT_DEMOGRAPHIC_NAMES_SOURCE_REQUIRED");
        var base = new LinkedHashMap<>(current.fields());
        base.remove("demographic_no");
        var nativeNames =
                new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(patient, base));
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        var middle = new TreeMap<Long, String>();
        long maximumOther = 0;
        for (var entry : relative.entrySet()) {
            checkpoint.run();
            var match = OTHER.matcher(entry.getKey());
            if (!match.matches()) continue;
            long ordinal = Long.parseLong(match.group(1));
            maximumOther = Math.max(maximumOther, ordinal);
            String type = read(values, entry.getValue(), 8, checkpoint);
            if (type.equals("GIV"))
                middle.put(ordinal, entry.getKey().substring(0, entry.getKey().lastIndexOf('/')));
        }
        var purpose = relative.get("LegalName[1]/@namePurpose");
        boolean legal = purpose != null && "L".equals(read(values, purpose, 8, checkpoint));
        for (var part : Part.values()) {
            checkpoint.run();
            var projection = evidence.groups().get(part.group);
            String rule = null;
            boolean sourceValid = legal;
            if (part == Part.GIVEN || part == Part.FAMILY) {
                var kind = relative.get(part.path + "/PartType[1]");
                var text = relative.get(part.path + "/Part[1]");
                sourceValid &=
                        kind != null
                                && text != null
                                && (part == Part.GIVEN ? "GIV" : "FAMC")
                                        .equals(
                                                kind == null
                                                        ? null
                                                        : read(values, kind, 8, checkpoint));
                if (sourceValid)
                    sourceValid =
                            Objects.equals(
                                    part == Part.GIVEN
                                            ? evidence.preparation().firstName()
                                            : evidence.preparation().lastName(),
                                    read(values, text, 50, checkpoint));
            } else if (part == Part.PREFIX) {
                var prefix = relative.get(part.path);
                String expected = evidence.preparation().scalars().get("Names/NamePrefix");
                sourceValid &=
                        prefix == null
                                ? expected == null
                                : Objects.equals(expected, read(values, prefix, 50, checkpoint));
            } else {
                var old = new ArrayList<String>();
                for (String path : middle.values()) {
                    var field = relative.get(path + "/Part[1]");
                    if (field == null) {
                        sourceValid = false;
                        break;
                    }
                    old.add(read(values, field, 50, checkpoint));
                }
                String expected =
                        evidence.preparation()
                                .scalars()
                                .get(CdsDemographicNativeDto.MIDDLE_NAMES_SCALAR);
                // A null prepared projection explicitly leaves oversized/ambiguous originals
                // source-only.
                if (expected != null) sourceValid &= expected.equals(String.join(" ", old));
            }
            if (!sourceValid) rule = "SOURCE_NAME_PROJECTION_REQUIRES_REVIEW";
            else if (!projection.preparedProjectionProven())
                rule = "PREPARED_NATIVE_NAME_PROJECTION_NOT_PROVEN";
            else if (projection.unchanged()) rule = "VERIFIED_UNCHANGED_SOURCE_NAME_PARTS";
            else {
                var candidate = nativeNames.fields().get(part.column);
                String value =
                        switch (part) {
                            case GIVEN -> nativeNames.names().first();
                            case FAMILY -> nativeNames.names().last();
                            case MIDDLE -> nativeNames.names().middle();
                            case PREFIX -> nativeNames.names().prefix();
                        };
                if (candidate.outcome() == CdsExportDemographicMappingDto.Outcome.REQUIRES_REVIEW)
                    rule = "CURRENT_NATIVE_NAME_REQUIRES_REVIEW";
                else if (part == Part.MIDDLE
                        && (value != null && middle.size() > 1
                                || evidence.preparation()
                                                        .scalars()
                                                        .get(
                                                                CdsDemographicNativeDto
                                                                        .MIDDLE_NAMES_SCALAR)
                                                == null
                                        && !middle.isEmpty()))
                    rule = "EDITED_COMPOUND_OR_UNPROJECTED_MIDDLE_NAMES_REQUIRES_REVIEW";
                else {
                    if (part == Part.MIDDLE) {
                        if (middle.isEmpty() && value != null) {
                            String path = "LegalName[1]/OtherName[" + (maximumOther + 1) + "]";
                            String actual = value;
                            replace(
                                    relative,
                                    path,
                                    xml -> {
                                        xml.element("cds_dt", "Part", actual);
                                        xml.element("cds_dt", "PartType", "GIV");
                                    },
                                    changes);
                        } else
                            for (String path : middle.values()) {
                                if (value == null) replace(relative, path, null, changes);
                                else {
                                    String actual = value;
                                    replace(
                                            relative,
                                            path + "/Part[1]",
                                            xml -> xml.text(actual),
                                            changes);
                                }
                            }
                    } else {
                        String path = part == Part.PREFIX ? part.path : part.path + "/Part[1]";
                        String actual = value;
                        replace(
                                relative,
                                path,
                                actual == null ? null : xml -> xml.text(actual),
                                changes);
                    }
                    rule = "EXACT_CURRENT_NATIVE_NAME_PART";
                }
            }
            boolean review =
                    rule.endsWith("REQUIRES_REVIEW")
                            || rule.equals("PREPARED_NATIVE_NAME_PROJECTION_NOT_PROVEN");
            String raw = current.fields().get(part.column);
            fields.put(
                    new CdsExportNativeFieldKeyDto("demographic", current.recordKey(), part.column),
                    new CdsExportFieldAssessmentDto(
                            raw == null
                                    ? Presence.SQL_NULL
                                    : raw.isEmpty() ? Presence.EMPTY : Presence.VALUE,
                            review ? Outcome.REQUIRES_REVIEW : Outcome.MAPPED,
                            "Names/" + part.path,
                            rule));
        }
        values.requireActive();
        checkpoint.run();
        return new Result(index.sourceManifestSha256(), address, sourceIds, fields, changes);
    }

    private static void replace(
            Map<String, CdsExportRetainedFieldDto> fields,
            String path,
            CdsExportXmlComposer.Content content,
            List<CdsExportRetainedSectionWriter.Change> changes) {
        var ids = new HashSet<String>();
        fields.forEach(
                (key, field) -> {
                    if (key.equals(path) || key.startsWith(path + "/")) ids.add(field.id());
                });
        if (!ids.isEmpty() || content != null)
            changes.add(
                    new CdsExportRetainedSectionWriter.Change("Names[1]/" + path, ids, content));
    }

    private static void verifyType(String path, CdsExportRetainedFieldDto field)
            throws IOException {
        var type =
                CdsWireSchema.schemaModel()
                        .element("cds", "OmdCds")
                        .child("cds", "PatientRecord")
                        .child("cds", "Demographics")
                        .child("cds", "Names");
        String namespace = "cds";
        for (String segment : path.split("/")) {
            if (type == null) throw new IOException("CDS_EXPORT_DEMOGRAPHIC_NAME_SOURCE_INVALID");
            if (segment.startsWith("@")) {
                namespace = "";
                type = type.attribute(namespace, segment.substring(1));
            } else {
                int end = segment.indexOf('[');
                if (end <= 0) throw new IOException("CDS_EXPORT_DEMOGRAPHIC_NAME_SOURCE_INVALID");
                String name = segment.substring(0, end);
                var candidates =
                        type.childNames().stream()
                                .filter(child -> child.local().equals(name))
                                .toList();
                if (candidates.size() != 1)
                    throw new IOException("CDS_EXPORT_DEMOGRAPHIC_NAME_SOURCE_INVALID");
                namespace = candidates.getFirst().namespace();
                type = type.child(namespace, name);
            }
        }
        if (type == null
                || !namespace.equals(field.namespace())
                || !field.valueType()
                        .equals("{" + type.getTypeNamespace() + "}" + type.getTypeName()))
            throw new IOException("CDS_EXPORT_DEMOGRAPHIC_NAME_SOURCE_INVALID");
    }

    private static String read(
            CdsExportRetainedSectionWriter.Values values,
            CdsExportRetainedFieldDto field,
            int maximum,
            Runnable checkpoint)
            throws IOException {
        var text = new StringBuilder();
        char[] buffer = new char[128];
        try (Reader input = values.text(field)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                checkpoint.run();
                if (count == 0 || text.length() + count > maximum * 2)
                    throw new IOException("CDS_EXPORT_DEMOGRAPHIC_NAME_SOURCE_LIMIT");
                text.append(buffer, 0, count);
            }
        } finally {
            Arrays.fill(buffer, '\0');
        }
        if (text.codePointCount(0, text.length()) > maximum)
            throw new IOException("CDS_EXPORT_DEMOGRAPHIC_NAME_SOURCE_LIMIT");
        return text.toString();
    }
}
