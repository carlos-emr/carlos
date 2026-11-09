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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Independently binds captured note access dependencies to their verified physical fields.
 * The caller authorizes the sealed scope before exposing fields, keeps output private until
 * finish, and rechecks current access afterwards. This verifier grants no permission itself.
 * It buffers at most one 128-byte metadata value; bodies and credential columns are never
 * buffered or interpreted here. Every byte still goes through the underlying capture verifier.
 */
public final class CdsExportCapturedNoteScopeValidator
        implements CdsExportNativeCaptureReader.Fields, AutoCloseable {
    private static final Set<String> COLUMNS =
            Set.of("note_id", "demographic_no", "program_no", "reporter_caisi_role", "locked");
    private final Map<String, CdsExportNoteAccessDto> expected;
    private final Set<String> seen = new HashSet<>();
    private final CdsExportNativeCaptureReader.Fields delegate;
    private CdsExportCapturedFieldDto pending;
    private byte[] bytes;
    private int offset;
    private boolean closed, finished;

    public CdsExportCapturedNoteScopeValidator(
            int patient,
            List<CdsExportNoteAccessDto> scopes,
            CdsExportNativeCaptureReader.Fields delegate)
            throws IOException {
        this.delegate = Objects.requireNonNull(delegate);
        Objects.requireNonNull(scopes);
        if (patient < 1 || scopes.size() > 8192) invalid();
        var expected = new HashMap<String, CdsExportNoteAccessDto>();
        for (var scope : scopes)
            if (scope.patient() != patient
                    || expected.putIfAbsent(
                                    CdsExportPatientTableSourceReader.key(scope.noteId()), scope)
                            != null) invalid();
        this.expected = Map.copyOf(expected);
    }

    @Override
    public OutputStream output(CdsExportCapturedFieldDto field) throws IOException {
        if (closed || finished || pending != null) invalid();
        if (!field.table().equals("casemgmt_note")) return delegate.output(field);
        if (!expected.containsKey(field.recordKey())) invalid();
        if (!COLUMNS.contains(field.column())) return delegate.output(field);
        if (field.kind() == CdsExportCapturedFieldDto.Kind.BINARY
                || field.exactNumeric() != null
                || field.bytes() > 128
                || !seen.add(field.recordKey() + "/" + field.column())) invalid();
        pending = field;
        bytes = new byte[(int) field.bytes()];
        offset = 0;
        OutputStream downstream = delegate.output(field);
        return new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                if (pending != field || bytes == null || offset == bytes.length) invalid();
                bytes[offset++] = (byte) value;
                downstream.write(value);
            }

            @Override
            public void write(byte[] value, int start, int count) throws IOException {
                Objects.checkFromIndexSize(start, count, value.length);
                if (pending != field || bytes == null || count > bytes.length - offset) invalid();
                System.arraycopy(value, start, bytes, offset, count);
                offset += count;
                downstream.write(value, start, count);
            }
        };
    }

    @Override
    public void verified(CdsExportCapturedFieldDto field) throws IOException {
        if (closed || finished) invalid();
        if (!field.table().equals("casemgmt_note") || !COLUMNS.contains(field.column())) {
            delegate.verified(field);
            return;
        }
        if (pending != field || bytes == null || offset != bytes.length) invalid();
        try {
            String actual =
                    field.kind() == CdsExportCapturedFieldDto.Kind.SQL_NULL
                            ? null
                            : StandardCharsets.UTF_8
                                    .newDecoder()
                                    .onMalformedInput(CodingErrorAction.REPORT)
                                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                                    .decode(ByteBuffer.wrap(bytes))
                                    .toString();
            var scope = expected.get(field.recordKey());
            String wanted =
                    switch (field.column()) {
                        case "note_id" -> Long.toString(scope.noteId());
                        case "demographic_no" -> Integer.toString(scope.patient());
                        case "program_no" -> scope.program();
                        case "reporter_caisi_role" -> scope.reporterRole();
                        case "locked" -> null;
                        default -> throw new IOException("CDS_EXPORT_CAPTURE_NOTE_SCOPE_INVALID");
                    };
            if (field.column().equals("locked")
                    ? actual != null && !actual.equals("0")
                    : !Objects.equals(actual, wanted)) invalid();
            delegate.verified(field);
        } finally {
            clear();
        }
    }

    /** Every declared note must supply all five physical dependencies, including an explicit SQL NULL lock. */
    public void finish() throws IOException {
        if (closed
                || finished
                || pending != null
                || seen.size() != expected.size() * COLUMNS.size()) invalid();
        finished = true;
    }

    private void clear() {
        if (bytes != null) Arrays.fill(bytes, (byte) 0);
        bytes = null;
        pending = null;
        offset = 0;
    }

    @Override
    public void close() {
        closed = true;
        clear();
        seen.clear();
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_CAPTURE_NOTE_SCOPE_INVALID");
    }
}
