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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Pure binding of complete care source fields to sealed original child preparations; current native receipts are a separate prerequisite. */
final class CdsExportCareRetainedSource {
    private static final Pattern LEAF =
            Pattern.compile("([A-Za-z]+\\[[1-9][0-9]{0,17}\\])/([A-Za-z]+)\\[1\\]");

    record Result(
            Set<String> sourceFields,
            Map<String, CdsExportDateDto> dates,
            List<CdsExportRetainedSectionWriter.Change> changes) {
        Result {
            sourceFields = Set.copyOf(sourceFields);
            dates = Map.copyOf(dates);
            changes = List.copyOf(changes);
        }

        @Override
        public String toString() {
            return "CdsExportCareRetainedSource.Result[protected]";
        }
    }

    Result readVerified(
            List<CdsExportRetainedFieldDto> fields,
            CdsExportRetainedImportOptionsReader.Options options,
            Map<String, CdsCareElementDto> prepared,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        checkpoint.run();
        values.requireActive();
        if (fields == null || fields.isEmpty() || options == null || prepared == null) invalid();
        new CdsExportRetainedSectionWriter().prepare(fields, values, checkpoint);
        String
                root =
                        CdsExportRetainedOccurrencePath.root(
                                fields.getFirst(), CdsCategory.CARE_ELEMENTS),
                patientKey = fields.getFirst().sourcePatientKey();
        var groups = new LinkedHashMap<String, Map<String, CdsExportRetainedFieldDto>>();
        var ids = new HashSet<String>();
        for (var field : fields) {
            checkpoint.run();
            if (!options.importRunId().equals(field.runId())
                    || !patientKey.equals(field.sourcePatientKey())
                    || !root.equals(
                            CdsExportRetainedOccurrencePath.root(field, CdsCategory.CARE_ELEMENTS))
                    || !ids.add(field.id())) invalid();
            String path =
                    field.sourcePath().length() == root.length()
                            ? ""
                            : field.sourcePath().substring(root.length() + 1);
            if (path.isEmpty()) continue;
            var match = LEAF.matcher(path);
            if (!match.matches()) invalid();
            String child = match.group(1),
                    leaf = match.group(2),
                    element = child.substring(0, child.indexOf('['));
            if (!CdsCareScalarContract.ELEMENTS.contains(element)
                    || groups.computeIfAbsent(child, ignored -> new LinkedHashMap<>())
                                    .putIfAbsent(leaf, field)
                            != null) invalid();
        }
        if (!groups.keySet().equals(prepared.keySet())) invalid();
        var dates = new LinkedHashMap<String, CdsExportDateDto>();
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        for (var group : groups.entrySet()) {
            checkpoint.run();
            String child = group.getKey(), element = child.substring(0, child.indexOf('['));
            var candidate = prepared.get(child);
            if (candidate == null
                    || !element.equals(candidate.element())
                    || candidate.date() == null
                    || candidate.date().precision() != CdsTemporalDto.Precision.FULL_DATE)
                invalid();
            var dateField = group.getValue().get("Date");
            if (dateField == null) invalid();
            String lexical = text(values, dateField, 128, checkpoint);
            CdsTemporalDto effective;
            try {
                effective =
                        CdsTemporal.shift(
                                CdsTemporal.parse(null, null, lexical, null, child + "/Date"),
                                options.timeshiftDays(),
                                child + "/Date");
            } catch (RuntimeException malformed) {
                throw new IOException("CDS_EXPORT_CARE_SOURCE_DATE_INVALID");
            }
            if (!effective.equals(candidate.date())) mismatch();
            var wire = new CdsExportPreparedTemporalMapper().map(effective, false);
            dates.put(child, wire);
            if (!lexical.equals(wire.lexical())) {
                String replacement = wire.lexical();
                changes.add(
                        new CdsExportRetainedSectionWriter.Change(
                                child + "/Date[1]",
                                Set.of(dateField.id()),
                                xml -> xml.text(replacement)));
            }
            var logical = new HashMap<String, String>();
            for (var entry : group.getValue().entrySet()) {
                if (entry.getKey().equals("Date")
                        || entry.getKey().equals("DocumentedGoals") && candidate.nativeOverflow())
                    continue;
                String raw = text(values, entry.getValue(), 131072, checkpoint);
                var type =
                        CdsWireSchema.schemaModel()
                                .element("cds", "OmdCds")
                                .child("cds", "PatientRecord")
                                .child("cds", "CareElements")
                                .child("cds", element);
                var names =
                        type.childNames().stream()
                                .filter(name -> name.local().equals(entry.getKey()))
                                .toList();
                if (names.size() != 1) invalid();
                String whitespace =
                        type.child(names.getFirst().namespace(), entry.getKey()).whitespace();
                logical.put(entry.getKey(), normalize(raw, whitespace));
            }
            bind(candidate, logical, group.getValue().get("DocumentedGoals"));
        }
        new CdsExportRetainedSectionWriter().prepareChanges(fields, values, changes, checkpoint);
        values.requireActive();
        checkpoint.run();
        return new Result(ids, dates, changes);
    }

    private static void bind(
            CdsCareElementDto prepared, Map<String, String> source, CdsExportRetainedFieldDto goal)
            throws IOException {
        String type, value, unit = null, code = null;
        boolean uninterpreted = false, overflow = false;
        switch (prepared.element()) {
            case "SmokingStatus" -> {
                type = "SKST";
                value = yesNo(required(source, "Status"));
            }
            case "SmokingPacks" -> {
                type = "POSK";
                value = required(source, "PerDay");
                unit = "pack/day";
            }
            case "Weight" -> {
                type = "WT";
                value = required(source, "Weight");
                unit = unit(source, "WeightUnit", "kg");
            }
            case "Height" -> {
                type = "HT";
                value = required(source, "Height");
                unit = unit(source, "HeightUnit", "cm");
            }
            case "WaistCircumference" -> {
                type = "WAIS";
                value = required(source, "WaistCircumference");
                unit = unit(source, "WaistCircumferenceUnit", "cm");
            }
            case "BloodPressure" -> {
                type = "BP";
                unit = unit(source, "BPUnit", "mmHg");
                String high = required(source, "SystolicBP"), low = required(source, "DiastolicBP");
                String normalizedHigh = CdsCareScalarContract.nonnegativeDecimal(high),
                        normalizedLow = CdsCareScalarContract.nonnegativeDecimal(low);
                if (normalizedHigh == null || normalizedLow == null) {
                    type = "CDS_CARE_VALUE";
                    uninterpreted = true;
                    value = "Systolic: " + high + "; Diastolic: " + low;
                } else value = normalizedHigh + "/" + normalizedLow;
            }
            case "DiabetesComplicationsScreening" -> {
                type = "CDS_EXAM";
                value = "Performed";
                code = required(source, "ExamCode");
                if (!Set.of("32468-1", "11397-7", "67536-3").contains(code)) mismatch();
            }
            case "DiabetesMotivationalCounselling" -> {
                type =
                        Map.of(
                                        "Nutrition",
                                        "MCCN",
                                        "Exercise",
                                        "MCCE",
                                        "Smoking Cessation",
                                        "MCCS",
                                        "Other",
                                        "MCCO")
                                .get(required(source, "CounsellingPerformed"));
                if (type == null) mismatch();
                value = "Yes";
            }
            case "DiabetesSelfManagementCollaborative" -> {
                type = "CDS_GOALS";
                code = unit(source, "CodeValue", "44943-9");
                if (goal == null || prepared.sourceValueDigest() == null) mismatch();
                String digest;
                long bytes;
                if (goal.logicalInline() != null)
                    bytes = goal.logicalInline().getBytes(StandardCharsets.UTF_8).length;
                else if (goal.originalInline() != null)
                    bytes = goal.originalInline().getBytes(StandardCharsets.UTF_8).length;
                else if (goal.originalPayload() != null) bytes = goal.originalPayload().bytes();
                else {
                    invalid();
                    return;
                }
                if (goal.originalInline() != null)
                    digest =
                            HexFormat.of()
                                    .formatHex(
                                            CdsImportKeys.digest()
                                                    .digest(
                                                            goal.originalInline()
                                                                    .getBytes(
                                                                            StandardCharsets
                                                                                    .UTF_8)));
                else if (goal.originalPayload() != null) digest = goal.originalPayload().sha256();
                else {
                    invalid();
                    return;
                }
                overflow = bytes > CdsCareScalarContract.GOAL_NATIVE_BYTE_BUDGET;
                if (!digest.equals(prepared.sourceValueDigest())
                        || overflow != prepared.nativeOverflow()) mismatch();
                value =
                        overflow
                                ? CdsCareScalarContract.OVERFLOW_GOALS
                                : required(source, "DocumentedGoals");
            }
            case "DiabetesSelfManagementChallenges" -> {
                type = "SMCD";
                code = unit(source, "CodeValue", "44941-3");
                value = yesNo(required(source, "ChallengesIdentified"));
            }
            case "DiabetesEducationalSelfManagement" -> {
                type = "DMME";
                value = yesNo(required(source, "EducationalTrainingPerformed"));
            }
            case "SelfMonitoringBloodGlucose" -> {
                type = "SMBG";
                value = yesNo(required(source, "SelfMonitoring"));
            }
            case "HypoglycemicEpisodes" -> {
                type = "HYPE";
                value = required(source, "NumOfReportedEpisodes");
            }
            default -> {
                invalid();
                return;
            }
        }
        if (Set.of("WT", "HT", "WAIS", "POSK", "HYPE").contains(type)) {
            String number = CdsCareScalarContract.nonnegativeDecimal(value);
            if (number == null || type.equals("HYPE") && number.contains(".")) {
                type = "CDS_CARE_VALUE";
                uninterpreted = true;
            } else value = number;
        }
        if (!Objects.equals(type, prepared.measurementType())
                || !Objects.equals(value, prepared.value())
                || !Objects.equals(unit, prepared.unit())
                || !Objects.equals(code, prepared.code())
                || overflow != prepared.nativeOverflow()
                || uninterpreted != prepared.uninterpreted()
                || !prepared.element().equals("DiabetesSelfManagementCollaborative")
                        && prepared.sourceValueDigest() != null) mismatch();
    }

    private static String required(Map<String, String> source, String field) throws IOException {
        String value = source.get(field);
        if (value == null) invalid();
        return value;
    }

    private static String unit(Map<String, String> source, String field, String expected)
            throws IOException {
        String value = required(source, field);
        if (!expected.equals(value)) mismatch();
        return value;
    }

    private static String yesNo(String value) throws IOException {
        if (Set.of("Y", "y").contains(value)) return "Yes";
        if (Set.of("N", "n").contains(value)) return "No";
        invalid();
        return null;
    }

    private static String normalize(String value, String whitespace) throws IOException {
        if (whitespace.equals("preserve")) return value;
        if (!Set.of("replace", "collapse").contains(whitespace)) invalid();
        String replaced = value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
        return whitespace.equals("replace")
                ? replaced
                : replaced.replaceAll(" +", " ").replaceAll("(^ )|( $)", "");
    }

    private static String text(
            CdsExportRetainedSectionWriter.Values values,
            CdsExportRetainedFieldDto field,
            int maximum,
            Runnable checkpoint)
            throws IOException {
        var result = new StringBuilder();
        char[] buffer = new char[4096];
        try (Reader input = values.text(field)) {
            for (int n; (n = input.read(buffer)) != -1; ) {
                checkpoint.run();
                if (n == 0 || result.length() > maximum - n) invalid();
                result.append(buffer, 0, n);
            }
        } finally {
            Arrays.fill(buffer, '\0');
        }
        return result.toString();
    }

    private static void mismatch() throws IOException {
        throw new IOException("CDS_EXPORT_CARE_SOURCE_PREPARATION_MISMATCH");
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_CARE_SOURCE_INVALID");
    }
}
