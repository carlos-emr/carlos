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
package io.github.carlos_emr.carlos.demographic.cdsexport.cli;

import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportProtectedFiles;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Exclusive private recipient-ciphertext delivery; a failed write never installs a partial destination. */
public final class CdsExportCliOutput {
    @FunctionalInterface
    public interface Writer {
        void write(OutputStream output) throws IOException;
    }

    @FunctionalInterface
    interface DirectoryPolicy {
        Path verify(Path directory) throws IOException;
    }

    private final DirectoryPolicy directories;

    public CdsExportCliOutput() {
        this(CdsExportProtectedFiles::dataDirectory);
    }

    CdsExportCliOutput(DirectoryPolicy directories) {
        this.directories = Objects.requireNonNull(directories);
    }

    // Cleanup only throws without a primary failure; otherwise its error is suppressed on that
    // failure.
    @SuppressWarnings("java:S1143")
    public void writeNew(Path destination, Writer writer) throws IOException {
        Objects.requireNonNull(destination);
        Objects.requireNonNull(writer);
        if (!destination.isAbsolute()
                || !destination.normalize().equals(destination)
                || destination.getParent() == null
                || destination.getFileName() == null
                || destination.toString().codePoints().anyMatch(Character::isISOControl))
            throw new IOException("CDS_EXPORT_OUTPUT_PATH_INVALID");
        Path parent = directories.verify(destination.getParent());
        if (!parent.equals(destination.getParent())
                || Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("CDS_EXPORT_OUTPUT_EXISTS");
        Object identity =
                Files.readAttributes(
                                parent,
                                java.nio.file.attribute.BasicFileAttributes.class,
                                LinkOption.NOFOLLOW_LINKS)
                        .fileKey();
        Path pending = parent.resolve(".cds-export-" + UUID.randomUUID() + ".pending");
        boolean created = false;
        Throwable primary = null;
        try {
            try (var channel =
                    FileChannel.open(
                            pending,
                            Set.of(
                                    StandardOpenOption.CREATE_NEW,
                                    StandardOpenOption.WRITE,
                                    LinkOption.NOFOLLOW_LINKS),
                            PosixFilePermissions.asFileAttribute(
                                    PosixFilePermissions.fromString("rw-------")))) {
                created = true;
                writer.write(Channels.newOutputStream(channel));
                channel.force(true);
            }
            directories.verify(parent);
            if (identity == null
                    || !identity.equals(
                            Files.readAttributes(
                                            parent,
                                            java.nio.file.attribute.BasicFileAttributes.class,
                                            LinkOption.NOFOLLOW_LINKS)
                                    .fileKey()))
                throw new IOException("CDS_EXPORT_OUTPUT_DIRECTORY_CHANGED");
            // An exclusive link is atomic and cannot replace an existing destination; ATOMIC_MOVE
            // can replace on Unix.
            Files.createLink(destination, pending);
            try (var directory =
                    FileChannel.open(parent, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                directory.force(true);
            }
        } catch (IOException | RuntimeException | Error failed) {
            primary = failed;
            throw failed;
        } finally {
            if (created)
                try {
                    Files.delete(pending);
                    try (var directory =
                            FileChannel.open(
                                    parent, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                        directory.force(true);
                    }
                } catch (IOException | RuntimeException cleanup) {
                    if (primary != null) primary.addSuppressed(cleanup);
                    else throw cleanup;
                }
        }
    }
}
