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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsReportNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsBase64ValueParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Authenticates complete report source, sealed metadata and embedded bytes before private assembly.
 * Current document ownership, restrictions, native receipts and referenced file capture are separate
 * mandatory proofs. An original FilePath is evidence only, never an export attachment location.
 */
public final class CdsExportReportRetainedSource {
    private static final Set<String> SCALARS = Set.of("Class", "SubClass", "SourceFacility", "Format",
            "SourceAuthorPhysician/AuthorFreeText", "SourceAuthorPhysician/AuthorName/FirstName", "SourceAuthorPhysician/AuthorName/LastName");
    private static final Pattern DATE = Pattern.compile("((?:EventDateTime|ReceivedDateTime|SentDateTime)\\[1\\]|ReportReviewed\\[[1-9][0-9]{0,17}\\]/DateTimeReportReviewed\\[1\\]|OBRContent\\[[1-9][0-9]{0,17}\\]/ObservationDateTime\\[1\\])/(YearOnly|YearMonth|FullDate|FullDateTime)\\[1\\]");
    public record Result(Set<String> sourceFields, Map<String, CdsExportDateDto> dates,
            List<CdsExportRetainedSectionWriter.Change> changes, CdsExportRetainedFieldDto filePath,
            String originalFilePath, Map<String, CdsReportNativeDto.Variant> variants) {
        public Result { sourceFields = Set.copyOf(sourceFields); dates = Map.copyOf(dates); changes = List.copyOf(changes); variants = Map.copyOf(variants); }
        @Override public String toString() { return "CdsExportReportRetainedSource.Result[protected]"; }
    }
    public Result read(int patient, String address, CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> supplied, CdsExportRetainedSectionWriter.Values values, long maximumContentBytes,
            CdsExportRunControl control) throws IOException {
        control.authorizeCategory(patient, CdsCategory.REPORTS, false); values.requireActive();
        var prepared = new CdsExportRetainedPreparationReader().report(index, address).value();
        var fields = new CdsExportRetainedOccurrenceFields().fieldsFor(patient, CdsCategory.REPORTS, address, index, checked, supplied, control::checkpoint);
        var options = new CdsExportRetainedImportOptionsReader().fromCaptured(index, address.substring(0, 36));
        var result = readVerified(fields, options, prepared, values, maximumContentBytes, control::checkpoint);
        values.requireActive(); control.authorizeCategory(patient, CdsCategory.REPORTS, false); control.checkpoint(); return result;
    }
    Result readVerified(List<CdsExportRetainedFieldDto> fields, CdsExportRetainedImportOptionsReader.Options options,
            CdsReportNativeDto prepared, CdsExportRetainedSectionWriter.Values values, long maximumContentBytes, Runnable checkpoint) throws IOException {
        Objects.requireNonNull(values); Objects.requireNonNull(checkpoint); checkpoint.run(); values.requireActive();
        if (fields == null || fields.isEmpty() || options == null || prepared == null || prepared.event() == null
                || prepared.received() == null || prepared.sent() == null || maximumContentBytes < 1) invalid();
        new CdsExportRetainedSectionWriter().prepare(fields, values, checkpoint);
        String root = CdsExportRetainedOccurrencePath.root(fields.getFirst(), CdsCategory.REPORTS), patientKey = fields.getFirst().sourcePatientKey();
        var ids = new HashSet<String>(); var paths = new HashSet<String>(); var selected = new HashMap<String, String>();
        var dates = new LinkedHashMap<String, CdsExportDateDto>(); var temporal = new HashMap<String, CdsTemporalDto>();
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>(); CdsExportRetainedFieldDto content = null, filePath = null; String external = null;
        var type = CdsWireSchema.schemaModel().element("cds", "OmdCds").child("cds", "PatientRecord").child("cds", "Reports");
        for (var field : fields) {
            checkpoint.run();
            if (!options.importRunId().equals(field.runId()) || !patientKey.equals(field.sourcePatientKey())
                    || !root.equals(CdsExportRetainedOccurrencePath.root(field, CdsCategory.REPORTS)) || !ids.add(field.id())) invalid();
            String path = field.sourcePath().length() == root.length() ? "" : field.sourcePath().substring(root.length() + 1); if (!paths.add(path)) invalid();
            String simple = path.replaceAll("\\[[1-9][0-9]{0,17}\\]", "");
            if (SCALARS.contains(simple)) {
                var scalar = type;
                for (String part : simple.split("/")) {
                    var child = scalar.childNames().stream().filter(name -> name.local().equals(part)).findFirst().orElseThrow(() -> new IOException("CDS_EXPORT_REPORT_SOURCE_INVALID"));
                    scalar = scalar.child(child.namespace(), child.local());
                }
                if (selected.putIfAbsent(simple, normalize(text(values, field, 131_072, checkpoint), scalar.whitespace())) != null) invalid();
            }
            if (Set.of("Content/TextContent", "Content/Media").contains(simple)) { if (content != null) invalid(); content = field; }
            if (simple.equals("FilePath")) { if (filePath != null) invalid(); filePath = field; external = text(values, field, 250, checkpoint); }
            var match = DATE.matcher(path); if (!match.matches()) continue;
            String parent = match.group(1), choice = match.group(2), lexical = text(values, field, 128, checkpoint); CdsTemporalDto effective;
            try {
                var original = CdsTemporal.parse(choice.equals("YearOnly") ? lexical : null, choice.equals("YearMonth") ? lexical : null,
                        choice.equals("FullDate") ? lexical : null, choice.equals("FullDateTime") ? lexical : null, parent);
                effective = CdsTemporal.shift(original, options.timeshiftDays(), parent);
            } catch (RuntimeException invalid) { throw new IOException("CDS_EXPORT_REPORT_SOURCE_DATE_INVALID"); }
            if (temporal.putIfAbsent(parent, effective) != null) invalid();
            var wire = new CdsExportPreparedTemporalMapper().map(effective, true); dates.put(parent, wire);
            if (!lexical.equals(wire.lexical())) { String replacement = wire.lexical(); changes.add(new CdsExportRetainedSectionWriter.Change(path, Set.of(field.id()), xml -> xml.text(replacement))); }
        }
        String author = selected.get("SourceAuthorPhysician/AuthorFreeText"), first = selected.get("SourceAuthorPhysician/AuthorName/FirstName"), last = selected.get("SourceAuthorPhysician/AuthorName/LastName");
        if (author != null && (first != null || last != null)) invalid();
        if (author == null && (first != null || last != null)) {
            author = first == null || first.isEmpty() ? last : last == null || last.isEmpty() ? first : first + " " + last;
            selected.put("SourceAuthorFormat", "structured");
        }
        if (author != null) selected.put("NativeSourceAuthor", author);
        if (content != null && content.sourcePath().endsWith("/TextContent[1]") && "Binary".equals(selected.get("Format"))) selected.put("ContentEncoding", "base64-from-text");
        if (!selected.equals(prepared.values()) || !prepared.event().equals(temporal.getOrDefault("EventDateTime[1]", CdsTemporalDto.unknown()))
                || !prepared.received().equals(temporal.getOrDefault("ReceivedDateTime[1]", CdsTemporalDto.unknown()))
                || !prepared.sent().equals(temporal.getOrDefault("SentDateTime[1]", CdsTemporalDto.unknown()))) mismatch();
        var variants = new HashMap<String, CdsReportNativeDto.Variant>();
        for (var variant : prepared.variants()) {
            if (variant == null || variant.name() == null || !Set.of("embedded", "referenced").contains(variant.name()) || variants.putIfAbsent(variant.name(), variant) != null
                    || variant.length() < 0 || variant.length() > maximumContentBytes || variant.digest() == null || !variant.digest().matches("[0-9a-f]{64}")) invalid();
        }
        if (variants.isEmpty() || variants.containsKey("embedded") != (content != null)
                || variants.containsKey("referenced") != (external != null && !external.isBlank())) mismatch();
        if (content != null) verifyContent(content, selected.get("Format"), variants.get("embedded"), values, maximumContentBytes, checkpoint);
        new CdsExportRetainedSectionWriter().prepareChanges(fields, values, changes, checkpoint); values.requireActive(); checkpoint.run();
        return new Result(ids, dates, changes, filePath, external, variants);
    }
    private static void verifyContent(CdsExportRetainedFieldDto field, String format, CdsReportNativeDto.Variant expected,
            CdsExportRetainedSectionWriter.Values values, long maximum, Runnable checkpoint) throws IOException {
        var digest = CdsImportKeys.digest(); long[] bytes = {0};
        OutputStream counted = new OutputStream() {
            @Override public void write(int value) throws IOException { byte[] one = {(byte) value}; write(one, 0, 1); }
            @Override public void write(byte[] data, int offset, int length) throws IOException {
                checkpoint.run(); if (length > maximum - bytes[0]) invalid(); digest.update(data, offset, length); bytes[0] += length;
            }
        };
        boolean binary = field.valueType().equals("{http://www.w3.org/2001/XMLSchema}base64Binary");
        if (binary) { try (var input = values.binary(field)) { transfer(input, counted, checkpoint); } }
        else if ("Binary".equals(format)) {
            try (var reader = values.text(field); var ascii = new InputStream() {
                @Override public int read() throws IOException { checkpoint.run(); int value = reader.read(); if (value > 127) invalid(); return value; }
                @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                    Objects.checkFromIndexSize(offset, length, bytes.length); if (length == 0) return 0;
                    char[] characters = new char[Math.min(length, 4096)];
                    try {
                        checkpoint.run(); int count = reader.read(characters); if (count == 0) invalid();
                        for (int i = 0; i < count; i++) { if (characters[i] > 127) invalid(); bytes[offset + i] = (byte) characters[i]; }
                        return count;
                    } finally { Arrays.fill(characters, '\0'); }
                }
            }; var input = CdsBase64ValueParser.decode(ascii)) { transfer(input, counted, checkpoint); }
        } else {
            var encoder = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            char[] buffer = new char[4096];
            try (var reader = values.text(field); var writer = new OutputStreamWriter(counted, encoder)) {
                for (int n; (n = reader.read(buffer)) != -1;) { checkpoint.run(); if (n == 0) invalid(); writer.write(buffer, 0, n); }
            } finally { Arrays.fill(buffer, '\0'); }
        }
        if (bytes[0] != expected.length() || !HexFormat.of().formatHex(digest.digest()).equals(expected.digest())) mismatch();
    }
    private static void transfer(InputStream input, OutputStream output, Runnable checkpoint) throws IOException {
        byte[] buffer = new byte[65536];
        try { for (int n; (n = input.read(buffer)) != -1;) { checkpoint.run(); if (n == 0) invalid(); output.write(buffer, 0, n); } }
        finally { Arrays.fill(buffer, (byte) 0); }
    }
    private static String normalize(String raw, String rule) throws IOException {
        if (!Set.of("preserve", "replace", "collapse").contains(rule)) invalid(); if (rule.equals("preserve")) return raw;
        String replaced = raw.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
        return rule.equals("replace") ? replaced : replaced.replaceAll(" +", " ").replaceAll("^ | $", "");
    }
    private static String text(CdsExportRetainedSectionWriter.Values values, CdsExportRetainedFieldDto field, int maximum, Runnable checkpoint) throws IOException {
        var result = new StringBuilder(); char[] buffer = new char[4096];
        try (Reader input = values.text(field)) { for (int n; (n = input.read(buffer)) != -1;) { checkpoint.run(); if (n == 0 || result.length() > maximum - n) invalid(); result.append(buffer, 0, n); } }
        finally { Arrays.fill(buffer, '\0'); } return result.toString();
    }
    private static void mismatch() throws IOException { throw new IOException("CDS_EXPORT_REPORT_SOURCE_PREPARATION_MISMATCH"); }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_REPORT_SOURCE_INVALID"); }
}
