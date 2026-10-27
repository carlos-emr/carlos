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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Console-safe one-shot logging; bounded technical evidence excludes messages, source values and credentials. */
final class CdsCliLogging {
    private CdsCliLogging(){ }

    /** A storage failure retains the identical safe projection for console delivery. */
    record OperationFailure(String protectedPath, String technicalJson) { }

    static void configure() {
        var resource=CdsCliLogging.class.getResource("/log4j2-cds-cli.xml");
        if (resource==null) throw new IllegalStateException("CDS_CLI_LOGGING_CONFIGURATION_MISSING");
        System.setProperty("log4j2.configurationFile", resource.toExternalForm());
        System.setProperty("log4j2.statusLoggerLevel", "OFF");
        java.util.logging.LogManager.getLogManager().reset();
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
        String code = bootstrap ? "CDS_BOOTSTRAP_FAILED" : "CDS_OPERATION_FAILED";
        String acceptance = bootstrap ? "NOT_STARTED" : "RECONCILE_DURABLE_STATE";
        try {
            List<Map<String, Object>> causes = new ArrayList<>();
            int remainingFrames = 32;
            for (Throwable cursor = failure; cursor != null && causes.size() < 16; cursor = cursor.getCause()) {
                Map<String, Object> item = new LinkedHashMap<>();
                String type = cursor.getClass().getName();
                item.put("type", typeName(type) ? type : "java.lang.Throwable");
                if (bootstrap && cursor instanceof org.springframework.beans.factory.BeanCreationException bean) {
                    String name = bean.getBeanName();
                    if (name != null && name.length() <= 256 && name.matches("[A-Za-z_$][A-Za-z0-9_.$-]*")) item.put("bean", name);
                }
                if (cursor instanceof SQLException sql) {
                    String state = sql.getSQLState();
                    if (state != null && state.matches("[A-Z0-9]{5}")) item.put("sqlState", state);
                    item.put("vendorCode", sql.getErrorCode());
                }
                List<String> frames = new ArrayList<>();
                if (remainingFrames > 0) {
                    for (StackTraceElement frame : cursor.getStackTrace()) {
                        if (remainingFrames == 0) break;
                        remainingFrames--;
                        String owner = frame.getClassName(), method = frame.getMethodName();
                        if (!typeName(owner) || method.length() > 128
                                || !(method.matches("[A-Za-z_$][A-Za-z0-9_$]*") || method.equals("<init>") || method.equals("<clinit>"))) continue;
                        frames.add(owner + "#" + method + ":" + frame.getLineNumber());
                    }
                }
                item.put("frames", frames);
                causes.add(item);
            }
            byte[] bytes = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(Map.of(
                    "code", code, "clinicalAcceptance", acceptance, "causes", causes));
            if (bytes.length <= 65536) return bytes;
        } catch (Exception unavailable) {
            // The primary failure must survive even a hostile Throwable or a
            // failed serializer. No raw fallback text is ever inspected.
        }
        return ("{\"code\":\"" + code + "\",\"clinicalAcceptance\":\"" + acceptance + "\",\"causes\":[]}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static boolean typeName(String value) {
        return value != null && value.length() <= 256
                && value.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
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
