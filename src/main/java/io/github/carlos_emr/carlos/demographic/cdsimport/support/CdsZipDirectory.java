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
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.util.Locale;

/** Streams ZIP/ZIP64 directory metadata; java.util.zip's local-entry CRC checks alone omit Unix modes. */
public final class CdsZipDirectory {
    private static final ObjectMapper JSON = new ObjectMapper();
    private CdsZipDirectory() { }

    @FunctionalInterface
    public interface Check { void run() throws IOException; }

    public static void validate(Path archive, Path nameIndex, Path snapshot, long maximumEntries,
            long expectedEntries, Check check) throws IOException {
        check.run();
        try (RandomAccessFile file = new RandomAccessFile(archive.toFile(), "r")) {
            long size = file.length();
            if (size < 22) throw CdsPaths.failure("CDS_ZIP_DIRECTORY_INVALID");
            int tailSize = (int) Math.min(size, 65557);
            byte[] tail = new byte[tailSize]; file.seek(size - tailSize); file.readFully(tail);
            int found = -1;
            for (int i = tail.length - 22; i >= 0; i--) {
                if ((i & 1023) == 0) check.run();
                if (little(tail, i, 4) == 0x06054b50L && little(tail, i + 20, 2) == tail.length - i - 22) { found = i; break; }
            }
            if (found < 0) throw CdsPaths.failure("CDS_ZIP_DIRECTORY_INVALID");
            long end = size - tailSize + found;
            if (little(tail, found + 4, 2) != 0 || little(tail, found + 6, 2) != 0) {
                throw CdsPaths.failure("CDS_ZIP_MULTIDISK_UNSUPPORTED");
            }
            long count = little(tail, found + 10, 2);
            long directorySize = little(tail, found + 12, 4);
            long offset = little(tail, found + 16, 4);
            if (count == 65535 || directorySize == 0xffffffffL || offset == 0xffffffffL) {
                file.seek(end - 20);
                if (u32(file) != 0x07064b50L || u32(file) != 0) throw CdsPaths.failure("CDS_ZIP64_DIRECTORY_INVALID");
                long zip64 = u64(file);
                if (u32(file) != 1 || zip64 < 0 || zip64 >= end - 20) throw CdsPaths.failure("CDS_ZIP_MULTIDISK_UNSUPPORTED");
                file.seek(zip64);
                if (u32(file) != 0x06064b50L || u64(file) < 44) throw CdsPaths.failure("CDS_ZIP64_DIRECTORY_INVALID");
                u16(file); u16(file);
                if (u32(file) != 0 || u32(file) != 0) throw CdsPaths.failure("CDS_ZIP_MULTIDISK_UNSUPPORTED");
                long diskCount = u64(file); count = u64(file);
                directorySize = u64(file); offset = u64(file);
                if (diskCount != count) throw CdsPaths.failure("CDS_ZIP_MULTIDISK_UNSUPPORTED");
            } else if (little(tail, found + 8, 2) != count) throw CdsPaths.failure("CDS_ZIP_MULTIDISK_UNSUPPORTED");
            if (count != expectedEntries || count > maximumEntries || count < 0 || offset < 0
                    || directorySize < 0 || offset > end || directorySize > end - offset) {
                throw CdsPaths.failure("CDS_ZIP_DIRECTORY_INVALID");
            }
            file.seek(offset);
            for (long i = 0; i < count; i++) {
                check.run();
                if (u32(file) != 0x02014b50L) throw CdsPaths.failure("CDS_ZIP_DIRECTORY_INVALID");
                int madeBy = u16(file); u16(file);
                int flags = u16(file); u16(file); u16(file); u16(file);
                long crc = u32(file); long compressed = u32(file); long expanded = u32(file);
                int nameLength = u16(file), extraLength = u16(file), commentLength = u16(file);
                int disk = u16(file); u16(file); long external = u32(file); long localOffset = u32(file);
                if (disk != 0 || (flags & 65) != 0) throw CdsPaths.failure("CDS_ZIP_ENCRYPTION_UNSUPPORTED");
                int unixType = (int) ((external >>> 16) & 0170000);
                if ((madeBy >>> 8) == 3 && unixType != 0 && unixType != 0100000 && unixType != 0040000) {
                    throw CdsPaths.failure("CDS_UNSAFE_ZIP_MEMBER");
                }
                byte[] name = new byte[nameLength]; file.readFully(name);
                String relative = new String(name, (flags & 2048) != 0 ? StandardCharsets.UTF_8 : Charset.forName("IBM437"));
                byte[] extra = new byte[extraLength]; file.readFully(extra);
                if (expanded == 0xffffffffL || compressed == 0xffffffffL || localOffset == 0xffffffffL) {
                    int cursor = 0; boolean zip64Found = false;
                    while (cursor + 4 <= extra.length) {
                        int tag = (int) little(extra, cursor, 2), length = (int) little(extra, cursor + 2, 2);
                        cursor += 4;
                        if (length > extra.length - cursor) throw CdsPaths.failure("CDS_ZIP64_DIRECTORY_INVALID");
                        if (tag == 1) {
                            int position = cursor;
                            if (expanded == 0xffffffffL) { if (position + 8 > cursor + length) throw CdsPaths.failure("CDS_ZIP64_DIRECTORY_INVALID"); expanded = little(extra, position, 8); position += 8; }
                            if (compressed == 0xffffffffL) position += 8;
                            if (position > cursor + length) throw CdsPaths.failure("CDS_ZIP64_DIRECTORY_INVALID");
                            zip64Found = true;
                        }
                        cursor += length;
                    }
                    if (!zip64Found) throw CdsPaths.failure("CDS_ZIP64_DIRECTORY_INVALID");
                }
                boolean directory = relative.endsWith("/");
                if (directory && (expanded != 0 || crc != 0)) throw CdsPaths.failure("CDS_ZIP_DIRECTORY_PAYLOAD");
                if ((madeBy >>> 8) == 3 && unixType != 0 && (unixType == 0040000) != directory) {
                    throw CdsPaths.failure("CDS_ZIP_DIRECTORY_MEMBER_MISMATCH");
                }
                if (directory) relative = relative.substring(0, relative.length() - 1);
                Path marker = nameIndex.resolve(key(relative));
                JsonNode foundNode;
                check.run();
                try (var bytes = Files.newInputStream(CdsPaths.within(marker, snapshot), LinkOption.NOFOLLOW_LINKS)) {
                    foundNode = JSON.readTree(bytes.readNBytes(65536));
                } catch (java.nio.file.NoSuchFileException missing) {
                    throw CdsPaths.failure("CDS_ZIP_DIRECTORY_MEMBER_MISMATCH");
                }
                if (foundNode == null || !relative.equals(foundNode.path("relativeName").asText())
                        || directory != foundNode.path("directory").asBoolean()
                        || foundNode.path("zipDirectorySeen").asBoolean()
                        || !foundNode.path("directory").asBoolean()
                            && (foundNode.path("length").asLong(-1) != expanded || foundNode.path("crc32").asLong(-1) != crc)) {
                    throw CdsPaths.failure("CDS_ZIP_DIRECTORY_MEMBER_MISMATCH");
                }
                ((ObjectNode) foundNode).put("zipDirectorySeen", true);
                check.run();
                Files.write(marker, JSON.writeValueAsBytes(foundNode), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                CdsPaths.forceFile(marker);
                check.run();
                file.seek(file.getFilePointer() + commentLength);
            }
            if (file.getFilePointer() != offset + directorySize) throw CdsPaths.failure("CDS_ZIP_DIRECTORY_INVALID");
            check.run();
        } catch (java.io.EOFException truncated) {
            throw CdsPaths.failure("CDS_ZIP_DIRECTORY_TRUNCATED");
        }
    }

    private static String key(String name) {
        return CdsPaths.hex(CdsPaths.sha256().digest(Normalizer.normalize(name, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)));
    }
    private static int u16(RandomAccessFile file) throws IOException { return file.readUnsignedByte() | file.readUnsignedByte() << 8; }
    private static long u32(RandomAccessFile file) throws IOException { return u16(file) | (long) u16(file) << 16; }
    private static long u64(RandomAccessFile file) throws IOException { return u32(file) | u32(file) << 32; }
    private static long little(byte[] bytes, int offset, int size) {
        long value = 0; for (int i = 0; i < size; i++) value |= (long) (bytes[offset + i] & 255) << (8 * i); return value;
    }
}
