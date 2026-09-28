/* Copyright (c) 2026 CARLOS Contributors. Published under the GPL GNU General Public License. */
package io.github.carlos_emr.carlos.email.core;

import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.io.IOException;

/** Rejects oversized SMTP snapshots and serialized email artifacts before writing excess bytes. */
public final class BoundedEmailOutputStream extends FilterOutputStream {
    private long remaining;

    /**
     * Creates a stream with the given byte budget. Writes exceeding the remaining budget
     * throw {@link IOException} before any bytes from that write reach the delegate.
     *
     * @param output the delegate stream
     * @param limit maximum number of bytes accepted
     */
    public BoundedEmailOutputStream(OutputStream output, long limit) {
        super(output);
        remaining = limit;
    }

    @Override
    public void write(int value) throws IOException {
        requireCapacity(1);
        out.write(value);
        remaining--;
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
        requireCapacity(length);
        out.write(bytes, offset, length);
        remaining -= length;
    }

    private void requireCapacity(int length) throws IOException {
        if (length > remaining) {
            throw new IOException("Prepared email exceeds the archive size limit");
        }
    }
}
