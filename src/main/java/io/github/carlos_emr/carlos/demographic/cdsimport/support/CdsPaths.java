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

import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/** Private import-storage primitives. Paths are protected context and never logged here. */
public final class CdsPaths {
    private CdsPaths() { }

    public static Path within(Path path, Path root) {
        return PathValidationUtils.validatePrivateImportPath(path.toFile(), root.toFile()).toPath();
    }

    public static Path privateDirectory(Path path, Path root) throws IOException {
        Path validated = within(path, root);
        if (Files.exists(validated, LinkOption.NOFOLLOW_LINKS)) {
            throw failure("CDS_STORAGE_ALREADY_EXISTS");
        }
        Files.createDirectory(validated, PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rwx------")));
        return validated;
    }

    public static OutputStream newPrivateFile(Path path, Path root) throws IOException {
        Path validated = within(path, root);
        return Channels.newOutputStream(Files.newByteChannel(validated,
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))));
    }

    public static void forceFile(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            channel.force(true);
        }
    }

    public static void forceDirectory(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            channel.force(true);
        }
    }

    public static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    public static String hex(byte[] value) { return HexFormat.of().formatHex(value); }

    public static CdsInputException failure(String code) { return new CdsInputException(code, "", ""); }
}
