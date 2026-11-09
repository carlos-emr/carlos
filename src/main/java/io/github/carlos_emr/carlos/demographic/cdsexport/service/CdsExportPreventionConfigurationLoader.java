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

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Trusted server setting only. Each call returns exact bounded bytes; source capture owns authorization, snapshot binding and cleanup. */
@Service
public class CdsExportPreventionConfigurationLoader {
    static final int MAX_XML = 1_048_576;
    private final String location;

    public CdsExportPreventionConfigurationLoader(
            @Value("${PREVENTION_ITEMS:classpath:oscar/prevention/PreventionItems.xml}")
                    String location) {
        this.location = Objects.requireNonNull(location);
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "PATH_TRAVERSAL_IN",
            justification =
                    "PREVENTION_ITEMS is trusted server configuration; no request path is accepted, and configured files are canonicalized and opened without following a leaf symlink")
    byte[] load() throws IOException {
        try {
            byte[] xml;
            if (location.startsWith("classpath:")) {
                String resource = location.substring(10);
                if (resource.isEmpty() || resource.startsWith("/") || resource.contains(".."))
                    invalid();
                try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                    if (input == null) invalid();
                    xml = input.readNBytes(MAX_XML + 1);
                }
            } else {
                var configured = Path.of(location);
                if (Files.isSymbolicLink(configured)) invalid();
                var path =
                        io.github.carlos_emr.carlos.utility.PathValidationUtils.resolveTrustedPath(
                                        configured.toFile())
                                .toPath();
                var before =
                        Files.readAttributes(
                                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!before.isRegularFile() || before.size() > MAX_XML) invalid();
                try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                    xml = input.readNBytes(MAX_XML + 1);
                }
                var after =
                        Files.readAttributes(
                                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!after.isRegularFile()
                        || before.size() != after.size()
                        || !before.lastModifiedTime().equals(after.lastModifiedTime())
                        || !Objects.equals(before.fileKey(), after.fileKey())) invalid();
            }
            if (xml.length < 1 || xml.length > MAX_XML) invalid();
            return xml;
        } catch (IOException | RuntimeException failed) {
            throw new IOException("CDS_EXPORT_PREVENTION_CONFIG_UNAVAILABLE");
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_PREVENTION_CONFIG_INVALID");
    }
}
