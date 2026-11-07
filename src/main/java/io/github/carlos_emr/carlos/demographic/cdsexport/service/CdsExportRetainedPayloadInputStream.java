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

import com.sun.security.auth.module.UnixSystem;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportProtectedFiles;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Exact selected field range, never a copy of a spool containing other categories or patients. */
final class CdsExportRetainedPayloadInputStream extends InputStream {
    private final FileChannel channel;
    private final CdsExportRetainedFieldDto.Payload expected;
    private final Runnable checkpoint;
    private final MessageDigest digest;
    private final Thread owner = Thread.currentThread();
    private long delivered;
    private boolean closed, failed, verified;
    CdsExportRetainedPayloadInputStream(Path root, CdsExportRetainedFieldDto.Payload expected, Runnable checkpoint) throws IOException {
        this.expected = Objects.requireNonNull(expected); this.checkpoint = Objects.requireNonNull(checkpoint);
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        FileChannel opened = null;
        checkpoint.run();
        try {
            Path base = CdsExportProtectedFiles.dataDirectory(root);
            Path file = CdsExportProtectedFiles.absolute(expected.location());
            if (!file.startsWith(base) || file.equals(base)) throw new IOException("CDS_EXPORT_RETAINED_PAYLOAD_OUTSIDE_ROOT");
            CdsExportProtectedFiles.dataDirectory(file.getParent());
            long uid = ((Number) Files.getAttribute(file, "unix:uid", LinkOption.NOFOLLOW_LINKS)).longValue();
            int mode = ((Number) Files.getAttribute(file, "unix:mode", LinkOption.NOFOLLOW_LINKS)).intValue();
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || uid != new UnixSystem().getUid() || (mode & 07077) != 0) {
                throw new IOException("CDS_EXPORT_RETAINED_PAYLOAD_NOT_PRIVATE");
            }
            opened = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
            if (opened.size() < expected.offset() + expected.bytes()) throw new IOException("CDS_EXPORT_RETAINED_PAYLOAD_INCOMPLETE");
            opened.position(expected.offset()); channel = opened;
        } catch (IOException | RuntimeException failure) {
            if (opened != null) try { opened.close(); } catch (IOException ignored) { /* primary refusal retained without a protected path */ }
            throw new IOException("CDS_EXPORT_RETAINED_PAYLOAD_UNAVAILABLE");
        }
    }
    @Override public int read() throws IOException { byte[] one = new byte[1]; int count = read(one, 0, 1); return count < 0 ? -1 : one[0] & 255; }
    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
        try {
            if (closed || failed || Thread.currentThread() != owner) throw new IOException("CDS_EXPORT_RETAINED_PAYLOAD_STREAM_CLOSED");
            Objects.checkFromIndexSize(offset, length, bytes.length); checkpoint.run();
            if (Thread.currentThread().isInterrupted()) throw new IOException("CDS_EXPORT_CANCELLED");
            if (length == 0) return 0;
            if (delivered == expected.bytes()) {
                if (!verified) {
                    if (!MessageDigest.isEqual(digest.digest(), HexFormat.of().parseHex(expected.sha256()))) {
                        throw new IOException("CDS_EXPORT_RETAINED_PAYLOAD_HASH_MISMATCH");
                    }
                    verified = true;
                }
                return -1;
            }
            int wanted = (int) Math.min(Math.min(65_536, length), expected.bytes() - delivered);
            int count;
            try { count = channel.read(ByteBuffer.wrap(bytes, offset, wanted)); }
            catch (IOException failure) { throw new IOException("CDS_EXPORT_RETAINED_PAYLOAD_READ_FAILED"); }
            if (count < 1) throw new IOException("CDS_EXPORT_RETAINED_PAYLOAD_INCOMPLETE");
            digest.update(bytes, offset, count); delivered += count; return count;
        } catch (IOException | RuntimeException | Error failure) { failed = true; throw failure; }
    }
    @Override public void close() throws IOException {
        closed = true;
        try { channel.close(); } catch (IOException failure) { throw new IOException("CDS_EXPORT_RETAINED_PAYLOAD_CLOSE_FAILED"); }
    }
}
