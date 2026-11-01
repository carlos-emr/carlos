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
package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** ZIP stream whose expanded bytes and entries are bounded, independently of entry metadata. */
public final class ImportZipInputStream extends ZipInputStream {
    private final long maxEntryBytes;
    private final long maxTotalBytes;
    private final int maxEntries;
    private long entryBytes;
    private long totalBytes;
    private int entries;

    /**
     * Creates an owning stream with positive per-entry, aggregate byte and entry-count limits.
     * Directory and skipped entries consume the same budgets as content entries.
     *
     * @param input compressed ZIP input, closed with this stream
     * @param maxEntryBytes maximum expanded bytes in one entry
     * @param maxTotalBytes maximum expanded bytes across the archive
     * @param maxEntries maximum entries, including empty entries
     */
    public ImportZipInputStream(InputStream input, long maxEntryBytes, long maxTotalBytes, int maxEntries) {
        super(input);
        if (maxEntryBytes < 1 || maxTotalBytes < 1 || maxEntries < 1) {
            throw new IllegalArgumentException("ZIP import limits must be positive");
        }
        this.maxEntryBytes = maxEntryBytes;
        this.maxTotalBytes = maxTotalBytes;
        this.maxEntries = maxEntries;
    }

    /** Drains any previous entry through the byte limits before opening the next entry. */
    @Override
    public ZipEntry getNextEntry() throws IOException {
        ZipEntry entry = super.getNextEntry();
        entryBytes = 0;
        if (entry != null) {
            if (entries == maxEntries) throw new IOException("ZIP import entry count limit exceeded");
            entries++;
        }
        return entry;
    }

    /** Checks actual expanded bytes before returning them to a parser or a file writer. */
    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, buffer.length);
        if (length == 0) return 0;
        long remaining = Math.min(maxEntryBytes - entryBytes, maxTotalBytes - totalBytes);
        // Read at most one byte beyond the budget, including when probing EOF at an exact limit.
        int request = (int) Math.min(length, remaining < length ? remaining + 1 : length);
        int count = super.read(buffer, offset, request);
        if (count > 0) {
            if (count > remaining) throw new IOException("ZIP import expanded byte limit exceeded");
            entryBytes += count;
            totalBytes += count;
        }
        return count;
    }
}
