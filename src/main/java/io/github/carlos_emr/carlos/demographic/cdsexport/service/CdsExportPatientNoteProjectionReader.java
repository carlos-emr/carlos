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
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded note projection from the authenticated frozen member. Oversized notes stream to discard and remain explicit review after full verification. */
public final class CdsExportPatientNoteProjectionReader {
    // Even the more permissive XSD cannot represent more UTF-8 bytes than this,
    // including the exact native outer frame. No potentially valid note is discarded.
    private static final int MAXIMUM_NOTE_BYTES = 4 * 65536 + 17;

    public record Result(
            String sourceManifestSha256, long rows, CdsExportPatientNoteMappingDto value) {
        public Result {
            if (sourceManifestSha256 == null
                    || !sourceManifestSha256.matches("[0-9a-f]{64}")
                    || rows < 0
                    || rows > 1
                    || (rows == 0) != (value == null))
                throw new IllegalArgumentException("CDS_EXPORT_PATIENT_NOTE_PROJECTION_INVALID");
        }

        @Override
        public String toString() {
            return "CdsExportPatientNoteProjectionReader.Result[protected]";
        }
    }

    private final CdsExportNativeCaptureService captures;

    public CdsExportPatientNoteProjectionReader(CdsExportNativeCaptureService captures) {
        this.captures = Objects.requireNonNull(captures);
    }

    public Result read(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportStagedFileDto manifest,
            long maximumFields,
            long maximumBytes,
            CdsExportRunControl control)
            throws IOException {
        if (!tables.contains("demographiccust"))
            throw new IllegalArgumentException("CDS_EXPORT_PATIENT_NOTE_INPUT_REQUIRED");
        try (var fields = new Fields(patient.patient(), lease.document().command().categories())) {
            var generation =
                    captures.scan(
                            lease,
                            patient,
                            tables,
                            manifest,
                            maximumFields,
                            maximumBytes,
                            control,
                            fields);
            fields.finish();
            long count = generation.tables().get("demographiccust").rows();
            if (count > 1 || count != fields.rows || (count == 0) != (fields.result == null))
                throw new IOException("CDS_EXPORT_PATIENT_NOTE_COUNT_MISMATCH");
            return new Result(manifest.plaintextSha256(), count, fields.result);
        }
    }

    private static final class Fields
            implements CdsExportNativeCaptureReader.Fields, AutoCloseable {
        private final int patient;
        private final Set<io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory>
                categories;
        private final Map<String, String> values = new LinkedHashMap<>();
        private CdsExportCapturedFieldDto pending;
        private String key;
        private byte[] bytes;
        private int at;
        private long rows;
        private boolean oversized, closed;
        private CdsExportPatientNoteMappingDto result;

        private Fields(
                int patient,
                Set<io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory> categories) {
            this.patient = patient;
            this.categories = Set.copyOf(categories);
        }

        @Override
        public OutputStream output(CdsExportCapturedFieldDto field) throws IOException {
            if (!field.table().equals("demographiccust")) return OutputStream.nullOutputStream();
            if (closed || pending != null) invalid();
            if (key != null && !key.equals(field.recordKey())) {
                finish();
                throw new IOException("CDS_EXPORT_PATIENT_NOTE_DUPLICATE_ROW");
            }
            key = field.recordKey();
            pending = field;
            at = 0;
            if (field.kind() != CdsExportCapturedFieldDto.Kind.SQL_NULL
                            && field.kind() != CdsExportCapturedFieldDto.Kind.UTF8
                    || field.exactNumeric() != null) invalid();
            if (field.column().equals("content") && field.bytes() > MAXIMUM_NOTE_BYTES) {
                oversized = true;
                return OutputStream.nullOutputStream();
            }
            if (field.bytes() > (field.column().equals("content") ? MAXIMUM_NOTE_BYTES : 4096))
                invalid();
            if (field.kind() == CdsExportCapturedFieldDto.Kind.SQL_NULL)
                return OutputStream.nullOutputStream();
            bytes = new byte[(int) field.bytes()];
            return new OutputStream() {
                @Override
                public void write(int value) throws IOException {
                    write(new byte[] {(byte) value}, 0, 1);
                }

                @Override
                public void write(byte[] value, int offset, int length) throws IOException {
                    Objects.checkFromIndexSize(offset, length, value.length);
                    if (closed || pending != field || bytes == null || length > bytes.length - at)
                        invalid();
                    System.arraycopy(value, offset, bytes, at, length);
                    at += length;
                }
            };
        }

        @Override
        public void verified(CdsExportCapturedFieldDto field) throws IOException {
            if (!field.table().equals("demographiccust")) return;
            if (closed || pending != field || values.containsKey(field.column())) invalid();
            String value = null;
            try {
                if (bytes != null) {
                    if (at != bytes.length) invalid();
                    value =
                            StandardCharsets.UTF_8
                                    .newDecoder()
                                    .onMalformedInput(CodingErrorAction.REPORT)
                                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                                    .decode(ByteBuffer.wrap(bytes))
                                    .toString();
                }
                values.put(field.column(), value);
            } finally {
                if (bytes != null) Arrays.fill(bytes, (byte) 0);
                bytes = null;
                pending = null;
            }
        }

        private void finish() throws IOException {
            if (closed || pending != null) invalid();
            if (key == null) return;
            if (result != null) throw new IOException("CDS_EXPORT_PATIENT_NOTE_DUPLICATE_ROW");
            var mapped =
                    new CdsExportPatientNoteMapper()
                            .map(
                                    patient,
                                    categories,
                                    new CdsExportCapturedTextRowDto(
                                            "demographiccust", key, values));
            if (oversized) {
                var decisions = new LinkedHashMap<>(mapped.fields());
                decisions.put(
                        new CdsExportNativeFieldKeyDto("demographiccust", key, "content"),
                        new CdsExportFieldAssessmentDto(
                                CdsExportFieldAssessmentDto.Presence.VALUE,
                                CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW,
                                "NoteAboutPatient",
                                "PATIENT_NOTE_WIRE_LENGTH_REQUIRES_REVIEW"));
                mapped =
                        new CdsExportPatientNoteMappingDto(
                                patient, null, decisions, mapped.delegatedFields());
            }
            result = mapped;
            rows++;
            values.clear();
            key = null;
        }

        @Override
        public void close() {
            closed = true;
            if (bytes != null) Arrays.fill(bytes, (byte) 0);
            bytes = null;
            pending = null;
            values.clear();
        }

        private static void invalid() throws IOException {
            throw new IOException("CDS_EXPORT_PATIENT_NOTE_SOURCE_INVALID");
        }
    }
}
