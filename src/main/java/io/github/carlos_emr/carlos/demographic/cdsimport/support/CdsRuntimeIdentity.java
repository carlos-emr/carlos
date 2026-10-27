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

import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportException;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.zone.ZoneRulesProvider;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeSet;

/** Actual semantic dependency/runtime bytes; no build paths, timestamps, patient files, or credentials. */
final class CdsRuntimeIdentity {
    private static final long JAR_LIMIT = 128L << 20;
    private static final long MODULE_LIMIT = 512L << 20;
    private static final String[] DEPENDENCIES = {
        "org/apache/xerces/jaxp/SAXParserFactoryImpl.class",
        "org/apache/xerces/jaxp/validation/XMLSchemaFactory.class",
        "com/fasterxml/jackson/core/JsonFactory.class",
        "com/fasterxml/jackson/databind/ObjectMapper.class",
        "com/fasterxml/jackson/annotation/JsonCreator.class",
        "com/fasterxml/jackson/datatype/jsr310/JavaTimeModule.class",
        "ca/uhn/hl7v2/parser/PipeParser.class",
        "ca/uhn/hl7v2/model/v23/message/ORU_R01.class",
        "org/hibernate/Session.class",
        "jakarta/persistence/EntityManager.class",
        "org/springframework/transaction/support/TransactionTemplate.class",
        "org/springframework/orm/jpa/JpaTransactionManager.class",
        "org/springframework/context/annotation/AnnotationConfigApplicationContext.class",
        "org/springframework/beans/factory/support/DefaultListableBeanFactory.class",
        "org/springframework/core/ResolvableType.class",
        "org/springframework/aop/framework/ProxyFactory.class",
        "org/springframework/expression/spel/standard/SpelExpressionParser.class",
        "com/mysql/cj/jdbc/Driver.class",
        "org/apache/commons/lang3/StringUtils.class",
        "org/apache/commons/io/FilenameUtils.class"
    };

    private CdsRuntimeIdentity() {}

    static void addTo(MessageDigest result, ClassLoader loader, byte[] buffer) throws IOException {
        if (buffer.length != 65536)
            throw new IllegalArgumentException("A single 64KiB fingerprint buffer is required");
        CdsImportKeys.add(result, "carlos-cds-runtime-v1");
        Map<Path, String> artifacts = new HashMap<>();
        for (String resource : DEPENDENCIES) {
            URL artifact;
            try {
                Class<?> type = Class.forName(resource.substring(0, resource.length() - 6).replace('/', '.'), false, loader);
                var source = type.getProtectionDomain().getCodeSource();
                artifact = source == null ? null : source.getLocation();
            } catch (ClassNotFoundException | LinkageError | SecurityException unavailable) {
                throw unavailable();
            }
            if (artifact == null || !artifact.getProtocol().equals("file")) throw unavailable();
            Path path;
            try {
                path = Path.of(artifact.toURI()).toRealPath();
            } catch (java.net.URISyntaxException invalid) {
                throw unavailable();
            }
            String hash = artifacts.get(path);
            if (hash == null) {
                hash = artifactHash(path, JAR_LIMIT, buffer);
                artifacts.put(path, hash);
            }
            CdsImportKeys.add(result, resource);
            CdsImportKeys.add(result, hash);
        }
        CdsImportKeys.add(result, "java.runtime");
        CdsImportKeys.add(result, Runtime.version().toString());
        for (String property :
                new String[] {"java.vendor", "java.vm.name", "java.vm.version", "java.vm.vendor"}) {
            String value = System.getProperty(property);
            if (value == null || value.length() > 1024) throw unavailable();
            CdsImportKeys.add(result, property);
            CdsImportKeys.add(result, value);
        }
        String javaHome = System.getProperty("java.home");
        if (javaHome == null) throw unavailable();
        CdsImportKeys.add(result, "java.runtime.module-image");
        CdsImportKeys.add(
                result,
                artifactHash(
                        Path.of(javaHome).resolve("lib/modules").toRealPath(),
                        MODULE_LIMIT,
                        buffer));
        CdsImportKeys.add(result, "java.default-locale");
        CdsImportKeys.add(result, Locale.getDefault().toLanguageTag());
        CdsImportKeys.add(result, "java.default-zone");
        CdsImportKeys.add(result, TimeZone.getDefault().getID());
        CdsImportKeys.add(result, "java.zone-rules");
        CdsImportKeys.add(result, zoneRulesHash());
    }

    static String artifactHash(Path path, long maximum, byte[] buffer) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > maximum)
            throw unavailable();
        MessageDigest digest = CdsImportKeys.digest();
        long bytes = 0;
        int read;
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            while ((read = input.read(buffer)) != -1) {
                bytes = Math.addExact(bytes, read);
                if (bytes > maximum) throw unavailable();
                digest.update(buffer, 0, read);
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    static String zoneRulesHash() throws IOException {
        TreeSet<String> zones = new TreeSet<>(ZoneRulesProvider.getAvailableZoneIds());
        if (zones.isEmpty() || zones.size() > 4096) throw unavailable();
        MessageDigest digest = CdsImportKeys.digest();
        var output = new BoundedRuleOutput(digest);
        try (var serialized = new ObjectOutputStream(output)) {
            for (String zone : zones) {
                if (zone.length() > 128) throw unavailable();
                var versions = ZoneRulesProvider.getVersions(zone);
                if (versions.isEmpty() || versions.size() > 16) throw unavailable();
                for (var version : versions.entrySet()) {
                    if (version.getKey().length() > 128) throw unavailable();
                    output.nextSegment();
                    serialized.writeUTF(zone);
                    serialized.writeUTF(version.getKey());
                    serialized.writeObject(version.getValue());
                    serialized.flush();
                    serialized.reset();
                    serialized.flush(); // Do not retain all zone object graphs.
                }
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static final class BoundedRuleOutput extends OutputStream {
        private final MessageDigest digest;
        private long total;
        private long segment;

        BoundedRuleOutput(MessageDigest digest) {
            this.digest = digest;
        }

        void nextSegment() {
            segment = 0;
        }

        @Override
        public void write(int value) {
            checked(1);
            digest.update((byte) value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            checked(length);
            digest.update(bytes, offset, length);
        }

        private void checked(int bytes) {
            total = Math.addExact(total, bytes);
            segment = Math.addExact(segment, bytes);
            if (total > (64L << 20) || segment > (1L << 20)) throw unavailable();
        }
    }

    private static CdsImportException unavailable() {
        return new CdsImportException("IMPORT_RULESET_RUNTIME_UNAVAILABLE");
    }
}
