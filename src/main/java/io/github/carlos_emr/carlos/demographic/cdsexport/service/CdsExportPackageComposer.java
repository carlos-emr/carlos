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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPackageDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPackageMemberDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportPackageValidator;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * Creates a finished ZIP/ZIP64 from immutable encrypted members and independently reads its
 * local records, payloads, central directory and end records before returning byte evidence.
 * Central records and verification receipts are encrypted streams, not an in-memory entry list.
 * STORED entries avoid a receiving importer's compression-ratio refusal for large repetitive
 * clinical text. This byte layer does not establish clinical or disclosure readiness.
 */
public final class CdsExportPackageComposer {
    // Cleanup only throws without a primary failure; otherwise its error is suppressed on that
    // failure.
    @SuppressWarnings("java:S1143")
    public CdsExportPackageDto compose(
            CdsExportStagingService staging,
            CdsExportPackageMembers members,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(staging);
        Objects.requireNonNull(members);
        Objects.requireNonNull(checkpoint);
        long[] count = {0}, expanded = {0};
        MessageDigest inventory = digest();
        var archive =
                staging.stage(
                        "PACKAGE_ZIP",
                        output -> {
                            var zip = new Counted(output);
                            CdsExportStagedFileDto[] central = {null};
                            Throwable primary = null;
                            try {
                                central[0] =
                                        staging.stage(
                                                "PACKAGE_CENTRAL",
                                                directory -> {
                                                    var paths = new CdsExportPackagePaths();
                                                    try (var cursor = members.open()) {
                                                        CdsExportPackageMemberDto member;
                                                        while ((member = cursor.next()) != null) {
                                                            checkpoint.run();
                                                            byte[] name =
                                                                    paths.accept(
                                                                            member.relativePath());
                                                            // ZIP64 counts entries in 64
                                                            // bits: no count is refused.
                                                            count[0] = Math.addExact(count[0], 1);
                                                            long size =
                                                                    member.file().plaintextBytes();
                                                            expanded[0] =
                                                                    Math.addExact(
                                                                            expanded[0], size);
                                                            bind(inventory, member);
                                                            CRC32 crc = new CRC32();
                                                            byte[] buffer = new byte[65536];
                                                            int n;
                                                            try (var input =
                                                                    staging.open(member.file())) {
                                                                while ((n = input.read(buffer))
                                                                        != -1) {
                                                                    checkpoint.run();
                                                                    crc.update(buffer, 0, n);
                                                                }
                                                            }
                                                            long offset = zip.bytes;
                                                            boolean
                                                                    largeSize =
                                                                            size >= 0xffff_ffffL,
                                                                    largeOffset =
                                                                            offset >= 0xffff_ffffL;
                                                            u32(zip, 0x04034b50L);
                                                            u16(zip, largeSize ? 45 : 20);
                                                            u16(zip, 2048);
                                                            u16(zip, 0);
                                                            u16(zip, 0);
                                                            u16(zip, 33);
                                                            u32(zip, crc.getValue());
                                                            u32(
                                                                    zip,
                                                                    largeSize
                                                                            ? 0xffff_ffffL
                                                                            : size);
                                                            u32(
                                                                    zip,
                                                                    largeSize
                                                                            ? 0xffff_ffffL
                                                                            : size);
                                                            u16(zip, name.length);
                                                            u16(zip, largeSize ? 20 : 0);
                                                            zip.write(name);
                                                            if (largeSize) {
                                                                u16(zip, 1);
                                                                u16(zip, 16);
                                                                u64(zip, size);
                                                                u64(zip, size);
                                                            }
                                                            try (var input =
                                                                    staging.open(member.file())) {
                                                                while ((n = input.read(buffer))
                                                                        != -1) {
                                                                    checkpoint.run();
                                                                    zip.write(buffer, 0, n);
                                                                }
                                                            }
                                                            boolean large =
                                                                    largeSize || largeOffset;
                                                            int extra =
                                                                    (largeSize ? 16 : 0)
                                                                            + (largeOffset ? 8 : 0);
                                                            u32(directory, 0x02014b50L);
                                                            u16(directory, large ? 45 : 20);
                                                            u16(directory, large ? 45 : 20);
                                                            u16(directory, 2048);
                                                            u16(directory, 0);
                                                            u16(directory, 0);
                                                            u16(directory, 33);
                                                            u32(directory, crc.getValue());
                                                            u32(
                                                                    directory,
                                                                    largeSize
                                                                            ? 0xffff_ffffL
                                                                            : size);
                                                            u32(
                                                                    directory,
                                                                    largeSize
                                                                            ? 0xffff_ffffL
                                                                            : size);
                                                            u16(directory, name.length);
                                                            u16(
                                                                    directory,
                                                                    extra > 0 ? extra + 4 : 0);
                                                            u16(directory, 0);
                                                            u16(directory, 0);
                                                            u16(directory, 0);
                                                            u32(directory, 0);
                                                            u32(
                                                                    directory,
                                                                    largeOffset
                                                                            ? 0xffff_ffffL
                                                                            : offset);
                                                            directory.write(name);
                                                            if (extra > 0) {
                                                                u16(directory, 1);
                                                                u16(directory, extra);
                                                                if (largeSize) {
                                                                    u64(directory, size);
                                                                    u64(directory, size);
                                                                }
                                                                if (largeOffset)
                                                                    u64(directory, offset);
                                                            }
                                                        }
                                                    }
                                                    if (count[0] == 0)
                                                        throw new IOException(
                                                                "CDS_EXPORT_PACKAGE_EMPTY");
                                                });
                                long centralOffset = zip.bytes;
                                try (var input = staging.open(central[0])) {
                                    byte[] buffer = new byte[65536];
                                    int n;
                                    while ((n = input.read(buffer)) != -1) {
                                        checkpoint.run();
                                        zip.write(buffer, 0, n);
                                    }
                                }
                                long centralSize = zip.bytes - centralOffset;
                                if (count[0] >= 65535
                                        || centralSize >= 0xffff_ffffL
                                        || centralOffset >= 0xffff_ffffL) {
                                    long zip64Offset = zip.bytes;
                                    u32(zip, 0x06064b50L);
                                    u64(zip, 44);
                                    u16(zip, 45);
                                    u16(zip, 45);
                                    u32(zip, 0);
                                    u32(zip, 0);
                                    u64(zip, count[0]);
                                    u64(zip, count[0]);
                                    u64(zip, centralSize);
                                    u64(zip, centralOffset);
                                    u32(zip, 0x07064b50L);
                                    u32(zip, 0);
                                    u64(zip, zip64Offset);
                                    u32(zip, 1);
                                }
                                u32(zip, 0x06054b50L);
                                u16(zip, 0);
                                u16(zip, 0);
                                u16(zip, Math.min(count[0], 65535));
                                u16(zip, Math.min(count[0], 65535));
                                u32(zip, Math.min(centralSize, 0xffff_ffffL));
                                u32(zip, Math.min(centralOffset, 0xffff_ffffL));
                                u16(zip, 0);
                                checkpoint.run();
                            } catch (IOException | RuntimeException | Error failure) {
                                primary = failure;
                                throw failure;
                            } finally {
                                if (central[0] != null)
                                    try {
                                        staging.discardUnpublished(central[0]);
                                    } catch (IOException cleanup) {
                                        if (primary != null) primary.addSuppressed(cleanup);
                                        else throw cleanup;
                                    }
                            }
                        });
        var proof =
                new CdsExportPackageDto(
                        archive,
                        count[0],
                        expanded[0],
                        HexFormat.of().formatHex(inventory.digest()));
        try {
            new CdsExportPackageValidator().validate(staging, members, proof, checkpoint);
            return proof;
        } catch (IOException | RuntimeException | Error failure) {
            try {
                staging.discardUnpublished(archive);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private static void u16(OutputStream out, long value) throws IOException {
        out.write((int) value);
        out.write((int) (value >>> 8));
    }

    private static void u32(OutputStream out, long value) throws IOException {
        u16(out, value);
        u16(out, value >>> 16);
    }

    private static void u64(OutputStream out, long value) throws IOException {
        u32(out, value);
        u32(out, value >>> 32);
    }

    /** Counts archive offsets; ZIP64 records carry them in 64 bits and no size is refused. */
    private static final class Counted extends FilterOutputStream {
        private long bytes;

        Counted(OutputStream output) {
            super(output);
        }

        @Override
        public void write(int value) throws IOException {
            out.write(value);
            bytes++;
        }

        @Override
        public void write(byte[] value, int offset, int length) throws IOException {
            out.write(value, offset, length);
            bytes += length;
        }
    }

    /** Domain-separated length-prefixed evidence binds both logical path and exact captured file identity. */
    public static void bind(MessageDigest digest, CdsExportPackageMemberDto member) {
        for (String value :
                new String[] {
                    member.relativePath(),
                    member.file().memberId(),
                    member.file().purpose(),
                    member.file().plaintextSha256(),
                    Long.toString(member.file().plaintextBytes())
                }) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
            digest.update(bytes);
        }
    }

    public static MessageDigest digest() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update("CARLOS-CDS-PACKAGE-MEMBERS-1\n".getBytes(StandardCharsets.US_ASCII));
            return digest;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
}
