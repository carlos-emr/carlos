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
package io.github.carlos_emr.carlos.demographic.cdsimport.cli;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Production-only plain Properties loading. The capability probe never initializes Spring or CarlosProperties. */
public record CdsCliConfiguration(Path path, Properties properties, int heapMiB) {
    private static final Path HOST = Path.of("/etc/carlos-emr/carlos.properties");
    private static final Path CONTAINER = Path.of("/run/carlos-config/carlos.properties");
    private static final int MAXIMUM_BYTES = 1024 * 1024;

    public CdsCliConfiguration {
        Properties snapshot = new Properties();
        snapshot.putAll(properties);
        properties = snapshot;
    }

    public static CdsCliConfiguration load(Map<String, String> environment) throws IOException {
        String selected = environment.get("CARLOS_CDS_PROPERTIES");
        if (selected == null || (!selected.equals(HOST.toString()) && !selected.equals(CONTAINER.toString()))) {
            throw new IOException("CDS_PRODUCTION_CONFIGURATION_REQUIRED");
        }
        Path path = Path.of(selected);
        boolean mounted = path.equals(CONTAINER);
        if (mounted && !Files.getFileStore(path).isReadOnly()) throw new IOException("CDS_CONFIGURATION_MUST_BE_READ_ONLY");
        for (Path cursor = path; cursor != null; cursor = cursor.getParent()) {
            if (Files.isSymbolicLink(cursor)) throw new IOException("CDS_CONFIGURATION_LINK_REFUSED");
            int owner = (int) Files.getAttribute(cursor, "unix:uid", LinkOption.NOFOLLOW_LINKS);
            int mode = (int) Files.getAttribute(cursor, "unix:mode", LinkOption.NOFOLLOW_LINKS);
            boolean readonlyConfig = mounted && cursor.startsWith(CONTAINER.getParent());
            if ((owner != 0 && !(readonlyConfig && owner == 10001)) || (mode & 0022) != 0
                    || (cursor.equals(path) && (mode & 0137) != 0)) {
                throw new IOException("CDS_CONFIGURATION_PERMISSIONS_INVALID");
            }
        }
        return parse(path, readBounded(path, MAXIMUM_BYTES));
    }

    static CdsCliConfiguration parse(Path path, byte[] bytes) throws IOException {
        if (bytes.length > MAXIMUM_BYTES) throw new IOException("CDS_CONFIGURATION_CAPACITY_INVALID");
        Properties properties = new Properties();
        properties.load(new ByteArrayInputStream(bytes));
        int heap;
        try {
            String configured = properties.getProperty("cds.import.cliHeapMiB", "512").trim();
            if (!configured.matches("[0-9]{1,5}")) throw new NumberFormatException();
            heap = Integer.parseInt(configured);
            if (heap < 128 || heap > 16384) throw new NumberFormatException();
        } catch (NumberFormatException failure) { throw new IOException("CDS_HEAP_CONFIGURATION_INVALID"); }
        return new CdsCliConfiguration(path, properties, heap);
    }

    static byte[] readBounded(Path path, int limit) throws IOException {
        BasicFileAttributes before = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile() || before.size() > limit) throw new IOException("CDS_CONTROL_FILE_INVALID");
        byte[] data;
        try (var channel = Files.newByteChannel(path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
                var input = java.nio.channels.Channels.newInputStream(channel)) {
            data = input.readNBytes(limit + 1);
        }
        BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (data.length > limit || before.fileKey() == null || !before.fileKey().equals(after.fileKey())
                || before.size() != after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())) {
            throw new IOException("CDS_CONTROL_FILE_CHANGED");
        }
        return data;
    }

    @Override public String toString() { return "CdsCliConfiguration[protected]"; }
}
