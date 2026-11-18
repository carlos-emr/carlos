/**
 * Copyright (c) 2026 CARLOS EMR Project Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the Report by Template page layout: the six pages load one scoped stylesheet and mark their
 * body with {@code rbt-page}, the shared bar stays in the page flow, and no other page picks the layout up.
 *
 * <p>These are source checks. They catch a page that loses the stylesheet, the scope class or the bar,
 * and a rule that escapes its scope; how the pages look is checked with screenshots, not here.</p>
 *
 * @since 2026-11-17
 */
@DisplayName("Report by Template layout JSP regressions")
@Tag("unit")
@Tag("report")
class ReportByTemplateLayoutJspRegressionUnitTest {

    private static final Path JSP_DIR = Path.of("src/main/webapp/WEB-INF/jsp/oscarReport/reportByTemplate");
    private static final Path STYLESHEET =
            Path.of("src/main/webapp/oscarReport/reportByTemplate/reportByTemplateLayout.css");
    private static final String STYLESHEET_LINK = "<link href=\"${pageContext.request.contextPath}"
            + "/oscarReport/reportByTemplate/reportByTemplateLayout.css\" rel=\"stylesheet\">";

    @ParameterizedTest
    @ValueSource(strings = {"homePage.jsp", "templateGroups.jsp", "addEditTemplate.jsp",
            "reportConfiguration.jsp", "viewTemplate.jsp", "resultReport.jsp"})
    @DisplayName("should load the scoped layout and the shared bar on each Report by Template page")
    void shouldLoadTheScopedLayout_onEachReportByTemplatePage(String page) throws IOException {
        String jsp = read(JSP_DIR.resolve(page));

        assertThat(jsp).contains(STYLESHEET_LINK)
                .containsPattern("<body class=\"rbt-page\"[ >]")
                .contains("<%@ include file=\"rbtTopNav.jspf\" %>");
        // The stylesheet is linked once, in the head, before the body it styles.
        assertThat(jsp.indexOf(STYLESHEET_LINK)).isEqualTo(jsp.lastIndexOf(STYLESHEET_LINK))
                .isLessThan(jsp.indexOf("<body class=\"rbt-page\""));
    }

    @Test
    @DisplayName("should keep the bar in the page flow and out of print")
    void shouldKeepTheBarInFlow_andOutOfPrint() throws IOException {
        String nav = read(JSP_DIR.resolve("rbtTopNav.jspf"));
        String css = read(STYLESHEET);

        // A fixed bar leaves the flow and covers the heading below it; a sticky one keeps its space.
        assertThat(nav).contains("class=\"navbar sticky-top bg-body border-bottom mb-2 noprint\"")
                .doesNotContainPattern("class=\"[^\"]*fixed-top");
        assertThat(css).containsPattern("@media print \\{\\s*\\.rbt-page \\.noprint \\{\\s*display: none;");
    }

    @Test
    @DisplayName("should keep the wide result in a labelled region that scrolls on its own")
    void shouldScrollTheWideResult_inALabelledRegion() throws IOException {
        String result = read(JSP_DIR.resolve("resultReport.jsp"));
        String css = read(STYLESHEET);

        assertThat(result).contains("<h3 id=\"rbtReportHeading\">")
                .contains("<div class=\"reportBorderDiv row\" role=\"region\" aria-labelledby=\"rbtReportHeading\" tabindex=\"0\">");
        assertThat(css).containsPattern("\\.rbt-page \\.reportBorderDiv \\{\\s*overflow-x: auto;");
    }

    @Test
    @DisplayName("should scope every rule of the layout stylesheet to Report by Template pages")
    void shouldScopeEveryRule_toReportByTemplatePages() throws IOException {
        List<String> selectors = selectors(read(STYLESHEET));

        assertThat(selectors).isNotEmpty().allSatisfy(selector -> assertThat(selector).matches("\\.rbt-page( .+)?"));
    }

    @Test
    @DisplayName("should leave every other page without the layout")
    void shouldLeaveOtherPages_withoutTheLayout() throws IOException {
        List<Path> users;
        try (Stream<Path> files = Files.walk(Path.of("src/main/webapp"))) {
            users = files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jsp") || path.toString().endsWith(".jspf"))
                    .filter(path -> {
                        String text = read(path);
                        return text.contains("reportByTemplateLayout.css") || text.contains("rbt-page");
                    })
                    .map(path -> JSP_DIR.relativize(path))
                    .sorted()
                    .toList();
        }

        assertThat(users).extracting(Path::toString).containsExactly("addEditTemplate.jsp", "homePage.jsp",
                "reportConfiguration.jsp", "resultReport.jsp", "templateGroups.jsp", "viewTemplate.jsp");
    }

    /** The selectors of every rule, with comments and at-rule headers removed. */
    private static List<String> selectors(String css) {
        String withoutComments = css.replaceAll("(?s)/\\*.*?\\*/", "");
        List<String> result = new ArrayList<>();
        Matcher rule = Pattern.compile("([^{}]+)\\{").matcher(withoutComments);
        while (rule.find()) {
            String head = rule.group(1).trim();
            if (head.startsWith("@")) {
                continue;
            }
            for (String selector : head.split(",")) {
                result.add(selector.trim());
            }
        }
        return result;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
