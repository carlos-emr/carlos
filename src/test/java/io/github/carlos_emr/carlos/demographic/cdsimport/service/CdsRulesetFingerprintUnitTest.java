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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * The deployed implementation's fingerprint hashes every class and resource it names, whatever
 * their number or size: CARLOS growing never stops every import and export.
 */
@Tag("unit")
class CdsRulesetFingerprintUnitTest {
    private static final String BASE = "io/github/carlos_emr/carlos/";
    private static final String ANCHOR =
            BASE + "demographic/cdsimport/service/CdsImportJobService.class";
    /** The resources the fingerprint reads by name rather than through its opener. */
    private static final String[] NAMED = {
        "cds-import/schema/EMR_Data_Migration_Schema.xsd",
        "cds-import/schema/EMR_Data_Migration_Schema_DT.xsd",
        "spoken_languages_codes.properties",
        "oscar/prevention/PreventionItems.xml",
        "META-INF/services/org.mariadb.jdbc.plugin.TlsSocketPlugin",
        BASE + "lab/ca/all/parsers/message_config.xml"
    };

    @TempDir Path temp;

    /** A deployment that answers from its own directory first, then from the deployed loader. */
    private static final class Deployment extends URLClassLoader {
        Deployment(Path root, ClassLoader parent) throws IOException {
            super(new URL[] {root.toUri().toURL()}, parent);
        }

        @Override
        public URL getResource(String name) {
            URL own = findResource(name);
            return own != null ? own : super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            var all = new ArrayList<>(Collections.list(findResources(name)));
            all.addAll(Collections.list(getParent().getResources(name)));
            return Collections.enumeration(all);
        }
    }

    @Test
    @ResourceLock("java.util.TimeZone")
    @ResourceLock("java.util.Locale")
    void shouldHashEveryClass_whenADeploymentHasMoreThanTheFormerCounts() throws Exception {
        // At most 4,096 classes a pattern, 256 nested classes of one class and 8,192 classes in
        // all were once hashed. A synthetic deployment holds every file the deployed fingerprint
        // reads, 8,000 more import classes and 257 nested classes of one named class.
        ClassLoader deployed = CdsImportJobService.class.getClassLoader();
        var read = new ArrayList<URL>();
        var original =
                CdsRulesetFingerprint.forClassLoader(
                        deployed,
                        url -> {
                            read.add(url);
                            return url.openStream();
                        });
        String anchor = deployed.getResource(ANCHOR).toExternalForm();
        Path origin = Path.of(java.net.URI.create(anchor.substring(0, anchor.lastIndexOf(ANCHOR))));
        Path root = temp.resolve("deployment");
        for (URL url : read) copy(url, root.resolve(origin.relativize(Path.of(url.toURI())).toString()));
        for (String name : NAMED) copy(deployed.getResource(name), root.resolve(name));
        for (int index = 0; index < 8_000; index++)
            write(root.resolve(BASE + "demographic/cdsimport/generated/G" + index + ".class"), "G" + index);
        for (int index = 0; index < 257; index++)
            write(root.resolve(BASE + "commn/model/Drug$Synthetic" + index + ".class"), "N" + index);
        var generated = new AtomicInteger();
        var nested = new AtomicInteger();
        try (var deployment = new Deployment(root, deployed)) {
            String rootUrl = root.toUri().toURL().toExternalForm();
            var fingerprint =
                    CdsRulesetFingerprint.forClassLoader(
                            deployment,
                            url -> {
                                String text = url.toExternalForm();
                                if (text.startsWith(rootUrl) && text.contains("/generated/G"))
                                    generated.incrementAndGet();
                                if (text.startsWith(rootUrl) && text.contains("$Synthetic"))
                                    nested.incrementAndGet();
                                return url.openStream();
                            });
            assertThat(fingerprint.sha256()).matches("[0-9a-f]{64}").isNotEqualTo(original.sha256());
        }
        assertThat(generated).hasValue(8_000);
        assertThat(nested).hasValue(257);
    }

    @Test
    @ResourceLock("java.util.TimeZone")
    @ResourceLock("java.util.Locale")
    void shouldHashAClass_whenItIsLargerThan16MiB() {
        // A resource over 16 MiB once stopped every import and export. The class is replaced by
        // a generated stream of 16 MiB and one byte, never stored.
        ClassLoader deployed = CdsImportJobService.class.getClassLoader();
        var original = CdsRulesetFingerprint.forClassLoader(deployed, URL::openStream);
        var sent = new AtomicLong();
        var larger =
                CdsRulesetFingerprint.forClassLoader(
                        deployed,
                        url ->
                                url.toExternalForm().endsWith("/" + ANCHOR)
                                        ? generated((16L << 20) + 1, sent)
                                        : url.openStream());
        assertThat(sent).hasValue((16L << 20) + 1);
        assertThat(larger.sha256()).matches("[0-9a-f]{64}").isNotEqualTo(original.sha256());
    }

    private static InputStream generated(long length, AtomicLong sent) {
        return new InputStream() {
            private long left = length;

            @Override
            public int read() {
                if (left == 0) return -1;
                left--;
                sent.incrementAndGet();
                return 7;
            }

            @Override
            public int read(byte[] bytes, int offset, int count) {
                if (left == 0) return -1;
                int n = (int) Math.min(count, left);
                java.util.Arrays.fill(bytes, offset, offset + n, (byte) 7);
                left -= n;
                sent.addAndGet(n);
                return n;
            }
        };
    }

    private static void copy(URL from, Path to) throws IOException {
        Files.createDirectories(to.getParent());
        try (var input = from.openStream()) {
            Files.copy(input, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void write(Path to, String text) throws IOException {
        Files.createDirectories(to.getParent());
        Files.writeString(to, text, StandardCharsets.UTF_8);
    }
}
