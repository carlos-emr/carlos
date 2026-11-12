/**
 * Copyright (c) 2026 CARLOS Contributors
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * CARLOS EMR
 */
package io.github.carlos_emr.carlos.casemgmt.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.utility.SafeEncode;

/**
 * Pins the output encoding of the note and issue history popup.
 *
 * <p>{@code showHistory.jsp} printed its two headings as raw EL. The patient heading is the
 * demographic's first and last name and the issue-history title is built from the issue
 * catalogue's descriptions, so either could carry markup that ran in the clinician's session when
 * a note's revision history was opened. Every value the page writes must go through the null-safe
 * CARLOS encoder for its context; the only exemption is the container-supplied context path.
 *
 * @since 2026-11-12
 */
@Tag("unit")
@Tag("security")
@DisplayName("Note and issue history popup output encoding")
class ShowHistoryJspEncodingRegressionTest {

    private static final Path SHOW_HISTORY_JSP =
            resolveProjectPath(Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/showHistory.jsp"));

    /** JSP comments and scriptlets hold no template output; tags are inspected separately. */
    private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
    /** A JSTL/custom tag such as {@code <c:if test="${...}">}: its EL is evaluated, not written. */
    private static final Pattern CUSTOM_TAG = Pattern.compile(
            "</?[a-zA-Z]+:[a-zA-Z]+\\b(?:[^>\"']|\"[^\"]*\"|'[^']*')*>", Pattern.DOTALL);
    private static final Pattern EL = Pattern.compile("\\$\\{([^}]*)}");
    private static final Pattern EXPRESSION_SCRIPTLET = Pattern.compile("<%=(.*?)%>", Pattern.DOTALL);

    @Test
    @DisplayName("should encode the title and patient name headings")
    void shouldEncodeHeadings_forTitleAndPatientName() throws IOException {
        String jsp = Files.readString(SHOW_HISTORY_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("${carlos:forHtmlContent(title)}")
                .contains("${carlos:forHtmlContent(demoName)}")
                .doesNotContain(">${title}<")
                .doesNotContain(">${demoName}<");
    }

    @Test
    @DisplayName("should write every EL value through a CARLOS encoder")
    void shouldEncodeEveryWrittenElValue_inTemplateText() throws IOException {
        String template = CUSTOM_TAG.matcher(withoutComments()).replaceAll("");

        List<String> raw = new ArrayList<>();
        Matcher expression = EL.matcher(template);
        while (expression.find()) {
            String body = expression.group(1).trim();
            // The context path is supplied by the container, not by stored or request data.
            if (!body.startsWith("carlos:for") && !"pageContext.request.contextPath".equals(body)) {
                raw.add(expression.group());
            }
        }

        assertThat(raw).as("EL written to the page without an encoder").isEmpty();
    }

    @Test
    @DisplayName("should not write a scriptlet expression other than the context path")
    void shouldWriteOnlyContextPath_fromScriptletExpressions() throws IOException {
        // Tag attributes such as roleName="<%=roleName$%>" are evaluated by the tag, never written.
        String template = CUSTOM_TAG.matcher(withoutComments()).replaceAll("");

        List<String> written = new ArrayList<>();
        Matcher expression = EXPRESSION_SCRIPTLET.matcher(template);
        while (expression.find()) {
            String body = expression.group(1).trim();
            if (!"request.getContextPath()".equals(body) && !body.startsWith("SafeEncode.for")) {
                written.add(expression.group());
            }
        }

        assertThat(written).as("scriptlet expressions written without an encoder").isEmpty();
    }

    @Test
    @DisplayName("should render stored markup in a heading as text")
    void shouldRenderMarkupAsText_forHeadingEncoder() {
        // The function the headings now call: the stored value must come out inert.
        String stored = "FAKE-XP<i data-xp=\"1\">q</i> &amp; </script data-xp><script>alert(1)</script>";

        String written = SafeEncode.forHtmlContent(stored);

        assertThat(written).doesNotContain("<").doesNotContain(">").contains("&lt;i data-xp=").contains("&amp;amp;");
        assertThat(SafeEncode.forHtmlContent(null)).isEmpty();
    }

    private static String withoutComments() throws IOException {
        return JSP_COMMENT.matcher(Files.readString(SHOW_HISTORY_JSP, StandardCharsets.UTF_8)).replaceAll("");
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty("basedir", System.getProperty("user.dir"))).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not resolve " + relativePath);
    }
}
