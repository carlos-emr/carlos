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

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import com.sun.security.auth.module.UnixSystem;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;

/** Console-safe one-shot logging; bounded technical evidence excludes messages, source values and credentials. */
final class CdsCliLogging {
    private CdsCliLogging(){ }

    /** A storage failure retains the identical safe projection for console delivery. */
    record OperationFailure(String protectedPath, String technicalJson) { }

    static void configure() {
        io.github.carlos_emr.carlos.demographic.cds.cli.CdsProcessLogging.configure();
    }

    static String bootstrapFailure(Properties properties, Throwable failure) {
        return writeEvidence(properties, "cli-bootstrap-", projection(failure, true));
    }

    /** Retains technical evidence without inferring whether acceptance or clinical writes committed. */
    static OperationFailure operationFailure(Properties properties, Throwable failure) {
        byte[] bytes = projection(failure, false);
        String location = writeEvidence(properties, "cli-operation-", bytes);
        return new OperationFailure(location, location == null ? new String(bytes, StandardCharsets.UTF_8) : null);
    }

    private static byte[] projection(Throwable failure, boolean bootstrap) {
        return io.github.carlos_emr.carlos.demographic.cds.cli.CdsProcessLogging.failureSummary(failure, bootstrap);
    }

    private static String writeEvidence(Properties properties, String prefix, byte[] bytes) {
        try {
            String configured=properties.getProperty("cds.import.stagingRoot");
            if (configured==null || configured.length() > 4096 || configured.chars().anyMatch(Character::isISOControl)) return null;
            Path root=Path.of(configured);
            if (!root.isAbsolute()) return null;
            for (Path cursor=root; cursor!=null; cursor=cursor.getParent()) if (Files.isSymbolicLink(cursor)) return null;
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) CdsPaths.privateDirectory(root, root.getParent());
            requirePrivate(root);
            Path directory=root.resolve("cli-diagnostics");
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                try {CdsPaths.privateDirectory(directory, root); }
                catch (java.nio.file.FileAlreadyExistsException concurrent){ /* Validate the shared private directory below. */ }
            }
            requirePrivate(directory);
            if (bytes.length>65536) return null;
            Path file=directory.resolve(prefix+UUID.randomUUID()+".json");
            try (OutputStream output=CdsPaths.newPrivateFile(file, directory)){output.write(bytes); }
            CdsPaths.forceFile(file); CdsPaths.forceDirectory(directory); return file.toString();
        } catch (Exception unavailable){return null; }
    }

    private static void requirePrivate(Path path) throws Exception {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)
                || (int) Files.getAttribute(path, "unix:uid", LinkOption.NOFOLLOW_LINKS)!=new UnixSystem().getUid()
                || (((int) Files.getAttribute(path, "unix:mode", LinkOption.NOFOLLOW_LINKS))&0077)!=0) {
            throw new IllegalStateException("CDS_PRIVATE_DIAGNOSTIC_STORAGE_REQUIRED");
        }
    }
}
