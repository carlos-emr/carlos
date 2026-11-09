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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawCellDto;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/** Snapshot-bound exact source range; SQL's independently computed byte count/hash must match full EOF. */
final class CdsExportRawValueInputStream extends InputStream {
    @FunctionalInterface
    interface Chunks {
        byte[] read(long offset, int length) throws IOException;
    }

    private final Chunks source;
    private final CdsExportRawCellDto expected;

    // Per-operation incremental digest, owned by this stream/row accumulator; never shared
    // singleton state.
    @SuppressWarnings("java:S9428")
    private final MessageDigest digest;

    private final Thread owner = Thread.currentThread();
    private byte[] buffer;
    private int position;
    private long delivered;
    private boolean closed, failed, verified;

    CdsExportRawValueInputStream(
            CdsExportPatientTableReadDao source,
            int patient,
            long row,
            String column,
            CdsExportRawCellDto expected) {
        this(
                expected,
                (offset, length) ->
                        source.readCdsExportValueChunk(patient, row, column, offset, length));
    }

    CdsExportRawValueInputStream(CdsExportRawCellDto expected, Chunks source) {
        this.source = Objects.requireNonNull(source);
        this.expected = Objects.requireNonNull(expected);
        if (expected.sqlNull())
            throw new IllegalArgumentException("CDS_EXPORT_NULL_SOURCE_STREAM_REFUSED");
        buffer = expected.inlineBytes();
        if (buffer == null) buffer = new byte[0];
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int count = read(one, 0, 1);
        return count < 0 ? -1 : one[0] & 255;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        try {
            if (closed || failed || Thread.currentThread() != owner)
                throw new IOException("CDS_EXPORT_NATIVE_VALUE_STREAM_CLOSED");
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (Thread.currentThread().isInterrupted())
                throw new IOException("CDS_EXPORT_CANCELLED");
            if (length == 0) return 0;
            if (delivered == expected.bytes()) {
                if (!verified) {
                    if (!MessageDigest.isEqual(
                            digest.digest(), HexFormat.of().parseHex(expected.sha256()))) {
                        throw new IOException("CDS_EXPORT_NATIVE_VALUE_CHANGED");
                    }
                    verified = true;
                }
                return -1;
            }
            if (position == buffer.length) {
                Arrays.fill(buffer, (byte) 0);
                position = 0;
                int wanted = (int) Math.min(65_536, expected.bytes() - delivered);
                buffer = source.read(delivered, wanted);
                if (buffer == null || buffer.length != wanted)
                    throw new IOException("CDS_EXPORT_NATIVE_VALUE_CHANGED");
            }
            int count = Math.min(length, buffer.length - position);
            if (count > expected.bytes() - delivered)
                throw new IOException("CDS_EXPORT_NATIVE_VALUE_CHANGED");
            System.arraycopy(buffer, position, bytes, offset, count);
            digest.update(buffer, position, count);
            position += count;
            delivered += count;
            return count;
        } catch (IOException | RuntimeException | Error failure) {
            failed = true;
            throw failure;
        }
    }

    @Override
    public void close() {
        closed = true;
        if (buffer != null) Arrays.fill(buffer, (byte) 0);
    }
}
