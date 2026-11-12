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
package io.github.carlos_emr.carlos.demographic.cdsexport.support;

import com.sun.security.auth.module.UnixSystem;
import io.github.carlos_emr.carlos.demographic.cds.cli.CdsProcessConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** POSIX source-policy/key and owned-data boundaries; no request-supplied path receives authority. */
public final class CdsExportProtectedFiles {
    private CdsExportProtectedFiles() {}

    public static Path absolute(String configured) throws IOException {
        try {
            Path selected = Path.of(configured);
            if (configured.isEmpty()
                    || !selected.isAbsolute()
                    || !selected.equals(selected.normalize())
                    || configured.length() > 8192
                    || configured.codePoints().anyMatch(Character::isISOControl)) invalid();
            return selected;
        } catch (RuntimeException invalid) {
            throw new IOException("CDS_EXPORT_STORAGE_CONFIGURATION_INVALID");
        }
    }

    /** Root-owned, private regular file; service-owned read-only secret mounts retain the same production boundary. */
    public static byte[] readRootFile(Path path, int maximumBytes) throws IOException {
        try {
            path = absolute(path.toString());
            var identity = new UnixSystem();
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) invalid();
            for (Path cursor = path; cursor != null; cursor = cursor.getParent()) {
                if (Files.isSymbolicLink(cursor)) invalid();
                long uid = number(cursor, "unix:uid");
                int mode = (int) number(cursor, "unix:mode");
                if (!(uid == 0
                                || uid == identity.getUid()
                                        && Files.getFileStore(cursor).isReadOnly())
                        || (mode & 0022) != 0) invalid();
                if (cursor.equals(path)) {
                    if ((mode & 07137) != 0
                            || (mode & 0040) != 0
                                    && number(cursor, "unix:gid") != identity.getGid()) invalid();
                } else if (!Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS)) invalid();
            }
            return CdsProcessConfiguration.readBounded(path, maximumBytes);
        } catch (IOException | RuntimeException invalid) {
            throw new IOException("CDS_EXPORT_PROTECTED_FILE_INVALID");
        }
    }

    /** A private service-owned directory, with no symlink or writable third-party ancestor. Does not create a root. */
    public static Path dataDirectory(Path path) throws IOException {
        try {
            path = absolute(path.toString());
            long process = new UnixSystem().getUid();
            for (Path cursor = path; cursor != null; cursor = cursor.getParent()) {
                if (Files.isSymbolicLink(cursor)
                        || !Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS)) invalid();
                long uid = number(cursor, "unix:uid");
                int mode = (int) number(cursor, "unix:mode");
                if (!(uid == 0 || uid == process) || (mode & 0022) != 0) invalid();
                if (cursor.equals(path) && (uid != process || (mode & 07777) != 0700)) invalid();
            }
            return path;
        } catch (IOException | RuntimeException invalid) {
            throw new IOException("CDS_EXPORT_PRIVATE_DIRECTORY_INVALID");
        }
    }

    private static long number(Path path, String attribute) throws IOException {
        return ((Number) Files.getAttribute(path, attribute, LinkOption.NOFOLLOW_LINKS))
                .longValue();
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_STORAGE_CONFIGURATION_INVALID");
    }
}
