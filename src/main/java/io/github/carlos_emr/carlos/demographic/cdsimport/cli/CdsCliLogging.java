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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Console-safe one-shot logging; bounded protected bootstrap evidence never contains raw messages or credentials. */
final class CdsCliLogging {
    private CdsCliLogging(){ }

    static void configure() {
        var resource=CdsCliLogging.class.getResource("/log4j2-cds-cli.xml");
        if (resource==null) throw new IllegalStateException("CDS_CLI_LOGGING_CONFIGURATION_MISSING");
        System.setProperty("log4j2.configurationFile", resource.toExternalForm());
        System.setProperty("log4j2.statusLoggerLevel", "OFF");
        java.util.logging.LogManager.getLogManager().reset();
    }

    static String bootstrapFailure(Properties properties, Throwable failure) {
        try {
            String configured=properties.getProperty("cds.import.stagingRoot");
            if (configured==null) return null;
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
            List<Map<String, Object>> causes=new ArrayList<>();
            for (Throwable cursor=failure; cursor!=null && causes.size()<16; cursor=cursor.getCause()) {
                Map<String, Object> item=new LinkedHashMap<>(); item.put("type", cursor.getClass().getName());
                if (cursor instanceof org.springframework.beans.factory.BeanCreationException bean
                        && bean.getBeanName()!=null && bean.getBeanName().matches("[A-Za-z0-9_.$-]{1,256}")) item.put("bean", bean.getBeanName());
                if (cursor instanceof SQLException sql) {
                    if (sql.getSQLState()!=null && sql.getSQLState().matches("[A-Z0-9]{5}")) item.put("sqlState", sql.getSQLState());
                    item.put("vendorCode", sql.getErrorCode());
                }
                List<String> frames=new ArrayList<>();
                for (StackTraceElement frame:cursor.getStackTrace()) {
                    if (frames.size()==32) break;
                    frames.add(frame.getClassName()+"#"+frame.getMethodName()+":"+frame.getLineNumber());
                }
                item.put("frames", frames); causes.add(item);
            }
            byte[] bytes=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(Map.of(
                    "code", "CDS_BOOTSTRAP_FAILED", "clinicalAcceptance", "NOT_STARTED", "causes", causes));
            if (bytes.length>65536) return null;
            Path file=directory.resolve("cli-bootstrap-"+UUID.randomUUID()+".json");
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
