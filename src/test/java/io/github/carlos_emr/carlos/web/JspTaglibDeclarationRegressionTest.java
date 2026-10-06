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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for JSPs that previously used a tag-library prefix
 * (e.g. {@code <security:>}, {@code <oscar:>}, {@code <bean:>}) without
 * declaring the corresponding {@code <%@ taglib %>} directive.
 *
 * <p>Each of these JSPs is dispatched standalone (as a Struts action result
 * or via {@code <jsp:include>}), so they cannot inherit taglib declarations
 * from a parent page; the missing directive would cause a JSP translation
 * failure or render the tag as literal text at runtime.
 *
 * @since 2026-05-21
 */
@DisplayName("JSP taglib declarations")
@Tag("unit")
class JspTaglibDeclarationRegressionTest {
    private static final String BASEDIR_PROPERTY = "basedir";
    // Matches JSP template comments so tag usage inside inactive code is ignored.
    private static final Pattern JSP_TEMPLATE_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
    // Matches HTML comments so commented-out tag usage is ignored after JSP comments are removed.
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    // Captures the attribute body of each <%@ taglib ... %> directive as group 1.
    private static final Pattern TAGLIB_DIRECTIVE = Pattern.compile("<%@\\s*taglib\\b(.*?)%>", Pattern.DOTALL);
    // Captures directive attributes as name (group 1), quote character (group 2), and value (group 3).
    private static final Pattern ATTRIBUTE = Pattern.compile("\\b(uri|prefix)\\s*=\\s*(['\"])(.*?)\\2");
    private static final String SHARED_TAGLIB_FILE_NAMES_PATTERN =
            "taglibs\\.jsp|taglibs\\.jspf|common-taglibs\\.jsp|common-tags\\.jsp";
    private static final Pattern SHARED_TAGLIB_FILE = Pattern.compile("^(?:" + SHARED_TAGLIB_FILE_NAMES_PATTERN + ")$");
    private static final Pattern SHARED_TAGLIB_INCLUDE = Pattern.compile(
            "(?s)<%@\\s*include[^>]+file\\s*=\\s*\"[^\"]*(?:" + SHARED_TAGLIB_FILE_NAMES_PATTERN + ")\""
                    + "|<jsp:include[^>]+page\\s*=\\s*\"[^\"]*(?:" + SHARED_TAGLIB_FILE_NAMES_PATTERN + ")\"");
    /*
     * Accept both legacy java.sun.com JSTL URIs and Jakarta EE jakarta.tags URIs
     * because CARLOS still contains migration-era JSPs with either namespace.
     */
    private static final Map<String, Pattern> STANDARD_TAGLIB_URIS = Map.of(
            "c", standardJstlUriPattern("core"),
            "fmt", standardJstlUriPattern("fmt"),
            "fn", standardJstlUriPattern("functions"),
            "sql", standardJstlUriPattern("sql"),
            "x", standardJstlUriPattern("xml"));
    /*
     * Precompile one usage regex per standard prefix. The scanner evaluates
     * every JSP asset, so building these once avoids per-file pattern churn
     * while keeping the prefix-specific tag and EL matching logic explicit.
     */
    private static final Map<String, Pattern> STANDARD_TAGLIB_USAGE = Map.of(
            "c", taglibUsagePattern("c"),
            "fmt", taglibUsagePattern("fmt"),
            "fn", taglibUsagePattern("fn"),
            "sql", taglibUsagePattern("sql"),
            "x", taglibUsagePattern("x"));
    private static final Path WEBAPP_ROOT = resolveProjectPath(Path.of("src/main/webapp"));
    private static final Path JSP_ROOT = resolveProjectPath(Path.of("src/main/webapp/WEB-INF/jsp"));

    @Test
    @DisplayName("should declare security taglib when listTemplates.jsp uses <security:oscarSec>")
    void shouldDeclareSecurityTaglib_forListTemplates() throws Exception {
        String jsp = readJsp("oscarReport/reportByTemplate/listTemplates.jsp");

        assertThat(jsp)
                .contains("<security:oscarSec")
                .contains("<%@ taglib uri=\"/WEB-INF/security.tld\" prefix=\"security\" %>");
    }

    @Test
    @DisplayName("should declare oscar taglib when zdemographicfulltitlesearch.jsp uses <oscar:oscarPropertiesCheck>")
    void shouldDeclareOscarTaglib_forZdemographicFullTitleSearch() throws Exception {
        String jsp = readJsp("demographic/zdemographicfulltitlesearch.jsp");

        assertThat(jsp)
                .contains("<oscar:oscarPropertiesCheck")
                .contains("<%@ taglib uri=\"/WEB-INF/oscar-tag.tld\" prefix=\"oscar\" %>");
    }

    @Test
    @DisplayName("should use fmt:message instead of removed Struts 1 bean:message in queue.jsp")
    void shouldNotUseStruts1BeanMessage_inProgramViewQueue() throws Exception {
        String jsp = readJsp("PMmodule/Admin/ProgramView/queue.jsp");

        // Struts 1 <bean:message> has no taglib in Struts 7; modern usage is <fmt:message>.
        // Both i18n keys that were previously bound to <bean:message> must now use <fmt:message>
        // against the oscarResources bundle. Match keys independently of source-line wrapping.
        assertThat(jsp)
                .doesNotContain("<bean:message")
                .contains("<fmt:setBundle basename=\"oscarResources\"/>")
                .contains("<fmt:message key=\"global.encounter\"/>")
                .containsPattern("<fmt:message\\s+key=\"provider\\.appointmentProviderAdminDay\\.btnE\"\\s*/>");
    }

    @Test
    @DisplayName("should declare every used standard JSTL taglib prefix reported by check-jsp-taglibs.sh")
    void shouldDeclareEveryUsedStandardTaglib_forRepositoryReport() throws Exception {
        List<String> missingDeclarations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(WEBAPP_ROOT)) {
            files.filter(Files::isRegularFile)
                    .filter(JspTaglibDeclarationRegressionTest::isJspAsset)
                    .filter(path -> !isSharedTaglibFile(path))
                    .forEach(path -> collectMissingStandardTaglibs(path, missingDeclarations));
        }

        assertThat(missingDeclarations).isEmpty();
    }

    private static String readJsp(String relativePath) throws Exception {
        return Files.readString(JSP_ROOT.resolve(relativePath));
    }

    private static boolean isJspAsset(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".jsp") || fileName.endsWith(".jspf") || fileName.endsWith(".tag");
    }

    private static boolean isSharedTaglibFile(Path path) {
        return SHARED_TAGLIB_FILE.matcher(path.getFileName().toString()).matches();
    }

    private static void collectMissingStandardTaglibs(Path path, List<String> missingDeclarations) {
        try {
            String jsp = stripTemplateComments(Files.readString(path));
            if (SHARED_TAGLIB_INCLUDE.matcher(jsp).find()) {
                return;
            }
            for (Map.Entry<String, Pattern> taglib : STANDARD_TAGLIB_URIS.entrySet()) {
                String prefix = taglib.getKey();
                if (usesPrefix(jsp, prefix) && !declaresPrefix(jsp, prefix, taglib.getValue())) {
                    missingDeclarations.add(WEBAPP_ROOT.relativize(path) + " missing " + prefix);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to inspect " + path, e);
        }
    }

    private static String stripTemplateComments(String jsp) {
        // Remove JSP comments before HTML comments so HTML-comment text inside an
        // inactive JSP block cannot confuse the HTML comment matcher.
        String withoutJspComments = JSP_TEMPLATE_COMMENT.matcher(jsp).replaceAll("");
        return HTML_COMMENT.matcher(withoutJspComments).replaceAll("");
    }

    private static boolean usesPrefix(String jsp, String prefix) {
        return STANDARD_TAGLIB_USAGE.get(prefix).matcher(jsp).find();
    }

    /**
     * Builds the standard taglib usage regex for one prefix.
     *
     * <p>The expression intentionally covers both server-side tag syntax
     * ({@code <c:if>}, allowing whitespace around the prefix separator) and
     * EL function syntax ({@code ${fn:contains(...)}}, including use inside a
     * larger EL expression). Any file matching either form must declare the
     * corresponding JSTL taglib unless it includes a shared taglib file.</p>
     */
    /**
     * Builds a regex that detects standard JSTL usage for one prefix.
     *
     * @param prefix the taglib prefix to inspect, such as {@code c} or {@code fmt}
     * @return a pattern matching both JSP tag syntax and EL function syntax for the prefix
     */
    private static Pattern taglibUsagePattern(String prefix) {
        // (?i) makes prefix matching case-insensitive; (?s) lets "." span
        // line breaks when matching EL expressions split across lines.
        return Pattern.compile("(?is)<\\s*" + Pattern.quote(prefix) + "\\s*:|\\$\\{[^}]*\\b"
                + Pattern.quote(prefix) + ":");
    }

    /**
     * Builds the accepted URI pattern for a standard JSTL library.
     *
     * @param taglibName the JSTL URI suffix, such as {@code core}, {@code fmt}, or {@code functions}
     * @return a pattern matching both legacy {@code java.sun.com} and Jakarta EE {@code jakarta.tags} URIs
     */
    private static Pattern standardJstlUriPattern(String taglibName) {
        return Pattern.compile("^(?:http://java\\.sun\\.com/jsp/jstl/" + Pattern.quote(taglibName)
                + "|jakarta\\.tags\\." + Pattern.quote(taglibName) + ")$");
    }

    /**
     * Returns whether the JSP declares the requested taglib prefix with an
     * accepted URI.
     *
     * <p>JSP taglib directives are parsed as {@code <%@ taglib ... %>} blocks
     * instead of a single attribute-order-specific regex so both
     * {@code <%@ taglib uri="..." prefix="c" %>} and
     * {@code <%@ taglib prefix="c" uri="..." %>} are handled. Both attributes
     * must match: the prefix confirms the namespace used by tags and EL
     * functions, while the URI pattern confirms that the prefix points to the
     * expected legacy or Jakarta JSTL library rather than another custom taglib.</p>
     */
    private static boolean declaresPrefix(String jsp, String prefix, Pattern uriPattern) {
        Matcher taglibMatcher = TAGLIB_DIRECTIVE.matcher(jsp);
        while (taglibMatcher.find()) {
            String directiveAttributes = taglibMatcher.group(1);
            String directivePrefix = null;
            String directiveUri = null;
            Matcher attributeMatcher = ATTRIBUTE.matcher(directiveAttributes);
            while (attributeMatcher.find()) {
                if ("prefix".equals(attributeMatcher.group(1))) {
                    directivePrefix = attributeMatcher.group(3);
                } else if ("uri".equals(attributeMatcher.group(1))) {
                    directiveUri = attributeMatcher.group(3);
                }
            }
            if (prefix.equals(directivePrefix) && directiveUri != null && uriPattern.matcher(directiveUri).matches()) {
                return true;
            }
        }
        return false;
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
