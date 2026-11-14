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

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Bounded field view of an export-owned authenticated values stream. Prefix/suffix bytes
 * are consumed, never exposed. EOF and close verify both the selected field digest and
 * the enclosing stream's authenticated EOF, then recheck authority. Closing early drains
 * the complete authenticated source; abandoning a field cannot hide a corrupt suffix.
 * A caller must bind offsets and hashes to a completely verified owned index first.
 */
final class CdsExportAuthenticatedRangeInputStream extends InputStream {
    @FunctionalInterface
    interface Checkpoint {
        void run() throws IOException;
    }

    private final InputStream source;

    // Per-operation incremental digest, owned by this stream/row accumulator; never shared
    // singleton state.
    @SuppressWarnings("java:S9428")
    private final MessageDigest digest;

    private final byte[] expected;
    private final Checkpoint checkpoint;
    private final Runnable authorize;
    private long remaining;
    private boolean finished, failed, closed;

    CdsExportAuthenticatedRangeInputStream(
            InputStream source,
            long offset,
            long bytes,
            String sha256,
            Checkpoint checkpoint,
            Runnable authorize)
            throws IOException {
        this.source = Objects.requireNonNull(source);
        this.checkpoint = Objects.requireNonNull(checkpoint);
        this.authorize = Objects.requireNonNull(authorize);
        if (offset < 0
                || bytes < 0
                || bytes > Long.MAX_VALUE - offset
                || sha256 == null
                || !sha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("CDS_EXPORT_VALUE_RANGE_INVALID");
        this.remaining = bytes;
        expected = HexFormat.of().parseHex(sha256);
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("CDS_EXPORT_SHA256_UNAVAILABLE");
        }
        try {
            drain(offset);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                source.close();
            } catch (IOException | RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int count = read(one, 0, 1);
        return count == -1 ? -1 : one[0] & 255;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        try {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (closed || failed) throw new IOException("CDS_EXPORT_VALUE_RANGE_CLOSED_OR_FAILED");
            checkpoint.run();
            if (length == 0) return 0;
            if (remaining == 0) {
                finish();
                return -1;
            }
            int count = source.read(bytes, offset, (int) Math.min(length, remaining));
            if (count <= 0) throw new IOException("CDS_EXPORT_VALUE_RANGE_TRUNCATED");
            digest.update(bytes, offset, count);
            remaining -= count;
            return count;
        } catch (IOException | RuntimeException | Error failure) {
            failed = true;
            throw failure;
        }
    }

    private void finish() throws IOException {
        if (finished) return;
        if (!MessageDigest.isEqual(digest.digest(), expected))
            throw new IOException("CDS_EXPORT_VALUE_RANGE_DIGEST_MISMATCH");
        byte[] buffer = new byte[65_536];
        try {
            int count;
            do {
                checkpoint.run();
                count = source.read(buffer);
                if (count == 0) throw new IOException("CDS_EXPORT_VALUE_RANGE_STALLED");
            } while (count != -1);
            authorize.run();
            checkpoint.run();
            finished = true;
        } finally {
            Arrays.fill(buffer, (byte) 0);
        }
    }

    private void drain(long bytes) throws IOException {
        byte[] buffer = new byte[65_536];
        try {
            while (bytes > 0) {
                checkpoint.run();
                int count = source.read(buffer, 0, (int) Math.min(buffer.length, bytes));
                if (count <= 0) throw new IOException("CDS_EXPORT_VALUE_RANGE_TRUNCATED");
                bytes -= count;
            }
        } finally {
            Arrays.fill(buffer, (byte) 0);
        }
    }

    // Cleanup only throws without a primary failure; otherwise its error is suppressed on that
    // failure.
    @SuppressWarnings("java:S1143")
    @Override
    public void close() throws IOException {
        if (closed) return;
        Throwable primary = null;
        try {
            if (!failed) {
                byte[] buffer = new byte[65_536];
                try {
                    while (read(buffer) != -1) {
                        /* Finish the range and authenticated enclosing EOF. */
                    }
                } finally {
                    Arrays.fill(buffer, (byte) 0);
                }
            }
        } catch (IOException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            closed = true;
            try {
                source.close();
            } catch (IOException | RuntimeException cleanup) {
                if (primary != null) primary.addSuppressed(cleanup);
                else throw cleanup;
            }
        }
    }
}
