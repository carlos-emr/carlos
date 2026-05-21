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
    private static final Pattern JSP_TEMPLATE_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern TAGLIB_DIRECTIVE = Pattern.compile("<%@\\s*taglib\\b(.*?)%>", Pattern.DOTALL);
    private static final Pattern ATTRIBUTE = Pattern.compile("\\b(uri|prefix)\\s*=\\s*(['\"])(.*?)\\2");
    private static final String SHARED_TAGLIB_FILE_NAMES =
            "taglibs\\.jsp|taglibs\\.jspf|common-taglibs\\.jsp|common-tags\\.jsp";
    private static final Pattern SHARED_TAGLIB_FILE = Pattern.compile("^(?:" + SHARED_TAGLIB_FILE_NAMES + ")$");
    private static final Pattern SHARED_TAGLIB_INCLUDE = Pattern.compile(
            "(?s)<%@\\s*include[^>]+file\\s*=\\s*\"[^\"]*(?:" + SHARED_TAGLIB_FILE_NAMES + ")\""
                    + "|<jsp:include[^>]+page\\s*=\\s*\"[^\"]*(?:" + SHARED_TAGLIB_FILE_NAMES + ")\"");
    private static final Map<String, Pattern> STANDARD_TAGLIB_URIS = Map.of(
            "c", Pattern.compile("^(?:http://java\\.sun\\.com/jsp/jstl/core|jakarta\\.tags\\.core)$"),
            "fmt", Pattern.compile("^(?:http://java\\.sun\\.com/jsp/jstl/fmt|jakarta\\.tags\\.fmt)$"),
            "fn", Pattern.compile("^(?:http://java\\.sun\\.com/jsp/jstl/functions|jakarta\\.tags\\.functions)$"),
            "sql", Pattern.compile("^(?:http://java\\.sun\\.com/jsp/jstl/sql|jakarta\\.tags\\.sql)$"),
            "x", Pattern.compile("^(?:http://java\\.sun\\.com/jsp/jstl/xml|jakarta\\.tags\\.xml)$"));
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
        return HTML_COMMENT.matcher(JSP_TEMPLATE_COMMENT.matcher(jsp).replaceAll("")).replaceAll("");
    }

    private static boolean usesPrefix(String jsp, String prefix) {
        return STANDARD_TAGLIB_USAGE.get(prefix).matcher(jsp).find();
    }

    private static Pattern taglibUsagePattern(String prefix) {
        return Pattern.compile("(?is)<\\s*" + Pattern.quote(prefix) + "\\s*:|\\$\\{[^}]*\\b"
                + Pattern.quote(prefix) + ":");
    }

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
