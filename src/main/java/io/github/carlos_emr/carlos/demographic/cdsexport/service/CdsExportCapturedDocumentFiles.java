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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.*;
import java.nio.charset.*;
import java.util.*;

/** File descriptors are usable only after the complete source/index authenticated scan. */
final class CdsExportCapturedDocumentFiles {
    record File(int document, String filename, CdsExportCapturedFieldDto content) {
        @Override
        public String toString() {
            return "CapturedDocumentFile[protected]";
        }
    }

    /** Every captured document file of the chart, by document. */
    Map<Integer, File> read(
            CdsExportNativeCaptureService captures,
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        return read(captures, lease, patient, tables, source, bounds, control, null, null);
    }

    /**
     * The captured files of the documents one section window holds. Every file row of the
     * capture is read and checked in every pass, whether or not the window keeps it; no count of
     * them refuses the chart.
     *
     * @param keep the documents of the window, or null to keep every file
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
            java.util.function.IntPredicate keep,
            CdsExportSectionWindowPlanner.Budget budget)
            throws IOException {
        var files = new TreeMap<Integer, File>();
        try (var fields = new Fields(files, keep, budget)) {
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
            if (!actual.equals(source.capture())
                    || fields.count
                            != actual.tables().get(CdsExportDocumentFileSourceReader.TABLE).rows())
                invalid();
            captures.authorizeSources(lease, patient, tables, control);
            patient.require(lease);
            control.authorizeCategory(
                    patient.patient(),
                    io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.REPORTS,
                    false);
        }
        if (budget != null && budget.exceeded()) return null;
        return Map.copyOf(files);
    }

    private static final class Fields
            implements CdsExportNativeCaptureReader.Fields, AutoCloseable {
        private final Map<Integer, File> files;
        private final java.util.function.IntPredicate keep;
        private final CdsExportSectionWindowPlanner.Budget budget;
        /** File rows read, kept or not. The capture's rising row keys make each a different document. */
        private long count;
        private final Map<String, CdsExportCapturedFieldDto> fields = new HashMap<>();
        private final Map<String, String> text = new HashMap<>();
        private String key;
        private CdsExportCapturedFieldDto pending;
        private ByteArrayOutputStream value;
        private boolean closed;

        Fields(
                Map<Integer, File> files,
                java.util.function.IntPredicate keep,
                CdsExportSectionWindowPlanner.Budget budget) {
            this.files = files;
            this.keep = keep;
            this.budget = budget;
        }

        @Override
        public OutputStream output(CdsExportCapturedFieldDto field) throws IOException {
            if (!CdsExportDocumentFileSourceReader.TABLE.equals(field.table()))
                return OutputStream.nullOutputStream();
            if (closed || pending != null) invalid();
            if (!field.recordKey().equals(key)) {
                finish();
                key = field.recordKey();
            }
            if (fields.containsKey(field.column()) || field.exactNumeric() != null) invalid();
            pending = field;
            if (field.column().equals("content")) {
                if (field.kind() != CdsExportCapturedFieldDto.Kind.BINARY) invalid();
                return OutputStream.nullOutputStream();
            }
            if (!Set.of("filename", "document_no").contains(field.column())
                    || field.kind() != CdsExportCapturedFieldDto.Kind.UTF8
                    || field.bytes() > 4096) invalid();
            value = new ByteArrayOutputStream((int) field.bytes());
            return value;
        }

        @Override
        public void verified(CdsExportCapturedFieldDto field) throws IOException {
            if (!CdsExportDocumentFileSourceReader.TABLE.equals(field.table())) return;
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
                                .decode(java.nio.ByteBuffer.wrap(value.toByteArray()))
                                .toString());
                value = null;
            }
            pending = null;
        }

        void finish() throws IOException {
            if (closed || pending != null) invalid();
            if (key == null) return;
            if (!fields.keySet().equals(Set.of("document_no", "filename", "content"))
                    || !text.keySet().equals(Set.of("document_no", "filename"))) invalid();
            int id;
            try {
                id = Integer.parseInt(text.get("document_no"));
                if (id < 1
                        || !Integer.toString(id).equals(text.get("document_no"))
                        || !CdsExportPatientTableSourceReader.key(id).equals(key)) {
                    invalid();
                }
            } catch (NumberFormatException malformed) {
                throw new IOException("CDS_EXPORT_DOCUMENT_CAPTURE_INVALID");
            }
            count++;
            // A window found too large keeps nothing more; it is read again with fewer documents.
            if ((keep == null || keep.test(id))
                    && (budget == null
                            || budget.add(3, 512L + 2L * text.get("filename").length()))
                    && files.putIfAbsent(
                                    id, new File(id, text.get("filename"), fields.get("content")))
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
        throw new IOException("CDS_EXPORT_DOCUMENT_CAPTURE_INVALID");
    }
}
