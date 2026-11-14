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
package io.github.carlos_emr.carlos.demographic.cdsexport.validator;

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPackageDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPackageMemberDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportPackageComposer;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportPackageMembers;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportPackagePaths;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportStagingService;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.zip.CRC32;

/**
 * Independent bounded reader for the exporter's deterministic STORED ZIP/ZIP64 profile.
 * Verifies actual payload hashes/CRCs and names against the sealed member inventory, then
 * compares every central record to encrypted receipts produced by that read. Requires an
 * exact end-of-central-directory and EOF, including the authenticated staging terminator.
 * No Java ZIP reader's acceptance of a truncated or missing central directory is assumed.
 */
public final class CdsExportPackageValidator {
    private static final long DWORD = 0xffff_ffffL;

    // Cleanup only throws without a primary failure; otherwise its error is suppressed on that
    // failure.
    @SuppressWarnings("java:S1143")
    public void validate(
            CdsExportStagingService staging,
            CdsExportPackageMembers members,
            CdsExportPackageDto expected,
            Runnable checkpoint)
            throws IOException {
        CdsExportStagedFileDto[] localProof = {null};
        Throwable primary = null;
        try (var raw = staging.open(expected.archive());
                var input = new Counted(raw)) {
            MessageDigest inventory = CdsExportPackageComposer.digest();
            long[] entries = {0}, expanded = {0};
            localProof[0] =
                    staging.stage(
                            "PACKAGE_LOCAL_CHECK",
                            output -> {
                                var receipts = new DataOutputStream(output);
                                var paths = new CdsExportPackagePaths();
                                try (var cursor = members.open()) {
                                    CdsExportPackageMemberDto member;
                                    while ((member = cursor.next()) != null) {
                                        checkpoint.run();
                                        byte[] name = paths.accept(member.relativePath());
                                        long offset = input.count;
                                        if (++entries[0] > expected.entries()) fail();
                                        long size = member.file().plaintextBytes();
                                        boolean large = size >= DWORD;
                                        if (u32(input) != 0x04034b50L
                                                || u16(input) != (large ? 45 : 20)
                                                || u16(input) != 2048
                                                || u16(input) != 0
                                                || u16(input) != 0
                                                || u16(input) != 33) fail();
                                        long crc = u32(input);
                                        if (u32(input) != (large ? DWORD : size)
                                                || u32(input) != (large ? DWORD : size)
                                                || u16(input) != name.length
                                                || u16(input) != (large ? 20 : 0)) fail();
                                        if (!Arrays.equals(input.readNBytes(name.length), name))
                                            fail();
                                        if (large
                                                && (u16(input) != 1
                                                        || u16(input) != 16
                                                        || u64(input) != size
                                                        || u64(input) != size)) fail();
                                        CRC32 actualCrc = new CRC32();
                                        MessageDigest actualHash = sha256();
                                        byte[] buffer = new byte[65536];
                                        long left = size;
                                        while (left > 0) {
                                            checkpoint.run();
                                            int read =
                                                    input.read(
                                                            buffer,
                                                            0,
                                                            (int) Math.min(left, buffer.length));
                                            if (read < 0) throw new EOFException();
                                            if (read == 0) continue;
                                            actualCrc.update(buffer, 0, read);
                                            actualHash.update(buffer, 0, read);
                                            left -= read;
                                        }
                                        if (actualCrc.getValue() != crc
                                                || !HexFormat.of()
                                                        .formatHex(actualHash.digest())
                                                        .equals(member.file().plaintextSha256()))
                                            fail();
                                        CdsExportPackageComposer.bind(inventory, member);
                                        expanded[0] = Math.addExact(expanded[0], size);
                                        receipts.writeLong(offset);
                                        receipts.writeLong(crc);
                                        receipts.writeLong(size);
                                    }
                                    receipts.flush();
                                }
                            });
            if (entries[0] != expected.entries()
                    || expanded[0] != expected.expandedBytes()
                    || !HexFormat.of()
                            .formatHex(inventory.digest())
                            .equals(expected.membersSha256())) fail();
            long centralOffset = input.count;
            long centralEntries = 0;
            MessageDigest centralInventory = CdsExportPackageComposer.digest();
            try (var cursor = members.open();
                    var receiptInput = new DataInputStream(staging.open(localProof[0]))) {
                CdsExportPackageMemberDto member;
                while ((member = cursor.next()) != null) {
                    checkpoint.run();
                    if (++centralEntries > entries[0]) fail();
                    CdsExportPackageComposer.bind(centralInventory, member);
                    long offset = receiptInput.readLong(),
                            crc = receiptInput.readLong(),
                            size = receiptInput.readLong();
                    if (size != member.file().plaintextBytes()) fail();
                    byte[] name =
                            member.relativePath().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    boolean largeSize = size >= DWORD, largeOffset = offset >= DWORD;
                    boolean large = largeSize || largeOffset;
                    int extra = largeSize ? 16 : 0;
                    if (largeOffset) extra += 8;
                    if (u32(input) != 0x02014b50L
                            || u16(input) != (large ? 45 : 20)
                            || u16(input) != (large ? 45 : 20)
                            || u16(input) != 2048
                            || u16(input) != 0
                            || u16(input) != 0
                            || u16(input) != 33
                            || u32(input) != crc
                            || u32(input) != (largeSize ? DWORD : size)
                            || u32(input) != (largeSize ? DWORD : size)
                            || u16(input) != name.length
                            || u16(input) != (extra > 0 ? extra + 4 : 0)
                            || u16(input) != 0
                            || u16(input) != 0
                            || u16(input) != 0
                            || u32(input) != 0
                            || u32(input) != (largeOffset ? DWORD : offset)) fail();
                    if (!Arrays.equals(input.readNBytes(name.length), name)) fail();
                    if (extra > 0) {
                        if (u16(input) != 1 || u16(input) != extra) fail();
                        if (largeSize && (u64(input) != size || u64(input) != size)) fail();
                        if (largeOffset && u64(input) != offset) fail();
                    }
                }
                if (centralEntries != entries[0]
                        || receiptInput.read() != -1
                        || !HexFormat.of()
                                .formatHex(centralInventory.digest())
                                .equals(expected.membersSha256())) fail();
            }
            long centralSize = input.count - centralOffset;
            if (entries[0] >= 65535 || centralOffset >= DWORD || centralSize >= DWORD) {
                long zip64Offset = input.count;
                if (u32(input) != 0x06064b50L
                        || u64(input) != 44
                        || u16(input) != 45
                        || u16(input) != 45
                        || u32(input) != 0
                        || u32(input) != 0
                        || u64(input) != entries[0]
                        || u64(input) != entries[0]
                        || u64(input) != centralSize
                        || u64(input) != centralOffset
                        || u32(input) != 0x07064b50L
                        || u32(input) != 0
                        || u64(input) != zip64Offset
                        || u32(input) != 1) fail();
            }
            if (u32(input) != 0x06054b50L
                    || u16(input) != 0
                    || u16(input) != 0
                    || u16(input) != Math.min(entries[0], 65535)
                    || u16(input) != Math.min(entries[0], 65535)
                    || u32(input) != Math.min(centralSize, DWORD)
                    || u32(input) != Math.min(centralOffset, DWORD)
                    || u16(input) != 0
                    || input.read() != -1) fail();
            checkpoint.run();
        } catch (EOFException truncated) {
            var failure = new IOException("CDS_EXPORT_PACKAGE_TRUNCATED");
            primary = failure;
            throw failure;
        } catch (IOException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            if (localProof[0] != null)
                try {
                    staging.discardUnpublished(localProof[0]);
                } catch (IOException cleanup) {
                    if (primary != null) primary.addSuppressed(cleanup);
                    else throw cleanup;
                }
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private static void fail() throws IOException {
        throw new IOException("CDS_EXPORT_PACKAGE_INVALID");
    }

    private static int u16(InputStream input) throws IOException {
        int first = input.read(), second = input.read();
        if ((first | second) < 0) throw new EOFException();
        return first | second << 8;
    }

    private static long u32(InputStream input) throws IOException {
        return u16(input) | (long) u16(input) << 16;
    }

    private static long u64(InputStream input) throws IOException {
        return u32(input) | u32(input) << 32;
    }

    private static final class Counted extends FilterInputStream {
        private long count;

        Counted(InputStream input) {
            super(input);
        }

        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value >= 0) count++;
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int n = in.read(bytes, offset, length);
            if (n > 0) count += n;
            return n;
        }
    }
}
