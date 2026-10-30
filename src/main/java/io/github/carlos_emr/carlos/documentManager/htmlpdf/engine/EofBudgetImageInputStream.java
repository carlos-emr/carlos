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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.engine;

import java.io.IOException;
import java.util.Objects;
import javax.imageio.IIOException;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageInputStreamImpl;

/**
 * A view of another {@link ImageInputStream} that cuts off a reader which keeps reading past the
 * end of the data.
 *
 * <p>Why it exists: the TwelveMonkeys lossless WebP decoder catches the {@code EOFException} its
 * bit reader gets when it prefetches at the tail of the data, and carries on with the bits it has.
 * That is harmless for a valid file, which reads past the end a bounded number of times (828 at
 * most in 14 600 valid files). A corrupt count in the header, however, sends the decoder round its
 * Huffman loops, each iteration reading past the end and raising a fresh {@code EOFException}:
 * 36-byte corrupt files kept a thread busy for 15 s and more, and one for over four minutes. No
 * other reader showed anything of the kind. The decode cannot be interrupted, so the stream it
 * reads from has to stop it.</p>
 *
 * <p>How it works: reads inside the data pass straight through to the wrapped stream, as do seeks.
 * A read that finds the end of the data (the wrapped stream returns {@code -1}, which the caller
 * sees as an ordinary end of data or, through {@code readByte}, as an {@code EOFException}) is
 * counted. The first {@code budget} such reads are tolerated. The next one, and every one after
 * it, fails with an {@link IIOException}. That is not an {@code EOFException}, so a reader that
 * swallows {@code EOFException} does not swallow it. The count is for the whole life of the view
 * and not for consecutive reads, because the library seeks back after each end-of-data read.</p>
 *
 * <p>The view does not own the wrapped stream: closing it leaves the wrapped stream open. It is
 * not thread-safe, like the streams it wraps, and is created per decode. Its message names no file
 * and no content.</p>
 *
 * @since 2026-10-29
 */
final class EofBudgetImageInputStream extends ImageInputStreamImpl {

    private final ImageInputStream stream;
    private final int budget;
    private int endReads;

    /**
     * Wraps {@code stream}, starting at its current position.
     *
     * @param stream the stream to read from; not closed by this view
     * @param budget how many reads at the end of the data are tolerated, at least 0
     * @throws IOException if the position of {@code stream} cannot be read
     * @throws NullPointerException if {@code stream} is {@code null}
     * @throws IllegalArgumentException if {@code budget} is negative
     */
    EofBudgetImageInputStream(ImageInputStream stream, int budget) throws IOException {
        this.stream = Objects.requireNonNull(stream, "stream");
        if (budget < 0) {
            throw new IllegalArgumentException("budget must not be negative");
        }
        this.budget = budget;
        this.streamPos = stream.getStreamPosition();
    }

    /**
     * {@return how many reads at the end of the data have been tolerated so far, never more than
     * the budget}
     */
    int endReads() {
        return endReads;
    }

    @Override
    public int read() throws IOException {
        checkClosed();
        bitOffset = 0;
        int value = stream.read();
        if (value < 0) {
            countEndRead();
        } else {
            streamPos++;
        }
        return value;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        checkClosed();
        bitOffset = 0;
        int count = stream.read(b, off, len);
        if (count < 0) {
            countEndRead();
        } else {
            streamPos += count;
        }
        return count;
    }

    @Override
    public void seek(long pos) throws IOException {
        // The base class validates (closed, before the flushed position) and moves this view; the
        // wrapped stream follows, so that both always stand at the same position.
        super.seek(pos);
        stream.seek(pos);
    }

    @Override
    public long length() {
        try {
            return stream.length();
        } catch (IOException e) {
            return -1L;
        }
    }

    private void countEndRead() throws IOException {
        // The count stops at the budget, so a reader that keeps going after the failure cannot
        // run it round to a negative number.
        if (endReads >= budget) {
            throw new IIOException("the reader kept reading past the end of the data");
        }
        endReads++;
    }
}
