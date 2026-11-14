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

import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bounded projection from the authenticated captured generation, never from live clinical
 * rows. The complete manifest, all captured fields and both transport EOFs are verified
 * before returning the selected patient's base demographic row to a mapper.
 */
public final class CdsExportDemographicCaptureReader {
    private final CdsExportNativeCaptureService captures;

    public CdsExportDemographicCaptureReader(CdsExportNativeCaptureService captures) {
        this.captures = java.util.Objects.requireNonNull(captures);
    }

    public CdsExportDemographicRowDto read(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportStagedFileDto manifest,
            long maximumFields,
            long maximumBytes,
            CdsExportRunControl control)
            throws IOException {
        if (!requiredTables.contains("demographic"))
            throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_CAPTURE_REQUIRED");
        try (var fields = new Fields(patient.patient())) {
            var capture =
                    captures.scan(
                            lease,
                            patient,
                            requiredTables,
                            manifest,
                            maximumFields,
                            maximumBytes,
                            control,
                            fields);
            if (capture.tables().get("demographic").rows() != 1)
                throw new IOException("CDS_EXPORT_DEMOGRAPHIC_CAPTURE_INVALID");
            return fields.result();
        }
    }

    private static final class Fields
            implements CdsExportNativeCaptureReader.Fields, AutoCloseable {
        private static final int MAX_ROW_BYTES = 1_048_576;
        private final int patient;
        private final String rowKey;
        private final Set<String> columns;
        private final Map<String, String> values = new HashMap<>();
        private byte[] pending;
        private CdsExportCapturedFieldDto field;
        private int position;
        private long total;

        private Fields(int patient) {
            this.patient = patient;
            rowKey = String.format(Locale.ROOT, "%010d", patient);
            var names = new HashSet<>(CdsExportDemographicRowDto.COLUMNS);
            names.add("demographic_no");
            columns = Set.copyOf(names);
        }

        @Override
        public OutputStream output(CdsExportCapturedFieldDto next) throws IOException {
            if (!next.table().equals("demographic")) return OutputStream.nullOutputStream();
            if (field != null
                    || !next.recordKey().equals(rowKey)
                    || !columns.contains(next.column())
                    || values.containsKey(next.column())
                    || next.kind() == CdsExportCapturedFieldDto.Kind.BINARY
                    || next.bytes() > MAX_ROW_BYTES - total)
                throw new IOException("CDS_EXPORT_DEMOGRAPHIC_CAPTURE_INVALID");
            field = next;
            total += next.bytes();
            pending = new byte[(int) next.bytes()];
            position = 0;
            return new OutputStream() {
                @Override
                public void write(int value) throws IOException {
                    if (field != next || position == pending.length)
                        throw new IOException("CDS_EXPORT_DEMOGRAPHIC_CAPTURE_INVALID");
                    pending[position++] = (byte) value;
                }

                @Override
                public void write(byte[] bytes, int offset, int length) throws IOException {
                    java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
                    if (field != next || length > pending.length - position)
                        throw new IOException("CDS_EXPORT_DEMOGRAPHIC_CAPTURE_INVALID");
                    System.arraycopy(bytes, offset, pending, position, length);
                    position += length;
                }
            };
        }

        @Override
        public void verified(CdsExportCapturedFieldDto next) throws IOException {
            if (!next.table().equals("demographic")) return;
            if (field != next || position != pending.length)
                throw new IOException("CDS_EXPORT_DEMOGRAPHIC_CAPTURE_INVALID");
            try {
                String value =
                        next.kind() == CdsExportCapturedFieldDto.Kind.SQL_NULL
                                ? null
                                : StandardCharsets.UTF_8
                                        .newDecoder()
                                        .onMalformedInput(CodingErrorAction.REPORT)
                                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                                        .decode(ByteBuffer.wrap(pending))
                                        .toString();
                values.put(next.column(), value);
            } finally {
                Arrays.fill(pending, (byte) 0);
                pending = null;
                field = null;
                position = 0;
            }
        }

        private CdsExportDemographicRowDto result() throws IOException {
            if (field != null
                    || !values.keySet().equals(columns)
                    || !Integer.toString(patient).equals(values.get("demographic_no"))) {
                throw new IOException("CDS_EXPORT_DEMOGRAPHIC_CAPTURE_INVALID");
            }
            values.remove("demographic_no");
            return new CdsExportDemographicRowDto(patient, values);
        }

        @Override
        public void close() {
            if (pending != null) Arrays.fill(pending, (byte) 0);
            pending = null;
            values.clear();
        }
    }
}
