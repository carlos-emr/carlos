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

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsHistoryNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/** Adds current native revisions without discarding unprojected fields from the original import. */
final class CdsExportHistoryNativeReconciler {
    CdsExportHistoryFieldReconciler.Result reconcile(
            int patient,
            CdsCategory category,
            String address,
            String manifest,
            CdsHistoryNativeDto prepared,
            CdsNativeRowState original,
            CdsExportHistoryFieldReconciler.Current current,
            List<CdsExportRetainedFieldDto> sourceFields,
            CdsExportHistoryRetainedSource.Result source,
            CdsExportNativeHistoryMapper.Result mapped,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        checkpoint.run();
        if (mapped.value().category() != category
                || sourceFields.isEmpty()
                || !CdsExportCaseManagementProjectionEvidence.matches(
                        original, "demographic_no", Integer.toString(patient))
                || !CdsExportCaseManagementProjectionEvidence.matches(
                        original, "note", prepared.content())
                || !CdsExportCaseManagementProjectionEvidence.matches(
                        original,
                        "provider_no",
                        prepared.providerNo() == null ? "" : prepared.providerNo()))
            throw new IOException("CDS_EXPORT_HISTORY_NATIVE_ORIGINAL_BINDING_INVALID");
        var changes = new LinkedHashMap<String, CdsExportRetainedSectionWriter.Change>();
        for (var change : source.changes()) changes.put(change.relativePath(), change);
        var nativeValue = mapped.value();
        if (category == CdsCategory.CLINICAL_NOTES) {
            replace(
                    changes,
                    sourceFields,
                    "MyClinicalNotesContent[1]",
                    xml -> xml.text(nativeValue.text().get("MyClinicalNotesContent")));
            if (nativeValue.text().containsKey("NoteType"))
                replace(
                        changes,
                        sourceFields,
                        "NoteType[1]",
                        xml -> xml.text(nativeValue.text().get("NoteType")));
            // Original source participant/reviewer entries remain intact. New native public
            // authors are already retained with their exact timestamps in the revision carrier.
            String observed = current.note().value().fields().get("observation_date");
            if (!CdsExportCaseManagementProjectionEvidence.matches(
                            original, "observation_date", observed)
                    && nativeValue.dates().containsKey("EventDateTime"))
                replace(
                        changes,
                        sourceFields,
                        "EventDateTime[1]",
                        xml ->
                                CdsExportNativeHistoryXmlWriter.dateContents(
                                        xml, nativeValue.dates().get("EventDateTime")));
        } else {
            boolean edited =
                    !CdsExportCaseManagementProjectionEvidence.matches(
                            original, "note", current.note().value().fields().get("note"));
            for (var entry : nativeValue.text().entrySet()) {
                String name = entry.getKey(), value = entry.getValue();
                if (name.equals("NativePersonalNarrative")) continue;
                if (!edited
                        && Set.of(
                                        "ProblemDiagnosisProcedureDescription",
                                        "PastHealthProblemDescriptionOrProcedures",
                                        "ProblemDiagnosisDescription",
                                        "RiskFactor",
                                        "AlertDescription",
                                        "Notes")
                                .contains(name)) continue;
                if (name.equals("Notes")) {
                    String previous = read(sourceFields, "Notes[1]", values, 64000, checkpoint);
                    if (previous != null && !previous.equals(value))
                        value = previous + "\n\n[Current native narrative]\n" + value;
                    int maximum =
                            category == CdsCategory.FAMILY_HISTORY
                                    ? 32000
                                    : category == CdsCategory.ALERTS_AND_SPECIAL_NEEDS
                                            ? 1000
                                            : 64000;
                    if (!CdsExportNativeFieldAssessments.legal(value, maximum))
                        review("CDS_EXPORT_HISTORY_COMBINED_NARRATIVE_CAPACITY");
                }
                String exact = value;
                replace(changes, sourceFields, name + "[1]", xml -> xml.text(exact));
            }
            for (var entry : nativeValue.dates().entrySet())
                replace(
                        changes,
                        sourceFields,
                        entry.getKey() + "[1]",
                        xml -> CdsExportNativeHistoryXmlWriter.dateContents(xml, entry.getValue()));
            if (nativeValue.code() != null) {
                String name =
                        category == CdsCategory.PROBLEM_LIST
                                ? "DiagnosisCode"
                                : "DiagnosisProcedureCode";
                replace(
                        changes,
                        sourceFields,
                        name + "[1]",
                        xml -> {
                            xml.element(
                                    "cds_dt", "StandardCodingSystem", nativeValue.code().system());
                            xml.element("cds_dt", "StandardCode", nativeValue.code().value());
                            xml.element(
                                    "cds_dt",
                                    "StandardCodeDescription",
                                    nativeValue.code().description());
                        });
            }
            var json = JsonMapper.builder().build();
            String metadata =
                    json.writeValueAsString(
                            Map.of(
                                    "version",
                                    1,
                                    "originalImportedProjection",
                                    prepared.content(),
                                    "nativeHistory",
                                    json.readTree(nativeValue.metadata())));
            var pattern =
                    Pattern.compile("^ResidualInfo\\[1\\]/DataElement\\[([1-9][0-9]*)\\](?:/|$)");
            long ordinal = 0;
            boolean residual = false;
            String root = CdsExportRetainedOccurrencePath.root(sourceFields.getFirst(), category);
            for (var field : sourceFields) {
                String relative =
                        field.sourcePath().equals(root)
                                ? ""
                                : field.sourcePath().substring(root.length() + 1);
                if (relative.startsWith("ResidualInfo[1]")) residual = true;
                var match = pattern.matcher(relative);
                if (match.find()) ordinal = Math.max(ordinal, Long.parseLong(match.group(1)));
            }
            var markers = new LinkedHashMap<String, String>();
            markers.put(
                    CdsNativeExtensionContract.HISTORY_ARCHIVED,
                    Boolean.toString(nativeValue.archived()));
            if (category == CdsCategory.PERSONAL_HISTORY
                    && nativeValue.text().containsKey("NativePersonalNarrative"))
                markers.put(
                        CdsNativeExtensionContract.HISTORY_PERSONAL_NARRATIVE,
                        nativeValue.text().get("NativePersonalNarrative"));
            if (!residual) {
                replace(
                        changes,
                        sourceFields,
                        "ResidualInfo[1]",
                        xml -> {
                            CdsExportNativeHistoryXmlWriter.residualElement(
                                    xml, "CARLOS.NativeHistory.v1", "string", metadata);
                            for (var marker : markers.entrySet())
                                CdsExportNativeHistoryXmlWriter.residualElement(
                                        xml,
                                        marker.getKey(),
                                        marker.getKey()
                                                        .equals(
                                                                CdsNativeExtensionContract
                                                                        .HISTORY_ARCHIVED)
                                                ? "boolean"
                                                : "string",
                                        marker.getValue());
                        });
            } else {
                replace(
                        changes,
                        sourceFields,
                        "ResidualInfo[1]/DataElement[" + (++ordinal) + "]",
                        xml -> {
                            xml.element("cds_dt", "Name", "CARLOS.NativeHistory.v1");
                            xml.element("cds_dt", "DataType", "string");
                            xml.element("cds_dt", "Content", metadata);
                        });
                for (var marker : markers.entrySet()) {
                    String existing = null;
                    for (var field : sourceFields) {
                        String relative = field.sourcePath().substring(root.length() + 1);
                        if (!relative.startsWith("ResidualInfo[1]/DataElement[")
                                || !relative.endsWith("/Name[1]")) continue;
                        // A name longer than this exact marker cannot identify it. The retained
                        // writer still authenticates every untouched field to EOF.
                        if (field.logicalInline() != null && field.logicalInline().length() > 128)
                            continue;
                        String name = read(sourceFields, relative, values, 128, checkpoint);
                        if (!marker.getKey().equals(name)) continue;
                        if (existing != null) review("CDS_EXPORT_HISTORY_NATIVE_MARKER_AMBIGUOUS");
                        existing = relative.substring(0, relative.length() - "/Name[1]".length());
                    }
                    String type =
                            marker.getKey().equals(CdsNativeExtensionContract.HISTORY_ARCHIVED)
                                    ? "boolean"
                                    : "string";
                    if (existing != null) {
                        if (!type.equals(
                                read(
                                        sourceFields,
                                        existing + "/DataType[1]",
                                        values,
                                        7,
                                        checkpoint)))
                            review("CDS_EXPORT_HISTORY_NATIVE_MARKER_TYPE_INVALID");
                        replace(
                                changes,
                                sourceFields,
                                existing + "/Content[1]",
                                xml -> xml.text(marker.getValue()));
                    } else {
                        replace(
                                changes,
                                sourceFields,
                                "ResidualInfo[1]/DataElement[" + (++ordinal) + "]",
                                xml -> {
                                    xml.element("cds_dt", "Name", marker.getKey());
                                    xml.element("cds_dt", "DataType", type);
                                    xml.element("cds_dt", "Content", marker.getValue());
                                });
                    }
                }
            }
        }
        return new CdsExportHistoryFieldReconciler.Result(
                manifest,
                address,
                category,
                mapped.fields(),
                new ArrayList<>(changes.values()),
                source.sourceFields());
    }

    private static void replace(
            Map<String, CdsExportRetainedSectionWriter.Change> changes,
            List<CdsExportRetainedFieldDto> fields,
            String relative,
            CdsExportXmlComposer.Content content)
            throws IOException {
        var ids = new HashSet<String>();
        String root =
                CdsExportRetainedOccurrencePath.root(
                        fields.getFirst(), fields.getFirst().category());
        for (var field : fields)
            if (field.sourcePath().equals(root + "/" + relative)
                    || field.sourcePath().startsWith(root + "/" + relative + "/"))
                ids.add(field.id());
        changes.keySet().removeIf(p -> p.equals(relative) || p.startsWith(relative + "/"));
        changes.put(relative, new CdsExportRetainedSectionWriter.Change(relative, ids, content));
    }

    private static String read(
            List<CdsExportRetainedFieldDto> fields,
            String relative,
            CdsExportRetainedSectionWriter.Values values,
            int maximum,
            Runnable checkpoint)
            throws IOException {
        String root =
                CdsExportRetainedOccurrencePath.root(
                        fields.getFirst(), fields.getFirst().category());
        var matches =
                fields.stream().filter(f -> f.sourcePath().equals(root + "/" + relative)).toList();
        if (matches.isEmpty()) return null;
        if (matches.size() != 1) throw new IOException("CDS_EXPORT_HISTORY_SOURCE_FIELD_AMBIGUOUS");
        var result = new StringBuilder();
        try (var reader = values.text(matches.getFirst())) {
            char[] buffer = new char[4096];
            for (int n; (n = reader.read(buffer)) != -1; ) {
                checkpoint.run();
                result.append(buffer, 0, n);
                if (result.length() > 2L * maximum)
                    review("CDS_EXPORT_HISTORY_SOURCE_NARRATIVE_CAPACITY");
            }
        }
        String value = result.toString();
        if (!CdsExportNativeFieldAssessments.legal(value, maximum))
            review("CDS_EXPORT_HISTORY_SOURCE_NARRATIVE_CAPACITY");
        return value;
    }

    private static void review(String code) throws CdsExportMappingReviewException {
        throw new CdsExportMappingReviewException(code);
    }
}
