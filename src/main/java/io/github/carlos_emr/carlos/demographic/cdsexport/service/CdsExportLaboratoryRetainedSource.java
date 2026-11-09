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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsLaboratoryNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/** Retained laboratory values remain bound to their sealed preparation and original shift, including all reviewer dates. */
public final class CdsExportLaboratoryRetainedSource {
    static final Map<String, String> NATIVE_SCALARS = Map.ofEntries(
            Map.entry("laboratoryName", "LaboratoryName"), Map.entry("reportedTestName", "TestNameReportedByLab"),
            Map.entry("testCode", "LabTestCode"), Map.entry("testName", "TestName"), Map.entry("accessionNumber", "AccessionNumber"),
            Map.entry("resultValue", "Result/Value"), Map.entry("unitOfMeasure", "Result/UnitOfMeasure"),
            Map.entry("lowLimit", "ReferenceRange/LowLimit"), Map.entry("highLimit", "ReferenceRange/HighLimit"),
            Map.entry("referenceRange", "ReferenceRange/ReferenceRangeText"), Map.entry("sourceBlocked", "BlockedTestResult"),
            Map.entry("resultStatus", "TestResultStatus"), Map.entry("informationFromLab", "TestResultsInformationReportedByTheLab"),
            Map.entry("notesFromLab", "NotesFromLab"), Map.entry("physiciansNotes", "PhysiciansNotes"));
    private static final Set<String> FLAGS = Set.of("ResultNormalAbnormalFlag/resultNormalAbnormalFlagAsEnum", "ResultNormalAbnormalFlag/resultNormalAbnormalFlagAsPlainText");
    private static final Pattern DATE = Pattern.compile("((?:CollectionDateTime|LabRequisitionDateTime)\\[1\\]|ResultReviewer\\[[1-9][0-9]{0,17}\\]/DateTimeResultReviewed\\[1\\])/(YearOnly|YearMonth|FullDate|FullDateTime)\\[1\\]");
    public record Result(Set<String> sourceFields, Map<String, CdsExportDateDto> dates, List<CdsExportRetainedSectionWriter.Change> changes) {
        public Result { sourceFields = Set.copyOf(sourceFields); dates = Map.copyOf(dates); changes = List.copyOf(changes); }
        @Override public String toString() { return "CdsExportLaboratoryRetainedSource.Result[protected]"; }
    }
    public Result read(int patient, String address, CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> supplied, CdsExportRetainedSectionWriter.Values values, CdsExportRunControl control) throws IOException {
        control.authorizeCategory(patient, CdsCategory.LABORATORY_RESULTS, true); values.requireActive();
        var prepared = new CdsExportRetainedPreparationReader().laboratory(index, address).value();
        var fields = new CdsExportRetainedOccurrenceFields().fieldsFor(patient, CdsCategory.LABORATORY_RESULTS, address, index, checked, supplied, control::checkpoint);
        var options = new CdsExportRetainedImportOptionsReader().fromCaptured(index, address.substring(0, 36));
        var result = readVerified(fields, options, prepared, values, control::checkpoint);
        values.requireActive(); control.authorizeCategory(patient, CdsCategory.LABORATORY_RESULTS, true); control.checkpoint(); return result;
    }
    Result readVerified(List<CdsExportRetainedFieldDto> fields, CdsExportRetainedImportOptionsReader.Options options,
            CdsLaboratoryNativeDto prepared, CdsExportRetainedSectionWriter.Values values, Runnable checkpoint) throws IOException {
        Objects.requireNonNull(values); Objects.requireNonNull(checkpoint); checkpoint.run(); values.requireActive();
        if (fields == null || fields.isEmpty() || options == null || prepared == null || prepared.collection() == null || prepared.requisition() == null) invalid();
        new CdsExportRetainedSectionWriter().prepare(fields, values, checkpoint);
        String root = CdsExportRetainedOccurrencePath.root(fields.getFirst(), CdsCategory.LABORATORY_RESULTS), patientKey = fields.getFirst().sourcePatientKey();
        var ids = new HashSet<String>(); var paths = new HashSet<String>(); var selected = new HashMap<String, String>();
        var dates = new LinkedHashMap<String, CdsExportDateDto>(); var temporal = new HashMap<String, CdsTemporalDto>(); var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        var known = new HashSet<>(NATIVE_SCALARS.values()); known.addAll(FLAGS);
        var type = CdsWireSchema.schemaModel().element("cds", "OmdCds").child("cds", "PatientRecord").child("cds", "LaboratoryResults");
        for (var field : fields) {
            checkpoint.run();
            if (!options.importRunId().equals(field.runId()) || !patientKey.equals(field.sourcePatientKey())
                    || !root.equals(CdsExportRetainedOccurrencePath.root(field, CdsCategory.LABORATORY_RESULTS)) || !ids.add(field.id())) invalid();
            String path = field.sourcePath().length() == root.length() ? "" : field.sourcePath().substring(root.length() + 1); if (!paths.add(path)) invalid();
            String simple = path.replaceAll("\\[[1-9][0-9]{0,17}\\]", "");
            if (known.contains(simple)) {
                var scalar = type;
                for (String part : simple.split("/")) {
                    var child = scalar.childNames().stream().filter(name -> name.local().equals(part)).findFirst().orElseThrow(() -> new IOException("CDS_EXPORT_LABORATORY_SOURCE_INVALID"));
                    scalar = scalar.child(child.namespace(), child.local());
                }
                String raw = text(values, field, 131_072, checkpoint), logical = normalize(raw, scalar.whitespace());
                if (selected.putIfAbsent(simple, logical) != null) invalid();
            }
            var match = DATE.matcher(path); if (!match.matches()) continue;
            String parent = match.group(1), choice = match.group(2), lexical = text(values, field, 128, checkpoint); CdsTemporalDto effective;
            try {
                var original = CdsTemporal.parse(choice.equals("YearOnly") ? lexical : null, choice.equals("YearMonth") ? lexical : null,
                        choice.equals("FullDate") ? lexical : null, choice.equals("FullDateTime") ? lexical : null, parent);
                effective = CdsTemporal.shift(original, options.timeshiftDays(), parent);
            } catch (RuntimeException invalid) { throw new IOException("CDS_EXPORT_LABORATORY_SOURCE_DATE_INVALID"); }
            if (temporal.putIfAbsent(parent, effective) != null) invalid();
            var wire = new CdsExportPreparedTemporalMapper().map(effective, true); dates.put(parent, wire);
            if (!lexical.equals(wire.lexical())) { String replacement = wire.lexical(); changes.add(new CdsExportRetainedSectionWriter.Change(path, Set.of(field.id()), xml -> xml.text(replacement))); }
        }
        if (!selected.equals(prepared.values()) || !prepared.collection().equals(temporal.getOrDefault("CollectionDateTime[1]", CdsTemporalDto.unknown()))
                || !prepared.requisition().equals(temporal.getOrDefault("LabRequisitionDateTime[1]", CdsTemporalDto.unknown())))
            throw new IOException("CDS_EXPORT_LABORATORY_SOURCE_PREPARATION_MISMATCH");
        new CdsExportRetainedSectionWriter().prepareChanges(fields, values, changes, checkpoint); values.requireActive(); checkpoint.run(); return new Result(ids, dates, changes);
    }
    private static String normalize(String raw, String rule) throws IOException {
        if (!Set.of("preserve", "replace", "collapse").contains(rule)) invalid();
        if (rule.equals("preserve")) return raw;
        String replaced = raw.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
        return rule.equals("replace") ? replaced : replaced.replaceAll(" +", " ").replaceAll("(^ )|( $)", "");
    }
    private static String text(CdsExportRetainedSectionWriter.Values values, CdsExportRetainedFieldDto field, int maximum, Runnable checkpoint) throws IOException {
        var result = new StringBuilder(); char[] buffer = new char[4096];
        try (Reader input = values.text(field)) {
            for (int n; (n = input.read(buffer)) != -1; ) { checkpoint.run(); if (n == 0 || result.length() > maximum - n) invalid(); result.append(buffer, 0, n); }
        } finally { Arrays.fill(buffer, '\0'); }
        return result.toString();
    }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_LABORATORY_SOURCE_INVALID"); }
}
