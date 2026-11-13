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
package io.github.carlos_emr.carlos.demographic.cdsexport.validator;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportPathComposer;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportSupportingFilesComposer;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.*;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CancellationException;

/**
 * Independently reads the finished ReadMe and event CSV against actual validated XML/ZIP
 * receipts and a sealed physician inventory. It never accepts the composer's own patient
 * summaries as output evidence. Both streams are caller owned and must reach EOF. This
 * checks supporting-file consistency, not source completeness or permission to disclose.
 * The exact patient-identifier index has a separate conservative 64 MiB retention bound.
 */
public final class CdsExportSupportingFilesValidator {
    private static final int MAXIMUM_LINE_CHARACTERS = 65536;
    private static final JsonMapper JSON =
            JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    /** The relative path is the actual ZIP entry, including its named export container. */
    public record Patient(
            String relativePath,
            CdsExportPatientFileDto naming,
            CdsExportXmlValidationDto xml,
            java.util.Map<String, Long> dataSummaries,
            java.util.List<io.github.carlos_emr.carlos.commn.dto.CdsExportListedSourceDto>
                    listed) {
        public Patient {
            dataSummaries =
                    java.util.Collections.unmodifiableMap(
                            new java.util.LinkedHashMap<>(dataSummaries));
            listed = java.util.List.copyOf(listed);
        }

        public Patient(
                String relativePath, CdsExportPatientFileDto naming, CdsExportXmlValidationDto xml) {
            this(relativePath, naming, xml, java.util.Map.of(), java.util.List.of());
        }

        @Override
        public String toString() {
            return "CdsExportSupportingFilesValidator.Patient[protected]";
        }
    }

    public record Result(
            String readMeSha256,
            long readMeBytes,
            String eventLogSha256,
            long eventLogBytes,
            long physicians,
            long patientFiles) {}

    /** Inputs are finite, ordered inventories from independent XML validation and the authorized capture. */
    public Result validate(
            InputStream readMe,
            InputStream eventLog,
            CdsExportPackageMetadataDto metadata,
            Iterator<CdsExportPhysicianSummaryDto> physicians,
            Iterator<Patient> patientFiles,
            long maximumPhysicians,
            long maximumPatients,
            long maximumBytes,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(readMe);
        Objects.requireNonNull(eventLog);
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(checkpoint);
        Objects.requireNonNull(physicians);
        Objects.requireNonNull(patientFiles);
        if (maximumPhysicians < 1
                || maximumPhysicians > 1000000
                || maximumPatients < 1
                || maximumPatients > 10000000
                || maximumBytes < 1
                || maximumBytes > (64L << 30))
            throw new IllegalArgumentException("CDS_EXPORT_SUPPORTING_LIMITS_INVALID");
        validateMetadata(metadata);
        long[] totalBytes = {0};
        var readMeInput = new Counted(readMe, totalBytes, maximumBytes, checkpoint);
        var logInput = new Counted(eventLog, totalBytes, maximumBytes, checkpoint);
        var text = new Lines(readMeInput, checkpoint);
        var csv = new Lines(logInput, checkpoint);
        text.field("Physician group", metadata.physicianGroup());
        text.field("Group contact information", metadata.groupContactInformation());
        text.field("EMR vendor and offering", "CARLOS EMR");
        text.field("Offering version", metadata.applicationVersion());
        text.field(
                "Export timestamp",
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                        metadata.exportTime().atZone(metadata.clinicalZone())));
        text.field("Logical profile", metadata.profile().identifier());
        text.field("Export container root", metadata.exportRoot());
        text.field(
                "Requested category scope",
                metadata.categories().size() == CdsCategory.values().length
                        ? "ALL_CDS_CATEGORIES"
                        : "SELECTED_CDS_CATEGORIES");
        text.field(
                "Selected categories",
                Arrays.stream(CdsCategory.values())
                        .filter(metadata.categories()::contains)
                        .map(CdsCategory::wireName)
                        .collect(java.util.stream.Collectors.joining(", ")));
        text.field(
                CdsExportSupportingFilesComposer.SCREENINGS,
                metadata.categories().contains(CdsCategory.PAST_HEALTH) ? "INCLUDED" : "NOT_SELECTED");
        text.field("Audit information access after migration", metadata.auditAccessDescription());
        text.field("Held patients not in this package", Long.toString(metadata.heldPatients().size()));
        for (var held : metadata.heldPatients()) {
            text.field("Held patient", Integer.toString(held.patient()));
            text.field("Held patient reason", held.code());
        }
        text.field(
                "Unreviewed local tables not exported",
                Long.toString(metadata.unreviewedLocalSources().size()));
        for (var local : metadata.unreviewedLocalSources()) {
            text.field("Unreviewed local table", local.table());
            text.field(
                    "Unreviewed local table estimated rows", Long.toString(local.estimatedRows()));
        }
        text.field(
                "Unlinked inbox tables not exported",
                Long.toString(metadata.unlinkedSources().size()));
        for (var unlinked : metadata.unlinkedSources()) {
            text.field("Unlinked inbox table", unlinked.table());
            text.field("Unlinked inbox table rows", Long.toString(unlinked.rows()));
        }
        text.exact("Patient file paths below are complete within the named export container.");
        text.exact(
                "NOT_SELECTED in ExportEventLog is outside the requested scope; zero is a known selected-category count.");
        text.exact(
                "ReportsText and ReportsBinary count representations; Reports counts source report occurrences.");
        text.exact(
                "Practice-administrative demographic fields \"date joined\" and \"effective date\" are not part of CDS 5.2 Demographics and are not exported.");
        text.exact(CdsExportSupportingFilesComposer.DATA_SUMMARY_NOTICE);
        text.exact(CdsExportSupportingFilesComposer.LISTED_NOTICE);
        text.field(
                "Listed clinic tables not exported",
                Long.toString(metadata.listedClinicSources().size()));
        for (var listed : metadata.listedClinicSources())
            text.field(
                    "Listed clinic table not exported",
                    listed.table() + ": " + listed.rows() + " rows");
        String header =
                "PatientID,"
                        + Arrays.stream(CdsCategory.values())
                                .map(CdsCategory::wireName)
                                .collect(java.util.stream.Collectors.joining(","))
                        + ",ReportsText,ReportsBinary";
        csv.exact(header);
        var paths = new CdsExportPathComposer();
        long physicianCount = 0, patients = 0;
        String previousFolder = null, previousPath = null;
        Patient pending = next(patientFiles, checkpoint);
        var senderIds = new HashSet<String>();
        long identityBytes = 0;
        while (physicians.hasNext()) {
            probe(checkpoint);
            if (++physicianCount > maximumPhysicians) limit();
            var physician = Objects.requireNonNull(physicians.next());
            String folder =
                    paths.physicianFolder(
                            physician.firstName(), physician.lastName(), physician.billingNumber());
            if (previousFolder != null && previousFolder.compareTo(folder) >= 0) invalid();
            previousFolder = folder;
            text.field("Physician", physician.firstName() + " " + physician.lastName());
            text.field("Physician OHIP billing number", physician.billingNumber());
            text.field("Physician patient files", Long.toString(physician.patientFiles()));
            long actual = 0;
            while (pending != null) {
                probe(checkpoint);
                var path = paths.compose(pending.naming());
                int comparison = path.physicianFolder().compareTo(folder);
                if (comparison > 0) break;
                if (comparison < 0) invalid();
                String relative =
                        metadata.exportRoot()
                                + "/"
                                + path.physicianFolder()
                                + "/"
                                + path.patientXmlFile();
                if (!relative.equals(pending.relativePath())
                        || previousPath != null && previousPath.compareTo(relative) >= 0) invalid();
                previousPath = relative;
                if (++patients > maximumPatients) limit();
                actual++;
                validatePatient(pending, metadata);
                identityBytes =
                        Math.addExact(
                                identityBytes,
                                256L + 4L * pending.xml().senderPatientId().length());
                if (identityBytes > (64L << 20)) limit();
                if (!senderIds.add(pending.xml().senderPatientId())) invalid();
                text.field("Patient file", relative);
                text.field("Patient XML SHA256", pending.xml().xmlSha256());
                for (var summary : pending.dataSummaries().entrySet())
                    text.field(
                            "Patient data summary records",
                            summary.getKey() + ": " + summary.getValue());
                for (var listed : pending.listed())
                    text.field(
                            "Patient listed table not exported",
                            listed.table() + ": " + listed.rows() + " rows");
                validateEvent(csv.required(), pending.xml(), metadata.categories());
                pending = next(patientFiles, checkpoint);
            }
            if (actual != physician.patientFiles()) invalid();
        }
        if (pending != null || patients == 0 || text.next() != null || csv.next() != null)
            invalid();
        probe(checkpoint);
        return new Result(
                readMeInput.hash(),
                readMeInput.bytes,
                logInput.hash(),
                logInput.bytes,
                physicianCount,
                patients);
    }

    private static void validateMetadata(CdsExportPackageMetadataDto value) throws IOException {
        if (value.profile() == null
                || value.clinicalZone() == null
                || value.exportTime() == null
                || !value.categories().contains(CdsCategory.DEMOGRAPHICS)
                || !required(value.exportRoot(), 255)
                || !required(value.physicianGroup(), 512)
                || !required(value.groupContactInformation(), 4096)
                || !required(value.applicationVersion(), 128)
                || !required(value.auditAccessDescription(), 4096)) invalid();
        PathValidationUtils.validatePathComponent(value.exportRoot(), "CDS export container");
    }

    private static void validatePatient(Patient patient, CdsExportPackageMetadataDto metadata)
            throws IOException {
        var name = Objects.requireNonNull(patient.naming());
        var xml = Objects.requireNonNull(patient.xml());
        if (!Objects.equals(name.senderPatientId(), xml.senderPatientId())
                || !Objects.equals(name.patientFirstName(), xml.legalFirstName())
                || !Objects.equals(name.patientLastName(), xml.legalLastName())
                || name.dateOfBirth() == null
                || !birth(xml.dateOfBirth(), name.dateOfBirth())
                || xml.xmlBytes() < 1
                || xml.xmlSha256() == null
                || !xml.xmlSha256().matches("[0-9a-f]{64}")
                || !xml.occurrences().keySet().equals(metadata.categories())
                || !Long.valueOf(1).equals(xml.occurrences().get(CdsCategory.DEMOGRAPHICS))
                || xml.occurrences().values().stream().anyMatch(count -> count == null || count < 0)
                || xml.textReports() < 0
                || xml.binaryReports() < 0
                || xml.externalReferences() < 0) invalid();
        Long reports = xml.occurrences().get(CdsCategory.REPORTS);
        if (reports == null
                ? xml.textReports() != 0 || xml.binaryReports() != 0
                : xml.textReports() > reports || xml.binaryReports() > reports) invalid();
    }

    private static boolean birth(String lexical, LocalDate expected) {
        if (lexical != null) lexical = lexical.strip();
        return lexical != null
                && lexical.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}(?:Z|[+-][0-9]{2}:[0-9]{2})?")
                && lexical.substring(0, 10).equals(expected.toString());
    }

    private static void validateEvent(
            String line, CdsExportXmlValidationDto xml, Set<CdsCategory> selected)
            throws IOException {
        List<String> fields = csv(line);
        if (fields.size() != CdsCategory.values().length + 3
                || !fields.getFirst().equals(xml.senderPatientId())) invalid();
        int index = 1;
        for (var category : CdsCategory.values()) {
            String expected =
                    selected.contains(category)
                            ? Long.toString(xml.occurrences().get(category))
                            : "NOT_SELECTED";
            if (!expected.equals(fields.get(index++))) invalid();
        }
        if (!(selected.contains(CdsCategory.REPORTS)
                                ? Long.toString(xml.textReports())
                                : "NOT_SELECTED")
                        .equals(fields.get(index++))
                || !(selected.contains(CdsCategory.REPORTS)
                                ? Long.toString(xml.binaryReports())
                                : "NOT_SELECTED")
                        .equals(fields.get(index))) invalid();
    }

    private static List<String> csv(String line) throws IOException {
        var fields = new ArrayList<String>();
        int at = 0;
        while (at < line.length()) {
            if (line.charAt(at++) != '"') invalid();
            var value = new StringBuilder();
            boolean closed = false;
            while (at < line.length()) {
                char current = line.charAt(at++);
                if (current != '"') value.append(current);
                else if (at < line.length() && line.charAt(at) == '"') {
                    value.append('"');
                    at++;
                } else {
                    closed = true;
                    break;
                }
            }
            if (!closed || fields.size() >= CdsCategory.values().length + 3) invalid();
            fields.add(value.toString());
            if (at == line.length()) break;
            if (line.charAt(at++) != ',' || at == line.length()) invalid();
        }
        return fields;
    }

    private static Patient next(Iterator<Patient> values, Runnable checkpoint) {
        probe(checkpoint);
        return values.hasNext() ? Objects.requireNonNull(values.next()) : null;
    }

    private static boolean required(String text, int maximum) {
        return text != null && !text.isBlank() && text.length() <= maximum;
    }

    private static void probe(Runnable checkpoint) {
        checkpoint.run();
        if (Thread.currentThread().isInterrupted())
            throw new CancellationException("CDS_EXPORT_CANCELLED");
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_SUPPORTING_FILES_INVALID");
    }

    private static void limit() throws IOException {
        throw new IOException("CDS_EXPORT_SUPPORTING_FILES_RESOURCE_LIMIT");
    }

    private static final class Lines {
        private final BufferedReader reader;
        private final Runnable checkpoint;

        Lines(InputStream input, Runnable checkpoint) {
            this.reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    input,
                                    StandardCharsets.UTF_8
                                            .newDecoder()
                                            .onMalformedInput(CodingErrorAction.REPORT)
                                            .onUnmappableCharacter(CodingErrorAction.REPORT)),
                            8192);
            this.checkpoint = checkpoint;
        }

        String next() throws IOException {
            var result = new StringBuilder();
            while (true) {
                if (result.length() % 4096 == 0) probe(checkpoint);
                int code = reader.read();
                if (code == -1) {
                    if (!result.isEmpty()) invalid();
                    return null;
                }
                if (code == '\n') return result.toString();
                if (code == '\r') {
                    if (reader.read() != '\n') invalid();
                    return result.toString();
                }
                if (result.length() >= MAXIMUM_LINE_CHARACTERS) limit();
                result.append((char) code);
            }
        }

        String required() throws IOException {
            String line = next();
            if (line == null) invalid();
            return line;
        }

        void exact(String expected) throws IOException {
            if (!expected.equals(required())) invalid();
        }

        void field(String label, String expected) throws IOException {
            String prefix = label + ": ", line = required();
            if (!line.startsWith(prefix)) invalid();
            var value = JSON.readTree(line.substring(prefix.length()));
            if (value == null || !value.isTextual() || !Objects.equals(expected, value.textValue()))
                invalid();
        }
    }

    private static final class Counted extends FilterInputStream {
        private final long[] total;
        private final long maximum;
        private final Runnable checkpoint;

        // Per-operation incremental digest, owned by this stream/row accumulator; never shared
        // singleton state.
        @SuppressWarnings("java:S9428")
        private final MessageDigest digest;

        private long bytes;

        Counted(InputStream input, long[] total, long maximum, Runnable checkpoint) {
            super(input);
            this.total = total;
            this.maximum = maximum;
            this.checkpoint = checkpoint;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        @Override
        public int read() throws IOException {
            probe(checkpoint);
            int value = in.read();
            if (value != -1) {
                count(1);
                digest.update((byte) value);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            probe(checkpoint);
            int read = in.read(buffer, offset, length);
            if (read > 0) {
                count(read);
                digest.update(buffer, offset, read);
            }
            return read;
        }

        private void count(int amount) throws IOException {
            if (amount > maximum - total[0]) limit();
            total[0] += amount;
            bytes += amount;
        }

        String hash() {
            return HexFormat.of().formatHex(digest.digest());
        }
    }
}
