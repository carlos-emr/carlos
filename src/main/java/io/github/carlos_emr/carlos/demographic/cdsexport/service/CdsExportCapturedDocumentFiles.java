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

/** Bounded file descriptors are usable only after the complete source/index authenticated scan. */
final class CdsExportCapturedDocumentFiles {
    record File(int document, String filename, CdsExportCapturedFieldDto content) {
        @Override public String toString() { return "CapturedDocumentFile[protected]"; }
    }
    Map<Integer, File> read(CdsExportNativeCaptureService captures, CdsExportLeaseDto lease, CdsExportCohortService.Patient patient,
            Set<String> tables, CdsExportPatientCaptureDto source, CdsExportClinicalOccurrenceService.Bounds bounds, CdsExportRunControl control) throws IOException {
        var files = new TreeMap<Integer, File>();
        try (var fields = new Fields(bounds.nativeRows(), files)) {
            var actual = captures.scan(lease, patient, tables, source.manifest(), bounds.fields(), bounds.bytes(), control, fields);
            fields.finish(); if (!actual.equals(source.capture()) || files.size() != actual.tables().get(CdsExportDocumentFileSourceReader.TABLE).rows()) invalid();
            captures.authorizeSources(lease, patient, tables, control); patient.require(lease); control.authorizeCategory(patient.patient(), io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.REPORTS, false);
        }
        return Map.copyOf(files);
    }
    private static final class Fields implements CdsExportNativeCaptureReader.Fields, AutoCloseable {
        private final int maximum;
        private final Map<Integer, File> files;
        private final Map<String, CdsExportCapturedFieldDto> fields = new HashMap<>();
        private final Map<String, String> text = new HashMap<>();
        private String key;
        private CdsExportCapturedFieldDto pending;
        private ByteArrayOutputStream value;
        private boolean closed;
        Fields(int maximum, Map<Integer, File> files) { this.maximum = maximum; this.files = files; }
        @Override public OutputStream output(CdsExportCapturedFieldDto field) throws IOException {
            if (!CdsExportDocumentFileSourceReader.TABLE.equals(field.table())) return OutputStream.nullOutputStream();
            if (closed || pending != null) invalid(); if (!field.recordKey().equals(key)) { finish(); key = field.recordKey(); }
            if (fields.containsKey(field.column()) || field.exactNumeric() != null) invalid(); pending = field;
            if (field.column().equals("content")) { if (field.kind() != CdsExportCapturedFieldDto.Kind.BINARY) invalid(); return OutputStream.nullOutputStream(); }
            if (!Set.of("filename", "document_no").contains(field.column()) || field.kind() != CdsExportCapturedFieldDto.Kind.UTF8 || field.bytes() > 4096) invalid();
            value = new ByteArrayOutputStream((int)field.bytes()); return value;
        }
        @Override public void verified(CdsExportCapturedFieldDto field) throws IOException {
            if (!CdsExportDocumentFileSourceReader.TABLE.equals(field.table())) return;
            if (closed || pending != field || fields.putIfAbsent(field.column(), field) != null) invalid();
            if (value != null) {
                if (value.size() != field.bytes()) invalid();
                text.put(field.column(), StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(value.toByteArray())).toString()); value = null;
            }
            pending = null;
        }
        void finish() throws IOException {
            if (closed || pending != null) invalid(); if (key == null) return;
            if (!fields.keySet().equals(Set.of("document_no", "filename", "content")) || !text.keySet().equals(Set.of("document_no", "filename"))) invalid();
            int id; try { id = Integer.parseInt(text.get("document_no")); if (id < 1 || !Integer.toString(id).equals(text.get("document_no")) || !CdsExportPatientTableSourceReader.key(id).equals(key)) { invalid(); } }
            catch (NumberFormatException malformed) { throw new IOException("CDS_EXPORT_DOCUMENT_CAPTURE_INVALID"); }
            if (files.size() >= maximum || files.putIfAbsent(id, new File(id, text.get("filename"), fields.get("content"))) != null) invalid();
            fields.clear(); text.clear(); key = null;
        }
        @Override public void close() { closed = true; fields.clear(); text.clear(); value = null; pending = null; }
    }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_DOCUMENT_CAPTURE_INVALID"); }
}
