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

import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsRulesetFingerprint;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.net.URL;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

/** Direction-specific identity of the actual application, source contracts and shared semantic runtime. No patient/configuration/key bytes are hashed. */
@Service
public class CdsExportImplementationIdentityService {
    private static final String BASE = "io/github/carlos_emr/carlos/";
    private static final String ANCHOR = BASE + "demographic/cdsexport/CdsExport.class";
    private static final long MAXIMUM_BYTES = 512L << 20;
    private final ClassLoader loader;
    private final Supplier<String> shared;
    private volatile String cached, sharedIdentity;
    public CdsExportImplementationIdentityService() { this(CdsExportImplementationIdentityService.class.getClassLoader(), () -> CdsRulesetFingerprint.current().sha256()); }
    CdsExportImplementationIdentityService(ClassLoader loader, Supplier<String> shared) { this.loader = Objects.requireNonNull(loader); this.shared = Objects.requireNonNull(shared); }
    public String current() {
        String runtime = shared.get(); if (runtime == null || !runtime.matches("[0-9a-f]{64}")) unavailable();
        synchronized (this) {
            if (cached != null) { if (!runtime.equals(sharedIdentity)) unavailable(); return cached; }
            String result = calculate(runtime); sharedIdentity = runtime; cached = result; return result;
        }
    }
    private String calculate(String runtime) {
        try {
            URL anchor = loader.getResource(ANCHOR); if (anchor == null) unavailable(); String address = anchor.toExternalForm(); int offset = address.lastIndexOf(ANCHOR);
            if (offset < 0 || !(address.startsWith("file:") || address.startsWith("jar:file:") || address.startsWith("war:file:") || address.startsWith("jar:war:file:"))) unavailable();
            String origin = address.substring(0, offset); var resolver = new PathMatchingResourcePatternResolver(loader); var entries = new TreeMap<String, String>();
            byte[] buffer = new byte[64 * 1024]; long[] total = {0};
            for (String pattern : List.of(BASE + "**/*.class", "cds-export/**/*.json", "db/migration/**/*.sql")) {
                var resources = resolver.getResources("classpath*:" + pattern); if (resources.length > 32_768) unavailable(); int included = 0;
                for (var resource : resources) {
                    var url = resource.getURL(); String text = url.toExternalForm(); if (!text.startsWith(origin)) continue;
                    String name = text.substring(origin.length()); URL selected = loader.getResource(name);
                    if (selected == null || !selected.toExternalForm().equals(text)) unavailable();
                    add(entries, name, url, buffer, total); included++;
                }
                if (included == 0) unavailable();
            }
            if (!entries.containsKey(ANCHOR)) unavailable();
            for (String name : List.of("cds-import/schema/EMR_Data_Migration_Schema.xsd", "cds-import/schema/EMR_Data_Migration_Schema_DT.xsd",
                    "spoken_languages_codes.properties", "oscar/prevention/PreventionItems.xml", BASE + "lab/ca/all/parsers/message_config.xml", "carlos-build.properties")) {
                URL resource = loader.getResource(name); if (resource == null || !resource.toExternalForm().equals(origin + name)) unavailable();
                add(entries, name, resource, buffer, total);
            }
            for (Class<?> dependency : List.of(org.bouncycastle.cms.CMSAuthEnvelopedDataParser.class,
                    org.bouncycastle.jce.provider.BouncyCastleProvider.class, org.bouncycastle.asn1.cms.CMSObjectIdentifiers.class)) {
                var source = dependency.getProtectionDomain().getCodeSource(); if (source == null) unavailable(); var url = source.getLocation();
                if (!"file".equals(url.getProtocol())) unavailable();
                var file = java.nio.file.Path.of(url.toURI());
                if (!java.nio.file.Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS) || java.nio.file.Files.size(file) > (128L << 20)) unavailable();
                add(entries, "dependency/" + dependency.getName(), url, buffer, total);
            }
            MessageDigest digest = CdsImportKeys.digest(); CdsImportKeys.add(digest, "CARLOS_CDS_EXPORT_IMPLEMENTATION_1"); CdsImportKeys.add(digest, runtime);
            for (var entry : entries.entrySet()) { CdsImportKeys.add(digest, entry.getKey()); CdsImportKeys.add(digest, entry.getValue()); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | java.net.URISyntaxException failed) { throw new IllegalStateException("CDS_EXPORT_IMPLEMENTATION_IDENTITY_UNAVAILABLE"); }
    }
    private static void add(Map<String, String> entries, String name, URL resource, byte[] buffer, long[] total) throws IOException {
        if (entries.size() >= 65_536 || name.length() > 4096) unavailable(); var digest = CdsImportKeys.digest();
        try (var input = resource.openStream()) {
            for (int n; (n = input.read(buffer)) != -1;) { if (n == 0) throw new IOException("CDS_EXPORT_IDENTITY_READ_STALLED"); total[0] = Math.addExact(total[0], n); if (total[0] > MAXIMUM_BYTES) unavailable(); digest.update(buffer, 0, n); }
        }
        String sha = HexFormat.of().formatHex(digest.digest()); var previous = entries.putIfAbsent(name, sha); if (previous != null && !previous.equals(sha)) unavailable();
    }
    private static void unavailable() { throw new IllegalStateException("CDS_EXPORT_IMPLEMENTATION_IDENTITY_UNAVAILABLE"); }
}
