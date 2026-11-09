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

/**
 * Streams exact revision metadata from the authenticated capture without materializing note
 * bodies or history. All source bytes and EOFs remain verified. Consumers keep results private
 * until return, including the fresh authorization after the last buffered metadata callback.
 */
public final class CdsExportNoteRevisionReader {
    @FunctionalInterface
    public interface Revisions {
        void accept(CdsExportNoteRevisionDto revision) throws IOException;
    }

    private final CdsExportNativeCaptureService captures;

    public CdsExportNoteRevisionReader(CdsExportNativeCaptureService captures) {
        this.captures = Objects.requireNonNull(captures);
    }

    public CdsExportNativeCaptureDto forEach(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            long maximumFields,
            long maximumBytes,
            CdsExportRunControl control,
            Revisions revisions)
            throws IOException {
        Objects.requireNonNull(source);
        Objects.requireNonNull(control);
        patient.require(lease);
        if (!requiredTables.contains("casemgmt_note")
                || !source.capture().tables().keySet().equals(requiredTables)) invalid();
        try (var fields = new Fields(patient.patient(), revisions)) {
            var actual =
                    captures.scan(
                            lease,
                            patient,
                            requiredTables,
                            source.manifest(),
                            maximumFields,
                            maximumBytes,
                            control,
                            fields);
            if (!actual.equals(source.capture())) invalid();
            fields.finish();
            if (fields.rows != actual.tables().get("casemgmt_note").rows()) invalid();
            captures.authorizeCapturedSources(lease, patient, requiredTables, actual, control);
            return actual;
        }
    }

    static final class Fields implements CdsExportNativeCaptureReader.Fields, AutoCloseable {
        private static final Set<String> COLUMNS =
                Set.of(
                        "note_id",
                        "demographic_no",
                        "uuid",
                        "observation_date",
                        "update_date",
                        "archived",
                        "signed");
        private final int patient;
        private final Revisions consumer;
        private final Map<String, String> values = new HashMap<>();
        private String key;
        private CdsExportCapturedFieldDto pending;
        private byte[] bytes;
        private int position;
        private long rows;
        private boolean closed;

        Fields(int patient, Revisions consumer) {
            if (patient < 1)
                throw new IllegalArgumentException("CDS_EXPORT_NOTE_REVISION_METADATA_INVALID");
            this.patient = patient;
            this.consumer = Objects.requireNonNull(consumer);
        }

        @Override
        public OutputStream output(CdsExportCapturedFieldDto next) throws IOException {
            if (closed || pending != null) invalid();
            if (!next.table().equals("casemgmt_note")) {
                finish();
                return OutputStream.nullOutputStream();
            }
            if (!next.recordKey().equals(key)) {
                finish();
                key = next.recordKey();
            }
            if (!COLUMNS.contains(next.column())) return OutputStream.nullOutputStream();
            if (next.kind() == CdsExportCapturedFieldDto.Kind.BINARY
                    || next.exactNumeric() != null
                    || next.bytes() > 256
                    || values.containsKey(next.column())) invalid();
            pending = next;
            bytes = new byte[(int) next.bytes()];
            position = 0;
            return new OutputStream() {
                @Override
                public void write(int value) throws IOException {
                    if (closed || pending != next || bytes == null || position == bytes.length)
                        invalid();
                    bytes[position++] = (byte) value;
                }

                @Override
                public void write(byte[] value, int offset, int length) throws IOException {
                    Objects.checkFromIndexSize(offset, length, value.length);
                    if (closed
                            || pending != next
                            || bytes == null
                            || length > bytes.length - position) invalid();
                    System.arraycopy(value, offset, bytes, position, length);
                    position += length;
                }
            };
        }

        @Override
        public void verified(CdsExportCapturedFieldDto next) throws IOException {
            if (closed) invalid();
            if (!next.table().equals("casemgmt_note") || !COLUMNS.contains(next.column())) return;
            if (pending != next || bytes == null || position != bytes.length) invalid();
            try {
                values.put(
                        next.column(),
                        next.kind() == CdsExportCapturedFieldDto.Kind.SQL_NULL
                                ? null
                                : StandardCharsets.UTF_8
                                        .newDecoder()
                                        .onMalformedInput(CodingErrorAction.REPORT)
                                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                                        .decode(ByteBuffer.wrap(bytes))
                                        .toString());
            } finally {
                Arrays.fill(bytes, (byte) 0);
                bytes = null;
                pending = null;
                position = 0;
            }
        }

        void finish() throws IOException {
            if (closed || pending != null) invalid();
            if (key == null) return;
            if (!values.keySet().equals(COLUMNS) || ++rows > 8192) invalid();
            CdsExportNoteRevisionDto revision;
            try {
                long note = Long.parseLong(values.get("note_id"));
                if (!Long.toString(note).equals(values.get("note_id"))
                        || !Integer.toString(patient).equals(values.get("demographic_no"))
                        || !CdsExportPatientTableSourceReader.key(note).equals(key)) {
                    invalid();
                    return;
                }
                revision =
                        new CdsExportNoteRevisionDto(
                                note,
                                patient,
                                values.get("uuid"),
                                values.get("observation_date"),
                                values.get("update_date"),
                                values.get("archived"),
                                values.get("signed"));
            } catch (IllegalArgumentException | NullPointerException malformed) {
                invalid();
                return;
            }
            consumer.accept(revision);
            key = null;
            values.clear();
        }

        @Override
        public void close() {
            closed = true;
            if (bytes != null) Arrays.fill(bytes, (byte) 0);
            bytes = null;
            pending = null;
            key = null;
            values.clear();
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_NOTE_REVISION_METADATA_INVALID");
    }
}
