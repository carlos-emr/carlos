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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inline handlers built as one scriptlet statement (issue #3941).
 *
 * <p>Passing a whole statement through the JavaScript-string encoder escapes the statement's own quotes
 * ({@code '} becomes {@code \x27}), which is not valid JavaScript outside a string, so the handler never runs. The
 * statement's values are encoded where it is built, so the handler attribute takes HTML-attribute encoding.
 *
 * @since 2026-10-18
 */
@DisplayName("Inline handler encoding in JSPs")
@Tag("unit")
class InlineHandlerEncodingJspRegressionTest {
    private static final String BASEDIR_PROPERTY = "basedir";
    private static final Path JSP_ROOT = resolveProjectPath(Path.of("src/main/webapp/WEB-INF/jsp"));
    private static final Pattern WHOLE_HANDLER_AS_JAVASCRIPT_STRING = Pattern.compile(
            "\\son[a-zA-Z]+=\"<carlos:encode value='<%=\\s*[A-Za-z_][A-Za-z0-9_]*\\s*%>' context=\"javaScriptAttribute\"/>\"");

    @Test
    void shouldEncodeWholeInlineHandlers_asHtmlAttributes() throws Exception {
        assertThat(readJsp("documentManager/showDocument.jsp"))
                .contains("onsubmit=\"<carlos:encode value='<%= ackFunc %>' context=\"htmlAttribute\"/>\"")
                .contains("ackFunc = \"getDocComment('\" + SafeEncode.forJavaScript(docId) + \"','\" + SafeEncode.forJavaScript(providerNo)")
                .contains("ackFunc = \"updateStatus('acknowledgeForm_\" + SafeEncode.forJavaScript(docId)");
        assertThat(readJsp("casemgmt/ChartNotesAjax.jsp"))
                .contains("onclick=\"<carlos:encode value='<%= url %>' context=\"htmlAttribute\"/>\"")
                .contains("SafeEncode.forUriComponent(formEntry.getNote())");
    }

    @Test
    void shouldNotEncodeWholeInlineHandlers_asJavaScriptStrings() throws Exception {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(JSP_ROOT)) {
            offenders = files.filter(file -> file.toString().endsWith(".jsp") || file.toString().endsWith(".jspf"))
                    .filter(file -> WHOLE_HANDLER_AS_JAVASCRIPT_STRING.matcher(read(file)).find())
                    .map(file -> JSP_ROOT.relativize(file).toString())
                    .sorted()
                    .toList();
        }
        assertThat(offenders).as("JSPs with an inline handler encoded whole as a JavaScript string").isEmpty();
    }

    private static String readJsp(String relativePath) throws IOException {
        return Files.readString(JSP_ROOT.resolve(relativePath));
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")))
                .toAbsolutePath()
                .normalize();
        for (int checkedParents = 0; current != null && checkedParents < 6; checkedParents++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate) || Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate " + relativePath + " from "
                + System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")));
    }
}
