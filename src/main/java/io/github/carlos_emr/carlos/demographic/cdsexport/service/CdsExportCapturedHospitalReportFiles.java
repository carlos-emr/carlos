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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPatientCaptureDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntPredicate;

/**
 * The captured HRM message files of one authenticated patient generation: each report's stored
 * file reference, what the capture found ({@link CdsExportHospitalReportFileSourceReader.State})
 * and, when it read the file, the descriptor of its authenticated bytes. The bytes themselves
 * are read later through {@link CdsExportCapturedValues}.
 *
 * @since 2026-11-13
 */
final class CdsExportCapturedHospitalReportFiles {
    private static final Set<String> COLUMNS =
            Set.of("hrm_document_id", "report_file", "state", "content");

    /** One report's captured file. {@code content} is null unless the file was read. */
    record File(
            int report,
            String reportFile,
            CdsExportHospitalReportFileSourceReader.State state,
            CdsExportCapturedFieldDto content,
            String recordKey) {
        /**
         * The captured row as the field-assessment validator sees it: the content is represented
         * by its digest, an empty file by an empty value and an unread file by SQL NULL.
         */
        CdsExportCapturedTextRowDto row() {
            var fields = new TreeMap<String, String>();
            fields.put("hrm_document_id", Integer.toString(report));
            fields.put("report_file", reportFile);
            fields.put("state", state.name());
            fields.put(
                    "content",
                    content == null
                            ? null
                            : content.bytes() == 0 ? "" : "sha256:" + content.sha256());
            return new CdsExportCapturedTextRowDto(
                    CdsExportHospitalReportSources.FILES, recordKey, fields);
        }

        @Override
        public String toString() {
            return "CapturedHospitalReportFile[protected]";
        }
    }

    /** Every captured message file of the chart, by report. */
    Map<Integer, File> read(
            CdsExportNativeCaptureService captures,
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        return read(captures, lease, patient, tables, source, bounds, control, null, null, null);
    }

    /**
     * The captured message files of the reports one section window holds. Every file row of the
     * capture is read and checked in every pass, whether or not the window keeps it; no count of
     * them refuses the chart.
     *
     * @param keep the reports of the window, or null to keep every file
     * @param known every report of the chart: a file of a report the chart does not hold refuses
     *     the capture; null when {@code keep} is null (the caller compares the two sets itself)
     * @param budget counts each kept file against the window; null to count none
     * @return the kept files, or null when the window passed its budget
     */
    Map<Integer, File> read(
            CdsExportNativeCaptureService captures,
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            IntPredicate keep,
            IntPredicate known,
            CdsExportSectionWindowPlanner.Budget budget)
            throws IOException {
        var files = new TreeMap<Integer, File>();
        try (var fields = new Fields(files, keep, known, budget)) {
            var actual =
                    captures.scan(
                            lease,
                            patient,
                            tables,
                            source.manifest(),
                            bounds.fields(),
                            bounds.bytes(),
                            control,
                            fields);
            fields.finish();
            var scope = actual.tables().get(CdsExportHospitalReportSources.FILES);
            if (!actual.equals(source.capture())
                    || scope == null
                    || !scope.columns().equals(COLUMNS)
                    || fields.count != scope.rows()) invalid();
            captures.authorizeSources(lease, patient, tables, control);
            patient.require(lease);
            control.authorizeCategory(patient.patient(), CdsCategory.REPORTS, false);
        }
        if (budget != null && budget.exceeded()) return null;
        return Map.copyOf(files);
    }

    private static final class Fields
            implements CdsExportNativeCaptureReader.Fields, AutoCloseable {
        private final Map<Integer, File> files;
        private final IntPredicate keep, known;
        private final CdsExportSectionWindowPlanner.Budget budget;
        /** File rows read, kept or not. The capture's rising row keys make each a different report. */
        private long count;
        private final Map<String, CdsExportCapturedFieldDto> fields = new HashMap<>();
        private final Map<String, String> text = new HashMap<>();
        private String key;
        private CdsExportCapturedFieldDto pending;
        private ByteArrayOutputStream value;
        private boolean closed;

        Fields(
                Map<Integer, File> files,
                IntPredicate keep,
                IntPredicate known,
                CdsExportSectionWindowPlanner.Budget budget) {
            this.files = files;
            this.keep = keep;
            this.known = known;
            this.budget = budget;
        }

        @Override
        public OutputStream output(CdsExportCapturedFieldDto field) throws IOException {
            if (!CdsExportHospitalReportSources.FILES.equals(field.table()))
                return OutputStream.nullOutputStream();
            if (closed || pending != null) invalid();
            if (!field.recordKey().equals(key)) {
                finish();
                key = field.recordKey();
            }
            if (fields.containsKey(field.column())
                    || !COLUMNS.contains(field.column())
                    || field.exactNumeric() != null) invalid();
            pending = field;
            if (field.kind() == CdsExportCapturedFieldDto.Kind.SQL_NULL) {
                // Only the stored reference and the content of an unread file may be absent.
                if (!Set.of("report_file", "content").contains(field.column())) invalid();
                return OutputStream.nullOutputStream();
            }
            if (field.column().equals("content")) {
                if (field.kind() != CdsExportCapturedFieldDto.Kind.BINARY) invalid();
                return OutputStream.nullOutputStream();
            }
            if (field.kind() != CdsExportCapturedFieldDto.Kind.UTF8 || field.bytes() > 4096)
                invalid();
            value = new ByteArrayOutputStream((int) field.bytes());
            return value;
        }

        @Override
        public void verified(CdsExportCapturedFieldDto field) throws IOException {
            if (!CdsExportHospitalReportSources.FILES.equals(field.table())) return;
            if (closed || pending != field || fields.putIfAbsent(field.column(), field) != null)
                invalid();
            if (value != null) {
                if (value.size() != field.bytes()) invalid();
                text.put(
                        field.column(),
                        StandardCharsets.UTF_8
                                .newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(value.toByteArray()))
                                .toString());
                value = null;
            }
            pending = null;
        }

        void finish() throws IOException {
            if (closed || pending != null) invalid();
            if (key == null) return;
            if (!fields.keySet().equals(COLUMNS)) invalid();
            int id;
            CdsExportHospitalReportFileSourceReader.State state;
            try {
                id = Integer.parseInt(text.get("hrm_document_id"));
                state = CdsExportHospitalReportFileSourceReader.State.valueOf(text.get("state"));
            } catch (NumberFormatException | NullPointerException malformed) {
                throw new IOException("CDS_EXPORT_HOSPITAL_REPORT_CAPTURE_INVALID");
            } catch (IllegalArgumentException unknown) {
                throw new IOException("CDS_EXPORT_HOSPITAL_REPORT_CAPTURE_INVALID");
            }
            var content = fields.get("content");
            boolean read = content.kind() == CdsExportCapturedFieldDto.Kind.BINARY;
            String reference = text.get("report_file");
            if (id < 1
                    || !Integer.toString(id).equals(text.get("hrm_document_id"))
                    || !CdsExportPatientTableSourceReader.key(id).equals(key)
                    || read != (state == CdsExportHospitalReportFileSourceReader.State.READ)
                    || (reference == null || reference.isEmpty())
                            != (state == CdsExportHospitalReportFileSourceReader.State.NOT_RECORDED))
                invalid();
            count++;
            if (known != null && !known.test(id)) invalid();
            // A window found too large keeps nothing more; it is read again with fewer reports.
            if ((keep == null || keep.test(id))
                    && (budget == null
                            || budget.add(1, 512L + (reference == null ? 0 : 2L * reference.length())))
                    && files.putIfAbsent(
                                    id, new File(id, reference, state, read ? content : null, key))
                            != null) invalid();
            fields.clear();
            text.clear();
            key = null;
        }

        @Override
        public void close() {
            closed = true;
            fields.clear();
            text.clear();
            value = null;
            pending = null;
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_HOSPITAL_REPORT_CAPTURE_INVALID");
    }
}
