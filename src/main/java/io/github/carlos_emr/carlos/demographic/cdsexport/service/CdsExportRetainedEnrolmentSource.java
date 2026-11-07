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

import io.github.carlos_emr.carlos.commn.model.CdsClinicalException;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsWireSchema;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicDetailsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedFieldDto;
import java.io.IOException;
import java.io.Reader;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Rebuilds every retained enrolment event using the verified original import shift.
 * Callers authenticate the parent field set, original options and sealed preparation
 * before using this pure mapper. It grants no disclosure or whole-chart coverage.
 */
public final class CdsExportRetainedEnrolmentSource {
    private static final String ROOT = "/OmdCds[1]/PatientRecord[1]/Demographics[1]/";
    private static final Pattern HISTORY = Pattern.compile("Enrolment\\[1\\]/EnrolmentHistory\\[([1-9][0-9]*)\\](?:/(.*))?");
    private static final Set<String> KNOWN = Set.of("EnrollmentStatus", "EnrollmentDate", "EnrollmentTerminationDate", "TerminationReason",
            "EnrolledToPhysician/Name/FirstName", "EnrolledToPhysician/Name/LastName", "EnrolledToPhysician/OHIPPhysicianId");
    public record History(int ordinal, String status, LocalDate start, LocalDate end, String reason, Map<String, String> values) {
        public History { values = Map.copyOf(values); }
        public LocalDate effective() { return end == null ? start : end; }
        @Override public String toString() { return "CdsExportRetainedEnrolmentSource.History[protected]"; }
    }
    public record Result(List<History> histories, int selectedOrdinal, Set<String> sourceFields,
            List<CdsExportRetainedSectionWriter.Change> changes, String selectedNativeProvider) {
        public Result { histories = List.copyOf(histories); sourceFields = Set.copyOf(sourceFields); changes = List.copyOf(changes); }
        @Override public String toString() { return "CdsExportRetainedEnrolmentSource.Result[protected]"; }
    }
    public Result read(List<CdsExportRetainedFieldDto> supplied, CdsExportRetainedSectionWriter.Values values,
            CdsExportRetainedImportOptionsReader.Options options, CdsDemographicDetailsDto preparation, Runnable checkpoint) throws IOException {
        Objects.requireNonNull(values); Objects.requireNonNull(options); Objects.requireNonNull(preparation); Objects.requireNonNull(checkpoint);
        values.requireActive(); checkpoint.run();
        var groups = new TreeMap<Integer, Map<String, String>>(); var seen = new HashSet<String>(); var ids = new HashSet<String>();
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>(); long bytes = 0;
        for (var field : supplied) {
            checkpoint.run();
            if (!field.sourcePath().startsWith(ROOT + "Enrolment[")) continue;
            if (field.category()!=io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.DEMOGRAPHICS || !options.importRunId().equals(field.runId()) || !seen.add(field.sourcePath()) || !ids.add(field.id())) invalid();
            String relative = field.sourcePath().substring(ROOT.length()); var match = HISTORY.matcher(relative);
            if (!match.matches()) invalid(); int ordinal;
            try { ordinal = Integer.parseInt(match.group(1)); } catch (NumberFormatException invalid) { throw new IOException("CDS_EXPORT_ENROLMENT_SOURCE_INVALID"); }
            if (ordinal > 8192 || ids.size() > 65536) limit();
            var group = groups.computeIfAbsent(ordinal, ignored -> new TreeMap<>()); String leaf = match.group(2);
            if (leaf == null) { verifyType(relative, field); continue; }
            String plain = leaf.replaceAll("\\[[0-9]+\\]", "");
            if (!KNOWN.contains(plain)) continue; // The outer retained writer preserves every unprojected source field.
            if (!leaf.equals(Arrays.stream(plain.split("/")).map(part -> part + "[1]").collect(java.util.stream.Collectors.joining("/")))) invalid();
            verifyType(relative, field); String text = text(values, field, checkpoint); bytes = Math.addExact(bytes, text.length()*2L+256);
            if (bytes > 16L << 20) limit();
            String canonical = text;
            if (plain.equals("EnrollmentDate") || plain.equals("EnrollmentTerminationDate")) {
                try {
                    var temporal = CdsTemporal.shift(CdsTemporal.parse(null, null, text, null, relative), options.timeshiftDays(), relative);
                    temporal.dateStorageAnchor(); if (!temporal.exactCalendarDate()) invalid();
                    var wire = new CdsExportPreparedTemporalMapper().map(temporal, false); canonical = temporal.earliest().toString();
                    if (!wire.lexical().equals(text)) { String replacement = wire.lexical(); changes.add(new CdsExportRetainedSectionWriter.Change(relative, Set.of(field.id()), xml -> xml.text(replacement))); }
                } catch (CdsClinicalException | java.time.DateTimeException | ArithmeticException invalid) { throw new IOException("CDS_EXPORT_ENROLMENT_SOURCE_DATE_REQUIRES_REVIEW"); }
            }
            if (group.putIfAbsent(plain, canonical) != null) invalid();
        }
        if (groups.size() != preparation.enrollmentHistoryCount()) throw new IOException("CDS_EXPORT_ENROLMENT_SOURCE_COUNT_MISMATCH");
        var histories = new ArrayList<History>(); History selected = null; boolean ambiguous=false; int expected = 1;
        for (var entry : groups.entrySet()) {
            checkpoint.run(); if (entry.getKey() != expected++) invalid(); var group = entry.getValue(); History history;
            try {
                String status = group.get("EnrollmentStatus"), start = group.get("EnrollmentDate"), end = group.get("EnrollmentTerminationDate");
                if (!("0".equals(status) || "1".equals(status)) || start == null || "0".equals(status) && end == null || "1".equals(status) && end != null) invalid();
                var first = LocalDate.parse(start); var last = end == null ? null : LocalDate.parse(end);
                if (first.getYear() < 1 || first.getYear() > 9999 || last != null && (last.isBefore(first) || last.getYear() < 1 || last.getYear() > 9999)) invalid();
                history = new History(entry.getKey(), status, first, last, group.get("TerminationReason"), group);
            } catch (java.time.DateTimeException malformed) { throw new IOException("CDS_EXPORT_ENROLMENT_SOURCE_INVALID"); }
            histories.add(history);
            if (selected == null || history.effective().isAfter(selected.effective())) { selected = history; ambiguous=false; }
            else if (history.effective().equals(selected.effective()) && !history.values().equals(selected.values())) ambiguous=true;
        }
        if (ambiguous)throw new IOException("CDS_EXPORT_ENROLMENT_SOURCE_AMBIGUOUS_HISTORY");
        var roster = preparation.roster();
        if (selected == null ? roster != null : roster == null
                || !("1".equals(selected.status()) ? "RO" : "TE").equals(roster.status())
                || !selected.start().toString().equals(roster.date())
                || !Objects.equals(selected.end() == null ? null : selected.end().toString(), roster.terminationDate())
                || !Objects.equals(selected.reason(), roster.terminationReason()))
            throw new IOException("CDS_EXPORT_ENROLMENT_SOURCE_PREPARATION_MISMATCH");
        values.requireActive(); checkpoint.run();
        return new Result(histories, selected == null ? 0 : selected.ordinal(), ids, changes, roster == null ? null : roster.enrolledProvider());
    }
    private static void verifyType(String path, CdsExportRetainedFieldDto field) throws IOException {
        var type = CdsWireSchema.schemaModel().element("cds", "OmdCds").child("cds", "PatientRecord").child("cds", "Demographics"); String namespace = "cds";
        for (String segment : path.split("/")) {
            if (type == null) invalid(); int end = segment.indexOf('['); if (end < 1) invalid(); String name = segment.substring(0, end);
            var names = type.childNames().stream().filter(child -> child.local().equals(name)).toList(); if (names.size() != 1) invalid();
            namespace = names.getFirst().namespace(); type = type.child(namespace, name);
        }
        if (type == null || !namespace.equals(field.namespace()) || !field.valueType().equals("{"+type.getTypeNamespace()+"}"+type.getTypeName())) invalid();
    }
    private static String text(CdsExportRetainedSectionWriter.Values values, CdsExportRetainedFieldDto field, Runnable checkpoint) throws IOException {
        var text = new StringBuilder(); char[] block = new char[256];
        try (Reader input = values.text(field)) {
            for (int count; (count = input.read(block)) != -1; ) { checkpoint.run(); if (count == 0 || text.length()+count > 65536) limit(); text.append(block, 0, count); }
        } finally { Arrays.fill(block, '\0'); }
        return text.toString();
    }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_ENROLMENT_SOURCE_INVALID"); }
    private static void limit() throws IOException { throw new IOException("CDS_EXPORT_ENROLMENT_SOURCE_RESOURCE_LIMIT"); }
}
