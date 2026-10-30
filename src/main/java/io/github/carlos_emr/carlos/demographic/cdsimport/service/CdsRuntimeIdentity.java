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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.utility.CdsImportKeys;

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsCalendarZone;

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

/** Actual semantic dependency/runtime bytes; no filesystem locations, patient files, or credentials enter the digest. */
final class CdsRuntimeIdentity {
    private static final long JAR_LIMIT = 128L << 20;
    private static final long MODULE_LIMIT = 512L << 20;

    private CdsRuntimeIdentity() {}

    /** Literal compile-time dependencies only: no source-controlled reflection or class instantiation. */
    private static Class<?>[] dependencies() {
        return new Class<?>[] {
            org.apache.xerces.jaxp.SAXParserFactoryImpl.class,
            org.apache.xerces.jaxp.validation.XMLSchemaFactory.class,
            org.jdom2.input.SAXBuilder.class,
            com.fasterxml.jackson.core.JsonFactory.class,
            com.fasterxml.jackson.databind.ObjectMapper.class,
            com.fasterxml.jackson.annotation.JsonCreator.class,
            com.fasterxml.jackson.datatype.jsr310.JavaTimeModule.class,
            ca.uhn.hl7v2.parser.PipeParser.class,
            ca.uhn.hl7v2.model.v23.message.ORU_R01.class,
            org.hibernate.Session.class,
            jakarta.persistence.EntityManager.class,
            org.springframework.transaction.support.TransactionTemplate.class,
            org.springframework.orm.jpa.JpaTransactionManager.class,
            org.springframework.context.annotation.AnnotationConfigApplicationContext.class,
            org.springframework.beans.factory.support.DefaultListableBeanFactory.class,
            org.springframework.core.ResolvableType.class,
            org.springframework.aop.framework.ProxyFactory.class,
            org.springframework.expression.spel.standard.SpelExpressionParser.class,
            org.springframework.jdbc.datasource.DataSourceUtils.class,
            net.sf.jsqlparser.parser.CCJSqlParserUtil.class,
            org.apache.commons.dbcp2.BasicDataSource.class,
            org.apache.commons.pool2.impl.GenericObjectPool.class,
            com.mysql.cj.jdbc.Driver.class,
            org.apache.commons.lang3.StringUtils.class,
            org.apache.commons.io.FilenameUtils.class,
            com.google.common.base.Ascii.class,
            com.google.common.base.CharMatcher.class
        };
    }

    static FrozenRuntime addTo(MessageDigest result, ClassLoader loader, byte[] buffer)
            throws IOException {
        if (buffer.length != 65536)
            throw new IllegalArgumentException("A single 64KiB fingerprint buffer is required");
        CdsImportKeys.add(result, "carlos-cds-runtime-v1");
        Map<Path, String> artifacts = new HashMap<>();
        try {
            for (Class<?> type : dependencies()) {
                String resource = type.getName().replace('.', '/') + ".class";
                if (loader.getResource(resource) == null) throw unavailable();
                var source = type.getProtectionDomain().getCodeSource();
                URL artifact = source == null ? null : source.getLocation();
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
        } catch (LinkageError | SecurityException missingImplementation) {
            throw unavailable();
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
        FrozenRuntime frozen = snapshot();
        CdsImportKeys.add(result, "java.default-locale");
        CdsImportKeys.add(result, frozen.locale.toLanguageTag());
        CdsImportKeys.add(result, "java.format-locale");
        CdsImportKeys.add(result, frozen.formatLocale.toLanguageTag());
        CdsImportKeys.add(result, "java.display-locale");
        CdsImportKeys.add(result, frozen.displayLocale.toLanguageTag());
        CdsImportKeys.add(result, "java.default-zone");
        CdsImportKeys.add(result, frozen.zone.getID());
        CdsImportKeys.add(result, "java.default-calendar-rules");
        CdsImportKeys.add(result, CdsCalendarZone.evidence(frozen.zone));
        CdsImportKeys.add(result, "java.zone-rules");
        CdsImportKeys.add(result, frozen.rulesHash);
        frozen.verify();
        return frozen;
    }

    static String artifactHash(Path path, long maximum, byte[] buffer) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw unavailable();
        long expected = Files.size(path);
        if (expected > maximum) throw unavailable();
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
        if (bytes != expected || Files.size(path) != expected) throw unavailable();
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    static FrozenRuntime snapshot() throws IOException {
        Locale locale = Locale.getDefault();
        TimeZone zone = TimeZone.getDefault();
        CdsCalendarZone.requireTrusted(zone);
        Locale formatLocale = Locale.getDefault(Locale.Category.FORMAT);
        Locale displayLocale = Locale.getDefault(Locale.Category.DISPLAY);
        FrozenRuntime result =
                new FrozenRuntime(locale, formatLocale, displayLocale, zone, zoneRulesHash());
        result.verifyDefaults();
        return result;
    }

    static final class FrozenRuntime {
        private final Locale locale;
        private final Locale formatLocale;
        private final Locale displayLocale;
        private final TimeZone zone;
        private final String rulesHash;
        private volatile long nextProbe = System.nanoTime() + 1_000_000_000L;

        FrozenRuntime(
                Locale locale,
                Locale formatLocale,
                Locale displayLocale,
                TimeZone zone,
                String rulesHash) {
            this.locale = locale;
            this.formatLocale = formatLocale;
            this.displayLocale = displayLocale;
            this.zone = zone;
            this.rulesHash = rulesHash;
        }

        void verifyDefaults() {
            CdsCalendarZone.requireTrusted(TimeZone.getDefault());
            if (!locale.equals(Locale.getDefault())
                    || !formatLocale.equals(Locale.getDefault(Locale.Category.FORMAT))
                    || !displayLocale.equals(Locale.getDefault(Locale.Category.DISPLAY))
                    || !zone.equals(TimeZone.getDefault()))
                throw new CdsImportException("IMPORT_FROZEN_RUNTIME_CHANGED");
        }

        void verify() {
            verifyDefaults();
            long now = System.nanoTime();
            if (now < nextProbe) return;
            synchronized (this) {
                if (System.nanoTime() < nextProbe) return;
                try {
                    if (!rulesHash.equals(zoneRulesHash()))
                        throw new CdsImportException("IMPORT_FROZEN_RUNTIME_CHANGED");
                } catch (IOException unavailable) {
                    throw unavailable();
                }
                nextProbe = System.nanoTime() + 1_000_000_000L;
            }
        }
    }

    static String zoneRulesHash() throws IOException {
        var available = ZoneRulesProvider.getAvailableZoneIds();
        if (available.isEmpty() || available.size() > 4096) throw unavailable();
        TreeSet<String> zones = new TreeSet<>(available);
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
                    serialized.flush();
                }
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static final class BoundedRuleOutput extends OutputStream {
        // Owned solely by zoneRulesHash's calling thread and its local ObjectOutputStream.
        @SuppressWarnings("java:S9428")
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
