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
 * Click actions that carry encoded data must live in {@code onclick} handlers, not in
 * {@code href="javascript:..."} links. A {@code javascript:} URL is URL-decoded by the
 * browser before it runs, so the data is not handled the same way as in an event handler.
 *
 * <p>This scans every JSP asset for {@code href="javascript:..."} values that also contain an
 * encoder call or encoded-value tag and fails if any are found.
 */
@DisplayName("JSP javascript: links with encoded data")
@Tag("unit")
class JspJavascriptHrefRegressionTest {
    private static final Pattern HREF_JAVASCRIPT =
            Pattern.compile("href\\s*=\\s*([\"'])\\s*javascript:", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENCODED_DATA = Pattern.compile(
            "context\\s*=\\s*[\"'](?:uriComponent|javaScriptAttribute|javaScript)[\"']"
                    + "|forUriComponent|forJavaScript|URLEncoder|SafeEncode\\.|Encode\\.for");
    /** Form pages whose only encoded value is an int parsed from the request (digits only). */
    private static final Pattern DIGITS_ONLY_FILES = Pattern.compile("form/formrourke[p]?\\d\\.jsp$");
    private static final Path WEBAPP_ROOT = resolveProjectPath(Path.of("src/main/webapp"));

    @Test
    @DisplayName("should not combine href javascript: with encoded data in any JSP")
    void shouldNotUseEncodedData_inJavascriptHref() throws Exception {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(WEBAPP_ROOT)) {
            for (Path path : (Iterable<Path>) files.filter(Files::isRegularFile)
                    .filter(JspJavascriptHrefRegressionTest::isJspAsset)::iterator) {
                if (DIGITS_ONLY_FILES.matcher(path.toString().replace('\\', '/')).find()) {
                    continue;
                }
                String text = Files.readString(path);
                Matcher m = HREF_JAVASCRIPT.matcher(text);
                while (m.find()) {
                    char quote = m.group(1).charAt(0);
                    int end = endOfAttribute(text, m.end(), quote);
                    if (ENCODED_DATA.matcher(text.substring(m.end(), end)).find()) {
                        int line = 1 + (int) text.substring(0, m.start()).chars().filter(c -> c == '\n').count();
                        offenders.add(WEBAPP_ROOT.relativize(path) + ":" + line);
                    }
                }
            }
        }
        assertThat(offenders)
                .as("move these to href=\"#\" onclick=\"...; return false;\"")
                .isEmpty();
    }

    /** Finds the closing quote, ignoring quotes inside JSP scriptlets, EL and custom-tag elements. */
    private static int endOfAttribute(String t, int from, char quote) {
        int i = from;
        while (i < t.length()) {
            int skipped = skipJspConstruct(t, i);
            if (skipped > i) {
                i = skipped;
            } else if (t.charAt(i) == quote) {
                return i;
            } else {
                i++;
            }
        }
        return t.length();
    }

    private static int skipJspConstruct(String t, int i) {
        if (t.startsWith("<%", i)) {
            int e = t.indexOf("%>", i);
            return e < 0 ? t.length() : e + 2;
        }
        if (t.startsWith("${", i)) {
            int e = t.indexOf('}', i);
            return e < 0 ? t.length() : e + 1;
        }
        if (t.startsWith("<carlos:", i) || t.startsWith("<c:", i) || t.startsWith("<fmt:", i)) {
            int j = i;
            while (j < t.length()) {
                if (t.startsWith("<%", j)) {
                    int e = t.indexOf("%>", j);
                    j = e < 0 ? t.length() : e + 2;
                } else if (t.startsWith("/>", j)) {
                    return j + 2;
                } else {
                    j++;
                }
            }
            return t.length();
        }
        return i;
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
