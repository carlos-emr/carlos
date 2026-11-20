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
package io.github.carlos_emr.carlos.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A {@code <fmt:message>} key that was wrapped onto two lines no longer matches its bundle
 * entry, so the page shows {@code ???key???} instead of the label (the inbox Search button and
 * two lab-view eChart buttons did this).
 *
 * <p>This scans every JSP asset for literal message keys that contain whitespace.
 */
@DisplayName("JSP message keys without whitespace")
@Tag("unit")
class JspMessageKeyWhitespaceRegressionTest {

    /** Any attributes may come before {@code key}, e.g. {@code <fmt:message bundle="..." key="...">}. */
    private static final Pattern MESSAGE_KEY =
            Pattern.compile("<fmt:message\\b[^>]*?\\bkey\\s*=\\s*([\"'])([^\"']*)\\1");

    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    private static final Path WEBAPP_ROOT = resolveProjectPath(Path.of("src/main/webapp"));

    @Test
    @DisplayName("should not split a literal message key with whitespace in any JSP")
    void shouldNotContainWhitespace_inLiteralMessageKeys() throws Exception {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(WEBAPP_ROOT)) {
            for (Path path : (Iterable<Path>) files.filter(Files::isRegularFile)
                    .filter(JspMessageKeyWhitespaceRegressionTest::isJspAsset)::iterator) {
                String text = Files.readString(path);
                Matcher m = MESSAGE_KEY.matcher(text);
                while (m.find()) {
                    String key = m.group(2);
                    // Keys built from EL or a scriptlet are not literal keys.
                    if (key.contains("${") || key.contains("<%")) {
                        continue;
                    }
                    // Leading or trailing spaces break the lookup too, so any whitespace counts.
                    if (WHITESPACE.matcher(key).find()) {
                        int line = 1 + (int) text.substring(0, m.start()).chars().filter(c -> c == '\n').count();
                        offenders.add(WEBAPP_ROOT.relativize(path) + ":" + line);
                    }
                }
            }
        }
        assertThat(offenders)
                .as("join these message keys onto one line")
                .isEmpty();
    }

    private static boolean isJspAsset(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".jsp") || name.endsWith(".jspf") || name.endsWith(".tag");
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty("basedir", System.getProperty("user.dir")))
                .toAbsolutePath().normalize();
        for (int checked = 0; current != null && checked < 6; checked++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate " + relativePath);
    }
}
